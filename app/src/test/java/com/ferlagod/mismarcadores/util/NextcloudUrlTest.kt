// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Marcadores — https://github.com/MisApps-labs/mis_marcadores

package com.ferlagod.mismarcadores.util

import com.ferlagod.mismarcadores.util.NextcloudUrl.Parsed
import org.junit.Assert.assertEquals
import org.junit.Test

class NextcloudUrlTest {

    @Test
    fun baseUrlIsKept() {
        assertEquals(Parsed("https://nube.ejemplo.com", null), NextcloudUrl.parse(" https://nube.ejemplo.com/ "))
        assertEquals(Parsed("https://ejemplo.com/nextcloud", null), NextcloudUrl.parse("https://ejemplo.com/nextcloud"))
    }

    @Test
    fun webDavUrlOfUserHomeUsesAppFolderNotRoot() {
        assertEquals(
            Parsed("https://nube.ejemplo.com", "ana.garcia"),
            NextcloudUrl.parse("https://nube.ejemplo.com/remote.php/dav/files/ana.garcia/")
        )
        assertEquals(
            Parsed("https://ejemplo.com/nextcloud", "ana@correo.es"),
            NextcloudUrl.parse("https://ejemplo.com/nextcloud/remote.php/dav/files/ana%40correo.es")
        )
    }

    @Test
    fun webDavUrlWithSubfolderIsRespected() {
        val url = "https://nube.ejemplo.com/remote.php/dav/files/ana/Copias/Marcadores"
        assertEquals(Parsed(url, "ana"), NextcloudUrl.parse("$url/"))
    }

    @Test
    fun browserAndLegacyUrlsAreReducedToTheBase() {
        assertEquals(Parsed("https://nube.ejemplo.com", null), NextcloudUrl.parse("https://nube.ejemplo.com/index.php/apps/files/?dir=/"))
        assertEquals(Parsed("https://nube.ejemplo.com", null), NextcloudUrl.parse("https://nube.ejemplo.com/apps/files/files/123?dir=/Docs"))
        assertEquals(Parsed("https://nube.ejemplo.com", null), NextcloudUrl.parse("https://nube.ejemplo.com/remote.php/webdav/"))
        assertEquals(Parsed("https://apps.ejemplo.com", null), NextcloudUrl.parse("https://apps.ejemplo.com"))
    }
}
