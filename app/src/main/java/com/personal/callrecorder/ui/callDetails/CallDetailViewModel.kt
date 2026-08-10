package com.personal.callrecorder.ui.callDetails

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personal.callrecorder.ai.AiRepository
import com.personal.callrecorder.data.entity.CallRecord
import com.personal.callrecorder.data.repository.CallRepository
import com.personal.callrecorder.transcription.TranscriptionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CallDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val callRepository: CallRepository,
    private val transcriptionRepository: TranscriptionRepository,
    private val aiRepository: AiRepository
) : ViewModel() {

    val callId: Long = savedStateHandle.get<Long>("callId") ?: -1L

    val record: StateFlow<CallRecord?> = callRepository.observeById(callId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val transcriptionEnabled: Boolean get() = transcriptionRepository.isEnabled
    val aiEnabled: Boolean get() = aiRepository.isEnabled

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    fun saveNotes(text: String) {
        viewModelScope.launch {
            callRepository.updateNotes(callId, text.ifBlank { null })
            _message.value = "Notes saved"
        }
    }

    fun transcribe() {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            val error = transcriptionRepository.transcribe(callId)
            _busy.value = false
            _message.value = error ?: "Transcription complete"
        }
    }

    fun summarize() {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            val error = aiRepository.summarize(callId)
            _busy.value = false
            _message.value = error ?: "Summary complete"
        }
    }

    fun delete(onDeleted: () -> Unit) {
        viewModelScope.launch {
            val rec = callRepository.getById(callId)
            rec?.recordingPath?.let { path ->
                runCatching { java.io.File(path).delete() }
            }
            callRepository.delete(callId)
            onDeleted()
        }
    }

    fun clearMessage() {
        _message.value = null
    }
}
