// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Marcadores — https://github.com/ferlagod/mis_marcadores

package com.ferlagod.mismarcadores.ui.screen

import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.HideImage
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.RestoreFromTrash
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material.icons.rounded.Title
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.ferlagod.mismarcadores.R
import com.ferlagod.mismarcadores.data.local.entity.BookmarkEntity
import com.ferlagod.mismarcadores.data.local.entity.FolderEntity
import com.ferlagod.mismarcadores.data.local.model.Tags
import com.ferlagod.mismarcadores.data.metadata.LinkMetadata
import com.ferlagod.mismarcadores.ui.components.ColorPalette
import com.ferlagod.mismarcadores.ui.components.ConfirmDestructiveDialog
import com.ferlagod.mismarcadores.ui.components.FolderDialog
import com.ferlagod.mismarcadores.ui.components.FolderPickerDialog
import com.ferlagod.mismarcadores.ui.components.SiteIcon
import com.ferlagod.mismarcadores.ui.components.TagChip
import com.ferlagod.mismarcadores.ui.components.filledFieldColors
import com.ferlagod.mismarcadores.ui.components.label
import com.ferlagod.mismarcadores.ui.theme.AccentOrange
import com.ferlagod.mismarcadores.ui.theme.AccentYellow
import com.ferlagod.mismarcadores.ui.theme.LabelColor
import com.ferlagod.mismarcadores.ui.theme.iconTint
import com.ferlagod.mismarcadores.ui.components.READABLE_MAX_WIDTH
import com.ferlagod.mismarcadores.ui.viewmodel.BookmarkDraft
import com.ferlagod.mismarcadores.util.LinkOpener
import com.ferlagod.mismarcadores.util.UrlUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Date

/** Valores iniciales de un marcador nuevo. */
data class NewBookmarkDefaults(
    val url: String? = null,
    val title: String? = null,
    val folderId: String? = null,
    val tag: String? = null,
    val favorite: Boolean = false,
    val readLater: Boolean = false
)

