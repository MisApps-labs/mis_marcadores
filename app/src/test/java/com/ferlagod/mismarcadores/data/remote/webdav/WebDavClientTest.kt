// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Marcadores — https://github.com/ferlagod/mis_marcadores

package com.ferlagod.mismarcadores.data.remote.webdav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
}
