// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Marcadores — https://github.com/ferlagod/mis_marcadores

package com.ferlagod.mismarcadores.data.sync

import com.ferlagod.mismarcadores.data.local.entity.BookmarkEntity
import com.ferlagod.mismarcadores.data.local.entity.FolderEntity
import com.ferlagod.mismarcadores.data.local.model.SyncStatus
import com.ferlagod.mismarcadores.data.local.model.Tags
import org.json.JSONArray
import org.json.JSONObject

/** Registro remoto descodificado: o bien la entidad viva, o bien una lápida de borrado. */
sealed interface RemoteRecord<out T> {
    data class Live<T>(val entity: T) : RemoteRecord<T>
    data class Tombstone(val id: String, val deletedAt: Long) : RemoteRecord<Nothing>
}

/**
 * Fichero de la versión 1 (cifrado de extremo a extremo, ya retirado). No se puede leer:
 * si el dispositivo tiene ese elemento, lo vuelve a subir en claro encima.
 */
class LegacyEncryptedFileException : IllegalArgumentException("Fichero cifrado de la versión 1")

/**
 * Formato de los ficheros en Nextcloud (ver docs/SYNC_FORMAT.md):
 * ```
 * { "format": "mismarcadores", "version": 2, "type": "bookmark", "id": "<uuid>", "data": { … } }
 * { "format": "mismarcadores", "version": 2, "type": "bookmark", "id": "<uuid>", "deleted": true, "deletedAt": 0 }
 * ```
 */
object RemoteCodec {

    const val FORMAT = "mismarcadores"
    const val VERSION = 2
    /** Versión 1: el contenido iba cifrado en `payload`. */
    private const val LEGACY_ENCRYPTED_VERSION = 1
    const val TYPE_BOOKMARK = "bookmark"
    const val TYPE_FOLDER = "folder"

    // ------------------------------------------------------------------
    // Marcadores
    // ------------------------------------------------------------------

    fun encodeBookmark(bookmark: BookmarkEntity): String {
        val data = JSONObject().apply {
            put("folderId", bookmark.folderId ?: JSONObject.NULL)
            put("url", bookmark.url)
            put("title", bookmark.title)
            put("description", bookmark.description)
            put("notes", bookmark.notes)
            put("tags", JSONArray(bookmark.tagList))
            put("imageUrl", bookmark.imageUrl ?: JSONObject.NULL)
            put("faviconUrl", bookmark.faviconUrl ?: JSONObject.NULL)
            put("color", bookmark.color)
            put("isFavorite", bookmark.isFavorite)
            put("isReadLater", bookmark.isReadLater)
            put("createdAt", bookmark.createdAt)
            put("updatedAt", bookmark.updatedAt)
            put("isTrashed", bookmark.isDeleted)
        }
        return envelope(TYPE_BOOKMARK, bookmark.id).put("data", data).toString()
    }

    fun decodeBookmark(raw: String, expectedId: String): RemoteRecord<BookmarkEntity> {
        val json = JSONObject(raw)
        val id = checkEnvelope(json, TYPE_BOOKMARK, expectedId)
        tombstoneOrNull(json, id)?.let { return it }

        val data = json.getJSONObject("data")
        val url = data.getString("url")
        require(url.isNotBlank()) { "Marcador sin dirección" }
        val tagsArray = data.optJSONArray("tags")
        val tags = if (tagsArray == null) emptyList() else List(tagsArray.length()) { tagsArray.optString(it) }
        val updatedAt = data.optLong("updatedAt", 0L)
        return RemoteRecord.Live(
            BookmarkEntity(
                id = id,
                folderId = data.optStringOrNull("folderId"),
                url = url,
                title = data.optString("title", ""),
                description = data.optString("description", ""),
                notes = data.optString("notes", ""),
                tags = Tags.join(tags),
                imageUrl = data.optStringOrNull("imageUrl"),
                faviconUrl = data.optStringOrNull("faviconUrl"),
                color = data.optString("color", "DEFAULT"),
                isFavorite = data.optBoolean("isFavorite", false),
                isReadLater = data.optBoolean("isReadLater", false),
                createdAt = data.optLong("createdAt", updatedAt),
                updatedAt = updatedAt,
                isDeleted = data.optBoolean("isTrashed", false),
                syncStatus = SyncStatus.SYNCED
            )
        )
    }

    // ------------------------------------------------------------------
    // Carpetas
    // ------------------------------------------------------------------

    fun encodeFolder(folder: FolderEntity): String {
        val data = JSONObject().apply {
            put("parentId", folder.parentId ?: JSONObject.NULL)
            put("name", folder.name)
            put("color", folder.color)
            put("updatedAt", folder.updatedAt)
        }
        return envelope(TYPE_FOLDER, folder.id).put("data", data).toString()
    }

    fun decodeFolder(raw: String, expectedId: String): RemoteRecord<FolderEntity> {
        val json = JSONObject(raw)
        val id = checkEnvelope(json, TYPE_FOLDER, expectedId)
        tombstoneOrNull(json, id)?.let { return it }

        val data = json.getJSONObject("data")
        return RemoteRecord.Live(
            FolderEntity(
                id = id,
                parentId = data.optStringOrNull("parentId"),
                name = data.optString("name", ""),
                color = data.optString("color", "DEFAULT"),
                updatedAt = data.optLong("updatedAt", 0L),
                isDeleted = false,
                syncStatus = SyncStatus.SYNCED
            )
        )
    }

    // ------------------------------------------------------------------
    // Lápidas
    // ------------------------------------------------------------------

    /** Lápida de borrado definitivo. */
    fun encodeTombstone(type: String, id: String, deletedAt: Long): String =
        envelope(type, id)
            .put("deleted", true)
            .put("deletedAt", deletedAt)
            .toString()

    // ------------------------------------------------------------------

    private fun envelope(type: String, id: String) = JSONObject().apply {
        put("format", FORMAT)
        put("version", VERSION)
        put("type", type)
        put("id", id)
    }

    private fun checkEnvelope(json: JSONObject, type: String, expectedId: String): String {
        require(json.optString("format") == FORMAT) { "Formato desconocido" }
        val version = json.optInt("version", 0)
        require(version <= VERSION) {
            "Fichero creado con una versión más nueva de Mis Marcadores; actualiza la app"
        }
        require(json.optString("type") == type) { "Tipo inesperado" }
        val id = json.getString("id")
        require(id == expectedId) { "El id del fichero no coincide con su nombre" }
        if (version <= LEGACY_ENCRYPTED_VERSION && json.has("payload")) throw LegacyEncryptedFileException()
        return id
    }

    private fun tombstoneOrNull(json: JSONObject, id: String): RemoteRecord.Tombstone? {
        if (!json.optBoolean("deleted", false)) return null
        return RemoteRecord.Tombstone(id, json.optLong("deletedAt", 0L))
    }

    private fun JSONObject.optStringOrNull(name: String): String? =
        if (!has(name) || isNull(name)) null else getString(name).takeIf { it.isNotBlank() }
}
