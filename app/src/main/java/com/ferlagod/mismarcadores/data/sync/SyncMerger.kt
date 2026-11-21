// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Marcadores — https://github.com/ferlagod/mis_marcadores

package com.ferlagod.mismarcadores.data.sync

import com.ferlagod.mismarcadores.data.local.entity.BookmarkEntity
import com.ferlagod.mismarcadores.data.local.entity.FolderEntity
import com.ferlagod.mismarcadores.data.local.model.SyncStatus
import com.ferlagod.mismarcadores.data.local.model.Tags

/** Acción a aplicar en local tras comparar una entidad local con su versión remota. */
sealed interface MergeAction<out T> {
    /** No hay nada que hacer. */
    data object Skip : MergeAction<Nothing>

    /** Borrar la fila local. */
    data object DeleteLocal : MergeAction<Nothing>

    /** Guardar la versión remota como sincronizada. */
    data class SaveRemote<T>(val entity: T) : MergeAction<T>

    /** Conservar los cambios locales pendientes y recordar el ETag remoto actual. */
    data class KeepLocal(val etag: String?) : MergeAction<Nothing>

    /** Guardar [entity] (fusión de ambas versiones) pendiente de subir con el ETag remoto. */
    data class SaveMerged<T>(val entity: T) : MergeAction<T>
}

/** Acción cuando una entidad local ya no existe en el servidor. */
enum class MissingRemoteAction {
    NOTHING,
    DELETE_LOCAL,
    MARK_FOR_UPLOAD,
    FORGET_ETAG,
    /** Marcador: pasa a la papelera local y se vuelve a subir (como marcador en papelera). */
    TRASH_AND_REUPLOAD
}

/**
 * Reglas de fusión de la sincronización. Son funciones puras para poder probarlas.
 *
 * Principio: nunca perder información. Si un marcador cambió a la vez en este dispositivo y en
 * otro, gana la versión más reciente campo a campo, pero las etiquetas se unen y, si las notas
 * personales son distintas, se conservan las dos.
 */
object SyncMerger {

    const val NOTES_SEPARATOR = "\n\n———\n\n"

    fun resolveBookmark(
        local: BookmarkEntity?,
        remote: RemoteRecord<BookmarkEntity>,
        etag: String?
    ): MergeAction<BookmarkEntity> {
        when (remote) {
            is RemoteRecord.Tombstone -> return when {
                local == null -> MergeAction.Skip
                // Cambios locales sin subir: se conservan y resucitan el marcador en el servidor.
                local.syncStatus == SyncStatus.PENDING_UPLOAD -> MergeAction.KeepLocal(etag)
                // Borrado definitivo hecho en otro dispositivo.
                else -> MergeAction.DeleteLocal
            }

            is RemoteRecord.Live -> {
                val remoteBookmark = remote.entity.copy(syncStatus = SyncStatus.SYNCED, remoteEtag = etag)
                    .withLocalStatsFrom(local)
                if (local == null) return MergeAction.SaveRemote(remoteBookmark)

                return when (local.syncStatus) {
                    SyncStatus.SYNCED -> MergeAction.SaveRemote(remoteBookmark)

                    SyncStatus.PENDING_DELETE ->
                        if (remoteBookmark.updatedAt > local.updatedAt) MergeAction.SaveRemote(remoteBookmark)
                        else MergeAction.KeepLocal(etag)

                    SyncStatus.PENDING_UPLOAD -> when {
                        sameContent(local, remoteBookmark) -> MergeAction.KeepLocal(etag)
                        else -> MergeAction.SaveMerged(merge(local, remoteBookmark).copy(remoteEtag = etag))
                    }
                }
            }
        }
    }

    /**
     * Fusiona dos versiones de un marcador: los campos simples los decide la más reciente; las
     * etiquetas se unen y las notas distintas se conservan juntas. El resultado queda pendiente
     * de subir (con la fecha más reciente de las dos) para que el otro dispositivo lo reciba.
     */
    fun merge(local: BookmarkEntity, remote: BookmarkEntity): BookmarkEntity {
        val (newer, older) = if (remote.updatedAt > local.updatedAt) remote to local else local to remote
        val notes = when {
            newer.notes.trim() == older.notes.trim() || older.notes.isBlank() -> newer.notes
            newer.notes.isBlank() -> older.notes
            newer.notes.contains(older.notes.trim()) -> newer.notes
            else -> newer.notes.trimEnd() + NOTES_SEPARATOR + older.notes.trim()
        }
        return newer.copy(
            id = local.id,
            tags = Tags.union(newer.tags, older.tags),
            notes = notes,
            title = newer.title.ifBlank { older.title },
            description = newer.description.ifBlank { older.description },
            imageUrl = newer.imageUrl ?: older.imageUrl,
            faviconUrl = newer.faviconUrl ?: older.faviconUrl,
            createdAt = minOf(local.createdAt, remote.createdAt),
            updatedAt = maxOf(local.updatedAt, remote.updatedAt),
            syncStatus = SyncStatus.PENDING_UPLOAD,
            openCount = local.openCount,
            lastOpenedAt = local.lastOpenedAt,
            linkStatus = if (newer.url == local.url) local.linkStatus else 0,
            linkCheckedAt = if (newer.url == local.url) local.linkCheckedAt else 0L
        )
    }

