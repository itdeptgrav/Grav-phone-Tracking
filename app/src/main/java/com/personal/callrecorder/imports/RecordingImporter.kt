package com.personal.callrecorder.imports

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.personal.callrecorder.call.CallLogResolver
import com.personal.callrecorder.contacts.ContactResolver
import com.personal.callrecorder.data.entity.CallRecord
import com.personal.callrecorder.data.entity.RecordingStatus
import com.personal.callrecorder.data.repository.CallRepository
import com.personal.callrecorder.data.settings.SettingsRepository
import com.personal.callrecorder.util.StorageManager
import com.personal.callrecorder.util.TimeProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class ImportSummary(
    val imported: Int = 0,
    val skipped: Int = 0,
    val failed: Int = 0,
    val error: String? = null
) {
    fun message(): String = when {
        error != null -> error
        imported == 0 && skipped == 0 -> "No recordings found in the folder"
        imported == 0 -> "Already up to date"
        else -> "Imported $imported recording(s)"
    }
}

/**
 * Imports recordings produced by the phone's own (OEM) call recorder — e.g.
 * OPPO/ColorOS — from a user-selected folder, copies them into app-private
 * storage, attaches contact/number/date/duration metadata, and stores them as
 * [CallRecord]s. These then flow through the same playback / transcription / AI
 * / search pipeline as any other recording.
 *
 * This is the realistic way to get true two-way call audio on a non-rooted
 * device: the OEM dialer (which has system privileges) does the recording; this
 * app is the organizing + intelligence layer on top.
 */
@Singleton
class RecordingImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
    private val repository: CallRepository,
    private val contactResolver: ContactResolver,
    private val callLogResolver: CallLogResolver,
    private val storage: StorageManager,
    private val time: TimeProvider
) {
    /**
     * Import a single recording shared into the app via ACTION_SEND (e.g. Google
     * Phone → Share → PersonalCallRecorder). The URI is a temporary read grant
     * from the sharer's own FileProvider; we copy its bytes with ContentResolver.
     * Number/direction are recovered from the just-ended call in the call log.
     */
    suspend fun importFromUri(uri: Uri): ImportSummary = withContext(Dispatchers.IO) {
        val name = queryDisplayName(uri) ?: "record-${time.now()}.wav"
        if (repository.existsImport(name)) return@withContext ImportSummary(skipped = 1)
        val ok = runCatching { importOneFromUri(uri, name) }
            .onFailure { Log.e(TAG, "Failed to import shared uri $name", it) }
            .isSuccess
        if (ok) ImportSummary(imported = 1) else ImportSummary(failed = 1, error = "Import failed")
    }

    private suspend fun importOneFromUri(uri: Uri, name: String) {
        val ext = name.substringAfterLast('.', "wav").lowercase()
        val startTime = time.now()

        val dest = storage.newRecordingFile(startTime, ext)
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Cannot open shared stream" }
            dest.outputStream().use { output -> input.copyTo(output) }
        }
        if (dest.length() <= 0L) {
            dest.delete()
            throw IllegalStateException("Shared file is empty")
        }

        val duration = readDurationMillis(dest)
        val meta = FilenameParser.parse(name)
        val logEntry = callLogResolver.mostRecent()
        val number = logEntry?.number ?: meta.number
        val direction = logEntry?.direction ?: meta.direction
        val contactName = contactResolver.resolveName(number) ?: meta.displayFallback
        val now = time.now()

        repository.insertImported(
            CallRecord(
                phoneNumber = number,
                contactName = contactName,
                direction = direction,
                startTime = startTime,
                endTime = startTime + duration,
                durationMillis = duration,
                recordingPath = dest.absolutePath,
                recordingStatus = RecordingStatus.COMPLETED,
                recordingMethod = "IMPORTED_SHARE",
                importSourceName = name,
                createdAt = now,
                updatedAt = now
            )
        )
    }

    private fun queryDisplayName(uri: Uri): String? = try {
        context.contentResolver.query(
            uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null
        )?.use { c ->
            if (c.moveToFirst()) {
                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) c.getString(idx) else null
            } else null
        }
    } catch (t: Throwable) {
        null
    }

    suspend fun importFromFolder(): ImportSummary = withContext(Dispatchers.IO) {
        val uriString = settings.settings.first().importFolderUri
            ?: return@withContext ImportSummary(error = "No import folder selected")

        val tree = runCatching { DocumentFile.fromTreeUri(context, Uri.parse(uriString)) }.getOrNull()
        if (tree == null || !tree.canRead()) {
            return@withContext ImportSummary(error = "Cannot read the selected folder")
        }

        var imported = 0
        var skipped = 0
        var failed = 0

        for (doc in tree.listFiles()) {
            if (!doc.isFile) continue
            val name = doc.name ?: continue
            if (!isAudio(name, doc.type)) continue
            if (repository.existsImport(name)) {
                skipped++
                continue
            }
            val ok = runCatching { importOne(doc, name) }
                .onFailure { Log.e(TAG, "Failed to import $name", it) }
                .isSuccess
            if (ok) imported++ else failed++
        }
        ImportSummary(imported, skipped, failed)
    }

    private suspend fun importOne(doc: DocumentFile, name: String) {
        val ext = name.substringAfterLast('.', "m4a").lowercase()
        val startTime = doc.lastModified().takeIf { it > 0 } ?: time.now()

        val dest = storage.newRecordingFile(startTime, ext)
        context.contentResolver.openInputStream(doc.uri).use { input ->
            requireNotNull(input) { "Cannot open source stream" }
            dest.outputStream().use { output -> input.copyTo(output) }
        }
        if (dest.length() <= 0L) {
            dest.delete()
            throw IllegalStateException("Copied file is empty")
        }

        val meta = FilenameParser.parse(name)
        val duration = readDurationMillis(dest)
        val contactName = contactResolver.resolveName(meta.number) ?: meta.displayFallback
        val now = time.now()

        repository.insertImported(
            CallRecord(
                phoneNumber = meta.number,
                contactName = contactName,
                direction = meta.direction,
                startTime = startTime,
                endTime = startTime + duration,
                durationMillis = duration,
                recordingPath = dest.absolutePath,
                recordingStatus = RecordingStatus.COMPLETED,
                recordingMethod = "IMPORTED",
                importSourceName = name,
                createdAt = now,
                updatedAt = now
            )
        )
    }

    private fun isAudio(name: String, mime: String?): Boolean {
        if (mime != null && mime.startsWith("audio/")) return true
        val ext = name.substringAfterLast('.', "").lowercase()
        return ext in AUDIO_EXTENSIONS
    }

    private fun readDurationMillis(file: File): Long {
        val mmr = MediaMetadataRetriever()
        return try {
            mmr.setDataSource(file.absolutePath)
            mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } catch (t: Throwable) {
            0L
        } finally {
            runCatching { mmr.release() }
        }
    }

    private companion object {
        const val TAG = "RecordingImporter"
        val AUDIO_EXTENSIONS = setOf("mp3", "m4a", "aac", "amr", "wav", "3gp", "ogg", "opus", "awb")
    }
}
