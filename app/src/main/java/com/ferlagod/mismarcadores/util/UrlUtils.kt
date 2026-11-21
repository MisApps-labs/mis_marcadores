// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Marcadores — https://github.com/MisApps-labs/mis_marcadores

package com.ferlagod.mismarcadores.util

import java.net.URI
import java.util.Locale

/**
 * Utilidades de direcciones web. Son funciones puras (sin Android) para poder probarlas.
 */
object UrlUtils {

    private val URL_IN_TEXT = Regex("""(?i)\b((?:https?://|www\.)[^\s<>"'«»]+)""")
    private val SCHEME = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:")
    private val TRAILING_PUNCTUATION = ".,;:!?)]}'\"»”’"
    private val BLOCKED_SCHEMES = setOf("javascript", "data", "file", "content", "intent", "vbscript")

    /** Parámetros de seguimiento que se pueden quitar sin cambiar la página. */
    private val TRACKING_PARAMS = setOf(
        "utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content", "utm_id", "utm_name",
        "fbclid", "gclid", "dclid", "msclkid", "mc_cid", "mc_eid", "igshid", "si", "ref_src", "_hsenc", "_hsmi"
    )

    /** Primera dirección que aparece en un texto (p. ej. "Mira esto https://… vía @app"). */
    fun extractUrl(text: String?): String? {
        if (text.isNullOrBlank()) return null
        val match = URL_IN_TEXT.find(text) ?: return null
        var url = match.value
        // Quita la puntuación final que no forma parte de la dirección (salvo paréntesis equilibrados).
        while (url.isNotEmpty() && url.last() in TRAILING_PUNCTUATION) {
            if (url.last() == ')' && url.count { it == '(' } >= url.count { it == ')' }) break
            url = url.dropLast(1)
        }
        return normalize(url)
    }

    /**
     * Dirección lista para guardar: sin espacios y con esquema (https:// por defecto).
     * Devuelve null si no parece una dirección web válida.
     */
    fun normalize(input: String?): String? {
        val raw = input?.trim()?.replace(" ", "%20") ?: return null
        if (raw.isEmpty()) return null
        val withScheme = when {
            raw.startsWith("//") -> "https:$raw"
            SCHEME.containsMatchIn(raw) && !raw.matches(Regex("^[^/]+:\\d+.*")) -> raw
            else -> "https://$raw"
        }
        return try {
            val uri = URI(withScheme)
            val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: return null
            // Esquemas que podrían ejecutar código o abrir ficheros locales: no se guardan.
            if (scheme in BLOCKED_SCHEMES) return null
            if (scheme == "http" || scheme == "https") {
                val host = uri.host ?: return null
                if (!host.contains('.') && host != "localhost") return null
            }
            withScheme
        } catch (_: Exception) {
            null
        }
    }

    fun isValid(input: String?): Boolean = normalize(input) != null

    /**
     * Dirección web lista para importar (http, https o ftp). Descarta las entradas internas de
     * los navegadores, como `place:` de Firefox o `chrome://`, que no son páginas.
     */
    fun normalizeWeb(input: String?): String? {
        val url = normalize(input) ?: return null
        val scheme = url.substringBefore(':').lowercase(Locale.ROOT)
        return url.takeIf { scheme == "http" || scheme == "https" || scheme == "ftp" }
    }

    /** Dominio sin "www." (p. ej. "es.wikipedia.org"). */
    fun host(url: String): String = try {
        URI(url).host?.lowercase(Locale.ROOT)?.removePrefix("www.").orEmpty()
    } catch (_: Exception) {
        ""
    }

    /** Dominio para mostrar; si no se puede obtener, la dirección sin esquema. */
    fun displayHost(url: String): String =
        host(url).ifEmpty { url.substringAfter("://").substringBefore('/') }.ifEmpty { url }

    /** Dirección sin esquema, "www." ni barra final, para mostrar de forma compacta. */
    fun displayUrl(url: String): String =
        url.substringAfter("://").removePrefix("www.").trimEnd('/')

    /**
     * Clave para detectar duplicados: sin esquema, "www.", fragmento, barra final ni parámetros
     * de seguimiento, y con el dominio en minúsculas.
     */
    fun canonical(url: String): String {
        val normalized = normalize(url) ?: return url.trim().lowercase(Locale.ROOT)
        return try {
            val uri = URI(normalized)
            val host = (uri.host ?: "").lowercase(Locale.ROOT).removePrefix("www.")
            val port = if (uri.port == -1 || uri.port == 80 || uri.port == 443) "" else ":${uri.port}"
            val path = (uri.rawPath ?: "").trimEnd('/')
            val query = uri.rawQuery?.split('&')
                ?.filter { it.isNotEmpty() && it.substringBefore('=').lowercase(Locale.ROOT) !in TRACKING_PARAMS }
                ?.sorted()
                ?.joinToString("&")
                .orEmpty()
            if (host.isEmpty()) normalized.lowercase(Locale.ROOT)
            else host + port + path + (if (query.isNotEmpty()) "?$query" else "")
        } catch (_: Exception) {
            normalized.lowercase(Locale.ROOT)
        }
    }

    /** Quita los parámetros de seguimiento (utm_*, fbclid…) de una dirección. */
    fun stripTracking(url: String): String {
        val queryStart = url.indexOf('?')
        if (queryStart < 0) return url
        val fragmentStart = url.indexOf('#', queryStart).let { if (it < 0) url.length else it }
        val base = url.substring(0, queryStart)
        val fragment = url.substring(fragmentStart)
        val kept = url.substring(queryStart + 1, fragmentStart).split('&')
            .filter { it.isNotEmpty() && it.substringBefore('=').lowercase(Locale.ROOT) !in TRACKING_PARAMS }
        return base + (if (kept.isEmpty()) "" else "?" + kept.joinToString("&")) + fragment
    }

    /** Icono por defecto de una web (/favicon.ico) para cuando la página no declara ninguno. */
    fun defaultFavicon(url: String): String? = try {
        val uri = URI(url)
        if (uri.scheme == null || uri.host == null) null
        else "${uri.scheme}://${uri.host}${if (uri.port == -1) "" else ":${uri.port}"}/favicon.ico"
    } catch (_: Exception) {
        null
    }
}
