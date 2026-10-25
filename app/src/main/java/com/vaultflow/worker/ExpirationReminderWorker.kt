package com.vaultflow.worker

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.room.Room
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.vaultflow.R
import com.vaultflow.data.local.VaultDatabase
import com.vaultflow.data.preferences.VaultPreferences
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

class ExpirationReminderWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val documentId = inputData.getString(KEY_DOCUMENT_ID) ?: return Result.failure()
        val expectedExpiration = inputData.getLong(KEY_EXPECTED_EXPIRATION, -1)
        val daysBefore = inputData.getInt(KEY_DAYS_BEFORE, -1)
        if (expectedExpiration < 0 || daysBefore !in SUPPORTED_INTERVALS) return Result.failure()

        return try {
            val preferences = VaultPreferences(applicationContext)
            if (!preferences.expirationRemindersEnabled.first() || daysBefore !in preferences.reminderIntervals.first()) return Result.success()

            val database = Room.databaseBuilder(applicationContext, VaultDatabase::class.java, DATABASE_NAME).build()
            val document = try {
                database.documentDao().getDocument(documentId)?.document
            } finally {
                database.close()
            } ?: return Result.success()
            if (document.expirationDate != expectedExpiration) return Result.success()

            val zone = ZoneId.systemDefault()
            val selectedIntervals = preferences.reminderIntervals.first()
            if (!ReminderTiming.shouldNotify(expectedExpiration, daysBefore, selectedIntervals, LocalDate.now(zone), zone)) return Result.success()
            notifyIfAllowed(documentId, daysBefore)
            Result.success()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            Result.retry()
        }
    }

    private fun notifyIfAllowed(documentId: String, daysBefore: Int) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, applicationContext.getString(R.string.expiration_reminders), NotificationManager.IMPORTANCE_DEFAULT))
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(applicationContext.getString(R.string.expiration_notification_title))
            .setContentText(applicationContext.getString(R.string.expiration_notification_body))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        NotificationManagerCompat.from(applicationContext).notify(documentId.hashCode() xor (daysBefore shl 16), notification)
    }

    companion object {
        const val KEY_DOCUMENT_ID = "document_id"
        const val KEY_EXPECTED_EXPIRATION = "expected_expiration"
        const val KEY_DAYS_BEFORE = "days_before"
        private const val CHANNEL_ID = "expiration_reminders"
        private const val DATABASE_NAME = "vaultflow.db"
        private val SUPPORTED_INTERVALS = setOf(30, 7, 1)
    }
}
