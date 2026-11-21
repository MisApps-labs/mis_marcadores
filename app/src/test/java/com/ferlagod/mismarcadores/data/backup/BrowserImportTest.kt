// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Marcadores — https://github.com/MisApps-labs/mis_marcadores

package com.ferlagod.mismarcadores.data.backup

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Importación de los ficheros que generan Chrome y Firefox, tal como los escriben los
 * navegadores (estructura, atributos y rarezas incluidas).
 */
class BrowserImportTest {

    private val names = RootFolderNames(toolbar = "Barra", other = "Otros", mobile = "Móvil")

    private fun ImportedFolder.folder(name: String) = folders.single { it.name == name }

    /** "Exportar marcadores" del gestor de marcadores de Chrome. */
    private val chromeHtml = """
        <!DOCTYPE NETSCAPE-Bookmark-file-1>
        <!-- This is an automatically generated file.
             It will be read and overwritten.
             DO NOT EDIT! -->
        <META HTTP-EQUIV="Content-Type" CONTENT="text/html; charset=UTF-8">
        <TITLE>Bookmarks</TITLE>
        <H1>Bookmarks</H1>
        <DL><p>
            <DT><H3 ADD_DATE="1699000000" LAST_MODIFIED="1700000000" PERSONAL_TOOLBAR_FOLDER="true">Barra de marcadores</H3>
            <DL><p>
                <DT><A HREF="https://www.google.com/" ADD_DATE="1699000001" ICON="data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAYAAAAf8/9hAAAA">Google</A>
                <DT><H3 ADD_DATE="1699000002" LAST_MODIFIED="1699000003">Trabajo</H3>
                <DL><p>
                    <DT><A HREF="https://github.com/" ADD_DATE="1699000004">GitHub: Let’s build from here</A>
                    <DT><H3 ADD_DATE="1699000005" LAST_MODIFIED="0">Docs</H3>
                    <DL><p>
                        <DT><A HREF="https://developer.android.com/?hl=es&amp;x=1" ADD_DATE="1699000006">Android Developers</A>
                    </DL><p>
                </DL><p>
                <DT><A HREF="chrome://settings/" ADD_DATE="1699000007">Configuración</A>
            </DL><p>
            <DT><H3 ADD_DATE="1699000008" LAST_MODIFIED="0">Otros marcadores</H3>
            <DL><p>
                <DT><A HREF="https://es.wikipedia.org/wiki/Espa%C3%B1a" ADD_DATE="1699000009">España - Wikipedia</A>
            </DL><p>
        </DL><p>
    """.trimIndent()

    /** "Exportar marcadores a HTML…" de la biblioteca de Firefox. */
    private val firefoxHtml = """
        <!DOCTYPE NETSCAPE-Bookmark-file-1>
        <!-- This is an automatically generated file.
             It will be read and overwritten.
             DO NOT EDIT! -->
        <meta http-equiv="Content-Security-Policy"
              content="default-src 'self'; script-src 'none'; img-src data: *; object-src 'none'"></meta>
        <META HTTP-EQUIV="Content-Type" CONTENT="text/html; charset=UTF-8">
        <Title>Bookmarks</Title>
        <H1>Menú de marcadores</H1>

        <DL><p>
            <DT><A HREF="place:parent=menu________&amp;sort=12&amp;maxResults=10" ADD_DATE="1700000000" LAST_MODIFIED="1700000000">Recientes</A>
            <DT><A HREF="https://www.mozilla.org/es-ES/firefox/" ADD_DATE="1700000001" LAST_MODIFIED="1700000002" ICON_URI="https://www.mozilla.org/favicon.ico" TAGS="mozilla,navegador">Firefox</A>
            <DD>Navegador libre
            <HR>
            <DT><H3 ADD_DATE="1700000003" LAST_MODIFIED="1700000004" PERSONAL_TOOLBAR_FOLDER="true">Barra de herramientas de marcadores</H3>
            <DD>Añada marcadores a esta carpeta para verlos en la barra de herramientas de marcadores
            <DL><p>
                <DT><A HREF="https://duckduckgo.com/" ADD_DATE="1700000005" LAST_MODIFIED="1700000006">DuckDuckGo</A>
                <DT><H3 ADD_DATE="1700000007" LAST_MODIFIED="1700000008">Recetas</H3>
                <DL><p>
                    <DT><A HREF="https://example.com/tortilla" ADD_DATE="1700000009" LAST_MODIFIED="1700000010">Tortilla de patatas</A>
                </DL><p>
            </DL><p>
            <DT><H3 ADD_DATE="1700000011" LAST_MODIFIED="1700000012" UNFILED_BOOKMARKS_FOLDER="true">Otros marcadores</H3>
            <DL><p>
            </DL><p>
        </DL>
    """.trimIndent()

