// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Marcadores — https://github.com/ferlagod/mis_marcadores

package com.ferlagod.mismarcadores.data.sync

import com.ferlagod.mismarcadores.data.local.entity.BookmarkEntity
import com.ferlagod.mismarcadores.data.local.model.LinkStatus
import com.ferlagod.mismarcadores.data.local.model.SyncStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncMergerTest {

    private fun bookmark(
        status: SyncStatus,
        updatedAt: Long = 1000L,
        title: String = "Título",
        notes: String = "",
        tags: String = "",
        etag: String? = "e1"
    ) = BookmarkEntity(
        id = "b1", url = "https://example.com", title = title, notes = notes, tags = tags,
        updatedAt = updatedAt, createdAt = 500L, syncStatus = status, remoteEtag = etag
    )

    @Test
    fun syncedLocalAcceptsRemoteAndKeepsLocalStats() {
        val local = bookmark(SyncStatus.SYNCED).copy(openCount = 7, lastOpenedAt = 99L, linkStatus = LinkStatus.BROKEN)
        val remote = bookmark(SyncStatus.SYNCED, updatedAt = 2000L, title = "Nuevo")
        val action = SyncMerger.resolveBookmark(local, RemoteRecord.Live(remote), "e2") as MergeAction.SaveRemote
        assertEquals("Nuevo", action.entity.title)
        assertEquals("e2", action.entity.remoteEtag)
        assertEquals(7, action.entity.openCount)
        assertEquals(LinkStatus.BROKEN, action.entity.linkStatus)
    }

    @Test
    fun concurrentEditsAreMergedWithoutLosingTagsOrNotes() {
        val local = bookmark(SyncStatus.PENDING_UPLOAD, updatedAt = 3000L, title = "Local", notes = "nota local", tags = "a,b")
        val remote = bookmark(SyncStatus.SYNCED, updatedAt = 2000L, title = "Remoto", notes = "nota remota", tags = "b,c")
        val merged = (SyncMerger.resolveBookmark(local, RemoteRecord.Live(remote), "e9") as MergeAction.SaveMerged).entity
        assertEquals("Local", merged.title) // gana la versión más reciente
        assertEquals("a,b,c", merged.tags)
        assertTrue(merged.notes.startsWith("nota local"))
        assertTrue(merged.notes.endsWith("nota remota"))
        assertEquals(SyncStatus.PENDING_UPLOAD, merged.syncStatus)
        assertEquals("e9", merged.remoteEtag)
        assertEquals(3000L, merged.updatedAt)
    }

    @Test
    fun identicalPendingContentJustUpdatesEtag() {
        val local = bookmark(SyncStatus.PENDING_UPLOAD, updatedAt = 3000L)
        val remote = bookmark(SyncStatus.SYNCED, updatedAt = 2000L)
        assertEquals(MergeAction.KeepLocal("e5"), SyncMerger.resolveBookmark(local, RemoteRecord.Live(remote), "e5"))
    }

    @Test
    fun tombstones() {
        val tombstone = RemoteRecord.Tombstone("b1", 5000L)
        assertEquals(MergeAction.DeleteLocal, SyncMerger.resolveBookmark(bookmark(SyncStatus.SYNCED), tombstone, "e2"))
        assertEquals(MergeAction.KeepLocal("e2"), SyncMerger.resolveBookmark(bookmark(SyncStatus.PENDING_UPLOAD), tombstone, "e2"))
        assertEquals(MergeAction.Skip, SyncMerger.resolveBookmark(null, tombstone, "e2"))
    }

    @Test
    fun missingRemoteGoesToTrashInsteadOfDeleting() {
        assertEquals(MissingRemoteAction.TRASH_AND_REUPLOAD, SyncMerger.resolveMissingRemoteBookmark(SyncStatus.SYNCED, "e1"))
        assertEquals(MissingRemoteAction.MARK_FOR_UPLOAD, SyncMerger.resolveMissingRemoteBookmark(SyncStatus.SYNCED, null))
        assertEquals(MissingRemoteAction.DELETE_LOCAL, SyncMerger.resolveMissingRemoteBookmark(SyncStatus.PENDING_DELETE, "e1"))
    }

    @Test
    fun massRemovalBrake() {
        assertFalse(SyncMerger.isMassRemoval(4, 4))
        assertTrue(SyncMerger.isMassRemoval(5, 20))
        assertFalse(SyncMerger.isMassRemoval(5, 100))
        assertTrue(SyncMerger.isMassRemoval(50, 10_000))
    }
}
