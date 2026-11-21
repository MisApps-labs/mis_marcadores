// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Marcadores — https://github.com/MisApps-labs/mis_marcadores

package com.ferlagod.mismarcadores.data.local.pref

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Tema de la interfaz. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Cómo se muestran los marcadores en las listas. */
enum class LayoutMode { LIST, CARDS, COMPACT }

/** Orden de los marcadores en las listas. */
enum class SortOrder { CREATED_DESC, CREATED_ASC, MODIFIED_DESC, TITLE_ASC, TITLE_DESC, DOMAIN_ASC, MOST_OPENED, RECENTLY_OPENED }

/** Qué hace un toque sobre un marcador. */
enum class TapAction { OPEN_LINK, EDIT }

/** Dónde se abren los enlaces. */
enum class OpenMode { IN_APP_TAB, BROWSER }

/** Preferencias de la app que no son secretas (aspecto y comportamiento). */
data class AppSettingsState(
    val layoutMode: LayoutMode = LayoutMode.LIST,
    val sortOrder: SortOrder = SortOrder.CREATED_DESC,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** En modo oscuro, negro puro en lugar de gris (pantallas OLED). */
    val amoled: Boolean = false,
    val tapAction: TapAction = TapAction.OPEN_LINK,
    val openMode: OpenMode = OpenMode.IN_APP_TAB,
    /** Descargar las imágenes de vista previa e iconos de las webs. */
    val loadPreviews: Boolean = true,
    /** Obtener título, descripción e imagen al guardar un enlace nuevo. */
    val autoFetchMetadata: Boolean = true,
    /** Quitar parámetros de seguimiento (utm_*, fbclid…) al guardar. */
    val stripTracking: Boolean = true,
    /** Al abrir un marcador de "Leer más tarde", sacarlo de esa lista. */
    val readLaterAutoRemove: Boolean = true,
    val autoPurgeTrash: Boolean = true,
    val appLock: Boolean = false,
    /** Copia de seguridad automática diaria en [autoBackupFolder]. */
    val autoBackup: Boolean = false,
    /** Carpeta elegida por el usuario (URI de árbol SAF con permiso persistente). */
    val autoBackupFolder: String? = null,
    /** Momento de la última copia automática correcta (0 si nunca). */
    val lastAutoBackup: Long = 0L,
    /** La última copia automática falló (p. ej. la carpeta ya no existe). */
    val autoBackupFailed: Boolean = false
)

/**
 * Preferencias no sensibles en SharedPreferences normales, expuestas como [StateFlow]
 * para que la interfaz reaccione al instante. Comparte fichero con el idioma.
 */
