// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Marcadores — https://github.com/MisApps-labs/mis_marcadores

package com.ferlagod.mismarcadores.ui.screen

import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.automirrored.rounded.DriveFileMove
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.RestoreFromTrash
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material.icons.rounded.ViewHeadline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.ferlagod.mismarcadores.R
import com.ferlagod.mismarcadores.data.local.entity.BookmarkEntity
import com.ferlagod.mismarcadores.data.local.entity.FolderEntity
import com.ferlagod.mismarcadores.data.local.model.FolderTree
import com.ferlagod.mismarcadores.data.local.model.LinkStatus
import com.ferlagod.mismarcadores.data.local.pref.AppSettings
import com.ferlagod.mismarcadores.data.local.pref.LayoutMode
import com.ferlagod.mismarcadores.data.local.pref.SortOrder
import com.ferlagod.mismarcadores.data.local.pref.TapAction
import com.ferlagod.mismarcadores.ui.components.ColorPalette
import com.ferlagod.mismarcadores.ui.components.ConfirmDestructiveDialog
import com.ferlagod.mismarcadores.ui.components.EmptyState
import com.ferlagod.mismarcadores.ui.components.FolderDialog
import com.ferlagod.mismarcadores.ui.components.FolderPickerDialog
import com.ferlagod.mismarcadores.ui.components.SiteIcon
import com.ferlagod.mismarcadores.ui.components.TagChip
import com.ferlagod.mismarcadores.ui.theme.AccentRed
import com.ferlagod.mismarcadores.ui.theme.AccentYellow
import com.ferlagod.mismarcadores.ui.theme.LabelColor
import com.ferlagod.mismarcadores.ui.theme.iconTint
import com.ferlagod.mismarcadores.ui.theme.toCardBackground
import com.ferlagod.mismarcadores.ui.theme.toCardBorder
import com.ferlagod.mismarcadores.ui.viewmodel.BookmarkFilter
import com.ferlagod.mismarcadores.util.LinkOpener
import com.ferlagod.mismarcadores.util.UrlUtils

