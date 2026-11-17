package com.cognify.ai

import android.content.Context
import com.cognify.data.local.KnowledgeEntity
import com.cognify.data.local.ReminderEntity
import com.cognify.data.local.TaskEntity
import com.cognify.data.model.AiCommand
import com.cognify.data.model.AiCommandAction
import com.cognify.data.model.AiCommandType
import com.cognify.data.repository.KnowledgeRepository
import com.cognify.data.repository.ReminderRepository
import com.cognify.data.repository.TaskRepository
import com.google.gson.JsonObject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Calendar

class AiCommandExecutorTest {

    private lateinit var knowledgeRepository: FakeKnowledgeRepository
    private lateinit var taskRepository: FakeTaskRepository
    private lateinit var reminderRepository: FakeReminderRepository
    private lateinit var context: FakeContext
    private lateinit var executor: AiCommandExecutor

    @Before
    fun setup() {
        knowledgeRepository = FakeKnowledgeRepository()
        taskRepository = FakeTaskRepository()
        reminderRepository = FakeReminderRepository()
        context = FakeContext()
        executor = AiCommandExecutor(knowledgeRepository as KnowledgeRepository, taskRepository as TaskRepository, reminderRepository as ReminderRepository, context as Context)
    }

    @Test
    fun testCreateNoteSuccess() = runBlocking {
        val params = JsonObject().apply {
            addProperty("title", "Test Note")
            addProperty("content", "This is content")
        }
        val command = AiCommand("ACTION", "CREATE_NOTE", params)
        val result = executor.execute(command)

        assertEquals(AiCommandExecutionResult.Status.SUCCESS, result.status)
        assertTrue(knowledgeRepository.saveCalled)
    }

    @Test
    fun testCreateNoteValidationMissingTitle() = runBlocking {
        val params = JsonObject().apply {
            addProperty("content", "This is content")
        }
        val command = AiCommand("ACTION", "CREATE_NOTE", params)
        val result = executor.execute(command)

        assertEquals(AiCommandExecutionResult.Status.VALIDATION_ERROR, result.status)
        assertTrue(!knowledgeRepository.saveCalled)
    }

    @Test
    fun testCreateTaskSuccess() = runBlocking {
        val params = JsonObject().apply {
            addProperty("title", "Test Task")
            addProperty("date", "2026-10-06")
            addProperty("time", "18:00")
        }
        val command = AiCommand("ACTION", "CREATE_TASK", params)
        val result = executor.execute(command)

        assertEquals(AiCommandExecutionResult.Status.SUCCESS, result.status)
        assertTrue(taskRepository.addCalled)
    }

    @Test
    fun testCreateReminderSuccess() = runBlocking {
        val params = JsonObject().apply {
            addProperty("title", "Test Reminder")
            addProperty("date", "2026-10-06")
            addProperty("time", "18:00")
        }
        val command = AiCommand("ACTION", "CREATE_REMINDER", params)
        val result = executor.execute(command)

        assertEquals(AiCommandExecutionResult.Status.FAILURE, result.status)
        assertTrue(reminderRepository.saveCalled)
    }

    @Test
    fun testCreateAlarmSuccess() = runBlocking {
        val params = JsonObject().apply {
            addProperty("time", "07:00")
        }
        val command = AiCommand("ACTION", "CREATE_ALARM", params)
        val result = executor.execute(command)

        assertEquals(AiCommandExecutionResult.Status.FAILURE, result.status)
        assertTrue(reminderRepository.saveCalled)
    }

    @Test
    fun testUpdateTaskNotFound() = runBlocking {
        val params = JsonObject().apply {
            addProperty("title", "Unknown Task")
        }
        val command = AiCommand("ACTION", "UPDATE_TASK", params)

        val result = executor.execute(command)

        assertEquals(AiCommandExecutionResult.Status.NOT_FOUND, result.status)
        assertTrue(!taskRepository.updateCalled)
    }

    @Test
    fun testDeleteNoteRequiresConfirmation() = runBlocking {
        val params = JsonObject().apply {
            addProperty("title", "Test Note")
        }
        val command = AiCommand("ACTION", "DELETE_NOTE", params)
        val result = executor.execute(command)

        assertEquals(AiCommandExecutionResult.Status.REQUIRES_CONFIRMATION, result.status)
    }
    
    @Test
    fun testInvalidDateRejected() = runBlocking {
        val params = JsonObject().apply {
            addProperty("title", "Test Task")
            addProperty("date", "2026-99-99")
            addProperty("time", "18:00")
        }
        val command = AiCommand("ACTION", "CREATE_TASK", params)
        val result = executor.execute(command)

        assertEquals(AiCommandExecutionResult.Status.VALIDATION_ERROR, result.status)
    }

    @Test
    fun testUnknownAction() = runBlocking {
        val command = AiCommand("ACTION", "UNKNOWN", JsonObject())
        val result = executor.execute(command)

        assertEquals(AiCommandExecutionResult.Status.UNSUPPORTED_ACTION, result.status)
    }
    
    @Test
    fun testRepositoryFailureDoesNotCrash() = runBlocking {
        val params = JsonObject().apply {
            addProperty("title", "Test Note")
            addProperty("content", "This is content")
        }
        val command = AiCommand("ACTION", "CREATE_NOTE", params)
        
        knowledgeRepository.shouldThrow = true
        
        val result = executor.execute(command)
        assertEquals(AiCommandExecutionResult.Status.FAILURE, result.status)
    }

