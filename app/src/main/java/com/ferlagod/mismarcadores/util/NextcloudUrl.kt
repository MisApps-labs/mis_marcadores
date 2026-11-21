// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Marcadores — https://github.com/MisApps-labs/mis_marcadores

package com.ferlagod.mismarcadores.util

import java.net.URLDecoder

/**
 * Interpreta la dirección del servidor que escribe el usuario. Es una función pura para poder
 * probarla.
 *
 * Se aceptan, además de la dirección base (`https://nube.ejemplo.com`):
 * - La URL WebDAV que muestra Nextcloud en Archivos › Ajustes
 *   (`…/remote.php/dav/files/<usuario>/`): se saca el id de usuario y se usa la carpeta
 *   `MisMarcadores` dentro de sus archivos, nunca la raíz.
 * - Una URL WebDAV con subcarpeta (`…/remote.php/dav/files/<usuario>/Otra/`): se respeta como
 *   carpeta elegida a mano.
 * - Direcciones copiadas del navegador o antiguas (`/index.php/apps/files`, `/apps/…`,
 *   `/remote.php/webdav`): se quedan en la dirección base.
 */
object NextcloudUrl {

    /**
     * @param serverUrl dirección base del servidor, o la URL WebDAV completa de una carpeta
     *   elegida a mano.
     * @param userId id de usuario si venía en la URL (null si hay que preguntarlo al servidor).
     */
    data class Parsed(val serverUrl: String, val userId: String?)

    private const val DAV_FILES = "/remote.php/dav/files/"
    private val BASE_MARKERS = listOf("/remote.php", "/index.php", "/apps/", "/ocs/")

    fun parse(input: String): Parsed {
        val url = input.trim().substringBefore('#').substringBefore('?').trimEnd('/')
        val dav = url.indexOf(DAV_FILES, ignoreCase = true)
        if (dav >= 0) {
            val base = url.substring(0, dav)
            val rest = url.substring(dav + DAV_FILES.length).trim('/')
            val user = rest.substringBefore('/')
            if (user.isEmpty()) return Parsed(base, null)
            val userId = try {
                URLDecoder.decode(user.replace("+", "%2B"), "UTF-8")
            } catch (_: IllegalArgumentException) {
                user
            }
            val folder = rest.substringAfter('/', "")
            return if (folder.isEmpty()) Parsed(base, userId) else Parsed(url, userId)
        }
        for (marker in BASE_MARKERS) {
            val i = url.indexOf(marker, ignoreCase = true)
            if (i >= 0) return Parsed(url.substring(0, i), null)
        }
        return Parsed(url, null)
    }
}
