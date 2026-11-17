package com.cognify.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cognify.data.local.ReminderEntity
import com.cognify.data.local.TaskEntity
import com.cognify.data.model.ReminderType
import com.cognify.data.repository.ReminderRepository
import com.cognify.data.repository.TaskRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Date
import javax.inject.Inject

data class ScheduleItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val timeInMillis: Long,
    val type: ScheduleItemType,
    val isCompleted: Boolean
)

enum class ScheduleItemType {
    TASK_DEADLINE,
    GENERAL_REMINDER,
    BIRTHDAY,
    EVENT
}

sealed class CalendarUiState {
    object Loading : CalendarUiState()
    object Empty : CalendarUiState()
    data class Success(val items: List<ScheduleItem>) : CalendarUiState()
    data class Error(val message: String) : CalendarUiState()
}

@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val taskRepository: TaskRepository,
    private val reminderRepository: ReminderRepository
) : ViewModel() {

    private val _selectedDate = MutableStateFlow(System.currentTimeMillis())
    val selectedDate: StateFlow<Long> = _selectedDate

    val uiState: StateFlow<CalendarUiState> = combine(
        taskRepository.getPendingTasks(),
        taskRepository.getCompletedTasks(),
        reminderRepository.getAllReminders(),
        _selectedDate
    ) { pendingTasks, completedTasks, reminders, date ->
        val allTasks = pendingTasks + completedTasks
        val dayStart = getStartOfDay(date)
        val dayEnd = getEndOfDay(date)

        val scheduleItems = mutableListOf<ScheduleItem>()

        // Filter Tasks due today
        allTasks.forEach { task ->
            if (task.dueDate != null && task.dueDate in dayStart..dayEnd) {
                scheduleItems.add(
                    ScheduleItem(
                        id = task.id,
                        title = task.title,
                        subtitle = com.cognify.CognifyApplication.appContext.getString(com.cognify.R.string.ui_task_category, task.category),
                        timeInMillis = task.dueDate,
                        type = ScheduleItemType.TASK_DEADLINE,
                        isCompleted = task.status == com.cognify.data.model.TaskStatus.COMPLETED
                    )
                )
            }
        }

        // Filter Reminders scheduled today
        reminders.forEach { reminder ->
            if (reminder.dateTime in dayStart..dayEnd) {
                if (reminder.type == ReminderType.TASK_DEADLINE) return@forEach
                val schedType = when (reminder.type) {
                    ReminderType.GENERAL -> ScheduleItemType.GENERAL_REMINDER
                    ReminderType.BIRTHDAY -> ScheduleItemType.BIRTHDAY
                    ReminderType.EVENT -> ScheduleItemType.EVENT
                    else -> ScheduleItemType.GENERAL_REMINDER
                }
                scheduleItems.add(
                    ScheduleItem(
                        id = reminder.id,
                        title = reminder.title,
                        subtitle = reminder.description.ifBlank { com.cognify.CognifyApplication.appContext.getString(com.cognify.R.string.ui_reminder_text) },
                        timeInMillis = reminder.dateTime,
                        type = schedType,
                        isCompleted = reminder.status == com.cognify.data.model.ReminderStatus.COMPLETED
                    )
                )
            }
        }

        // Sort items by time
        scheduleItems.sortBy { it.timeInMillis }

        if (scheduleItems.isEmpty()) CalendarUiState.Empty
        else CalendarUiState.Success(scheduleItems)
    }
    .catch { e -> emit(CalendarUiState.Error(e.message ?: "Unknown error occurred")) }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CalendarUiState.Loading)

    fun selectDate(timeInMillis: Long) {
        _selectedDate.value = timeInMillis
    }

    fun deleteScheduleItem(item: ScheduleItem) {
        viewModelScope.launch {
            if (item.type == ScheduleItemType.TASK_DEADLINE) {
                taskRepository.deleteTask(item.id)
            } else {
                reminderRepository.deleteReminder(item.id)
            }
        }
    }

    private fun getStartOfDay(time: Long): Long {
        return Calendar.getInstance().apply {
            timeInMillis = time
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private fun getEndOfDay(time: Long): Long {
        return Calendar.getInstance().apply {
            timeInMillis = time
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }.timeInMillis
    }
}
