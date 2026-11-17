package com.cognify.ui.reminder

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cognify.data.local.ReminderEntity
import com.cognify.data.model.LinkedItemType
import com.cognify.data.model.ReminderPriority
import com.cognify.data.model.ReminderStatus
import com.cognify.data.model.ReminderType
import com.cognify.data.model.RepeatInterval
import com.cognify.data.model.SyncStatus
import com.cognify.data.repository.ReminderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import kotlin.math.abs

sealed class AddEditReminderEvent {
    object SaveSuccess : AddEditReminderEvent()
    data class Error(val message: String) : AddEditReminderEvent()
}

@HiltViewModel
class AddEditReminderViewModel @Inject constructor(
    private val repository: ReminderRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val reminderId: String? = savedStateHandle["reminderId"]

    private val _existingReminder = MutableStateFlow<ReminderEntity?>(null)
    val existingReminder: StateFlow<ReminderEntity?> = _existingReminder

    private val _events = MutableSharedFlow<AddEditReminderEvent>()
    val events: SharedFlow<AddEditReminderEvent> = _events

    init {
        reminderId?.let { id ->
            viewModelScope.launch {
                _existingReminder.value = repository.getById(id)
            }
        }
    }

    fun save(
        title: String,
        description: String,
        dateTime: Long,
        type: ReminderType,
        priority: ReminderPriority,
        repeatInterval: RepeatInterval,
        linkedItemId: String?,
        linkedItemType: LinkedItemType,
        aiSuggested: Boolean? = null,
        lastReminderAnalysis: String? = null
    ) {
        if (title.isBlank()) {
            viewModelScope.launch { _events.emit(AddEditReminderEvent.Error("Title cannot be empty")) }
            return
        }

        if (dateTime <= System.currentTimeMillis()) {
            viewModelScope.launch { _events.emit(AddEditReminderEvent.Error("Reminder time must be in the future")) }
            return
        }

        val now = System.currentTimeMillis()
        val generatedNotifId = _existingReminder.value?.notificationId ?: abs(UUID.randomUUID().hashCode())

        val reminder = ReminderEntity(
            id = reminderId ?: UUID.randomUUID().toString(),
            title = title.trim(),
            description = description.trim(),
            dateTime = dateTime,
            type = type,
            status = if (reminderId != null) (_existingReminder.value?.status ?: ReminderStatus.SCHEDULED) else ReminderStatus.SCHEDULED,
            priority = priority,
            repeatInterval = repeatInterval,
            notificationId = generatedNotifId,
            linkedItemId = linkedItemId?.trim()?.takeIf { it.isNotBlank() },
            linkedItemType = linkedItemType,
            aiSuggested = aiSuggested,
            lastReminderAnalysis = lastReminderAnalysis,
            createdAt = _existingReminder.value?.createdAt ?: now,
            updatedAt = now,
            syncStatus = SyncStatus.PENDING,
            lastSyncedAt = null
        )

        viewModelScope.launch {
            try {
                repository.saveReminder(reminder)
                _events.emit(AddEditReminderEvent.SaveSuccess)
            } catch (e: Exception) {
                _events.emit(AddEditReminderEvent.Error(e.message ?: "Save failed"))
            }
        }
    }

    fun delete() {
        reminderId?.let { id ->
            viewModelScope.launch {
                try {
                    repository.deleteReminder(id)
                    _events.emit(AddEditReminderEvent.SaveSuccess)
                } catch (e: Exception) {
                    _events.emit(AddEditReminderEvent.Error(e.message ?: "Delete failed"))
                }
            }
        }
    }
}
