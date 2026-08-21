package com.personal.callrecorder.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.personal.callrecorder.data.dao.CallDao
import com.personal.callrecorder.data.dao.MissedCallDao
import com.personal.callrecorder.data.entity.CallRecord
import com.personal.callrecorder.data.entity.MissedCallRecord

@Database(
    // v2 -> v3: added missed_calls (calls that rang and were never answered —
    // previously dropped entirely, never reaching call_records at all).
    // fallbackToDestructiveMigration in DatabaseModule means this is a clean
    // recreate, not a real migration — fine pre-release, revisit once this
    // app holds data worth preserving across an upgrade.
    entities = [CallRecord::class, MissedCallRecord::class],
    version = 3,
    // Schema export disabled to avoid requiring a room.schemaLocation ksp arg.
    // Enable + configure the arg if you start writing migrations.
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class CallDatabase : RoomDatabase() {
    abstract fun callDao(): CallDao
    abstract fun missedCallDao(): MissedCallDao

    companion object {
        const val NAME = "call_recorder.db"
    }
}