/** Acciones de la lista de marcadores. */
class BookmarksActions(
    val onBack: () -> Unit,
    val onSearchQueryChange: (String) -> Unit,
    val onLayoutChange: (LayoutMode) -> Unit,
    val onSortOrderChange: (SortOrder) -> Unit,
    val onOpen: (BookmarkEntity) -> Unit,
    val onEdit: (BookmarkEntity) -> Unit,
    val onNew: () -> Unit,
    val onSetFavorite: (Collection<String>, Boolean) -> Unit,
    val onSetReadLater: (Collection<String>, Boolean) -> Unit,
    val onSetColor: (Collection<String>, LabelColor) -> Unit,
    val onMove: (Collection<String>, String?) -> Unit,
    val onAddTags: (Collection<String>, List<String>) -> Unit,
    val onTrash: (Collection<String>) -> Unit,
    val onRestore: (Collection<String>) -> Unit,
    val onDeletePermanently: (Collection<String>) -> Unit,
    val onEmptyTrash: () -> Unit,
    val onMarkLinkOk: (Collection<String>) -> Unit,
    val onCreateFolder: (name: String, color: LabelColor, parentId: String?, onCreated: (String) -> Unit) -> Unit,
    val onOpenFolder: (FolderEntity) -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarksScreen(
    bookmarks: List<BookmarkEntity>,
    folders: List<FolderEntity>,
    foldersMap: Map<String, FolderEntity>,
    allTags: List<String>,
    filter: BookmarkFilter,
    searchQuery: String,
    layoutMode: LayoutMode,
    sortOrder: SortOrder,
    tapAction: TapAction,
    loadPreviews: Boolean,
    autoPurgeTrash: Boolean,
    canGoUp: Boolean,
    folderBookmarkCounts: Map<String?, Int>,
    snackbarHostState: SnackbarHostState,
    actions: BookmarksActions
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val isTrash = filter is BookmarkFilter.Trash
    var selected by rememberSaveable { mutableStateOf(setOf<String>()) }
    val selectionMode = selected.isNotEmpty()
    // Si un marcador seleccionado desaparece (sincronización, borrado), deja de estar seleccionado.
    LaunchedEffect(bookmarks) {
        val ids = bookmarks.mapTo(HashSet()) { it.id }
        if (selected.any { it !in ids }) selected = selected.filterTo(HashSet()) { it in ids }
    }
    BackHandler(enabled = selectionMode) { selected = emptySet() }
    // Dentro de una subcarpeta, "Atrás" sube a la carpeta anterior.
    BackHandler(enabled = !selectionMode && canGoUp) { actions.onBack() }

    // Subcarpetas y ruta de la carpeta abierta.
    val currentFolderId = (filter as? BookmarkFilter.Folder)?.folderId
    val subfolders = remember(folders, currentFolderId) {
        if (currentFolderId == null) emptyList()
        else folders.filter { it.parentId == currentFolderId }.sortedBy { it.name.lowercase() }
    }
    val path = remember(foldersMap, currentFolderId) {
        FolderTree.path(foldersMap, currentFolderId)
    }
    var showNewSubfolder by remember { mutableStateOf(false) }

    var showMovePicker by remember { mutableStateOf<Set<String>?>(null) }
    var showTagDialog by remember { mutableStateOf<Set<String>?>(null) }
    var showColorDialog by remember { mutableStateOf<Set<String>?>(null) }
    var confirmDelete by remember { mutableStateOf<Set<String>?>(null) }
    var confirmEmptyTrash by remember { mutableStateOf(false) }
    var newFolderFor by remember { mutableStateOf<Set<String>?>(null) }

    val title = when (filter) {
        BookmarkFilter.All -> stringResource(R.string.all_bookmarks)
        BookmarkFilter.Favorites -> stringResource(R.string.favorites)
        BookmarkFilter.ReadLater -> stringResource(R.string.read_later)
        BookmarkFilter.Unsorted -> stringResource(R.string.unsorted)
        BookmarkFilter.Broken -> stringResource(R.string.broken_links)
        BookmarkFilter.Trash -> stringResource(R.string.trash)
        is BookmarkFilter.Folder -> filter.folderName
        is BookmarkFilter.Tag -> "#" + filter.tag
    }

    fun clearSelection() {
        selected = emptySet()
    }

    Scaffold(
        topBar = {
            if (selectionMode) {
                SelectionBar(
                    count = selected.size,
                    isTrash = isTrash,
                    onClose = ::clearSelection,
                    onSelectAll = { selected = bookmarks.mapTo(HashSet()) { it.id } },
                    onFavorite = {
                        val allFav = bookmarks.filter { it.id in selected }.all { it.isFavorite }
                        actions.onSetFavorite(selected, !allFav)
                        clearSelection()
                    },
                    onReadLater = {
                        val all = bookmarks.filter { it.id in selected }.all { it.isReadLater }
                        actions.onSetReadLater(selected, !all)
                        clearSelection()
                    },
                    onMove = { showMovePicker = selected },
                    onTag = { showTagDialog = selected },
                    onColor = { showColorDialog = selected },
                    onShare = {
                        LinkOpener.shareMany(context, bookmarks.filter { it.id in selected }.map { it.title to it.url })
                        clearSelection()
                    },
                    onTrash = {
                        actions.onTrash(selected)
                        clearSelection()
                    },
                    onRestore = {
                        actions.onRestore(selected)
                        clearSelection()
                    },
                    onDeleteForever = { confirmDelete = selected }
                )
            } else {
                TopAppBar(
                    title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = {
                        IconButton(onClick = actions.onBack) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.cd_back), tint = MaterialTheme.colorScheme.primary)
                        }
                    },
                    actions = {
                        if (!isTrash) SortMenu(current = sortOrder, onSelect = actions.onSortOrderChange)
                        LayoutMenu(current = layoutMode, onSelect = actions.onLayoutChange)
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
                )
            }
        },
        floatingActionButton = {
            if (!isTrash && !selectionMode) {
                FloatingActionButton(
                    onClick = actions.onNew,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(Icons.Rounded.BookmarkAdd, contentDescription = stringResource(R.string.new_bookmark))
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        val minCell = if (layoutMode == LayoutMode.CARDS) 165.dp else 340.dp
        val gridState = rememberLazyGridState()
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minCell),
            state = gridState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(if (layoutMode == LayoutMode.COMPACT) 6.dp else 10.dp)
        ) {
            item(key = "search", span = { GridItemSpan(maxLineSpan) }) {
                SearchField(
                    query = searchQuery,
                    hint = stringResource(if (isTrash) R.string.search_trash_hint else R.string.search_hint),
                    onQueryChange = actions.onSearchQueryChange
                )
            }
            if (currentFolderId != null && searchQuery.isBlank()) {
                item(key = "subfolders", span = { GridItemSpan(maxLineSpan) }) {
                    SubfolderSection(
                        path = path.map { it.name },
                        subfolders = subfolders,
                        counts = folderBookmarkCounts,
                        onOpen = actions.onOpenFolder,
                        onNewSubfolder = { showNewSubfolder = true }
                    )
                }
            }
            if (isTrash && bookmarks.isNotEmpty()) {
                item(key = "trash_banner", span = { GridItemSpan(maxLineSpan) }) {
                    InfoBanner(
                        text = if (autoPurgeTrash) pluralStringResource(R.plurals.trash_banner_auto, AppSettings.TRASH_RETENTION_DAYS, AppSettings.TRASH_RETENTION_DAYS)
                        else stringResource(R.string.trash_banner),
                        actionLabel = stringResource(R.string.empty_trash),
                        onAction = { confirmEmptyTrash = true }
                    )
                }
            }
            if (filter is BookmarkFilter.Broken && bookmarks.isNotEmpty()) {
                item(key = "broken_banner", span = { GridItemSpan(maxLineSpan) }) {
                    InfoBanner(text = stringResource(R.string.broken_banner))
                }
            }
            if (bookmarks.isEmpty() && (subfolders.isEmpty() || searchQuery.isNotBlank())) {
                item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                    Box(modifier = Modifier.fillMaxWidth().padding(top = 64.dp), contentAlignment = Alignment.Center) {
                        when {
                            searchQuery.isNotBlank() -> EmptyState(Icons.Rounded.Search, stringResource(R.string.empty_search_title), message = stringResource(R.string.empty_search_message))
                            isTrash -> EmptyState(Icons.Rounded.Delete, stringResource(R.string.empty_trash_title), message = stringResource(R.string.empty_trash_message))
                            filter is BookmarkFilter.Favorites -> EmptyState(Icons.Rounded.Star, stringResource(R.string.empty_favorites_title), message = stringResource(R.string.empty_favorites_message))
                            filter is BookmarkFilter.ReadLater -> EmptyState(Icons.Rounded.Schedule, stringResource(R.string.empty_read_later_title), message = stringResource(R.string.empty_read_later_message))
                            filter is BookmarkFilter.Broken -> EmptyState(Icons.Rounded.CheckCircle, stringResource(R.string.empty_broken_title))
                            else -> EmptyState(Icons.Rounded.Bookmarks, stringResource(R.string.empty_title), message = stringResource(R.string.empty_message))
                        }
                    }
                }
            }
            items(bookmarks, key = { it.id }) { bookmark ->
                val isSelected = bookmark.id in selected
                val onClick = {
                    when {
                        selectionMode -> selected = if (isSelected) selected - bookmark.id else selected + bookmark.id
                        isTrash -> actions.onEdit(bookmark)
                        tapAction == TapAction.EDIT -> actions.onEdit(bookmark)
                        else -> actions.onOpen(bookmark)
                    }
                }
                val onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    selected = if (isSelected) selected - bookmark.id else selected + bookmark.id
                }
                val menu: @Composable () -> Unit = {
                    if (!selectionMode) {
                        ItemMenu(
                            bookmark = bookmark,
                            isTrash = isTrash,
                            onOpen = { actions.onOpen(bookmark) },
                            onEdit = { actions.onEdit(bookmark) },
                            onCopy = { LinkOpener.copy(context, bookmark.url) },
                            onShare = { LinkOpener.share(context, bookmark.title, bookmark.url) },
                            onFavorite = { actions.onSetFavorite(listOf(bookmark.id), !bookmark.isFavorite) },
                            onReadLater = { actions.onSetReadLater(listOf(bookmark.id), !bookmark.isReadLater) },
                            onMove = { showMovePicker = setOf(bookmark.id) },
                            onMarkOk = { actions.onMarkLinkOk(listOf(bookmark.id)) },
                            onTrash = { actions.onTrash(listOf(bookmark.id)) },
                            onRestore = { actions.onRestore(listOf(bookmark.id)) },
                            onDeleteForever = { confirmDelete = setOf(bookmark.id) }
                        )
                    }
                }
                val folderName = bookmark.folderId?.let { foldersMap[it]?.name }
                    ?.takeIf { filter !is BookmarkFilter.Folder }
                Box(modifier = Modifier.animateItem()) {
                    when (layoutMode) {
                        LayoutMode.LIST -> BookmarkListItem(bookmark, folderName, isSelected, loadPreviews, onClick, onLongClick, menu)
                        LayoutMode.COMPACT -> BookmarkCompactItem(bookmark, isSelected, loadPreviews, onClick, onLongClick, menu)
                        LayoutMode.CARDS -> BookmarkCard(bookmark, isSelected, loadPreviews, onClick, onLongClick, menu)
                    }
                }
            }
        }
    }

    showMovePicker?.let { ids ->
        FolderPickerDialog(
            title = stringResource(R.string.move_to),
            folders = folders,
            selectedId = bookmarks.firstOrNull { it.id in ids }?.folderId?.takeIf { ids.size == 1 },
            onDismiss = { showMovePicker = null },
            onSelect = { folderId ->
                actions.onMove(ids, folderId)
                showMovePicker = null
                clearSelection()
            },
            onCreateNew = {
                newFolderFor = ids
                showMovePicker = null
            }
        )
    }

    newFolderFor?.let { ids ->
        FolderDialog(
            title = stringResource(R.string.new_folder),
            initialName = "",
            initialColor = LabelColor.DEFAULT,
            initialParentId = null,
            existingFolders = folders,
            onDismiss = { newFolderFor = null },
            onConfirm = { name, color, parentId ->
                actions.onCreateFolder(name, color, parentId) { id -> actions.onMove(ids, id) }
                newFolderFor = null
                clearSelection()
            }
        )
    }

    if (showNewSubfolder && currentFolderId != null) {
        FolderDialog(
            title = stringResource(R.string.new_subfolder),
            initialName = "",
            initialColor = LabelColor.DEFAULT,
            initialParentId = currentFolderId,
            existingFolders = folders,
            onDismiss = { showNewSubfolder = false },
            onConfirm = { name, color, parentId ->
                actions.onCreateFolder(name, color, parentId) {}
                showNewSubfolder = false
            }
        )
    }

    showTagDialog?.let { ids ->
        AddTagsDialog(
            suggestions = allTags,
            onDismiss = { showTagDialog = null },
            onConfirm = { tags ->
                actions.onAddTags(ids, tags)
                showTagDialog = null
                clearSelection()
            }
        )
    }

    showColorDialog?.let { ids ->
        AlertDialog(
            onDismissRequest = { showColorDialog = null },
            title = { Text(stringResource(R.string.color), fontWeight = FontWeight.Bold) },
            text = {
                ColorPalette(selected = null, onSelect = { color ->
                    actions.onSetColor(ids, color)
                    showColorDialog = null
                    clearSelection()
                })
            },
            confirmButton = { TextButton(onClick = { showColorDialog = null }) { Text(stringResource(R.string.cancel)) } },
            shape = RoundedCornerShape(20.dp)
        )
    }

    confirmDelete?.let { ids ->
        ConfirmDestructiveDialog(
            title = stringResource(R.string.delete_forever_title),
            message = pluralStringResource(R.plurals.delete_forever_message, ids.size, ids.size),
            confirmLabel = stringResource(R.string.delete_forever),
            onDismiss = { confirmDelete = null },
            onConfirm = {
                actions.onDeletePermanently(ids)
                confirmDelete = null
                clearSelection()
            }
        )
    }

    if (confirmEmptyTrash) {
        ConfirmDestructiveDialog(
            title = stringResource(R.string.empty_trash_confirm_title),
            message = stringResource(R.string.empty_trash_confirm_message),
            confirmLabel = stringResource(R.string.empty_trash),
            onDismiss = { confirmEmptyTrash = false },
            onConfirm = {
                actions.onEmptyTrash()
                confirmEmptyTrash = false
            }
        )
    }
}

