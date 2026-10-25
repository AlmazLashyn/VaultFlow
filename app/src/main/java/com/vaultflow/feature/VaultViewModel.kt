package com.vaultflow.feature

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultflow.core.security.FileTooLargeException
import com.vaultflow.data.local.CategoryEntity
import com.vaultflow.data.local.CategoryWithCount
import com.vaultflow.data.local.DocumentEntity
import com.vaultflow.data.local.DocumentWithTags
import com.vaultflow.data.preferences.VaultPreferences
import com.vaultflow.data.repository.DocumentDraft
import com.vaultflow.data.repository.OpenedDocument
import com.vaultflow.data.repository.UnsupportedDocumentException
import com.vaultflow.data.repository.VaultRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ImportProgress(val bytesRead: Long, val finished: Boolean = false)

sealed interface VaultMessage {
    data object ImportFailed : VaultMessage
    data object TooLarge : VaultMessage
    data object UnsupportedFile : VaultMessage
    data object OperationFailed : VaultMessage
    data object DataCleared : VaultMessage
}

@HiltViewModel
class VaultViewModel @Inject constructor(
    private val repository: VaultRepository,
    private val preferences: VaultPreferences,
) : ViewModel() {
    private val query = MutableStateFlow("")
    val searchQuery = query.asStateFlow()
    private val favoritesOnly = MutableStateFlow(false)
    private val categoryId = MutableStateFlow<String?>(null)
    private val sortOrder = MutableStateFlow("recent")
    private val _messages = MutableSharedFlow<VaultMessage>(extraBufferCapacity = 1)
    val messages = _messages.asSharedFlow()
    private val _importProgress = MutableStateFlow<ImportProgress?>(null)
    val importProgress = _importProgress.asStateFlow()
    private val _viewer = MutableStateFlow<OpenedDocument?>(null)
    val viewer = _viewer.asStateFlow()
    private val _locked = MutableStateFlow(false)
    val locked = _locked.asStateFlow()
    private val _onboardingComplete = MutableStateFlow<Boolean?>(null)
    val onboardingComplete = _onboardingComplete.asStateFlow()
    private val _biometricEnabled = MutableStateFlow(false)
    val biometricEnabled = _biometricEnabled.asStateFlow()
    private val _expirationRemindersEnabled = MutableStateFlow(false)
    val expirationRemindersEnabled = _expirationRemindersEnabled.asStateFlow()
    private val _reminderIntervals = MutableStateFlow(setOf(30, 7, 1))
    val reminderIntervals = _reminderIntervals.asStateFlow()
    private val _export = MutableStateFlow<OpenedDocument?>(null)
    val export = _export.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val documents = combine(query, favoritesOnly, categoryId, sortOrder) { q, favorites, category, sort ->
        SearchFilters(q, favorites, category, sort)
    }.flatMapLatest { filters ->
        repository.observeDocuments(filters.query, filters.favorites, filters.categoryId, filters.sortOrder)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val categories = repository.observeCategories().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val categoriesWithCount = repository.observeCategoriesWithCount().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val documentCount = repository.observeDocumentCount().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    init {
        viewModelScope.launch {
            combine(preferences.onboardingComplete, preferences.biometricLock, preferences.expirationRemindersEnabled, preferences.reminderIntervals) { onboarding, biometric, reminders, intervals ->
                SettingsSnapshot(onboarding, biometric, reminders, intervals)
            }.collect { snapshot ->
                    val onboarding = snapshot.onboarding
                    val biometric = snapshot.biometric
                    _onboardingComplete.value = onboarding
                    _biometricEnabled.value = biometric
                    _expirationRemindersEnabled.value = snapshot.reminders
                    _reminderIntervals.value = snapshot.intervals
                    if (onboarding && biometric) _locked.value = true
                    if (!biometric) _locked.value = false
                }
            }
        viewModelScope.launch { runCatching { repository.reconcileStorage() } }
    }

    fun search(value: String) { query.value = value }
    fun selectCategory(value: String?) { categoryId.value = value; favoritesOnly.value = false }
    fun showFavorites(value: Boolean) { favoritesOnly.value = value; categoryId.value = null }
    fun setSortOrder(value: String) { sortOrder.value = value }
    fun clearFilters() { query.value = ""; favoritesOnly.value = false; categoryId.value = null }

    fun finishOnboarding() = viewModelScope.launch {
        preferences.completeOnboarding()
        _onboardingComplete.value = true
        _locked.value = _biometricEnabled.value
    }

    fun setBiometricEnabled(enabled: Boolean, canAuthenticate: Boolean): Boolean {
        if (enabled && !canAuthenticate) return false
        _biometricEnabled.value = enabled
        _locked.value = enabled
        viewModelScope.launch {
            preferences.setBiometricLock(enabled)
        }
        return true
    }

    fun setExpirationRemindersEnabled(enabled: Boolean) = viewModelScope.launch {
        preferences.setExpirationRemindersEnabled(enabled)
        repository.refreshReminders()
    }

    fun setReminderInterval(days: Int, enabled: Boolean) = viewModelScope.launch {
        if (days !in setOf(30, 7, 1)) return@launch
        val intervals = preferences.reminderIntervals.first().toMutableSet()
        if (enabled) intervals += days else intervals -= days
        preferences.setReminderIntervals(intervals)
        repository.refreshReminders()
    }

    fun refreshReminders() = viewModelScope.launch { repository.refreshReminders() }

    fun lockOnBackground() {
        if (_biometricEnabled.value) {
            _locked.value = true
            closeViewer()
        }
    }

    fun unlock() { _locked.value = false }

    fun importDocument(uri: Uri, draft: DocumentDraft) = viewModelScope.launch {
        _importProgress.value = ImportProgress(0)
        try {
            repository.importDocument(uri, draft) { bytes -> _importProgress.value = ImportProgress(bytes) }
            _importProgress.value = ImportProgress(0, finished = true)
        } catch (error: CancellationException) {
            throw error
        } catch (_: FileTooLargeException) {
            _messages.tryEmit(VaultMessage.TooLarge)
            _importProgress.value = null
        } catch (_: UnsupportedDocumentException) {
            _messages.tryEmit(VaultMessage.UnsupportedFile)
            _importProgress.value = null
        } catch (_: Throwable) {
            _messages.tryEmit(VaultMessage.ImportFailed)
            _importProgress.value = null
        }
    }

    fun observeDocument(id: String) = repository.observeDocument(id)

    fun toggleFavorite(document: DocumentEntity) = viewModelScope.launch {
        try { repository.setFavorite(document, !document.isFavorite) }
        catch (error: CancellationException) { throw error }
        catch (_: Throwable) { _messages.tryEmit(VaultMessage.OperationFailed) }
    }

    fun updateDocument(document: DocumentEntity, tags: List<String>) = viewModelScope.launch {
        try { repository.updateDocument(document, tags) }
        catch (error: CancellationException) { throw error }
        catch (_: Throwable) { _messages.tryEmit(VaultMessage.OperationFailed) }
    }

    fun deleteDocument(id: String, onFinished: (Boolean) -> Unit = {}) = viewModelScope.launch {
        try {
            repository.deleteDocument(id)
            onFinished(true)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            _messages.tryEmit(VaultMessage.OperationFailed)
            onFinished(false)
        }
    }

    fun clearAllData() = viewModelScope.launch {
        try {
            repository.clearDocuments()
            preferences.clear()
            _onboardingComplete.value = false
            _biometricEnabled.value = false
            _locked.value = false
            _messages.tryEmit(VaultMessage.DataCleared)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            _messages.tryEmit(VaultMessage.OperationFailed)
        }
    }

    fun openDocument(id: String) = viewModelScope.launch {
        closeViewer()
        try {
            _viewer.value = repository.openForViewing(id)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            _messages.tryEmit(VaultMessage.OperationFailed)
        }
    }

    fun prepareExport(id: String) = viewModelScope.launch {
        finishExport()
        try {
            _export.value = repository.prepareExport(id)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            _messages.tryEmit(VaultMessage.OperationFailed)
        }
    }

    fun finishExport() {
        val opened = _export.value ?: return
        _export.value = null
        viewModelScope.launch { repository.releaseExport(opened.plaintextFile) }
    }

    fun closeViewer() {
        val opened = _viewer.value ?: return
        _viewer.value = null
        viewModelScope.launch { repository.releasePlaintext(opened.plaintextFile) }
    }

    fun dismissImportProgress() { _importProgress.value = null }

    private data class SettingsSnapshot(val onboarding: Boolean, val biometric: Boolean, val reminders: Boolean, val intervals: Set<Int>)
    private data class SearchFilters(val query: String, val favorites: Boolean, val categoryId: String?, val sortOrder: String)
}
