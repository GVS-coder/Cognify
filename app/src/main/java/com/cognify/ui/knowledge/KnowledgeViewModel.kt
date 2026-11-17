package com.cognify.ui.knowledge

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cognify.data.local.KnowledgeEntity
import com.cognify.data.model.KnowledgeType
import com.cognify.data.repository.KnowledgeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

import androidx.lifecycle.SavedStateHandle
import com.cognify.data.repository.AnalyticsLogger
import com.cognify.data.local.AnalyticsEventType

sealed class KnowledgeUiState {
    object Loading : KnowledgeUiState()
    object Empty : KnowledgeUiState()
    data class Success(val items: List<KnowledgeEntity>, val query: String = "") : KnowledgeUiState()
    data class Error(val message: String) : KnowledgeUiState()
}

@OptIn(ExperimentalCoroutinesApi::class, kotlinx.coroutines.FlowPreview::class)
@HiltViewModel
class KnowledgeViewModel @Inject constructor(
    private val repository: KnowledgeRepository,
    private val savedStateHandle: SavedStateHandle,
    private val analyticsLogger: AnalyticsLogger
) : ViewModel() {

    private val _searchQuery = savedStateHandle.getStateFlow("search_query", "")
    private val _filterType = MutableStateFlow<KnowledgeType?>(null)
    private val _showFavoritesOnly = MutableStateFlow(false)

    private val debouncedQuery = _searchQuery.debounce(300).distinctUntilChanged()

    val uiState: StateFlow<KnowledgeUiState> = combine(
        debouncedQuery,
        _filterType,
        _showFavoritesOnly
    ) { query, type, favOnly -> Triple(query, type, favOnly) }
        .flatMapLatest { (query, type, favOnly) ->
            when {
                favOnly -> repository.getFavorites()
                query.isNotBlank() -> repository.search(query.trim())
                type != null -> repository.getByType(type.name)
                else -> repository.getAllKnowledge()
            }
        }
        .map { items ->
            if (items.isEmpty()) KnowledgeUiState.Empty
            else KnowledgeUiState.Success(items, _searchQuery.value)
        }
        .catch { e -> emit(KnowledgeUiState.Error(e.message ?: "Unknown error")) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), KnowledgeUiState.Loading)

    fun setSearchQuery(query: String) { 
        savedStateHandle["search_query"] = query
        if (query.isNotBlank()) {
            viewModelScope.launch {
                analyticsLogger.logEvent(AnalyticsEventType.SEARCH_QUERY, metadata = query)
            }
        }
    }
    fun setFilter(type: KnowledgeType?) { _filterType.value = type; _showFavoritesOnly.value = false }
    fun setFavoritesOnly(enabled: Boolean) { _showFavoritesOnly.value = enabled; if (enabled) _filterType.value = null }

    fun logKnowledgeView(id: String) {
        viewModelScope.launch {
            analyticsLogger.logEvent(AnalyticsEventType.KNOWLEDGE_VIEWED, referenceId = id)
        }
    }

    fun deleteKnowledge(id: String) {
        viewModelScope.launch { repository.deleteKnowledge(id) }
    }

    fun syncFromCloud() {
        viewModelScope.launch { repository.syncFromFirestore() }
    }
}
