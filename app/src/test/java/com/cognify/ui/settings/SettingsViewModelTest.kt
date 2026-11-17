package com.cognify.ui.settings

import android.content.Context
import android.os.Build
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.core.app.ApplicationProvider
import com.cognify.data.local.AppDatabase
import com.cognify.data.local.ReminderDao
import com.cognify.data.model.Resource
import com.cognify.data.model.User
import com.cognify.data.repository.AppSettings
import com.cognify.data.repository.AuthRepository
import com.cognify.data.repository.ProfileRepository
import com.cognify.data.repository.SettingsRepository
import com.cognify.data.repository.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.O], manifest = Config.NONE)
class SettingsViewModelTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var settingsRepository: SettingsRepository
    private lateinit var profileRepository: ProfileRepository
    private lateinit var authRepository: AuthRepository
    private lateinit var appDatabase: AppDatabase
    private lateinit var reminderDao: ReminderDao
    private lateinit var context: Context

    private lateinit var viewModel: SettingsViewModel
    private val appSettingsFlow = MutableStateFlow(AppSettings())

    private val testUser = User(uid = "user_12345", email = "test@cognify.app", displayName = "Test User")

    @Before
    fun setup() = kotlinx.coroutines.runBlocking {
        Dispatchers.setMain(testDispatcher)

        context = ApplicationProvider.getApplicationContext()

        settingsRepository = mock(SettingsRepository::class.java)
        `when`(settingsRepository.appSettingsFlow).thenReturn(appSettingsFlow)

        profileRepository = mock(ProfileRepository::class.java)
        authRepository = mock(AuthRepository::class.java)
        appDatabase = mock(AppDatabase::class.java)
        reminderDao = mock(ReminderDao::class.java)
        `when`(appDatabase.reminderDao()).thenReturn(reminderDao)
        `when`(reminderDao.getByStatusList(anyString(), anyString())).thenReturn(emptyList())

        `when`(authRepository.getCurrentUser()).thenReturn(testUser)

        viewModel = SettingsViewModel(
            settingsRepository = settingsRepository,
            profileRepository = profileRepository,
            authRepository = authRepository,
            appDatabase = appDatabase,
            context = context,
            ioDispatcher = testDispatcher
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `successful account deletion clears cloud, local room database, preferences, and logs out`() = runTest(testDispatcher) {
        `when`(profileRepository.deleteAccount()).thenReturn(Resource.Success(Unit))

        var callbackSuccess: Boolean? = null
        viewModel.deleteAccount { success, _ ->
            callbackSuccess = success
        }

        advanceUntilIdle()

        assertEquals(DeleteAccountState.Success, viewModel.deleteState.value)
        assertEquals(true, callbackSuccess)

        // Verify cloud deletion was called
        verify(profileRepository).deleteAccount()

        // Verify local room database tables were cleared specifically for the deleted user
        verify(appDatabase).clearUserData("user_12345")

        // Verify preferences were cleared
        verify(settingsRepository).clearPreferences()

        // Verify auth logout occurred
        verify(authRepository).logout()
    }

    @Test
    fun `cloud firestore deletion failure does not wipe local database and reports error`() = runTest(testDispatcher) {
        val errorMessage = "Firestore connection failed: network timeout"
        `when`(profileRepository.deleteAccount()).thenReturn(Resource.Error(errorMessage))

        var callbackSuccess: Boolean? = null
        var callbackError: String? = null
        viewModel.deleteAccount { success, error ->
            callbackSuccess = success
            callbackError = error
        }

        advanceUntilIdle()

        assertEquals(DeleteAccountState.Error(errorMessage), viewModel.deleteState.value)
        assertEquals(false, callbackSuccess)
        assertEquals(errorMessage, callbackError)

        // Verify cloud deletion was attempted
        verify(profileRepository).deleteAccount()

        // CRITICAL: Local Room database and settings must NOT be cleared when cloud deletion fails!
        verify(appDatabase, never()).clearUserData(anyString())
        verify(settingsRepository, never()).clearPreferences()
        verify(authRepository, never()).logout()
    }

    @Test
    fun `authentication deletion failure does not wipe local database and reports error`() = runTest(testDispatcher) {
        val errorMessage = "Firebase Auth user deletion failed"
        `when`(profileRepository.deleteAccount()).thenReturn(Resource.Error(errorMessage))

        viewModel.deleteAccount()
        advanceUntilIdle()

        assertEquals(DeleteAccountState.Error(errorMessage), viewModel.deleteState.value)
        verify(appDatabase, never()).clearUserData(anyString())
        verify(settingsRepository, never()).clearPreferences()
        verify(authRepository, never()).logout()
    }

    @Test
    fun `recent login required error transitions to RequiresReauth state without clearing local data`() = runTest(testDispatcher) {
        val reauthMessage = "RECENT_LOGIN_REQUIRED:password:test@cognify.app"
        val exception = com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException("ERROR_REQUIRES_RECENT_LOGIN", "Recent login required")
        `when`(profileRepository.deleteAccount()).thenReturn(Resource.Error(reauthMessage, exception))

        viewModel.deleteAccount()
        advanceUntilIdle()

        assertEquals(
            DeleteAccountState.RequiresReauth("password", "test@cognify.app"),
            viewModel.deleteState.value
        )

        // Local data must not be touched yet
        verify(appDatabase, never()).clearUserData(anyString())
        verify(settingsRepository, never()).clearPreferences()
        verify(authRepository, never()).logout()
    }

    @Test
    fun `reauthenticateWithPassword succeeds and completes account deletion and local cleanup`() = runTest(testDispatcher) {
        // Step 1: Initial delete triggers recent login required
        `when`(profileRepository.deleteAccount())
            .thenReturn(Resource.Error("RECENT_LOGIN_REQUIRED:password:test@cognify.app"))
            .thenReturn(Resource.Success(Unit))

        viewModel.deleteAccount()
        advanceUntilIdle()
        assertTrue(viewModel.deleteState.value is DeleteAccountState.RequiresReauth)

        // Step 2: User provides correct password to reauthenticate
        `when`(authRepository.reauthenticate("ValidPassword123!")).thenReturn(Resource.Success(Unit))

        viewModel.reauthenticateWithPassword("ValidPassword123!")
        advanceUntilIdle()

        // Reauth was called
        verify(authRepository).reauthenticate("ValidPassword123!")
        // Deletion succeeded
        assertEquals(DeleteAccountState.Success, viewModel.deleteState.value)
        // Local data wiped
        verify(appDatabase).clearUserData("user_12345")
        verify(settingsRepository).clearPreferences()
        verify(authRepository).logout()
    }

    @Test
    fun `reauthenticateWithPassword failure reports error and does not delete account`() = runTest(testDispatcher) {
        `when`(authRepository.reauthenticate("WrongPassword")).thenReturn(Resource.Error("Incorrect password."))

        viewModel.reauthenticateWithPassword("WrongPassword")
        advanceUntilIdle()

        assertEquals(DeleteAccountState.Error("Incorrect password."), viewModel.deleteState.value)
        verify(profileRepository, never()).deleteAccount()
        verify(appDatabase, never()).clearUserData(anyString())
    }

    @Test
    fun `reauthenticateWithGoogle succeeds and completes deletion`() = runTest(testDispatcher) {
        `when`(authRepository.reauthenticateWithGoogle("google_id_token_xyz")).thenReturn(Resource.Success(Unit))
        `when`(profileRepository.deleteAccount()).thenReturn(Resource.Success(Unit))

        viewModel.reauthenticateWithGoogle("google_id_token_xyz")
        advanceUntilIdle()

        verify(authRepository).reauthenticateWithGoogle("google_id_token_xyz")
        assertEquals(DeleteAccountState.Success, viewModel.deleteState.value)
        verify(appDatabase).clearUserData("user_12345")
    }

    @Test
    fun `missing user session prevents deletion and reports error immediately without false success`() = runTest(testDispatcher) {
        `when`(authRepository.getCurrentUser()).thenReturn(null)

        var callbackSuccess: Boolean? = null
        viewModel.deleteAccount { success, _ ->
            callbackSuccess = success
        }

        advanceUntilIdle()

        assertTrue(viewModel.deleteState.value is DeleteAccountState.Error)
        assertEquals(false, callbackSuccess)
        verify(profileRepository, never()).deleteAccount()
        verify(appDatabase, never()).clearUserData(anyString())
    }

    @Test
    fun `repeated deleteAccount calls while loading are ignored`() = runTest(testDispatcher) {
        `when`(profileRepository.deleteAccount()).thenReturn(Resource.Success(Unit))

        // Trigger first call
        viewModel.deleteAccount()
        // Immediate second call while in loading/execution
        viewModel.deleteAccount()

        advanceUntilIdle()

        // Should only have been called once despite multiple invocations
        verify(profileRepository, times(1)).deleteAccount()
    }

    @Test
    fun `updateThemeMode persists through repository`() = runTest(testDispatcher) {
        viewModel.updateThemeMode(ThemeMode.DARK)
        advanceUntilIdle()
        verify(settingsRepository).updateThemeMode(ThemeMode.DARK)
    }

    @Test
    fun `updateDynamicColor persists through repository`() = runTest(testDispatcher) {
        viewModel.updateDynamicColor(false)
        advanceUntilIdle()
        verify(settingsRepository).updateDynamicColor(false)
    }

    @Test
    fun `resetDeleteState resets state to Idle`() {
        viewModel.resetDeleteState()
        assertEquals(DeleteAccountState.Idle, viewModel.deleteState.value)
    }
}
