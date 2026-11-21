// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Marcadores — https://github.com/ferlagod/mis_marcadores

package com.ferlagod.mismarcadores.data.remote.webdav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WebDavClientTest {

    @Test
    fun readsUserIdFromOcsResponse() {
        val json = """{"ocs":{"meta":{"status":"ok","statuscode":200},"data":{"id":"ana.garcia","email":"ana@example.com","display-name":"Ana"}}}"""
        assertEquals("ana.garcia", WebDavClient.parseOcsUserId(json))
    }

    @Test
    fun missingOrInvalidUserIdIsNull() {
        assertNull(WebDavClient.parseOcsUserId("""{"ocs":{"data":{}}}"""))
        assertNull(WebDavClient.parseOcsUserId("""{"ocs":{"data":{"id":"  "}}}"""))
        assertNull(WebDavClient.parseOcsUserId("<html>no es JSON</html>"))
    }

    @Test
    fun recognisesMultistatusListings() {
        val nextcloud = """<?xml version="1.0"?>
            <d:multistatus xmlns:d="DAV:" xmlns:oc="http://owncloud.org/ns">
              <d:response><d:href>/remote.php/dav/files/ana/MisMarcadores/</d:href></d:response>
            </d:multistatus>"""
        assertTrue(WebDavClient.isMultistatus(nextcloud))
        assertTrue(WebDavClient.isMultistatus("<D:multistatus xmlns:D=\"DAV:\"><D:response></D:response></D:multistatus>"))
        assertTrue(WebDavClient.isMultistatus("<multistatus xmlns=\"DAV:\"><response/></multistatus>"))
    }

    @Test
    fun emptyOrForeignListingsAreRejected() {
        assertFalse(WebDavClient.isMultistatus(""))
        assertFalse(WebDavClient.isMultistatus("   \n"))
        assertFalse(WebDavClient.isMultistatus("<html><body>Bad gateway</body></html>"))
        // Multistatus sin ninguna respuesta: ni siquiera la propia carpeta.
        assertFalse(WebDavClient.isMultistatus("<d:multistatus xmlns:d=\"DAV:\"></d:multistatus>"))
    }
}
