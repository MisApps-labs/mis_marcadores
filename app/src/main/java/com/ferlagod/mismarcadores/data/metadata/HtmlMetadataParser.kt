// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Marcadores — https://github.com/MisApps-labs/mis_marcadores

package com.ferlagod.mismarcadores.data.metadata

import java.net.URI

/** Datos de una página web para rellenar un marcador. */
data class LinkMetadata(
    /** Dirección final, tras las redirecciones. */
    val url: String,
    val title: String? = null,
    val description: String? = null,
    val imageUrl: String? = null,
    val faviconUrl: String? = null,
    val siteName: String? = null
)

/**
 * Extrae los metadatos de la cabecera de una página HTML (Open Graph, Twitter Cards, `<title>`,
 * `<meta name="description">` e iconos). Función pura, sin Android, para poder probarla.
 *
 * No usa un analizador HTML completo: solo lee las etiquetas `<meta>`, `<link>` y `<title>`,
 * que es lo que interesa, y tolera HTML mal formado.
 */
object HtmlMetadataParser {

    private val TAG = Regex("""<(meta|link)\b([^>]*)>""", RegexOption.IGNORE_CASE)
    private val ATTR = Regex("""([a-zA-Z_:.-]+)\s*=\s*("([^"]*)"|'([^']*)'|([^\s"'>]+))""")
    private val TITLE = Regex("""<title[^>]*>([\s\S]*?)</title>""", RegexOption.IGNORE_CASE)
    private val WHITESPACE = Regex("\\s+")
    private val ENTITY = Regex("&(#x[0-9a-fA-F]+|#[0-9]+|[a-zA-Z]+);")

    private const val MAX_TITLE = 300
    private const val MAX_DESCRIPTION = 1000

    fun parse(html: String, pageUrl: String): LinkMetadata {
        val head = html.substringBefore("</head>", html.substringBefore("<body", html))
        val metas = HashMap<String, String>()
        val icons = mutableListOf<Pair<String, Int>>()

        for (match in TAG.findAll(head)) {
            val tag = match.groupValues[1].lowercase()
            val attrs = attributes(match.groupValues[2])
            if (tag == "meta") {
                val name = (attrs["property"] ?: attrs["name"] ?: attrs["itemprop"])?.lowercase()?.trim() ?: continue
                val content = attrs["content"]?.let(::decodeEntities)?.trim()?.takeIf { it.isNotEmpty() } ?: continue
                metas.putIfAbsent(name, content)
            } else {
                val rel = attrs["rel"]?.lowercase() ?: continue
                val href = attrs["href"]?.trim()?.takeIf { it.isNotEmpty() } ?: continue
                val rank = when {
                    "apple-touch-icon" in rel -> 3
                    rel.split(' ').contains("icon") && attrs["type"]?.contains("svg") != true -> iconSizeRank(attrs["sizes"])
                    else -> continue
                }
                icons += decodeEntities(href) to rank
            }
        }

        val title = metas["og:title"] ?: metas["twitter:title"]
            ?: TITLE.find(head)?.groupValues?.get(1)?.let(::decodeEntities)
            ?: metas["title"]
        val description = metas["og:description"] ?: metas["twitter:description"] ?: metas["description"]
        val image = metas["og:image:secure_url"] ?: metas["og:image"] ?: metas["og:image:url"]
            ?: metas["twitter:image"] ?: metas["twitter:image:src"] ?: metas["image"]
        val canonical = metas["og:url"]?.takeIf { it.startsWith("http") }

        return LinkMetadata(
            url = pageUrl,
            title = title?.clean(MAX_TITLE),
            description = description?.clean(MAX_DESCRIPTION),
            imageUrl = image?.let { resolve(canonical ?: pageUrl, it) }?.takeIf { it.startsWith("http") },
            faviconUrl = icons.maxByOrNull { it.second }?.first?.let { resolve(pageUrl, it) }?.takeIf { it.startsWith("http") },
            siteName = metas["og:site_name"]?.clean(MAX_TITLE)
        )
    }

    /** Prefiere iconos grandes (se ven mejor en la lista) pero no gigantes. */
    private fun iconSizeRank(sizes: String?): Int {
        val size = sizes?.substringBefore('x')?.trim()?.toIntOrNull() ?: return 1
        return if (size in 32..256) 2 else 1
    }

    private fun attributes(raw: String): Map<String, String> =
        ATTR.findAll(raw).associate { m ->
            m.groupValues[1].lowercase() to (m.groups[3]?.value ?: m.groups[4]?.value ?: m.groups[5]?.value ?: "")
        }

    private fun String.clean(max: Int): String? =
        decodeEntities(this).replace(WHITESPACE, " ").trim().take(max).takeIf { it.isNotEmpty() }

    fun resolve(base: String, href: String): String? = try {
        URI(base).resolve(href.trim().replace(" ", "%20")).toString()
    } catch (_: Exception) {
        null
    }

    /** Decodifica las entidades HTML más habituales (&amp;, &#39;, &#x27;, &nbsp;…). */
    fun decodeEntities(text: String): String {
        if (!text.contains('&')) return text
        return ENTITY.replace(text) { m ->
            val entity = m.groupValues[1]
            when {
                entity.startsWith("#x", ignoreCase = true) ->
                    entity.substring(2).toIntOrNull(16)?.let { String(Character.toChars(it)) } ?: m.value
                entity.startsWith("#") ->
                    entity.substring(1).toIntOrNull()?.let { String(Character.toChars(it)) } ?: m.value
                else -> NAMED[entity] ?: NAMED[entity.lowercase()] ?: m.value
            }
        }
    }

    private val NAMED = mapOf(
        "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to " ",
        "ndash" to "–", "mdash" to "—", "hellip" to "…", "laquo" to "«", "raquo" to "»",
        "lsquo" to "‘", "rsquo" to "’", "ldquo" to "“", "rdquo" to "”", "middot" to "·",
        "copy" to "©", "reg" to "®", "trade" to "™", "euro" to "€", "bull" to "•",
        "aacute" to "á", "eacute" to "é", "iacute" to "í", "oacute" to "ó", "uacute" to "ú",
        "Aacute" to "Á", "Eacute" to "É", "Iacute" to "Í", "Oacute" to "Ó", "Uacute" to "Ú",
        "ntilde" to "ñ", "Ntilde" to "Ñ", "uuml" to "ü", "Uuml" to "Ü", "ouml" to "ö", "auml" to "ä",
        "ccedil" to "ç", "egrave" to "è", "agrave" to "à", "iquest" to "¿", "iexcl" to "¡"
    )
}
