package com.vaultflow.data.local

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val icon: String,
    val sortOrder: Int,
)

@Entity(
    tableName = "documents",
    foreignKeys = [ForeignKey(entity = CategoryEntity::class, parentColumns = ["id"], childColumns = ["categoryId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index("categoryId"), Index("createdAt"), Index("expirationDate"), Index("isFavorite")],
)
data class DocumentEntity(
    @PrimaryKey val id: String,
    val title: String,
    val categoryId: String,
    val encryptedFileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val createdAt: Long,
    val documentDate: Long?,
    val expirationDate: Long?,
    val isFavorite: Boolean = false,
    val encryptionVersion: Int = 1,
    val isMissing: Boolean = false,
)

@Entity(
    tableName = "document_tags",
    primaryKeys = ["documentId", "tag"],
    foreignKeys = [ForeignKey(entity = DocumentEntity::class, parentColumns = ["id"], childColumns = ["documentId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("tag")],
)
data class DocumentTagEntity(val documentId: String, val tag: String)

data class DocumentWithTags(
    @Embedded val document: DocumentEntity,
    @Relation(parentColumn = "id", entityColumn = "documentId") val tags: List<DocumentTagEntity>,
)

data class CategoryWithCount(
    @Embedded val category: CategoryEntity,
    @ColumnInfo(name = "documentCount") val documentCount: Int,
)

object DefaultCategories {
    val all = listOf(
        CategoryEntity("personal", "Personal", "person", 0),
        CategoryEntity("finance", "Finance", "payments", 1),
        CategoryEntity("work", "Work", "work", 2),
        CategoryEntity("contracts", "Contracts", "contract", 3),
        CategoryEntity("receipts", "Receipts", "receipt", 4),
        CategoryEntity("insurance", "Insurance", "shield", 5),
        CategoryEntity("other", "Other", "folder", 6),
    )
}
