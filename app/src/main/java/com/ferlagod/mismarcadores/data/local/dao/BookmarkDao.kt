// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Marcadores — https://github.com/MisApps-labs/mis_marcadores

package com.ferlagod.mismarcadores.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.ferlagod.mismarcadores.data.local.entity.BookmarkEntity
import com.ferlagod.mismarcadores.data.local.model.SyncStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface BookmarkDao {

    @Query("SELECT * FROM bookmarks WHERE isDeleted = 0 ORDER BY createdAt DESC")
    fun getActiveFlow(): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM bookmarks WHERE isDeleted = 1 AND syncStatus != 'PENDING_DELETE' ORDER BY updatedAt DESC")
    fun getTrashFlow(): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM bookmarks WHERE id = :id LIMIT 1")
    fun getByIdFlow(id: String): Flow<BookmarkEntity?>

    @Query("SELECT * FROM bookmarks WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): BookmarkEntity?

    /** Todas las filas, incluidas papelera y borrados pendientes (uso interno de sincronización). */
    @Query("SELECT * FROM bookmarks")
    suspend fun getAll(): List<BookmarkEntity>

    @Query("SELECT * FROM bookmarks WHERE isDeleted = 0 AND syncStatus != 'PENDING_DELETE'")
    suspend fun getLive(): List<BookmarkEntity>

    @Query("SELECT * FROM bookmarks WHERE syncStatus = :syncStatus")
    suspend fun getBySyncStatus(syncStatus: SyncStatus): List<BookmarkEntity>

    @Upsert
    suspend fun upsert(bookmark: BookmarkEntity)

    @Upsert
    suspend fun upsertAll(bookmarks: List<BookmarkEntity>)

    @Query("UPDATE bookmarks SET isFavorite = :value, updatedAt = :updatedAt, syncStatus = 'PENDING_UPLOAD' WHERE id IN (:ids) AND isFavorite != :value AND syncStatus != 'PENDING_DELETE'")
    suspend fun setFavorite(ids: List<String>, value: Boolean, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE bookmarks SET isReadLater = :value, updatedAt = :updatedAt, syncStatus = 'PENDING_UPLOAD' WHERE id IN (:ids) AND isReadLater != :value AND syncStatus != 'PENDING_DELETE'")
    suspend fun setReadLater(ids: List<String>, value: Boolean, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE bookmarks SET color = :color, updatedAt = :updatedAt, syncStatus = 'PENDING_UPLOAD' WHERE id IN (:ids) AND color != :color AND syncStatus != 'PENDING_DELETE'")
    suspend fun setColor(ids: List<String>, color: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE bookmarks SET folderId = :folderId, updatedAt = :updatedAt, syncStatus = 'PENDING_UPLOAD' WHERE id IN (:ids) AND (folderId IS NOT :folderId) AND syncStatus != 'PENDING_DELETE'")
    suspend fun moveToFolder(ids: List<String>, folderId: String?, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE bookmarks SET tags = :tags, updatedAt = :updatedAt, syncStatus = 'PENDING_UPLOAD' WHERE id = :id AND syncStatus != 'PENDING_DELETE'")
    suspend fun setTags(id: String, tags: String, updatedAt: Long = System.currentTimeMillis())

    /** Estadística local: no cambia la fecha de modificación ni provoca subidas. */
    @Query("UPDATE bookmarks SET openCount = openCount + 1, lastOpenedAt = :now WHERE id = :id")
    suspend fun registerOpen(id: String, now: Long = System.currentTimeMillis())

    /** Resultado de la comprobación del enlace (solo local). */
    @Query("UPDATE bookmarks SET linkStatus = :status, linkCheckedAt = :now WHERE id = :id")
    suspend fun setLinkStatus(id: String, status: Int, now: Long = System.currentTimeMillis())

    /** Marcadores en la papelera desde antes de [before] (para el vaciado automático). */
    @Query("SELECT id FROM bookmarks WHERE isDeleted = 1 AND syncStatus != 'PENDING_DELETE' AND updatedAt < :before")
    suspend fun getTrashIdsOlderThan(before: Long): List<String>

    @Query("UPDATE bookmarks SET isDeleted = 1, syncStatus = 'PENDING_UPLOAD', updatedAt = :updatedAt WHERE id = :id")
    suspend fun moveToTrash(id: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE bookmarks SET isDeleted = 0, syncStatus = 'PENDING_UPLOAD', updatedAt = :updatedAt WHERE id = :id")
    suspend fun restoreFromTrash(id: String, updatedAt: Long = System.currentTimeMillis())

    /** Marca para borrado definitivo; la sincronización subirá una lápida y eliminará la fila. */
    @Query("UPDATE bookmarks SET isDeleted = 1, syncStatus = 'PENDING_DELETE', updatedAt = :updatedAt WHERE id = :id")
    suspend fun markForPurge(id: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE bookmarks SET syncStatus = 'PENDING_DELETE', updatedAt = :updatedAt WHERE isDeleted = 1")
    suspend fun markTrashForPurge(updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM bookmarks WHERE syncStatus = 'PENDING_DELETE'")
    suspend fun deletePendingPurge()

    @Query("DELETE FROM bookmarks WHERE isDeleted = 1")
    suspend fun deleteTrashPermanently()

    /** Saca los marcadores de una carpeta eliminada y los deja sin carpeta. */
    @Query("UPDATE bookmarks SET folderId = NULL, updatedAt = :updatedAt, syncStatus = 'PENDING_UPLOAD' WHERE folderId = :folderId AND syncStatus != 'PENDING_DELETE'")
    suspend fun detachFromFolder(folderId: String, updatedAt: Long = System.currentTimeMillis())

    /** Marca como sincronizada la versión subida, solo si no ha cambiado mientras se subía. */
    @Query("UPDATE bookmarks SET syncStatus = 'SYNCED', remoteEtag = :etag WHERE id = :id AND updatedAt = :updatedAt AND syncStatus = 'PENDING_UPLOAD'")
    suspend fun markSynced(id: String, updatedAt: Long, etag: String?): Int

    @Query("UPDATE bookmarks SET remoteEtag = :etag WHERE id = :id")
    suspend fun setRemoteEtag(id: String, etag: String?)

    /** Desapareció del servidor: a la papelera y pendiente de volver a subirse. */
    @Query(
        "UPDATE bookmarks SET isDeleted = 1, syncStatus = 'PENDING_UPLOAD', remoteEtag = NULL, " +
            "updatedAt = CASE WHEN isDeleted = 1 THEN updatedAt ELSE :now END WHERE id = :id"
    )
    suspend fun trashForReupload(id: String, now: Long = System.currentTimeMillis())

    @Query("UPDATE bookmarks SET syncStatus = :status WHERE id = :id")
    suspend fun updateSyncStatus(id: String, status: SyncStatus)

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun deletePermanently(id: String)

    /** Olvida el estado remoto (al conectar una cuenta o restaurar la bóveda): todo pasa a pendiente. */
    @Query(
        "UPDATE bookmarks SET remoteEtag = NULL, " +
            "syncStatus = CASE WHEN syncStatus = 'SYNCED' THEN 'PENDING_UPLOAD' ELSE syncStatus END"
    )
    suspend fun resetSyncMetadata()

    @Query("SELECT COUNT(*) FROM bookmarks WHERE isDeleted = 0 AND syncStatus != 'PENDING_DELETE'")
    suspend fun countLive(): Int
}
