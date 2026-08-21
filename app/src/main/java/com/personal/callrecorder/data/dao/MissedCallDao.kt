package com.personal.callrecorder.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.personal.callrecorder.data.entity.MissedCallRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface MissedCallDao {

    @Insert
    suspend fun insert(record: MissedCallRecord): Long

    @Query("SELECT * FROM missed_calls ORDER BY ringStartTime DESC")
    fun observeAll(): Flow<List<MissedCallRecord>>

    @Query("SELECT COUNT(*) FROM missed_calls")
    fun observeCount(): Flow<Int>

    @Query("SELECT * FROM missed_calls WHERE id = :id")
    suspend fun getById(id: Long): MissedCallRecord?

    @Query("DELETE FROM missed_calls WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** Used by the same "delete older than N days" maintenance setting call_records already has. */
    @Query("SELECT * FROM missed_calls WHERE ringStartTime < :cutoff")
    suspend fun getOlderThan(cutoff: Long): List<MissedCallRecord>

    @Query("DELETE FROM missed_calls WHERE ringStartTime < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long)
}
