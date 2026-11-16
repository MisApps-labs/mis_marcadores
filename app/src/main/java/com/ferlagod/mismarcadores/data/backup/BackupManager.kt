// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 ferlagod
// Mis Marcadores — https://github.com/ferlagod/mis_marcadores

package com.ferlagod.mismarcadores.data.backup

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.room.withTransaction
import com.ferlagod.mismarcadores.data.local.AppDatabase
import com.ferlagod.mismarcadores.data.local.entity.BookmarkEntity
import com.ferlagod.mismarcadores.data.local.entity.FolderEntity
import com.ferlagod.mismarcadores.data.local.model.SyncStatus
import com.ferlagod.mismarcadores.data.local.model.Tags
import com.ferlagod.mismarcadores.ui.theme.LabelColor
import com.ferlagod.mismarcadores.util.UrlUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/** Resultado de una importación. */
data class ImportResult(val added: Int, val duplicates: Int, val folders: Int)

/**
 * Copias de seguridad en el formato HTML de marcadores de los navegadores: se pueden abrir con
 * cualquier navegador y llevar a Chrome, Firefox, Safari… o traer de ellos.
 */
class BackupManager(context: Context) {

    private val appContext = context.applicationContext
    private val database = AppDatabase.getInstance(appContext)
    private val bookmarkDao = database.bookmarkDao()
    private val folderDao = database.folderDao()

