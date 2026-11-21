// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Marcadores — https://github.com/MisApps-labs/mis_marcadores

package com.ferlagod.mismarcadores.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextSearchTest {

    @Test
    fun ignoresAccentsAndCase() {
        assertTrue(TextSearch.normalize("Canción ÑANDÚ").contains("cancion"))
        assertEquals(listOf("cafe", "rapido"), TextSearch.terms("  Café   RÁPIDO "))
    }

    @Test
    fun matchRangesPointToOriginalText() {
        val text = "Mi canción favorita"
        val ranges = TextSearch.matchRanges(text, TextSearch.terms("cancion"))
        assertEquals(listOf(3..9), ranges)
        assertEquals("canción", text.substring(ranges[0]))
    }
}
