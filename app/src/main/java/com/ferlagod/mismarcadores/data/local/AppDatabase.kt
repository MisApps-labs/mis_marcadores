// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Marcadores — https://github.com/ferlagod/mis_marcadores

package com.ferlagod.mismarcadores.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.ferlagod.mismarcadores.data.local.converter.Converters
import com.ferlagod.mismarcadores.data.local.dao.BookmarkDao
import com.ferlagod.mismarcadores.data.local.dao.FolderDao
import com.ferlagod.mismarcadores.data.local.dao.RemoteTombstoneDao
import com.ferlagod.mismarcadores.data.local.entity.BookmarkEntity
import com.ferlagod.mismarcadores.data.local.entity.FolderEntity
import com.ferlagod.mismarcadores.data.local.entity.RemoteTombstoneEntity

/**
 * Base de datos local (Room). Es la fuente de verdad de la app; Nextcloud es una réplica.
 */
@Database(
    entities = [
        FolderEntity::class,
        BookmarkEntity::class,
        RemoteTombstoneEntity::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun folderDao(): FolderDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun remoteTombstoneDao(): RemoteTombstoneDao

    companion object {
        private const val DATABASE_NAME = "mismarcadores.db"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                ).build().also { INSTANCE = it }
            }
        }
    }
}
