// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Marcadores — https://github.com/MisApps-labs/mis_marcadores

package com.ferlagod.mismarcadores.data.backup

import com.ferlagod.mismarcadores.data.local.entity.BookmarkEntity
import com.ferlagod.mismarcadores.data.local.entity.FolderEntity
import com.ferlagod.mismarcadores.data.local.model.FolderTree
import com.ferlagod.mismarcadores.data.local.model.Tags
import com.ferlagod.mismarcadores.data.metadata.HtmlMetadataParser
import com.ferlagod.mismarcadores.util.UrlUtils

/** Carpeta leída de un fichero de marcadores. */
class ImportedFolder(val name: String, val color: String? = null) {
    val folders = mutableListOf<ImportedFolder>()
    val bookmarks = mutableListOf<ImportedBookmark>()

    fun countBookmarks(): Int = bookmarks.size + folders.sumOf { it.countBookmarks() }
}

/** Marcador leído de un fichero de marcadores. */
data class ImportedBookmark(
    val url: String,
    val title: String = "",
    val description: String = "",
    val notes: String = "",
    val tags: List<String> = emptyList(),
    val addedAt: Long? = null,
    val modifiedAt: Long? = null,
    val isFavorite: Boolean = false,
    val isReadLater: Boolean = false,
    val color: String? = null,
    val imageUrl: String? = null,
    val faviconUrl: String? = null
)

/**
 * Formato "Netscape Bookmark File", el que usan todos los navegadores para exportar e importar
 * marcadores (Chrome, Firefox, Safari, Edge…) y servicios como linkding, Raindrop o Pocket.
 *
 * Además de los atributos estándar (HREF, ADD_DATE, LAST_MODIFIED, TAGS y TOREAD de linkding/Pocket)
 * se guardan DATA-FAVORITE, DATA-COLOR (también en carpetas), DATA-NOTES, DATA-IMAGE e ICON_URI
 * (el de Firefox) para no perder nada al restaurar una copia
 * de Mis Marcadores. Los navegadores los ignoran.
 */
object NetscapeBookmarks {

    private val TOKEN = Regex(
        """<h3([^>]*)>([\s\S]*?)</h3>|<dl[^>]*>|</dl>|<a\s([^>]*)>([\s\S]*?)</a>|<dd>([^<]*)""",
        RegexOption.IGNORE_CASE
    )
    private val ATTR = Regex("""([a-zA-Z_:-]+)\s*=\s*("([^"]*)"|'([^']*)'|([^\s"'>]+))""")
    private val TAGS_IN_TEXT = Regex("<[^>]+>")

    fun isBookmarkFile(text: String): Boolean =
        text.contains("<A ", ignoreCase = true) && text.contains("HREF", ignoreCase = true)

    // ------------------------------------------------------------------
    // Lectura
    // ------------------------------------------------------------------

    fun parse(html: String): ImportedFolder {
        val root = ImportedFolder("")
        val stack = ArrayDeque<ImportedFolder>().apply { addLast(root) }
        var pendingFolder: ImportedFolder? = null
        var depth = 0
        var lastBookmark: Pair<ImportedFolder, Int>? = null

        for (m in TOKEN.findAll(html)) {
            val token = m.value
            when {
                m.groups[1] != null || token.startsWith("<h3", ignoreCase = true) -> {
                    val folderAttrs = attributes(m.groupValues[1])
                    val folder = ImportedFolder(text(m.groupValues[2]).ifBlank { "—" }, folderAttrs["data-color"])
                    stack.last().folders += folder
                    pendingFolder = folder
                    lastBookmark = null
                }
                token.startsWith("<dl", ignoreCase = true) -> {
                    depth++
                    // El primer <DL> es la raíz; los siguientes abren la carpeta del <H3> anterior.
                    if (depth > 1) stack.addLast(pendingFolder ?: stack.last())
                    pendingFolder = null
                    lastBookmark = null
                }
                token.startsWith("</dl", ignoreCase = true) -> {
                    if (depth > 1 && stack.size > 1) stack.removeLast()
                    depth = maxOf(0, depth - 1)
                    lastBookmark = null
                }
                token.startsWith("<a", ignoreCase = true) -> {
                    val attrs = attributes(m.groupValues[3])
                    val url = attrs["href"]?.let(HtmlMetadataParser::decodeEntities)?.let(UrlUtils::normalizeWeb)
                    if (url == null) {
                        lastBookmark = null
                        continue
                    }
                    val folder = stack.last()
                    folder.bookmarks += ImportedBookmark(
                        url = url,
                        title = text(m.groupValues[4]),
                        notes = attrs["data-notes"]?.let(HtmlMetadataParser::decodeEntities).orEmpty(),
                        tags = attrs["tags"]?.let { Tags.parse(HtmlMetadataParser.decodeEntities(it)) }.orEmpty(),
                        addedAt = attrs["add_date"]?.toLongOrNull()?.let(::toMillis),
                        modifiedAt = attrs["last_modified"]?.toLongOrNull()?.let(::toMillis),
                        isFavorite = attrs["data-favorite"] == "1",
                        isReadLater = attrs["toread"] == "1",
                        color = attrs["data-color"],
                        imageUrl = attrs["data-image"]?.let(HtmlMetadataParser::decodeEntities)?.let(::webUrlOrNull),
                        // ICON_URI es el atributo estándar de Firefox para la dirección del icono.
                        faviconUrl = attrs["icon_uri"]?.let(HtmlMetadataParser::decodeEntities)?.let(::webUrlOrNull)
                    )
                    lastBookmark = folder to folder.bookmarks.lastIndex
                }
                token.startsWith("<dd", ignoreCase = true) -> {
                    val (folder, index) = lastBookmark ?: continue
                    val description = text(m.groupValues[5])
                    if (description.isNotEmpty()) {
                        folder.bookmarks[index] = folder.bookmarks[index].copy(description = description)
                    }
                    lastBookmark = null
                }
            }
        }
        return root
    }

