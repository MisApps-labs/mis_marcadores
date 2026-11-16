// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Marcadores — https://github.com/ferlagod/mis_marcadores

package com.ferlagod.mismarcadores.data.local.model

/**
 * Etiquetas de un marcador. Se guardan como texto separado por comas, así que una etiqueta
 * no puede contener comas. Se comparan sin distinguir mayúsculas.
 */
object Tags {

    const val MAX_LENGTH = 40
    private val WHITESPACE = Regex("\\s+")

    /** Limpia una etiqueta escrita por el usuario: sin "#", comas ni espacios repetidos. */
    fun clean(raw: String): String =
        raw.replace(',', ' ')
            .trim()
            .trimStart('#')
            .replace(WHITESPACE, " ")
            .trim()
            .take(MAX_LENGTH)

    fun parse(stored: String?): List<String> =
        if (stored.isNullOrBlank()) emptyList() else normalize(stored.split(','))

    fun join(tags: Collection<String>): String = normalize(tags).joinToString(",")

    /** Limpia, quita vacías y duplicadas (sin distinguir mayúsculas) conservando el orden. */
    fun normalize(tags: Collection<String>): List<String> {
        val seen = HashSet<String>()
        return tags.map(::clean).filter { it.isNotEmpty() && seen.add(it.lowercase()) }
    }

    /** Unión de dos listas de etiquetas (para fusionar versiones de un marcador). */
    fun union(a: String, b: String): String = join(parse(a) + parse(b))

    fun contains(stored: String, tag: String): Boolean = parse(stored).any { it.equals(tag, ignoreCase = true) }
}

/** Resultado de la comprobación de un enlace. */
object LinkStatus {
    const val UNKNOWN = 0
    const val OK = 1
    const val BROKEN = 2
}