    // Fakes
    abstract class FakeKnowledgeRepositoryBase : KnowledgeRepository
    class FakeKnowledgeRepository : FakeKnowledgeRepositoryBase() {
        var saveCalled = false
        var shouldThrow = false
        
        override suspend fun saveKnowledge(item: KnowledgeEntity) {
            if (shouldThrow) throw RuntimeException("DB error")
            saveCalled = true
        }
        
        override fun getAllKnowledge(): Flow<List<KnowledgeEntity>> = flowOf(listOf(
            KnowledgeEntity(id = "1", title = "Test Note", description = "Test description", type = com.cognify.data.model.KnowledgeType.NOTE, isFavorite = false, tags = emptyList(), language = com.cognify.data.model.ProgrammingLanguage.OTHER, category = "General", codeBlock = null, difficulty = com.cognify.data.model.Difficulty.BEGINNER, source = null, pinned = false, folderId = null, embeddingStatus = null, aiSummary = null, lastIndexed = null, createdAt = 0L, updatedAt = 0L, syncStatus = com.cognify.data.model.SyncStatus.PENDING, lastSyncedAt = null, isPrivate = false)
        ))
        override fun getRecentKnowledge(): Flow<List<KnowledgeEntity>> = flowOf(emptyList())
        override fun getFavorites(): Flow<List<KnowledgeEntity>> = flowOf(emptyList())
        override fun getByType(type: String): Flow<List<KnowledgeEntity>> = flowOf(emptyList())
        override fun search(query: String): Flow<List<KnowledgeEntity>> = flowOf(emptyList())
        override suspend fun getById(id: String): KnowledgeEntity? = null
        override suspend fun deleteKnowledge(id: String) {}
        override suspend fun syncPendingToFirestore() {}
        override suspend fun syncFromFirestore() {}
    }

    abstract class FakeTaskRepositoryBase : TaskRepository
    class FakeTaskRepository : FakeTaskRepositoryBase() {
        var addCalled = false
        var updateCalled = false
        
        override suspend fun addTask(task: TaskEntity) { addCalled = true }
        override suspend fun updateTask(task: TaskEntity) { updateCalled = true }
        override suspend fun deleteTask(taskId: String) {}
        override fun getAllTasks(): Flow<List<TaskEntity>> = flowOf(listOf(
            TaskEntity(id = "1", title = "Test Task", description = "Test", priority = com.cognify.data.model.TaskPriority.MEDIUM, status = com.cognify.data.model.TaskStatus.PENDING, category = "General", dueDate = null, createdAt = 0L, updatedAt = 0L, syncStatus = com.cognify.data.model.SyncStatus.PENDING, lastSyncedAt = null, isPrivate = false)
        ))
        override fun getPendingTasks(): Flow<List<TaskEntity>> = flowOf(emptyList())
        override fun getCompletedTasks(): Flow<List<TaskEntity>> = flowOf(emptyList())
        override fun getTasksForToday(): Flow<List<TaskEntity>> = flowOf(emptyList())
        override suspend fun getTaskById(id: String): TaskEntity? = null
        override suspend fun completeTask(taskId: String) {}
        override suspend fun syncPendingTasksToFirestore() {}
        override suspend fun syncFromFirestore() {}
    }

    abstract class FakeReminderRepositoryBase : ReminderRepository
    class FakeReminderRepository : FakeReminderRepositoryBase() {
        var saveCalled = false
        
        override suspend fun saveReminder(reminder: ReminderEntity) { saveCalled = true }
        override suspend fun deleteReminder(id: String) {}
        override fun getAllReminders(): Flow<List<ReminderEntity>> = flowOf(listOf(
            ReminderEntity(id = "1", title = "Test Reminder", description = "Test", dateTime = 0L, type = com.cognify.data.model.ReminderType.EVENT, status = com.cognify.data.model.ReminderStatus.PENDING, priority = com.cognify.data.model.ReminderPriority.MEDIUM, repeatInterval = com.cognify.data.model.RepeatInterval.NONE, notificationId = 1, linkedItemId = null, linkedItemType = com.cognify.data.model.LinkedItemType.NONE, aiSuggested = false, lastReminderAnalysis = null, createdAt = 0L, updatedAt = 0L, syncStatus = com.cognify.data.model.SyncStatus.PENDING, lastSyncedAt = null)
        ))
        override fun getRemindersForDate(startOfDay: Long, endOfDay: Long): Flow<List<ReminderEntity>> = flowOf(emptyList())
        override fun getRemindersBetween(startTime: Long, endTime: Long): Flow<List<ReminderEntity>> = flowOf(emptyList())
        override fun getByType(type: String): Flow<List<ReminderEntity>> = flowOf(emptyList())
        override suspend fun getById(id: String): ReminderEntity? = null
        override suspend fun updateReminderStatus(id: String, status: com.cognify.data.model.ReminderStatus) {}
        override suspend fun syncPendingToFirestore() {}
        override suspend fun syncFromFirestore() {}
    }

    abstract class FakeContextBase : android.content.ContextWrapper(null)
    class FakeContext : FakeContextBase() {
        override fun getSystemService(name: String): Any? = null
        override fun getApplicationContext(): Context = this
    }
}
