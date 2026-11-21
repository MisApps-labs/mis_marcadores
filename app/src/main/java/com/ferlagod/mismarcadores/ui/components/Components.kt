// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Marcadores — https://github.com/MisApps-labs/mis_marcadores

package com.ferlagod.mismarcadores.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.ferlagod.mismarcadores.R
import com.ferlagod.mismarcadores.data.local.entity.FolderEntity
import com.ferlagod.mismarcadores.data.local.model.FolderTree
import com.ferlagod.mismarcadores.ui.theme.AccentRed
import com.ferlagod.mismarcadores.ui.theme.LabelColor
import com.ferlagod.mismarcadores.ui.theme.getAccentColor
import com.ferlagod.mismarcadores.ui.theme.iconTint
import com.ferlagod.mismarcadores.ui.theme.isAppInDarkTheme
import com.ferlagod.mismarcadores.util.UrlUtils
import kotlin.math.absoluteValue

/** Ancho máximo cómodo de lectura en tablets y móviles desplegados. */
val READABLE_MAX_WIDTH = 720.dp

/** Nombre legible de cada color (para accesibilidad). */
@Composable
fun LabelColor.label(): String = stringResource(
    when (this) {
        LabelColor.DEFAULT -> R.string.color_default
        LabelColor.YELLOW -> R.string.color_yellow
        LabelColor.BLUE -> R.string.color_blue
        LabelColor.GREEN -> R.string.color_green
        LabelColor.ORANGE -> R.string.color_orange
        LabelColor.RED -> R.string.color_red
        LabelColor.PINK -> R.string.color_pink
        LabelColor.PURPLE -> R.string.color_purple
        LabelColor.TEAL -> R.string.color_teal
        LabelColor.INDIGO -> R.string.color_indigo
    }
)

/** Paleta de colores que se reparte en varias filas si no cabe. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorPalette(
    selected: LabelColor?,
    onSelect: (LabelColor) -> Unit,
    modifier: Modifier = Modifier,
    swatchSize: Dp = 34.dp
) {
    val isDark = isAppInDarkTheme()
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        LabelColor.entries.forEach { labelColor ->
            val color = labelColor.getAccentColor(isDark)
            val isSelected = selected == labelColor
            val label = labelColor.label()
            Box(
                modifier = Modifier
                    .size(swatchSize)
                    .clip(CircleShape)
                    .background(color)
                    .border(
                        BorderStroke(
                            if (isSelected) 2.5.dp else 1.dp,
                            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                        ),
                        CircleShape
                    )
                    .clickable(role = Role.RadioButton) { onSelect(labelColor) }
                    .semantics {
                        contentDescription = label
                        this.selected = isSelected
                    },
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        tint = if (color.luminance() > 0.5f) Color.Black else Color.White,
                        modifier = Modifier.size(swatchSize / 2)
                    )
                }
            }
        }
    }
}

/**
 * Diálogo para elegir carpeta mostrando la jerarquía.
 *
 * @param excludedIds carpetas que no se pueden elegir (p. ej. la propia carpeta y sus subcarpetas).
 * @param onCreateNew si no es null, muestra "Nueva carpeta" al final.
 */