// ----------------------------------------------------------------------
// Elementos de la lista
// ----------------------------------------------------------------------

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ItemSurface(
    bookmark: BookmarkEntity,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    content: @Composable () -> Unit
) {
    val color = LabelColor.fromKey(bookmark.color)
    val primary = MaterialTheme.colorScheme.primary
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (selected) primary.copy(alpha = 0.16f) else color.toCardBackground(),
        border = when {
            selected -> BorderStroke(2.dp, primary)
            color != LabelColor.DEFAULT -> BorderStroke(1.dp, color.toCardBorder())
            else -> null
        },
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        content()
    }
}

@Composable
private fun StatusIcons(bookmark: BookmarkEntity) {
    if (bookmark.linkStatus == LinkStatus.BROKEN) {
        Icon(Icons.Rounded.LinkOff, contentDescription = stringResource(R.string.broken_link), tint = AccentRed, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(4.dp))
    }
    if (bookmark.isReadLater) {
        Icon(Icons.Rounded.Schedule, contentDescription = stringResource(R.string.read_later), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(4.dp))
    }
    if (bookmark.isFavorite) {
        Icon(Icons.Rounded.Star, contentDescription = stringResource(R.string.favorite), tint = AccentYellow, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun relativeDate(timestamp: Long): String =
    DateUtils.getRelativeTimeSpanString(timestamp, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE).toString()

@Composable
private fun BookmarkListItem(
    bookmark: BookmarkEntity,
    folderName: String?,
    selected: Boolean,
    loadPreviews: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    menu: @Composable () -> Unit
) {
    ItemSurface(bookmark, selected, onClick, onLongClick) {
        Row(modifier = Modifier.padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 2.dp)) {
            SiteIcon(url = bookmark.url, faviconUrl = bookmark.faviconUrl, loadImages = loadPreviews, size = 40.dp)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = bookmark.displayTitle,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    StatusIcons(bookmark)
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = listOfNotNull(UrlUtils.displayHost(bookmark.url), folderName, relativeDate(bookmark.createdAt)).joinToString("  ·  "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (bookmark.description.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = bookmark.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                TagRow(bookmark.tagList)
            }
            Box { menu() }
        }
    }
}

@Composable
private fun BookmarkCompactItem(
    bookmark: BookmarkEntity,
    selected: Boolean,
    loadPreviews: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    menu: @Composable () -> Unit
) {
    ItemSurface(bookmark, selected, onClick, onLongClick) {
        Row(
            modifier = Modifier.padding(start = 12.dp, top = 4.dp, bottom = 4.dp, end = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SiteIcon(url = bookmark.url, faviconUrl = bookmark.faviconUrl, loadImages = loadPreviews, size = 28.dp)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = bookmark.displayTitle,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = UrlUtils.displayUrl(bookmark.url),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            StatusIcons(bookmark)
            Box { menu() }
        }
    }
}

@Composable
private fun BookmarkCard(
    bookmark: BookmarkEntity,
    selected: Boolean,
    loadPreviews: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    menu: @Composable () -> Unit
) {
    ItemSurface(bookmark, selected, onClick, onLongClick) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.91f)
                    .background(LabelColor.fromKey(bookmark.color).iconTint().copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                SiteIcon(url = bookmark.url, faviconUrl = bookmark.faviconUrl, loadImages = loadPreviews, size = 44.dp)
                if (loadPreviews && bookmark.imageUrl != null) {
                    val context = LocalContext.current
                    AsyncImage(
                        model = remember(bookmark.imageUrl) { ImageRequest.Builder(context).data(bookmark.imageUrl).crossfade(true).build() },
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                ) {
                    if (bookmark.isFavorite || bookmark.isReadLater || bookmark.linkStatus == LinkStatus.BROKEN) {
                        Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)) {
                            Row(modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)) { StatusIcons(bookmark) }
                        }
                    }
                }
            }
            Row(modifier = Modifier.padding(start = 12.dp, top = 10.dp, bottom = 10.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = bookmark.displayTitle,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        minLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = UrlUtils.displayHost(bookmark.url),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Box { menu() }
            }
        }
    }
}

