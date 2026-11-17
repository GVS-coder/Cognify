package com.cognify.di

import android.content.Context
import androidx.room.Room
import com.cognify.data.local.AppDatabase
import com.cognify.data.local.KnowledgeDao
import com.cognify.data.local.TaskDao
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
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return AppDatabase.getDatabase(context)
    }

    @Provides
    fun provideTaskDao(appDatabase: AppDatabase): TaskDao {
        return appDatabase.taskDao()
    }

    @Provides
    fun provideKnowledgeDao(appDatabase: AppDatabase): KnowledgeDao {
        return appDatabase.knowledgeDao()
    }

    @Provides
    fun provideReminderDao(appDatabase: AppDatabase): com.cognify.data.local.ReminderDao {
        return appDatabase.reminderDao()
    }

    @Provides
    fun provideAnalyticsEventDao(appDatabase: AppDatabase): com.cognify.data.local.AnalyticsEventDao {
        return appDatabase.analyticsEventDao()
    }

    @Provides
    fun provideConversationDao(appDatabase: AppDatabase): com.cognify.data.local.ConversationDao {
        return appDatabase.conversationDao()
    }

    @Provides
    fun provideCalculatorHistoryDao(appDatabase: AppDatabase): com.cognify.data.local.CalculatorHistoryDao {
        return appDatabase.calculatorHistoryDao()
    }

    @Provides
    fun provideAiUsageDao(appDatabase: AppDatabase): com.cognify.data.local.AiUsageDao {
        return appDatabase.aiUsageDao()
    }
}
