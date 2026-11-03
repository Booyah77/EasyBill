package com.example.personalbookkeeping.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.example.personalbookkeeping.data.ai.AiBookkeepingManager
import com.example.personalbookkeeping.data.ai.AiBookkeepingResult
import com.example.personalbookkeeping.data.ai.AiException
import com.example.personalbookkeeping.data.ai.AiFailure
import com.example.personalbookkeeping.data.ai.AiPhase
import com.example.personalbookkeeping.data.ai.AiSettings
import com.example.personalbookkeeping.data.ai.AiSettingsStore
import com.example.personalbookkeeping.data.ai.HttpsChatCompletionsClient
import com.example.personalbookkeeping.data.repository.LedgerRepository
import com.example.personalbookkeeping.domain.model.LedgerEntry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AiUiState(
    val draft: String = "",
    val phase: AiPhase = AiPhase.IDLE,
    val settings: AiSettings? = null,
    val loadingSettings: Boolean = true,
    val settingsBusy: Boolean = false,
    val settingsError: AiFailure? = null,
    val error: AiFailure? = null,
    val canRetrySave: Boolean = false,
    val savedEntries: List<LedgerEntry> = emptyList()
) {
    val busy: Boolean get() = phase != AiPhase.IDLE
}

class AiBookkeepingViewModel(
    private val savedState: SavedStateHandle,
    private val settingsStore: AiSettingsStore,
    repository: LedgerRepository
) : ViewModel() {
    private val manager = AiBookkeepingManager(settingsStore, HttpsChatCompletionsClient(), repository)
    private val mutableState = MutableStateFlow(AiUiState(draft = savedState["ai_draft"] ?: ""))
    val uiState = mutableState.asStateFlow()
    private var operation: Job? = null

    init {
        viewModelScope.launch {
            try {
                val settings = withContext(Dispatchers.IO) { settingsStore.loadSettings() }
                mutableState.update { it.copy(settings = settings, loadingSettings = false) }
            } catch (exception: AiException) {
                mutableState.update { it.copy(settingsError = exception.failure, loadingSettings = false) }
            }
        }
    }

    fun updateDraft(value: String) {
        if (uiState.value.busy || uiState.value.canRetrySave) return
        savedState["ai_draft"] = value
        mutableState.update { it.copy(draft = value, error = null) }
    }

    fun recognize() {
        if (uiState.value.busy || uiState.value.canRetrySave || operation?.isActive == true) return
        runOperation(retry = false)
    }

    fun retrySave() {
        if (uiState.value.busy || !uiState.value.canRetrySave || operation?.isActive == true) return
        runOperation(retry = true)
    }

    private fun runOperation(retry: Boolean) {
        val text = uiState.value.draft
        mutableState.update { it.copy(phase = if (retry) AiPhase.SAVING else AiPhase.RECOGNIZING, error = null) }
        operation = viewModelScope.launch {
            try {
                val updatePhase: (AiPhase) -> Unit = { phase -> mutableState.update { it.copy(phase = phase) } }
                val result = if (retry) manager.retrySave(updatePhase) else manager.recognizeAndSave(text, updatePhase)
                when (result) {
                    is AiBookkeepingResult.Saved -> {
                        savedState["ai_draft"] = ""
                        mutableState.update {
                            it.copy(draft = "", savedEntries = result.entries, error = null, canRetrySave = false)
                        }
                    }
                    is AiBookkeepingResult.Failed -> mutableState.update {
                        it.copy(error = result.failure, canRetrySave = result.canRetrySave)
                    }
                    AiBookkeepingResult.Busy -> Unit
                }
            } catch (exception: CancellationException) {
                mutableState.update { it.copy(canRetrySave = manager.hasPendingSave) }
                throw exception
            } finally {
                mutableState.update { it.copy(phase = AiPhase.IDLE) }
            }
        }
    }

    fun cancelRecognition() {
        if (uiState.value.phase == AiPhase.RECOGNIZING) operation?.cancel()
    }

    fun newDraft() {
        if (!uiState.value.busy && !uiState.value.canRetrySave) {
            mutableState.update { it.copy(savedEntries = emptyList(), error = null) }
        }
    }

    fun saveSettings(settings: AiSettings, key: String, onSaved: () -> Unit) {
        if (uiState.value.settingsBusy || uiState.value.loadingSettings) return
        mutableState.update { it.copy(settingsBusy = true, settingsError = null) }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { settingsStore.save(settings, key) }
                mutableState.update { it.copy(settings = settings, settingsError = null, error = null) }
                onSaved()
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: AiException) {
                mutableState.update { it.copy(settingsError = exception.failure) }
            } finally {
                mutableState.update { it.copy(settingsBusy = false) }
            }
        }
    }

    fun clearSettings(onCleared: () -> Unit) {
        if (uiState.value.settingsBusy) return
        mutableState.update { it.copy(settingsBusy = true, settingsError = null) }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { settingsStore.clear() }
                mutableState.update { it.copy(settings = null, error = null) }
                onCleared()
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: AiException) {
                mutableState.update { it.copy(settingsError = exception.failure) }
            } finally {
                mutableState.update { it.copy(settingsBusy = false) }
            }
        }
    }
}

class AiBookkeepingViewModelFactory(
    private val settingsStore: AiSettingsStore,
    private val repository: LedgerRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        require(modelClass.isAssignableFrom(AiBookkeepingViewModel::class.java))
        return AiBookkeepingViewModel(extras.createSavedStateHandle(), settingsStore, repository) as T
    }
}