class AppSettings private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(read())
    val state: StateFlow<AppSettingsState> = _state.asStateFlow()

    private fun read() = AppSettingsState(
        layoutMode = enumOrDefault(prefs.getString(KEY_LAYOUT, null), LayoutMode.LIST),
        sortOrder = enumOrDefault(prefs.getString(KEY_SORT, null), SortOrder.CREATED_DESC),
        themeMode = enumOrDefault(prefs.getString(KEY_THEME, null), ThemeMode.SYSTEM),
        amoled = prefs.getBoolean(KEY_AMOLED, false),
        tapAction = enumOrDefault(prefs.getString(KEY_TAP_ACTION, null), TapAction.OPEN_LINK),
        openMode = enumOrDefault(prefs.getString(KEY_OPEN_MODE, null), OpenMode.IN_APP_TAB),
        loadPreviews = prefs.getBoolean(KEY_LOAD_PREVIEWS, true),
        autoFetchMetadata = prefs.getBoolean(KEY_AUTO_FETCH, true),
        stripTracking = prefs.getBoolean(KEY_STRIP_TRACKING, true),
        readLaterAutoRemove = prefs.getBoolean(KEY_READ_LATER_AUTO_REMOVE, true),
        autoPurgeTrash = prefs.getBoolean(KEY_AUTO_PURGE, true),
        appLock = prefs.getBoolean(KEY_APP_LOCK, false),
        autoBackup = prefs.getBoolean(KEY_AUTO_BACKUP, false),
        autoBackupFolder = prefs.getString(KEY_AUTO_BACKUP_FOLDER, null),
        lastAutoBackup = prefs.getLong(KEY_LAST_AUTO_BACKUP, 0L),
        autoBackupFailed = prefs.getBoolean(KEY_AUTO_BACKUP_FAILED, false)
    )

    private fun update(block: SharedPreferences.Editor.() -> Unit) {
        prefs.edit(action = block)
        _state.value = read()
    }

    fun setLayoutMode(mode: LayoutMode) = update { putString(KEY_LAYOUT, mode.name) }
    fun setSortOrder(order: SortOrder) = update { putString(KEY_SORT, order.name) }
    fun setThemeMode(mode: ThemeMode) = update { putString(KEY_THEME, mode.name) }
    fun setAmoled(enabled: Boolean) = update { putBoolean(KEY_AMOLED, enabled) }
    fun setTapAction(action: TapAction) = update { putString(KEY_TAP_ACTION, action.name) }
    fun setOpenMode(mode: OpenMode) = update { putString(KEY_OPEN_MODE, mode.name) }
    fun setLoadPreviews(enabled: Boolean) = update { putBoolean(KEY_LOAD_PREVIEWS, enabled) }
    fun setAutoFetchMetadata(enabled: Boolean) = update { putBoolean(KEY_AUTO_FETCH, enabled) }
    fun setStripTracking(enabled: Boolean) = update { putBoolean(KEY_STRIP_TRACKING, enabled) }
    fun setReadLaterAutoRemove(enabled: Boolean) = update { putBoolean(KEY_READ_LATER_AUTO_REMOVE, enabled) }
    fun setAutoPurgeTrash(enabled: Boolean) = update { putBoolean(KEY_AUTO_PURGE, enabled) }
    fun setAppLock(enabled: Boolean) = update { putBoolean(KEY_APP_LOCK, enabled) }
    fun setAutoBackup(enabled: Boolean) = update { putBoolean(KEY_AUTO_BACKUP, enabled) }
    fun setAutoBackupFolder(uri: String?) = update {
        putString(KEY_AUTO_BACKUP_FOLDER, uri)
        putBoolean(KEY_AUTO_BACKUP_FAILED, false)
    }
    fun setAutoBackupResult(success: Boolean, time: Long = System.currentTimeMillis()) = update {
        if (success) putLong(KEY_LAST_AUTO_BACKUP, time)
        putBoolean(KEY_AUTO_BACKUP_FAILED, !success)
    }

    companion object {
        private const val PREFS = "mismarcadores_settings"
        private const val KEY_LAYOUT = "layout_mode"
        private const val KEY_SORT = "sort_order"
        private const val KEY_THEME = "theme_mode"
        private const val KEY_AMOLED = "amoled"
        private const val KEY_TAP_ACTION = "tap_action"
        private const val KEY_OPEN_MODE = "open_mode"
        private const val KEY_LOAD_PREVIEWS = "load_previews"
        private const val KEY_AUTO_FETCH = "auto_fetch_metadata"
        private const val KEY_STRIP_TRACKING = "strip_tracking"
        private const val KEY_READ_LATER_AUTO_REMOVE = "read_later_auto_remove"
        private const val KEY_AUTO_PURGE = "auto_purge_trash"
        private const val KEY_APP_LOCK = "app_lock"
        private const val KEY_AUTO_BACKUP = "auto_backup"
        private const val KEY_AUTO_BACKUP_FOLDER = "auto_backup_folder"
        private const val KEY_LAST_AUTO_BACKUP = "last_auto_backup"
        private const val KEY_AUTO_BACKUP_FAILED = "auto_backup_failed"

        /** Días que un marcador permanece en la papelera antes del vaciado automático. */
        const val TRASH_RETENTION_DAYS = 30

        @Volatile
        private var INSTANCE: AppSettings? = null

        fun getInstance(context: Context): AppSettings =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: AppSettings(context).also { INSTANCE = it }
            }

        private inline fun <reified E : Enum<E>> enumOrDefault(name: String?, default: E): E =
            name?.let { n -> enumValues<E>().firstOrNull { it.name == n } } ?: default
    }
}