@Composable
private fun TagRow(tags: List<String>) {
    if (tags.isEmpty()) return
    Spacer(modifier = Modifier.height(6.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        tags.take(3).forEach { TagChip(tag = it, modifier = Modifier.weight(1f, fill = false)) }
        if (tags.size > 3) {
            Text(
                "+${tags.size - 3}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterVertically)
            )
        }
    }
}

@Composable
private fun ItemMenu(
    bookmark: BookmarkEntity,
    isTrash: Boolean,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onFavorite: () -> Unit,
    onReadLater: () -> Unit,
    onMove: () -> Unit,
    onMarkOk: () -> Unit,
    onTrash: () -> Unit,
    onRestore: () -> Unit,
    onDeleteForever: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    IconButton(onClick = { expanded = true }) {
        Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.cd_options), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        @Composable
        fun entry(textRes: Int, icon: androidx.compose.ui.graphics.vector.ImageVector, destructive: Boolean = false, action: () -> Unit) {
            val tint = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
            DropdownMenuItem(
                text = { Text(stringResource(textRes), color = tint) },
                leadingIcon = { Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp)) },
                onClick = {
                    expanded = false
                    action()
                }
            )
        }
        if (isTrash) {
            entry(R.string.restore, Icons.Rounded.RestoreFromTrash, action = onRestore)
            entry(R.string.delete_forever, Icons.Rounded.DeleteForever, destructive = true, action = onDeleteForever)
        } else {
            entry(R.string.open_link, Icons.AutoMirrored.Rounded.OpenInNew, action = onOpen)
            entry(R.string.edit, Icons.Rounded.Edit, action = onEdit)
            entry(R.string.copy_link, Icons.Rounded.ContentCopy, action = onCopy)
            entry(R.string.share_link, Icons.Rounded.Share, action = onShare)
            HorizontalDivider()
            entry(if (bookmark.isFavorite) R.string.remove_favorite else R.string.add_favorite, if (bookmark.isFavorite) Icons.Rounded.StarBorder else Icons.Rounded.Star, action = onFavorite)
            entry(if (bookmark.isReadLater) R.string.remove_read_later else R.string.add_read_later, Icons.Rounded.Schedule, action = onReadLater)
            entry(R.string.move_to, Icons.AutoMirrored.Rounded.DriveFileMove, action = onMove)
            if (bookmark.linkStatus == LinkStatus.BROKEN) entry(R.string.mark_link_ok, Icons.Rounded.CheckCircle, action = onMarkOk)
            HorizontalDivider()
            entry(R.string.move_to_trash, Icons.Rounded.Delete, destructive = true, action = onTrash)
        }
    }
}

