package com.cognify.ui.task

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cognify.data.local.TaskEntity
import com.cognify.data.model.SyncStatus
import com.cognify.data.model.TaskPriority
import com.cognify.data.model.TaskStatus
import com.cognify.data.repository.TaskRepository
import com.cognify.data.repository.AnalyticsLogger
import com.cognify.data.local.AnalyticsEventType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

sealed class AddEditTaskEvent {
    object SaveSuccess : AddEditTaskEvent()
    object DeleteSuccess : AddEditTaskEvent()
    data class Error(val message: String) : AddEditTaskEvent()
}

@HiltViewModel
class AddEditTaskViewModel @Inject constructor(
    private val taskRepository: TaskRepository,
    private val analyticsLogger: AnalyticsLogger
) : ViewModel() {

    private val _task = MutableStateFlow<TaskEntity?>(null)
    val task: StateFlow<TaskEntity?> = _task.asStateFlow()

    private val _events = MutableSharedFlow<AddEditTaskEvent>()
    val events: SharedFlow<AddEditTaskEvent> = _events

    fun loadTask(taskId: String?) {
        if (taskId == null) return
        viewModelScope.launch {
            _task.value = taskRepository.getTaskById(taskId)
        }
    }

    fun saveTask(
        id: String?,
        title: String,
        description: String,
        priority: TaskPriority,
        status: TaskStatus,
        category: String,
        dueDate: Long?
    ) {
        if (title.isBlank()) {
            viewModelScope.launch { _events.emit(AddEditTaskEvent.Error("Title cannot be empty")) }
            return
        }
        
        viewModelScope.launch {
            try {
                val timestamp = System.currentTimeMillis()
                if (id == null) {
                    val newTask = TaskEntity(
                        id = UUID.randomUUID().toString(),
                        title = title,
                        description = description,
                        priority = priority,
                        status = status,
                        category = category,
                        dueDate = dueDate,
                        createdAt = timestamp,
                        updatedAt = timestamp,
                        syncStatus = SyncStatus.PENDING,
                        lastSyncedAt = null,
                        isPrivate = false
                    )
                    taskRepository.addTask(newTask)
                    analyticsLogger.logEvent(AnalyticsEventType.TASK_CREATED, referenceId = newTask.id)
                } else {
                    val existingTask = _task.value
                    if (existingTask != null) {
                        val updatedTask = existingTask.copy(
                            title = title,
                            description = description,
                            priority = priority,
                            status = status,
                            category = category,
                            dueDate = dueDate,
                            updatedAt = timestamp,
                            syncStatus = SyncStatus.PENDING,
                            isPrivate = false
                        )
                        taskRepository.updateTask(updatedTask)
                    }
                }
                _events.emit(AddEditTaskEvent.SaveSuccess)
            } catch (e: Exception) {
                _events.emit(AddEditTaskEvent.Error(e.message ?: "Failed to save task"))
            }
        }
    }

    fun deleteTask(taskId: String) {
        viewModelScope.launch {
            try {
                taskRepository.deleteTask(taskId)
                _events.emit(AddEditTaskEvent.DeleteSuccess)
            } catch (e: Exception) {
                _events.emit(AddEditTaskEvent.Error(e.message ?: "Failed to delete task"))
            }
        }
    }
}
