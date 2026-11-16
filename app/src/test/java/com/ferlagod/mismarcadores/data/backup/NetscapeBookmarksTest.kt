// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Marcadores — https://github.com/ferlagod/mis_marcadores

package com.ferlagod.mismarcadores.data.backup

import com.ferlagod.mismarcadores.data.local.entity.BookmarkEntity
import com.ferlagod.mismarcadores.data.local.entity.FolderEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NetscapeBookmarksTest {

    /** Exportación típica de Chrome/Firefox, con carpetas anidadas y descripción. */
    private val browserExport = """
        <!DOCTYPE NETSCAPE-Bookmark-file-1>
        <META HTTP-EQUIV="Content-Type" CONTENT="text/html; charset=UTF-8">
        <TITLE>Bookmarks</TITLE>
        <H1>Bookmarks</H1>
        <DL><p>
            <DT><H3 ADD_DATE="1700000000" PERSONAL_TOOLBAR_FOLDER="true">Barra de marcadores</H3>
            <DL><p>
                <DT><A HREF="https://kotlinlang.org/" ADD_DATE="1700000001" TAGS="dev,kotlin">Kotlin &amp; Co</A>
                <DD>Lenguaje para Android
                <DT><H3>Recetas</H3>
                <DL><p>
                    <DT><A HREF="https://example.com/tortilla" TOREAD="1">Tortilla</A>
                </DL><p>
            </DL><p>
            <DT><A HREF="javascript:void(0)">Bookmarklet</A>
            <DT><A HREF="https://raiz.example.org">En la raíz</A>
        </DL><p>
    """.trimIndent()

    @Test
    fun parsesFoldersBookmarksAndAttributes() {
        val root = NetscapeBookmarks.parse(browserExport)
        assertEquals(listOf("https://raiz.example.org"), root.bookmarks.map { it.url })
        assertEquals(1, root.folders.size)

        val toolbar = root.folders.single()
        assertEquals("Barra de marcadores", toolbar.name)
        val kotlin = toolbar.bookmarks.single()
        assertEquals("Kotlin & Co", kotlin.title)
        assertEquals("Lenguaje para Android", kotlin.description)
        assertEquals(listOf("dev", "kotlin"), kotlin.tags)
        assertEquals(1700000001000L, kotlin.addedAt)

        val recipes = toolbar.folders.single()
        assertEquals("Recetas", recipes.name)
        assertTrue(recipes.bookmarks.single().isReadLater)
        assertEquals(3, root.countBookmarks())
    }

    @Test
    fun exportThenImportKeepsEverything() {
        val parent = FolderEntity(id = "f1", name = "Trabajo <IT>")
        val child = FolderEntity(id = "f2", parentId = "f1", name = "Docs")
        val bookmarks = listOf(
            BookmarkEntity(
                id = "b1", folderId = "f2", url = "https://example.com/?a=1&b=2", title = "Ejemplo \"citado\"",
                description = "Descripción", notes = "Línea 1\nLínea 2", tags = "uno,dos",
                color = "BLUE", isFavorite = true, isReadLater = true, createdAt = 1_700_000_000_000L, updatedAt = 1_700_000_500_000L
            ),
            BookmarkEntity(id = "b2", url = "https://sin-carpeta.example", createdAt = 1_700_000_100_000L)
        )

        val html = NetscapeBookmarks.export(listOf(parent, child), bookmarks)
        val root = NetscapeBookmarks.parse(html)

        assertEquals("https://sin-carpeta.example", root.bookmarks.single().url)
        val work = root.folders.single()
        assertEquals("Trabajo <IT>", work.name)
        val b = work.folders.single { it.name == "Docs" }.bookmarks.single()
        assertEquals("https://example.com/?a=1&b=2", b.url)
        assertEquals("Ejemplo \"citado\"", b.title)
        assertEquals("Descripción", b.description)
        assertEquals("Línea 1\nLínea 2", b.notes)
        assertEquals(listOf("uno", "dos"), b.tags)
        assertEquals("BLUE", b.color)
        assertTrue(b.isFavorite)
        assertTrue(b.isReadLater)
        assertEquals(1_700_000_000_000L, b.addedAt)
    }

    @Test
    fun plainTextListOfLinks() {
        val root = NetscapeBookmarks.parsePlainText("https://a.example\nnada\nmira www.b.example/x\nhttps://a.example/")
        assertEquals(listOf("https://a.example", "https://www.b.example/x"), root.bookmarks.map { it.url })
    }
}
