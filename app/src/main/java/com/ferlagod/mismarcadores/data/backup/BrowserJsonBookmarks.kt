// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Marcadores — https://github.com/MisApps-labs/mis_marcadores

package com.ferlagod.mismarcadores.data.backup

import com.ferlagod.mismarcadores.data.local.model.Tags
import com.ferlagod.mismarcadores.util.UrlUtils
import org.json.JSONArray
import org.json.JSONObject

/** Nombres (traducidos) de las carpetas raíz de los navegadores que no traen nombre propio. */
data class RootFolderNames(val toolbar: String, val other: String, val mobile: String)

/**
 * Formatos JSON de marcadores de los navegadores:
 *
 * - **Firefox**: la copia de "Biblioteca → Importar y respaldar → Copia de seguridad…" (`.json`)
 *   y las copias automáticas de la carpeta `bookmarkbackups` (`.jsonlz4`, ver [MozLz4]).
 * - **Chrome / Edge / Brave**: el fichero `Bookmarks` de la carpeta del perfil.
 *
 * El formato HTML (la opción "Exportar" de cualquier navegador) lo lee [NetscapeBookmarks].
 */
object BrowserJsonBookmarks {

    /** Devuelve null si [text] no es un JSON de marcadores conocido. */
    fun parse(text: String, names: RootFolderNames): ImportedFolder? {
        val json = try {
            JSONObject(text.trimStart('\uFEFF').trim())
        } catch (_: Exception) {
            return null
        }
        return when {
            json.has("roots") -> parseChrome(json.getJSONObject("roots"))
            json.optString("type") == FIREFOX_CONTAINER || json.has("children") -> parseFirefox(json, names)
            else -> null
        }
    }

    // ------------------------------------------------------------------
    // Firefox
    // ------------------------------------------------------------------

    private const val FIREFOX_CONTAINER = "text/x-moz-place-container"
    private const val FIREFOX_BOOKMARK = "text/x-moz-place"

    private fun parseFirefox(rootJson: JSONObject, names: RootFolderNames): ImportedFolder {
        val root = ImportedFolder("")
        for (child in rootJson.optJSONArray("children").objects()) {
            when (child.optString("root")) {
                // El menú de marcadores es la raíz (como en la exportación HTML de Firefox).
                "bookmarksMenuFolder" -> fillFirefox(root, child)
                "toolbarFolder" -> root.folders += ImportedFolder(names.toolbar).also { fillFirefox(it, child) }
                "unfiledBookmarksFolder" -> root.folders += ImportedFolder(names.other).also { fillFirefox(it, child) }
                "mobileFolder" -> root.folders += ImportedFolder(names.mobile).also { fillFirefox(it, child) }
                // "tagsFolder" solo repite los marcadores; las etiquetas ya vienen en cada uno.
                "tagsFolder" -> Unit
                else -> addFirefoxItem(root, child)
            }
        }
        return root
    }

    private fun fillFirefox(target: ImportedFolder, container: JSONObject) {
        for (child in container.optJSONArray("children").objects()) addFirefoxItem(target, child)
    }

    private fun addFirefoxItem(target: ImportedFolder, item: JSONObject) {
        when (item.optString("type")) {
            FIREFOX_CONTAINER -> target.folders += ImportedFolder(item.optString("title").ifBlank { "—" }).also { fillFirefox(it, item) }
            FIREFOX_BOOKMARK -> {
                val url = UrlUtils.normalizeWeb(item.optString("uri")) ?: return
                target.bookmarks += ImportedBookmark(
                    url = url,
                    title = item.optString("title").trim(),
                    tags = Tags.parse(item.optString("tags")),
                    addedAt = item.optLong("dateAdded").takeIf { it > 0 }?.let(NetscapeBookmarks::toMillis),
                    modifiedAt = item.optLong("lastModified").takeIf { it > 0 }?.let(NetscapeBookmarks::toMillis)
                )
            }
        }
    }

    // ------------------------------------------------------------------
    // Chrome
    // ------------------------------------------------------------------

    /** Diferencia entre la época de Windows (1601) que usa Chrome y la de Unix (1970), en ms. */
    private const val WINDOWS_EPOCH_OFFSET_MS = 11_644_473_600_000L

