package com.vaultflow.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.vaultflow.MainActivity
import com.vaultflow.R
import com.vaultflow.data.preferences.VaultPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test

class VaultFlowUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun firstRunOnboardingCanContinueIntoVaultAndSettings() {
        val activity = compose.activity
        val preferences = VaultPreferences(activity.applicationContext)
        val original = runBlocking {
            StoredSettings(
                onboarding = preferences.onboardingComplete.first(),
                biometric = preferences.biometricLock.first(),
                reminders = preferences.expirationRemindersEnabled.first(),
                intervals = preferences.reminderIntervals.first(),
            )
        }
        val continueLabel = activity.getString(R.string.continue_label)
        val notNow = activity.getString(R.string.not_now)
        val vaultTitle = activity.getString(R.string.your_vault)
        try {
            // Force the first-run path and restore the emulator's saved settings at the end.
            runBlocking { preferences.clear() }
            compose.waitUntil(10_000) { hasNode(continueLabel) }
            compose.onNodeWithText(continueLabel).performClick()
            compose.onNodeWithText(continueLabel).performClick()
            compose.onNodeWithText(notNow).performClick()

            compose.waitUntil(10_000) { hasNode(vaultTitle) }
            compose.onNodeWithText(vaultTitle).assertIsDisplayed()
            compose.onNodeWithText(activity.getString(R.string.settings)).performClick()
            compose.waitUntil(10_000) { hasNode(activity.getString(R.string.biometric_lock)) }
            compose.onNodeWithText(activity.getString(R.string.biometric_lock)).assertIsDisplayed()
            compose.onNodeWithText(activity.getString(R.string.expiration_reminders)).assertIsDisplayed()
        } finally {
            runBlocking {
                preferences.clear()
                preferences.setBiometricLock(original.biometric)
                preferences.setExpirationRemindersEnabled(original.reminders)
                preferences.setReminderIntervals(original.intervals)
                if (original.onboarding) preferences.completeOnboarding()
            }
        }
    }

    private fun hasNode(text: String): Boolean = runCatching {
        compose.onNodeWithText(text).assertIsDisplayed()
    }.isSuccess

    private data class StoredSettings(val onboarding: Boolean, val biometric: Boolean, val reminders: Boolean, val intervals: Set<Int>)
}
