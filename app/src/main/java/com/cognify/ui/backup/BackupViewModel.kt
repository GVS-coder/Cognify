package com.cognify.ui.backup

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cognify.data.model.CognifyBackup
import com.cognify.data.model.Resource
import com.cognify.data.repository.BackupMetadata
import com.cognify.data.repository.BackupRepository
import com.google.gson.Gson
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import javax.inject.Inject

sealed class BackupUiState {
    object Initial : BackupUiState()
    object Loading : BackupUiState()
    data class Success(val metadata: BackupMetadata) : BackupUiState()
    data class Error(val message: String) : BackupUiState()
}

@HiltViewModel
class BackupViewModel @Inject constructor(
    private val backupRepository: BackupRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<BackupUiState>(BackupUiState.Initial)
    val uiState: StateFlow<BackupUiState> = _uiState.asStateFlow()

    private val gson = Gson()

    init {
        fetchMetadata()
    }

    fun fetchMetadata() {
        viewModelScope.launch {
            _uiState.value = BackupUiState.Loading
            when (val result = backupRepository.getLastBackupMetadata()) {
                is Resource.Success -> {
                    if (result.data != null) {
                        _uiState.value = BackupUiState.Success(result.data)
                    } else {
                        _uiState.value = BackupUiState.Error("Unknown metadata state")
                    }
                }
                is Resource.Error -> {
                    _uiState.value = BackupUiState.Error(result.message ?: "Failed to load metadata")
                }
                is Resource.Loading -> { }
            }
        }
    }

    fun exportLocalBackup(context: Context, uri: Uri, appVersion: String) {
        viewModelScope.launch {
            _uiState.value = BackupUiState.Loading
            when (val result = backupRepository.createLocalBackup(appVersion)) {
                is Resource.Success -> {
                    val backupData = result.data
                    if (backupData != null) {
                        try {
                            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                                OutputStreamWriter(outputStream, Charsets.UTF_8).use { writer ->
                                    gson.toJson(backupData, writer)
                                }
                            }
                            // Metadata could be artificially updated to show a success state
                            fetchMetadata()
                        } catch (e: Exception) {
                            _uiState.value = BackupUiState.Error("Failed to write backup file: ${e.message}")
                        }
                    } else {
                        _uiState.value = BackupUiState.Error("No backup data generated")
                    }
                }
                is Resource.Error -> {
                    _uiState.value = BackupUiState.Error(result.message ?: "Backup failed")
                }
                is Resource.Loading -> { }
            }
        }
    }

    fun importLocalBackup(context: Context, uri: Uri) {
        viewModelScope.launch {
            _uiState.value = BackupUiState.Loading
            try {
                var backupData: CognifyBackup? = null
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    InputStreamReader(inputStream, Charsets.UTF_8).use { reader ->
                        backupData = gson.fromJson(reader, CognifyBackup::class.java)
                    }
                }

                if (backupData == null) {
                    _uiState.value = BackupUiState.Error("This backup file is invalid or corrupted.")
                    return@launch
                }

                if (backupData?.format != "cognify_backup") {
                    _uiState.value = BackupUiState.Error("This backup file is invalid or corrupted.")
                    return@launch
                }

                when (val result = backupRepository.restoreLocalBackup(backupData!!)) {
                    is Resource.Success -> {
                        fetchMetadata()
                    }
                    is Resource.Error -> {
                        _uiState.value = BackupUiState.Error(result.message ?: "Restore failed")
                    }
                    is Resource.Loading -> { }
                }

            } catch (e: Exception) {
                _uiState.value = BackupUiState.Error("This backup file is invalid or corrupted.")
            }
        }
    }
}
