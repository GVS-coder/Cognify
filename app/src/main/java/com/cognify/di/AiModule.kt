package com.cognify.di

import com.cognify.ai.AiDataSource

import com.cognify.ai.CloudflareAiService
import com.cognify.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AiModule {



    @Provides
    @Singleton
    fun provideAiDataSource(
        cloudflareAiService: CloudflareAiService
    ): AiDataSource {
        return cloudflareAiService
    }
}
