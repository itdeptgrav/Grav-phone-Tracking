package com.personal.callrecorder.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.personal.callrecorder.recording.AudioQuality
import com.personal.callrecorder.recording.CallAudioSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val AUTO_RECORD = booleanPreferencesKey("auto_record")
        val RECORD_INCOMING = booleanPreferencesKey("record_incoming")
        val RECORD_OUTGOING = booleanPreferencesKey("record_outgoing")
        val METHOD = stringPreferencesKey("recording_method")
        val QUALITY = stringPreferencesKey("audio_quality")
        val AUDIO_SOURCE = stringPreferencesKey("call_audio_source")
        val SAMPLE_RATE = intPreferencesKey("sample_rate_hz")
        val TRANSCRIPTION = booleanPreferencesKey("transcription_enabled")
        val AUTO_SUMMARY = booleanPreferencesKey("auto_summaries")
        val BIOMETRIC = booleanPreferencesKey("biometric_lock")
        val DELETE_OLDER_DAYS = intPreferencesKey("delete_older_days")
        val LEGAL_ACCEPTED = booleanPreferencesKey("legal_accepted")
        val IMPORT_FOLDER_URI = stringPreferencesKey("import_folder_uri")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            autoRecord = p[Keys.AUTO_RECORD] ?: true,
            recordIncoming = p[Keys.RECORD_INCOMING] ?: true,
            recordOutgoing = p[Keys.RECORD_OUTGOING] ?: true,
            recordingMethod = p[Keys.METHOD]?.let { safeMethod(it) } ?: RecordingMethod.AUTOMATIC,
            audioQuality = p[Keys.QUALITY]?.let { safeQuality(it) } ?: AudioQuality.STANDARD,
            callAudioSource = p[Keys.AUDIO_SOURCE]?.let { safeSource(it) } ?: CallAudioSource.VOICE_RECOGNITION,
            sampleRateHz = p[Keys.SAMPLE_RATE] ?: 16000,
            transcriptionEnabled = p[Keys.TRANSCRIPTION] ?: false,
            autoSummaries = p[Keys.AUTO_SUMMARY] ?: false,
            biometricLock = p[Keys.BIOMETRIC] ?: false,
            deleteOlderThanDays = p[Keys.DELETE_OLDER_DAYS] ?: 0,
            legalNoticeAccepted = p[Keys.LEGAL_ACCEPTED] ?: false,
            importFolderUri = p[Keys.IMPORT_FOLDER_URI]
        )
    }

    suspend fun setAutoRecord(value: Boolean) = edit { it[Keys.AUTO_RECORD] = value }
    suspend fun setRecordIncoming(value: Boolean) = edit { it[Keys.RECORD_INCOMING] = value }
    suspend fun setRecordOutgoing(value: Boolean) = edit { it[Keys.RECORD_OUTGOING] = value }
    suspend fun setMethod(value: RecordingMethod) = edit { it[Keys.METHOD] = value.name }
    suspend fun setQuality(value: AudioQuality) = edit { it[Keys.QUALITY] = value.name }
    suspend fun setCallAudioSource(value: CallAudioSource) = edit { it[Keys.AUDIO_SOURCE] = value.name }
    suspend fun setSampleRate(value: Int) = edit { it[Keys.SAMPLE_RATE] = value }
    suspend fun setTranscriptionEnabled(value: Boolean) = edit { it[Keys.TRANSCRIPTION] = value }
    suspend fun setAutoSummaries(value: Boolean) = edit { it[Keys.AUTO_SUMMARY] = value }
    suspend fun setBiometricLock(value: Boolean) = edit { it[Keys.BIOMETRIC] = value }
    suspend fun setDeleteOlderThanDays(value: Int) = edit { it[Keys.DELETE_OLDER_DAYS] = value }
    suspend fun setLegalAccepted(value: Boolean) = edit { it[Keys.LEGAL_ACCEPTED] = value }
    suspend fun setImportFolderUri(value: String?) = edit {
        if (value == null) it.remove(Keys.IMPORT_FOLDER_URI) else it[Keys.IMPORT_FOLDER_URI] = value
    }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit(block)
    }

    private fun safeMethod(name: String) =
        runCatching { RecordingMethod.valueOf(name) }.getOrDefault(RecordingMethod.AUTOMATIC)

    private fun safeQuality(name: String) =
        runCatching { AudioQuality.valueOf(name) }.getOrDefault(AudioQuality.STANDARD)

    private fun safeSource(name: String) =
        runCatching { CallAudioSource.valueOf(name) }.getOrDefault(CallAudioSource.VOICE_RECOGNITION)
}