// ----------------------------------------------------------------------
// Barras y menús
// ----------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionBar(
    count: Int,
    isTrash: Boolean,
    onClose: () -> Unit,
    onSelectAll: () -> Unit,
    onFavorite: () -> Unit,
    onReadLater: () -> Unit,
    onMove: () -> Unit,
    onTag: () -> Unit,
    onColor: () -> Unit,
    onShare: () -> Unit,
    onTrash: () -> Unit,
    onRestore: () -> Unit,
    onDeleteForever: () -> Unit
) {
    var more by remember { mutableStateOf(false) }
    TopAppBar(
        title = { Text(pluralStringResource(R.plurals.selected_count, count, count)) },
        navigationIcon = {
            IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.cd_clear_selection)) }
        },
        actions = {
            if (isTrash) {
                IconButton(onClick = onSelectAll) { Icon(Icons.Rounded.SelectAll, contentDescription = stringResource(R.string.select_all)) }
                IconButton(onClick = onRestore) { Icon(Icons.Rounded.RestoreFromTrash, contentDescription = stringResource(R.string.restore)) }
                IconButton(onClick = onDeleteForever) { Icon(Icons.Rounded.DeleteForever, contentDescription = stringResource(R.string.delete_forever), tint = MaterialTheme.colorScheme.error) }
            } else {
                IconButton(onClick = onFavorite) { Icon(Icons.Rounded.Star, contentDescription = stringResource(R.string.favorite)) }
                IconButton(onClick = onMove) { Icon(Icons.AutoMirrored.Rounded.DriveFileMove, contentDescription = stringResource(R.string.move_to)) }
                IconButton(onClick = onTrash) { Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.move_to_trash)) }
                Box {
                    IconButton(onClick = { more = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.cd_options)) }
                    DropdownMenu(expanded = more, onDismissRequest = { more = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.select_all)) }, leadingIcon = { Icon(Icons.Rounded.SelectAll, null) }, onClick = { more = false; onSelectAll() })
                        DropdownMenuItem(text = { Text(stringResource(R.string.read_later)) }, leadingIcon = { Icon(Icons.Rounded.Schedule, null) }, onClick = { more = false; onReadLater() })
                        DropdownMenuItem(text = { Text(stringResource(R.string.add_tags)) }, leadingIcon = { Icon(Icons.Rounded.Tag, null) }, onClick = { more = false; onTag() })
                        DropdownMenuItem(text = { Text(stringResource(R.string.color)) }, leadingIcon = { Icon(Icons.Rounded.Palette, null) }, onClick = { more = false; onColor() })
                        DropdownMenuItem(text = { Text(stringResource(R.string.share_link)) }, leadingIcon = { Icon(Icons.Rounded.Share, null) }, onClick = { more = false; onShare() })
                    }
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    )
}

