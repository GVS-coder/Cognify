package com.cognify.ui.reminder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cognify.data.local.AnalyticsEventType
import com.cognify.data.local.ReminderEntity
import com.cognify.data.repository.AnalyticsLogger
import com.cognify.data.model.ReminderStatus
import com.cognify.data.model.ReminderType
import com.cognify.data.repository.ReminderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class ReminderUiState {
    object Loading : ReminderUiState()
    object Empty : ReminderUiState()
    data class Success(val items: List<ReminderEntity>) : ReminderUiState()
    data class Error(val message: String) : ReminderUiState()
}

enum class ReminderFilter {
    ALL, ACTIVE, COMPLETED, BIRTHDAYS, TASKS
}

@HiltViewModel
class ReminderViewModel @Inject constructor(
    private val repository: ReminderRepository,
    private val analyticsLogger: AnalyticsLogger
) : ViewModel() {

    private val _filter = MutableStateFlow(ReminderFilter.ALL)
    val filter: StateFlow<ReminderFilter> = _filter

    val uiState: StateFlow<ReminderUiState> = combine(
        repository.getAllReminders(),
        _filter
    ) { items, currentFilter -> Pair(items, currentFilter) }
        .map { (items, currentFilter) ->
            val filtered = when (currentFilter) {
                ReminderFilter.ALL -> items
                ReminderFilter.ACTIVE -> items.filter { it.status == ReminderStatus.SCHEDULED || it.status == ReminderStatus.PENDING }
                ReminderFilter.COMPLETED -> items.filter { it.status == ReminderStatus.COMPLETED }
                ReminderFilter.BIRTHDAYS -> items.filter { it.type == ReminderType.BIRTHDAY }
                ReminderFilter.TASKS -> items.filter { it.type == ReminderType.TASK_DEADLINE }
            }
            if (filtered.isEmpty()) ReminderUiState.Empty
            else ReminderUiState.Success(filtered)
        }
        .catch { e -> emit(ReminderUiState.Error(e.message ?: "Unknown error occurred")) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ReminderUiState.Loading)

    fun setFilter(filter: ReminderFilter) {
        _filter.value = filter
    }

    fun updateReminderStatus(item: ReminderEntity, isCompleted: Boolean) {
        viewModelScope.launch {
            val nextStatus = if (isCompleted) ReminderStatus.COMPLETED else ReminderStatus.SCHEDULED
            repository.updateReminderStatus(item.id, nextStatus)
            if (isCompleted) {
                analyticsLogger.logEvent(AnalyticsEventType.REMINDER_COMPLETED, referenceId = item.id)
            }
        }
    }

    fun deleteReminder(id: String) {
        viewModelScope.launch {
            repository.deleteReminder(id)
        }
    }

    fun syncFromCloud() {
        viewModelScope.launch {
            repository.syncFromFirestore()
        }
    }
}
