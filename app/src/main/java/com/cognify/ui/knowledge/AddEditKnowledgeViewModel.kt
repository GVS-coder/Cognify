package com.cognify.ui.knowledge

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cognify.data.local.AnalyticsEventType
import com.cognify.data.local.KnowledgeEntity
import com.cognify.data.repository.AnalyticsLogger
import com.cognify.data.model.Difficulty
import com.cognify.data.model.KnowledgeType
import com.cognify.data.model.ProgrammingLanguage
import com.cognify.data.model.SyncStatus
import com.cognify.data.repository.KnowledgeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

sealed class AddEditKnowledgeEvent {
    object SaveSuccess : AddEditKnowledgeEvent()
    data class Error(val message: String) : AddEditKnowledgeEvent()
}

@HiltViewModel
class AddEditKnowledgeViewModel @Inject constructor(
    private val repository: KnowledgeRepository,
    private val analyticsLogger: AnalyticsLogger,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val knowledgeId: String? = savedStateHandle["knowledgeId"]

    private val _existingItem = MutableStateFlow<KnowledgeEntity?>(null)
    val existingItem: StateFlow<KnowledgeEntity?> = _existingItem

    private val _events = MutableSharedFlow<AddEditKnowledgeEvent>()
    val events: SharedFlow<AddEditKnowledgeEvent> = _events

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving

    init {
        knowledgeId?.let { id ->
            viewModelScope.launch {
                _existingItem.value = repository.getById(id)
            }
        }
    }

    fun save(
        title: String,
        description: String,
        type: KnowledgeType,
        tags: List<String>,
        language: ProgrammingLanguage,
        difficulty: Difficulty,
        category: String,
        codeBlock: String?,
        source: String?,
        isFavorite: Boolean,
        pinned: Boolean,
        isPrivate: Boolean = _existingItem.value?.isPrivate ?: false
    ) {
        if (title.isBlank()) {
            viewModelScope.launch { _events.emit(AddEditKnowledgeEvent.Error("Title cannot be empty")) }
            return
        }
        _isSaving.value = true
        val now = System.currentTimeMillis()
        val item = KnowledgeEntity(
            id = knowledgeId ?: UUID.randomUUID().toString(),
            title = title.trim(),
            description = description.trim(),
            type = type,
            tags = tags,
            language = language,
            difficulty = difficulty,
            category = category.trim(),
            codeBlock = codeBlock?.trim()?.takeIf { it.isNotBlank() },
            source = source?.trim()?.takeIf { it.isNotBlank() },
            isFavorite = isFavorite,
            pinned = pinned,
            folderId = _existingItem.value?.folderId,
            embeddingStatus = null,
            aiSummary = null,
            lastIndexed = null,
            createdAt = _existingItem.value?.createdAt ?: now,
            updatedAt = now,
            syncStatus = SyncStatus.PENDING,
            lastSyncedAt = null,
            isPrivate = isPrivate
        )
        viewModelScope.launch {
            try {
                repository.saveKnowledge(item)
                if (knowledgeId == null) {
                    analyticsLogger.logEvent(AnalyticsEventType.KNOWLEDGE_ADDED, referenceId = item.id)
                }
                _events.emit(AddEditKnowledgeEvent.SaveSuccess)
            } catch (e: Exception) {
                _events.emit(AddEditKnowledgeEvent.Error(e.message ?: "Save failed"))
            } finally {
                _isSaving.value = false
            }
        }
    }
}
