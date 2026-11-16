// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Marcadores — https://github.com/ferlagod/mis_marcadores

package com.ferlagod.mismarcadores.data.metadata

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HtmlMetadataParserTest {

    @Test
    fun prefersOpenGraphAndResolvesRelativeUrls() {
        val html = """
            <!doctype html><html><head>
            <meta charset="utf-8">
            <title>Título de la pestaña</title>
            <meta property="og:title" content="Caf&eacute; &amp; Tostadas &#8211; Receta">
            <meta name="description" content="Descripción normal">
            <meta property='og:description' content='Descripción OG'>
            <meta property="og:image" content="/img/portada.jpg">
            <link rel="icon" href="/favicon-16.png" sizes="16x16">
            <link rel="apple-touch-icon" href="https://cdn.example.com/touch.png">
            </head><body><meta property="og:title" content="No debe leerse"></body></html>
        """.trimIndent()

        val meta = HtmlMetadataParser.parse(html, "https://example.com/recetas/cafe")
        assertEquals("Café & Tostadas – Receta", meta.title)
        assertEquals("Descripción OG", meta.description)
        assertEquals("https://example.com/img/portada.jpg", meta.imageUrl)
        assertEquals("https://cdn.example.com/touch.png", meta.faviconUrl)
    }

    @Test
    fun fallsBackToTitleTagAndCollapsesWhitespace() {
        val html = "<html><head><title>\n  Hola\n   mundo  </title></head></html>"
        val meta = HtmlMetadataParser.parse(html, "https://example.com")
        assertEquals("Hola mundo", meta.title)
        assertNull(meta.description)
        assertNull(meta.imageUrl)
        assertNull(meta.faviconUrl)
    }

    @Test
    fun ignoresNonHttpImages() {
        val html = """<head><meta property="og:image" content="data:image/png;base64,AAA"></head>"""
        assertNull(HtmlMetadataParser.parse(html, "https://example.com").imageUrl)
    }

    @Test
    fun decodesEntities() {
        assertEquals("¿Qué tal? «sí» it's", HtmlMetadataParser.decodeEntities("&iquest;Qu&eacute; tal? &laquo;s&iacute;&raquo; it&#x27;s"))
        assertEquals("&unknown;", HtmlMetadataParser.decodeEntities("&unknown;"))
    }
}
