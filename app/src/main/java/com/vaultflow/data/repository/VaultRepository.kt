package com.vaultflow.data.repository

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.os.storage.StorageManager
import androidx.room.withTransaction
import com.vaultflow.core.security.EncryptedFileCipher
import com.vaultflow.core.security.FileTooLargeException
import com.vaultflow.core.security.InvalidEncryptedFileException
import com.vaultflow.data.local.CategoryEntity
import com.vaultflow.data.local.CategoryWithCount
import com.vaultflow.data.local.DefaultCategories
import com.vaultflow.data.local.DocumentDao
import com.vaultflow.data.local.DocumentEntity
import com.vaultflow.data.local.DocumentWithTags
import com.vaultflow.data.local.VaultDatabase
import com.vaultflow.domain.DocumentDraftValidator
import com.vaultflow.worker.ExpirationReminderScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.io.PushbackInputStream
import java.time.Instant
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

data class DocumentDraft(
    val title: String,
    val categoryId: String,
    val tags: List<String> = emptyList(),
    val documentDate: Long? = null,
    val expirationDate: Long? = null,
)

data class OpenedDocument(val document: DocumentWithTags, val plaintextFile: File)

@Singleton
class VaultRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: VaultDatabase,
    private val dao: DocumentDao,
    private val cipher: EncryptedFileCipher,
    private val reminderScheduler: ExpirationReminderScheduler,
) {
    private val vaultDir = File(context.noBackupFilesDir, "vault").apply { mkdirs() }
    private val exportDir = File(context.cacheDir, "share").apply { mkdirs() }

    fun observeDocuments(query: String, favorite: Boolean, categoryId: String?, sortOrder: String) =
        dao.observeDocuments(query, favorite, categoryId, sortOrder)

    fun observeCategories(): Flow<List<CategoryEntity>> = dao.observeCategories()
    fun observeCategoriesWithCount(): Flow<List<CategoryWithCount>> = dao.observeCategoriesWithCount()
    fun observeDocumentCount(): Flow<Int> = dao.observeDocumentCount()
    suspend fun getDocument(id: String): DocumentWithTags? = dao.getDocument(id)
    fun observeDocument(id: String): Flow<DocumentWithTags?> = dao.observeDocument(id)

    suspend fun refreshReminders() = withContext(Dispatchers.IO) {
        runCatching { reminderScheduler.refresh(dao.getAllDocuments()) }
    }

    suspend fun importDocument(uri: Uri, draft: DocumentDraft, onProgress: (Long) -> Unit): DocumentEntity = withContext(Dispatchers.IO) {
        val title = draft.title.trim()
        require(DocumentDraftValidator.isValid(draft)) { "Invalid document metadata" }
        val resolver = context.contentResolver
        dao.insertCategories(DefaultCategories.all)
        val sourceSize = querySize(resolver, uri)
        if (sourceSize != null && sourceSize > EncryptedFileCipher.MAX_IMPORT_BYTES) throw FileTooLargeException()
        if (sourceSize != null && sourceSize > 0) {
            val allocatable = runCatching {
                val storage = context.getSystemService(StorageManager::class.java)
                storage.getAllocatableBytes(storage.getUuidForPath(vaultDir))
            }.getOrDefault(vaultDir.usableSpace)
            if (allocatable < sourceSize + 64 * 1024) throw IOException("Not enough private storage")
        }

        val source = resolver.openInputStream(uri) ?: throw IOException("Source is unavailable")
        source.use { raw ->
            val input = PushbackInputStream(raw, SIGNATURE_BYTES)
            val prefix = ByteArray(SIGNATURE_BYTES)
            var prefixLength = 0
            while (prefixLength < prefix.size) {
                val read = input.read(prefix, prefixLength, prefix.size - prefixLength)
                if (read < 0) break
                if (read > 0) prefixLength += read
            }
            val type = detectFileType(prefix, prefixLength) ?: throw UnsupportedDocumentException()
            input.unread(prefix, 0, prefixLength)

            val id = UUID.randomUUID().toString()
            val encryptedName = "${UUID.randomUUID()}${type.extension}"
            val destination = File(vaultDir, encryptedName)
            var inserted = false
            try {
                val storedBytes = cipher.encrypt(input, destination, onProgress)
                val document = DocumentEntity(
                    id = id,
                    title = title,
                    categoryId = draft.categoryId.takeIf { category -> DefaultCategories.all.any { it.id == category } } ?: "other",
                    encryptedFileName = encryptedName,
                    mimeType = type.mimeType,
                    sizeBytes = storedBytes,
                    createdAt = System.currentTimeMillis(),
                    documentDate = draft.documentDate,
                    expirationDate = draft.expirationDate,
                )
                val tags = draft.tags.map { it.trim().take(40) }.filter { it.isNotBlank() }.distinct().take(MAX_TAGS)
                dao.saveDocument(document, tags)
                inserted = true
                runCatching { reminderScheduler.schedule(document) }
                document
            } catch (error: Throwable) {
                if (!inserted) destination.delete()
                throw error
            }
        }
    }

    suspend fun updateDocument(document: DocumentEntity, tags: List<String>) = withContext(Dispatchers.IO) {
        val title = document.title.trim()
        require(title.isNotEmpty() && title.length <= 120) { "Invalid document title" }
        database.withTransaction {
            dao.updateDocument(document.copy(title = title))
            dao.deleteTags(document.id)
            dao.insertTags(tags.map { it.trim().take(40) }.filter { it.isNotBlank() }.distinct().take(MAX_TAGS)
                .map { com.vaultflow.data.local.DocumentTagEntity(document.id, it) })
        }
        runCatching { reminderScheduler.schedule(document.copy(title = title)) }
    }

    suspend fun setFavorite(document: DocumentEntity, favorite: Boolean) = withContext(Dispatchers.IO) {
        dao.setFavorite(document.id, favorite)
    }

    suspend fun deleteDocument(id: String) = withContext(Dispatchers.IO) {
        val document = dao.getDocument(id)?.document ?: return@withContext
        val file = encryptedFile(document.encryptedFileName)
        if (file.exists() && !file.delete()) throw IOException("Could not remove encrypted document")
        dao.deleteDocument(id)
        reminderScheduler.cancelDocument(id)
    }

    suspend fun clearDocuments() = withContext(Dispatchers.IO) {
        vaultDir.listFiles().orEmpty().forEach { file ->
            if (file.exists() && !file.delete()) throw IOException("Could not clear all encrypted documents")
        }
        context.cacheDir.listFiles().orEmpty().filter { it.isFile && it.name.startsWith("cleartext-") }.forEach { file ->
            if (file.exists() && !file.delete()) throw IOException("Could not clear temporary document data")
        }
        exportDir.listFiles().orEmpty().forEach { file ->
            if (file.exists() && !file.delete()) throw IOException("Could not clear temporary export data")
        }
        database.withTransaction { dao.clearDocuments() }
        reminderScheduler.cancelAll()
    }

    suspend fun openForViewing(id: String): OpenedDocument = withContext(Dispatchers.IO) {
        val document = dao.getDocument(id) ?: throw DocumentUnavailableException()
        val encrypted = encryptedFile(document.document.encryptedFileName)
        if (!encrypted.exists()) {
            dao.setMissing(id, true)
            throw DocumentUnavailableException()
        }
        val extension = document.document.mimeType.substringAfter('/', "bin").let { if (it == "jpeg") "jpg" else it }
        val cleartext = File(context.cacheDir, "cleartext-${UUID.randomUUID()}.$extension")
        try {
            cipher.decrypt(encrypted, cleartext)
            dao.setMissing(id, false)
            OpenedDocument(document.copy(document = document.document.copy(isMissing = false)), cleartext)
        } catch (error: Throwable) {
            cleartext.delete()
            if (error is InvalidEncryptedFileException || error is FileTooLargeException) throw DocumentIntegrityException(error)
            throw error
        }
    }

    suspend fun releasePlaintext(file: File) = withContext(Dispatchers.IO) {
        if (file.name.startsWith("cleartext-")) file.delete()
    }

    suspend fun prepareExport(id: String): OpenedDocument = withContext(Dispatchers.IO) {
        val document = dao.getDocument(id) ?: throw DocumentUnavailableException()
        val encrypted = encryptedFile(document.document.encryptedFileName)
        if (!encrypted.exists()) {
            dao.setMissing(id, true)
            throw DocumentUnavailableException()
        }
        val extension = document.document.mimeType.substringAfter('/', "bin").let { if (it == "jpeg") "jpg" else it }
        val cleartext = File(exportDir, "${UUID.randomUUID()}.$extension")
        try {
            cipher.decrypt(encrypted, cleartext)
            dao.setMissing(id, false)
            OpenedDocument(document.copy(document = document.document.copy(isMissing = false)), cleartext)
        } catch (error: Throwable) {
            cleartext.delete()
            if (error is InvalidEncryptedFileException || error is FileTooLargeException) throw DocumentIntegrityException(error)
            throw error
        }
    }

    suspend fun releaseExport(file: File) = withContext(Dispatchers.IO) {
        if (file.parentFile?.canonicalFile == exportDir.canonicalFile) file.delete()
    }

    suspend fun reconcileStorage() = withContext(Dispatchers.IO) {
        context.cacheDir.listFiles()?.filter { it.isFile && it.name.startsWith("cleartext-") }?.forEach(File::delete)
        exportDir.listFiles()?.forEach(File::delete)
        dao.getAllDocuments().forEach { document ->
            val isMissing = runCatching { !encryptedFile(document.encryptedFileName).exists() }.getOrDefault(true)
            if (document.isMissing != isMissing) dao.setMissing(document.id, isMissing)
        }
        dao.insertCategories(DefaultCategories.all)
        refreshReminders()
        // Unreferenced encrypted files are intentionally retained for manual recovery.
    }

    private fun querySize(resolver: ContentResolver, uri: Uri): Long? = runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getLong(0).takeIf { it >= 0 } else null
        }
    }.getOrNull()

    private fun encryptedFile(name: String): File {
        val parts = name.split('.')
        val validId = parts.size == 2 && runCatching { UUID.fromString(parts[0]).toString() == parts[0] }.getOrDefault(false)
        if (!validId || parts[1] !in setOf("pdf", "jpg", "png")) throw DocumentUnavailableException()
        val file = File(vaultDir, name).canonicalFile
        if (file.parentFile != vaultDir.canonicalFile) throw DocumentUnavailableException()
        return file
    }

    private data class FileType(val mimeType: String, val extension: String)

    private fun detectFileType(prefix: ByteArray, length: Int): FileType? {
        if (length >= 5 && prefix.copyOfRange(0, 5).contentEquals("%PDF-".toByteArray(Charsets.US_ASCII))) return FileType("application/pdf", ".pdf")
        if (length >= 8 && prefix.copyOfRange(0, 8).contentEquals(PNG_SIGNATURE)) return FileType("image/png", ".png")
        if (length >= 3 && prefix[0] == 0xff.toByte() && prefix[1] == 0xd8.toByte() && prefix[2] == 0xff.toByte()) return FileType("image/jpeg", ".jpg")
        return null
    }

    companion object {
        private const val SIGNATURE_BYTES = 8
        private const val MAX_TAGS = 20
        private val PNG_SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)
    }
}

class UnsupportedDocumentException : Exception()
class DocumentUnavailableException : Exception()
class DocumentIntegrityException(cause: Throwable? = null) : Exception(cause)
