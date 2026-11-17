package com.cognify.ui.smartinbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cognify.data.model.GmailMessage
import com.cognify.data.model.SmartInboxCategory
import com.cognify.data.repository.GmailRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import androidx.lifecycle.SavedStateHandle

data class SmartInboxUiState(
    val isLoading: Boolean = false,
    val messages: List<GmailMessage> = emptyList(),
    val error: String? = null,
    val selectedCategory: SmartInboxCategory? = null,
    val customQuery: String? = null
)

@HiltViewModel
class SmartInboxViewModel @Inject constructor(
    private val gmailRepository: GmailRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        SmartInboxUiState(
            selectedCategory = savedStateHandle.get<String>("selectedCategory")?.let {
                try { SmartInboxCategory.valueOf(it) } catch (e: Exception) { null }
            },
            customQuery = savedStateHandle.get<String>("customQuery")
        )
    )
    val uiState: StateFlow<SmartInboxUiState> = _uiState.asStateFlow()

    init {
        loadMessages()
    }

    fun loadMessages(isRefresh: Boolean = false) {
        if (!isRefresh) {
            _uiState.update { it.copy(isLoading = true, error = null) }
        }
        
        viewModelScope.launch {
            val state = _uiState.value
            val result = gmailRepository.getRecentMessages(state.selectedCategory, state.customQuery)
            
            result.onSuccess { messages ->
                _uiState.update { it.copy(isLoading = false, messages = messages, error = null) }
            }.onFailure { error ->
                _uiState.update { it.copy(isLoading = false, error = error.message ?: "Failed to load emails") }
            }
        }
    }

    fun setCategory(category: SmartInboxCategory?) {
        savedStateHandle["selectedCategory"] = category?.name
        savedStateHandle["customQuery"] = null
        _uiState.update { it.copy(selectedCategory = category, customQuery = null) }
        loadMessages()
    }

    fun setCustomQuery(query: String) {
        savedStateHandle["selectedCategory"] = SmartInboxCategory.CUSTOM.name
        savedStateHandle["customQuery"] = query
        _uiState.update { it.copy(selectedCategory = SmartInboxCategory.CUSTOM, customQuery = query) }
        loadMessages()
    }
}