/** Operaciones que el editor pide al ViewModel. */
class EditorActions(
    val onBack: () -> Unit,
    val onSave: suspend (draft: BookmarkDraft, original: BookmarkDraft) -> String?,
    val fetchMetadata: suspend (String) -> LinkMetadata?,
    val findDuplicate: suspend (url: String, excludeId: String?) -> BookmarkEntity?,
    val onOpenLink: (BookmarkEntity) -> Unit,
    val onOpenExisting: (String) -> Unit,
    val onTrash: (String) -> Unit,
    val onRestore: (String) -> Unit,
    val onDeleteForever: (String) -> Unit,
    val onCreateFolder: (name: String, color: LabelColor, parentId: String?, onCreated: (String) -> Unit) -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarkEditorScreen(
    existing: BookmarkEntity?,
    defaults: NewBookmarkDefaults,
    folders: List<FolderEntity>,
    allTags: List<String>,
    autoFetch: Boolean,
    loadPreviews: Boolean,
    snackbarHostState: SnackbarHostState,
    actions: EditorActions
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val isNew = existing == null
    val isTrashed = existing?.isDeleted == true

    var url by rememberSaveable { mutableStateOf(existing?.url ?: defaults.url.orEmpty()) }
    var title by rememberSaveable { mutableStateOf(existing?.title ?: defaults.title.orEmpty()) }
    var description by rememberSaveable { mutableStateOf(existing?.description.orEmpty()) }
    var notes by rememberSaveable { mutableStateOf(existing?.notes.orEmpty()) }
    var tags by rememberSaveable { mutableStateOf(existing?.tagList ?: listOfNotNull(defaults.tag)) }
    var folderId by rememberSaveable { mutableStateOf(existing?.folderId ?: defaults.folderId) }
    var color by rememberSaveable { mutableStateOf(LabelColor.fromKey(existing?.color)) }
    var isFavorite by rememberSaveable { mutableStateOf(existing?.isFavorite ?: defaults.favorite) }
    var isReadLater by rememberSaveable { mutableStateOf(existing?.isReadLater ?: defaults.readLater) }
    var imageUrl by rememberSaveable { mutableStateOf(existing?.imageUrl) }
    var faviconUrl by rememberSaveable { mutableStateOf(existing?.faviconUrl) }

    var fetching by remember { mutableStateOf(false) }
    var fetchedFor by rememberSaveable { mutableStateOf(if (isNew) null else existing?.url) }
    var urlError by remember { mutableStateOf<String?>(null) }
    var duplicate by remember { mutableStateOf<BookmarkEntity?>(null) }
    var saving by remember { mutableStateOf(false) }
    var showFolderPicker by remember { mutableStateOf(false) }
    var showNewFolder by remember { mutableStateOf(false) }
    var showDiscard by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var showColors by rememberSaveable { mutableStateOf(existing?.color != null && existing.color != LabelColor.DEFAULT.key) }
    val clipboardUrl = remember { if (isNew && defaults.url == null) LinkOpener.clipboardUrl(context) else null }

    val msgInvalidUrl = stringResource(R.string.error_invalid_url)
    val msgFetchFailed = stringResource(R.string.fetch_failed)

    val draft = BookmarkDraft(
        id = existing?.id, url = url, title = title, description = description, notes = notes, tags = tags,
        folderId = folderId, color = color, isFavorite = isFavorite, isReadLater = isReadLater,
        imageUrl = imageUrl, faviconUrl = faviconUrl
    )
    val initialDraft = remember {
        draft.copy(
            url = existing?.url ?: defaults.url.orEmpty(),
            title = existing?.title ?: defaults.title.orEmpty(),
            description = existing?.description.orEmpty(),
            notes = existing?.notes.orEmpty(),
            tags = existing?.tagList ?: listOfNotNull(defaults.tag),
            folderId = existing?.folderId ?: defaults.folderId,
            color = LabelColor.fromKey(existing?.color),
            isFavorite = existing?.isFavorite ?: defaults.favorite,
            isReadLater = existing?.isReadLater ?: defaults.readLater,
            imageUrl = existing?.imageUrl,
            faviconUrl = existing?.faviconUrl
        )
    }
    val hasChanges = draft != initialDraft

    fun applyMetadata(meta: LinkMetadata, overwriteText: Boolean) {
        if (overwriteText || title.isBlank()) meta.title?.let { title = it }
        if (overwriteText || description.isBlank()) meta.description?.let { description = it }
        meta.imageUrl?.let { imageUrl = it }
        meta.faviconUrl?.let { faviconUrl = it }
    }

    fun fetch(overwriteText: Boolean) {
        val target = UrlUtils.normalize(url) ?: run {
            urlError = msgInvalidUrl
            return
        }
        scope.launch {
            fetching = true
            val meta = actions.fetchMetadata(target)
            fetching = false
            fetchedFor = url
            if (meta != null) applyMetadata(meta, overwriteText)
            else if (overwriteText) snackbarHostState.showSnackbar(msgFetchFailed, withDismissAction = true)
        }
    }

    fun save() {
        if (UrlUtils.normalize(url) == null) {
            urlError = msgInvalidUrl
            return
        }
        focusManager.clearFocus()
        saving = true
        scope.launch {
            val id = actions.onSave(draft, initialDraft)
            saving = false
            if (id == null) urlError = msgInvalidUrl else actions.onBack()
        }
    }

    // Obtiene los datos de la web automáticamente cuando se escribe o pega una dirección nueva.
    LaunchedEffect(url) {
        urlError = null
        duplicate = null
        val normalized = UrlUtils.normalize(url) ?: return@LaunchedEffect
        delay(if (url == defaults.url) 0 else 700)
        duplicate = actions.findDuplicate(normalized, existing?.id)
        if (autoFetch && isNew && fetchedFor != url) fetch(overwriteText = false)
    }

    BackHandler(enabled = hasChanges && !isTrashed) { showDiscard = true }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(if (isNew) R.string.new_bookmark else R.string.edit_bookmark),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { if (hasChanges && !isTrashed) showDiscard = true else actions.onBack() }) {
                        Icon(
                            if (isNew) Icons.Rounded.Close else Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                actions = {
                    if (existing != null && !isTrashed) {
                        IconButton(onClick = { actions.onOpenLink(existing) }) {
                            Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = stringResource(R.string.open_link), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Box {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.cd_options), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.copy_link)) },
                                    leadingIcon = { Icon(Icons.Rounded.ContentCopy, null) },
                                    onClick = { showMenu = false; LinkOpener.copy(context, existing.url) }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.share_link)) },
                                    leadingIcon = { Icon(Icons.Rounded.Share, null) },
                                    onClick = { showMenu = false; LinkOpener.share(context, existing.title, existing.url) }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.move_to_trash), color = MaterialTheme.colorScheme.error) },
                                    leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error) },
                                    onClick = {
                                        showMenu = false
                                        actions.onTrash(existing.id)
                                        actions.onBack()
                                    }
                                )
                            }
                        }
                    }
                    if (isTrashed) {
                        IconButton(onClick = { actions.onRestore(existing.id); actions.onBack() }) {
                            Icon(Icons.Rounded.RestoreFromTrash, contentDescription = stringResource(R.string.restore), tint = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Rounded.DeleteForever, contentDescription = stringResource(R.string.delete_forever), tint = MaterialTheme.colorScheme.error)
                        }
                    } else {
                        TextButton(onClick = ::save, enabled = !saving && url.isNotBlank()) {
                            if (saving) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            else Text(stringResource(R.string.save), fontWeight = FontWeight.Bold)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .wrapContentWidth(Alignment.CenterHorizontally)
                .widthIn(max = READABLE_MAX_WIDTH)
                .padding(horizontal = 16.dp)
        ) {
            if (isTrashed) {
                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.error.copy(alpha = 0.1f), modifier = Modifier.fillMaxWidth()) {
                    Text(
                        stringResource(R.string.in_trash_notice),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(12.dp)
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // --- Dirección ---
            OutlinedTextField(
                value = url,
                onValueChange = { url = it.replace("\n", "").trim() },
                placeholder = { Text(stringResource(R.string.url_hint)) },
                leadingIcon = { Icon(Icons.Rounded.Link, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                trailingIcon = {
                    when {
                        fetching -> CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        url.isNotBlank() -> IconButton(onClick = { fetch(overwriteText = true) }) {
                            Icon(Icons.Rounded.AutoAwesome, contentDescription = stringResource(R.string.fetch_metadata), tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                },
                isError = urlError != null,
                supportingText = urlError?.let { { Text(it) } },
                singleLine = true,
                readOnly = isTrashed,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next, autoCorrectEnabled = false),
                shape = RoundedCornerShape(12.dp),
                colors = filledFieldColors(),
                modifier = Modifier.fillMaxWidth()
            )

            if (clipboardUrl != null && url.isBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    onClick = { url = clipboardUrl },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.ContentPaste, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.paste_from_clipboard), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            Text(clipboardUrl, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            AnimatedVisibility(visible = duplicate != null) {
                duplicate?.let { dup ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = AccentOrange.copy(alpha = 0.12f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    ) {
                        Row(modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Warning, contentDescription = null, tint = AccentOrange, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                stringResource(R.string.duplicate_warning, dup.displayTitle),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(vertical = 8.dp)
                            )
                            TextButton(onClick = { actions.onOpenExisting(dup.id) }) { Text(stringResource(R.string.view)) }
                        }
                    }
                }
            }

            // --- Vista previa ---
            if (UrlUtils.normalize(url) != null) {
                Spacer(modifier = Modifier.height(12.dp))
                PreviewCard(
                    url = UrlUtils.normalize(url) ?: url,
                    title = title,
                    imageUrl = imageUrl,
                    faviconUrl = faviconUrl,
                    loadPreviews = loadPreviews,
                    onRemoveImage = if (imageUrl != null && !isTrashed) ({ imageUrl = null }) else null
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = title,
                onValueChange = { title = it.replace("\n", " ") },
                placeholder = { Text(stringResource(R.string.title_hint)) },
                leadingIcon = { Icon(Icons.Rounded.Title, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                singleLine = true,
                readOnly = isTrashed,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                shape = RoundedCornerShape(12.dp),
                colors = filledFieldColors(),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                placeholder = { Text(stringResource(R.string.description_hint)) },
                minLines = 2,
                maxLines = 6,
                readOnly = isTrashed,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                shape = RoundedCornerShape(12.dp),
                colors = filledFieldColors(),
                modifier = Modifier.fillMaxWidth()
            )

            // --- Organización ---
            Spacer(modifier = Modifier.height(18.dp))
            GroupLabel(stringResource(R.string.section_organize))
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                Column {
                    val folderName = folders.firstOrNull { it.id == folderId }?.name
                    val folderColor = LabelColor.fromKey(folders.firstOrNull { it.id == folderId }?.color)
                    OptionRow(
                        icon = Icons.Rounded.Folder,
                        iconTint = if (folderName != null) folderColor.iconTint() else MaterialTheme.colorScheme.primary,
                        title = stringResource(R.string.folder),
                        value = folderName ?: stringResource(R.string.no_folder),
                        onClick = { if (!isTrashed) showFolderPicker = true }
                    )
                    EditorDivider()
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OptionIcon(Icons.Rounded.Tag, MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(14.dp))
                            Text(stringResource(R.string.tags), style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        TagEditor(tags = tags, suggestions = allTags, onTagsChange = { if (!isTrashed) tags = it })
                    }
                    EditorDivider()
                    SwitchOption(Icons.Rounded.Star, AccentYellow, stringResource(R.string.favorite), isFavorite, enabled = !isTrashed) { isFavorite = it }
                    EditorDivider()
                    SwitchOption(Icons.Rounded.Schedule, MaterialTheme.colorScheme.primary, stringResource(R.string.read_later), isReadLater, enabled = !isTrashed) { isReadLater = it }
                    EditorDivider()
                    OptionRow(
                        icon = Icons.Rounded.Palette,
                        iconTint = color.iconTint(),
                        title = stringResource(R.string.color),
                        value = color.label(),
                        onClick = { if (!isTrashed) showColors = !showColors }
                    )
                    AnimatedVisibility(visible = showColors) {
                        ColorPalette(
                            selected = color,
                            onSelect = { color = it },
                            swatchSize = 30.dp,
                            modifier = Modifier.padding(start = 56.dp, end = 16.dp, bottom = 14.dp)
                        )
                    }
                }
            }

            // --- Notas ---
            Spacer(modifier = Modifier.height(18.dp))
            GroupLabel(stringResource(R.string.notes))
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                placeholder = { Text(stringResource(R.string.notes_hint)) },
                leadingIcon = { Icon(Icons.AutoMirrored.Rounded.Notes, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                minLines = 3,
                readOnly = isTrashed,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                shape = RoundedCornerShape(12.dp),
                colors = filledFieldColors(),
                modifier = Modifier.fillMaxWidth()
            )

            if (existing != null) {
                Spacer(modifier = Modifier.height(16.dp))
                val dateFormat = remember { DateFormat.getMediumDateFormat(context) }
                val timeFormat = remember { DateFormat.getTimeFormat(context) }
                fun format(t: Long) = dateFormat.format(Date(t)) + " " + timeFormat.format(Date(t))
                Text(
                    text = buildString {
                        append(stringResource(R.string.info_created, format(existing.createdAt)))
                        append('\n')
                        append(stringResource(R.string.info_modified, format(existing.updatedAt)))
                        if (existing.openCount > 0) {
                            append('\n')
                            append(pluralStringResource(R.plurals.info_opened, existing.openCount, existing.openCount, format(existing.lastOpenedAt)))
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }
            Spacer(modifier = Modifier.height(40.dp))
        }
    }

    if (showFolderPicker) {
        FolderPickerDialog(
            title = stringResource(R.string.folder),
            folders = folders,
            selectedId = folderId,
            onDismiss = { showFolderPicker = false },
            onSelect = {
                folderId = it
                showFolderPicker = false
            },
            onCreateNew = {
                showFolderPicker = false
                showNewFolder = true
            }
        )
    }

    if (showNewFolder) {
        FolderDialog(
            title = stringResource(R.string.new_folder),
            initialName = "",
            initialColor = LabelColor.DEFAULT,
            initialParentId = null,
            existingFolders = folders,
            onDismiss = { showNewFolder = false },
            onConfirm = { name, c, parentId ->
                actions.onCreateFolder(name, c, parentId) { id -> folderId = id }
                showNewFolder = false
            }
        )
    }

    if (showDiscard) {
        AlertDialog(
            onDismissRequest = { showDiscard = false },
            title = { Text(stringResource(R.string.unsaved_title), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.unsaved_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showDiscard = false
                    save()
                }) { Text(stringResource(R.string.save), fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDiscard = false
                    actions.onBack()
                }) { Text(stringResource(R.string.discard), color = MaterialTheme.colorScheme.error) }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }

    if (confirmDelete && existing != null) {
        ConfirmDestructiveDialog(
            title = stringResource(R.string.delete_forever_title),
            message = pluralStringResource(R.plurals.delete_forever_message, 1, 1),
            confirmLabel = stringResource(R.string.delete_forever),
            onDismiss = { confirmDelete = false },
            onConfirm = {
                confirmDelete = false
                actions.onDeleteForever(existing.id)
                actions.onBack()
            }
        )
    }
}

@Composable
private fun PreviewCard(
    url: String,
    title: String,
    imageUrl: String?,
    faviconUrl: String?,
    loadPreviews: Boolean,
    onRemoveImage: (() -> Unit)?
) {
    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Column {
            if (loadPreviews && imageUrl != null) {
                val context = LocalContext.current
                Box {
                    AsyncImage(
                        model = remember(imageUrl) { ImageRequest.Builder(context).data(imageUrl).crossfade(true).build() },
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1.91f)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    )
                    if (onRemoveImage != null) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                        ) {
                            IconButton(onClick = onRemoveImage, modifier = Modifier.size(34.dp)) {
                                Icon(Icons.Rounded.HideImage, contentDescription = stringResource(R.string.remove_image), modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                SiteIcon(url = url, faviconUrl = faviconUrl, loadImages = loadPreviews, size = 32.dp)
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = title.ifBlank { UrlUtils.displayHost(url) },
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = UrlUtils.displayUrl(url),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun EditorDivider() {
    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(start = 56.dp))
}

@Composable
private fun OptionIcon(icon: ImageVector, tint: androidx.compose.ui.graphics.Color) {
    Box(
        modifier = Modifier
            .size(26.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(tint.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun OptionRow(icon: ImageVector, iconTint: androidx.compose.ui.graphics.Color, title: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OptionIcon(icon, iconTint)
        Spacer(modifier = Modifier.width(14.dp))
        Text(title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium), modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 180.dp))
        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun SwitchOption(
    icon: ImageVector,
    tint: androidx.compose.ui.graphics.Color,
    title: String,
    checked: Boolean,
    enabled: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, role = Role.Switch) { onChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OptionIcon(icon, tint)
        Spacer(modifier = Modifier.width(14.dp))
        Text(title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium), modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

/**
 * Editor de etiquetas: chips con botón para quitar, un campo para escribir (Intro o coma
 * añaden la etiqueta) y sugerencias de las etiquetas que ya se usan.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagEditor(tags: List<String>, suggestions: List<String>, onTagsChange: (List<String>) -> Unit) {
    var input by remember { mutableStateOf("") }

    fun commit(raw: String) {
        val clean = Tags.clean(raw)
        if (clean.isNotEmpty()) onTagsChange(Tags.normalize(tags + clean))
        input = ""
    }

    Column {
        if (tags.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                tags.forEach { tag ->
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                    ) {
                        Row(modifier = Modifier.padding(start = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("#$tag", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            IconButton(onClick = { onTagsChange(tags - tag) }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.cd_remove_tag, tag), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
        OutlinedTextField(
            value = input,
            onValueChange = { value ->
                if (value.contains(',') || value.contains('\n')) {
                    value.split(',', '\n').dropLast(1).forEach(::commit)
                    input = value.split(',', '\n').last()
                    if (input.isNotBlank() && value.endsWith(",")) commit(input)
                } else {
                    input = value
                }
            },
            placeholder = { Text(stringResource(R.string.tag_hint)) },
            prefix = { Text("#") },
            singleLine = true,
            trailingIcon = if (input.isNotBlank()) {
                { IconButton(onClick = { commit(input) }) { Icon(Icons.Rounded.Check, contentDescription = stringResource(R.string.add), tint = MaterialTheme.colorScheme.primary) } }
            } else null,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, capitalization = KeyboardCapitalization.None),
            keyboardActions = KeyboardActions(onDone = { commit(input) }),
            shape = RoundedCornerShape(12.dp),
            colors = filledFieldColors(),
            modifier = Modifier.fillMaxWidth()
        )
        val existing = tags.mapTo(HashSet()) { it.lowercase() }
        val matching = suggestions
            .filter { it.lowercase() !in existing && (input.isBlank() || it.contains(input.trim().trimStart('#'), ignoreCase = true)) }
            .take(10)
        if (matching.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                matching.forEach { suggestion ->
                    TagChip(tag = suggestion, onClick = { commit(suggestion) })
                }
            }
        }
    }
}
