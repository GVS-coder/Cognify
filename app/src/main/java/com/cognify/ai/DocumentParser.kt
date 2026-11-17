package com.cognify.ai

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DocumentParser @Inject constructor(
    @ApplicationContext private val context: Context
) {

    init {
        PDFBoxResourceLoader.init(context)
    }

    suspend fun parseDocument(uri: Uri): ParsedDocument = withContext(Dispatchers.IO) {
        val (name, size) = getFileInfo(uri)
        val extension = name.substringAfterLast('.', "").lowercase()

        if (size > 10 * 1024 * 1024) { // 10MB limit
            throw Exception("File is too large. Maximum size is 10MB.")
        }

        val text = when (extension) {
            "pdf" -> extractPdfText(uri)
            "txt", "md", "csv" -> extractPlainText(uri)
            else -> throw Exception("Unsupported file format: $extension")
        }
        
        if (text.isBlank()) {
            throw Exception("Could not extract text. The document might be empty, scanned, or image-only.")
        }
        
        val truncatedText = if (text.length > 50000) {
            text.substring(0, 50000) // Truncate to save tokens
        } else text

        ParsedDocument(name, extension.uppercase(), size, truncatedText)
    }

    private fun extractPdfText(uri: Uri): String {
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            val document = PDDocument.load(inputStream)
            val stripper = PDFTextStripper()
            val text = stripper.getText(document)
            document.close()
            return text
        }
        return ""
    }

    private fun extractPlainText(uri: Uri): String {
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            return inputStream.bufferedReader().readText()
        }
        return ""
    }

    private fun getFileInfo(uri: Uri): Pair<String, Long> {
        var name = "Unknown Document"
        var size = 0L
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst()) {
                name = cursor.getString(nameIndex)
                size = cursor.getLong(sizeIndex)
            }
        }
        return Pair(name, size)
    }
}

data class ParsedDocument(
    val name: String,
    val type: String,
    val sizeBytes: Long,
    val text: String
) {
    fun formatSize(): String {
        if (sizeBytes < 1024) return "$sizeBytes B"
        val z = (63 - java.lang.Long.numberOfLeadingZeros(sizeBytes)) / 10
        return String.format("%.1f %sB", sizeBytes.toDouble() / (1L shl (z * 10)), " KMGTPE"[z])
    }
}
