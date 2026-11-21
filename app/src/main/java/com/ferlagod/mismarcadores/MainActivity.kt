// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Marcadores — https://github.com/MisApps-labs/mis_marcadores

package com.ferlagod.mismarcadores

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ferlagod.mismarcadores.data.local.entity.BookmarkEntity
import com.ferlagod.mismarcadores.data.local.pref.ThemeMode
import com.ferlagod.mismarcadores.ui.screen.AppLockScreen
import com.ferlagod.mismarcadores.ui.screen.BookmarkEditorScreen
import com.ferlagod.mismarcadores.ui.screen.BookmarksActions
import com.ferlagod.mismarcadores.ui.screen.BookmarksScreen
import com.ferlagod.mismarcadores.ui.screen.EditorActions
import com.ferlagod.mismarcadores.ui.screen.HomeActions
import com.ferlagod.mismarcadores.ui.screen.HomeCounts
import com.ferlagod.mismarcadores.ui.screen.HomeScreen
import com.ferlagod.mismarcadores.ui.screen.NewBookmarkDefaults
import com.ferlagod.mismarcadores.ui.screen.SettingsActions
import com.ferlagod.mismarcadores.ui.screen.SettingsScreen
import com.ferlagod.mismarcadores.ui.theme.MisMarcadoresTheme
import com.ferlagod.mismarcadores.ui.viewmodel.BookmarkFilter
import com.ferlagod.mismarcadores.ui.viewmodel.MainViewModel
import com.ferlagod.mismarcadores.ui.viewmodel.MainViewModelFactory
import com.ferlagod.mismarcadores.ui.viewmodel.PendingNavigation
import com.ferlagod.mismarcadores.ui.viewmodel.UiEvent
import com.ferlagod.mismarcadores.util.AppLanguage
import com.ferlagod.mismarcadores.util.AppLock
import com.ferlagod.mismarcadores.util.LanguageManager
import com.ferlagod.mismarcadores.util.LinkOpener
import kotlinx.coroutines.launch

object AppDestinations {
    const val HOME = "home"
    const val LIST = "list"
    const val SETTINGS = "settings"
    const val EDITOR = "editor?id={id}&url={url}&title={title}&folderId={folderId}&tag={tag}&fav={fav}&later={later}"

    fun editorRoute(id: String? = null, defaults: NewBookmarkDefaults = NewBookmarkDefaults()): String =
        "editor?id=${enc(id)}&url=${enc(defaults.url)}&title=${enc(defaults.title)}" +
            "&folderId=${enc(defaults.folderId)}&tag=${enc(defaults.tag)}&fav=${defaults.favorite}&later=${defaults.readLater}"