    fun resolveFolder(
        local: FolderEntity?,
        remote: RemoteRecord<FolderEntity>,
        etag: String?
    ): MergeAction<FolderEntity> {
        when (remote) {
            is RemoteRecord.Tombstone -> return when {
                local == null -> MergeAction.Skip
                local.syncStatus == SyncStatus.PENDING_UPLOAD -> MergeAction.KeepLocal(etag)
                else -> MergeAction.DeleteLocal
            }

            is RemoteRecord.Live -> {
                val remoteFolder = remote.entity.copy(syncStatus = SyncStatus.SYNCED, remoteEtag = etag)
                if (local == null || local.syncStatus == SyncStatus.SYNCED) {
                    return MergeAction.SaveRemote(remoteFolder)
                }
                // Carpetas: gana la última modificación.
                return if (remoteFolder.updatedAt > local.updatedAt) MergeAction.SaveRemote(remoteFolder)
                else MergeAction.KeepLocal(etag)
            }
        }
    }

    fun resolveMissingRemote(syncStatus: SyncStatus, remoteEtag: String?): MissingRemoteAction = when (syncStatus) {
        // Ya estaba en el servidor y ha desaparecido: otro dispositivo (o el usuario) lo borró.
        SyncStatus.SYNCED -> if (remoteEtag != null) MissingRemoteAction.DELETE_LOCAL else MissingRemoteAction.MARK_FOR_UPLOAD
        // Hay cambios locales: se vuelve a crear en el servidor.
        SyncStatus.PENDING_UPLOAD -> if (remoteEtag != null) MissingRemoteAction.FORGET_ETAG else MissingRemoteAction.NOTHING
        SyncStatus.PENDING_DELETE -> MissingRemoteAction.DELETE_LOCAL
    }

    /**
     * Como [resolveMissingRemote], pero para marcadores: si uno sincronizado desaparece del
     * servidor sin lápida no se borra, sino que va a la papelera y se vuelve a subir.
     */
    fun resolveMissingRemoteBookmark(syncStatus: SyncStatus, remoteEtag: String?): MissingRemoteAction =
        when (val action = resolveMissingRemote(syncStatus, remoteEtag)) {
            MissingRemoteAction.DELETE_LOCAL ->
                if (syncStatus == SyncStatus.PENDING_DELETE) action else MissingRemoteAction.TRASH_AND_REUPLOAD
            else -> action
        }

    /** Por debajo de este número de marcadores retirados en una ronda nunca se frena. */
    const val MASS_REMOVAL_MIN = 5

    /** A partir de este número de marcadores retirados en una ronda siempre se frena. */
    const val MASS_REMOVAL_ABSOLUTE = 50

    /**
     * Freno de seguridad: ¿retirar [removed] marcadores visibles de los [live] que hay en el
     * dispositivo es tan anómalo que conviene no aplicarlo? En bloque suele indicar una carpeta
     * remota equivocada, vaciada o restaurada; en ese caso se conservan y se vuelven a subir.
     */
    fun isMassRemoval(removed: Int, live: Int): Boolean =
        removed >= MASS_REMOVAL_MIN && (removed >= MASS_REMOVAL_ABSOLUTE || removed * 4 >= live)

    /**
     * Freno para carpetas: además de la regla de [isMassRemoval], se frena siempre que hayan
     * desaparecido carpetas y en el servidor no quede ninguna (carpeta remota vaciada o
     * cambiada), aunque sean pocas. La app nunca borra ficheros de carpetas vivas: las borra
     * con lápidas, así que un fichero que desaparece es siempre anómalo.
     */
    fun isMassFolderRemoval(removed: Int, live: Int, remoteFolderFiles: Int): Boolean =
        removed > 0 && (isMassRemoval(removed, live) || remoteFolderFiles == 0)

    fun sameContent(a: BookmarkEntity, b: BookmarkEntity): Boolean =
        a.url == b.url &&
            a.title == b.title &&
            a.description == b.description &&
            a.notes == b.notes &&
            Tags.parse(a.tags) == Tags.parse(b.tags) &&
            a.imageUrl == b.imageUrl &&
            a.faviconUrl == b.faviconUrl &&
            a.folderId == b.folderId &&
            a.color == b.color &&
            a.isFavorite == b.isFavorite &&
            a.isReadLater == b.isReadLater &&
            a.isDeleted == b.isDeleted
}
