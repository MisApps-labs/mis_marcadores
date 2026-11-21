// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Marcadores — https://github.com/ferlagod/mis_marcadores

package com.ferlagod.mismarcadores.ui.viewmodel

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.annotation.StringRes
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.ferlagod.mismarcadores.R
import com.ferlagod.mismarcadores.data.backup.AutoBackupWorker
import com.ferlagod.mismarcadores.data.backup.BackupManager
import com.ferlagod.mismarcadores.data.backup.RootFolderNames
import com.ferlagod.mismarcadores.data.local.AppDatabase
import com.ferlagod.mismarcadores.data.local.entity.BookmarkEntity
import com.ferlagod.mismarcadores.data.local.entity.FolderEntity
import com.ferlagod.mismarcadores.data.local.model.FolderTree
import com.ferlagod.mismarcadores.data.local.model.LinkStatus
import com.ferlagod.mismarcadores.data.local.model.SyncStatus
import com.ferlagod.mismarcadores.data.local.model.Tags
import com.ferlagod.mismarcadores.data.local.pref.AppSettings
import com.ferlagod.mismarcadores.data.local.pref.AppSettingsState
import com.ferlagod.mismarcadores.data.local.pref.LayoutMode
import com.ferlagod.mismarcadores.data.local.pref.NextcloudCredentials
import com.ferlagod.mismarcadores.data.local.pref.OpenMode
import com.ferlagod.mismarcadores.data.local.pref.SortOrder
import com.ferlagod.mismarcadores.data.local.pref.TapAction
import com.ferlagod.mismarcadores.data.local.pref.ThemeMode
import com.ferlagod.mismarcadores.data.metadata.LinkMetadata
import com.ferlagod.mismarcadores.data.metadata.LinkMetadataFetcher
import com.ferlagod.mismarcadores.data.sync.SyncRepository
import com.ferlagod.mismarcadores.data.sync.SyncState
import com.ferlagod.mismarcadores.data.sync.SyncWorker
import com.ferlagod.mismarcadores.ui.theme.LabelColor
import com.ferlagod.mismarcadores.util.LanguageManager
import com.ferlagod.mismarcadores.util.NextcloudUrl
import com.ferlagod.mismarcadores.util.TextSearch
import com.ferlagod.mismarcadores.util.UrlUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Qué marcadores muestra la lista. */
sealed interface BookmarkFilter {
    data object All : BookmarkFilter
    data object Favorites : BookmarkFilter
    data object ReadLater : BookmarkFilter
    data object Unsorted : BookmarkFilter
    data object Broken : BookmarkFilter
    data object Trash : BookmarkFilter
    data class Folder(val folderId: String, val folderName: String) : BookmarkFilter
    data class Tag(val tag: String) : BookmarkFilter
}

/** Avisos puntuales para la interfaz (snackbars). */
sealed interface UiEvent {
    /** Se movieron marcadores a la papelera: la interfaz ofrece "Deshacer". */
    data class Trashed(val ids: List<String>) : UiEvent
    data class Message(val text: String) : UiEvent
}

/** Navegación pedida desde fuera de la interfaz (accesos directos, compartir desde otra app). */
sealed interface PendingNavigation {
    data class NewBookmark(val url: String? = null, val title: String? = null) : PendingNavigation
}

/** Datos del editor de un marcador. */
data class BookmarkDraft(
    val id: String?,
    val url: String,
    val title: String,
    val description: String,
    val notes: String,
    val tags: List<String>,
    val folderId: String?,
    val color: LabelColor,
    val isFavorite: Boolean,
    val isReadLater: Boolean,
    val imageUrl: String?,
    val faviconUrl: String?
)

/** Progreso de la comprobación de enlaces. */
data class LinkCheckProgress(val done: Int, val total: Int, val broken: Int, val finished: Boolean = false)

/** Una etiqueta con el número de marcadores que la usan. */
data class TagCount(val name: String, val count: Int)