    @Test
    fun chromeHtmlExport() {
        val root = BackupManager.parseFile(chromeHtml.toByteArray(), names)
        assertTrue(root.bookmarks.isEmpty())
        val bar = root.folder("Barra de marcadores")
        // chrome://settings no es una página web: no se importa.
        assertEquals(listOf("https://www.google.com/"), bar.bookmarks.map { it.url })
        val work = bar.folder("Trabajo")
        assertEquals("GitHub: Let’s build from here", work.bookmarks.single().title)
        val docs = work.folder("Docs")
        assertEquals("https://developer.android.com/?hl=es&x=1", docs.bookmarks.single().url)
        assertEquals(1699000006000L, docs.bookmarks.single().addedAt)
        assertEquals("España - Wikipedia", root.folder("Otros marcadores").bookmarks.single().title)
        assertEquals(4, root.countBookmarks())
    }

    @Test
    fun firefoxHtmlExport() {
        val root = BackupManager.parseFile(firefoxHtml.toByteArray(), names)
        // Las entradas "place:" (consultas internas de Firefox) se descartan.
        val firefox = root.bookmarks.single()
        assertEquals("https://www.mozilla.org/es-ES/firefox/", firefox.url)
        assertEquals("Navegador libre", firefox.description)
        assertEquals(listOf("mozilla", "navegador"), firefox.tags)

        val toolbar = root.folder("Barra de herramientas de marcadores")
        assertEquals(listOf("DuckDuckGo"), toolbar.bookmarks.map { it.title })
        // La descripción de la carpeta no se pega a ningún marcador.
        assertEquals("", toolbar.bookmarks.single().description)
        assertEquals("Tortilla de patatas", toolbar.folder("Recetas").bookmarks.single().title)
        assertEquals(0, root.folder("Otros marcadores").countBookmarks())
        assertEquals(3, root.countBookmarks())
    }

    /** "Copia de seguridad…" de la biblioteca de Firefox (JSON). */
    private val firefoxJson = """
        {"guid":"root________","title":"","index":0,"dateAdded":1700000000000000,"lastModified":1700000000000000,"id":1,"typeCode":2,"type":"text/x-moz-place-container","root":"placesRoot","children":[
          {"guid":"menu________","title":"menu","index":0,"id":2,"typeCode":2,"type":"text/x-moz-place-container","root":"bookmarksMenuFolder","children":[
            {"guid":"a1","title":"Mozilla","index":0,"dateAdded":1700000001000000,"lastModified":1700000002000000,"id":10,"typeCode":1,"tags":"web,libre","type":"text/x-moz-place","uri":"https://www.mozilla.org/"},
            {"guid":"s1","title":"","index":1,"id":11,"typeCode":3,"type":"text/x-moz-place-separator"},
            {"guid":"q1","title":"Más visitados","index":2,"id":12,"typeCode":1,"type":"text/x-moz-place","uri":"place:sort=8&maxResults=10"},
            {"guid":"f1","title":"Viajes","index":3,"id":13,"typeCode":2,"type":"text/x-moz-place-container","children":[
              {"guid":"a2","title":"Mapa","index":0,"dateAdded":1700000003000000,"id":14,"typeCode":1,"type":"text/x-moz-place","uri":"https://www.openstreetmap.org/"}
            ]}
          ]},
          {"guid":"toolbar_____","title":"toolbar","index":1,"id":3,"typeCode":2,"type":"text/x-moz-place-container","root":"toolbarFolder","children":[
            {"guid":"a3","title":"Wikipedia","index":0,"id":15,"typeCode":1,"type":"text/x-moz-place","uri":"https://es.wikipedia.org/"}
          ]},
          {"guid":"tags________","title":"tags","index":2,"id":4,"typeCode":2,"type":"text/x-moz-place-container","root":"tagsFolder","children":[
            {"guid":"t1","title":"web","typeCode":2,"type":"text/x-moz-place-container","children":[{"title":"Mozilla","typeCode":1,"type":"text/x-moz-place","uri":"https://www.mozilla.org/"}]}
          ]},
          {"guid":"unfiled_____","title":"unfiled","index":3,"id":5,"typeCode":2,"type":"text/x-moz-place-container","root":"unfiledBookmarksFolder","children":[
            {"guid":"a4","title":"Sin ordenar","index":0,"id":16,"typeCode":1,"type":"text/x-moz-place","uri":"https://example.org/"}
          ]},
          {"guid":"mobile______","title":"mobile","index":4,"id":6,"typeCode":2,"type":"text/x-moz-place-container","root":"mobileFolder","children":[]}
        ]}
    """.trimIndent()

