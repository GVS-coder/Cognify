package com.cognify.ai

import com.cognify.data.repository.KnowledgeRepository
import com.cognify.data.repository.ReminderRepository
import com.cognify.data.repository.TaskRepository
import com.cognify.ui.ai.AiMode
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContextProvider @Inject constructor(
    private val taskRepository: TaskRepository,
    private val knowledgeRepository: KnowledgeRepository,
    private val reminderRepository: ReminderRepository
) {

    suspend fun getRelevantContext(mode: AiMode): String {
        val contextBuilder = java.lang.StringBuilder()

        when (mode) {
            AiMode.TASK -> {
                appendTasks(contextBuilder, limit = 10)
                appendReminders(contextBuilder)
            }
            AiMode.KNOWLEDGE -> {
                appendKnowledge(contextBuilder, limit = 5)
            }
            AiMode.STUDY -> {
                appendKnowledge(contextBuilder, limit = 5)
                appendTasks(contextBuilder, limit = 5)
            }
            AiMode.GENERAL -> {
                appendTasks(contextBuilder, limit = 5)
                appendReminders(contextBuilder)
                appendKnowledge(contextBuilder, limit = 3)
            }
        }

        return contextBuilder.toString()
    }

    private suspend fun appendTasks(builder: java.lang.StringBuilder, limit: Int) {
        val tasks = taskRepository.getPendingTasks().firstOrNull()
            ?.filter { !it.isPrivate }
            ?.sortedByDescending { it.updatedAt }
            ?.take(limit)

        if (!tasks.isNullOrEmpty()) {
            builder.append("### Pending Tasks:\n")
            tasks.forEach { task ->
                builder.append("- ${task.title} (ID: ${task.id})")
                if (task.description.isNotBlank()) builder.append(": ${task.description}")
                builder.append(" (Priority: ${task.priority}, Category: ${task.category})\n")
            }
            builder.append("\n")
        }
    }

    private suspend fun appendKnowledge(builder: java.lang.StringBuilder, limit: Int) {
        val knowledgeList = knowledgeRepository.getAllKnowledge().firstOrNull()
            ?.filter { !it.isPrivate }
            ?.sortedByDescending { it.updatedAt }
            ?.take(limit)

        if (!knowledgeList.isNullOrEmpty()) {
            builder.append("### Code Base & Knowledge Vault Entries:\n")
            knowledgeList.forEach { knowledge ->
                builder.append("- **${knowledge.title}** (ID: ${knowledge.id}, Type: ${knowledge.type.name}, Language: ${knowledge.language.displayName})\n")
                if (knowledge.description.isNotBlank()) {
                    builder.append("  Description: ${knowledge.description}\n")
                }
                if (!knowledge.codeBlock.isNullOrBlank()) {
                    builder.append("  Code:\n  ```${knowledge.language.name.lowercase()}\n  ${knowledge.codeBlock}\n  ```\n")
                }
            }
            builder.append("\n")
        }
    }

    private suspend fun appendReminders(builder: java.lang.StringBuilder) {
        val startOfDay = getStartOfDay()
        val endOfDay = getEndOfDay()
        val reminders = reminderRepository.getRemindersForDate(startOfDay, endOfDay).firstOrNull()
        if (!reminders.isNullOrEmpty()) {
            builder.append("### Today's Reminders and Alarms:\n")
            reminders.forEach { reminder ->
                builder.append("- ${reminder.title} (ID: ${reminder.id}, Type: ${reminder.type.name}, Status: ${reminder.status})\n")
            }
            builder.append("\n")
        }
    }

    private fun getStartOfDay(): Long {
        val calendar = java.util.Calendar.getInstance()
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
        calendar.set(java.util.Calendar.MINUTE, 0)
        calendar.set(java.util.Calendar.SECOND, 0)
        calendar.set(java.util.Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    private fun getEndOfDay(): Long {
        val calendar = java.util.Calendar.getInstance()
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 23)
        calendar.set(java.util.Calendar.MINUTE, 59)
        calendar.set(java.util.Calendar.SECOND, 59)
        calendar.set(java.util.Calendar.MILLISECOND, 999)
        return calendar.timeInMillis
    }
}
