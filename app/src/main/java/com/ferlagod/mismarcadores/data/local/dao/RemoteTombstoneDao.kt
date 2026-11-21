// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Marcadores — https://github.com/MisApps-labs/mis_marcadores

package com.ferlagod.mismarcadores.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.ferlagod.mismarcadores.data.local.entity.RemoteTombstoneEntity

@Dao
interface RemoteTombstoneDao {

    @Upsert
    suspend fun upsert(tombstone: RemoteTombstoneEntity)

    @Query("SELECT * FROM remote_tombstones")
    suspend fun getAll(): List<RemoteTombstoneEntity>

    @Query("DELETE FROM remote_tombstones WHERE fileName = :fileName")
    suspend fun delete(fileName: String)

    @Query("DELETE FROM remote_tombstones")
    suspend fun clear()
}