    @Test
    fun firefoxJsonBackup() {
        val root = BackupManager.parseFile(firefoxJson.toByteArray(), names)
        val mozilla = root.bookmarks.single()
        assertEquals("https://www.mozilla.org/", mozilla.url)
        assertEquals(listOf("web", "libre"), mozilla.tags)
        assertEquals(1700000001000L, mozilla.addedAt)
        assertEquals("Mapa", root.folder("Viajes").bookmarks.single().title)
        assertEquals("Wikipedia", root.folder("Barra").bookmarks.single().title)
        assertEquals("Sin ordenar", root.folder("Otros").bookmarks.single().title)
        // La carpeta de etiquetas de Firefox repite marcadores: no se importa.
        assertEquals(4, root.countBookmarks())
    }

    @Test
    fun firefoxJsonLz4Backup() {
        // Bloque LZ4 escrito a mano: literales "abc", copia de 6 bytes a distancia 3, literal "X".
        val block = byteArrayOf(0x32, 'a'.code.toByte(), 'b'.code.toByte(), 'c'.code.toByte(), 3, 0, 0x10, 'X'.code.toByte())
        assertArrayEquals("abcabcabcX".toByteArray(), MozLz4.lz4Block(block, 0, 10))

        // Un fichero .jsonlz4 completo con el JSON sin comprimir (solo literales).
        val json = firefoxJson.toByteArray()
        val lz4 = java.io.ByteArrayOutputStream().apply {
            write(0xF0)
            var rest = json.size - 15
            while (rest >= 255) { write(255); rest -= 255 }
            write(rest)
            write(json)
        }.toByteArray()
        val size = json.size
        val file = "mozLz40\u0000".toByteArray(Charsets.ISO_8859_1) +
            byteArrayOf(size.toByte(), (size shr 8).toByte(), (size shr 16).toByte(), (size shr 24).toByte()) + lz4
        assertEquals(4, BackupManager.parseFile(file, names).countBookmarks())
    }

    /** Fichero `Bookmarks` de la carpeta del perfil de Chrome. */
    @Test
    fun chromeProfileJson() {
        val json = """
            {"checksum":"x","roots":{
              "bookmark_bar":{"children":[
                {"date_added":"13345678901234567","guid":"1","id":"5","name":"Kotlin","type":"url","url":"https://kotlinlang.org/"},
                {"children":[{"date_added":"0","id":"7","name":"Compose","type":"url","url":"https://developer.android.com/compose"}],"id":"6","name":"Android","type":"folder"}
              ],"id":"1","name":"Barra de marcadores","type":"folder"},
              "other":{"children":[{"id":"8","name":"Ajustes","type":"url","url":"chrome://settings"}],"id":"2","name":"Otros marcadores","type":"folder"},
              "synced":{"children":[],"id":"3","name":"Marcadores del móvil","type":"folder"}
            },"version":1}
        """.trimIndent()
        val root = BackupManager.parseFile(json.toByteArray(), names)
        val bar = root.folder("Barra de marcadores")
        val kotlin = bar.bookmarks.single()
        assertEquals("Kotlin", kotlin.title)
        // 13345678901234567 µs desde 1601 = 1701205301234 ms desde 1970 (28/11/2023).
        assertEquals(1701205301234L, kotlin.addedAt)
        assertEquals("Compose", bar.folder("Android").bookmarks.single().title)
        assertEquals(2, root.countBookmarks())
    }
}
