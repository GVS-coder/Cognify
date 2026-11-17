package com.cognify.ai

import com.cognify.data.model.EmailAnalysisResult
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException

object EmailAnalysisParser {
    private val gson = Gson()

    fun parse(response: String?): EmailAnalysisResult? {
        if (response.isNullOrBlank()) return null

        val trimmed = response.trim()
        
        // Extract potential JSON block if there is extra text
        val startIndex = trimmed.indexOf('{')
        val endIndex = trimmed.lastIndexOf('}')
        
        var jsonCandidate = trimmed
        if (startIndex != -1 && endIndex != -1 && endIndex > startIndex) {
            jsonCandidate = trimmed.substring(startIndex, endIndex + 1)
        }

        if (jsonCandidate.startsWith("{") && jsonCandidate.endsWith("}")) {
            return try {
                gson.fromJson(jsonCandidate, EmailAnalysisResult::class.java)
            } catch (e: JsonSyntaxException) {
                null
            }
        }
        
        return null
    }
}