/**
 * ViewModel principal: cuenta, sincronización, ajustes, carpetas, etiquetas y marcadores.
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getInstance(application)
    private val folderDao = database.folderDao()
    private val bookmarkDao = database.bookmarkDao()
    private val backupManager = BackupManager(application)

    private val syncRepository = SyncRepository.getInstance(application)
    private val authPreferences = syncRepository.authPreferences
    private val appSettings = AppSettings.getInstance(application)

    val syncState: StateFlow<SyncState> = syncRepository.syncState
    val settings: StateFlow<AppSettingsState> = appSettings.state

    /** Serializa las escrituras de marcadores. */
    private val writeMutex = Mutex()

    private val _events = Channel<UiEvent>(Channel.BUFFERED)
    val events: Flow<UiEvent> = _events.receiveAsFlow()

    private val _pendingNavigation = MutableStateFlow<PendingNavigation?>(null)
    val pendingNavigation: StateFlow<PendingNavigation?> = _pendingNavigation.asStateFlow()

    private val _isConfigured = MutableStateFlow(authPreferences.hasCredentials())
    val isConfigured: StateFlow<Boolean> = _isConfigured.asStateFlow()

    private val _currentFilter = MutableStateFlow<BookmarkFilter>(BookmarkFilter.All)
    val currentFilter: StateFlow<BookmarkFilter> = _currentFilter.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _busy = MutableStateFlow(false)
    /** Hay una importación/exportación en curso. */
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _linkCheck = MutableStateFlow<LinkCheckProgress?>(null)
    val linkCheck: StateFlow<LinkCheckProgress?> = _linkCheck.asStateFlow()
    private var linkCheckJob: Job? = null

    private fun <T> Flow<T>.state(initial: T) = stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), initial)

    val activeBookmarks = bookmarkDao.getActiveFlow().state(emptyList())
    val trashBookmarks = bookmarkDao.getTrashFlow().state(emptyList())
    val allFolders = folderDao.getAllFoldersFlow().state(emptyList())
    val foldersMap: StateFlow<Map<String, FolderEntity>> = allFolders.map { list -> list.associateBy { it.id } }.state(emptyMap())

    /** Índice de búsqueda (texto normalizado sin acentos), recalculado solo cuando cambian los marcadores. */
    private val searchIndex = combine(activeBookmarks, trashBookmarks) { active, trash ->
        (active + trash).associate { it.id to searchText(it) }
    }.state(emptyMap())

    val displayedBookmarks: StateFlow<List<BookmarkEntity>> = combine(
        combine(activeBookmarks, trashBookmarks, ::Pair),
        _currentFilter,
        _searchQuery,
        settings.map { it.sortOrder }.distinctUntilChanged(),
        searchIndex
    ) { (active, trash), filter, query, sortOrder, index ->
        val filtered = when (filter) {
            BookmarkFilter.All -> active
            BookmarkFilter.Favorites -> active.filter { it.isFavorite }
            BookmarkFilter.ReadLater -> active.filter { it.isReadLater }
            BookmarkFilter.Unsorted -> active.filter { it.folderId == null }
            BookmarkFilter.Broken -> active.filter { it.linkStatus == LinkStatus.BROKEN }
            BookmarkFilter.Trash -> trash
            is BookmarkFilter.Folder -> active.filter { it.folderId == filter.folderId }
            is BookmarkFilter.Tag -> active.filter { Tags.contains(it.tags, filter.tag) }
        }
        val terms = TextSearch.terms(query)
        val matching = if (terms.isEmpty()) filtered else filtered.filter { b ->
            val haystack = index[b.id] ?: searchText(b)
            terms.all { haystack.contains(it) }
        }
        if (filter is BookmarkFilter.Trash) matching.sortedByDescending { it.updatedAt }
        else matching.sortedWith(sortOrder.comparator())
    }.state(emptyList())

    val totalCount = activeBookmarks.map { it.size }.state(0)
    val favoritesCount = activeBookmarks.map { list -> list.count { it.isFavorite } }.state(0)
    val readLaterCount = activeBookmarks.map { list -> list.count { it.isReadLater } }.state(0)
    val unsortedCount = activeBookmarks.map { list -> list.count { it.folderId == null } }.state(0)
    val brokenCount = activeBookmarks.map { list -> list.count { it.linkStatus == LinkStatus.BROKEN } }.state(0)
    val trashCount = trashBookmarks.map { it.size }.state(0)

    /** Últimos marcadores añadidos (para la pantalla de inicio). */
    val recentBookmarks = activeBookmarks.map { list -> list.sortedByDescending { it.createdAt }.take(10) }.state(emptyList())

    /** Etiquetas en uso, de más a menos usadas. */
    val tagCounts: StateFlow<List<TagCount>> = activeBookmarks.map { list ->
        val counts = LinkedHashMap<String, Pair<String, Int>>()
        for (b in list) for (tag in b.tagList) {
            val key = tag.lowercase()
            val current = counts[key]
            counts[key] = (current?.first ?: tag) to ((current?.second ?: 0) + 1)
        }
        counts.values.map { TagCount(it.first, it.second) }
            .sortedWith(compareByDescending<TagCount> { it.count }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    }.state(emptyList())

    private fun text(@StringRes id: Int, vararg args: Any): String =
        LanguageManager.localizedContext(getApplication()).getString(id, *args)

    private fun quantity(id: Int, count: Int, vararg args: Any): String =
        LanguageManager.localizedContext(getApplication()).resources.getQuantityString(id, count, *(if (args.isEmpty()) arrayOf<Any>(count) else args))

    init {
        viewModelScope.launch { purgeOldTrash() }
        viewModelScope.launch {
            allFolders.collect { folders ->
                val current = _currentFilter.value
                if (current is BookmarkFilter.Folder) {
                    val match = folders.firstOrNull { it.id == current.folderId }
                    if (match == null) {
                        val exists = folderDao.getFolderById(current.folderId)?.let { !it.isDeleted } == true
                        if (!exists) setFilter(BookmarkFilter.All)
                    } else if (match.name != current.folderName) {
                        _currentFilter.value = BookmarkFilter.Folder(match.id, match.name)
                    }
                }
            }
        }
        if (settings.value.autoBackup && settings.value.autoBackupFolder != null) {
            AutoBackupWorker.schedule(application)
        }
        if (_isConfigured.value) {
            SyncWorker.schedule(application)
            triggerSync()
        }
    }

    // ==========================================
    // Ajustes
    // ==========================================

    fun setLayoutMode(mode: LayoutMode) = appSettings.setLayoutMode(mode)
    fun setSortOrder(order: SortOrder) = appSettings.setSortOrder(order)
    fun setThemeMode(mode: ThemeMode) = appSettings.setThemeMode(mode)
    fun setAmoled(enabled: Boolean) = appSettings.setAmoled(enabled)
    fun setAppLock(enabled: Boolean) = appSettings.setAppLock(enabled)
    fun setTapAction(action: TapAction) = appSettings.setTapAction(action)
    fun setOpenMode(mode: OpenMode) = appSettings.setOpenMode(mode)
    fun setLoadPreviews(enabled: Boolean) = appSettings.setLoadPreviews(enabled)
    fun setAutoFetchMetadata(enabled: Boolean) = appSettings.setAutoFetchMetadata(enabled)
    fun setStripTracking(enabled: Boolean) = appSettings.setStripTracking(enabled)
    fun setReadLaterAutoRemove(enabled: Boolean) = appSettings.setReadLaterAutoRemove(enabled)

    fun setAutoPurgeTrash(enabled: Boolean) {
        appSettings.setAutoPurgeTrash(enabled)
        if (enabled) viewModelScope.launch { purgeOldTrash() }
    }

    /** Vacía de la papelera los marcadores que llevan más de 30 días en ella (si está activado). */
    private suspend fun purgeOldTrash() {
        if (!settings.value.autoPurgeTrash) return
        val before = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(AppSettings.TRASH_RETENTION_DAYS.toLong())
        val ids = bookmarkDao.getTrashIdsOlderThan(before)
        if (ids.isEmpty()) return
        writeMutex.withLock { database.withTransaction { ids.forEach { purgeLocked(it) } } }
        triggerSync()
    }

    // ==========================================
    // Cuenta
    // ==========================================

    fun connectNextcloud(
        serverUrl: String,
        username: String,
        appPassword: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (serverUrl.isBlank() || username.isBlank() || appPassword.isBlank()) {
            onError(text(R.string.err_all_fields))
            return
        }
        val url = serverUrl.trim()
        if (!url.startsWith("https://", ignoreCase = true)) {
            onError(text(R.string.err_https_required))
            return
        }
        viewModelScope.launch {
            // Se acepta también la URL WebDAV o la que se ve en el navegador (ver NextcloudUrl).
            val server = NextcloudUrl.parse(url)
            val credentials = NextcloudCredentials(server.serverUrl, username.trim(), appPassword.trim(), server.userId)
            syncRepository.connectAccount(credentials)
                .onSuccess {
                    _isConfigured.value = true
                    SyncWorker.schedule(getApplication())
                    triggerSync()
                    onSuccess()
                }
                .onFailure { onError(it.message ?: text(R.string.err_connect_failed)) }
        }
    }

    fun disconnectNextcloud() {
        SyncWorker.cancel(getApplication())
        authPreferences.clearCredentials()
        syncRepository.resetSyncState()
        _isConfigured.value = false
        viewModelScope.launch { clearPendingPurges() }
    }

    private suspend fun clearPendingPurges() {
        writeMutex.withLock {
            database.withTransaction {
                bookmarkDao.deletePendingPurge()
                folderDao.deletePendingPurge()
                database.remoteTombstoneDao().clear()
            }
        }
    }

    fun getCredentials(): NextcloudCredentials? = authPreferences.getCredentials()
    fun getLastSync(): Long? = authPreferences.getLastSync()

    fun triggerSync() {
        if (!_isConfigured.value) return
        syncRepository.requestSync()
    }

    // ==========================================
    // Navegación y búsqueda
    // ==========================================

    /** Carpetas desde las que se ha entrado en subcarpetas (para que "Atrás" suba un nivel). */
    private val _filterStack = MutableStateFlow<List<BookmarkFilter>>(emptyList())
    val canGoUp: StateFlow<Boolean> = _filterStack.map { it.isNotEmpty() }.state(false)

    fun setFilter(filter: BookmarkFilter) {
        _filterStack.value = emptyList()
        _currentFilter.value = filter
        _searchQuery.value = ""
    }

    /** Entra en una subcarpeta desde la lista actual. */
    fun openFolder(folder: FolderEntity) {
        _filterStack.value = _filterStack.value + _currentFilter.value
        _currentFilter.value = BookmarkFilter.Folder(folder.id, folder.name)
        _searchQuery.value = ""
    }

    /** Vuelve a la carpeta anterior. Devuelve false si no había ninguna (hay que salir de la lista). */
    fun goUp(): Boolean {
        val stack = _filterStack.value
        if (stack.isEmpty()) return false
        _filterStack.value = stack.dropLast(1)
        _currentFilter.value = stack.last()
        _searchQuery.value = ""
        return true
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun requestNavigation(navigation: PendingNavigation) {
        _pendingNavigation.value = navigation
    }

    fun consumeNavigation() {
        _pendingNavigation.value = null
    }

    /** Texto compartido desde otra app: se abre el editor con el enlace que contenga. */
    fun handleSharedText(subject: String?, text: String?) {
        val url = UrlUtils.extractUrl(text) ?: UrlUtils.extractUrl(subject)
        if (url == null) {
            _events.trySend(UiEvent.Message(text(R.string.share_no_link)))
            return
        }
        // El asunto suele ser el título de la página; si el texto trae algo más que la dirección, también.
        val title = subject?.takeIf { it.isNotBlank() && UrlUtils.extractUrl(it) == null }
            ?: text?.replace(Regex("""\S*https?://\S+"""), "")?.trim()?.lines()?.firstOrNull { it.isNotBlank() }
        requestNavigation(PendingNavigation.NewBookmark(url, title))
    }

    // ==========================================
    // Carpetas
    // ==========================================

    fun createFolder(name: String, color: LabelColor, parentId: String? = null, onCreated: (String) -> Unit = {}) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val existing = folderDao.getAll().firstOrNull {
                !it.isDeleted && it.parentId == parentId && it.name.equals(trimmed, ignoreCase = true)
            }
            if (existing != null) {
                onCreated(existing.id)
                return@launch
            }
            val folder = FolderEntity(
                id = UUID.randomUUID().toString(),
                parentId = parentId,
                name = trimmed,
                color = color.key,
                updatedAt = System.currentTimeMillis(),
                syncStatus = SyncStatus.PENDING_UPLOAD
            )
            folderDao.upsertFolder(folder)
            onCreated(folder.id)
            triggerSync()
        }
    }

    fun updateFolder(id: String, name: String, color: LabelColor, parentId: String? = null) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val folders = folderDao.getAll().filter { !it.isDeleted }
            val safeParent = parentId?.takeUnless { FolderTree.wouldCreateCycle(folders, id, it) }
            val siblingConflict = folders.any {
                it.id != id && it.parentId == safeParent && it.name.equals(trimmed, ignoreCase = true)
            }
            if (siblingConflict) return@launch
            folderDao.updateFolderDetails(id, trimmed, color.key, safeParent)
            triggerSync()
        }
    }

    /** Elimina una carpeta: sus marcadores quedan sin carpeta y sus subcarpetas suben un nivel. */
    fun deleteFolder(folderId: String) {
        viewModelScope.launch {
            writeMutex.withLock {
                database.withTransaction {
                    val folder = folderDao.getFolderById(folderId) ?: return@withTransaction
                    bookmarkDao.detachFromFolder(folderId)
                    folderDao.reparentChildren(folderId, folder.parentId)
                    if (_isConfigured.value) folderDao.markAsDeleted(folderId)
                    else folderDao.deletePermanently(folderId)
                }
            }
            if (_currentFilter.value.let { it is BookmarkFilter.Folder && it.folderId == folderId }) {
                _currentFilter.value = BookmarkFilter.All
            }
            triggerSync()
        }
    }

    // ==========================================
    // Marcadores
    // ==========================================

    suspend fun getBookmark(id: String): BookmarkEntity? = writeMutex.withLock { bookmarkDao.getById(id) }

    fun observeBookmark(id: String): Flow<BookmarkEntity?> = bookmarkDao.getByIdFlow(id)

    /** Otro marcador (no en la papelera) con la misma dirección, para avisar de duplicados. */
    suspend fun findDuplicate(url: String, excludeId: String?): BookmarkEntity? {
        val normalized = UrlUtils.normalize(url) ?: return null
        val key = UrlUtils.canonical(normalized)
        return bookmarkDao.getLive().firstOrNull { it.id != excludeId && UrlUtils.canonical(it.url) == key }
    }

    /** Título, descripción e imágenes de una web. Devuelve null si no se pudo leer. */
    suspend fun fetchMetadata(url: String): LinkMetadata? {
        val normalized = UrlUtils.normalize(url) ?: return null
        return try {
            LinkMetadataFetcher.fetch(normalized)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    /** Dirección lista para guardar (con esquema y, si está activado, sin parámetros de seguimiento). */
    fun cleanUrl(url: String): String? {
        val normalized = UrlUtils.normalize(url) ?: return null
        return if (settings.value.stripTracking) UrlUtils.stripTracking(normalized) else normalized
    }

    /**
     * Guarda un marcador nuevo o editado. No cambia nada (ni la fecha) si no hay cambios.
     * Devuelve el id guardado, o null si la dirección no es válida.
     *
     * [original] son los valores que mostraba el editor al abrirse. Solo se aplican los campos
     * que el usuario ha cambiado respecto a ellos; el resto conserva el valor actual de la base
     * de datos, por si una sincronización lo modificó mientras el editor estaba abierto.
     */
    suspend fun saveBookmark(draft: BookmarkDraft, original: BookmarkDraft? = null): String? {
        val url = cleanUrl(draft.url) ?: return null
        val id = writeMutex.withLock {
            database.withTransaction {
                val existing = draft.id?.let { bookmarkDao.getById(it) }
                val base = existing ?: BookmarkEntity(id = draft.id ?: UUID.randomUUID().toString(), url = url)
                // Sin valores originales (o marcador nuevo) se aplica el borrador entero.
                val orig = original?.takeIf { existing != null }
                fun <T> changed(field: (BookmarkDraft) -> T) = orig == null || field(draft) != field(orig)
                val newUrl = if (changed { it.url }) url else base.url
                val updated = base.copy(
                    url = newUrl,
                    title = if (changed { it.title }) draft.title.trim() else base.title,
                    description = if (changed { it.description }) draft.description.trim() else base.description,
                    notes = if (changed { it.notes }) draft.notes.trimEnd() else base.notes,
                    tags = if (changed { it.tags }) Tags.join(draft.tags) else base.tags,
                    folderId = if (changed { it.folderId }) draft.folderId else base.folderId,
                    color = if (changed { it.color }) draft.color.key else base.color,
                    isFavorite = if (changed { it.isFavorite }) draft.isFavorite else base.isFavorite,
                    isReadLater = if (changed { it.isReadLater }) draft.isReadLater else base.isReadLater,
                    imageUrl = if (changed { it.imageUrl }) draft.imageUrl?.takeIf { it.isNotBlank() } else base.imageUrl,
                    faviconUrl = if (changed { it.faviconUrl }) draft.faviconUrl?.takeIf { it.isNotBlank() } else base.faviconUrl,
                    linkStatus = if (existing != null && existing.url != newUrl) LinkStatus.UNKNOWN else base.linkStatus
                )
                if (existing != null && existing == updated) return@withTransaction existing.id
                val now = System.currentTimeMillis()
                bookmarkDao.upsert(
                    updated.copy(
                        updatedAt = now,
                        createdAt = if (existing == null) now else existing.createdAt,
                        syncStatus = if (existing?.syncStatus == SyncStatus.PENDING_DELETE) SyncStatus.PENDING_DELETE else SyncStatus.PENDING_UPLOAD
                    )
                )
                updated.id
            }
        }
        triggerSync()
        return id
    }

    /** Se ha abierto el enlace: estadística local, posible salida de "Leer más tarde" y sincronización. */
    fun onBookmarkOpened(bookmark: BookmarkEntity) {
        viewModelScope.launch {
            writeMutex.withLock {
                bookmarkDao.registerOpen(bookmark.id)
                if (bookmark.isReadLater && settings.value.readLaterAutoRemove && !bookmark.isDeleted) {
                    bookmarkDao.setReadLater(listOf(bookmark.id), false)
                }
            }
            triggerSync()
        }
    }

    fun setFavorite(ids: Collection<String>, value: Boolean) = bulkUpdate { bookmarkDao.setFavorite(ids.toList(), value) }
    fun setReadLater(ids: Collection<String>, value: Boolean) = bulkUpdate { bookmarkDao.setReadLater(ids.toList(), value) }
    fun setColor(ids: Collection<String>, color: LabelColor) = bulkUpdate { bookmarkDao.setColor(ids.toList(), color.key) }
    fun moveBookmarks(ids: Collection<String>, folderId: String?) = bulkUpdate { bookmarkDao.moveToFolder(ids.toList(), folderId) }

    /** Añade etiquetas a varios marcadores. */
    fun addTags(ids: Collection<String>, tags: List<String>) {
        val clean = Tags.normalize(tags)
        if (clean.isEmpty()) return
        bulkUpdate {
            database.withTransaction {
                for (id in ids) {
                    val b = bookmarkDao.getById(id) ?: continue
                    val merged = Tags.join(b.tagList + clean)
                    if (merged != b.tags) bookmarkDao.setTags(id, merged)
                }
            }
        }
    }

    /** Cambia el nombre de una etiqueta en todos los marcadores (si ya existe, se fusionan). */
    fun renameTag(old: String, new: String) {
        val clean = Tags.clean(new)
        if (clean.isEmpty() || clean == old) return
        bulkUpdate {
            database.withTransaction {
                for (b in bookmarkDao.getAll()) {
                    if (b.syncStatus == SyncStatus.PENDING_DELETE || !Tags.contains(b.tags, old)) continue
                    val tags = b.tagList.map { if (it.equals(old, ignoreCase = true)) clean else it }
                    bookmarkDao.setTags(b.id, Tags.join(tags))
                }
            }
            if ((_currentFilter.value as? BookmarkFilter.Tag)?.tag.equals(old, ignoreCase = true)) {
                _currentFilter.value = BookmarkFilter.Tag(clean)
            }
        }
    }

    /** Quita una etiqueta de todos los marcadores. */
    fun deleteTag(tag: String) = bulkUpdate {
        database.withTransaction {
            for (b in bookmarkDao.getAll()) {
                if (b.syncStatus == SyncStatus.PENDING_DELETE || !Tags.contains(b.tags, tag)) continue
                bookmarkDao.setTags(b.id, Tags.join(b.tagList.filterNot { it.equals(tag, ignoreCase = true) }))
            }
        }
        if ((_currentFilter.value as? BookmarkFilter.Tag)?.tag.equals(tag, ignoreCase = true)) {
            _currentFilter.value = BookmarkFilter.All
        }
    }

    fun moveToTrash(ids: Collection<String>) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            writeMutex.withLock { database.withTransaction { ids.forEach { bookmarkDao.moveToTrash(it) } } }
            _events.trySend(UiEvent.Trashed(ids.toList()))
            triggerSync()
        }
    }

    fun restore(ids: Collection<String>) = bulkUpdate {
        database.withTransaction { ids.forEach { bookmarkDao.restoreFromTrash(it) } }
    }

    fun deletePermanently(ids: Collection<String>) = bulkUpdate {
        database.withTransaction { ids.forEach { purgeLocked(it) } }
    }

    fun emptyTrash() = bulkUpdate {
        if (_isConfigured.value) bookmarkDao.markTrashForPurge() else bookmarkDao.deleteTrashPermanently()
    }

    /** Borrado definitivo (con lápida en el servidor si hay cuenta). Requiere el cerrojo de escritura. */
    private suspend fun purgeLocked(id: String) {
        if (_isConfigured.value) bookmarkDao.markForPurge(id) else bookmarkDao.deletePermanently(id)
    }

    private fun bulkUpdate(block: suspend () -> Unit) {
        viewModelScope.launch {
            writeMutex.withLock { block() }
            triggerSync()
        }
    }

    // ==========================================
    // Comprobación de enlaces
    // ==========================================

    /** Comprueba todos los enlaces (4 a la vez) y marca los que ya no existen. */
    fun checkLinks() {
        if (linkCheckJob?.isActive == true) return
        linkCheckJob = viewModelScope.launch {
            val targets = bookmarkDao.getLive()
            val done = AtomicInteger(0)
            val broken = AtomicInteger(0)
            _linkCheck.value = LinkCheckProgress(0, targets.size, 0)
            val semaphore = Semaphore(4)
            coroutineScope {
                targets.map { b ->
                    async {
                        semaphore.withPermit {
                            val status = LinkMetadataFetcher.check(b.url)
                            // Un fallo temporal no borra un resultado anterior conocido.
                            if (status != LinkStatus.UNKNOWN || b.linkStatus == LinkStatus.UNKNOWN) {
                                bookmarkDao.setLinkStatus(b.id, status)
                            }
                            if (status == LinkStatus.BROKEN) broken.incrementAndGet()
                            _linkCheck.value = LinkCheckProgress(done.incrementAndGet(), targets.size, broken.get())
                        }
                    }
                }.awaitAll()
            }
            _linkCheck.value = LinkCheckProgress(targets.size, targets.size, broken.get(), finished = true)
            _events.trySend(
                UiEvent.Message(
                    if (broken.get() == 0) text(R.string.link_check_all_ok)
                    else quantity(R.plurals.link_check_broken, broken.get())
                )
            )
        }
    }

    fun cancelLinkCheck() {
        linkCheckJob?.cancel()
        _linkCheck.value = null
    }

    /** El usuario dice que un enlace marcado como roto sí funciona. */
    fun markLinkOk(ids: Collection<String>) {
        viewModelScope.launch {
            // El estado del enlace es solo local: no hace falta sincronizar.
            ids.forEach { bookmarkDao.setLinkStatus(it, LinkStatus.OK) }
        }
    }

    // ==========================================
    // Copia de seguridad
    // ==========================================

    fun exportBackup(uri: Uri) = runBusy {
        val count = backupManager.exportTo(uri)
        _events.trySend(UiEvent.Message(quantity(R.plurals.export_done, count)))
    }

    fun importBackup(uris: List<Uri>) {
        if (uris.isEmpty()) return
        runBusy {
            val names = RootFolderNames(
                toolbar = text(R.string.import_folder_toolbar),
                other = text(R.string.import_folder_other),
                mobile = text(R.string.import_folder_mobile)
            )
            val result = backupManager.import(uris, names)
            val message = when {
                result.added == 0 && result.duplicates > 0 -> quantity(R.plurals.import_all_duplicates, result.duplicates)
                result.added == 0 -> text(R.string.import_nothing)
                result.duplicates > 0 -> quantity(R.plurals.import_done, result.added) + " " +
                    quantity(R.plurals.import_skipped, result.duplicates)
                else -> quantity(R.plurals.import_done, result.added)
            }
            _events.trySend(UiEvent.Message(message))
            if (result.added > 0) triggerSync()
        }
    }

    fun setAutoBackup(enabled: Boolean) {
        val canEnable = enabled && settings.value.autoBackupFolder != null
        appSettings.setAutoBackup(canEnable)
        if (canEnable) AutoBackupWorker.schedule(getApplication(), replace = true)
        else AutoBackupWorker.cancel(getApplication())
    }

    fun setAutoBackupFolder(uri: Uri) {
        val resolver = getApplication<Application>().contentResolver
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        try {
            resolver.takePersistableUriPermission(uri, flags)
        } catch (e: SecurityException) {
            _events.trySend(UiEvent.Message(text(R.string.backup_failed, e.localizedMessage ?: e.javaClass.simpleName)))
            return
        }
        val previous = settings.value.autoBackupFolder
        if (previous != null && previous != uri.toString()) {
            runCatching { resolver.releasePersistableUriPermission(previous.toUri(), flags) }
        }
        appSettings.setAutoBackupFolder(uri.toString())
        setAutoBackup(true)
    }

    private fun runBusy(block: suspend () -> Unit) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.trySend(UiEvent.Message(text(R.string.backup_failed, e.localizedMessage ?: e.javaClass.simpleName)))
            } finally {
                _busy.value = false
            }
        }
    }

    private fun searchText(b: BookmarkEntity) =
        TextSearch.normalize(listOf(b.title, b.url, b.description, b.notes, b.tags.replace(',', ' ')).joinToString("\n"))
}

