package com.personal.callrecorder.data.repository

import com.personal.callrecorder.data.dao.CallDao
import com.personal.callrecorder.data.entity.CallRecord
import com.personal.callrecorder.data.entity.ProcessingStatus
import com.personal.callrecorder.data.entity.RecordingStatus
import com.personal.callrecorder.util.TimeProvider
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for call records. Deliberately thin and vendor-neutral
 * so a future CMS sync layer can observe/replay the same operations.
 */
@Singleton
class CallRepository @Inject constructor(
    private val dao: CallDao,
    private val time: TimeProvider
) {
    fun observeAll(): Flow<List<CallRecord>> = dao.observeAll()

    fun observeById(id: Long): Flow<CallRecord?> = dao.observeById(id)

    suspend fun getById(id: Long): CallRecord? = dao.getById(id)

    fun search(query: String): Flow<List<CallRecord>> = dao.search(query.trim())

    /** Create a record at the moment recording/monitoring begins. */
    suspend fun createInProgress(
        phoneNumber: String?,
        contactName: String?,
        direction: com.personal.callrecorder.call.CallDirection,
        startTime: Long,
        status: RecordingStatus,
        method: String?
    ): Long {
        val now = time.now()
        return dao.insert(
            CallRecord(
                phoneNumber = phoneNumber,
                contactName = contactName,
                direction = direction,
                startTime = startTime,
                recordingStatus = status,
                recordingMethod = method,
                createdAt = now,
                updatedAt = now
            )
        )
    }

    suspend fun update(record: CallRecord) =
        dao.update(record.copy(updatedAt = time.now()))

    /** Whether an OEM recording with this source filename was already imported. */
    suspend fun existsImport(sourceName: String): Boolean =
        dao.countByImportSource(sourceName) > 0

    suspend fun insertImported(record: CallRecord): Long = dao.insert(record)

    suspend fun finalizeRecording(
        id: Long,
        endTime: Long,
        durationMillis: Long,
        recordingPath: String?,
        status: RecordingStatus,
        error: String? = null
    ) {
        val existing = dao.getById(id) ?: return
        dao.update(
            existing.copy(
                endTime = endTime,
                durationMillis = durationMillis,
                recordingPath = recordingPath,
                recordingStatus = status,
                recordingError = error,
                updatedAt = time.now()
            )
        )
    }

    suspend fun updateContact(id: Long, phoneNumber: String?, contactName: String?) {
        val existing = dao.getById(id) ?: return
        dao.update(
            existing.copy(
                phoneNumber = phoneNumber ?: existing.phoneNumber,
                contactName = contactName ?: existing.contactName,
                updatedAt = time.now()
            )
        )
    }

    suspend fun updateNotes(id: Long, notes: String?) =
        dao.updateNotes(id, notes, time.now())

    suspend fun updateTranscription(id: Long, text: String?, status: ProcessingStatus) =
        dao.updateTranscription(id, text, status, time.now())

    suspend fun updateSummary(id: Long, json: String?, status: ProcessingStatus) =
        dao.updateSummary(id, json, status, time.now())

    suspend fun delete(id: Long) = dao.deleteById(id)

    suspend fun getOlderThan(cutoff: Long): List<CallRecord> = dao.getOlderThan(cutoff)
}
