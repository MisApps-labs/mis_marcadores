// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Marcadores — https://github.com/ferlagod/mis_marcadores

package com.ferlagod.mismarcadores.ui.screen

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.ferlagod.mismarcadores.R
import com.ferlagod.mismarcadores.data.local.entity.BookmarkEntity
import com.ferlagod.mismarcadores.data.local.entity.FolderEntity
import com.ferlagod.mismarcadores.data.local.model.FolderTree
import com.ferlagod.mismarcadores.data.local.model.LinkStatus
import com.ferlagod.mismarcadores.data.local.pref.LayoutMode
import com.ferlagod.mismarcadores.data.local.pref.SortOrder
import com.ferlagod.mismarcadores.data.sync.SyncState
import com.ferlagod.mismarcadores.ui.components.ConfirmDestructiveDialog
import com.ferlagod.mismarcadores.ui.components.FolderDialog
import com.ferlagod.mismarcadores.ui.components.READABLE_MAX_WIDTH
import com.ferlagod.mismarcadores.ui.components.SiteIcon
import com.ferlagod.mismarcadores.ui.components.TagChip
import com.ferlagod.mismarcadores.ui.components.filledFieldColors
import com.ferlagod.mismarcadores.ui.theme.AccentBlue
import com.ferlagod.mismarcadores.ui.theme.AccentGreen
import com.ferlagod.mismarcadores.ui.theme.AccentOrange
import com.ferlagod.mismarcadores.ui.theme.AccentRed
import com.ferlagod.mismarcadores.ui.theme.AccentYellow
import com.ferlagod.mismarcadores.ui.theme.LabelColor
import com.ferlagod.mismarcadores.ui.theme.iconTint
import com.ferlagod.mismarcadores.ui.viewmodel.BookmarkFilter
import com.ferlagod.mismarcadores.ui.viewmodel.TagCount
import com.ferlagod.mismarcadores.ui.viewmodel.comparator
import com.ferlagod.mismarcadores.util.UrlUtils

/** Recuentos de las vistas rápidas de la pantalla de inicio. */
data class HomeCounts(
    val total: Int = 0,
    val favorites: Int = 0,
    val readLater: Int = 0,
    val unsorted: Int = 0,
    val broken: Int = 0,
    val trash: Int = 0
)

