package com.vaultflow.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.vaultDataStore: DataStore<Preferences> by preferencesDataStore(name = "vault_preferences")

@Singleton
class VaultPreferences @Inject constructor(@ApplicationContext context: Context) {
    private val store = context.vaultDataStore
    val onboardingComplete: Flow<Boolean> = store.data.map { it[ONBOARDING_COMPLETE] ?: false }
    val biometricLock: Flow<Boolean> = store.data.map { it[BIOMETRIC_LOCK] ?: false }
    val expirationRemindersEnabled: Flow<Boolean> = store.data.map { it[REMINDERS_ENABLED] ?: false }
    val reminderIntervals: Flow<Set<Int>> = store.data.map { preferences ->
        preferences[REMINDER_INTERVALS]?.split(',')?.mapNotNull(String::toIntOrNull)?.filter { it in SUPPORTED_INTERVALS }?.toSet()
            ?: SUPPORTED_INTERVALS
    }

    suspend fun completeOnboarding() = store.edit { it[ONBOARDING_COMPLETE] = true }
    suspend fun setBiometricLock(enabled: Boolean) = store.edit { it[BIOMETRIC_LOCK] = enabled }
    suspend fun clear() = store.edit { it.clear() }
    suspend fun setExpirationRemindersEnabled(enabled: Boolean) = store.edit { it[REMINDERS_ENABLED] = enabled }
    suspend fun setReminderIntervals(days: Set<Int>) = store.edit {
        it[REMINDER_INTERVALS] = days.filter { day -> day in SUPPORTED_INTERVALS }.sortedDescending().joinToString(",")
    }

    private companion object {
        val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
        val BIOMETRIC_LOCK = booleanPreferencesKey("biometric_lock")
        val REMINDERS_ENABLED = booleanPreferencesKey("expiration_reminders_enabled")
        val REMINDER_INTERVALS = stringPreferencesKey("expiration_reminder_intervals")
        val SUPPORTED_INTERVALS = setOf(30, 7, 1)
    }
}
