package com.personal.callrecorder.di

import android.content.Context
import androidx.room.Room
import com.personal.callrecorder.data.database.CallDatabase
import com.personal.callrecorder.data.dao.CallDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): CallDatabase =
        Room.databaseBuilder(context, CallDatabase::class.java, CallDatabase.NAME)
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideCallDao(database: CallDatabase): CallDao = database.callDao()
}
