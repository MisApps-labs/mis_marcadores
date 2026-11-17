// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Marcadores — https://github.com/ferlagod/mis_marcadores

@file:Suppress("DEPRECATION") // security-crypto está deprecado pero no hay sustituto estable equivalente

package com.ferlagod.mismarcadores.data.local.pref

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Credenciales de Nextcloud / ownCloud.
 */
data class NextcloudCredentials(
    val serverUrl: String,
    val username: String,
    val appPassword: String,
    /**
     * Identificador interno del usuario en Nextcloud, que es el que aparece en la ruta WebDAV.
     * Puede no coincidir con [username] (inicio de sesión con correo, LDAP…). Null si no se conoce.
     */
    val userId: String? = null
)

/**
 * Almacén seguro (EncryptedSharedPreferences, clave en Android Keystore) para las credenciales
 * de Nextcloud.
 */
class AuthPreferences private constructor(context: Context) {

    private val prefs: SharedPreferences = createPrefs(context.applicationContext)

    companion object {
        private const val TAG = "AuthPreferences"
        private const val PREFS_FILENAME = "mismarcadores_secure_auth_prefs"
        private const val KEY_SERVER_URL = "key_server_url"
        private const val KEY_USERNAME = "key_username"
        private const val KEY_APP_PASSWORD = "key_app_password"
        private const val KEY_USER_ID = "key_user_id"
        /** Claves de la antigua bóveda cifrada; solo se leen para migrar. */
        private val LEGACY_VAULT_KEYS = listOf("key_salt_base64", "key_kdf_iterations", "key_check", "key_derived_key_base64")
        private const val KEY_LAST_SYNC = "key_last_sync"
        const val DEFAULT_FOLDER_NAME = "MisMarcadores"

        @Volatile
        private var INSTANCE: AuthPreferences? = null

        fun getInstance(context: Context): AuthPreferences =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: AuthPreferences(context).also { INSTANCE = it }
            }

        /**
         * Si el Keystore se ha perdido (p. ej. tras restaurar una copia de seguridad en otro
         * dispositivo), el fichero cifrado es ilegible: se borra y se crea de nuevo.
         */
        private fun createPrefs(context: Context): SharedPreferences {
            fun build(): SharedPreferences {
                val masterKey = MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
                return EncryptedSharedPreferences.create(
                    context,
                    PREFS_FILENAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
            }
            return try {
                build()
            } catch (e: Exception) {
                Log.w(TAG, "Preferencias seguras ilegibles; se reinician", e)
                context.deleteSharedPreferences(PREFS_FILENAME)
                build()
            }
        }
    }

    // ------------------------------------------------------------------
    // Credenciales
    // ------------------------------------------------------------------

    fun saveCredentials(credentials: NextcloudCredentials) {
        prefs.edit {
            putString(KEY_SERVER_URL, credentials.serverUrl.trim().trimEnd('/'))
            putString(KEY_USERNAME, credentials.username.trim())
            putString(KEY_APP_PASSWORD, credentials.appPassword.trim())
            val userId = credentials.userId?.trim()?.takeIf { it.isNotEmpty() }
            if (userId != null) putString(KEY_USER_ID, userId) else remove(KEY_USER_ID)
        }
    }

    fun getCredentials(): NextcloudCredentials? {
        val server = prefs.getString(KEY_SERVER_URL, null)?.takeIf { it.isNotBlank() } ?: return null
        val user = prefs.getString(KEY_USERNAME, null)?.takeIf { it.isNotBlank() } ?: return null
        val pass = prefs.getString(KEY_APP_PASSWORD, null)?.takeIf { it.isNotBlank() } ?: return null
        val userId = prefs.getString(KEY_USER_ID, null)?.takeIf { it.isNotBlank() }
        return NextcloudCredentials(server, user, pass, userId)
    }

    fun hasCredentials(): Boolean = getCredentials() != null

    fun clearCredentials() {
        prefs.edit {
            remove(KEY_SERVER_URL)
            remove(KEY_USERNAME)
            remove(KEY_APP_PASSWORD)
            remove(KEY_USER_ID)
            remove(KEY_LAST_SYNC)
        }
    }

    // ------------------------------------------------------------------
    // Migración desde la versión con cifrado de extremo a extremo
    // ------------------------------------------------------------------

    /**
     * true si el dispositivo viene de la versión con cifrado y tiene que volver a subir todo en
     * claro. Tras hacerlo se llama a [clearLegacyVault].
     */
    fun hasLegacyVault(): Boolean = LEGACY_VAULT_KEYS.any { prefs.contains(it) }

    /** Borra los restos de la antigua bóveda (salt, verificador y clave derivada). */
    fun clearLegacyVault() {
        prefs.edit { LEGACY_VAULT_KEYS.forEach { remove(it) } }
    }

    // ------------------------------------------------------------------
    // Estado
    // ------------------------------------------------------------------

    fun setLastSync(timestamp: Long) {
        prefs.edit { putLong(KEY_LAST_SYNC, timestamp) }
    }

    fun getLastSync(): Long? = prefs.getLong(KEY_LAST_SYNC, 0L).takeIf { it > 0L }

    /**
     * URL WebDAV de la carpeta de la app:
     * https://servidor/remote.php/dav/files/<id de usuario>/MisMarcadores/
     */
    fun getWebDavRootUrl(): String? = getCredentials()?.let { buildWebDavRootUrl(it) }

    fun buildWebDavRootUrl(creds: NextcloudCredentials): String {
        val rawServer = creds.serverUrl.trim().trimEnd('/')
        if (rawServer.contains("/remote.php/dav/files/")) return "$rawServer/"
        val user = java.net.URLEncoder.encode(creds.userId ?: creds.username, "UTF-8").replace("+", "%20")
        return "$rawServer/remote.php/dav/files/$user/$DEFAULT_FOLDER_NAME/"
    }

    fun clearAll() {
        prefs.edit { clear() }
    }
}
