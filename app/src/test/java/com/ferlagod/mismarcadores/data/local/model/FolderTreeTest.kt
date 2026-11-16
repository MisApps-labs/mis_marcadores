// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Marcadores — https://github.com/ferlagod/mis_marcadores

package com.ferlagod.mismarcadores.data.local.model

import com.ferlagod.mismarcadores.data.local.entity.BookmarkEntity
import com.ferlagod.mismarcadores.data.local.entity.FolderEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderTreeTest {

    private fun folder(id: String, parent: String? = null, name: String = id) = FolderEntity(id = id, parentId = parent, name = name)

    private fun bookmark(id: String, folderId: String?, createdAt: Long = 0L) = BookmarkEntity(
        id = id,
        folderId = folderId,
        url = "https://example.com/$id",
        title = id,
        createdAt = createdAt
    )

    private val folders = listOf(
        folder("a", name = "Trabajo"),
        folder("b", "a", "Proyectos"),
        folder("c", "b", "2026"),
        folder("d", name = "Casa")
    )

    @Test
    fun descendants() {
        assertEquals(setOf("b", "c"), FolderTree.descendantIds(folders, "a"))
        assertEquals(emptySet<String>(), FolderTree.descendantIds(folders, "d"))
    }

    @Test
    fun detectsCycles() {
        assertTrue(FolderTree.wouldCreateCycle(folders, "a", "c"))
        assertTrue(FolderTree.wouldCreateCycle(folders, "a", "a"))
        assertFalse(FolderTree.wouldCreateCycle(folders, "c", "d"))
        assertFalse(FolderTree.wouldCreateCycle(folders, "c", null))
    }

    @Test
    fun flattenSortsAndIndents() {
        val flat = FolderTree.flatten(folders).map { it.first.id to it.second }
        assertEquals(listOf("d" to 0, "a" to 0, "b" to 1, "c" to 2), flat)
    }

    @Test
    fun flattenSurvivesCyclesAndOrphans() {
        val broken = listOf(folder("x", "y"), folder("y", "x"), folder("z", "missing"))
        val ids = FolderTree.flatten(broken).map { it.first.id }
        assertEquals(setOf("x", "y", "z"), ids.toSet())
        assertEquals(3, ids.size)
    }

    @Test
    fun pathStopsOnCycles() {
        val map = folders.associateBy { it.id }
        assertEquals(listOf("a", "b", "c"), FolderTree.path(map, "c").map { it.id })
        val cyclic = listOf(folder("x", "y"), folder("y", "x")).associateBy { it.id }
        assertEquals(2, FolderTree.path(cyclic, "x").size)
    }

    @Test
    fun expandableTreeStartsCollapsedWithTotalCounts() {
        val bookmarks = listOf(
            bookmark("bm1", "a", 10L),
            bookmark("bm2", "b", 20L),
            bookmark("bm3", "c", 30L),
            bookmark("bm4", null, 40L)
        )
        val items = FolderTree.buildExpandableTree(folders, bookmarks, emptySet())
        assertEquals(2, items.size)
        val casa = items[0] as FolderTree.TreeItem.FolderItem
        val trabajo = items[1] as FolderTree.TreeItem.FolderItem
        assertEquals("d", casa.folder.id)
        assertEquals(0, casa.level)
        assertFalse(casa.expanded)
        assertEquals(0, casa.subfolderCount)
        assertEquals(0, casa.bookmarkCount)
        assertEquals("a", trabajo.folder.id)
        assertEquals(0, trabajo.level)
        assertFalse(trabajo.expanded)
        assertEquals(1, trabajo.subfolderCount)
        assertEquals(3, trabajo.bookmarkCount)
    }

    @Test
    fun expandableTreeRevealsDirectSubfoldersAndBookmarksWhenExpanded() {
        val bookmarks = listOf(
            bookmark("bm1", "a", 10L),
            bookmark("bm1_new", "a", 25L),
            bookmark("bm2", "b", 20L),
            bookmark("bm3", "c", 30L)
        )
        val items = FolderTree.buildExpandableTree(folders, bookmarks, setOf("a"))
        assertEquals(
            listOf(
                "folder_d" to 0,
                "folder_a" to 0,
                "folder_b" to 1,
                "bookmark_bm1_new" to 1,
                "bookmark_bm1" to 1
            ),
            items.map { it.key to it.level }
        )
        val trabajo = items[1] as FolderTree.TreeItem.FolderItem
        val proyectos = items[2] as FolderTree.TreeItem.FolderItem
        assertTrue(trabajo.expanded)
        assertFalse(proyectos.expanded)
        assertEquals(2, proyectos.bookmarkCount)
    }

    @Test
    fun expandableTreeRevealsNestedLevelsRecursively() {
        val bookmarks = listOf(
            bookmark("bm1", "a", 10L),
            bookmark("bm2", "b", 20L),
            bookmark("bm3", "c", 30L)
        )
        val items = FolderTree.buildExpandableTree(folders, bookmarks, setOf("a", "b", "c"))
        assertEquals(
            listOf(
                "folder_d" to 0,
                "folder_a" to 0,
                "folder_b" to 1,
                "folder_c" to 2,
                "bookmark_bm3" to 3,
                "bookmark_bm2" to 2,
                "bookmark_bm1" to 1
            ),
            items.map { it.key to it.level }
        )
    }

    @Test
    fun expandableTreeSurvivesCyclesAndOrphans() {
        val broken = listOf(folder("x", "y"), folder("y", "x"), folder("z", "missing"))
        val bookmarks = listOf(bookmark("bm_x", "x", 10L), bookmark("bm_y", "y", 20L))

        val collapsed = FolderTree.buildExpandableTree(broken, bookmarks, emptySet())
        assertEquals(listOf("folder_x" to 0, "folder_z" to 0), collapsed.map { it.key to it.level })

        val expanded = FolderTree.buildExpandableTree(broken, bookmarks, setOf("x", "y"))
        assertEquals(
            listOf("folder_x" to 0, "folder_y" to 1, "bookmark_bm_y" to 2, "bookmark_bm_x" to 1, "folder_z" to 0),
            expanded.map { it.key to it.level }
        )
    }
}
