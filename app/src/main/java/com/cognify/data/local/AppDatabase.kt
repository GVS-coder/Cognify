package com.cognify.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.withTransaction

@Database(
    entities = [
        TaskEntity::class, 
        KnowledgeEntity::class, 
        ReminderEntity::class,
        AnalyticsEventEntity::class,
        ConversationEntity::class,
        ConversationMessageEntity::class,
        CalculatorHistoryEntity::class,
        AiDailyUsageEntity::class
    ],
    version = 14,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun knowledgeDao(): KnowledgeDao
    abstract fun reminderDao(): ReminderDao
    abstract fun analyticsEventDao(): AnalyticsEventDao
    abstract fun conversationDao(): ConversationDao
    abstract fun calculatorHistoryDao(): CalculatorHistoryDao
    abstract fun aiUsageDao(): AiUsageDao

    suspend fun clearUserData(userId: String) {
        withTransaction {
            taskDao().deleteAllTasks(userId)
            knowledgeDao().deleteAll(userId)
            reminderDao().deleteAllReminders(userId)
            calculatorHistoryDao().clearHistory(userId)
            analyticsEventDao().deleteAllEvents(userId)
            conversationDao().deleteAllMessages(userId)
            conversationDao().deleteAllConversations(userId)
            aiUsageDao().deleteUsageForUser(userId)
        }
    }

    companion object {
        val MIGRATION_5_6 = object : androidx.room.migration.Migration(5, 6) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `conversations` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `conversation_messages` (`id` TEXT NOT NULL, `conversationId` TEXT NOT NULL, `role` TEXT NOT NULL, `content` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`conversationId`) REFERENCES `conversations`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_conversation_messages_conversationId` ON `conversation_messages` (`conversationId`)")
            }
        }
        
        val MIGRATION_6_7 = object : androidx.room.migration.Migration(6, 7) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `conversation_messages` ADD COLUMN `attachmentName` TEXT")
                db.execSQL("ALTER TABLE `conversation_messages` ADD COLUMN `attachmentType` TEXT")
                db.execSQL("ALTER TABLE `conversation_messages` ADD COLUMN `attachmentSize` TEXT")
            }
        }
        
        val MIGRATION_7_8 = object : androidx.room.migration.Migration(7, 8) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `calculator_history` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `expression` TEXT NOT NULL, `result` TEXT NOT NULL, `timestamp` INTEGER NOT NULL)")
            }
        }
        
        val MIGRATION_8_9 = object : androidx.room.migration.Migration(8, 9) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `ai_daily_usage` (`date` TEXT NOT NULL, `aiAssistantRequests` INTEGER NOT NULL, `documentAnalyses` INTEGER NOT NULL, `userId` TEXT, PRIMARY KEY(`date`))")
            }
        }

        val MIGRATION_9_10 = object : androidx.room.migration.Migration(9, 10) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `reminders` ADD COLUMN `isEnabled` INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE `reminders` ADD COLUMN `selectedWeekdays` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `reminders` ADD COLUMN `soundId` TEXT NOT NULL DEFAULT 'alarm_default'")
                db.execSQL("ALTER TABLE `reminders` ADD COLUMN `isVibrationEnabled` INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE `reminders` ADD COLUMN `snoozeDurationMinutes` INTEGER NOT NULL DEFAULT 15")
                db.execSQL("ALTER TABLE `reminders` ADD COLUMN `deleteAfterRinging` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `reminders` ADD COLUMN `skipHolidays` INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_10_11 = object : androidx.room.migration.Migration(10, 11) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `reminders_new` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `description` TEXT NOT NULL, `dateTime` INTEGER NOT NULL, `type` TEXT NOT NULL, `status` TEXT NOT NULL, `priority` TEXT NOT NULL, `repeatInterval` TEXT NOT NULL, `notificationId` INTEGER NOT NULL, `linkedItemId` TEXT, `linkedItemType` TEXT NOT NULL, `aiSuggested` INTEGER, `lastReminderAnalysis` TEXT, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `syncStatus` TEXT NOT NULL, `lastSyncedAt` INTEGER, `isEnabled` INTEGER NOT NULL DEFAULT 1, `selectedWeekdays` TEXT NOT NULL DEFAULT '', `soundId` TEXT NOT NULL DEFAULT 'alarm_default', `isVibrationEnabled` INTEGER NOT NULL DEFAULT 1, `snoozeDurationMinutes` INTEGER NOT NULL DEFAULT 15, `skipHolidays` INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(`id`))")
                db.execSQL("INSERT INTO `reminders_new` (`id`, `title`, `description`, `dateTime`, `type`, `status`, `priority`, `repeatInterval`, `notificationId`, `linkedItemId`, `linkedItemType`, `aiSuggested`, `lastReminderAnalysis`, `createdAt`, `updatedAt`, `syncStatus`, `lastSyncedAt`, `isEnabled`, `selectedWeekdays`, `soundId`, `isVibrationEnabled`, `snoozeDurationMinutes`, `skipHolidays`) SELECT `id`, `title`, `description`, `dateTime`, `type`, `status`, `priority`, `repeatInterval`, `notificationId`, `linkedItemId`, `linkedItemType`, `aiSuggested`, `lastReminderAnalysis`, `createdAt`, `updatedAt`, `syncStatus`, `lastSyncedAt`, `isEnabled`, `selectedWeekdays`, `soundId`, `isVibrationEnabled`, `snoozeDurationMinutes`, `skipHolidays` FROM `reminders`")
                db.execSQL("DROP TABLE `reminders`")
                db.execSQL("ALTER TABLE `reminders_new` RENAME TO `reminders`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_reminders_status` ON `reminders` (`status`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_reminders_dateTime` ON `reminders` (`dateTime`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_reminders_syncStatus` ON `reminders` (`syncStatus`)")
            }
        }

        val MIGRATION_11_12 = object : androidx.room.migration.Migration(11, 12) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `reminders` ADD COLUMN `customSoundUri` TEXT")
            }
        }

        val MIGRATION_12_13 = object : androidx.room.migration.Migration(12, 13) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `ai_daily_usage` ADD COLUMN `emailAnalyses` INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_13_14 = object : androidx.room.migration.Migration(13, 14) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `tasks` ADD COLUMN `userId` TEXT NOT NULL DEFAULT 'legacy_unassigned'")
                db.execSQL("ALTER TABLE `knowledge` ADD COLUMN `userId` TEXT NOT NULL DEFAULT 'legacy_unassigned'")
                db.execSQL("ALTER TABLE `reminders` ADD COLUMN `userId` TEXT NOT NULL DEFAULT 'legacy_unassigned'")
                db.execSQL("ALTER TABLE `analytics_events` ADD COLUMN `userId` TEXT NOT NULL DEFAULT 'legacy_unassigned'")
                db.execSQL("ALTER TABLE `conversations` ADD COLUMN `userId` TEXT NOT NULL DEFAULT 'legacy_unassigned'")
                db.execSQL("ALTER TABLE `conversation_messages` ADD COLUMN `userId` TEXT NOT NULL DEFAULT 'legacy_unassigned'")
                db.execSQL("ALTER TABLE `calculator_history` ADD COLUMN `userId` TEXT NOT NULL DEFAULT 'legacy_unassigned'")
                
                db.execSQL("CREATE TABLE IF NOT EXISTS `ai_daily_usage_new` (`date` TEXT NOT NULL, `userId` TEXT NOT NULL DEFAULT 'legacy_unassigned', `aiAssistantRequests` INTEGER NOT NULL, `documentAnalyses` INTEGER NOT NULL, `emailAnalyses` INTEGER NOT NULL, PRIMARY KEY(`date`, `userId`))")
                db.execSQL("INSERT INTO `ai_daily_usage_new` (`date`, `aiAssistantRequests`, `documentAnalyses`, `emailAnalyses`, `userId`) SELECT `date`, `aiAssistantRequests`, `documentAnalyses`, `emailAnalyses`, COALESCE(`userId`, 'legacy_unassigned') FROM `ai_daily_usage`")
                db.execSQL("DROP TABLE `ai_daily_usage`")
                db.execSQL("ALTER TABLE `ai_daily_usage_new` RENAME TO `ai_daily_usage`")
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: android.content.Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = androidx.room.Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "cognify_database"
                )
                .addMigrations(MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
