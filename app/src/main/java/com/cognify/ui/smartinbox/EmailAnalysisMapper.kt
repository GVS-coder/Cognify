package com.cognify.ui.smartinbox

import android.os.Bundle
import com.cognify.data.model.EmailAnalysisResult
import java.text.SimpleDateFormat
import java.util.Locale

object EmailAnalysisMapper {

    fun parseFlexibleDate(dateStr: String?): Long {
        if (dateStr.isNullOrBlank()) return -1L
        val formats = listOf(
            "MMM dd, yyyy",
            "yyyy-MM-dd",
            "dd/MM/yyyy",
            "MM/dd/yyyy",
            "yyyy-MM-dd'T'HH:mm:ss",
            "dd MMM yyyy",
            "MMMM dd, yyyy"
        )
        for (format in formats) {
            try {
                val sdf = SimpleDateFormat(format, Locale.getDefault())
                sdf.isLenient = false
                val date = sdf.parse(dateStr)
                if (date != null) return date.time
            } catch (e: Exception) {
                // Ignore and try next
            }
        }
        return -1L
    }

    fun mapToTaskPrefill(result: EmailAnalysisResult): Map<String, Any?> {
        val title = result.title ?: result.type ?: "New Task"
        val description = buildString {
            result.organization?.let { append("Org: $it\n") }
            result.dateTime?.let { append("Date: $it\n") }
            result.summary?.let { append("Summary: $it\n") }
            if (!result.actionItems.isNullOrEmpty()) {
                append("Actions:\n")
                result.actionItems.forEach { append("- $it\n") }
            }
        }
        val dueDate = parseFlexibleDate(result.deadline)
        
        return mapOf(
            "prefillTitle" to title,
            "prefillDescription" to description.trimEnd(),
            "prefillDueDate" to dueDate
        )
    }

    fun mapToReminderPrefill(result: EmailAnalysisResult): Map<String, Any?> {
        val title = result.title ?: result.type ?: "New Reminder"
        val description = buildString {
            result.organization?.let { append("Org: $it\n") }
            result.summary?.let { append("Summary: $it\n") }
        }
        val dateTimeStr = if (!result.dateTime.isNullOrBlank()) result.dateTime else result.deadline
        val dateTime = parseFlexibleDate(dateTimeStr)
        
        return mapOf(
            "prefillTitle" to title,
            "prefillDescription" to description.trimEnd(),
            "prefillDateTime" to dateTime
        )
    }
}
