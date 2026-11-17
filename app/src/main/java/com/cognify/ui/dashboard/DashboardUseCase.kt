package com.cognify.ui.dashboard

import com.cognify.data.local.TaskEntity
import com.cognify.data.model.ReminderStatus
import com.cognify.data.model.ReminderType
import com.cognify.data.model.TaskStatus
import com.cognify.data.repository.ReminderRepository
import com.cognify.data.repository.TaskRepository
import com.cognify.ui.calendar.ScheduleItem
import com.cognify.ui.calendar.ScheduleItemType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import java.util.Calendar
import javax.inject.Inject

class DashboardUseCase @Inject constructor(
    private val taskRepository: TaskRepository,
    private val reminderRepository: ReminderRepository
) {
    fun getTodaySchedule(): Flow<List<ScheduleItem>> {
        return combine(
            taskRepository.getPendingTasks(),
            taskRepository.getCompletedTasks(),
            reminderRepository.getAllReminders()
        ) { pendingTasks, completedTasks, reminders ->
            val allTasks = pendingTasks + completedTasks
            val dayStart = getStartOfDay()
            val dayEnd = getEndOfDay()

            val list = mutableListOf<ScheduleItem>()
            allTasks.forEach { task ->
                if (task.dueDate != null && task.dueDate in dayStart..dayEnd) {
                    list.add(
                        ScheduleItem(
                            id = task.id,
                            title = task.title,
                            subtitle = com.cognify.CognifyApplication.appContext.getString(com.cognify.R.string.ui_task_category, task.category),
                            timeInMillis = task.dueDate,
                            type = ScheduleItemType.TASK_DEADLINE,
                            isCompleted = task.status == TaskStatus.COMPLETED
                        )
                    )
                }
            }

            reminders.forEach { reminder ->
                if (reminder.dateTime in dayStart..dayEnd) {
                    if (reminder.type == ReminderType.TASK_DEADLINE) return@forEach
                    val schedType = when (reminder.type) {
                        ReminderType.GENERAL -> ScheduleItemType.GENERAL_REMINDER
                        ReminderType.BIRTHDAY -> ScheduleItemType.BIRTHDAY
                        ReminderType.EVENT -> ScheduleItemType.EVENT
                        else -> ScheduleItemType.GENERAL_REMINDER
                    }
                    list.add(
                        ScheduleItem(
                            id = reminder.id,
                            title = reminder.title,
                            subtitle = reminder.description.ifBlank { com.cognify.CognifyApplication.appContext.getString(com.cognify.R.string.ui_reminder_text) },
                            timeInMillis = reminder.dateTime,
                            type = schedType,
                            isCompleted = reminder.status == ReminderStatus.COMPLETED
                        )
                    )
                }
            }
            list.sortBy { it.timeInMillis }
            list
        }.flowOn(Dispatchers.Default)
    }

    fun getAiSuggestion(): Flow<String> {
        return combine(
            taskRepository.getPendingTasks(),
            reminderRepository.getAllReminders()
        ) { tasks, reminders ->
            val overdueTasks = tasks.count { it.dueDate != null && it.dueDate < System.currentTimeMillis() }
            val upcomingTasks = tasks.size
            
            when {
                overdueTasks > 0 -> com.cognify.CognifyApplication.appContext.getString(com.cognify.R.string.dashboard_overdue_tasks, overdueTasks)
                upcomingTasks > 5 -> "You have a lot on your plate ($upcomingTasks tasks). Ask AI to break them down!"
                reminders.isNotEmpty() -> "You have active reminders. Ask AI for a summary of your day."
                else -> "Your schedule is clear! Ask AI to help you plan your next goal or study session."
            }
        }.flowOn(Dispatchers.Default)
    }

    private fun getStartOfDay(): Long {
        return Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private fun getEndOfDay(): Long {
        return Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }.timeInMillis
    }
}
