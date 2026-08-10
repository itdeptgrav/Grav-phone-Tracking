package com.personal.callrecorder.ui.diagnostics

import androidx.lifecycle.ViewModel
import com.personal.callrecorder.capability.CapabilityDetector
import com.personal.callrecorder.capability.DeviceCapabilities
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class DiagnosticsViewModel @Inject constructor(
    private val detector: CapabilityDetector
) : ViewModel() {

    private val _capabilities = MutableStateFlow(detector.detect())
    val capabilities: StateFlow<DeviceCapabilities> = _capabilities

    /** Re-run detection (e.g. after returning from a permission grant). */
    fun refresh() {
        _capabilities.value = detector.detect()
    }
}
