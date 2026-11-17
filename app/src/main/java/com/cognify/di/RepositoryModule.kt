package com.cognify.di

import com.cognify.data.repository.KnowledgeRepository
import com.cognify.data.repository.KnowledgeRepositoryImpl
import com.cognify.data.repository.TaskRepository
import com.cognify.data.repository.TaskRepositoryImpl

import com.cognify.data.repository.SettingsRepository
import com.cognify.data.repository.SettingsRepositoryImpl
import com.cognify.data.repository.ProfileRepository
import com.cognify.data.repository.ProfileRepositoryImpl
import com.cognify.data.repository.BackupRepository
import com.cognify.data.repository.BackupRepositoryImpl
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {


    @Binds
    @Singleton
    abstract fun bindSettingsRepository(
        settingsRepositoryImpl: SettingsRepositoryImpl
    ): SettingsRepository

    @Binds
    @Singleton
    abstract fun bindProfileRepository(
        profileRepositoryImpl: ProfileRepositoryImpl
    ): ProfileRepository

    @Binds
    @Singleton
    abstract fun bindBackupRepository(
        backupRepositoryImpl: BackupRepositoryImpl
    ): BackupRepository

    @Binds
    @Singleton
    abstract fun bindTaskRepository(
        taskRepositoryImpl: TaskRepositoryImpl
    ): TaskRepository

    @Binds
    @Singleton
    abstract fun bindKnowledgeRepository(
        impl: KnowledgeRepositoryImpl
    ): KnowledgeRepository

    @Binds
    @Singleton
    abstract fun bindReminderRepository(
        impl: com.cognify.data.repository.ReminderRepositoryImpl
    ): com.cognify.data.repository.ReminderRepository

    @Binds
    @Singleton
    abstract fun bindAIRepository(
        impl: com.cognify.data.repository.AIRepositoryImpl
    ): com.cognify.data.repository.AIRepository

    @Binds
    @Singleton
    abstract fun bindAnalyticsLogger(
        impl: com.cognify.data.repository.AnalyticsLoggerImpl
    ): com.cognify.data.repository.AnalyticsLogger

    @Binds
    @Singleton
    abstract fun bindAnalyticsRepository(
        impl: com.cognify.data.repository.AnalyticsRepositoryImpl
    ): com.cognify.data.repository.AnalyticsRepository

    @Binds
    @Singleton
    abstract fun bindGmailRepository(
        impl: com.cognify.data.repository.GmailRepositoryImpl
    ): com.cognify.data.repository.GmailRepository

    companion object {
        @Provides
        @Singleton
        fun provideFirebaseFirestore(): FirebaseFirestore {
            return FirebaseFirestore.getInstance()
        }
    }
}
