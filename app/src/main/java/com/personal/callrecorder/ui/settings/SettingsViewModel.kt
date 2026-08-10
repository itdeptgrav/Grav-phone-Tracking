package com.personal.callrecorder.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personal.callrecorder.ai.AiRepository
import com.personal.callrecorder.data.repository.CallRepository
import com.personal.callrecorder.data.settings.AppSettings
import com.personal.callrecorder.data.settings.RecordingMethod
import com.personal.callrecorder.data.settings.SettingsRepository
import com.personal.callrecorder.imports.RecordingImporter
import com.personal.callrecorder.recording.AudioQuality
import com.personal.callrecorder.recording.CallAudioSource
import com.personal.callrecorder.transcription.TranscriptionRepository
import com.personal.callrecorder.util.Formatters
import com.personal.callrecorder.util.StorageManager
import com.personal.callrecorder.util.TimeProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val storage: StorageManager,
    private val callRepository: CallRepository,
    private val time: TimeProvider,
    private val importer: RecordingImporter,
    transcriptionRepository: TranscriptionRepository,
    aiRepository: AiRepository
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    val transcriptionAvailable: Boolean = transcriptionRepository.isEnabled
    val aiAvailable: Boolean = aiRepository.isEnabled

    private val _storageText = MutableStateFlow("…")
    val storageText: StateFlow<String> = _storageText

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    init {
        refreshStorage()
    }

    fun refreshStorage() {
        viewModelScope.launch {
            val bytes = withContext(Dispatchers.IO) { storage.totalBytes() }
            _storageText.value = Formatters.fileSize(bytes)
        }
    }

    // Block bodies (return Unit) so these match (T) -> Unit callbacks in the UI.
    fun setAutoRecord(v: Boolean) { viewModelScope.launch { settingsRepository.setAutoRecord(v) } }
    fun setRecordIncoming(v: Boolean) { viewModelScope.launch { settingsRepository.setRecordIncoming(v) } }
    fun setRecordOutgoing(v: Boolean) { viewModelScope.launch { settingsRepository.setRecordOutgoing(v) } }
    fun setMethod(v: RecordingMethod) { viewModelScope.launch { settingsRepository.setMethod(v) } }
    fun setQuality(v: AudioQuality) { viewModelScope.launch { settingsRepository.setQuality(v) } }
    fun setCallAudioSource(v: CallAudioSource) { viewModelScope.launch { settingsRepository.setCallAudioSource(v) } }
    fun setSampleRate(v: Int) { viewModelScope.launch { settingsRepository.setSampleRate(v) } }
    fun setTranscription(v: Boolean) { viewModelScope.launch { settingsRepository.setTranscriptionEnabled(v) } }
    fun setAutoSummaries(v: Boolean) { viewModelScope.launch { settingsRepository.setAutoSummaries(v) } }
    fun setBiometricLock(v: Boolean) { viewModelScope.launch { settingsRepository.setBiometricLock(v) } }
    fun setDeleteOlderThanDays(v: Int) { viewModelScope.launch { settingsRepository.setDeleteOlderThanDays(v) } }

    /** Apply the retention policy immediately. */
    fun purgeOldNow() {
        viewModelScope.launch {
            val days = settings.value.deleteOlderThanDays
            if (days <= 0) {
                _message.value = "Retention is set to keep forever"
                return@launch
            }
            val cutoff = time.now() - days.toLong() * 24 * 60 * 60 * 1000
            val old = callRepository.getOlderThan(cutoff)
            withContext(Dispatchers.IO) {
                old.forEach { rec ->
                    storage.delete(rec.recordingPath)
                    callRepository.delete(rec.id)
                }
            }
            refreshStorage()
            _message.value = "Deleted ${old.size} old call(s)"
        }
    }

    fun clearMessage() { _message.value = null }

    /** Persist the chosen OEM recordings folder and immediately import from it. */
    fun setImportFolder(uri: String) {
        viewModelScope.launch {
            settingsRepository.setImportFolderUri(uri)
            importNow()
        }
    }

    /** Re-scan the configured folder for new OEM recordings. */
    fun importNow() {
        viewModelScope.launch {
            _message.value = "Importing…"
            val summary = importer.importFromFolder()
            refreshStorage()
            _message.value = summary.message()
        }
    }
}
