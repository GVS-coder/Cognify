package com.cognify.data.local

import androidx.room.TypeConverter
import com.cognify.data.model.Difficulty
import com.cognify.data.model.KnowledgeType
import com.cognify.data.model.ProgrammingLanguage
import com.cognify.data.model.SyncStatus
import com.cognify.data.model.TaskPriority
import com.cognify.data.model.TaskStatus
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class Converters {

    private val gson = Gson()

    // ── Analytics Enums ────────────────────────────────────────────────────────
    @TypeConverter fun fromAnalyticsEventType(t: AnalyticsEventType): String = t.name
    @TypeConverter fun toAnalyticsEventType(s: String): AnalyticsEventType =
        try { AnalyticsEventType.valueOf(s) } catch (e: Exception) { AnalyticsEventType.AI_CHAT_MESSAGE }

    // ── Task Enums ─────────────────────────────────────────────────────────────
    @TypeConverter fun fromTaskPriority(p: TaskPriority): String = p.name
    @TypeConverter fun toTaskPriority(s: String): TaskPriority =
        try { TaskPriority.valueOf(s) } catch (e: Exception) { TaskPriority.LOW }

    @TypeConverter fun fromTaskStatus(s: TaskStatus): String = s.name
    @TypeConverter fun toTaskStatus(s: String): TaskStatus =
        try { TaskStatus.valueOf(s) } catch (e: Exception) { TaskStatus.PENDING }

    @TypeConverter fun fromSyncStatus(s: SyncStatus): String = s.name
    @TypeConverter fun toSyncStatus(s: String): SyncStatus =
        try { SyncStatus.valueOf(s) } catch (e: Exception) { SyncStatus.PENDING }

    // ── Knowledge Enums ────────────────────────────────────────────────────────
    @TypeConverter fun fromKnowledgeType(t: KnowledgeType): String = t.name
    @TypeConverter fun toKnowledgeType(s: String): KnowledgeType =
        try { KnowledgeType.valueOf(s) } catch (e: Exception) { KnowledgeType.NOTE }

    @TypeConverter fun fromProgrammingLanguage(l: ProgrammingLanguage): String = l.name
    @TypeConverter fun toProgrammingLanguage(s: String): ProgrammingLanguage =
        try { ProgrammingLanguage.valueOf(s) } catch (e: Exception) { ProgrammingLanguage.NONE }

    @TypeConverter fun fromDifficulty(d: Difficulty): String = d.name
    @TypeConverter fun toDifficulty(s: String): Difficulty =
        try { Difficulty.valueOf(s) } catch (e: Exception) { Difficulty.NONE }

    // ── Reminder Enums ─────────────────────────────────────────────────────────
    @TypeConverter fun fromReminderType(t: com.cognify.data.model.ReminderType): String = t.name
    @TypeConverter fun toReminderType(s: String): com.cognify.data.model.ReminderType =
        try { com.cognify.data.model.ReminderType.valueOf(s) } catch (e: Exception) { com.cognify.data.model.ReminderType.GENERAL }

    @TypeConverter fun fromRepeatInterval(r: com.cognify.data.model.RepeatInterval): String = r.name
    @TypeConverter fun toRepeatInterval(s: String): com.cognify.data.model.RepeatInterval =
        try { com.cognify.data.model.RepeatInterval.valueOf(s) } catch (e: Exception) { com.cognify.data.model.RepeatInterval.NONE }

    @TypeConverter fun fromReminderStatus(st: com.cognify.data.model.ReminderStatus): String = st.name
    @TypeConverter fun toReminderStatus(s: String): com.cognify.data.model.ReminderStatus =
        try { com.cognify.data.model.ReminderStatus.valueOf(s) } catch (e: Exception) { com.cognify.data.model.ReminderStatus.SCHEDULED }

    @TypeConverter fun fromReminderPriority(pr: com.cognify.data.model.ReminderPriority): String = pr.name
    @TypeConverter fun toReminderPriority(s: String): com.cognify.data.model.ReminderPriority =
        try { com.cognify.data.model.ReminderPriority.valueOf(s) } catch (e: Exception) { com.cognify.data.model.ReminderPriority.MEDIUM }

    @TypeConverter fun fromLinkedItemType(l: com.cognify.data.model.LinkedItemType): String = l.name
    @TypeConverter fun toLinkedItemType(s: String): com.cognify.data.model.LinkedItemType =
        try { com.cognify.data.model.LinkedItemType.valueOf(s) } catch (e: Exception) { com.cognify.data.model.LinkedItemType.NONE }

    // ── Collections ────────────────────────────────────────────────────────────
    @TypeConverter
    fun fromStringList(list: List<String>): String = gson.toJson(list)

    @TypeConverter
    fun toStringList(json: String): List<String> {
        val type = object : TypeToken<List<String>>() {}.type
        return try { gson.fromJson(json, type) ?: emptyList() } catch (e: Exception) { emptyList() }
    }
}
