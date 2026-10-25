package com.vaultflow.data.repository

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.vaultflow.core.security.EncryptedFileCipher
import com.vaultflow.core.security.KeyProvider
import com.vaultflow.data.local.VaultDatabase
import com.vaultflow.data.preferences.VaultPreferences
import com.vaultflow.worker.ExpirationReminderScheduler
import java.io.File
import java.util.UUID
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VaultRepositoryTest {
    @Test fun syntheticPdfImportsEncryptsOpensSearchesEditsAndDeletes() = runBlocking {
        val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.databaseBuilder(context, VaultDatabase::class.java, "vaultflow.db").build()
        val sourceBytes = "%PDF-1.4\nsynthetic test document\n%%EOF\n".toByteArray()
        val source = File(context.cacheDir, "test-${UUID.randomUUID()}.pdf").apply { writeBytes(sourceBytes) }
        val cipher = EncryptedFileCipher(KeyProvider { SecretKeySpec(ByteArray(32) { it.toByte() }, "AES") })
        val scheduler = ExpirationReminderScheduler(context, VaultPreferences(context))
        val repository = VaultRepository(context, database, database.documentDao(), cipher, scheduler)
        var cleanupId: String? = null
        try {
            val imported = repository.importDocument(
                Uri.fromFile(source),
                DocumentDraft("Travel proof", "personal", listOf("trip", "receipt")),
            ) { }
            cleanupId = imported.id

            val saved = repository.getDocument(imported.id)!!
            assertEquals("Travel proof", saved.document.title)
            assertEquals(setOf("trip", "receipt"), saved.tags.map { it.tag }.toSet())
            assertEquals(listOf(imported.id), repository.observeDocuments("trip", false, "personal", "title").first().map { it.document.id })

            val encrypted = File(File(context.noBackupFilesDir, "vault"), imported.encryptedFileName)
            assertTrue(encrypted.exists())
            assertFalse(encrypted.readBytes().contentEquals(sourceBytes))

            val opened = repository.openForViewing(imported.id)
            assertArrayEquals(sourceBytes, opened.plaintextFile.readBytes())
            repository.releasePlaintext(opened.plaintextFile)

            repository.updateDocument(saved.document.copy(title = "Updated proof"), listOf("archive"))
            repository.setFavorite(saved.document, true)
            val updated = repository.getDocument(imported.id)!!
            assertEquals("Updated proof", updated.document.title)
            assertTrue(updated.document.isFavorite)
            assertEquals(listOf("archive"), updated.tags.map { it.tag })

            repository.deleteDocument(imported.id)
            cleanupId = null
            assertNull(repository.getDocument(imported.id))
            assertFalse(encrypted.exists())
        } finally {
            cleanupId?.let { runCatching { repository.deleteDocument(it) } }
            source.delete()
            database.close()
        }
    }
}
