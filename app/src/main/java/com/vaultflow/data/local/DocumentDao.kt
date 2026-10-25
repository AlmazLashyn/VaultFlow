package com.vaultflow.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentDao {
    @Transaction
    @Query(
        """SELECT d.* FROM documents d
            LEFT JOIN categories c ON c.id = d.categoryId
            WHERE (:query = '' OR lower(d.title) LIKE '%' || lower(:query) || '%'
                OR lower(c.name) LIKE '%' || lower(:query) || '%'
                OR EXISTS (SELECT 1 FROM document_tags t WHERE t.documentId = d.id AND lower(t.tag) LIKE '%' || lower(:query) || '%'))
              AND (:favoritesOnly = 0 OR d.isFavorite = 1)
              AND (:categoryId IS NULL OR d.categoryId = :categoryId)
            ORDER BY CASE WHEN :sortOrder = 'title' THEN lower(d.title) END ASC,
                     CASE WHEN :sortOrder = 'expiration' AND d.expirationDate IS NULL THEN 1 ELSE 0 END ASC,
                     CASE WHEN :sortOrder = 'expiration' THEN d.expirationDate END ASC,
                     d.createdAt DESC""",
    )
    fun observeDocuments(query: String, favoritesOnly: Boolean, categoryId: String?, sortOrder: String): Flow<List<DocumentWithTags>>

    @Transaction
    @Query("SELECT * FROM documents WHERE id = :id")
    suspend fun getDocument(id: String): DocumentWithTags?

    @Transaction
    @Query("SELECT * FROM documents WHERE id = :id")
    fun observeDocument(id: String): Flow<DocumentWithTags?>

    @Query("SELECT * FROM documents")
    suspend fun getAllDocuments(): List<DocumentEntity>

    @Query("SELECT * FROM categories ORDER BY sortOrder")
    fun observeCategories(): Flow<List<CategoryEntity>>

    @Query("SELECT c.*, COUNT(d.id) AS documentCount FROM categories c LEFT JOIN documents d ON d.categoryId = c.id GROUP BY c.id ORDER BY c.sortOrder")
    fun observeCategoriesWithCount(): Flow<List<CategoryWithCount>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCategories(categories: List<CategoryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(document: DocumentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTags(tags: List<DocumentTagEntity>)

    @Query("DELETE FROM document_tags WHERE documentId = :documentId")
    suspend fun deleteTags(documentId: String)

    @Transaction
    suspend fun saveDocument(document: DocumentEntity, tags: List<String>) {
        insertDocument(document)
        deleteTags(document.id)
        insertTags(tags.distinct().map { DocumentTagEntity(document.id, it) })
    }

    @Query("UPDATE documents SET isFavorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: String, favorite: Boolean)

    @Update
    suspend fun updateDocument(document: DocumentEntity)

    @Query("UPDATE documents SET isMissing = :missing WHERE id = :id")
    suspend fun setMissing(id: String, missing: Boolean)

    @Query("DELETE FROM documents WHERE id = :id")
    suspend fun deleteDocument(id: String)

    @Query("DELETE FROM documents")
    suspend fun clearDocuments()

    @Query("SELECT COUNT(*) FROM documents")
    fun observeDocumentCount(): Flow<Int>
}