@Composable
private fun SortMenu(current: SortOrder, onSelect: (SortOrder) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.AutoMirrored.Rounded.Sort, contentDescription = stringResource(R.string.sort_by), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            SortOrder.entries.forEach { order ->
                DropdownMenuItem(
                    text = { Text(stringResource(order.labelRes())) },
                    leadingIcon = { RadioButton(selected = order == current, onClick = null) },
                    onClick = {
                        onSelect(order)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun LayoutMenu(current: LayoutMode, onSelect: (LayoutMode) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(current.icon(), contentDescription = stringResource(R.string.layout), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            LayoutMode.entries.forEach { mode ->
                DropdownMenuItem(
                    text = { Text(stringResource(mode.labelRes())) },
                    leadingIcon = { Icon(mode.icon(), contentDescription = null) },
                    trailingIcon = { RadioButton(selected = mode == current, onClick = null) },
                    onClick = {
                        onSelect(mode)
                        expanded = false
                    }
                )
            }
        }
    }
}

fun LayoutMode.icon() = when (this) {
    LayoutMode.LIST -> Icons.Rounded.ViewAgenda
    LayoutMode.CARDS -> Icons.Rounded.GridView
    LayoutMode.COMPACT -> Icons.Rounded.ViewHeadline
}

fun LayoutMode.labelRes(): Int = when (this) {
    LayoutMode.LIST -> R.string.layout_list
    LayoutMode.CARDS -> R.string.layout_cards
    LayoutMode.COMPACT -> R.string.layout_compact
}

fun SortOrder.labelRes(): Int = when (this) {
    SortOrder.CREATED_DESC -> R.string.sort_created_desc
    SortOrder.CREATED_ASC -> R.string.sort_created_asc
    SortOrder.MODIFIED_DESC -> R.string.sort_modified_desc
    SortOrder.TITLE_ASC -> R.string.sort_title_asc
    SortOrder.TITLE_DESC -> R.string.sort_title_desc
    SortOrder.DOMAIN_ASC -> R.string.sort_domain
    SortOrder.MOST_OPENED -> R.string.sort_most_opened
    SortOrder.RECENTLY_OPENED -> R.string.sort_recently_opened
}

@Composable
private fun SearchField(query: String, hint: String, onQueryChange: (String) -> Unit) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(10.dp))
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {}),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    if (query.isEmpty()) {
                        Text(hint, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                    }
                    inner()
                }
            )
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Rounded.Clear, contentDescription = stringResource(R.string.cd_clear_search), modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

