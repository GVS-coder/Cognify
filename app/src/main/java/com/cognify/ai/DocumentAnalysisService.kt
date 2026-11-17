package com.cognify.ai

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.cognify.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

enum class DocumentAnalysisErrorType {
    FILE_TOO_LARGE,
    PAGE_LIMIT_EXCEEDED,
    UNSUPPORTED_FILE,
    EMPTY_DOCUMENT,
    OCR_FAILED,
    AI_FAILED,
    NETWORK_ERROR,
    TIMEOUT,
    SERVER_ERROR,
    RATE_LIMIT,
    UNKNOWN
}

data class DocumentAnalysisErrorDetails(
    val type: DocumentAnalysisErrorType,
    val currentValue: String? = null,
    val maximumValue: String? = null,
    val detailMessage: String? = null
)

class DocumentAnalysisException(
    val errorDetails: DocumentAnalysisErrorDetails
) : Exception(errorDetails.detailMessage ?: errorDetails.type.name)

@Singleton
class DocumentAnalysisService @Inject constructor(
    @ApplicationContext private val context: Context
) {

    suspend fun analyzeDocument(uri: Uri, action: String): String = withContext(Dispatchers.IO) {
        val (name, size, type) = getFileInfo(uri)
        val maxSizeBytes = 5L * 1024 * 1024
        if (size > maxSizeBytes) {
            throw DocumentAnalysisException(
                DocumentAnalysisErrorDetails(
                    type = DocumentAnalysisErrorType.FILE_TOO_LARGE,
                    currentValue = "${size / (1024 * 1024)} MB",
                    maximumValue = "5 MB",
                    detailMessage = "This document is too large."
                )
            )
        }
        if (type != "application/pdf" && !name.lowercase().endsWith(".pdf")) {
            throw DocumentAnalysisException(
                DocumentAnalysisErrorDetails(
                    type = DocumentAnalysisErrorType.UNSUPPORTED_FILE,
                    detailMessage = "Only PDF files are supported."
                )
            )
        }
        val boundary = "Boundary-" + System.currentTimeMillis()
        val endpoint = "${BuildConfig.CLOUDFLARE_AI_BACKEND_URL}/document"
        val url = URL(endpoint)
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        connection.doOutput = true
        connection.doInput = true
        connection.connectTimeout = 30000 // OCR can be slow
        connection.readTimeout = 60000

        try {
            connection.outputStream.use { outputStream ->
                val writer = PrintWriter(OutputStreamWriter(outputStream, "UTF-8"), true)
                
                // Add action
                writer.append("--$boundary").append("\r\n")
                writer.append("Content-Disposition: form-data; name=\"action\"").append("\r\n")
                writer.append("Content-Type: text/plain; charset=UTF-8").append("\r\n")
                writer.append("\r\n")
                writer.append(action).append("\r\n")
                writer.flush()
                
                // Add file
                writer.append("--$boundary").append("\r\n")
                val finalName = if (name.lowercase().endsWith(".pdf")) name else "$name.pdf"
                writer.append("Content-Disposition: form-data; name=\"file\"; filename=\"$finalName\"").append("\r\n")
                writer.append("Content-Type: application/pdf").append("\r\n")
                writer.append("Content-Transfer-Encoding: binary").append("\r\n")
                writer.append("\r\n")
                writer.flush()
                
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
                outputStream.flush()
                
                writer.append("\r\n")
                writer.flush()
                
                // End boundary
                writer.append("--$boundary--").append("\r\n")
                writer.close()
            }

            val responseCode = connection.responseCode
            timber.log.Timber.d("Document Analysis - HTTP Status: %d", responseCode)
            timber.log.Timber.d("Document Analysis - Content-Type: %s", connection.contentType)

            val inputStream = if (responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }

            val responseBody = inputStream?.bufferedReader()?.use { it.readText() } ?: ""
            // Log truncated response body
            val logBody = if (responseBody.length > 200) responseBody.take(200) + "..." else responseBody
            timber.log.Timber.d("Document Analysis - Response Body: %s", logBody)

            if (responseCode !in 200..299) {
                val jsonResponse = try {
                    JSONObject(responseBody)
                } catch (e: Exception) {
                    null
                }
                
                val jsonErrorMsg = jsonResponse?.optString("error")?.takeIf { it.isNotBlank() }
                val code = jsonResponse?.optString("code")

                val errorDetails = when (code) {
                    "INVALID_REQUEST" -> DocumentAnalysisErrorDetails(DocumentAnalysisErrorType.UNKNOWN, detailMessage = "The document request is invalid.")
                    "INVALID_PDF" -> DocumentAnalysisErrorDetails(DocumentAnalysisErrorType.UNSUPPORTED_FILE, detailMessage = "Only PDF files are supported.")
                    "FILE_TOO_LARGE" -> DocumentAnalysisErrorDetails(DocumentAnalysisErrorType.FILE_TOO_LARGE, maximumValue = "5 MB")
                    "OCR_REQUEST_FAILED" -> if (jsonErrorMsg?.contains("Rate limit", ignoreCase = true) == true) {
                        DocumentAnalysisErrorDetails(DocumentAnalysisErrorType.RATE_LIMIT, detailMessage = "Document analysis rate limit exceeded. Please try again later.")
                    } else {
                        DocumentAnalysisErrorDetails(DocumentAnalysisErrorType.OCR_FAILED, detailMessage = jsonErrorMsg)
                    }
                    "OCR_EMPTY_RESULT" -> DocumentAnalysisErrorDetails(DocumentAnalysisErrorType.EMPTY_DOCUMENT)
                    "OCR_RESPONSE_INVALID", "OCR_TEXT_TOO_LARGE" -> DocumentAnalysisErrorDetails(DocumentAnalysisErrorType.OCR_FAILED, detailMessage = "Failed to process document text.")
                    "NVIDIA_AUTH_ERROR" -> DocumentAnalysisErrorDetails(DocumentAnalysisErrorType.SERVER_ERROR, detailMessage = "Document analysis authorization failed.")
                    "NVIDIA_RATE_LIMIT" -> DocumentAnalysisErrorDetails(DocumentAnalysisErrorType.RATE_LIMIT, detailMessage = "The AI service is temporarily busy.")
                    "NVIDIA_TIMEOUT" -> DocumentAnalysisErrorDetails(DocumentAnalysisErrorType.TIMEOUT, detailMessage = "Document AI analysis timed out.")
                    "NVIDIA_UPSTREAM_ERROR" -> DocumentAnalysisErrorDetails(DocumentAnalysisErrorType.AI_FAILED, detailMessage = "AI provider failed to process.")
                    "NVIDIA_RESPONSE_INVALID" -> DocumentAnalysisErrorDetails(DocumentAnalysisErrorType.AI_FAILED, detailMessage = "Received malformed AI response.")
                    "DOCUMENT_ANALYSIS_FAILED" -> DocumentAnalysisErrorDetails(DocumentAnalysisErrorType.SERVER_ERROR, detailMessage = "The document processing service failed.")
                    else -> DocumentAnalysisErrorDetails(DocumentAnalysisErrorType.UNKNOWN, detailMessage = jsonErrorMsg ?: "Unable to analyze the document.")
                }
                
                throw DocumentAnalysisException(errorDetails)
            }

            if (responseBody.isBlank()) {
                throw DocumentAnalysisException(DocumentAnalysisErrorDetails(DocumentAnalysisErrorType.SERVER_ERROR, detailMessage = "Received an invalid response."))
            }

            val jsonResponse = try {
                JSONObject(responseBody)
            } catch (e: org.json.JSONException) {
                throw DocumentAnalysisException(DocumentAnalysisErrorDetails(DocumentAnalysisErrorType.SERVER_ERROR, detailMessage = "Received an invalid response."))
            }

            if (jsonResponse.optBoolean("success", false)) {
                return@withContext jsonResponse.optString("response")
            } else {
                val errorMsg = jsonResponse.optString("error", "Unable to analyze the document. Please try again.")
                throw DocumentAnalysisException(DocumentAnalysisErrorDetails(DocumentAnalysisErrorType.UNKNOWN, detailMessage = errorMsg))
            }
        } catch (e: Exception) {
            if (e is DocumentAnalysisException) throw e
            val errorMsg = e.message ?: "Unable to analyze the document. Please try again."
            if (errorMsg.contains("failed to connect", true) || errorMsg.contains("Unable to resolve host", true)) {
                throw DocumentAnalysisException(DocumentAnalysisErrorDetails(DocumentAnalysisErrorType.NETWORK_ERROR))
            }
            if (errorMsg.contains("timeout", true)) {
                throw DocumentAnalysisException(DocumentAnalysisErrorDetails(DocumentAnalysisErrorType.TIMEOUT))
            }
            throw DocumentAnalysisException(DocumentAnalysisErrorDetails(DocumentAnalysisErrorType.UNKNOWN, detailMessage = errorMsg))
        }
    }

    fun getFileInfo(uri: Uri): Triple<String, Long, String> {
        var name = "document.pdf"
        var size = 0L
        val type = context.contentResolver.getType(uri) ?: "application/pdf"
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst()) {
                if (nameIndex >= 0) name = cursor.getString(nameIndex)
                if (sizeIndex >= 0) size = cursor.getLong(sizeIndex)
            }
        }
        return Triple(name, size, type)
    }
}
