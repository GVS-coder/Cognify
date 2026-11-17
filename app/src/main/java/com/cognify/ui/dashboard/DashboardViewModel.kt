package com.cognify.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cognify.data.local.AnalyticsEventType
import com.cognify.data.local.KnowledgeEntity
import com.cognify.data.local.TaskEntity
import com.cognify.data.model.ReminderStatus
import com.cognify.data.model.ReminderType
import com.cognify.data.model.TaskStatus
import com.cognify.data.repository.AnalyticsLogger
import com.cognify.data.repository.AnalyticsRepository
import com.cognify.data.repository.KnowledgeRepository
import com.cognify.data.repository.ProfileRepository
import com.cognify.data.repository.ReminderRepository
import com.cognify.data.repository.TaskRepository
import com.cognify.data.model.User
import com.cognify.ui.calendar.ScheduleItem
import com.cognify.ui.calendar.ScheduleItemType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

enum class TaskFilter {
    TODAY, PENDING, COMPLETED
}

sealed class DashboardUiState {
    object Loading : DashboardUiState()
    data class Success(val tasks: List<TaskEntity>) : DashboardUiState()
    data class Error(val message: String) : DashboardUiState()
    object Empty : DashboardUiState()
}

data class ProductivityStats(
    val todaysTasks: Int = 0,
    val pendingTasks: Int = 0,
    val completedTasks: Int = 0,
    val knowledgeEntries: Int = 0,
    val notes: Int = 0,
    val codeSnippets: Int = 0
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val taskRepository: TaskRepository,
    private val knowledgeRepository: KnowledgeRepository,
    private val reminderRepository: ReminderRepository,
    private val analyticsRepository: AnalyticsRepository,
    private val profileRepository: ProfileRepository,
    private val dashboardUseCase: DashboardUseCase,
    private val analyticsLogger: AnalyticsLogger
) : ViewModel() {

    private val _uiState = MutableStateFlow<DashboardUiState>(DashboardUiState.Loading)
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private val _currentFilter = MutableStateFlow(TaskFilter.TODAY)
    val currentFilter: StateFlow<TaskFilter> = _currentFilter.asStateFlow()

    val recentKnowledge: StateFlow<List<KnowledgeEntity>> = knowledgeRepository
        .getRecentKnowledge()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val todaySchedule: StateFlow<List<ScheduleItem>> = dashboardUseCase.getTodaySchedule()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val aiSuggestion: StateFlow<String> = dashboardUseCase.getAiSuggestion()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Ask AI to help you plan your day!")

    val taskProgress: StateFlow<Pair<Int, Int>> = taskRepository.getTasksForToday()
        .map { tasks ->
            val completed = tasks.count { it.status == TaskStatus.COMPLETED }
            Pair(completed, tasks.size)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Pair(0, 0))

    private val _lastSyncTime = MutableStateFlow<Long?>(null)
    val lastSyncTime: StateFlow<Long?> = _lastSyncTime.asStateFlow()

    val currentUser: StateFlow<User?> = profileRepository.getCurrentUserFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val productivityStats: StateFlow<ProductivityStats> = combine(
        analyticsRepository.allTasks,
        analyticsRepository.allKnowledge
    ) { tasks, knowledgeList ->
        val todayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val todayEnd = todayStart + 86400000L

        val todaysTasksCount = tasks.count { it.dueDate in todayStart..todayEnd || (it.dueDate == 0L && it.createdAt in todayStart..todayEnd) }
        val pendingCount = tasks.count { it.status != TaskStatus.COMPLETED }
        val completedCount = tasks.count { it.status == TaskStatus.COMPLETED }
        
        val knowledgeCount = knowledgeList.size
        val notesCount = knowledgeList.count { it.type == com.cognify.data.model.KnowledgeType.NOTE || it.type == com.cognify.data.model.KnowledgeType.DOCUMENTATION }
        val snippetsCount = knowledgeList.count { it.type == com.cognify.data.model.KnowledgeType.CODE }

        ProductivityStats(
            todaysTasks = todaysTasksCount,
            pendingTasks = pendingCount,
            completedTasks = completedCount,
            knowledgeEntries = knowledgeCount,
            notes = notesCount,
            codeSnippets = snippetsCount
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ProductivityStats())

    val weeklyChartData: StateFlow<Map<String, Int>> = analyticsRepository.allTasks.map { tasks ->
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        cal.add(Calendar.DAY_OF_YEAR, -6)
        
        val startOfPast7Days = cal.timeInMillis
        val completedLast7Days = tasks.filter { 
            it.status == TaskStatus.COMPLETED && it.updatedAt >= startOfPast7Days
        }
        
        val sdf = java.text.SimpleDateFormat("EEE", java.util.Locale.getDefault())
        val dataMap = mutableMapOf<String, Int>()
        
        for (i in 0..6) {
            val dayCal = Calendar.getInstance()
            dayCal.add(Calendar.DAY_OF_YEAR, - (6 - i))
            val dayName = sdf.format(dayCal.time)
            dataMap[dayName] = 0
        }
        
        completedLast7Days.forEach { task ->
            val dayName = sdf.format(java.util.Date(task.updatedAt))
            if (dataMap.containsKey(dayName)) {
                dataMap[dayName] = (dataMap[dayName] ?: 0) + 1
            }
        }
        
        dataMap
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    init {
        loadTasks()
        syncTasks()
        syncReminders()
    }

    private fun syncTasks() {
        viewModelScope.launch {
            try {
                taskRepository.syncPendingTasksToFirestore()
                taskRepository.syncFromFirestore()
                _lastSyncTime.value = System.currentTimeMillis()
            } catch (e: Exception) {
                // ignore or log
            }
        }
    }

    private fun syncReminders() {
        viewModelScope.launch {
            try {
                reminderRepository.syncPendingToFirestore()
                reminderRepository.syncFromFirestore()
                _lastSyncTime.value = System.currentTimeMillis()
            } catch (e: Exception) {
                // ignore or log
            }
        }
    }

    fun setFilter(filter: TaskFilter) {
        _currentFilter.value = filter
        loadTasks()
    }

    private var loadTasksJob: kotlinx.coroutines.Job? = null

    private fun loadTasks() {
        loadTasksJob?.cancel()
        loadTasksJob = viewModelScope.launch {
            _uiState.value = DashboardUiState.Loading

            val taskFlow = when (_currentFilter.value) {
                TaskFilter.TODAY -> taskRepository.getTasksForToday()
                TaskFilter.PENDING -> taskRepository.getPendingTasks()
                TaskFilter.COMPLETED -> taskRepository.getCompletedTasks()
            }

            taskFlow.catch { e ->
                _uiState.value = DashboardUiState.Error(e.message ?: "Failed to load tasks")
            }.collect { tasks ->
                if (tasks.isEmpty()) {
                    _uiState.value = DashboardUiState.Empty
                } else {
                    _uiState.value = DashboardUiState.Success(tasks)
                }
            }
        }
    }

    fun toggleTaskCompletion(task: TaskEntity, isComplete: Boolean) {
        viewModelScope.launch {
            if (isComplete) {
                taskRepository.completeTask(task.id)
                analyticsLogger.logEvent(AnalyticsEventType.TASK_COMPLETED, referenceId = task.id)
            } else {
                taskRepository.updateTask(task.copy(status = TaskStatus.PENDING))
            }
        }
    }

    fun deleteTask(task: TaskEntity) {
        viewModelScope.launch {
            taskRepository.deleteTask(task.id)
        }
    }

    fun deleteTaskById(id: String) {
        viewModelScope.launch {
            taskRepository.deleteTask(id)
        }
    }

    fun deleteReminderById(id: String) {
        viewModelScope.launch {
            reminderRepository.deleteReminder(id)
        }
    }
}
