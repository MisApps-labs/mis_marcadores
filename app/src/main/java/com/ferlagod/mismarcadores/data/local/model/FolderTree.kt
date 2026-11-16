// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Marcadores — https://github.com/ferlagod/mis_marcadores

package com.ferlagod.mismarcadores.data.local.model

import com.ferlagod.mismarcadores.data.local.entity.BookmarkEntity
import com.ferlagod.mismarcadores.data.local.entity.FolderEntity

/**
 * Operaciones sobre la jerarquía de carpetas. Todas toleran datos inconsistentes
 * (padres inexistentes o ciclos llegados por sincronización) sin colgarse.
 */
object FolderTree {

    /** Elemento visible del árbol extensible de carpetas y marcadores. */
    sealed interface TreeItem {
        val key: String
        val level: Int

        data class FolderItem(
            val folder: FolderEntity,
            override val level: Int,
            val expanded: Boolean,
            val subfolderCount: Int,
            val bookmarkCount: Int
        ) : TreeItem {
            override val key: String = "folder_${folder.id}"
        }

        data class BookmarkItem(
            val bookmark: BookmarkEntity,
            override val level: Int
        ) : TreeItem {
            override val key: String = "bookmark_${bookmark.id}"
        }
    }

    /** Ids de todas las subcarpetas (a cualquier profundidad) de [folderId], sin incluirla. */
    fun descendantIds(folders: List<FolderEntity>, folderId: String): Set<String> {
        val children = folders.groupBy { it.parentId }
        val result = linkedSetOf<String>()
        val pending = ArrayDeque(listOf(folderId))
        while (pending.isNotEmpty()) {
            val current = pending.removeFirst()
            for (child in children[current].orEmpty()) {
                if (child.id != folderId && result.add(child.id)) pending.addLast(child.id)
            }
        }
        return result
    }

    /** True si asignar [newParentId] como padre de [folderId] crearía un ciclo. */
    fun wouldCreateCycle(folders: List<FolderEntity>, folderId: String, newParentId: String?): Boolean =
        newParentId != null && (newParentId == folderId || newParentId in descendantIds(folders, folderId))

    /**
     * Árbol aplanado en orden de visualización con su nivel de sangría. Las carpetas cuyo
     * padre no existe (o que forman un ciclo) se muestran en el nivel superior.
     */
    fun flatten(folders: List<FolderEntity>, comparator: Comparator<FolderEntity> = byName): List<Pair<FolderEntity, Int>> {
        val ids = folders.mapTo(HashSet()) { it.id }
        val children = folders.groupBy { it.parentId }.mapValues { (_, list) -> list.sortedWith(comparator) }
        val result = mutableListOf<Pair<FolderEntity, Int>>()
        val visited = HashSet<String>()

        fun add(folder: FolderEntity, level: Int) {
            if (!visited.add(folder.id)) return
            result += folder to level
            children[folder.id].orEmpty().forEach { add(it, level + 1) }
        }

        folders.filter { it.parentId == null || it.parentId !in ids }.sortedWith(comparator).forEach { add(it, 0) }
        // Restos de un ciclo: se cuelgan del nivel superior para que no desaparezcan.
        folders.sortedWith(comparator).forEach { add(it, 0) }
        return result
    }

    /**
     * Construye la lista visible del árbol extensible de carpetas y marcadores.
     * Solo incluye las subcarpetas y marcadores directos de las carpetas en [expandedIds].
     */
    fun buildExpandableTree(
        folders: List<FolderEntity>,
        bookmarks: List<BookmarkEntity>,
        expandedIds: Set<String>,
        folderComparator: Comparator<FolderEntity> = byName,
        bookmarkComparator: Comparator<BookmarkEntity> = byCreatedDesc
    ): List<TreeItem> {
        if (folders.isEmpty()) return emptyList()
        val ids = folders.mapTo(HashSet()) { it.id }
        val byId = folders.associateBy { it.id }
        val children = folders
            .filter { it.parentId != null && it.parentId in ids }
            .groupBy { it.parentId }
            .mapValues { (_, list) -> list.sortedWith(folderComparator) }
        val bookmarksByFolder = bookmarks
            .filter { it.folderId != null }
            .groupBy { it.folderId }
            .mapValues { (_, list) -> list.sortedWith(bookmarkComparator) }

        val rootFolders = folders
            .filter { it.parentId == null || it.parentId !in ids }
            .sortedWith(folderComparator)
            .toMutableList()
        val reachable = HashSet<String>()

        fun markReachable(startId: String) {
            val queue = ArrayDeque(listOf(startId))
            while (queue.isNotEmpty()) {
                val id = queue.removeFirst()
                if (!reachable.add(id)) continue
                children[id].orEmpty().forEach { queue.addLast(it.id) }
            }
        }

        rootFolders.forEach { markReachable(it.id) }
        for (folder in folders.sortedWith(folderComparator)) {
            if (folder.id in reachable) continue
            // Sube por los padres hasta encontrar una carpeta del ciclo para mostrarla en la raíz.
            var current: FolderEntity? = folder
            val seen = HashSet<String>()
            while (current != null && seen.add(current.id)) {
                current = current.parentId?.let(byId::get)
            }
            val cycleRoot = current ?: folder
            rootFolders += cycleRoot
            markReachable(cycleRoot.id)
        }
        rootFolders.sortWith(folderComparator)

        val result = mutableListOf<TreeItem>()
        val visited = HashSet<String>()

        fun add(folder: FolderEntity, level: Int) {
            if (!visited.add(folder.id)) return
            val directChildren = children[folder.id].orEmpty()
            val directBookmarks = bookmarksByFolder[folder.id].orEmpty()
            val totalBookmarks = directBookmarks.size +
                descendantIds(folders, folder.id).sumOf { bookmarksByFolder[it]?.size ?: 0 }
            val expanded = folder.id in expandedIds
            result += TreeItem.FolderItem(
                folder = folder,
                level = level,
                expanded = expanded,
                subfolderCount = directChildren.size,
                bookmarkCount = totalBookmarks
            )
            if (expanded) {
                directChildren.forEach { add(it, level + 1) }
                directBookmarks.forEach { result += TreeItem.BookmarkItem(it, level + 1) }
            }
        }

        rootFolders.forEach { add(it, 0) }
        return result
    }

    /** Ruta desde la raíz hasta [folderId] (incluida). Vacía si no existe. */
    fun path(foldersById: Map<String, FolderEntity>, folderId: String?): List<FolderEntity> {
        val result = mutableListOf<FolderEntity>()
        val seen = HashSet<String>()
        var current = folderId?.let { foldersById[it] }
        while (current != null && seen.add(current.id)) {
            result.add(0, current)
            current = current.parentId?.let { foldersById[it] }
        }
        return result
    }

    private val byName = compareBy<FolderEntity, String>(String.CASE_INSENSITIVE_ORDER) { it.name }
    private val byCreatedDesc = compareByDescending<BookmarkEntity> { it.createdAt }
}