/** Acciones de la pantalla de inicio. */
class HomeActions(
    val onSelectFilter: (BookmarkFilter) -> Unit,
    val onSearch: (String) -> Unit,
    val onNewBookmark: () -> Unit,
    val onOpenBookmark: (BookmarkEntity) -> Unit,
    val onEditBookmark: (BookmarkEntity) -> Unit,
    val onCreateFolder: (name: String, color: LabelColor, parentId: String?) -> Unit,
    val onUpdateFolder: (id: String, name: String, color: LabelColor, parentId: String?) -> Unit,
    val onDeleteFolder: (String) -> Unit,
    val onRenameTag: (old: String, new: String) -> Unit,
    val onDeleteTag: (String) -> Unit,
    val onTriggerSync: () -> Unit,
    val onOpenSettings: () -> Unit
)

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    isConfigured: Boolean,
    folders: List<FolderEntity>,
    bookmarks: List<BookmarkEntity>,
    recent: List<BookmarkEntity>,
    tags: List<TagCount>,
    counts: HomeCounts,
    loadPreviews: Boolean,
    layoutMode: LayoutMode,
    sortOrder: SortOrder,
    syncState: SyncState,
    snackbarHostState: SnackbarHostState,
    actions: HomeActions
) {
    val primary = MaterialTheme.colorScheme.primary
    val focusManager = LocalFocusManager.current
    val haptics = LocalHapticFeedback.current
    val listState = rememberLazyListState()
    val fabExpanded by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 } }

    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var parentForNewFolder by remember { mutableStateOf<String?>(null) }
    var folderToEdit by remember { mutableStateOf<FolderEntity?>(null) }
    var folderToDelete by remember { mutableStateOf<FolderEntity?>(null) }
    var tagMenu by remember { mutableStateOf<String?>(null) }
    var tagToRename by remember { mutableStateOf<String?>(null) }
    var tagToDelete by remember { mutableStateOf<String?>(null) }
    var showAllTags by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var expandedFolderIds by rememberSaveable { mutableStateOf(setOf<String>()) }

    val treeItems = remember(folders, bookmarks, expandedFolderIds, sortOrder) {
        FolderTree.buildExpandableTree(folders, bookmarks, expandedFolderIds, bookmarkComparator = sortOrder.comparator())
    }

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = actions.onNewBookmark,
                icon = { Icon(Icons.Rounded.BookmarkAdd, contentDescription = null) },
                text = { Text(stringResource(R.string.new_bookmark), fontWeight = FontWeight.SemiBold) },
                containerColor = primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                expanded = fabExpanded
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val sidePadding = maxOf(16.dp, (maxWidth - READABLE_MAX_WIDTH) / 2)
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(start = sidePadding, end = sidePadding, top = 4.dp, bottom = 104.dp)
            ) {
                item(key = "header") {
                    HomeHeader(
                        total = counts.total,
                        folderCount = folders.size,
                        actions = {
                            SyncStatusIconButton(
                                isConfigured = isConfigured,
                                syncState = syncState,
                                onClick = { if (isConfigured) actions.onTriggerSync() else actions.onOpenSettings() }
                            )
                            IconButton(onClick = actions.onOpenSettings) {
                                Icon(Icons.Rounded.Settings, contentDescription = stringResource(R.string.cd_settings), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    )
                }

                item(key = "search") {
                    GlobalSearchField(
                        query = query,
                        onQueryChange = { query = it },
                        onSearch = {
                            focusManager.clearFocus()
                            actions.onSearch(query.trim())
                            query = ""
                        }
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                }

                if (counts.total == 0 && counts.trash == 0) {
                    item(key = "tip") {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = primary.copy(alpha = 0.10f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Share, contentDescription = null, tint = primary, modifier = Modifier.size(26.dp))
                                Spacer(modifier = Modifier.width(14.dp))
                                Column {
                                    Text(
                                        stringResource(R.string.tip_share_title),
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        stringResource(R.string.tip_share_message),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(18.dp))
                    }
                }

                item(key = "quick") {
                    GroupLabel(stringResource(R.string.quick_views))
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            QuickAccessRow(stringResource(R.string.all_bookmarks), Icons.Rounded.Bookmarks, primary, counts.total) {
                                actions.onSelectFilter(BookmarkFilter.All)
                            }
                            RowDivider(62.dp)
                            QuickAccessRow(stringResource(R.string.favorites), Icons.Rounded.Star, AccentYellow, counts.favorites) {
                                actions.onSelectFilter(BookmarkFilter.Favorites)
                            }
                            RowDivider(62.dp)
                            QuickAccessRow(stringResource(R.string.read_later), Icons.Rounded.Schedule, AccentBlue, counts.readLater) {
                                actions.onSelectFilter(BookmarkFilter.ReadLater)
                            }
                            RowDivider(62.dp)
                            QuickAccessRow(stringResource(R.string.unsorted), Icons.Rounded.Inbox, AccentOrange, counts.unsorted) {
                                actions.onSelectFilter(BookmarkFilter.Unsorted)
                            }
                            if (counts.broken > 0) {
                                RowDivider(62.dp)
                                QuickAccessRow(stringResource(R.string.broken_links), Icons.Rounded.LinkOff, AccentRed, counts.broken) {
                                    actions.onSelectFilter(BookmarkFilter.Broken)
                                }
                            }
                            RowDivider(62.dp)
                            QuickAccessRow(stringResource(R.string.trash), Icons.Rounded.Delete, AccentRed, counts.trash) {
                                actions.onSelectFilter(BookmarkFilter.Trash)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }

                if (recent.isNotEmpty()) {
                    item(key = "recent") {
                        GroupLabel(stringResource(R.string.recently_added))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(recent, key = { it.id }) { b ->
                                RecentCard(
                                    bookmark = b,
                                    loadPreviews = loadPreviews,
                                    onClick = { actions.onOpenBookmark(b) },
                                    onLongClick = {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        actions.onEditBookmark(b)
                                    }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }

                if (tags.isNotEmpty()) {
                    item(key = "tags") {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            GroupLabel(stringResource(R.string.tags), modifier = Modifier.weight(1f))
                            if (tags.size > TAGS_PREVIEW) {
                                TextButton(onClick = { showAllTags = !showAllTags }) {
                                    Text(
                                        if (showAllTags) stringResource(R.string.show_less) else stringResource(R.string.show_all, tags.size),
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            FlowRow(
                                modifier = Modifier.padding(14.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                (if (showAllTags) tags else tags.take(TAGS_PREVIEW)).forEach { tag ->
                                    Box {
                                        Surface(
                                            shape = RoundedCornerShape(50),
                                            color = Color.Transparent,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(50))
                                                .combinedClickable(
                                                    onClick = { actions.onSelectFilter(BookmarkFilter.Tag(tag.name)) },
                                                    onLongClick = {
                                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                        tagMenu = tag.name
                                                    }
                                                )
                                        ) {
                                            TagChip(tag = tag.name, count = tag.count)
                                        }
                                        DropdownMenu(expanded = tagMenu == tag.name, onDismissRequest = { tagMenu = null }) {
                                            DropdownMenuItem(
                                                text = { Text(stringResource(R.string.rename_tag)) },
                                                leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                                onClick = {
                                                    tagMenu = null
                                                    tagToRename = tag.name
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text(stringResource(R.string.delete_tag), color = MaterialTheme.colorScheme.error) },
                                                leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp)) },
                                                onClick = {
                                                    tagMenu = null
                                                    tagToDelete = tag.name
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }

                item(key = "folders_header") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        GroupLabel(stringResource(R.string.my_folders), modifier = Modifier.weight(1f))
                        TextButton(onClick = {
                            parentForNewFolder = null
                            showCreateFolderDialog = true
                        }) {
                            Icon(Icons.Rounded.CreateNewFolder, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.new_folder), fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                if (treeItems.isEmpty()) {
                    item(key = "no_folders") {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surface,
                            onClick = {
                                parentForNewFolder = null
                                showCreateFolderDialog = true
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(Icons.Rounded.CreateNewFolder, contentDescription = null, tint = primary, modifier = Modifier.size(32.dp))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = stringResource(R.string.no_folders),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    itemsIndexed(treeItems, key = { _, item -> item.key }) { index, item ->
                        val shape = when {
                            treeItems.size == 1 -> RoundedCornerShape(14.dp)
                            index == 0 -> RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp)
                            index == treeItems.lastIndex -> RoundedCornerShape(bottomStart = 14.dp, bottomEnd = 14.dp)
                            else -> RoundedCornerShape(0.dp)
                        }
                        Surface(
                            shape = shape,
                            color = MaterialTheme.colorScheme.surface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .animateItem()
                        ) {
                            Column {
                                when (item) {
                                    is FolderTree.TreeItem.FolderItem -> {
                                        val folder = item.folder
                                        FolderRow(
                                            name = folder.name,
                                            tint = LabelColor.fromKey(folder.color).iconTint(),
                                            count = item.bookmarkCount,
                                            indentationLevel = item.level,
                                            expanded = item.expanded,
                                            onClick = {
                                                expandedFolderIds = if (folder.id in expandedFolderIds) {
                                                    expandedFolderIds - folder.id
                                                } else {
                                                    expandedFolderIds + folder.id
                                                }
                                            },
                                            onOpenList = { actions.onSelectFilter(BookmarkFilter.Folder(folder.id, folder.name)) },
                                            onAddSubfolder = {
                                                parentForNewFolder = folder.id
                                                showCreateFolderDialog = true
                                            },
                                            onEdit = { folderToEdit = folder },
                                            onDelete = { folderToDelete = folder }
                                        )
                                    }
                                    is FolderTree.TreeItem.BookmarkItem -> {
                                        FolderBookmarkRow(
                                            bookmark = item.bookmark,
                                            indentationLevel = item.level,
                                            loadPreviews = loadPreviews,
                                            layoutMode = layoutMode,
                                            onClick = { actions.onOpenBookmark(item.bookmark) },
                                            onLongClick = {
                                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                actions.onEditBookmark(item.bookmark)
                                            },
                                            onEdit = { actions.onEditBookmark(item.bookmark) }
                                        )
                                    }
                                }
                                if (index < treeItems.lastIndex) {
                                    val dividerStart = when (item) {
                                        is FolderTree.TreeItem.FolderItem -> (72 + item.level * 18).dp
                                        is FolderTree.TreeItem.BookmarkItem -> (56 + item.level * 18).dp
                                    }
                                    RowDivider(dividerStart)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateFolderDialog) {
        FolderDialog(
            title = if (parentForNewFolder != null) stringResource(R.string.new_subfolder) else stringResource(R.string.new_folder),
            initialName = "",
            initialColor = LabelColor.DEFAULT,
            initialParentId = parentForNewFolder,
            existingFolders = folders,
            onDismiss = { showCreateFolderDialog = false },
            onConfirm = { name, color, parentId ->
                actions.onCreateFolder(name, color, parentId)
                if (parentForNewFolder != null) {
                    expandedFolderIds = expandedFolderIds + parentForNewFolder!!
                }
                if (parentId != null) {
                    expandedFolderIds = expandedFolderIds + parentId
                }
                showCreateFolderDialog = false
            }
        )
    }

    folderToEdit?.let { folder ->
        FolderDialog(
            title = stringResource(R.string.edit_folder),
            initialName = folder.name,
            initialColor = LabelColor.fromKey(folder.color),
            initialParentId = folder.parentId,
            existingFolders = folders,
            currentFolderId = folder.id,
            onDismiss = { folderToEdit = null },
            onConfirm = { name, color, parentId ->
                actions.onUpdateFolder(folder.id, name, color, parentId)
                folderToEdit = null
            }
        )
    }

    folderToDelete?.let { folder ->
        ConfirmDestructiveDialog(
            title = stringResource(R.string.delete_folder_title),
            message = stringResource(R.string.delete_folder_message, folder.name),
            confirmLabel = stringResource(R.string.delete),
            onDismiss = { folderToDelete = null },
            onConfirm = {
                actions.onDeleteFolder(folder.id)
                expandedFolderIds = expandedFolderIds - folder.id
                folderToDelete = null
            }
        )
    }

    tagToRename?.let { tag ->
        RenameTagDialog(
            tag = tag,
            onDismiss = { tagToRename = null },
            onConfirm = { newName ->
                actions.onRenameTag(tag, newName)
                tagToRename = null
            }
        )
    }

    tagToDelete?.let { tag ->
        ConfirmDestructiveDialog(
            title = stringResource(R.string.delete_tag_title),
            message = stringResource(R.string.delete_tag_message, tag),
            confirmLabel = stringResource(R.string.delete),
            onDismiss = { tagToDelete = null },
            onConfirm = {
                actions.onDeleteTag(tag)
                tagToDelete = null
            }
        )
    }
}

private const val TAGS_PREVIEW = 18

@Composable
private fun RenameTagDialog(tag: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf(tag) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rename_tag), fontWeight = FontWeight.Bold) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.replace(",", "").replace("\n", "") },
                singleLine = true,
                prefix = { Text("#") },
                shape = RoundedCornerShape(12.dp),
                colors = filledFieldColors(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (name.isNotBlank()) onConfirm(name) }),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) {
                Text(stringResource(R.string.save), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        },
        shape = RoundedCornerShape(20.dp)
    )
}

/**
 * Cabecera de la pantalla principal: nombre de la app en pequeño, saludo según la hora,
 * resumen y logo redondo. Mismo esquema que el resto de la familia Mis Apps.
 */
@Composable
private fun HomeHeader(total: Int, folderCount: Int, actions: @Composable () -> Unit) {
    val hour = remember { java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY) }
    val greeting = stringResource(
        when (hour) {
            in 6..12 -> R.string.greeting_morning
            in 13..20 -> R.string.greeting_afternoon
            else -> R.string.greeting_night
        }
    )
    val summary = pluralStringResource(R.plurals.home_bookmarks_count, total, total) +
        "  ·  " + pluralStringResource(R.plurals.home_folders_count, folderCount, folderCount)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 4.dp, top = 4.dp, bottom = 18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.app_name).uppercase(),
                style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.2.sp),
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            actions()
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = greeting,
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Image(
                painter = painterResource(R.drawable.ic_logo_round),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .border(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), CircleShape)
            )
        }
    }
}

@Composable
fun GroupLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp),
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 12.dp, bottom = 8.dp, top = 4.dp)
    )
}

@Composable
private fun RowDivider(start: Dp) {
    HorizontalDivider(
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant,
        modifier = Modifier.padding(start = start)
    )
}

@Composable
private fun GlobalSearchField(query: String, onQueryChange: (String) -> Unit, onSearch: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    if (query.isEmpty()) {
                        Text(
                            text = stringResource(R.string.search_everywhere_hint),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
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

@Composable
private fun QuickAccessRow(title: String, icon: ImageVector, iconTint: Color, count: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(iconTint.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(18.dp))
        }
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Text(text = count.toString(), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.width(6.dp))
        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.size(18.dp))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecentCard(bookmark: BookmarkEntity, loadPreviews: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .width(150.dp)
            .clip(RoundedCornerShape(14.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            SiteIcon(url = bookmark.url, faviconUrl = bookmark.faviconUrl, loadImages = loadPreviews, size = 34.dp)
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = bookmark.displayTitle,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                minLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = UrlUtils.displayHost(bookmark.url),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun FolderRow(
    name: String,
    tint: Color,
    count: Int,
    indentationLevel: Int,
    expanded: Boolean,
    onClick: () -> Unit,
    onOpenList: () -> Unit,
    onAddSubfolder: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val chevronRotation by animateFloatAsState(if (expanded) 90f else 0f, label = "folder_chevron")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = (12 + indentationLevel * 18).dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .size(18.dp)
                .rotate(chevronRotation)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Icon(
            imageVector = if (expanded) Icons.Rounded.FolderOpen else Icons.Rounded.Folder,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = count.toString(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box {
            IconButton(onClick = { showMenu = true }) {
                Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.cd_options), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            }
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.view)) },
                    leadingIcon = { Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    onClick = { showMenu = false; onOpenList() }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.new_subfolder)) },
                    leadingIcon = { Icon(Icons.Rounded.CreateNewFolder, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    onClick = { showMenu = false; onAddSubfolder() }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.edit_folder)) },
                    leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    onClick = { showMenu = false; onEdit() }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.delete_folder), color = MaterialTheme.colorScheme.error) },
                    leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp)) },
                    onClick = { showMenu = false; onDelete() }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderBookmarkRow(
    bookmark: BookmarkEntity,
    indentationLevel: Int,
    loadPreviews: Boolean,
    layoutMode: LayoutMode,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onEdit: () -> Unit
) {
    val compact = layoutMode == LayoutMode.COMPACT
    val cards = layoutMode == LayoutMode.CARDS
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(
                start = (16 + indentationLevel * 18).dp,
                top = if (compact) 2.dp else 8.dp,
                bottom = if (compact) 2.dp else 8.dp,
                end = 4.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val imageUrl = bookmark.imageUrl
        if (cards && loadPreviews && imageUrl != null) {
            val context = LocalContext.current
            Box(
                modifier = Modifier
                    .size(width = 84.dp, height = 48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                SiteIcon(url = bookmark.url, faviconUrl = bookmark.faviconUrl, loadImages = loadPreviews, size = 24.dp)
                AsyncImage(
                    model = remember(imageUrl) { ImageRequest.Builder(context).data(imageUrl).crossfade(true).build() },
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        } else {
            SiteIcon(
                url = bookmark.url,
                faviconUrl = bookmark.faviconUrl,
                loadImages = loadPreviews,
                size = when (layoutMode) {
                    LayoutMode.COMPACT -> 20.dp
                    LayoutMode.LIST -> 28.dp
                    LayoutMode.CARDS -> 40.dp
                }
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = bookmark.displayTitle,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = if (cards) 2 else 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!compact) {
                Text(
                    text = UrlUtils.displayHost(bookmark.url),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (cards && bookmark.description.isNotBlank()) {
                Text(
                    text = bookmark.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (bookmark.linkStatus == LinkStatus.BROKEN) {
            Spacer(modifier = Modifier.width(4.dp))
            Icon(Icons.Rounded.LinkOff, contentDescription = stringResource(R.string.broken_link), tint = AccentRed, modifier = Modifier.size(16.dp))
        }
        if (bookmark.isReadLater) {
            Spacer(modifier = Modifier.width(4.dp))
            Icon(Icons.Rounded.Schedule, contentDescription = stringResource(R.string.read_later), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
        }
        if (bookmark.isFavorite) {
            Spacer(modifier = Modifier.width(4.dp))
            Icon(Icons.Rounded.Star, contentDescription = stringResource(R.string.favorite), tint = AccentYellow, modifier = Modifier.size(16.dp))
        }
        IconButton(onClick = onEdit) {
            Icon(
                Icons.Rounded.Edit,
                contentDescription = stringResource(R.string.edit),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun SyncStatusIconButton(isConfigured: Boolean, syncState: SyncState, onClick: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "sync_rotate")
    val angle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(animation = tween(1200, easing = LinearEasing), repeatMode = RepeatMode.Restart),
        label = "angle"
    )
    val (icon, tint, rotating, description) = if (!isConfigured) {
        SyncIconData(Icons.Rounded.CloudOff, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), false, stringResource(R.string.sync_configure))
    } else {
        when (syncState) {
            is SyncState.Idle -> SyncIconData(Icons.Rounded.Sync, MaterialTheme.colorScheme.onSurfaceVariant, false, stringResource(R.string.sync_now))
            is SyncState.Syncing -> SyncIconData(Icons.Rounded.Sync, AccentOrange, true, stringResource(R.string.sync_in_progress))
            is SyncState.Success -> SyncIconData(Icons.Rounded.CheckCircle, AccentGreen, false, stringResource(R.string.sync_done))
            is SyncState.Error -> SyncIconData(Icons.Rounded.ErrorOutline, AccentRed, false, stringResource(R.string.sync_error))
        }
    }
    IconButton(onClick = onClick) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = tint,
            modifier = Modifier
                .size(22.dp)
                .rotate(if (rotating) angle else 0f)
        )
    }
}

private data class SyncIconData(val icon: ImageVector, val tint: Color, val isRotating: Boolean, val description: String)
