// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Marcadores — https://github.com/MisApps-labs/mis_marcadores

package com.ferlagod.mismarcadores.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UrlUtilsTest {

    @Test
    fun normalizeAddsSchemeAndRejectsNonsense() {
        assertEquals("https://example.com", UrlUtils.normalize("example.com"))
        assertEquals("https://example.com/a%20b", UrlUtils.normalize("  example.com/a b "))
        assertEquals("http://example.com", UrlUtils.normalize("http://example.com"))
        assertEquals("https://localhost:8080/x", UrlUtils.normalize("localhost:8080/x"))
        assertEquals("https://example.com/x", UrlUtils.normalize("//example.com/x"))
        assertNull(UrlUtils.normalize("hola"))
        assertNull(UrlUtils.normalize(""))
        assertNull(UrlUtils.normalize("javascript:alert(1)"))
        assertNull(UrlUtils.normalize("file:///sdcard/secret"))
    }

    @Test
    fun extractUrlFromSharedText() {
        assertEquals(
            "https://es.wikipedia.org/wiki/Marcador_(Internet)",
            UrlUtils.extractUrl("Mira esto: https://es.wikipedia.org/wiki/Marcador_(Internet).")
        )
        assertEquals("https://www.example.com/page", UrlUtils.extractUrl("«Título» www.example.com/page, vía @app"))
        assertNull(UrlUtils.extractUrl("sin enlaces aquí"))
    }

    @Test
    fun canonicalIgnoresCosmeticDifferences() {
        val a = UrlUtils.canonical("https://www.Example.com/path/?utm_source=x&b=2&a=1#frag")
        val b = UrlUtils.canonical("http://example.com/path?a=1&b=2")
        assertEquals(a, b)
        assertNotEquals(UrlUtils.canonical("https://example.com/a"), UrlUtils.canonical("https://example.com/b"))
    }

    @Test
    fun stripTrackingKeepsRealParameters() {
        assertEquals(
            "https://example.com/p?id=7#top",
            UrlUtils.stripTracking("https://example.com/p?utm_source=news&id=7&fbclid=abc#top")
        )
        assertEquals("https://example.com/p", UrlUtils.stripTracking("https://example.com/p?utm_medium=x"))
        assertEquals("https://example.com/p", UrlUtils.stripTracking("https://example.com/p"))
    }

    @Test
    fun hostAndDisplay() {
        assertEquals("es.wikipedia.org", UrlUtils.host("https://es.wikipedia.org/wiki/X"))
        assertEquals("example.com", UrlUtils.host("https://www.example.com"))
        assertEquals("example.com/a", UrlUtils.displayUrl("https://www.example.com/a/"))
        assertEquals("https://example.com:8443/favicon.ico", UrlUtils.defaultFavicon("https://example.com:8443/a/b"))
    }
}
