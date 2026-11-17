package com.cognify.data.model

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiCommandTest {

    private val gson = Gson()

    @Test
    fun testNormalTextParsing() {
        val json = """
            {
              "type": "TEXT",
              "confirmationMessage": "Hello"
            }
        """.trimIndent()

        val command = gson.fromJson(json, AiCommand::class.java)
        
        assertEquals(AiCommandType.TEXT, command.type)
        assertNull(command.action)
        assertNull(command.parameters)
        assertEquals("Hello", command.confirmationMessage)
    }

    @Test
    fun testCreateNoteParsing() {
        val json = """
            {
              "type": "ACTION",
              "action": "CREATE_NOTE",
              "parameters": {
                "title": "Professional",
                "content": "I am a student"
              },
              "confirmationMessage": "Created the note."
            }
        """.trimIndent()

        val command = gson.fromJson(json, AiCommand::class.java)

        assertEquals(AiCommandType.ACTION, command.type)
        assertEquals(AiCommandAction.CREATE_NOTE, command.action)
        assertEquals("Professional", command.parameters?.get("title")?.asString)
        assertEquals("I am a student", command.parameters?.get("content")?.asString)
        assertEquals("Created the note.", command.confirmationMessage)
    }

    @Test
    fun testCreateTaskParsing() {
        val json = """
            {
              "type": "ACTION",
              "action": "CREATE_TASK",
              "parameters": {
                "title": "Complete Java assignment",
                "date": "2026-10-06",
                "time": "18:00"
              },
              "confirmationMessage": "Task created."
            }
        """.trimIndent()

        val command = gson.fromJson(json, AiCommand::class.java)

        assertEquals(AiCommandType.ACTION, command.type)
        assertEquals(AiCommandAction.CREATE_TASK, command.action)
        assertEquals("Complete Java assignment", command.parameters?.get("title")?.asString)
        assertEquals("2026-10-06", command.parameters?.get("date")?.asString)
        assertEquals("18:00", command.parameters?.get("time")?.asString)
        assertEquals("Task created.", command.confirmationMessage)
    }

    @Test
    fun testCreateAlarmParsing() {
        val json = """
            {
              "type": "ACTION",
              "action": "CREATE_ALARM",
              "parameters": {
                "title": "Wake Up",
                "date": "2026-10-06",
                "time": "06:00"
              },
              "confirmationMessage": "Alarm created."
            }
        """.trimIndent()

        val command = gson.fromJson(json, AiCommand::class.java)

        assertEquals(AiCommandType.ACTION, command.type)
        assertEquals(AiCommandAction.CREATE_ALARM, command.action)
        assertEquals("Wake Up", command.parameters?.get("title")?.asString)
        assertEquals("06:00", command.parameters?.get("time")?.asString)
        assertEquals("Alarm created.", command.confirmationMessage)
    }

    @Test
    fun testUnknownActionSafelyParses() {
        val json = """
            {
              "type": "ACTION",
              "action": "CREATE_SCHEDULE",
              "confirmationMessage": "I scheduled it."
            }
        """.trimIndent()

        val command = gson.fromJson(json, AiCommand::class.java)

        assertEquals(AiCommandType.ACTION, command.type)
        assertEquals(AiCommandAction.UNKNOWN, command.action)
    }
}