    companion object {
        const val MIME_HTML = "text/html"

        /**
         * Reconoce el formato por el contenido (no por la extensión, que a menudo se pierde al
         * pasar el fichero al móvil): mozlz4, JSON de Chrome/Firefox, HTML de navegador o texto.
         */
        fun parseFile(bytes: ByteArray, names: RootFolderNames): ImportedFolder {
            val raw = if (MozLz4.isMozLz4(bytes)) MozLz4.decompress(bytes) else bytes
            val text = String(raw, Charsets.UTF_8)
            if (text.trimStart('\uFEFF', ' ', '\n', '\r', '\t').startsWith("{")) {
                BrowserJsonBookmarks.parse(text, names)?.let { return it }
            }
            return if (NetscapeBookmarks.isBookmarkFile(text)) NetscapeBookmarks.parse(text)
            else NetscapeBookmarks.parsePlainText(text)
        }
        const val AUTO_BACKUP_KEEP = 3
        private const val AUTO_BACKUP_PREFIX = "MisMarcadores-auto-"
        private const val SAFETY_DIR = "MisMarcadores"
        private const val MAX_IMPORT_BYTES = 30L * 1024 * 1024

        fun exportFileName(): String =
            "MisMarcadores-" + SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date()) + ".html"
    }

    // ------------------------------------------------------------------
    // Exportación
    // ------------------------------------------------------------------

    private suspend fun exportHtml(): Pair<String, Int> {
        val folders = folderDao.getAll().filter { !it.isDeleted && it.syncStatus != SyncStatus.PENDING_DELETE }
        val bookmarks = bookmarkDao.getLive()
        return NetscapeBookmarks.export(folders, bookmarks, "Mis Marcadores") to bookmarks.size
    }

    /** Exporta todos los marcadores a [uri] (elegido con ACTION_CREATE_DOCUMENT). Devuelve cuántos. */
    suspend fun exportTo(uri: Uri): Int = withContext(Dispatchers.IO) {
        val (html, count) = exportHtml()
        appContext.contentResolver.openOutputStream(uri, "wt")?.use { it.write(html.toByteArray(Charsets.UTF_8)) }
            ?: throw IOException("Cannot open $uri")
        count
    }

    /**
     * Copia de seguridad antes de conectar una cuenta de Nextcloud, en Descargas/MisMarcadores
     * (Android 10+) o en la carpeta de documentos de la app. Sin marcadores no se crea nada.
     */
    suspend fun exportSafetyCopy(): String? = withContext(Dispatchers.IO) {
        if (bookmarkDao.countLive() == 0) return@withContext null
        val (html, _) = exportHtml()
        val bytes = html.toByteArray(Charsets.UTF_8)
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(Date())
        val fileName = "MisMarcadores-backup-$stamp.html"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = appContext.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, MIME_HTML)
                put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$SAFETY_DIR")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IOException("Cannot create $fileName")
            try {
                resolver.openOutputStream(uri)?.use { it.write(bytes) } ?: throw IOException("Cannot open $uri")
                resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                throw e
            }
            "${Environment.DIRECTORY_DOWNLOADS}/$SAFETY_DIR/$fileName"
        } else {
            val dir = appContext.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
                ?: throw IOException("External storage not available")
            File(dir, fileName).apply { writeBytes(bytes) }.absolutePath
        }
    }

    /**
     * Copia automática en la carpeta [treeUri] elegida por el usuario. Se conservan las
     * [AUTO_BACKUP_KEEP] más recientes. Sin marcadores no se crea nada.
     */
    suspend fun exportAutoBackup(treeUri: Uri): Int? = withContext(Dispatchers.IO) {
        if (bookmarkDao.countLive() == 0) return@withContext null
        val resolver = appContext.contentResolver
        val treeDocId = DocumentsContract.getTreeDocumentId(treeUri)
        val parent = DocumentsContract.buildDocumentUriUsingTree(treeUri, treeDocId)
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(Date())
        val doc = DocumentsContract.createDocument(resolver, parent, MIME_HTML, "$AUTO_BACKUP_PREFIX$stamp.html")
            ?: throw IOException("Cannot create backup in $treeUri")
        val (html, count) = exportHtml()
        try {
            resolver.openOutputStream(doc)?.use { it.write(html.toByteArray(Charsets.UTF_8)) }
                ?: throw IOException("Cannot open $doc")
        } catch (e: Exception) {
            runCatching { DocumentsContract.deleteDocument(resolver, doc) }
            throw e
        }
        pruneAutoBackups(treeUri, treeDocId)
        count
    }

    private fun pruneAutoBackups(treeUri: Uri, treeDocId: String) {
        val resolver = appContext.contentResolver
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, treeDocId)
        val backups = ArrayList<Pair<String, String>>()
        resolver.query(
            children,
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null, null, null
        )?.use { c ->
            while (c.moveToNext()) {
                val id = c.getString(0) ?: continue
                val name = c.getString(1) ?: continue
                if (name.startsWith(AUTO_BACKUP_PREFIX)) backups += name to id
            }
        }
        // La marca de tiempo del nombre (yyyyMMdd-HHmmss) ordena cronológicamente.
        backups.sortedByDescending { it.first }.drop(AUTO_BACKUP_KEEP).forEach { (_, id) ->
            runCatching { DocumentsContract.deleteDocument(resolver, DocumentsContract.buildDocumentUriUsingTree(treeUri, id)) }
        }
    }

    // ------------------------------------------------------------------
    // Importación
    // ------------------------------------------------------------------

    /**
     * Importa marcadores conservando sus carpetas:
     * - HTML exportado por cualquier navegador (Chrome, Firefox, Safari, Edge…) o por Mis Marcadores.
     * - Copias JSON de Firefox (`.json` y `.jsonlz4`) y el fichero `Bookmarks` de Chrome.
     * - Listas de direcciones en texto.
     * Las carpetas con el mismo nombre se reutilizan y los enlaces que ya existen no se duplican.
     */
    suspend fun import(uris: List<Uri>, names: RootFolderNames): ImportResult = withContext(Dispatchers.IO) {
        var added = 0
        var duplicates = 0
        var newFolders = 0
        for (uri in uris) {
            val root = parseFile(readLimited(openStream(uri)), names)
            val result = store(root)
            added += result.added
            duplicates += result.duplicates
            newFolders += result.folders
        }
        ImportResult(added, duplicates, newFolders)
    }

    private suspend fun store(root: ImportedFolder): ImportResult = database.withTransaction {
        storeInTransaction(root)
    }

    private suspend fun storeInTransaction(root: ImportedFolder): ImportResult {
        val existingFolders = folderDao.getAll().filter { !it.isDeleted }.toMutableList()
        val known = bookmarkDao.getAll().filter { it.syncStatus != SyncStatus.PENDING_DELETE }
            .mapTo(HashSet()) { UrlUtils.canonical(it.url) }
        var added = 0
        var duplicates = 0
        var folders = 0
        val now = System.currentTimeMillis()

        suspend fun folderFor(name: String, parentId: String?): String {
            existingFolders.firstOrNull { it.parentId == parentId && it.name.equals(name, ignoreCase = true) }
                ?.let { return it.id }
            val folder = FolderEntity(
                id = UUID.randomUUID().toString(),
                parentId = parentId,
                name = name.take(120),
                syncStatus = SyncStatus.PENDING_UPLOAD
            )
            folderDao.upsertFolder(folder)
            existingFolders += folder
            folders++
            return folder.id
        }

        suspend fun walk(folder: ImportedFolder, folderId: String?) {
            for (b in folder.bookmarks) {
                if (!known.add(UrlUtils.canonical(b.url))) {
                    duplicates++
                    continue
                }
                val created = b.addedAt ?: now
                bookmarkDao.upsert(
                    BookmarkEntity(
                        folderId = folderId,
                        url = b.url,
                        title = b.title.take(500),
                        description = b.description,
                        notes = b.notes,
                        tags = Tags.join(b.tags),
                        color = LabelColor.fromKey(b.color).key,
                        isFavorite = b.isFavorite,
                        isReadLater = b.isReadLater,
                        createdAt = created,
                        updatedAt = maxOf(b.modifiedAt ?: created, created),
                        syncStatus = SyncStatus.PENDING_UPLOAD
                    )
                )
                added++
            }
            for (child in folder.folders) {
                // Las carpetas vacías (sin marcadores dentro, a ninguna profundidad) no se crean.
                if (child.countBookmarks() == 0) continue
                walk(child, folderFor(child.name, folderId))
            }
        }

        walk(root, null)
        return ImportResult(added, duplicates, folders)
    }

    private fun openStream(uri: Uri): InputStream =
        appContext.contentResolver.openInputStream(uri) ?: throw IOException("Cannot open $uri")

    private fun readLimited(input: InputStream): ByteArray = input.use { stream ->
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val read = stream.read(buffer)
            if (read < 0) break
            total += read
            if (total > MAX_IMPORT_BYTES) throw IOException("File too large")
            out.write(buffer, 0, read)
        }
        out.toByteArray()
    }
}
