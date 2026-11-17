package com.cognify.ai

import android.content.Context
import com.cognify.data.local.KnowledgeEntity
import com.cognify.data.local.ReminderEntity
import com.cognify.data.local.TaskEntity
import com.cognify.data.model.AiCommand
import com.cognify.data.model.AiCommandAction
import com.cognify.data.model.Difficulty
import com.cognify.data.model.KnowledgeType
import com.cognify.data.model.LinkedItemType
import com.cognify.data.model.ProgrammingLanguage
import com.cognify.data.model.ReminderPriority
import com.cognify.data.model.ReminderStatus
import com.cognify.data.model.ReminderType
import com.cognify.data.model.RepeatInterval
import com.cognify.data.model.SyncStatus
import com.cognify.data.model.TaskPriority
import com.cognify.data.model.TaskStatus
import com.cognify.data.repository.KnowledgeRepository
import com.cognify.data.repository.ReminderRepository
import com.cognify.data.repository.TaskRepository
import com.cognify.notification.ReminderScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.firstOrNull
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

class AiCommandExecutor @Inject constructor(
    private val knowledgeRepository: KnowledgeRepository,
    private val taskRepository: TaskRepository,
    private val reminderRepository: ReminderRepository,
    @ApplicationContext private val context: Context
) {

    suspend fun execute(command: AiCommand, isConfirmed: Boolean = false): AiCommandExecutionResult {
        return try {
            when (command.action) {
                AiCommandAction.CREATE_NOTE -> createNote(command)
                AiCommandAction.UPDATE_NOTE -> updateNote(command, isConfirmed)
                AiCommandAction.DELETE_NOTE -> deleteNote(command, isConfirmed)
                
                AiCommandAction.CREATE_TASK -> createTask(command)
                AiCommandAction.UPDATE_TASK -> updateTask(command, isConfirmed)
                AiCommandAction.DELETE_TASK -> deleteTask(command, isConfirmed)
                
                AiCommandAction.CREATE_REMINDER -> createReminder(command)
                AiCommandAction.UPDATE_REMINDER -> updateReminder(command, isConfirmed)
                AiCommandAction.DELETE_REMINDER -> deleteReminder(command, isConfirmed)
                
                AiCommandAction.CREATE_ALARM -> createAlarm(command)
                AiCommandAction.UPDATE_ALARM -> updateAlarm(command, isConfirmed)
                AiCommandAction.DELETE_ALARM -> deleteAlarm(command, isConfirmed)
                
                AiCommandAction.BATCH_ACTION -> executeBatch(command)
                
                AiCommandAction.UNKNOWN -> AiCommandExecutionResult(AiCommandExecutionResult.Status.UNSUPPORTED_ACTION, "This action is not supported.")
                null -> AiCommandExecutionResult(AiCommandExecutionResult.Status.UNSUPPORTED_ACTION, "No action provided.")
            }
        } catch (e: Exception) {
            AiCommandExecutionResult(AiCommandExecutionResult.Status.FAILURE, "Failed to execute command: ${e.message}")
        }
    }

    private suspend fun executeBatch(command: AiCommand): AiCommandExecutionResult {
        val commandsArray = command.parameters?.getAsJsonArray("commands") ?: return AiCommandExecutionResult(AiCommandExecutionResult.Status.VALIDATION_ERROR, "No commands found in batch.")
        
        val results = mutableListOf<String>()
        var hasFailures = false
        
        for (i in 0 until commandsArray.size()) {
            val cmdJson = commandsArray.get(i).asJsonObject
            val actionStr = cmdJson.get("action")?.asString
            val parameters = cmdJson.getAsJsonObject("parameters")
            
            val subCommand = AiCommand(
                rawType = "ACTION",
                rawAction = actionStr,
                parameters = parameters
            )
            
            val result = execute(subCommand, isConfirmed = true) // Batch commands auto-confirmed for now
            if (result.status != AiCommandExecutionResult.Status.SUCCESS) {
                hasFailures = true
            }
        }
        
        return if (hasFailures) {
            AiCommandExecutionResult(AiCommandExecutionResult.Status.SUCCESS, "Executed batch with some errors.")
        } else {
            AiCommandExecutionResult(AiCommandExecutionResult.Status.SUCCESS, command.confirmationMessage ?: "Executed all commands successfully.")
        }
    }

    private suspend fun createNote(command: AiCommand): AiCommandExecutionResult {
        val params = command.parameters ?: return AiCommandExecutionResult(AiCommandExecutionResult.Status.VALIDATION_ERROR, "Missing parameters.")
        val title = params.get("title")?.asString
        val content = params.get("content")?.asString

        if (title.isNullOrBlank() || content.isNullOrBlank()) {
            return AiCommandExecutionResult(AiCommandExecutionResult.Status.VALIDATION_ERROR, "I need a title and content before I can create the note.")
        }

        val note = KnowledgeEntity(
            id = UUID.randomUUID().toString(),
            title = title,
            description = content,
            type = KnowledgeType.NOTE,
            tags = emptyList(),
            language = ProgrammingLanguage.OTHER,
            category = "General",
            isFavorite = false,
            codeBlock = null,
            difficulty = Difficulty.BEGINNER,
            source = null,
            pinned = false,
            folderId = null,
            embeddingStatus = null,
            aiSummary = null,
            lastIndexed = null,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            syncStatus = SyncStatus.PENDING,
            lastSyncedAt = null,
            isPrivate = false
        )

        knowledgeRepository.saveKnowledge(note)
        return AiCommandExecutionResult(AiCommandExecutionResult.Status.SUCCESS, "Created the note \"${title}\".")
    }

    private suspend fun updateNote(command: AiCommand, isConfirmed: Boolean): AiCommandExecutionResult {
        val params = command.parameters ?: return AiCommandExecutionResult(AiCommandExecutionResult.Status.VALIDATION_ERROR, "Missing parameters.")
        val targetId = params.get("targetId")?.asString
        val title = params.get("title")?.asString
        if (targetId.isNullOrBlank() && title.isNullOrBlank()) {
            return AiCommandExecutionResult(AiCommandExecutionResult.Status.VALIDATION_ERROR, "I need the name or context to know which note to update.")
        }
        
        val notes = knowledgeRepository.getAllKnowledge().firstOrNull()?.filter { !it.isPrivate } ?: emptyList()
        val matchingNotes = mutableListOf<com.cognify.data.local.KnowledgeEntity>()
        
        if (!targetId.isNullOrBlank()) {
            notes.find { it.id == targetId }?.let { matchingNotes.add(it) }
        }
        if (matchingNotes.isEmpty() && !title.isNullOrBlank()) {
            matchingNotes.addAll(notes.filter { it.title.equals(title, ignoreCase = true) })
        }
        
        if (matchingNotes.isEmpty()) {
            return AiCommandExecutionResult(AiCommandExecutionResult.Status.NOT_FOUND, "Could not find that note.")
        }
        if (matchingNotes.size > 1) {
            return AiCommandExecutionResult(AiCommandExecutionResult.Status.AMBIGUOUS_TARGET, "Found multiple matching notes. Please be more specific.")
        }

        val target = matchingNotes.first()
        val newContent = params.get("content")?.asString ?: target.description
        
        if (!isConfirmed) {
            val changes = mutableListOf<ConfirmationDetails.Update.Change>()
            if (newContent != target.description) {
                changes.add(ConfirmationDetails.Update.Change("Content", target.description, newContent))
            }
            if (changes.isEmpty()) {
                return AiCommandExecutionResult(AiCommandExecutionResult.Status.VALIDATION_ERROR, "No changes detected for the note \"${target.title}\".")
            }
            return AiCommandExecutionResult(
                AiCommandExecutionResult.Status.REQUIRES_CONFIRMATION, 
                "Please confirm the update to the note.",
                ConfirmationDetails.Update("Note", target.title, changes)
            )
        }

        val updatedNote = target.copy(
            description = newContent,
            updatedAt = System.currentTimeMillis(),
            syncStatus = SyncStatus.PENDING
        )
        
        knowledgeRepository.saveKnowledge(updatedNote)
        return AiCommandExecutionResult(AiCommandExecutionResult.Status.SUCCESS, "Updated the note \"${title}\".")
    }

    private suspend fun deleteNote(command: AiCommand, isConfirmed: Boolean): AiCommandExecutionResult {
        val params = command.parameters ?: return AiCommandExecutionResult(AiCommandExecutionResult.Status.VALIDATION_ERROR, "Missing parameters.")
        val targetId = params.get("targetId")?.asString
        val title = params.get("title")?.asString
        if (targetId.isNullOrBlank() && title.isNullOrBlank()) {
            return AiCommandExecutionResult(AiCommandExecutionResult.Status.VALIDATION_ERROR, "I need the name or context to know which note to delete.")
        }
        
        val notes = knowledgeRepository.getAllKnowledge().firstOrNull()?.filter { !it.isPrivate } ?: emptyList()
        val matchingNotes = mutableListOf<com.cognify.data.local.KnowledgeEntity>()
        
        if (!targetId.isNullOrBlank()) {
            notes.find { it.id == targetId }?.let { matchingNotes.add(it) }
        }
        if (matchingNotes.isEmpty() && !title.isNullOrBlank()) {
            matchingNotes.addAll(notes.filter { it.title.equals(title, ignoreCase = true) })
        }
        
        if (matchingNotes.isEmpty()) {
            return AiCommandExecutionResult(AiCommandExecutionResult.Status.NOT_FOUND, "Could not find that note.")
        }
        if (matchingNotes.size > 1) {
            return AiCommandExecutionResult(AiCommandExecutionResult.Status.AMBIGUOUS_TARGET, "Found multiple matching notes. Please be more specific.")
        }

        val target = matchingNotes.first()

        if (!isConfirmed) {
            return AiCommandExecutionResult(
                AiCommandExecutionResult.Status.REQUIRES_CONFIRMATION, 
                "Please confirm deletion of the note.",
                ConfirmationDetails.Delete("Note", target.title, "\"${target.description.take(50)}\"")
            )
        }
        
        knowledgeRepository.deleteKnowledge(target.id)
        return AiCommandExecutionResult(AiCommandExecutionResult.Status.SUCCESS, "Deleted the note \"${target.title}\".")
    }

    private suspend fun createTask(command: AiCommand): AiCommandExecutionResult {
        val params = command.parameters ?: return AiCommandExecutionResult(AiCommandExecutionResult.Status.VALIDATION_ERROR, "Missing parameters.")
        val title = params.get("title")?.asString

        if (title.isNullOrBlank()) {
            return AiCommandExecutionResult(AiCommandExecutionResult.Status.VALIDATION_ERROR, "I need a title to create the task.")
        }

        val dateStr = params.get("date")?.asString
        val timeStr = params.get("time")?.asString
        
        var dueDate: Long? = null
        if (!dateStr.isNullOrBlank() || !timeStr.isNullOrBlank()) {
            dueDate = parseDateTime(dateStr, timeStr)
            if (dueDate == null) {
                return AiCommandExecutionResult(AiCommandExecutionResult.Status.VALIDATION_ERROR, "I couldn't understand the date or time format.")
            }
        }

        val task = TaskEntity(
            id = UUID.randomUUID().toString(),
            title = title,
            description = params.get("description")?.asString ?: "",
            priority = TaskPriority.MEDIUM,
            status = TaskStatus.PENDING,
            category = "General",
            dueDate = dueDate,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            syncStatus = SyncStatus.PENDING,
            lastSyncedAt = null,
            isPrivate = false
        )

        taskRepository.addTask(task)
        return AiCommandExecutionResult(AiCommandExecutionResult.Status.SUCCESS, "Created the task \"${title}\".")
    }

    private suspend fun updateTask(command: AiCommand, isConfirmed: Boolean): AiCommandExecutionResult {
        val params = command.parameters ?: return AiCommandExecutionResult(AiCommandExecutionResult.Status.VALIDATION_ERROR, "Missing parameters.")
        val targetId = params.get("targetId")?.asString
        val title = params.get("title")?.asString
        if (targetId.isNullOrBlank() && title.isNullOrBlank()) {
            return AiCommandExecutionResult(AiCommandExecutionResult.Status.VALIDATION_ERROR, "I need the name or context to know which task to update.")
        }
        
        val tasks = taskRepository.getAllTasks().firstOrNull() ?: emptyList()
        val matchingTasks = mutableListOf<com.cognify.data.local.TaskEntity>()
        
        if (!targetId.isNullOrBlank()) {
            tasks.find { it.id == targetId }?.let { matchingTasks.add(it) }
        }
        if (matchingTasks.isEmpty() && !title.isNullOrBlank()) {
            matchingTasks.addAll(tasks.filter { it.title.equals(title, ignoreCase = true) })
        }
        
        if (matchingTasks.isEmpty()) {
            return AiCommandExecutionResult(AiCommandExecutionResult.Status.NOT_FOUND, "Could not find that task.")
        }
        if (matchingTasks.size > 1) {
            return AiCommandExecutionResult(AiCommandExecutionResult.Status.AMBIGUOUS_TARGET, "Found multiple matching tasks. Please be more specific.")
        }

        val target = matchingTasks.first()
        val newTitle = params.get("newTitle")?.asString ?: target.title
        val newDesc = params.get("description")?.asString ?: target.description
        
        val dateStr = params.get("date")?.asString
        val timeStr = params.get("time")?.asString
        
        var newDueDate = target.dueDate
        if (dateStr != null || timeStr != null) {
            val parsed = parseDateTime(dateStr, timeStr)
            if (parsed == null) {
                return AiCommandExecutionResult(AiCommandExecutionResult.Status.VALIDATION_ERROR, "I couldn't understand the new date or time format.")
            }
            newDueDate = parsed
        }

        if (!isConfirmed) {
            val changes = mutableListOf<ConfirmationDetails.Update.Change>()
            if (newTitle != target.title) changes.add(ConfirmationDetails.Update.Change("Title", target.title, newTitle))
            if (newDesc != target.description) changes.add(ConfirmationDetails.Update.Change("Description", target.description, newDesc))
            if (newDueDate != target.dueDate) {
                val oldTimeFormat = target.dueDate?.let { SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault()).format(it) }
                val newTimeFormat = newDueDate?.let { SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault()).format(it) }
                changes.add(ConfirmationDetails.Update.Change("Due Date", oldTimeFormat, newTimeFormat))
            }
            if (changes.isEmpty()) {
                return AiCommandExecutionResult(AiCommandExecutionResult.Status.VALIDATION_ERROR, "No changes detected for the task \"${target.title}\".")
            }
            return AiCommandExecutionResult(
                AiCommandExecutionResult.Status.REQUIRES_CONFIRMATION, 
                "Please confirm the update to the task.",
                ConfirmationDetails.Update("Task", target.title, changes)
            )
        }

        val updatedTask = target.copy(
            title = newTitle,
            description = newDesc,
            dueDate = newDueDate,
            updatedAt = System.currentTimeMillis(),
            syncStatus = SyncStatus.PENDING
        )
        
        taskRepository.updateTask(updatedTask)
        return AiCommandExecutionResult(AiCommandExecutionResult.Status.SUCCESS, "Updated the task \"${target.title}\".")
    }

    private suspend fun deleteTask(command: AiCommand, isConfirmed: Boolean): AiCommandExecutionResult {
        val params = command.parameters ?: return AiCommandExecutionResult(AiCommandExecutionResult.Status.VALIDATION_ERROR, "Missing parameters.")
        val targetId = params.get("targetId")?.asString
        val title = params.get("title")?.asString
        if (targetId.isNullOrBlank() && title.isNullOrBlank()) {
            return AiCommandExecutionResult(AiCommandExecutionResult.Status.VALIDATION_ERROR, "I need the name or context to know which task to delete.")
        }
        
        val tasks = taskRepository.getAllTasks().firstOrNull() ?: emptyList()
        val matchingTasks = mutableListOf<com.cognify.data.local.TaskEntity>()
        
        if (!targetId.isNullOrBlank()) {
            tasks.find { it.id == targetId }?.let { matchingTasks.add(it) }
        }
        if (matchingTasks.isEmpty() && !title.isNullOrBlank()) {
            matchingTasks.addAll(tasks.filter { it.title.equals(title, ignoreCase = true) })
        }
        
        if (matchingTasks.isEmpty()) {
            return AiCommandExecutionResult(AiCommandExecutionResult.Status.NOT_FOUND, "Could not find that task.")
        }
        if (matchingTasks.size > 1) {
            return AiCommandExecutionResult(AiCommandExecutionResult.Status.AMBIGUOUS_TARGET, "Found multiple matching tasks. Please be more specific.")
        }

        val target = matchingTasks.first()

        if (!isConfirmed) {
            val timeFormat = target.dueDate?.let { SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault()).format(it) }
            return AiCommandExecutionResult(
                AiCommandExecutionResult.Status.REQUIRES_CONFIRMATION, 
                "Please confirm deletion of the task.",
                ConfirmationDetails.Delete("Task", target.title, timeFormat?.let { "Due: $it" })
            )
        }
        
        taskRepository.deleteTask(target.id)
        return AiCommandExecutionResult(AiCommandExecutionResult.Status.SUCCESS, "Deleted the task \"${target.title}\".")
    }

    private suspend fun createReminder(command: AiCommand): AiCommandExecutionResult {
        val params = command.parameters ?: return AiCommandExecutionResult(AiCommandExecutionResult.Status.VALIDATION_ERROR, "Missing parameters.")
        val title = params.get("title")?.asString
        if (title.isNullOrBlank()) {
            return AiCommandExecutionResult(AiCommandExecutionResult.Status.VALIDATION_ERROR, "I need a title to create a reminder.")
        }

        val dateStr = params.get("date")?.asString
        val timeStr = params.get("time")?.asString
        val dateTime = parseDateTime(dateStr, timeStr) ?: return AiCommandExecutionResult(AiCommandExecutionResult.Status.VALIDATION_ERROR, "I need a valid date and time for the reminder.")

        val reminder = ReminderEntity(
            id = UUID.randomUUID().toString(),
            title = title,
            description = params.get("description")?.asString ?: "",
            dateTime = dateTime,
            type = ReminderType.EVENT,
            status = ReminderStatus.PENDING,
            priority = ReminderPriority.MEDIUM,
            repeatInterval = RepeatInterval.NONE,
            notificationId = (System.currentTimeMillis() % Int.MAX_VALUE).toInt(),
            linkedItemId = null,
            linkedItemType = LinkedItemType.NONE,
            aiSuggested = true,
            lastReminderAnalysis = null,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            syncStatus = SyncStatus.PENDING,
            lastSyncedAt = null
        )

        reminderRepository.saveReminder(reminder)
        ReminderScheduler.scheduleReminder(context, reminder)
        
        val timeFormat = dateTime.let { SimpleDateFormat("MMM d, yyyy 'at' h:mm a", Locale.getDefault()).format(it) }
        return AiCommandExecutionResult(AiCommandExecutionResult.Status.SUCCESS, "Reminder set for $timeFormat: \"${title}\".")
    }

    private suspend fun updateReminder(command: AiCommand, isConfirmed: Boolean): AiCommandExecutionResult {
        val params = command.parameters ?: return AiCommandExecutionResult(AiCommandExecutionResult.Status.VALIDATION_ERROR, "Missing parameters.")
        val targetId = params.get("targetId")?.asString
        val title = params.get("title")?.asString
        if (targetId.isNullOrBlank() && title.isNullOrBlank()) {
            return AiCommandExecutionResult(AiCommandExecutionResult.Status.VALIDATION_ERROR, "I need the name or context to know which reminder to update.")
        }
        
        val reminders = reminderRepository.getAllReminders().firstOrNull() ?: emptyList()
        val matchingReminders = mutableListOf<com.cognify.data.local.ReminderEntity>()
        
        if (!targetId.isNullOrBlank()) {
            reminders.find { it.id == targetId && it.type == ReminderType.EVENT }?.let { matchingReminders.add(it) }
        }
        if (matchingReminders.isEmpty() && !title.isNullOrBlank()) {
            matchingReminders.addAll(reminders.filter { it.title.equals(title, ignoreCase = true) && it.type == ReminderType.EVENT })
        }
        
        if (matchingReminders.isEmpty()) {
            return AiCommandExecutionResult(AiCommandExecutionResult.Status.NOT_FOUND, "Could not find that reminder.")
        }
        if (matchingReminders.size > 1) {
            return AiCommandExecutionResult(AiCommandExecutionResult.Status.AMBIGUOUS_TARGET, "Found multiple matching reminders. Please be more specific.")
        }

        val target = matchingReminders.first()
        val dateStr = params.get("date")?.asString
        val timeStr = params.get("time")?.asString
        
        val newTitle = params.get("newTitle")?.asString ?: target.title
        var newDateTime = target.dateTime
        if (dateStr != null || timeStr != null) {
            val parsed = parseDateTime(dateStr, timeStr) ?: return AiCommandExecutionResult(AiCommandExecutionResult.Status.VALIDATION_ERROR, "I couldn't understand the new date or time format.")
            newDateTime = parsed
        }

        if (!isConfirmed) {
            val changes = mutableListOf<ConfirmationDetails.Update.Change>()
            if (newTitle != target.title) changes.add(ConfirmationDetails.Update.Change("Title", target.title, newTitle))
            if (newDateTime != target.dateTime) {
                val oldTimeFormat = target.dateTime.let { SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault()).format(it) }
                val newTimeFormat = newDateTime.let { SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault()).format(it) }
                changes.add(ConfirmationDetails.Update.Change("Date/Time", oldTimeFormat, newTimeFormat))
            }
            if (changes.isEmpty()) {
                return AiCommandExecutionResult(AiCommandExecutionResult.Status.VALIDATION_ERROR, "No changes detected for the reminder \"${target.title}\".")
            }
            return AiCommandExecutionResult(
                AiCommandExecutionResult.Status.REQUIRES_CONFIRMATION, 
                "Please confirm the update to the reminder.",
                ConfirmationDetails.Update("Reminder", target.title, changes)
            )
        }

        val updatedReminder = target.copy(
            title = newTitle,
            dateTime = newDateTime,
            updatedAt = System.currentTimeMillis(),
            syncStatus = SyncStatus.PENDING
        )
        
        reminderRepository.saveReminder(updatedReminder)
        ReminderScheduler.scheduleReminder(context, updatedReminder)
        return AiCommandExecutionResult(AiCommandExecutionResult.Status.SUCCESS, "Updated the reminder \"${target.title}\".")
    }

    private suspend fun deleteReminder(command: AiCommand, isConfirmed: Boolean): AiCommandExecutionResult {
        val params = command.parameters ?: return AiCommandExecutionResult(AiCommandExecutionResult.Status.VALIDATION_ERROR, "Missing parameters.")
        val targetId = params.get("targetId")?.asString
        val title = params.get("title")?.asString
        if (targetId.isNullOrBlank() && title.isNullOrBlank()) {
            return AiCommandExecutionResult(AiCommandExecutionResult.Status.VALIDATION_ERROR, "I need the name or context to know which reminder to delete.")
        }
        
        val reminders = reminderRepository.getAllReminders().firstOrNull() ?: emptyList()
        val matchingReminders = mutableListOf<com.cognify.data.local.ReminderEntity>()
        
        if (!targetId.isNullOrBlank()) {
            reminders.find { it.id == targetId && it.type == ReminderType.EVENT }?.let { matchingReminders.add(it) }
        }
        if (matchingReminders.isEmpty() && !title.isNullOrBlank()) {
            matchingReminders.addAll(reminders.filter { it.title.equals(title, ignoreCase = true) && it.type == ReminderType.EVENT })
        }
        
        if (matchingReminders.isEmpty()) {
            return AiCommandExecutionResult(AiCommandExecutionResult.Status.NOT_FOUND, "Could not find that reminder.")
        }
        if (matchingReminders.size > 1) {
            return AiCommandExecutionResult(AiCommandExecutionResult.Status.AMBIGUOUS_TARGET, "Found multiple matching reminders. Please be more specific.")
        }

        val target = matchingReminders.first()

        if (!isConfirmed) {
            val timeFormat = target.dateTime.let { SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault()).format(it) }
            return AiCommandExecutionResult(
                AiCommandExecutionResult.Status.REQUIRES_CONFIRMATION, 
                "Please confirm deletion of the reminder.",
                ConfirmationDetails.Delete("Reminder", target.title, timeFormat)
            )
        }
        
        reminderRepository.deleteReminder(target.id)
        ReminderScheduler.cancelReminder(context, target.id, target.notificationId)
        return AiCommandExecutionResult(AiCommandExecutionResult.Status.SUCCESS, "Deleted the reminder \"${target.title}\".")
    }

    private suspend fun createAlarm(command: AiCommand): AiCommandExecutionResult {
        val params = command.parameters ?: return AiCommandExecutionResult(AiCommandExecutionResult.Status.VALIDATION_ERROR, "Missing parameters.")
        val timeStr = params.get("time")?.asString
        val dateStr = params.get("date")?.asString
        
        if (timeStr.isNullOrBlank()) {
            return AiCommandExecutionResult(AiCommandExecutionResult.Status.VALIDATION_ERROR, "I need a time for the alarm.")
        }
        
        val dateTime = parseDateTime(dateStr, timeStr) ?: return AiCommandExecutionResult(AiCommandExecutionResult.Status.VALIDATION_ERROR, "I couldn't understand the time for the alarm.")

        val alarm = ReminderEntity(
            id = UUID.randomUUID().toString(),
            title = params.get("title")?.asString ?: "Alarm",
            description = params.get("description")?.asString ?: "",
            dateTime = dateTime,
            type = ReminderType.GENERAL, // App design uses GENERAL for Alarms triggered via ExactAlarmReceiver it seems, but let's check: Wait, "Reminders VS Alarms. Alarm is when ReminderType == GENERAL" per user constraints from phase 1.
            status = ReminderStatus.PENDING,
            priority = ReminderPriority.HIGH,
            repeatInterval = RepeatInterval.NONE,
            notificationId = (System.currentTimeMillis() % Int.MAX_VALUE).toInt(),
            linkedItemId = null,
            linkedItemType = LinkedItemType.NONE,
            aiSuggested = true,
            lastReminderAnalysis = null,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            syncStatus = SyncStatus.PENDING,
            lastSyncedAt = null,
            isEnabled = true,
            selectedWeekdays = "",
            soundId = "alarm_default",
            isVibrationEnabled = true,
            snoozeDurationMinutes = 15,
            skipHolidays = false
        )

        reminderRepository.saveReminder(alarm)
        ReminderScheduler.scheduleReminder(context, alarm)
        val timeFormat = dateTime.let { SimpleDateFormat("MMM d, yyyy 'at' h:mm a", Locale.getDefault()).format(it) }
        return AiCommandExecutionResult(AiCommandExecutionResult.Status.SUCCESS, "Set an alarm for $timeFormat.")
    }

    private suspend fun updateAlarm(command: AiCommand, isConfirmed: Boolean): AiCommandExecutionResult {
        val params = command.parameters ?: return AiCommandExecutionResult(AiCommandExecutionResult.Status.VALIDATION_ERROR, "Missing parameters.")
        val targetId = params.get("targetId")?.asString
        val title = params.get("title")?.asString
        val oldTimeStr = params.get("oldTime")?.asString ?: params.get("time")?.asString
        if (targetId.isNullOrBlank() && title.isNullOrBlank() && oldTimeStr.isNullOrBlank()) {
            return AiCommandExecutionResult(AiCommandExecutionResult.Status.VALIDATION_ERROR, "I need the name, time, or context to know which alarm to update.")
        }
        
        val reminders = reminderRepository.getAllReminders().firstOrNull() ?: emptyList()
        val matchingAlarms = mutableListOf<com.cognify.data.local.ReminderEntity>()
        
        if (!targetId.isNullOrBlank()) {
            reminders.find { it.id == targetId && it.type == ReminderType.GENERAL }?.let { matchingAlarms.add(it) }
        }
        if (matchingAlarms.isEmpty() && !title.isNullOrBlank()) {
            matchingAlarms.addAll(reminders.filter { it.title.equals(title, ignoreCase = true) && it.type == ReminderType.GENERAL })
        }
        
        if (matchingAlarms.isEmpty()) {
            return AiCommandExecutionResult(AiCommandExecutionResult.Status.NOT_FOUND, "Could not find that alarm.")
        }
        if (matchingAlarms.size > 1) {
            return AiCommandExecutionResult(AiCommandExecutionResult.Status.AMBIGUOUS_TARGET, "Found multiple matching alarms. Please be more specific.")
        }
        
        val target = matchingAlarms.first()
        
        val newDateStr = params.get("date")?.asString
        val newTimeStr = params.get("newTime")?.asString ?: params.get("time")?.asString
        
        var newDateTime = target.dateTime
        if (newTimeStr != null) {
            val parsed = parseDateTime(newDateStr, newTimeStr) ?: return AiCommandExecutionResult(AiCommandExecutionResult.Status.VALIDATION_ERROR, "I couldn't understand the new date or time format.")
            newDateTime = parsed
        }

        if (!isConfirmed) {
            val changes = mutableListOf<ConfirmationDetails.Update.Change>()
            if (newDateTime != target.dateTime) {
                val oldTimeFormat = target.dateTime.let { SimpleDateFormat("h:mm a", Locale.getDefault()).format(it) }
                val newTimeFormat = newDateTime.let { SimpleDateFormat("h:mm a", Locale.getDefault()).format(it) }
                changes.add(ConfirmationDetails.Update.Change("Time", oldTimeFormat, newTimeFormat))
            }
            if (changes.isEmpty()) {
                return AiCommandExecutionResult(AiCommandExecutionResult.Status.VALIDATION_ERROR, "No changes detected for the alarm.")
            }
            return AiCommandExecutionResult(
                AiCommandExecutionResult.Status.REQUIRES_CONFIRMATION, 
                "Please confirm the update to the alarm.",
                ConfirmationDetails.Update("Alarm", target.title, changes)
            )
        }

        val updatedAlarm = target.copy(
            dateTime = newDateTime,
            updatedAt = System.currentTimeMillis(),
            syncStatus = SyncStatus.PENDING
        )
        
        reminderRepository.saveReminder(updatedAlarm)
        ReminderScheduler.scheduleReminder(context, updatedAlarm)
        return AiCommandExecutionResult(AiCommandExecutionResult.Status.SUCCESS, "Updated the alarm.")
    }

    private suspend fun deleteAlarm(command: AiCommand, isConfirmed: Boolean): AiCommandExecutionResult {
        val params = command.parameters ?: return AiCommandExecutionResult(AiCommandExecutionResult.Status.VALIDATION_ERROR, "Missing parameters.")
        val targetId = params.get("targetId")?.asString
        val title = params.get("title")?.asString
        val oldTimeStr = params.get("time")?.asString
        if (targetId.isNullOrBlank() && title.isNullOrBlank() && oldTimeStr.isNullOrBlank()) {
            return AiCommandExecutionResult(AiCommandExecutionResult.Status.VALIDATION_ERROR, "I need the name, time, or context to know which alarm to delete.")
        }
        
        val reminders = reminderRepository.getAllReminders().firstOrNull() ?: emptyList()
        val matchingAlarms = mutableListOf<com.cognify.data.local.ReminderEntity>()
        
        if (!targetId.isNullOrBlank()) {
            reminders.find { it.id == targetId && it.type == ReminderType.GENERAL }?.let { matchingAlarms.add(it) }
        }
        if (matchingAlarms.isEmpty() && !title.isNullOrBlank()) {
            matchingAlarms.addAll(reminders.filter { it.title.equals(title, ignoreCase = true) && it.type == ReminderType.GENERAL })
        }
        
        if (matchingAlarms.isEmpty()) {
            return AiCommandExecutionResult(AiCommandExecutionResult.Status.NOT_FOUND, "Could not find that alarm.")
        }
        if (matchingAlarms.size > 1) {
            return AiCommandExecutionResult(AiCommandExecutionResult.Status.AMBIGUOUS_TARGET, "Found multiple matching alarms. Please be more specific.")
        }
        
        val target = matchingAlarms.first()

        if (!isConfirmed) {
            val timeFormat = target.dateTime.let { SimpleDateFormat("h:mm a", Locale.getDefault()).format(it) }
            return AiCommandExecutionResult(
                AiCommandExecutionResult.Status.REQUIRES_CONFIRMATION, 
                "Please confirm deletion of the alarm.",
                ConfirmationDetails.Delete("Alarm", target.title, timeFormat)
            )
        }
        
        reminderRepository.deleteReminder(target.id)
        ReminderScheduler.cancelReminder(context, target.id, target.notificationId)
        return AiCommandExecutionResult(AiCommandExecutionResult.Status.SUCCESS, "Deleted the alarm.")
    }

    private fun parseDateTime(dateStr: String?, timeStr: String?): Long? {
        if (dateStr.isNullOrBlank() && timeStr.isNullOrBlank()) return null
        
        val cal = Calendar.getInstance()
        
        if (!dateStr.isNullOrBlank()) {
            try {
                val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).apply {
                    isLenient = false
                }
                val parsedDate = dateFormat.parse(dateStr)
                if (parsedDate != null) {
                    val dateCal = Calendar.getInstance().apply { time = parsedDate }
                    cal.set(Calendar.YEAR, dateCal.get(Calendar.YEAR))
                    cal.set(Calendar.MONTH, dateCal.get(Calendar.MONTH))
                    cal.set(Calendar.DAY_OF_MONTH, dateCal.get(Calendar.DAY_OF_MONTH))
                }
            } catch (e: ParseException) {
                return null
            }
        }
        
        if (!timeStr.isNullOrBlank()) {
            try {
                val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault()).apply {
                    isLenient = false
                }
                val parsedTime = timeFormat.parse(timeStr)
                if (parsedTime != null) {
                    val timeCal = Calendar.getInstance().apply { time = parsedTime }
                    cal.set(Calendar.HOUR_OF_DAY, timeCal.get(Calendar.HOUR_OF_DAY))
                    cal.set(Calendar.MINUTE, timeCal.get(Calendar.MINUTE))
                    cal.set(Calendar.SECOND, 0)
                    cal.set(Calendar.MILLISECOND, 0)
                }
            } catch (e: ParseException) {
                return null
            }
        }
        
        // If time is in the past and only time was provided, move it to tomorrow
        if (dateStr.isNullOrBlank() && cal.timeInMillis < System.currentTimeMillis()) {
            cal.add(Calendar.DAY_OF_MONTH, 1)
        }
        
        return cal.timeInMillis
    }
}