    /**
     * Lista simple de direcciones (una por línea, o mezcladas con texto): para importar ficheros
     * .txt o exportaciones de otras apps que no usan el formato de los navegadores.
     */
    fun parsePlainText(text: String): ImportedFolder {
        val root = ImportedFolder("")
        val seen = HashSet<String>()
        text.lineSequence().mapNotNull { UrlUtils.extractUrl(it) }.forEach { url ->
            if (seen.add(UrlUtils.canonical(url))) root.bookmarks += ImportedBookmark(url = url)
        }
        return root
    }

    /** Solo direcciones http(s): se descartan los iconos incrustados (data:) y otros esquemas. */
    private fun webUrlOrNull(value: String): String? =
        value.trim().takeIf { it.startsWith("https://", ignoreCase = true) || it.startsWith("http://", ignoreCase = true) }

    /** Fechas en segundos (formato estándar) o, si son enormes, ya en milisegundos/microsegundos. */
    internal fun toMillis(value: Long): Long? = when {
        value <= 0 -> null
        value < 100_000_000_000L -> value * 1000
        value < 100_000_000_000_000L -> value
        else -> value / 1000
    }

    private fun attributes(raw: String): Map<String, String> =
        ATTR.findAll(raw).associate { m ->
            m.groupValues[1].lowercase() to (m.groups[3]?.value ?: m.groups[4]?.value ?: m.groups[5]?.value ?: "")
        }

    private fun text(raw: String): String =
        HtmlMetadataParser.decodeEntities(raw.replace(TAGS_IN_TEXT, "")).replace(Regex("\\s+"), " ").trim()

    // ------------------------------------------------------------------
    // Escritura
    // ------------------------------------------------------------------

    /** Exporta los marcadores visibles con la jerarquía de carpetas. */
    fun export(folders: List<FolderEntity>, bookmarks: List<BookmarkEntity>, title: String = "Bookmarks"): String {
        val sb = StringBuilder()
        sb.append("<!DOCTYPE NETSCAPE-Bookmark-file-1>\n")
        sb.append("<!-- This is an automatically generated file.\n     It will be read and overwritten.\n     DO NOT EDIT! -->\n")
        sb.append("<META HTTP-EQUIV=\"Content-Type\" CONTENT=\"text/html; charset=UTF-8\">\n")
        sb.append("<TITLE>").append(escape(title)).append("</TITLE>\n")
        sb.append("<H1>").append(escape(title)).append("</H1>\n")

        val folderIds = folders.mapTo(HashSet()) { it.id }
        val byFolder = bookmarks.groupBy { it.folderId?.takeIf { id -> id in folderIds } }
        val children = folders.groupBy { it.parentId?.takeIf { id -> id in folderIds } }
        val written = HashSet<String>()

        fun writeList(parentId: String?, indent: String) {
            sb.append(indent).append("<DL><p>\n")
            val inner = "$indent    "
            for (folder in children[parentId].orEmpty().sortedBy { it.name.lowercase() }) {
                if (!written.add(folder.id)) continue
                sb.append(inner).append("<DT><H3 ADD_DATE=\"").append(folder.updatedAt / 1000)
                    .append("\" LAST_MODIFIED=\"").append(folder.updatedAt / 1000).append('"')
                if (folder.color != "DEFAULT") sb.append(" DATA-COLOR=\"").append(escape(folder.color)).append('"')
                sb.append(">")
                    .append(escape(folder.name)).append("</H3>\n")
                writeList(folder.id, inner)
            }
            for (b in byFolder[parentId].orEmpty().sortedBy { it.createdAt }) {
                sb.append(inner).append("<DT><A HREF=\"").append(escape(b.url)).append('"')
                    .append(" ADD_DATE=\"").append(b.createdAt / 1000).append('"')
                    .append(" LAST_MODIFIED=\"").append(b.updatedAt / 1000).append('"')
                if (b.tagList.isNotEmpty()) sb.append(" TAGS=\"").append(escape(b.tagList.joinToString(","))).append('"')
                if (b.isReadLater) sb.append(" TOREAD=\"1\"")
                if (b.isFavorite) sb.append(" DATA-FAVORITE=\"1\"")
                if (b.color != "DEFAULT") sb.append(" DATA-COLOR=\"").append(escape(b.color)).append('"')
                if (b.notes.isNotBlank()) sb.append(" DATA-NOTES=\"").append(escape(b.notes)).append('"')
                b.imageUrl?.takeIf { it.isNotBlank() }?.let { sb.append(" DATA-IMAGE=\"").append(escape(it)).append('"') }
                b.faviconUrl?.takeIf { it.isNotBlank() }?.let { sb.append(" ICON_URI=\"").append(escape(it)).append('"') }
                sb.append('>').append(escape(b.title.ifBlank { b.url })).append("</A>\n")
                if (b.description.isNotBlank()) sb.append(inner).append("<DD>").append(escape(b.description)).append('\n')
            }
            sb.append(indent).append("</DL><p>\n")
        }

        writeList(null, "")
        // Carpetas en un ciclo (datos dañados): se escriben al final para no perder sus marcadores.
        FolderTree.flatten(folders).map { it.first }.filter { it.id !in written }.forEach { folder ->
            written += folder.id
            sb.append("<DT><H3>").append(escape(folder.name)).append("</H3>\n")
            writeList(folder.id, "")
        }
        return sb.toString()
    }

    private fun escape(text: String): String = buildString(text.length) {
        for (c in text) when (c) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            '\n' -> append("&#10;")
            '\r' -> Unit
            else -> append(c)
        }
    }
}