/** Comparador de marcadores para cada criterio de orden. */
fun SortOrder.comparator(): Comparator<BookmarkEntity> = when (this) {
    SortOrder.CREATED_DESC -> compareByDescending { it.createdAt }
    SortOrder.CREATED_ASC -> compareBy { it.createdAt }
    SortOrder.MODIFIED_DESC -> compareByDescending { it.updatedAt }
    SortOrder.TITLE_ASC -> compareBy<BookmarkEntity, String>(String.CASE_INSENSITIVE_ORDER) { it.displayTitle }.thenByDescending { it.createdAt }
    SortOrder.TITLE_DESC -> compareByDescending<BookmarkEntity, String>(String.CASE_INSENSITIVE_ORDER) { it.displayTitle }.thenByDescending { it.createdAt }
    SortOrder.DOMAIN_ASC -> compareBy<BookmarkEntity> { UrlUtils.host(it.url) }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.displayTitle }
    SortOrder.MOST_OPENED -> compareByDescending<BookmarkEntity> { it.openCount }.thenByDescending { it.lastOpenedAt }.thenByDescending { it.createdAt }
    SortOrder.RECENTLY_OPENED -> compareByDescending<BookmarkEntity> { it.lastOpenedAt }.thenByDescending { it.createdAt }
}

class MainViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) return MainViewModel(application) as T
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
