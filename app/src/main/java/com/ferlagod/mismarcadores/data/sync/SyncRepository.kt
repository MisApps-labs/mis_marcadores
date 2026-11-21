// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Marcadores — https://github.com/ferlagod/mis_marcadores

package com.ferlagod.mismarcadores.data.sync

import android.content.Context
import android.util.Log
import androidx.annotation.StringRes
import androidx.room.withTransaction
import com.ferlagod.mismarcadores.R
import com.ferlagod.mismarcadores.data.backup.BackupManager
import com.ferlagod.mismarcadores.data.local.AppDatabase
import com.ferlagod.mismarcadores.data.local.entity.BookmarkEntity
import com.ferlagod.mismarcadores.data.local.entity.RemoteTombstoneEntity
import com.ferlagod.mismarcadores.data.local.model.SyncStatus
import com.ferlagod.mismarcadores.data.local.pref.AuthPreferences
import com.ferlagod.mismarcadores.data.local.pref.NextcloudCredentials
import com.ferlagod.mismarcadores.data.remote.webdav.RemoteFileMeta
import com.ferlagod.mismarcadores.data.remote.webdav.WebDavAuthException
import com.ferlagod.mismarcadores.data.remote.webdav.WebDavClient
import com.ferlagod.mismarcadores.data.remote.webdav.WebDavException
import com.ferlagod.mismarcadores.data.remote.webdav.WebDavPreconditionFailedException
import com.ferlagod.mismarcadores.util.LanguageManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

enum class SyncStage(@param:StringRes val labelRes: Int) {
    CONNECTING(R.string.sync_stage_connecting),
    DOWNLOADING(R.string.sync_stage_downloading),
    UPLOADING(R.string.sync_stage_uploading)
}

sealed interface SyncState {
    data object Idle : SyncState
    data class Syncing(val stage: SyncStage = SyncStage.CONNECTING) : SyncState
    /**
     * @param warnings ficheros remotos que no se pudieron leer.
     * @param protectedItems carpetas y marcadores que el servidor quería retirar en bloque y se han conservado
     *   (ver [SyncMerger.isMassRemoval]).
     */
    data class Success(
        val timestamp: Long = System.currentTimeMillis(),
        val warnings: Int = 0,
        val protectedItems: Int = 0
    ) : SyncState
    data class Error(val message: String, val cause: Throwable? = null) : SyncState
}

/**
 * Sincronización bidireccional local-first con Nextcloud (WebDAV).
 *
 * Cada ronda:
 * 1. Comprueba las credenciales y que existe la carpeta remota.
 * 2. Lista los ficheros remotos con sus ETags.
 * 3. Descarga lo que cambió en remoto y lo fusiona (ver [SyncMerger]).
 * 4. Sube carpetas y marcadores pendientes usando If-Match / If-None-Match.
 *
 * Es un singleton para que la app y el WorkManager compartan el mismo cerrojo.
 */
