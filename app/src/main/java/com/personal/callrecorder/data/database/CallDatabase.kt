package com.personal.callrecorder.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.personal.callrecorder.data.dao.CallDao
import com.personal.callrecorder.data.entity.CallRecord

@Database(
    entities = [CallRecord::class],
    version = 2,
    // Schema export disabled to avoid requiring a room.schemaLocation ksp arg.
    // Enable + configure the arg if you start writing migrations.
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class CallDatabase : RoomDatabase() {
    abstract fun callDao(): CallDao

    companion object {
        const val NAME = "call_recorder.db"
    }
}
