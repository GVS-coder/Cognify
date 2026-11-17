package com.cognify.ui.analytics

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.cognify.data.local.AnalyticsEventEntity
import com.cognify.data.local.KnowledgeEntity
import com.cognify.data.local.ReminderEntity
import com.cognify.data.local.TaskEntity
import com.cognify.data.model.SyncStatus
import com.cognify.data.model.TaskPriority
import com.cognify.data.model.TaskStatus
import com.cognify.data.repository.AnalyticsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.util.Calendar

@OptIn(ExperimentalCoroutinesApi::class)
class AnalyticsViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var viewModel: AnalyticsViewModel
    private lateinit var mockRepository: AnalyticsRepository
    
    private val testDispatcher = StandardTestDispatcher()

    private val tasksFlow = MutableStateFlow<List<TaskEntity>>(emptyList())
    private val knowledgeFlow = MutableStateFlow<List<KnowledgeEntity>>(emptyList())
    private val remindersFlow = MutableStateFlow<List<ReminderEntity>>(emptyList())
    private val eventsFlow = MutableStateFlow<List<AnalyticsEventEntity>>(emptyList())

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        mockRepository = mock(AnalyticsRepository::class.java)
        `when`(mockRepository.allTasks).thenReturn(tasksFlow)
        `when`(mockRepository.allKnowledge).thenReturn(knowledgeFlow)
        `when`(mockRepository.allReminders).thenReturn(remindersFlow)
        `when`(mockRepository.allEvents).thenReturn(eventsFlow)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `weeklyTasksData contains daily counts for completed tasks`() = runTest {
        viewModel = AnalyticsViewModel(mockRepository)
        
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 12)
        val todayMidday = cal.timeInMillis
        
        cal.add(Calendar.DAY_OF_YEAR, -1)
        val yesterdayMidday = cal.timeInMillis

        cal.add(Calendar.DAY_OF_YEAR, -10)
        val outsideWeek = cal.timeInMillis

        val task1 = TaskEntity(
            id = "1", title = "T1", description = "", priority = TaskPriority.MEDIUM,
            status = TaskStatus.COMPLETED, category = "", dueDate = null,
            createdAt = todayMidday, updatedAt = todayMidday, syncStatus = SyncStatus.SYNCED, lastSyncedAt = null
        )
        val task2 = TaskEntity(
            id = "2", title = "T2", description = "", priority = TaskPriority.MEDIUM,
            status = TaskStatus.COMPLETED, category = "", dueDate = null,
            createdAt = yesterdayMidday, updatedAt = yesterdayMidday, syncStatus = SyncStatus.SYNCED, lastSyncedAt = null
        )
        val task3 = TaskEntity(
            id = "3", title = "T3", description = "", priority = TaskPriority.MEDIUM,
            status = TaskStatus.PENDING, category = "", dueDate = null,
            createdAt = todayMidday, updatedAt = todayMidday, syncStatus = SyncStatus.SYNCED, lastSyncedAt = null
        )
        val task4 = TaskEntity(
            id = "4", title = "T4", description = "", priority = TaskPriority.MEDIUM,
            status = TaskStatus.COMPLETED, category = "", dueDate = null,
            createdAt = outsideWeek, updatedAt = outsideWeek, syncStatus = SyncStatus.SYNCED, lastSyncedAt = null
        )

        tasksFlow.value = listOf(task1, task2, task3, task4)
        
        val job = launch(testDispatcher) {
            viewModel.uiState.collect {}
        }
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is AnalyticsUiState.Success)
        
        val successState = state as AnalyticsUiState.Success
        val weeklyData = successState.weeklyTasksData
        
        assertEquals(7, weeklyData.size)
        
        val totalCompletedInChart = weeklyData.sumOf { it.second }
        assertTrue(totalCompletedInChart >= 1)
        
        job.cancel()
    }

    @Test
    fun `weeklyTasksData returns empty counts when no completed tasks exist`() = runTest {
        viewModel = AnalyticsViewModel(mockRepository)
        
        tasksFlow.value = emptyList()
        
        val job = launch(testDispatcher) {
            viewModel.uiState.collect {}
        }
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is AnalyticsUiState.Success)
        
        val successState = state as AnalyticsUiState.Success
        val weeklyData = successState.weeklyTasksData
        
        assertEquals(7, weeklyData.size)
        val totalCompletedInChart = weeklyData.sumOf { it.second }
        assertEquals(0, totalCompletedInChart)
        
        job.cancel()
    }
}
