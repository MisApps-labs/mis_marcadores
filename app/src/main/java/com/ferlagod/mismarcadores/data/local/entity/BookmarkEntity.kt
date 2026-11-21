// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Marcadores — https://github.com/MisApps-labs/mis_marcadores

package com.ferlagod.mismarcadores.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.ferlagod.mismarcadores.data.local.model.LinkStatus
import com.ferlagod.mismarcadores.data.local.model.SyncStatus
import com.ferlagod.mismarcadores.data.local.model.Tags
import com.ferlagod.mismarcadores.ui.theme.LabelColor
import java.util.UUID

/**
 * Marcador guardado localmente (arquitectura local-first).
 *
 * Campos sincronizados: carpeta, enlace, textos, etiquetas, imágenes, color, favorito,
 * "leer más tarde", fechas y papelera.
 *
 * Campos solo locales (no cambian [updatedAt] ni se suben): [openCount], [lastOpenedAt],
 * [linkStatus] y [linkCheckedAt]. Son estadísticas de este dispositivo.
 *
 * - [isDeleted]: el marcador está en la papelera (se sincroniza y se puede restaurar).
 * - [syncStatus] = PENDING_DELETE: borrado definitivo pendiente de propagar al servidor.
 * - [remoteEtag]: ETag del fichero remoto la última vez que local y servidor coincidían.
 */
@Entity(
    tableName = "bookmarks",
    indices = [
        Index(value = ["folderId"]),
        Index(value = ["url"]),
        Index(value = ["isFavorite"]),
        Index(value = ["syncStatus"]),
        Index(value = ["isDeleted"]),
        Index(value = ["createdAt"])
    ]
)
data class BookmarkEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String = UUID.randomUUID().toString(),

    @ColumnInfo(name = "folderId")
    val folderId: String? = null,

    @ColumnInfo(name = "url")
    val url: String,

    @ColumnInfo(name = "title")
    val title: String = "",

    @ColumnInfo(name = "description")
    val description: String = "",

    /** Notas personales sobre el enlace. */
    @ColumnInfo(name = "notes")
    val notes: String = "",

    /** Etiquetas separadas por comas (ver [Tags]). */
    @ColumnInfo(name = "tags")
    val tags: String = "",

    /** Imagen de vista previa de la web (og:image). */
    @ColumnInfo(name = "imageUrl")
    val imageUrl: String? = null,

    @ColumnInfo(name = "faviconUrl")
    val faviconUrl: String? = null,

    @ColumnInfo(name = "color")
    val color: String = LabelColor.DEFAULT.key,

    @ColumnInfo(name = "isFavorite")
    val isFavorite: Boolean = false,

    @ColumnInfo(name = "isReadLater")
    val isReadLater: Boolean = false,

    @ColumnInfo(name = "createdAt")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "updatedAt")
    val updatedAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "isDeleted")
    val isDeleted: Boolean = false,

    @ColumnInfo(name = "syncStatus")
    val syncStatus: SyncStatus = SyncStatus.PENDING_UPLOAD,

    @ColumnInfo(name = "remoteEtag")
    val remoteEtag: String? = null,

    // --- Solo locales ---

    @ColumnInfo(name = "openCount")
    val openCount: Int = 0,

    @ColumnInfo(name = "lastOpenedAt")
    val lastOpenedAt: Long = 0L,

    /** Resultado de la última comprobación del enlace ([LinkStatus]). */
    @ColumnInfo(name = "linkStatus")
    val linkStatus: Int = LinkStatus.UNKNOWN,

    @ColumnInfo(name = "linkCheckedAt")
    val linkCheckedAt: Long = 0L
) {
    val tagList: List<String> get() = Tags.parse(tags)

    /** Título que se muestra: el guardado o, si no hay, el dominio. */
    val displayTitle: String get() = title.ifBlank { com.ferlagod.mismarcadores.util.UrlUtils.displayHost(url) }

    /** Copia con los campos solo locales de [local] (al aceptar una versión remota). */
    fun withLocalStatsFrom(local: BookmarkEntity?): BookmarkEntity =
        if (local == null) this
        else copy(
            openCount = local.openCount,
            lastOpenedAt = local.lastOpenedAt,
            linkStatus = if (local.url == url) local.linkStatus else LinkStatus.UNKNOWN,
            linkCheckedAt = if (local.url == url) local.linkCheckedAt else 0L
        )
}