    private fun parseChrome(roots: JSONObject): ImportedFolder {
        val root = ImportedFolder("")
        for (key in roots.keys()) {
            val node = roots.optJSONObject(key) ?: continue
            if (node.optString("type") != "folder") continue
            root.folders += chromeFolder(node)
        }
        return root
    }

    private fun chromeFolder(node: JSONObject): ImportedFolder {
        val folder = ImportedFolder(node.optString("name").ifBlank { "—" })
        for (child in node.optJSONArray("children").objects()) {
            when (child.optString("type")) {
                "folder" -> folder.folders += chromeFolder(child)
                "url" -> {
                    val url = UrlUtils.normalizeWeb(child.optString("url")) ?: continue
                    folder.bookmarks += ImportedBookmark(
                        url = url,
                        title = child.optString("name").trim(),
                        addedAt = chromeTime(child.optString("date_added"))
                    )
                }
            }
        }
        return folder
    }

    /** Microsegundos desde 1601 → milisegundos desde 1970. */
    private fun chromeTime(raw: String): Long? =
        raw.toLongOrNull()?.takeIf { it > 0 }?.let { it / 1000 - WINDOWS_EPOCH_OFFSET_MS }?.takeIf { it > 0 }

    private fun JSONArray?.objects(): List<JSONObject> =
        if (this == null) emptyList() else List(length()) { optJSONObject(it) }.filterNotNull()
}

/**
 * Formato `mozlz4` de las copias automáticas de Firefox (`.jsonlz4`): la cabecera
 * `mozLz40\0`, el tamaño descomprimido (4 bytes, little-endian) y un bloque LZ4.
 */
object MozLz4 {

    private val MAGIC = "mozLz40\u0000".toByteArray(Charsets.ISO_8859_1)
    private const val MAX_SIZE = 64 * 1024 * 1024

    fun isMozLz4(bytes: ByteArray): Boolean =
        bytes.size >= 12 && MAGIC.indices.all { bytes[it] == MAGIC[it] }

    fun decompress(bytes: ByteArray): ByteArray {
        require(isMozLz4(bytes)) { "No es un fichero mozlz4" }
        val size = (bytes[8].toInt() and 0xFF) or ((bytes[9].toInt() and 0xFF) shl 8) or
            ((bytes[10].toInt() and 0xFF) shl 16) or ((bytes[11].toInt() and 0xFF) shl 24)
        require(size in 0..MAX_SIZE) { "Tamaño no válido" }
        return lz4Block(bytes, 12, size)
    }

    /** Descompresión de un bloque LZ4 (formato de bloque, sin marco). */
    fun lz4Block(src: ByteArray, start: Int, outSize: Int): ByteArray {
        val out = ByteArray(outSize)
        var ip = start
        var op = 0
        while (ip < src.size) {
            val token = src[ip++].toInt() and 0xFF
            var literals = token ushr 4
            if (literals == 15) {
                while (true) {
                    val b = src[ip++].toInt() and 0xFF
                    literals += b
                    if (b != 255) break
                }
            }
            require(op + literals <= outSize && ip + literals <= src.size) { "Bloque LZ4 dañado" }
            System.arraycopy(src, ip, out, op, literals)
            ip += literals
            op += literals
            if (ip >= src.size) break // la última secuencia solo lleva literales

            val offset = (src[ip].toInt() and 0xFF) or ((src[ip + 1].toInt() and 0xFF) shl 8)
            ip += 2
            require(offset in 1..op) { "Bloque LZ4 dañado" }
            var matchLength = (token and 0x0F) + 4
            if ((token and 0x0F) == 15) {
                while (true) {
                    val b = src[ip++].toInt() and 0xFF
                    matchLength += b
                    if (b != 255) break
                }
            }
            require(op + matchLength <= outSize) { "Bloque LZ4 dañado" }
            // Copia byte a byte: el origen puede solaparse con el destino (repeticiones).
            var from = op - offset
            repeat(matchLength) { out[op++] = out[from++] }
        }
        return if (op == outSize) out else out.copyOf(op)
    }
}
