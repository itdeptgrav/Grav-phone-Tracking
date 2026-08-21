package com.personal.callrecorder.data.repository

import com.personal.callrecorder.data.dao.MissedCallDao
import com.personal.callrecorder.data.entity.MissedCallRecord
import com.personal.callrecorder.util.TimeProvider
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/** Single source of truth for calls that rang and were never answered. */
@Singleton
class MissedCallRepository @Inject constructor(
    private val dao: MissedCallDao,
    private val time: TimeProvider
) {
    fun observeAll(): Flow<List<MissedCallRecord>> = dao.observeAll()

    fun observeCount(): Flow<Int> = dao.observeCount()

    suspend fun record(phoneNumber: String?, contactName: String?, ringStartTime: Long): Long =
        dao.insert(
            MissedCallRecord(
                phoneNumber = phoneNumber,
                contactName = contactName,
                ringStartTime = ringStartTime,
                endTime = time.now(),
                createdAt = time.now()
            )
        )

    suspend fun delete(id: Long) = dao.deleteById(id)

    suspend fun getOlderThan(cutoff: Long): List<MissedCallRecord> = dao.getOlderThan(cutoff)

    suspend fun deleteOlderThan(cutoff: Long) = dao.deleteOlderThan(cutoff)
}