/** Ruta de la carpeta abierta y sus subcarpetas, para navegar por la jerarquía. */
@Composable
private fun SubfolderSection(
    path: List<String>,
    subfolders: List<FolderEntity>,
    counts: Map<String?, Int>,
    onOpen: (FolderEntity) -> Unit,
    onNewSubfolder: () -> Unit
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = path.joinToString("  ›  "),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp)
            )
            TextButton(onClick = onNewSubfolder) {
                Icon(Icons.Rounded.CreateNewFolder, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.new_subfolder), fontWeight = FontWeight.SemiBold)
            }
        }
        if (subfolders.isNotEmpty()) {
            Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                Column {
                    subfolders.forEachIndexed { index, folder ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpen(folder) }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.Folder, contentDescription = null, tint = LabelColor.fromKey(folder.color).iconTint(), modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(14.dp))
                            Text(
                                folder.name,
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text((counts[folder.id] ?: 0).toString(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.size(18.dp))
                        }
                        if (index < subfolders.lastIndex) {
                            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(start = 54.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoBanner(text: String, actionLabel: String? = null, onAction: () -> Unit = {}) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 8.dp)
            )
            if (actionLabel != null) {
                TextButton(onClick = onAction) { Text(actionLabel, color = AccentRed, fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}

/** Diálogo para añadir etiquetas a varios marcadores. */
@Composable
private fun AddTagsDialog(suggestions: List<String>, onDismiss: () -> Unit, onConfirm: (List<String>) -> Unit) {
    var tags by remember { mutableStateOf(listOf<String>()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_tags), fontWeight = FontWeight.Bold) },
        text = {
            TagEditor(
                tags = tags,
                suggestions = suggestions,
                onTagsChange = { tags = it }
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(tags) }, enabled = tags.isNotEmpty()) {
                Text(stringResource(R.string.add), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        shape = RoundedCornerShape(20.dp)
    )
}
