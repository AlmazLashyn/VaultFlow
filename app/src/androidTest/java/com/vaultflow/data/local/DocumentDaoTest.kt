package com.vaultflow.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class DocumentDaoTest {
    private lateinit var database: VaultDatabase
    private lateinit var dao: DocumentDao
    private lateinit var context: Context

    @Before fun setUp() = runBlocking {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, VaultDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.documentDao()
        dao.insertCategories(DefaultCategories.all)
    }

    @After fun tearDown() {
        database.close()
    }

    @Test fun metadataTagsSearchFavoritesAndDeletionWorkTogether() = runBlocking {
        val original = document("a", "Passport", "personal", createdAt = 100, expiresAt = 300)
        dao.saveDocument(original, listOf("travel", "identity"))

        assertEquals(listOf("a"), dao.observeDocuments("identity", false, null, "recent").first().map { it.document.id })
        assertEquals(listOf("a"), dao.observeDocuments("personal", false, "personal", "recent").first().map { it.document.id })

        dao.updateDocument(original.copy(title = "Travel passport", categoryId = "finance"))
        dao.deleteTags(original.id)
        dao.insertTags(listOf(DocumentTagEntity(original.id, "renewal")))
        dao.setFavorite(original.id, true)

        val updated = dao.getDocument(original.id)!!
        assertEquals("Travel passport", updated.document.title)
        assertEquals("finance", updated.document.categoryId)
        assertEquals(listOf("renewal"), updated.tags.map { it.tag })
        assertTrue(updated.document.isFavorite)
        assertEquals(listOf("a"), dao.observeDocuments("renewal", true, "finance", "title").first().map { it.document.id })
        assertTrue(dao.observeDocuments("identity", false, null, "recent").first().isEmpty())

        dao.deleteDocument(original.id)
        assertNull(dao.getDocument(original.id))
        assertTrue(dao.observeDocuments("renewal", false, null, "recent").first().isEmpty())
    }

    @Test fun sortingPlacesMissingExpirationsLastAndUsesTitleOrder() = runBlocking {
        dao.saveDocument(document("b", "Beta", "work", createdAt = 100, expiresAt = 300), emptyList())
        dao.saveDocument(document("a", "Alpha", "personal", createdAt = 200, expiresAt = 200), emptyList())
        dao.saveDocument(document("c", "Gamma", "contracts", createdAt = 300, expiresAt = null), emptyList())

        assertEquals(listOf("a", "b", "c"), dao.observeDocuments("", false, null, "title").first().map { it.document.id })
        assertEquals(listOf("a", "b", "c"), dao.observeDocuments("", false, null, "expiration").first().map { it.document.id })
        assertEquals(listOf("c", "a", "b"), dao.observeDocuments("", false, null, "recent").first().map { it.document.id })
    }

    @Test fun savedMetadataSurvivesDatabaseReopen() = runBlocking {
        val name = "vaultflow-test-${UUID.randomUUID()}.db"
        var persistent: VaultDatabase? = null
        try {
            persistent = Room.databaseBuilder(context, VaultDatabase::class.java, name).build()
            val persistentDao = persistent.documentDao()
            persistentDao.insertCategories(DefaultCategories.all)
            persistentDao.saveDocument(document("persisted", "Insurance card", "insurance", 500, 900), listOf("policy"))
            persistent.close()
            persistent = null

            persistent = Room.databaseBuilder(context, VaultDatabase::class.java, name).build()
            val restored = persistent.documentDao().getDocument("persisted")!!
            assertEquals("Insurance card", restored.document.title)
            assertEquals(listOf("policy"), restored.tags.map { it.tag })
        } finally {
            persistent?.close()
            context.deleteDatabase(name)
        }
    }

    private fun document(id: String, title: String, categoryId: String, createdAt: Long, expiresAt: Long?) = DocumentEntity(
        id = id,
        title = title,
        categoryId = categoryId,
        encryptedFileName = "${UUID.nameUUIDFromBytes(id.toByteArray())}.pdf",
        mimeType = "application/pdf",
        sizeBytes = 128,
        createdAt = createdAt,
        documentDate = null,
        expirationDate = expiresAt,
    )
}
