// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Marcadores — https://github.com/ferlagod/mis_marcadores

package com.ferlagod.mismarcadores.data.metadata

import com.ferlagod.mismarcadores.data.local.model.LinkStatus
import com.ferlagod.mismarcadores.util.UrlUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.UnknownHostException
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit

/**
 * Descarga la cabecera de una página para obtener sus metadatos y comprueba si un enlace
 * sigue funcionando. Solo se conecta a la web del propio enlace (no usa servicios de terceros).
 */
object LinkMetadataFetcher {

    /** Solo se leen los primeros bytes: los metadatos están en la cabecera. */
    private const val MAX_BYTES = 512 * 1024L

    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36 MisMarcadores"

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .callTimeout(25, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    private fun request(url: String, method: String = "GET") = Request.Builder()
        .url(url)
        .method(method, null)
        .header("User-Agent", USER_AGENT)
        .header("Accept", "text/html,application/xhtml+xml;q=0.9,*/*;q=0.8")
        .header("Accept-Language", java.util.Locale.getDefault().toLanguageTag() + ",en;q=0.7")
        .build()

    /**
     * Obtiene título, descripción, imagen e icono de [url].
     * Si la dirección no es una página HTML (p. ej. un PDF), devuelve solo la dirección final.
     */
    suspend fun fetch(url: String): LinkMetadata = withContext(Dispatchers.IO) {
        client.newCall(request(url)).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val finalUrl = response.request.url.toString()
            val body = response.body ?: return@withContext LinkMetadata(finalUrl, faviconUrl = UrlUtils.defaultFavicon(finalUrl))
            val contentType = body.contentType()
            if (contentType != null && contentType.subtype !in setOf("html", "xhtml+xml")) {
                return@withContext LinkMetadata(
                    url = finalUrl,
                    title = finalUrl.substringAfterLast('/').substringBefore('?').takeIf { it.isNotBlank() },
                    faviconUrl = UrlUtils.defaultFavicon(finalUrl)
                )
            }
            val source = body.source()
            source.request(MAX_BYTES)
            val bytes = source.buffer.readByteArray(minOf(source.buffer.size, MAX_BYTES))
            val html = String(bytes, detectCharset(contentType?.charset(), bytes))
            val meta = HtmlMetadataParser.parse(html, finalUrl)
            meta.copy(faviconUrl = meta.faviconUrl ?: UrlUtils.defaultFavicon(finalUrl))
        }
    }

    /** Charset de la cabecera HTTP o, si no la hay, el declarado en el propio HTML (UTF-8 por defecto). */
    private fun detectCharset(header: Charset?, bytes: ByteArray): Charset {
        if (header != null) return header
        val start = String(bytes, 0, minOf(bytes.size, 4096), Charsets.ISO_8859_1)
        val declared = Regex("""charset\s*=\s*["']?([A-Za-z0-9_-]+)""", RegexOption.IGNORE_CASE).find(start)?.groupValues?.get(1)
        return try {
            declared?.let { Charset.forName(it) } ?: Charsets.UTF_8
        } catch (_: Exception) {
            Charsets.UTF_8
        }
    }

    /**
     * Comprueba si el enlace funciona. Solo se considera roto si la página ya no existe
     * (404, 410) o el dominio no resuelve; los bloqueos o fallos temporales no cuentan.
     */
    suspend fun check(url: String): Int = withContext(Dispatchers.IO) {
        if (!url.startsWith("http", ignoreCase = true)) return@withContext LinkStatus.UNKNOWN
        try {
            val code = client.newCall(request(url, "HEAD")).execute().use { it.code }
            val finalCode = if (code == 405 || code == 403 || code == 501) {
                // Algunas webs no aceptan HEAD: se repite con GET.
                client.newCall(request(url)).execute().use { it.code }
            } else code
            when {
                finalCode in 200..399 -> LinkStatus.OK
                finalCode == 404 || finalCode == 410 -> LinkStatus.BROKEN
                else -> LinkStatus.UNKNOWN
            }
        } catch (_: UnknownHostException) {
            LinkStatus.BROKEN
        } catch (_: IOException) {
            LinkStatus.UNKNOWN
        } catch (_: IllegalArgumentException) {
            LinkStatus.BROKEN
        }
    }
}
