package com.cognify.ai

import com.cognify.data.model.AiCommand
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException

object AiCommandParser {
    private val gson = Gson()

    fun parse(response: String?): AiCommand? {
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
            try {
                val parsed = gson.fromJson(jsonCandidate, AiCommand::class.java)
                if (parsed.type == com.cognify.data.model.AiCommandType.ACTION || 
                    (parsed.type == com.cognify.data.model.AiCommandType.TEXT && parsed.confirmationMessage != null) ||
                    parsed.type == com.cognify.data.model.AiCommandType.UNKNOWN) {
                    return parsed
                }
            } catch (e: JsonSyntaxException) {
                // If it looks like it was trying to be an action but failed parsing,
                // do not leak raw JSON to the user.
                if (jsonCandidate.contains("\"type\": \"ACTION\"") || jsonCandidate.contains("\"type\":\"ACTION\"")) {
                    return AiCommand(
                        rawType = "TEXT",
                        confirmationMessage = "I encountered an error trying to process that command. Please try again."
                    )
                }
            }
        }

        // If it's just raw text (and not a malformed action JSON), strip any remaining markdown if they tried to format text.
        val cleanedText = if (trimmed.startsWith("```") && trimmed.endsWith("```")) {
            trimmed.substringAfter("\n").substringBeforeLast("```").trim()
        } else {
            trimmed
        }

        return AiCommand(
            rawType = "TEXT",
            confirmationMessage = cleanedText
        )
    }
}
