// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Marcadores — https://github.com/ferlagod/mis_marcadores

package com.ferlagod.mismarcadores.data.sync

import com.ferlagod.mismarcadores.data.local.entity.BookmarkEntity
import com.ferlagod.mismarcadores.data.local.entity.FolderEntity
import com.ferlagod.mismarcadores.data.local.model.SyncStatus
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class RemoteCodecTest {

    private val bookmark = BookmarkEntity(
        id = "11111111-2222-3333-4444-555555555555",
        folderId = "folder-1",
        url = "https://example.com/página",
        title = "Mi enlace",
        description = "Descripción",
        notes = "Notas",
        tags = "uno,dos",
        imageUrl = "https://example.com/og.png",
        faviconUrl = null,
        color = "BLUE",
        isFavorite = true,
        isReadLater = true,
        createdAt = 1729990000000L,
        updatedAt = 1729999999000L,
        isDeleted = true,
        syncStatus = SyncStatus.PENDING_UPLOAD
    )

    @Test
    fun bookmarkRoundTrip() {
        val raw = RemoteCodec.encodeBookmark(bookmark)
        assertEquals(bookmark.url, JSONObject(raw).getJSONObject("data").getString("url"))
        val record = RemoteCodec.decodeBookmark(raw, bookmark.id) as RemoteRecord.Live
        assertEquals(bookmark.copy(syncStatus = SyncStatus.SYNCED), record.entity)
    }

    @Test
    fun folderRoundTrip() {
        val folder = FolderEntity(id = "f1", parentId = "f0", name = "Trabajo", color = "RED", updatedAt = 42L)
        val record = RemoteCodec.decodeFolder(RemoteCodec.encodeFolder(folder), "f1") as RemoteRecord.Live
        assertEquals(folder.copy(syncStatus = SyncStatus.SYNCED), record.entity)
    }

    @Test
    fun idMustMatchFileName() {
        val raw = RemoteCodec.encodeBookmark(bookmark)
        try {
            RemoteCodec.decodeBookmark(raw, "otro")
            fail("Debe rechazar un fichero cuyo id no coincide con su nombre")
        } catch (_: IllegalArgumentException) {
        }
    }

    @Test
    fun tombstoneRoundTrip() {
        val raw = RemoteCodec.encodeTombstone(RemoteCodec.TYPE_BOOKMARK, bookmark.id, 123L)
        assertEquals(RemoteRecord.Tombstone(bookmark.id, 123L), RemoteCodec.decodeBookmark(raw, bookmark.id))
    }

    @Test
    fun legacyEncryptedFilesAreDetected() {
        val legacy = JSONObject()
            .put("format", "mismarcadores").put("version", 1).put("type", "bookmark")
            .put("id", bookmark.id).put("payload", "AAAA")
            .toString()
        try {
            RemoteCodec.decodeBookmark(legacy, bookmark.id)
            fail("Un fichero cifrado de la versión 1 debe detectarse")
        } catch (_: LegacyEncryptedFileException) {
        }
    }

    @Test
    fun legacyTombstonesAreStillRead() {
        val legacy = JSONObject()
            .put("format", "mismarcadores").put("version", 1).put("type", "folder")
            .put("id", "f1").put("deleted", true).put("deletedAt", 7L).put("auth", "AAAA")
            .toString()
        assertEquals(RemoteRecord.Tombstone("f1", 7L), RemoteCodec.decodeFolder(legacy, "f1"))
    }
}
