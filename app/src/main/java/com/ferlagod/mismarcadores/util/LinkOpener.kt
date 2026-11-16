// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Marcadores — https://github.com/ferlagod/mis_marcadores

package com.ferlagod.mismarcadores.util

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri
import com.ferlagod.mismarcadores.R
import com.ferlagod.mismarcadores.data.local.pref.OpenMode

/** Abrir, compartir y copiar enlaces. */
object LinkOpener {

    /**
     * Abre [url] en una pestaña personalizada del navegador (con el color de la app) o en el
     * navegador. Devuelve false si no hay ninguna app capaz de abrirla.
     */
    fun open(context: Context, url: String, mode: OpenMode, toolbarColor: Int, darkTheme: Boolean): Boolean {
        val uri = url.toUri()
        val isWeb = uri.scheme.equals("http", true) || uri.scheme.equals("https", true)
        return try {
            if (mode == OpenMode.IN_APP_TAB && isWeb) {
                val colors = CustomTabColorSchemeParams.Builder().setToolbarColor(toolbarColor).build()
                CustomTabsIntent.Builder()
                    .setShowTitle(true)
                    .setShareState(CustomTabsIntent.SHARE_STATE_ON)
                    .setDefaultColorSchemeParams(colors)
                    .setColorScheme(if (darkTheme) CustomTabsIntent.COLOR_SCHEME_DARK else CustomTabsIntent.COLOR_SCHEME_LIGHT)
                    .build()
                    .launchUrl(context, uri)
            } else {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, uri)
                        .addCategory(Intent.CATEGORY_BROWSABLE)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
            true
        } catch (_: ActivityNotFoundException) {
            // Sin navegador compatible con pestañas: se intenta con cualquier app.
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                true
            } catch (_: ActivityNotFoundException) {
                false
            }
        }
    }

    fun share(context: Context, title: String?, url: String) {
        val text = if (title.isNullOrBlank()) url else "$title\n$url"
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            if (!title.isNullOrBlank()) putExtra(Intent.EXTRA_SUBJECT, title)
        }
        context.startActivity(Intent.createChooser(send, context.getString(R.string.share_link)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Comparte varios enlaces de una vez, uno por línea. */
    fun shareMany(context: Context, items: List<Pair<String, String>>) {
        val text = items.joinToString("\n\n") { (title, url) -> if (title.isBlank()) url else "$title\n$url" }
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(send, context.getString(R.string.share_link)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun copy(context: Context, url: String) {
        val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText("URL", url))
    }

    /** Dirección web que hay en el portapapeles, si la hay (para ofrecer pegarla). */
    fun clipboardUrl(context: Context): String? {
        val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return null
        val clip = clipboard.primaryClip ?: return null
        if (clip.itemCount == 0) return null
        val text = clip.getItemAt(0).coerceToText(context)?.toString()
        return UrlUtils.extractUrl(text)
    }
}