    private fun enc(value: String?) = Uri.encode(value.orEmpty())
}

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels { MainViewModelFactory(application) }

    /** La app está tapada por la pantalla de bloqueo (huella/PIN). */
    private var appLocked by mutableStateOf(false)
    private var authInProgress = false
    private var backgroundSince = 0L

    private val confirmCredential = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        onAuthResult(result.resultCode == Activity.RESULT_OK)
    }

    override fun attachBaseContext(newBase: Context) {
        // Android 8-12: aplica el idioma elegido en Ajustes (en 13+ lo hace el sistema).
        super.attachBaseContext(LanguageManager.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        appLocked = savedInstanceState?.getBoolean(STATE_LOCKED)
            ?: (viewModel.settings.value.appLock && AppLock.isAvailable(this))
        if (savedInstanceState == null) handleIntent(intent)

        setContent {
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            val darkTheme = when (settings.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            LaunchedEffect(settings.appLock) { applyRecentsPrivacy(settings.appLock) }

            MisMarcadoresTheme(darkTheme = darkTheme, amoled = settings.amoled) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        AppNavigationHost(
                            viewModel = viewModel,
                            appLocked = appLocked,
                            darkTheme = darkTheme,
                            currentLanguage = LanguageManager.current(this@MainActivity),
                            onLanguageSelected = { LanguageManager.set(this@MainActivity, it) },
                            isAppLockAvailable = { AppLock.isAvailable(this@MainActivity) },
                            onAppLockChange = { enabled ->
                                // Activarlo exige autenticarse una vez, para comprobar que funciona.
                                if (enabled) requestUnlock { ok -> if (ok) viewModel.setAppLock(true) }
                                else viewModel.setAppLock(false)
                            }
                        )
                        AnimatedVisibility(visible = appLocked, enter = fadeIn(), exit = fadeOut()) {
                            AppLockScreen(onUnlock = { requestUnlock() })
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        val lockEnabled = viewModel.settings.value.appLock && AppLock.isAvailable(this)
        if (lockEnabled && backgroundSince > 0 && SystemClock.elapsedRealtime() - backgroundSince > AppLock.GRACE_PERIOD_MS) {
            appLocked = true
        }
        backgroundSince = 0
        if (appLocked && lockEnabled) requestUnlock() else appLocked = false
        // Sincroniza al volver a la app para traer cambios de otros dispositivos.
        viewModel.triggerSync()
    }

    override fun onStop() {
        super.onStop()
        if (!authInProgress) backgroundSince = SystemClock.elapsedRealtime()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_LOCKED, appLocked)
    }

    private var pendingAuthCallback: ((Boolean) -> Unit)? = null

    private fun requestUnlock(callback: ((Boolean) -> Unit)? = null) {
        if (authInProgress) return
        authInProgress = true
        pendingAuthCallback = callback
        val intent = AppLock.authenticate(
            activity = this,
            title = getString(R.string.app_lock_prompt_title),
            subtitle = getString(R.string.app_lock_prompt_subtitle),
            onResult = ::onAuthResult
        )
        if (intent != null) confirmCredential.launch(intent)
    }

    private fun onAuthResult(success: Boolean) {
        authInProgress = false
        if (success) appLocked = false
        pendingAuthCallback?.invoke(success)
        pendingAuthCallback = null
    }

    /** Oculta el contenido en la vista de apps recientes cuando el bloqueo está activado. */
    private fun applyRecentsPrivacy(enabled: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) setRecentsScreenshotEnabled(!enabled)
    }

    /** Acceso directo y enlaces compartidos desde otras apps. */
    private fun handleIntent(intent: Intent?) {
        intent ?: return
        when (intent.action) {
            ACTION_NEW_BOOKMARK -> viewModel.requestNavigation(PendingNavigation.NewBookmark())
            Intent.ACTION_SEND -> {
                viewModel.handleSharedText(
                    subject = intent.getStringExtra(Intent.EXTRA_SUBJECT) ?: intent.getStringExtra(Intent.EXTRA_TITLE),
                    text = intent.getStringExtra(Intent.EXTRA_TEXT)
                )
                intent.action = null
            }
        }
    }

    companion object {
        const val ACTION_NEW_BOOKMARK = "com.ferlagod.mismarcadores.action.NEW_BOOKMARK"
        private const val STATE_LOCKED = "app_locked"
    }
}

@Composable
private fun AppNavigationHost(
    viewModel: MainViewModel,
    appLocked: Boolean,
    darkTheme: Boolean,
    currentLanguage: AppLanguage,
    onLanguageSelected: (AppLanguage) -> Unit,
    isAppLockAvailable: () -> Boolean,
    onAppLockChange: (Boolean) -> Unit
) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()

    val isConfigured by viewModel.isConfigured.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val currentFilter by viewModel.currentFilter.collectAsStateWithLifecycle()
    val folders by viewModel.allFolders.collectAsStateWithLifecycle()
    val foldersMap by viewModel.foldersMap.collectAsStateWithLifecycle()
    val bookmarks by viewModel.displayedBookmarks.collectAsStateWithLifecycle()
    val activeBookmarks by viewModel.activeBookmarks.collectAsStateWithLifecycle()
    val recent by viewModel.recentBookmarks.collectAsStateWithLifecycle()
    val tagCounts by viewModel.tagCounts.collectAsStateWithLifecycle()
    val total by viewModel.totalCount.collectAsStateWithLifecycle()
    val favorites by viewModel.favoritesCount.collectAsStateWithLifecycle()
    val readLater by viewModel.readLaterCount.collectAsStateWithLifecycle()
    val unsorted by viewModel.unsortedCount.collectAsStateWithLifecycle()
    val broken by viewModel.brokenCount.collectAsStateWithLifecycle()
    val trash by viewModel.trashCount.collectAsStateWithLifecycle()
    val syncState by viewModel.syncState.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val pendingNavigation by viewModel.pendingNavigation.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val linkCheck by viewModel.linkCheck.collectAsStateWithLifecycle()
    val canGoUp by viewModel.canGoUp.collectAsStateWithLifecycle()
    val folderCounts = remember(activeBookmarks) { activeBookmarks.groupingBy { it.folderId }.eachCount() }

    val toolbarColor = MaterialTheme.colorScheme.background.toArgb()
    val allTagNames = tagCounts.map { it.name }

    fun openBookmark(bookmark: BookmarkEntity) {
        val ok = LinkOpener.open(context, bookmark.url, settings.openMode, toolbarColor, darkTheme)
        if (ok) viewModel.onBookmarkOpened(bookmark)
        else {
            viewModel.triggerSync()
            scope.launch { snackbarHostState.showSnackbar(resources.getString(R.string.no_app_to_open), withDismissAction = true) }
        }
    }

    fun editBookmark(id: String) {
        viewModel.triggerSync()
        navController.navigate(AppDestinations.editorRoute(id = id))
    }

    // Avisos del ViewModel: "Movido a la papelera · Deshacer", resultados de exportar…
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            snackbarHostState.currentSnackbarData?.dismiss()
            scope.launch {
                when (event) {
                    is UiEvent.Trashed -> {
                        val result = snackbarHostState.showSnackbar(
                            message = resources.getQuantityString(R.plurals.moved_to_trash, event.ids.size, event.ids.size),
                            actionLabel = resources.getString(R.string.undo),
                            withDismissAction = true,
                            duration = SnackbarDuration.Long
                        )
                        if (result == SnackbarResult.ActionPerformed) viewModel.restore(event.ids)
                    }
                    is UiEvent.Message -> snackbarHostState.showSnackbar(event.text, withDismissAction = true)
                }
            }
        }
    }

    // Acceso directo y enlaces compartidos (cuando la app está desbloqueada).
    LaunchedEffect(pendingNavigation, appLocked) {
        val target = pendingNavigation ?: return@LaunchedEffect
        if (appLocked) return@LaunchedEffect
        viewModel.consumeNavigation()
        when (target) {
            is PendingNavigation.NewBookmark -> navController.navigate(
                AppDestinations.editorRoute(defaults = NewBookmarkDefaults(url = target.url, title = target.title))
            )
        }
    }

    /** Valores de un marcador nuevo creado desde la lista actual (carpeta, etiqueta, favoritos…). */
    fun defaultsForFilter(filter: BookmarkFilter) = NewBookmarkDefaults(
        folderId = (filter as? BookmarkFilter.Folder)?.folderId,
        tag = (filter as? BookmarkFilter.Tag)?.tag,
        favorite = filter is BookmarkFilter.Favorites,
        readLater = filter is BookmarkFilter.ReadLater
    )

    NavHost(
        navController = navController,
        startDestination = AppDestinations.HOME
    ) {
        composable(AppDestinations.HOME) {
            HomeScreen(
                isConfigured = isConfigured,
                folders = folders,
                bookmarks = activeBookmarks,
                recent = recent,
                tags = tagCounts,
                counts = HomeCounts(total, favorites, readLater, unsorted, broken, trash),
                loadPreviews = settings.loadPreviews,
                layoutMode = settings.layoutMode,
                sortOrder = settings.sortOrder,
                syncState = syncState,
                snackbarHostState = snackbarHostState,
                actions = HomeActions(
                    onSelectFilter = { filter ->
                        viewModel.setFilter(filter)
                        navController.navigate(AppDestinations.LIST)
                    },
                    onSearch = { query ->
                        viewModel.setFilter(BookmarkFilter.All)
                        viewModel.setSearchQuery(query)
                        navController.navigate(AppDestinations.LIST)
                    },
                    onNewBookmark = { navController.navigate(AppDestinations.editorRoute()) },
                    onOpenBookmark = ::openBookmark,
                    onEditBookmark = { editBookmark(it.id) },
                    onCreateFolder = { name, color, parent -> viewModel.createFolder(name, color, parent) },
                    onUpdateFolder = viewModel::updateFolder,
                    onDeleteFolder = viewModel::deleteFolder,
                    onRenameTag = viewModel::renameTag,
                    onDeleteTag = viewModel::deleteTag,
                    onTriggerSync = viewModel::triggerSync,
                    onOpenSettings = { navController.navigate(AppDestinations.SETTINGS) }
                )
            )
        }

        composable(AppDestinations.LIST) {
            BookmarksScreen(
                bookmarks = bookmarks,
                folders = folders,
                foldersMap = foldersMap,
                allTags = allTagNames,
                filter = currentFilter,
                searchQuery = searchQuery,
                layoutMode = settings.layoutMode,
                sortOrder = settings.sortOrder,
                tapAction = settings.tapAction,
                loadPreviews = settings.loadPreviews,
                autoPurgeTrash = settings.autoPurgeTrash,
                canGoUp = canGoUp,
                folderBookmarkCounts = folderCounts,
                snackbarHostState = snackbarHostState,
                actions = BookmarksActions(
                    onBack = { if (!viewModel.goUp()) navController.popBackStack() },
                    onSearchQueryChange = viewModel::setSearchQuery,
                    onLayoutChange = viewModel::setLayoutMode,
                    onSortOrderChange = viewModel::setSortOrder,
                    onOpen = ::openBookmark,
                    onEdit = { editBookmark(it.id) },
                    onNew = { navController.navigate(AppDestinations.editorRoute(defaults = defaultsForFilter(currentFilter))) },
                    onSetFavorite = viewModel::setFavorite,
                    onSetReadLater = viewModel::setReadLater,
                    onSetColor = viewModel::setColor,
                    onMove = viewModel::moveBookmarks,
                    onAddTags = viewModel::addTags,
                    onTrash = viewModel::moveToTrash,
                    onRestore = viewModel::restore,
                    onDeletePermanently = viewModel::deletePermanently,
                    onEmptyTrash = viewModel::emptyTrash,
                    onMarkLinkOk = viewModel::markLinkOk,
                    onCreateFolder = viewModel::createFolder,
                    onOpenFolder = viewModel::openFolder
                )
            )
        }

        composable(AppDestinations.SETTINGS) {
            SettingsScreen(
                isConfigured = isConfigured,
                currentCredentials = remember(isConfigured) { viewModel.getCredentials() },
                syncState = syncState,
                lastSync = remember(isConfigured, syncState) { viewModel.getLastSync() },
                settings = settings,
                busy = busy,
                linkCheck = linkCheck,
                snackbarHostState = snackbarHostState,
                isAppLockAvailable = isAppLockAvailable(),
                currentLanguage = currentLanguage,
                actions = SettingsActions(
                    onBack = { navController.popBackStack() },
                    onSaveNextcloud = viewModel::connectNextcloud,
                    onDisconnectNextcloud = viewModel::disconnectNextcloud,
                    onTriggerSync = viewModel::triggerSync,
                    onThemeChange = viewModel::setThemeMode,
                    onAmoledChange = viewModel::setAmoled,
                    onLayoutChange = viewModel::setLayoutMode,
                    onSortOrderChange = viewModel::setSortOrder,
                    onTapActionChange = viewModel::setTapAction,
                    onOpenModeChange = viewModel::setOpenMode,
                    onAutoFetchChange = viewModel::setAutoFetchMetadata,
                    onLoadPreviewsChange = viewModel::setLoadPreviews,
                    onStripTrackingChange = viewModel::setStripTracking,
                    onReadLaterAutoRemoveChange = viewModel::setReadLaterAutoRemove,
                    onCheckLinks = viewModel::checkLinks,
                    onCancelLinkCheck = viewModel::cancelLinkCheck,
                    onAutoPurgeChange = viewModel::setAutoPurgeTrash,
                    onAppLockChange = onAppLockChange,
                    onExport = viewModel::exportBackup,
                    onImport = viewModel::importBackup,
                    onAutoBackupChange = viewModel::setAutoBackup,
                    onAutoBackupFolderSelected = viewModel::setAutoBackupFolder,
                    onLanguageSelected = onLanguageSelected
                )
            )
        }

        composable(
            route = AppDestinations.EDITOR,
            arguments = listOf(
                navArgument("id") { type = NavType.StringType; defaultValue = "" },
                navArgument("url") { type = NavType.StringType; defaultValue = "" },
                navArgument("title") { type = NavType.StringType; defaultValue = "" },
                navArgument("folderId") { type = NavType.StringType; defaultValue = "" },
                navArgument("tag") { type = NavType.StringType; defaultValue = "" },
                navArgument("fav") { type = NavType.BoolType; defaultValue = false },
                navArgument("later") { type = NavType.BoolType; defaultValue = false }
            )
        ) { entry ->
            val args = entry.arguments
            fun arg(name: String) = args?.getString(name)?.takeIf { it.isNotBlank() }
            val id = arg("id")
            val defaults = NewBookmarkDefaults(
                url = arg("url"),
                title = arg("title"),
                folderId = arg("folderId"),
                tag = arg("tag"),
                favorite = args?.getBoolean("fav") ?: false,
                readLater = args?.getBoolean("later") ?: false
            )

            // El marcador se lee una sola vez; null mientras carga.
            var loaded by remember(id) { mutableStateOf(id == null) }
            var existing by remember(id) { mutableStateOf<BookmarkEntity?>(null) }
            LaunchedEffect(id) {
                if (id != null) {
                    existing = viewModel.getBookmark(id)
                    loaded = true
                    if (existing == null) navController.popBackStack()
                }
            }
            if (loaded && (id == null || existing != null)) {
                BookmarkEditorScreen(
                    existing = existing,
                    defaults = defaults,
                    folders = folders,
                    allTags = allTagNames,
                    autoFetch = settings.autoFetchMetadata,
                    loadPreviews = settings.loadPreviews,
                    snackbarHostState = snackbarHostState,
                    actions = EditorActions(
                        onBack = { navController.popBackStack() },
                        onSave = viewModel::saveBookmark,
                        fetchMetadata = viewModel::fetchMetadata,
                        findDuplicate = viewModel::findDuplicate,
                        onOpenLink = ::openBookmark,
                        onOpenExisting = { otherId ->
                            viewModel.triggerSync()
                            navController.navigate(AppDestinations.editorRoute(id = otherId)) {
                                popUpTo(AppDestinations.EDITOR) { inclusive = true }
                            }
                        },
                        onTrash = { viewModel.moveToTrash(listOf(it)) },
                        onRestore = { viewModel.restore(listOf(it)) },
                        onDeleteForever = { viewModel.deletePermanently(listOf(it)) },
                        onCreateFolder = viewModel::createFolder
                    )
                )
            }
        }
    }
}
