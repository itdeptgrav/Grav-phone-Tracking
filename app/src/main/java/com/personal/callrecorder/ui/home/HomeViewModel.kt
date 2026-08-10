package com.personal.callrecorder.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.personal.callrecorder.data.entity.CallRecord
import com.personal.callrecorder.data.repository.CallRepository
import com.personal.callrecorder.data.settings.SettingsRepository
import com.personal.callrecorder.imports.RecordingImporter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    repository: CallRepository,
    private val importer: RecordingImporter,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val calls: StateFlow<List<CallRecord>> = repository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        // If an OEM recordings folder is configured, scan it for new files each
        // time home opens. Already-imported files are skipped; the list updates
        // reactively as records are inserted.
        viewModelScope.launch {
            val hasFolder = settingsRepository.settings.first().importFolderUri != null
            if (hasFolder) runCatching { importer.importFromFolder() }
        }
    }
}
