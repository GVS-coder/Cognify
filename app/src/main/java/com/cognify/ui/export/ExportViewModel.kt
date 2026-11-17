package com.cognify.ui.export

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cognify.data.local.KnowledgeEntity
import com.cognify.data.model.KnowledgeType
import com.cognify.data.repository.KnowledgeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class ExportState(
    val isLoading: Boolean = false,
    val isSuccess: Boolean = false,
    val error: String? = null,
    val message: String = ""
)

@HiltViewModel
class ExportViewModel @Inject constructor(
    private val knowledgeRepository: KnowledgeRepository
) : ViewModel() {

    private val _exportState = MutableStateFlow(ExportState())
    val exportState: StateFlow<ExportState> = _exportState.asStateFlow()

    fun clearState() {
        _exportState.value = ExportState()
    }

    fun generateAndExportCsv(context: Context, uri: Uri, exportNotes: Boolean, exportCode: Boolean) {
        viewModelScope.launch {
            _exportState.value = ExportState(isLoading = true)
            try {
                val knowledge = knowledgeRepository.getAllKnowledge().first()

                val codeTypes = setOf(KnowledgeType.CODE, KnowledgeType.SQL, KnowledgeType.LINUX)
                val filteredList = mutableListOf<KnowledgeEntity>()

                knowledge.forEach { item ->
                    if (item.isPrivate) return@forEach // Exclude private notes from unauthenticated plain-text export
                    val isCode = codeTypes.contains(item.type)
                    if (isCode && exportCode) {
                        filteredList.add(item)
                    } else if (!isCode && exportNotes) {
                        filteredList.add(item)
                    }
                }

                if (filteredList.isEmpty()) {
                    val msg = if (exportNotes && exportCode) "No data available to export."
                              else if (exportNotes) "No notes available to export."
                              else "No code entries available to export."
                    _exportState.value = ExportState(isLoading = false, error = msg)
                    return@launch
                }

                writeCsv(context, uri, filteredList)

                val msg = if (exportNotes && exportCode) "Notes and Code exported successfully."
                          else if (exportNotes) "Notes exported successfully."
                          else "Code exported successfully."

                _exportState.value = ExportState(isLoading = false, isSuccess = true, message = msg)

            } catch (e: Exception) {
                _exportState.value = ExportState(isLoading = false, error = "Export failed: ${e.message}")
            }
        }
    }

    private fun writeCsv(context: Context, uri: Uri, data: List<KnowledgeEntity>) {
        context.contentResolver.openOutputStream(uri)?.use { outputStream ->
            OutputStreamWriter(outputStream, Charsets.UTF_8).use { writer ->
                val headers = listOf("Title", "Knowledge Type", "Programming Language", "Description", "Code", "Category", "Tags", "Source", "Created Date", "Updated Date", "Favorite", "Pinned", "Private")
                writer.write(headers.joinToString(",") { escapeCsv(it) })
                writer.write("\n")

                val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

                data.forEach { item ->
                    val row = listOf(
                        item.title,
                        item.type.name,
                        item.language.displayName,
                        item.description,
                        item.codeBlock ?: "",
                        item.category,
                        item.tags.joinToString("; "),
                        item.source ?: "",
                        sdf.format(Date(item.createdAt)),
                        sdf.format(Date(item.updatedAt)),
                        item.isFavorite.toString(),
                        item.pinned.toString(),
                        item.isPrivate.toString()
                    )
                    writer.write(row.joinToString(",") { escapeCsv(it) })
                    writer.write("\n")
                }
            }
        }
    }

    private fun escapeCsv(value: String): String {
        var escaped = value.replace("\"", "\"\"")
        if (escaped.contains(",") || escaped.contains("\"") || escaped.contains("\n") || escaped.contains("\r")) {
            escaped = "\"$escaped\""
        }
        return escaped
    }
}
