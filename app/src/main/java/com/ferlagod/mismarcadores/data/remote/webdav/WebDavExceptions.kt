// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Marcadores — https://github.com/ferlagod/mis_marcadores

package com.ferlagod.mismarcadores.data.remote.webdav

import java.io.IOException

/** Error HTTP devuelto por el servidor WebDAV. */
open class WebDavException(val code: Int, message: String, val reason: Reason = Reason.HTTP) : IOException(message) {
    enum class Reason {
        HTTP,
        PATH_NOT_FOUND,
        /** El servidor respondió bien pero con algo que no es un listado WebDAV. */
        INVALID_RESPONSE
    }
}

/** 401/403: usuario o contraseña de aplicación incorrectos. */
class WebDavAuthException(code: Int) :
    WebDavException(code, "Usuario o contraseña de aplicación incorrectos ($code)")

/** 412: el fichero remoto cambió desde la última lectura (control de concurrencia optimista). */
class WebDavPreconditionFailedException(fileName: String) :
    WebDavException(412, "El fichero $fileName cambió en el servidor durante la sincronización")
