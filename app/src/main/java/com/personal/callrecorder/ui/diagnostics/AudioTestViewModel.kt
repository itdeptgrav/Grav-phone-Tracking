package com.personal.callrecorder.ui.diagnostics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personal.callrecorder.recording.AudioSourceProbe
import com.personal.callrecorder.recording.CallAudioSource
import com.personal.callrecorder.recording.ProbeResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AudioTestViewModel @Inject constructor(
    private val probe: AudioSourceProbe
) : ViewModel() {

    data class Row(
        val source: CallAudioSource,
        val result: ProbeResult? = null,
        val running: Boolean = false
    )

    private val _rows = MutableStateFlow(CallAudioSource.entries.map { Row(it) })
    val rows: StateFlow<List<Row>> = _rows

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy

    fun test(source: CallAudioSource) {
        if (_busy.value) return
        _busy.value = true
        _rows.update { list -> list.map { if (it.source == source) it.copy(running = true) else it } }
        viewModelScope.launch {
            val result = probe.probe(source)
            _rows.update { list ->
                list.map { if (it.source == source) it.copy(result = result, running = false) else it }
            }
            _busy.value = false
        }
    }
}
