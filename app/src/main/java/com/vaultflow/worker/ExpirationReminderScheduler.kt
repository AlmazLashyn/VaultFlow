package com.vaultflow.worker

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.vaultflow.data.local.DocumentEntity
import com.vaultflow.data.preferences.VaultPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

@Singleton
class ExpirationReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: VaultPreferences,
) {
    private val workManager by lazy { WorkManager.getInstance(context) }

    suspend fun schedule(document: DocumentEntity) {
        val selected = preferences.reminderIntervals.first()
        if (document.expirationDate == null || !preferences.expirationRemindersEnabled.first()) {
            SUPPORTED_INTERVALS.forEach { workManager.cancelUniqueWork(workName(document.id, it)) }
            return
        }
        val zone = ZoneId.systemDefault()
        SUPPORTED_INTERVALS.forEach { days ->
            val workName = workName(document.id, days)
            if (days !in selected) {
                workManager.cancelUniqueWork(workName)
                return@forEach
            }
            val delay = (ReminderTiming.triggerAt(document.expirationDate, days, zone) - System.currentTimeMillis()).coerceAtLeast(0)
            val data = workDataOf(
                ExpirationReminderWorker.KEY_DOCUMENT_ID to document.id,
                ExpirationReminderWorker.KEY_EXPECTED_EXPIRATION to document.expirationDate,
                ExpirationReminderWorker.KEY_DAYS_BEFORE to days,
            )
            val request = OneTimeWorkRequestBuilder<ExpirationReminderWorker>()
                .setInputData(data)
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .addTag(ALL_REMINDERS_TAG)
                .addTag(documentTag(document.id))
                .build()
            workManager.enqueueUniqueWork(workName, ExistingWorkPolicy.REPLACE, request)
        }
    }

    suspend fun refresh(documents: List<DocumentEntity>) {
        if (!preferences.expirationRemindersEnabled.first()) {
            cancelAll()
            return
        }
        documents.forEach { schedule(it) }
    }

    fun cancelDocument(documentId: String) {
        SUPPORTED_INTERVALS.forEach { workManager.cancelUniqueWork(workName(documentId, it)) }
    }

    fun cancelAll() {
        workManager.cancelAllWorkByTag(ALL_REMINDERS_TAG)
    }

    private fun workName(id: String, days: Int) = "vaultflow-expiration-$id-$days"
    private fun documentTag(id: String) = "vaultflow-document-$id"

    companion object {
        const val ALL_REMINDERS_TAG = "vaultflow-expiration-reminders"
        private val SUPPORTED_INTERVALS = setOf(30, 7, 1)
    }
}
