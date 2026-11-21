// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Marcadores — https://github.com/MisApps-labs/mis_marcadores

package com.ferlagod.mismarcadores.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Lápida remota ya procesada (fichero de un marcador o carpeta borrados definitivamente).
 * Sirve para no volver a descargarla en cada sincronización.
 */
@Entity(tableName = "remote_tombstones")
data class RemoteTombstoneEntity(
    @PrimaryKey
    @ColumnInfo(name = "fileName")
    val fileName: String,

    @ColumnInfo(name = "etag")
    val etag: String?
)
