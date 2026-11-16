// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Marcadores — https://github.com/ferlagod/mis_marcadores

package com.ferlagod.mismarcadores.data.local.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TagsTest {

    @Test
    fun cleansAndDeduplicates() {
        assertEquals(listOf("Recetas", "viaje largo"), Tags.normalize(listOf(" #Recetas ", "recetas", "viaje   largo", "", "#")))
        assertEquals("a,b", Tags.join(listOf("a", "B", "b", "a")).let { Tags.join(Tags.parse(it)) }.lowercase())
    }

    @Test
    fun parseJoinUnionContains() {
        assertEquals(emptyList<String>(), Tags.parse(""))
        assertEquals(listOf("uno", "dos"), Tags.parse("uno, dos,,"))
        assertEquals("uno,dos,tres", Tags.union("uno,dos", "DOS,tres"))
        assertTrue(Tags.contains("Uno,dos", "uno"))
    }
}
