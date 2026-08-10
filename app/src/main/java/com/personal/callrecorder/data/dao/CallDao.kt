package com.personal.callrecorder.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.personal.callrecorder.data.entity.CallRecord
import com.personal.callrecorder.data.entity.ProcessingStatus
import com.personal.callrecorder.data.entity.RecordingStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface CallDao {

    @Insert
    suspend fun insert(record: CallRecord): Long

    @Update
    suspend fun update(record: CallRecord)

    @Query("SELECT * FROM call_records ORDER BY startTime DESC")
    fun observeAll(): Flow<List<CallRecord>>

    @Query("SELECT * FROM call_records WHERE id = :id")
    fun observeById(id: Long): Flow<CallRecord?>

    @Query("SELECT * FROM call_records WHERE id = :id")
    suspend fun getById(id: Long): CallRecord?

    /**
     * Full-text-style search over the fields a user would reasonably search:
     * contact name, phone number, transcript, summary, notes. LIKE keeps this
     * dependency-free and is more than fast enough for a personal call history.
     */
    @Query(
        """
        SELECT * FROM call_records
        WHERE contactName LIKE '%' || :q || '%'
           OR phoneNumber LIKE '%' || :q || '%'
           OR transcription LIKE '%' || :q || '%'
           OR summary LIKE '%' || :q || '%'
           OR notes LIKE '%' || :q || '%'
        ORDER BY startTime DESC
        """
    )
    fun search(q: String): Flow<List<CallRecord>>

    @Query("UPDATE call_records SET notes = :notes, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateNotes(id: Long, notes: String?, updatedAt: Long)

    @Query(
        "UPDATE call_records SET transcription = :text, transcriptionStatus = :status, " +
            "updatedAt = :updatedAt WHERE id = :id"
    )
    suspend fun updateTranscription(
        id: Long,
        text: String?,
        status: ProcessingStatus,
        updatedAt: Long
    )

    @Query(
        "UPDATE call_records SET summary = :json, summaryStatus = :status, " +
            "updatedAt = :updatedAt WHERE id = :id"
    )
    suspend fun updateSummary(
        id: Long,
        json: String?,
        status: ProcessingStatus,
        updatedAt: Long
    )

    @Query("DELETE FROM call_records WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** Used by the "delete recordings older than N days" maintenance setting. */
    @Query("SELECT * FROM call_records WHERE startTime < :cutoff")
    suspend fun getOlderThan(cutoff: Long): List<CallRecord>

    @Query("SELECT COUNT(*) FROM call_records WHERE recordingStatus = :status")
    fun countByStatus(status: RecordingStatus): Flow<Int>

    /** Used to skip already-imported OEM recordings. */
    @Query("SELECT COUNT(*) FROM call_records WHERE importSourceName = :name")
    suspend fun countByImportSource(name: String): Int
}
