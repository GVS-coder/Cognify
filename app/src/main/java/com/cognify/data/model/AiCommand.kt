package com.cognify.data.model

import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName

data class AiCommand(
    @SerializedName("type")
    private val rawType: String?,
    
    @SerializedName("action")
    private val rawAction: String? = null,
    
    @SerializedName("parameters")
    val parameters: JsonObject? = null,
    
    @SerializedName("confirmationMessage")
    val confirmationMessage: String? = null
) {
    val type: AiCommandType
        get() = AiCommandType.fromString(rawType)

    val action: AiCommandAction?
        get() = if (rawAction == null) null else AiCommandAction.fromString(rawAction)
}

enum class AiCommandType {
    TEXT,
    ACTION,
    UNKNOWN;

    companion object {
        fun fromString(value: String?): AiCommandType {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: UNKNOWN
        }
    }
}

enum class AiCommandAction {
    CREATE_NOTE,
    UPDATE_NOTE,
    DELETE_NOTE,
    
    CREATE_TASK,
    UPDATE_TASK,
    DELETE_TASK,
    
    CREATE_REMINDER,
    UPDATE_REMINDER,
    DELETE_REMINDER,
    
    CREATE_ALARM,
    UPDATE_ALARM,
    DELETE_ALARM,
    
    BATCH_ACTION,
    
    UNKNOWN;

    companion object {
        fun fromString(value: String?): AiCommandAction {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: UNKNOWN
        }
    }
}
