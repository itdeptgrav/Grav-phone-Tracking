package com.personal.callrecorder.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personal.callrecorder.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Gate state for the very top of the UI tree. */
data class AppGate(
    val loaded: Boolean = false,
    val legalAccepted: Boolean = false,
    val biometricLock: Boolean = false
)

@HiltViewModel
class AppViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val gate: StateFlow<AppGate> = settingsRepository.settings
        .map { AppGate(true, it.legalNoticeAccepted, it.biometricLock) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppGate())

    fun acceptLegal() {
        viewModelScope.launch { settingsRepository.setLegalAccepted(true) }
    }
}