@Composable
fun FolderPickerDialog(
    title: String,
    folders: List<FolderEntity>,
    selectedId: String?,
    onDismiss: () -> Unit,
    onSelect: (String?) -> Unit,
    excludedIds: Set<String> = emptySet(),
    noneLabel: String = stringResource(R.string.no_folder),
    onCreateNew: (() -> Unit)? = null
) {
    val isDark = isAppInDarkTheme()
    val tree = remember(folders, excludedIds) { FolderTree.flatten(folders).filter { it.first.id !in excludedIds } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                PickerRow(
                    icon = Icons.Rounded.FolderOff,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    label = noneLabel,
                    level = 0,
                    selected = selectedId == null,
                    onClick = { onSelect(null) }
                )
                tree.forEach { (folder, level) ->
                    PickerRow(
                        icon = Icons.Rounded.Folder,
                        tint = LabelColor.fromKey(folder.color).iconTint(isDark),
                        label = folder.name,
                        level = level,
                        selected = selectedId == folder.id,
                        onClick = { onSelect(folder.id) }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
        dismissButton = onCreateNew?.let { create ->
            { TextButton(onClick = create) { Text(stringResource(R.string.new_folder)) } }
        }
    )
}

@Composable
private fun PickerRow(
    icon: ImageVector,
    tint: Color,
    label: String,
    level: Int,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(start = (8 + level * 18).dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (selected) {
            Icon(Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        }
    }
}

/** Diálogo para crear o editar una carpeta (nombre, color y carpeta padre). */
@Composable
fun FolderDialog(
    title: String,
    initialName: String,
    initialColor: LabelColor,
    initialParentId: String?,
    existingFolders: List<FolderEntity>,
    currentFolderId: String? = null,
    onDismiss: () -> Unit,
    onConfirm: (name: String, color: LabelColor, parentId: String?) -> Unit
) {
    var folderName by remember { mutableStateOf(initialName) }
    var selectedColor by remember { mutableStateOf(initialColor) }
    var selectedParentId by remember { mutableStateOf(initialParentId) }
    var showParentPicker by remember { mutableStateOf(false) }
    val nameFocus = remember { FocusRequester() }

    // Una carpeta no puede moverse dentro de sí misma ni de sus subcarpetas (crearía un ciclo).
    val excluded = remember(existingFolders, currentFolderId) {
        currentFolderId?.let { FolderTree.descendantIds(existingFolders, it) + it }.orEmpty()
    }
    val parentName = existingFolders.firstOrNull { it.id == selectedParentId }?.name
    val duplicateName = remember(folderName, selectedParentId, existingFolders, currentFolderId) {
        val trimmed = folderName.trim()
        trimmed.isNotEmpty() && existingFolders.any {
            it.id != currentFolderId && it.parentId == selectedParentId && it.name.equals(trimmed, ignoreCase = true)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                OutlinedTextField(
                    value = folderName,
                    onValueChange = { folderName = it.replace("\n", "") },
                    placeholder = { Text(stringResource(R.string.folder_name_hint)) },
                    singleLine = true,
                    isError = duplicateName,
                    supportingText = if (duplicateName) {
                        { Text(stringResource(R.string.folder_duplicate_error), color = AccentRed, fontSize = 12.sp) }
                    } else null,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        if (folderName.isNotBlank() && !duplicateName) onConfirm(folderName, selectedColor, selectedParentId)
                    }),
                    shape = RoundedCornerShape(12.dp),
                    colors = filledFieldColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(nameFocus)
                )
                LaunchedEffect(Unit) {
                    if (initialName.isEmpty()) {
                        kotlinx.coroutines.delay(120)
                        runCatching { nameFocus.requestFocus() }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.color),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                ColorPalette(selected = selectedColor, onSelect = { selectedColor = it }, swatchSize = 30.dp)

                if (existingFolders.any { it.id !in excluded }) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.parent_folder),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        onClick = { showParentPicker = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = parentName ?: stringResource(R.string.parent_none),
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(folderName, selectedColor, selectedParentId) },
                enabled = folderName.isNotBlank() && !duplicateName
            ) {
                Text(text = stringResource(R.string.save), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.cancel), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        shape = RoundedCornerShape(20.dp)
    )

    if (showParentPicker) {
        FolderPickerDialog(
            title = stringResource(R.string.parent_folder),
            folders = existingFolders,
            selectedId = selectedParentId,
            excludedIds = excluded,
            noneLabel = stringResource(R.string.parent_none),
            onDismiss = { showParentPicker = false },
            onSelect = {
                selectedParentId = it
                showParentPicker = false
            }
        )
    }
}

/** Colores de los campos de texto "rellenos" de la familia Mis Apps (sin borde). */
@Composable
fun filledFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
    focusedBorderColor = Color.Transparent,
    unfocusedBorderColor = Color.Transparent,
    focusedTextColor = MaterialTheme.colorScheme.onSurface,
    unfocusedTextColor = MaterialTheme.colorScheme.onSurface
)

/** Diálogo de confirmación para acciones que borran algo. */
@Composable
fun ConfirmDestructiveDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        shape = RoundedCornerShape(20.dp)
    )
}

/** Estado vacío con icono grande, título y explicación. */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null
) {
    Column(
        modifier = modifier.padding(horizontal = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
            modifier = Modifier.size(96.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(44.dp))
            }
        }
        Spacer(modifier = Modifier.height(18.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        if (message != null) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

/** Color estable para la inicial de una web (el mismo dominio, siempre el mismo color). */
@Composable
private fun hostColor(host: String): Color {
    val palette = LabelColor.entries.drop(1)
    val isDark = isAppInDarkTheme()
    return palette[host.hashCode().absoluteValue % palette.size].getAccentColor(isDark)
}

/**
 * Icono de una web: su favicon si se pueden cargar imágenes y existe; si no, la inicial del
 * dominio sobre un círculo de color.
 */
@Composable
fun SiteIcon(
    url: String,
    faviconUrl: String?,
    loadImages: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp
) {
    val host = remember(url) { UrlUtils.displayHost(url) }
    val letter = remember(host) { host.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "•" }
    val color = hostColor(host)
    val fallback = @Composable {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(color.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = letter,
                color = color,
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * 0.42f).sp
            )
        }
    }
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.28f))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        val icon = faviconUrl ?: UrlUtils.defaultFavicon(url)
        // La inicial se ve mientras carga el icono y se queda si la web no tiene ninguno.
        var loaded by remember(icon) { mutableStateOf(false) }
        if (!loaded) fallback()
        if (loadImages && icon != null) {
            val context = LocalContext.current
            AsyncImage(
                model = remember(icon) { ImageRequest.Builder(context).data(icon).crossfade(true).build() },
                contentDescription = null,
                contentScale = ContentScale.Fit,
                onSuccess = { loaded = true },
                onError = { loaded = false },
                modifier = Modifier
                    .fillMaxSize()
                    .background(if (loaded) Color.White else Color.Transparent)
                    .padding(size * 0.14f)
            )
        }
    }
}

/** Chip de etiqueta. */
@Composable
fun TagChip(
    tag: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    count: Int? = null,
    onClick: (() -> Unit)? = null
) {
    val primary = MaterialTheme.colorScheme.primary
    Surface(
        shape = RoundedCornerShape(50),
        color = if (selected) primary else primary.copy(alpha = 0.12f),
        modifier = modifier.then(if (onClick != null) Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onClick) else Modifier)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "#$tag",
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) MaterialTheme.colorScheme.onPrimary else primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (count != null) {
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = (if (selected) MaterialTheme.colorScheme.onPrimary else primary).copy(alpha = 0.7f)
                )
            }
        }
    }
}
