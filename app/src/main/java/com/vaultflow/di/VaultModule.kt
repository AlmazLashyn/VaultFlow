package com.vaultflow.di

import android.content.Context
import androidx.room.Room
import com.vaultflow.core.security.AndroidKeystoreKeyProvider
import com.vaultflow.core.security.EncryptedFileCipher
import com.vaultflow.core.security.KeyProvider
import com.vaultflow.data.local.DefaultCategories
import com.vaultflow.data.local.VaultDatabase
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class VaultBindings {
    @Binds
    @Singleton
    abstract fun bindKeyProvider(provider: AndroidKeystoreKeyProvider): KeyProvider
}

@Module
@InstallIn(SingletonComponent::class)
object VaultModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): VaultDatabase =
        Room.databaseBuilder(context, VaultDatabase::class.java, "vaultflow.db")
            .addCallback(object : androidx.room.RoomDatabase.Callback() {
                override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    super.onCreate(db)
                    DefaultCategories.all.forEach { category ->
                        db.execSQL(
                            "INSERT OR IGNORE INTO categories (id, name, icon, sortOrder) VALUES (?, ?, ?, ?)",
                            arrayOf<Any?>(category.id, category.name, category.icon, category.sortOrder),
                        )
                    }
                }
            })
            .build()

    @Provides
    @Singleton
    fun provideCipher(keyProvider: KeyProvider) = EncryptedFileCipher(keyProvider)

    @Provides
    fun provideDocumentDao(database: VaultDatabase) = database.documentDao()
}
