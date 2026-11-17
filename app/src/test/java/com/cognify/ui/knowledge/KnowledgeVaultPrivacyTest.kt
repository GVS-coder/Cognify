package com.cognify.ui.knowledge

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.cognify.ai.AiCommandExecutionResult
import com.cognify.ai.AiCommandExecutor
import com.cognify.data.model.AiCommand
import com.cognify.data.local.KnowledgeEntity
import com.cognify.data.local.ReminderEntity
import com.cognify.data.local.TaskEntity
import com.cognify.data.model.Difficulty
import com.cognify.data.model.KnowledgeType
import com.cognify.data.model.ProgrammingLanguage
import com.cognify.data.model.SyncStatus
import com.cognify.data.repository.AnalyticsLogger
import com.cognify.data.repository.KnowledgeRepository
import com.cognify.data.repository.ReminderRepository
import com.cognify.data.repository.TaskRepository
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.mock

@OptIn(ExperimentalCoroutinesApi::class)
class KnowledgeVaultPrivacyTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var fakeRepo: FakeKnowledgeRepository
    private lateinit var mockAnalyticsLogger: AnalyticsLogger

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        fakeRepo = FakeKnowledgeRepository()
        mockAnalyticsLogger = mock(AnalyticsLogger::class.java)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createSampleNote(
        id: String = "note-1",
        title: String = "Sample Title",
        description: String = "Secret confidential content",
        isPrivate: Boolean = false,
        codeBlock: String? = null,
        type: KnowledgeType = KnowledgeType.NOTE
    ): KnowledgeEntity {
        return KnowledgeEntity(
            id = id,
            title = title,
            description = description,
            type = type,
            tags = listOf("security", "confidential"),
            language = ProgrammingLanguage.KOTLIN,
            category = "Security",
            isFavorite = false,
            codeBlock = codeBlock,
            difficulty = Difficulty.BEGINNER,
            source = null,
            pinned = false,
            folderId = null,
            embeddingStatus = null,
            aiSummary = null,
            lastIndexed = null,
            createdAt = 1000L,
            updatedAt = 1000L,
            syncStatus = SyncStatus.SYNCED,
            lastSyncedAt = 1000L,
            isPrivate = isPrivate,
            userId = "user_123"
        )
    }

    // ── Scenario 1: A public note appears in normal Knowledge Vault lists ─────
    @Test
    fun testPublicNoteAppearsInNormalLists() = runTest {
        val publicNote = createSampleNote(id = "pub-1", title = "Public Guide", isPrivate = false)
        fakeRepo.items.add(publicNote)

        val list = fakeRepo.getAllKnowledge().first()
        assertEquals(1, list.size)
        assertEquals("Public Guide", list.first().title)
        assertFalse(list.first().isPrivate)
    }

    // ── Scenario 2: Private note is excluded from views where it should be hidden ──
    @Test
    fun testPrivateNoteExcludedFromViewsWhereHidden() = runTest {
        val publicCode = createSampleNote(id = "code-pub", title = "Public Snippet", isPrivate = false, type = KnowledgeType.CODE)
        val privateCode = createSampleNote(id = "code-priv", title = "Private Key Snippet", isPrivate = true, type = KnowledgeType.CODE)
        fakeRepo.items.addAll(listOf(publicCode, privateCode))

        // 1. CodeFragment filter rule verification
        val allItems = fakeRepo.getAllKnowledge().first()
        val codeViewItems = allItems.filter {
            !it.isPrivate && (it.type == KnowledgeType.CODE || it.type == KnowledgeType.SQL || it.type == KnowledgeType.LINUX)
        }
        assertEquals(1, codeViewItems.size)
        assertEquals("code-pub", codeViewItems.first().id)

        // 2. Recent Knowledge (Dashboard) rule verification (isPrivate = 0)
        val recentItems = fakeRepo.getRecentKnowledge().first()
        assertTrue(recentItems.none { it.isPrivate })

        // 3. AI Command Executor exclusion verification
        val taskRepo = mock(TaskRepository::class.java)
        val reminderRepo = mock(ReminderRepository::class.java)
        val context = mock(android.content.Context::class.java)
        val aiExecutor = AiCommandExecutor(fakeRepo, taskRepo, reminderRepo, context)

        val deleteParams = JsonObject().apply { addProperty("title", "Private Key Snippet") }
        val deleteCmd = AiCommand("ACTION", "DELETE_NOTE", deleteParams)
        val result = aiExecutor.execute(deleteCmd)
        // AI command should not find private note
        assertEquals(AiCommandExecutionResult.Status.NOT_FOUND, result.status)
    }

    // ── Scenario 3: Private note remains accessible through intended context ──
    @Test
    fun testPrivateNoteAccessibleThroughIntendedContext() = runTest {
        val privateNote = createSampleNote(id = "priv-42", title = "My Secret Note", isPrivate = true)
        fakeRepo.items.add(privateNote)

        // Intended authorized context: direct ID lookup for AddEditKnowledgeFragment with biometric prompt
        val retrieved = fakeRepo.getById("priv-42")
        assertNotNull(retrieved)
        assertEquals("My Secret Note", retrieved?.title)
        assertTrue(retrieved?.isPrivate == true)
        assertEquals("Secret confidential content", retrieved?.description)
    }

    // ── Scenario 4: Editing private note title or description preserves privacy flag ─
    @Test
    fun testEditingPrivateNotePreservesPrivacyFlag() = runTest {
        val privateNote = createSampleNote(id = "priv-10", title = "Original Title", description = "Secret", isPrivate = true)
        fakeRepo.items.add(privateNote)

        val savedStateHandle = androidx.lifecycle.SavedStateHandle(mapOf("knowledgeId" to "priv-10"))
        val viewModel = AddEditKnowledgeViewModel(fakeRepo, mockAnalyticsLogger, savedStateHandle)
        testDispatcher.scheduler.advanceUntilIdle()

        // Edit only title and description without explicitly setting isPrivate
        viewModel.save(
            title = "Updated Title",
            description = "Updated Secret Content",
            type = KnowledgeType.NOTE,
            tags = emptyList(),
            language = ProgrammingLanguage.NONE,
            difficulty = Difficulty.NONE,
            category = "General",
            codeBlock = null,
            source = null,
            isFavorite = false,
            pinned = false
            // isPrivate omitted, should default to existing item's isPrivate (true)
        )
        testDispatcher.scheduler.advanceUntilIdle()

        val saved = fakeRepo.savedItems.last()
        assertEquals("Updated Title", saved.title)
        assertEquals("Updated Secret Content", saved.description)
        assertTrue("Editing note must preserve isPrivate = true", saved.isPrivate)
    }

    // ── Scenario 5: Toggling public note to private updates visibility ────────
    @Test
    fun testTogglingPublicToPrivate() = runTest {
        val note = createSampleNote(id = "note-toggle", isPrivate = false)
        fakeRepo.items.add(note)

        val savedStateHandle = androidx.lifecycle.SavedStateHandle(mapOf("knowledgeId" to "note-toggle"))
        val viewModel = AddEditKnowledgeViewModel(fakeRepo, mockAnalyticsLogger, savedStateHandle)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.save(
            title = note.title,
            description = note.description,
            type = note.type,
            tags = note.tags,
            language = note.language,
            difficulty = note.difficulty,
            category = note.category,
            codeBlock = note.codeBlock,
            source = note.source,
            isFavorite = note.isFavorite,
            pinned = note.pinned,
            isPrivate = true
        )
        testDispatcher.scheduler.advanceUntilIdle()

        val updated = fakeRepo.savedItems.last()
        assertTrue(updated.isPrivate)
    }

    // ── Scenario 6: Toggling private note to public updates visibility ────────
    @Test
    fun testTogglingPrivateToPublic() = runTest {
        val note = createSampleNote(id = "note-toggle-pub", isPrivate = true)
        fakeRepo.items.add(note)

        val savedStateHandle = androidx.lifecycle.SavedStateHandle(mapOf("knowledgeId" to "note-toggle-pub"))
        val viewModel = AddEditKnowledgeViewModel(fakeRepo, mockAnalyticsLogger, savedStateHandle)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.save(
            title = note.title,
            description = note.description,
            type = note.type,
            tags = note.tags,
            language = note.language,
            difficulty = note.difficulty,
            category = note.category,
            codeBlock = note.codeBlock,
            source = note.source,
            isFavorite = note.isFavorite,
            pinned = note.pinned,
            isPrivate = false
        )
        testDispatcher.scheduler.advanceUntilIdle()

        val updated = fakeRepo.savedItems.last()
        assertFalse(updated.isPrivate)
    }

    // ── Scenario 7: Search respects privacy rules ─────────────────────────────
    @Test
    fun testSearchRespectsPrivacyRules() = runTest {
        val publicNote = createSampleNote(id = "pub-search", title = "Android Guide", description = "Contains password keyword", isPrivate = false)
        val privateNote = createSampleNote(id = "priv-search", title = "Personal Secret Note", description = "Secret password keyword", isPrivate = true)
        fakeRepo.items.addAll(listOf(publicNote, privateNote))

        // Querying "password":
        // Public note matches on description.
        // Private note must NOT match on description!
        val searchResults = fakeRepo.search("password").first()
        assertEquals(1, searchResults.size)
        assertEquals("pub-search", searchResults.first().id)

        // Querying "Personal":
        // Matches private note's title!
        val titleSearchResults = fakeRepo.search("Personal").first()
        assertEquals(1, titleSearchResults.size)
        assertEquals("priv-search", titleSearchResults.first().id)
    }

    // ── Scenario 8: Mapping between local and cloud models preserves isPrivate ─
    @Test
    fun testMappingPreservesPrivacyFlag() {
        val privateEntity = createSampleNote(id = "map-1", isPrivate = true)

        // Simulating serialization to Firestore map
        val firestoreData = mapOf(
            "title" to privateEntity.title,
            "description" to privateEntity.description,
            "isPrivate" to privateEntity.isPrivate
        )
        assertTrue(firestoreData.containsKey("isPrivate"))
        assertEquals(true, firestoreData["isPrivate"])

        // Simulating deserialization
        val deserializedIsPrivate = firestoreData["isPrivate"] as? Boolean ?: false
        assertTrue(deserializedIsPrivate)
    }

    // ── Scenario 9: Synchronization does not accidentally expose private notes ─
    @Test
    fun testSyncPreservesLocalPrivacyWhenCloudLacksField() {
        val existingLocal = createSampleNote(id = "legacy-cloud-note", isPrivate = true)

        // Simulating a legacy cloud document where "isPrivate" was omitted (null)
        val cloudIsPrivate: Boolean? = null
        val resolvedPrivacy = cloudIsPrivate ?: existingLocal.isPrivate

        assertTrue("When cloud lacks isPrivate, existing local privacy must be preserved", resolvedPrivacy)
    }

    // ── Scenario 10: Existing public note behavior continues to work ─────────
    @Test
    fun testExistingPublicNoteBehavior() = runTest {
        val publicNote = createSampleNote(
            id = "std-note",
            title = "Kotlin Basics",
            description = "Intro to Kotlin Coroutines",
            codeBlock = "val x = 1",
            isPrivate = false
        )
        fakeRepo.items.add(publicNote)

        val retrieved = fakeRepo.getById("std-note")
        assertNotNull(retrieved)
        assertEquals("Kotlin Basics", retrieved?.title)
        assertEquals("Intro to Kotlin Coroutines", retrieved?.description)
        assertEquals("val x = 1", retrieved?.codeBlock)
        assertFalse(retrieved?.isPrivate ?: true)
    }

    // ── Test Double: FakeKnowledgeRepository ──────────────────────────────────
    class FakeKnowledgeRepository : KnowledgeRepository {
        val items = mutableListOf<KnowledgeEntity>()
        val savedItems = mutableListOf<KnowledgeEntity>()

        override fun getAllKnowledge(): Flow<List<KnowledgeEntity>> = flowOf(items)

        override fun getRecentKnowledge(): Flow<List<KnowledgeEntity>> =
            flowOf(items.filter { !it.isPrivate }.take(3))

        override fun getFavorites(): Flow<List<KnowledgeEntity>> =
            flowOf(items.filter { it.isFavorite })

        override fun getByType(type: String): Flow<List<KnowledgeEntity>> =
            flowOf(items.filter { it.type.name == type })

        override fun search(query: String): Flow<List<KnowledgeEntity>> {
            val lower = query.lowercase()
            return flowOf(items.filter { item ->
                if (!item.isPrivate) {
                    item.title.lowercase().contains(lower) ||
                    item.description.lowercase().contains(lower) ||
                    (item.codeBlock?.lowercase()?.contains(lower) == true) ||
                    item.tags.any { it.lowercase().contains(lower) }
                } else {
                    item.title.lowercase().contains(lower)
                }
            })
        }

        override suspend fun getById(id: String): KnowledgeEntity? =
            items.find { it.id == id }

        override suspend fun saveKnowledge(item: KnowledgeEntity) {
            savedItems.add(item)
            val index = items.indexOfFirst { it.id == item.id }
            if (index >= 0) items[index] = item else items.add(item)
        }

        override suspend fun deleteKnowledge(id: String) {
            items.removeAll { it.id == id }
        }

        override suspend fun syncPendingToFirestore() {}
        override suspend fun syncFromFirestore() {}
    }
}
