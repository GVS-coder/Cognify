package com.cognify.ui.smartinbox

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.cognify.data.model.EmailAnalysisResult
import com.cognify.data.model.Resource
import com.cognify.data.local.AiDailyUsageEntity
import com.cognify.data.repository.AIRepository
import com.cognify.data.repository.AiUsageRepository
import com.cognify.data.repository.GmailRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

@OptIn(ExperimentalCoroutinesApi::class)
class GmailEmailDetailsViewModelTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()
    
    private lateinit var aiRepository: AIRepository
    private lateinit var gmailRepository: GmailRepository
    private lateinit var aiUsageRepository: AiUsageRepository
    private lateinit var viewModel: GmailEmailDetailsViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        aiRepository = mock(AIRepository::class.java)
        gmailRepository = mock(GmailRepository::class.java)
        aiUsageRepository = mock(AiUsageRepository::class.java)

        viewModel = GmailEmailDetailsViewModel(gmailRepository, aiRepository, aiUsageRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testAnalyzeEmail_Success_IncrementsUsage() = runTest(testDispatcher) {
        val dummyAnalysisResult = EmailAnalysisResult(
            type = "HACKATHON",
            title = "Test",
            organization = "Org",
            dateTime = "Now",
            deadline = "Later",
            location = "Online",
            summary = "Summary",
            actionItems = listOf("Action")
        )
        
        `when`(aiUsageRepository.canUseEmailAnalysis()).thenReturn(true)
        `when`(aiUsageRepository.getTodayUsage()).thenReturn(AiDailyUsageEntity(date = "2026-10-06", userId = "test_user_id"))
        `when`(aiRepository.analyzeEmail("message content")).thenReturn(flowOf(Resource.Success(dummyAnalysisResult)))

        viewModel.analyzeEmail("message content")
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isAnalyzing)
        assertEquals(dummyAnalysisResult, state.analysisResult)
        assertNull(state.analyzeError)

        verify(aiUsageRepository).incrementEmailAnalysisUsage()
    }

    @Test
    fun testAnalyzeEmail_BackendError_DoesNotIncrementUsage() = runTest(testDispatcher) {
        val errorMsg = "The AI service is temporarily unavailable. Please try again later."
        
        `when`(aiUsageRepository.canUseEmailAnalysis()).thenReturn(true)
        `when`(aiRepository.analyzeEmail("message content")).thenReturn(flowOf(Resource.Error(errorMsg)))

        viewModel.analyzeEmail("message content")
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isAnalyzing)
        assertNull(state.analysisResult)
        assertEquals(errorMsg, state.analyzeError)

        verify(aiUsageRepository, never()).incrementEmailAnalysisUsage()
    }

    @Test
    fun testAnalyzeEmail_MalformedResponse_DoesNotIncrementUsage() = runTest(testDispatcher) {
        val errorMsg = "Could not process the AI analysis response."
        
        `when`(aiUsageRepository.canUseEmailAnalysis()).thenReturn(true)
        `when`(aiRepository.analyzeEmail("message content")).thenReturn(flowOf(Resource.Error(errorMsg)))

        viewModel.analyzeEmail("message content")
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isAnalyzing)
        assertNull(state.analysisResult)
        assertEquals(errorMsg, state.analyzeError)

        verify(aiUsageRepository, never()).incrementEmailAnalysisUsage()
    }
}