class SyncRepository private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val database = AppDatabase.getInstance(appContext)
    private val folderDao = database.folderDao()
    private val bookmarkDao = database.bookmarkDao()
    private val tombstoneDao = database.remoteTombstoneDao()

    val authPreferences = AuthPreferences.getInstance(appContext)

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    private val syncMutex = Mutex()

    /**
     * Ámbito propio de la app: una sincronización pedida desde la interfaz termina aunque se
     * cierre la pantalla (y su ViewModel) a mitad.
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** El servidor rechazó una subida (412): hay que fusionar en otra ronda. */
    @Volatile
    private var rerunRequested = false

    /** Alguien pidió sincronizar mientras otra operación tenía el cerrojo. */
    @Volatile
    private var pendingRequest = false
    private var warnings = 0
    private var protectedItems = 0

    companion object {
        private const val TAG = "SyncRepository"
        const val FOLDER_PREFIX = "folder_"
        const val BOOKMARK_PREFIX = "bookmark_"
        const val JSON_EXT = ".json"
        private const val MAX_ROUNDS = 3

        @Volatile
        private var INSTANCE: SyncRepository? = null

        fun getInstance(context: Context): SyncRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: SyncRepository(context).also { INSTANCE = it }
            }

        private fun bookmarkFile(id: String) = "$BOOKMARK_PREFIX$id$JSON_EXT"
        private fun folderFile(id: String) = "$FOLDER_PREFIX$id$JSON_EXT"
    }

    /** Traduce excepciones técnicas a mensajes comprensibles en el idioma de la app. */
    fun friendlyMessage(e: Throwable): String {
        val res = LanguageManager.localizedContext(appContext)
        return when (e) {
            is WebDavAuthException -> res.getString(R.string.err_auth)
            is UnknownHostException -> res.getString(R.string.err_unknown_host)
            is SocketTimeoutException -> res.getString(R.string.err_timeout)
            is SSLException -> res.getString(R.string.err_ssl)
            is WebDavException -> when (e.reason) {
                WebDavException.Reason.PATH_NOT_FOUND -> res.getString(R.string.err_path_not_found)
                WebDavException.Reason.INVALID_RESPONSE -> res.getString(R.string.err_invalid_response)
                WebDavException.Reason.HTTP -> res.getString(R.string.err_server, e.code)
            }
            is java.io.IOException -> res.getString(
                R.string.err_network,
                e.localizedMessage ?: res.getString(R.string.err_no_connection)
            )
            else -> e.localizedMessage ?: res.getString(R.string.err_unknown)
        }
    }

    /** Pide una sincronización en segundo plano, sin esperar a que termine. */
    fun requestSync() {
        scope.launch { synchronize() }
    }

    fun resetSyncState() {
        _syncState.value = SyncState.Idle
    }

    fun createClient(credentials: NextcloudCredentials): WebDavClient = WebDavClient(
        baseUrl = authPreferences.buildWebDavRootUrl(credentials),
        username = credentials.username,
        appPassword = credentials.appPassword
    )

    // ==================================================================
    // Conexión de cuenta
    // ==================================================================

    /**
     * Conecta una cuenta: valida credenciales y prepara los datos locales para subirse completos
     * en la primera sincronización.
     *
     * Antes de tocar nada guarda una copia de los marcadores en el almacenamiento de la app
     * (ver [BackupManager.exportSafetyCopy]).
     */
    suspend fun connectAccount(credentials: NextcloudCredentials): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                // La ruta WebDAV usa el id interno del usuario, que puede no ser el nombre con el
                // que inicia sesión (p. ej. el correo). Si el servidor no lo dice, se usa el nombre.
                val resolved = if (credentials.userId != null || "/remote.php/dav/files/" in credentials.serverUrl) credentials
                else credentials.copy(userId = WebDavClient.fetchUserId(credentials.serverUrl, credentials.username, credentials.appPassword))
                val client = createClient(resolved)
                client.ensureRootFolder()
                client.listRemoteFiles()

                try {
                    BackupManager(appContext).exportSafetyCopy()
                } catch (e: Exception) {
                    // La copia es una red de seguridad; si falla, la sincronización sigue protegida
                    // por las reglas de fusión y el freno de borrados masivos.
                    Log.w(TAG, "No se pudo guardar la copia previa a la sincronización", e)
                }

                syncMutex.withLock {
                    // Puede ser otra cuenta: se olvida el estado remoto previo.
                    database.withTransaction {
                        bookmarkDao.resetSyncMetadata()
                        folderDao.resetSyncMetadata()
                        tombstoneDao.clear()
                    }
                    authPreferences.saveCredentials(resolved)
                    authPreferences.clearLegacyVault()
                }
                servePendingRequest()
                Result.success(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "connectAccount", e)
                Result.failure(Exception(friendlyMessage(e), e))
            }
        }

    // ==================================================================
    // Sincronización
    // ==================================================================

    suspend fun synchronize(): Result<Unit> = withContext(Dispatchers.IO) {
        val credentials = authPreferences.getCredentials()
        if (credentials == null) {
            _syncState.value = SyncState.Idle
            return@withContext Result.success(Unit)
        }

        if (!tryAcquire()) return@withContext Result.success(Unit)

        try {
            if (authPreferences.hasLegacyVault()) {
                // Venía de la versión cifrada: se vuelve a subir todo en claro (ver downloadRecord).
                database.withTransaction {
                    bookmarkDao.resetSyncMetadata()
                    folderDao.resetSyncMetadata()
                    tombstoneDao.clear()
                }
                authPreferences.clearLegacyVault()
            }
            val client = createClient(credentials)
            warnings = 0
            protectedItems = 0
            var rounds = 0
            do {
                rerunRequested = false
                pendingRequest = false
                runRound(client)
                rounds++
            } while ((rerunRequested || pendingRequest) && rounds < MAX_ROUNDS)

            val now = System.currentTimeMillis()
            authPreferences.setLastSync(now)
            _syncState.value = SyncState.Success(now, warnings, protectedItems)
            Result.success(Unit)
        } catch (e: CancellationException) {
            // Cancelada desde fuera (WorkManager detuvo el trabajo…): no es un error que mostrar.
            _syncState.value = SyncState.Idle
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "synchronize", e)
            _syncState.value = SyncState.Error(friendlyMessage(e), e)
            Result.failure(e)
        } finally {
            syncMutex.unlock()
            servePendingRequest()
        }
    }

    /**
     * Toma el cerrojo de sincronización. Si está ocupado, deja apuntada la petición para que la
     * atienda quien lo tiene (en otra ronda o al soltarlo) y devuelve false.
     */
    private fun tryAcquire(): Boolean {
        if (syncMutex.tryLock()) return true
        pendingRequest = true
        // Quien tenía el cerrojo pudo soltarlo justo ahora, sin llegar a ver la petición.
        return syncMutex.tryLock()
    }

    /** Tras soltar el cerrojo: si llegó una petición mientras estaba ocupado, se atiende ahora. */
    private fun servePendingRequest() {
        if (pendingRequest) requestSync()
    }

    private suspend fun runRound(client: WebDavClient) {
        _syncState.value = SyncState.Syncing(SyncStage.CONNECTING)
        client.ensureRootFolder()

        val remote = client.listRemoteFiles().associateBy { it.name }

        _syncState.value = SyncState.Syncing(SyncStage.DOWNLOADING)
        val folderRemovals = pullFolders(client, remote)
        val bookmarkRemovals = pullBookmarks(client, remote)
        applyRemovals(folderRemovals, bookmarkRemovals, remoteFolderFiles = idsWithPrefix(remote, FOLDER_PREFIX).size)

        _syncState.value = SyncState.Syncing(SyncStage.UPLOADING)
        pushFolders(client, remote)
        pushBookmarks(client, remote)

        tombstoneDao.getAll().filter { it.fileName !in remote }.forEach { tombstoneDao.delete(it.fileName) }
    }

    // ------------------------------------------------------------------
    // Descarga
    // ------------------------------------------------------------------

    private fun idsWithPrefix(remote: Map<String, RemoteFileMeta>, prefix: String) =
        remote.values.filter { it.name.startsWith(prefix) && it.name.endsWith(JSON_EXT) }
            .associateBy { it.name.removePrefix(prefix).removeSuffix(JSON_EXT) }

    /**
     * Descarga las carpetas. Devuelve las carpetas sincronizadas que han desaparecido del servidor
     * sin lápida: no se borran aquí, sino en [applyRemovals], que antes comprueba que no sea un
     * vaciado en bloque de la carpeta remota.
     */
    private suspend fun pullFolders(client: WebDavClient, remote: Map<String, RemoteFileMeta>): List<String> {
        val remoteFolders = idsWithPrefix(remote, FOLDER_PREFIX)
        val knownTombstones = tombstoneDao.getAll().associate { it.fileName to it.etag }

        for ((id, meta) in remoteFolders) {
            val local = folderDao.getFolderById(id)
            if (local?.remoteEtag != null && local.remoteEtag == meta.etag) continue
            if (local == null && meta.etag != null && knownTombstones[meta.name] == meta.etag) continue

            val record = downloadRecord(meta.name, onLegacy = { markFolderForOverwrite(id, meta.etag) }) {
                RemoteCodec.decodeFolder(client.downloadText(meta.name), id)
            } ?: continue

            if (record is RemoteRecord.Tombstone) tombstoneDao.upsert(RemoteTombstoneEntity(meta.name, meta.etag))

            database.withTransaction {
                val current = folderDao.getFolderById(id)
                when (val action = SyncMerger.resolveFolder(current, record, meta.etag)) {
                    is MergeAction.SaveRemote -> folderDao.upsertFolder(action.entity)
                    is MergeAction.KeepLocal -> folderDao.setRemoteEtag(id, action.etag)
                    MergeAction.DeleteLocal -> deleteFolderAndDetachInTx(id)
                    else -> Unit
                }
            }
        }

        val missing = mutableListOf<String>()
        for (local in folderDao.getAll()) {
            if (local.id in remoteFolders) continue
            when (SyncMerger.resolveMissingRemote(local.syncStatus, local.remoteEtag)) {
                MissingRemoteAction.DELETE_LOCAL ->
                    if (local.syncStatus == SyncStatus.PENDING_DELETE) database.withTransaction { deleteFolderAndDetachInTx(local.id) }
                    else missing += local.id
                MissingRemoteAction.MARK_FOR_UPLOAD -> folderDao.updateSyncStatus(local.id, SyncStatus.PENDING_UPLOAD)
                MissingRemoteAction.FORGET_ETAG -> folderDao.setRemoteEtag(local.id, null)
                MissingRemoteAction.NOTHING, MissingRemoteAction.TRASH_AND_REUPLOAD -> Unit
            }
        }
        return missing
    }

    private suspend fun deleteFolderAndDetachInTx(id: String) {
        val folder = folderDao.getFolderById(id)
        bookmarkDao.detachFromFolder(id)
        if (folder != null) folderDao.reparentChildren(id, folder.parentId)
        folderDao.deletePermanently(id)
    }

    /** Descarga los marcadores. Las retiradas de marcadores visibles se devuelven para [applyRemovals]. */
    private suspend fun pullBookmarks(client: WebDavClient, remote: Map<String, RemoteFileMeta>): List<PendingRemoval> {
        val remoteBookmarks = idsWithPrefix(remote, BOOKMARK_PREFIX)
        val knownTombstones = tombstoneDao.getAll().associate { it.fileName to it.etag }

        // Retiradas de marcadores visibles: se aplican al final, y solo si no son un borrado en bloque.
        val pendingRemovals = mutableListOf<PendingRemoval>()

        for ((id, meta) in remoteBookmarks) {
            val local = bookmarkDao.getById(id)
            if (local?.remoteEtag != null && local.remoteEtag == meta.etag) continue
            if (local == null && meta.etag != null && knownTombstones[meta.name] == meta.etag) continue

            val record = downloadRecord(meta.name, onLegacy = { markBookmarkForOverwrite(id, meta.etag) }) {
                RemoteCodec.decodeBookmark(client.downloadText(meta.name), id)
            } ?: continue

            if (record is RemoteRecord.Tombstone) tombstoneDao.upsert(RemoteTombstoneEntity(meta.name, meta.etag))

            database.withTransaction {
                val current = bookmarkDao.getById(id)
                val action = SyncMerger.resolveBookmark(current, record, meta.etag)
                if (current != null && !current.isDeleted && action is MergeAction.DeleteLocal) {
                    pendingRemovals += PendingRemoval.Tombstone(id, record, meta.etag)
                } else {
                    applyBookmarkAction(id, action)
                }
            }
        }

        for (local in bookmarkDao.getAll()) {
            if (local.id in remoteBookmarks) continue
            when (SyncMerger.resolveMissingRemoteBookmark(local.syncStatus, local.remoteEtag)) {
                MissingRemoteAction.TRASH_AND_REUPLOAD ->
                    if (local.isDeleted) bookmarkDao.trashForReupload(local.id)
                    else pendingRemovals += PendingRemoval.Missing(local.id)
                MissingRemoteAction.DELETE_LOCAL -> bookmarkDao.deletePermanently(local.id)
                MissingRemoteAction.MARK_FOR_UPLOAD -> bookmarkDao.updateSyncStatus(local.id, SyncStatus.PENDING_UPLOAD)
                MissingRemoteAction.FORGET_ETAG -> bookmarkDao.setRemoteEtag(local.id, null)
                MissingRemoteAction.NOTHING -> Unit
            }
        }

        return pendingRemovals
    }

    /**
     * Aplica las retiradas que pidió el servidor (carpetas desaparecidas y marcadores visibles
     * borrados o desaparecidos), salvo que sean un borrado en bloque: entonces se conservan y se
     * vuelven a subir, tanto las carpetas como los marcadores (ver [SyncMerger.isMassRemoval]).
     */
    private suspend fun applyRemovals(folderIds: List<String>, removals: List<PendingRemoval>, remoteFolderFiles: Int) {
        if (folderIds.isEmpty() && removals.isEmpty()) return

        val brake = SyncMerger.isMassRemoval(removals.size, bookmarkDao.countLive()) ||
            SyncMerger.isMassFolderRemoval(folderIds.size, folderDao.countLive(), remoteFolderFiles)

        if (brake) {
            Log.w(TAG, "El servidor retiraría ${folderIds.size} carpetas y ${removals.size} marcadores a la vez; se conservan y se vuelven a subir")
            database.withTransaction {
                for (id in folderIds) {
                    val current = folderDao.getFolderById(id) ?: continue
                    if (current.syncStatus == SyncStatus.PENDING_DELETE) continue
                    folderDao.setRemoteEtag(id, null)
                    folderDao.updateSyncStatus(id, SyncStatus.PENDING_UPLOAD)
                }
                for (removal in removals) {
                    val current = bookmarkDao.getById(removal.id) ?: continue
                    if (current.syncStatus == SyncStatus.PENDING_DELETE) continue
                    val etag = (removal as? PendingRemoval.Tombstone)?.etag
                    bookmarkDao.setRemoteEtag(removal.id, etag)
                    bookmarkDao.updateSyncStatus(removal.id, SyncStatus.PENDING_UPLOAD)
                }
            }
            protectedItems += folderIds.size + removals.size
            return
        }

        for (id in folderIds) {
            database.withTransaction {
                // Se vuelve a decidir con el estado actual: pudo editarse durante la descarga.
                val current = folderDao.getFolderById(id) ?: return@withTransaction
                if (SyncMerger.resolveMissingRemote(current.syncStatus, current.remoteEtag) == MissingRemoteAction.DELETE_LOCAL) {
                    deleteFolderAndDetachInTx(id)
                }
            }
        }

        for (removal in removals) {
            database.withTransaction {
                val current = bookmarkDao.getById(removal.id) ?: return@withTransaction
                when (removal) {
                    is PendingRemoval.Tombstone ->
                        applyBookmarkAction(removal.id, SyncMerger.resolveBookmark(current, removal.record, removal.etag))
                    is PendingRemoval.Missing ->
                        if (SyncMerger.resolveMissingRemoteBookmark(current.syncStatus, current.remoteEtag) == MissingRemoteAction.TRASH_AND_REUPLOAD) {
                            bookmarkDao.trashForReupload(current.id)
                        }
                }
            }
        }
    }

    /** Marcador visible que el servidor quiere retirar; se decide al final de [pullBookmarks]. */
    private sealed interface PendingRemoval {
        val id: String
        class Tombstone(override val id: String, val record: RemoteRecord<BookmarkEntity>, val etag: String?) : PendingRemoval
        class Missing(override val id: String) : PendingRemoval
    }

    private suspend fun applyBookmarkAction(id: String, action: MergeAction<BookmarkEntity>) {
        when (action) {
            is MergeAction.SaveRemote -> bookmarkDao.upsert(action.entity)
            is MergeAction.SaveMerged -> bookmarkDao.upsert(action.entity)
            is MergeAction.KeepLocal -> bookmarkDao.setRemoteEtag(id, action.etag)
            MergeAction.DeleteLocal -> bookmarkDao.deletePermanently(id)
            MergeAction.Skip -> Unit
        }
    }

    /**
     * Descarga y descodifica un fichero. Un fichero corrupto o de una versión futura se omite
     * (se cuenta como aviso) en lugar de detener toda la sincronización.
     *
     * Los ficheros cifrados de la versión 1 no se pueden leer: [onLegacy] decide si se
     * sobrescriben con la copia local (devuelve false si este dispositivo no tiene el elemento).
     */
    private suspend fun <T> downloadRecord(name: String, onLegacy: suspend () -> Boolean, block: suspend () -> T): T? = try {
        block()
    } catch (e: WebDavException) {
        if (e.code == 404) null else throw e
    } catch (_: LegacyEncryptedFileException) {
        if (!onLegacy()) {
            Log.w(TAG, "Fichero cifrado antiguo sin copia local: $name"); warnings++
        }
        null
    } catch (e: JSONException) {
        Log.w(TAG, "JSON no válido en $name", e); warnings++; null
    } catch (e: IllegalArgumentException) {
        Log.w(TAG, "Fichero no válido $name", e); warnings++; null
    }

    /**
     * El servidor tiene una versión cifrada (v1) de un elemento que este dispositivo conoce:
     * se apunta su ETag y se marca para subir, de modo que la copia local en claro la sustituya.
     */
    private suspend fun markBookmarkForOverwrite(id: String, etag: String?): Boolean {
        val local = bookmarkDao.getById(id) ?: return false
        bookmarkDao.setRemoteEtag(id, etag)
        if (local.syncStatus != SyncStatus.PENDING_DELETE) bookmarkDao.updateSyncStatus(id, SyncStatus.PENDING_UPLOAD)
        return true
    }

    private suspend fun markFolderForOverwrite(id: String, etag: String?): Boolean {
        val local = folderDao.getFolderById(id) ?: return false
        folderDao.setRemoteEtag(id, etag)
        if (local.syncStatus != SyncStatus.PENDING_DELETE) folderDao.updateSyncStatus(id, SyncStatus.PENDING_UPLOAD)
        return true
    }

    // ------------------------------------------------------------------
    // Subida
    // ------------------------------------------------------------------

    private suspend fun pushFolders(client: WebDavClient, remote: Map<String, RemoteFileMeta>) {
        for (folder in folderDao.getFoldersBySyncStatus(SyncStatus.PENDING_UPLOAD)) {
            val name = folderFile(folder.id)
            val etag = putGuarded(client, name, RemoteCodec.encodeFolder(folder), folder.remoteEtag) ?: continue
            if (folderDao.markSynced(folder.id, folder.updatedAt, etag.value) == 0) {
                folderDao.setRemoteEtag(folder.id, etag.value)
            }
        }

        for (folder in folderDao.getFoldersBySyncStatus(SyncStatus.PENDING_DELETE)) {
            val name = folderFile(folder.id)
            val knownEtag = folder.remoteEtag ?: remote[name]?.etag
            if (knownEtag == null) {
                folderDao.deletePermanently(folder.id)
                continue
            }
            val etag = putGuarded(client, name, RemoteCodec.encodeTombstone(RemoteCodec.TYPE_FOLDER, folder.id, folder.updatedAt), knownEtag)
                ?: continue
            tombstoneDao.upsert(RemoteTombstoneEntity(name, etag.value))
            folderDao.deletePermanently(folder.id)
        }
    }

    private suspend fun pushBookmarks(client: WebDavClient, remote: Map<String, RemoteFileMeta>) {
        for (bookmark in bookmarkDao.getBySyncStatus(SyncStatus.PENDING_UPLOAD)) {
            val name = bookmarkFile(bookmark.id)
            val etag = putGuarded(client, name, RemoteCodec.encodeBookmark(bookmark), bookmark.remoteEtag) ?: continue
            if (bookmarkDao.markSynced(bookmark.id, bookmark.updatedAt, etag.value) == 0) {
                // Se editó mientras se subía: sigue pendiente, pero con el ETag nuevo.
                bookmarkDao.setRemoteEtag(bookmark.id, etag.value)
            }
        }

        for (bookmark in bookmarkDao.getBySyncStatus(SyncStatus.PENDING_DELETE)) {
            val name = bookmarkFile(bookmark.id)
            val knownEtag = bookmark.remoteEtag ?: remote[name]?.etag
            if (knownEtag == null) {
                bookmarkDao.deletePermanently(bookmark.id)
                continue
            }
            val etag = putGuarded(client, name, RemoteCodec.encodeTombstone(RemoteCodec.TYPE_BOOKMARK, bookmark.id, bookmark.updatedAt), knownEtag)
                ?: continue
            tombstoneDao.upsert(RemoteTombstoneEntity(name, etag.value))
            bookmarkDao.deletePermanently(bookmark.id)
        }
    }

    private class Etag(val value: String?)

    /**
     * PUT con control de concurrencia. Devuelve null si el servidor rechazó la subida porque
     * el fichero cambió (se pedirá otra ronda para fusionar primero).
     */
    private suspend fun putGuarded(client: WebDavClient, name: String, body: String, knownEtag: String?): Etag? {
        return try {
            val etag = client.uploadText(name, body, ifMatch = knownEtag, ifNoneMatch = knownEtag == null)
            Etag(etag ?: client.fetchEtag(name))
        } catch (_: WebDavPreconditionFailedException) {
            rerunRequested = true
            null
        }
    }
}
