package com.cognify.ai

import com.cognify.BuildConfig
import com.cognify.data.model.AiUsage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CloudflareAiService @Inject constructor() : AiDataSource {

    override suspend fun generateResponse(prompt: String, requestType: String): String? = withContext(Dispatchers.IO) {
        try {
            val endpoint = "${BuildConfig.CLOUDFLARE_AI_BACKEND_URL}/ai"
            val url = URL(endpoint)
            val connection = url.openConnection() as HttpURLConnection

            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "application/json")
            connection.doOutput = true
            connection.doInput = true
            connection.connectTimeout = 15000
            connection.readTimeout = 30000

            val jsonBody = JSONObject().apply {
                put("prompt", prompt)
                put("requestType", requestType)
            }

            OutputStreamWriter(connection.outputStream).use { writer ->
                writer.write(jsonBody.toString())
                writer.flush()
            }

            val responseCode = connection.responseCode
            val inputStream = if (responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }

            val responseBody = inputStream?.bufferedReader()?.use { it.readText() }

            if (responseBody == null) {
                return@withContext null
            }

            val jsonResponse = try {
                JSONObject(responseBody)
            } catch (e: org.json.JSONException) {
                if (responseCode !in 200..299) {
                    throw AiServiceException("The AI service is temporarily unavailable. Please try again later.")
                }
                throw AiServiceException("Received an invalid response from the AI service.")
            }
            
            val success = jsonResponse.optBoolean("success", false)

            if (success) {
                return@withContext jsonResponse.optString("response")
            } else {
                val errorMsg = jsonResponse.optString("error", "An unknown error occurred")
                throw AiServiceException(errorMsg)
            }

        } catch (e: AiServiceException) {
            throw e
        } catch (e: Exception) {
            timber.log.Timber.e(e, "Cloudflare AI request failed")
            throw e
        }
    }

    override suspend fun getAiUsage(): AiUsage? {
        // Not implemented in Phase 2C-3 backend yet, return default/mock or null
        return null
    }
}

class AiServiceException(message: String) : Exception(message)
