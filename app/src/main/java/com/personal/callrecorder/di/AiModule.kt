package com.personal.callrecorder.di

import com.personal.callrecorder.ai.AiSummaryProvider
import com.personal.callrecorder.ai.DisabledAiSummaryProvider
import com.personal.callrecorder.transcription.DisabledTranscriptionProvider
import com.personal.callrecorder.transcription.TranscriptionProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds the transcription and AI providers. To enable these features later,
 * swap the disabled implementations here for real ones (e.g. WhisperApiProvider,
 * BackendAiSummaryProvider) — nothing else in the app needs to change.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AiModule {

    @Binds
    @Singleton
    abstract fun bindTranscriptionProvider(
        impl: DisabledTranscriptionProvider
    ): TranscriptionProvider

    @Binds
    @Singleton
    abstract fun bindAiSummaryProvider(
        impl: DisabledAiSummaryProvider
    ): AiSummaryProvider
}
