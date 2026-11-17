package com.cognify.ai

import com.cognify.data.model.AiCommandAction
import com.cognify.data.model.AiCommandType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class AiCommandParserTest {

    @Test
    fun testNormalTextJson() {
        val json = """
            {
              "type": "TEXT",
              "confirmationMessage": "This is a normal conversation response."
            }
        """.trimIndent()
        val command = AiCommandParser.parse(json)
        assertNotNull(command)
        assertEquals(AiCommandType.TEXT, command?.type)
        assertEquals("This is a normal conversation response.", command?.confirmationMessage)
    }

    @Test
    fun testCreateNote() {
        val json = """
            {
              "type": "ACTION",
              "action": "CREATE_NOTE",
              "parameters": {
                "title": "Professional",
                "content": "I am a student"
              },
              "confirmationMessage": "I can create the note."
            }
        """.trimIndent()
        val command = AiCommandParser.parse(json)
        assertNotNull(command)
        assertEquals(AiCommandType.ACTION, command?.type)
        assertEquals(AiCommandAction.CREATE_NOTE, command?.action)
        assertEquals("Professional", command?.parameters?.get("title")?.asString)
        assertEquals("I am a student", command?.parameters?.get("content")?.asString)
    }

    @Test
    fun testCreateTask() {
        val json = """
            {
              "type": "ACTION",
              "action": "CREATE_TASK",
              "parameters": {
                "title": "Study Java",
                "date": "2023-11-05",
                "time": "18:00"
              },
              "confirmationMessage": "Task created."
            }
        """.trimIndent()
        val command = AiCommandParser.parse(json)
        assertNotNull(command)
        assertEquals(AiCommandType.ACTION, command?.type)
        assertEquals(AiCommandAction.CREATE_TASK, command?.action)
        assertEquals("Study Java", command?.parameters?.get("title")?.asString)
    }

    @Test
    fun testCreateReminder() {
        val json = """
            {
              "type": "ACTION",
              "action": "CREATE_REMINDER",
              "parameters": {
                "title": "Buy milk"
              },
              "confirmationMessage": "Reminder set."
            }
        """.trimIndent()
        val command = AiCommandParser.parse(json)
        assertNotNull(command)
        assertEquals(AiCommandType.ACTION, command?.type)
        assertEquals(AiCommandAction.CREATE_REMINDER, command?.action)
    }

    @Test
    fun testCreateAlarm() {
        val json = """
            {
              "type": "ACTION",
              "action": "CREATE_ALARM",
              "parameters": {
                "time": "07:00"
              },
              "confirmationMessage": "Alarm set."
            }
        """.trimIndent()
        val command = AiCommandParser.parse(json)
        assertNotNull(command)
        assertEquals(AiCommandType.ACTION, command?.type)
        assertEquals(AiCommandAction.CREATE_ALARM, command?.action)
    }

    @Test
    fun testUpdateTask() {
        val json = """
            {
              "type": "ACTION",
              "action": "UPDATE_TASK",
              "parameters": {
                "title": "Java",
                "date": "2023-11-06"
              },
              "confirmationMessage": "Task updated."
            }
        """.trimIndent()
        val command = AiCommandParser.parse(json)
        assertNotNull(command)
        assertEquals(AiCommandType.ACTION, command?.type)
        assertEquals(AiCommandAction.UPDATE_TASK, command?.action)
    }

    @Test
    fun testDeleteNote() {
        val json = """
            {
              "type": "ACTION",
              "action": "DELETE_NOTE",
              "parameters": {
                "title": "Professional"
              },
              "confirmationMessage": "Do you want to delete this?"
            }
        """.trimIndent()
        val command = AiCommandParser.parse(json)
        assertNotNull(command)
        assertEquals(AiCommandType.ACTION, command?.type)
        assertEquals(AiCommandAction.DELETE_NOTE, command?.action)
    }

    @Test
    fun testMarkdownWrappedJson() {
        val json = """
            ```json
            {
              "type": "ACTION",
              "action": "CREATE_NOTE",
              "confirmationMessage": "Unwrapped."
            }
            ```
        """.trimIndent()
        val command = AiCommandParser.parse(json)
        assertNotNull(command)
        assertEquals(AiCommandType.ACTION, command?.type)
        assertEquals(AiCommandAction.CREATE_NOTE, command?.action)
    }

    @Test
    fun testWhitespaceAroundJson() {
        val json = "   \n  { \"type\": \"TEXT\", \"confirmationMessage\": \"Whitespace test\" } \n  "
        val command = AiCommandParser.parse(json)
        assertNotNull(command)
        assertEquals(AiCommandType.TEXT, command?.type)
        assertEquals("Whitespace test", command?.confirmationMessage)
    }

    @Test
    fun testMalformedJson() {
        val json = """
            {
              "type": "ACTION",
              "action": "CREATE_NOTE"
              // missing comma and closing braces
        """.trimIndent()
        val command = AiCommandParser.parse(json)
        assertNotNull(command)
        assertEquals(AiCommandType.TEXT, command?.type)
        assertEquals(json, command?.confirmationMessage)
    }

    @Test
    fun testUnknownAction() {
        val json = """
            {
              "type": "ACTION",
              "action": "CREATE_SCHEDULE"
            }
        """.trimIndent()
        val command = AiCommandParser.parse(json)
        assertNotNull(command)
        assertEquals(AiCommandType.ACTION, command?.type)
        assertEquals(AiCommandAction.UNKNOWN, command?.action)
    }

    @Test
    fun testUnknownType() {
        val json = """
            {
              "type": "MAGIC"
            }
        """.trimIndent()
        val command = AiCommandParser.parse(json)
        assertNotNull(command)
        assertEquals(AiCommandType.UNKNOWN, command?.type)
    }

    @Test
    fun testEmptyResponse() {
        val command1 = AiCommandParser.parse("")
        assertNull(command1)
        val command2 = AiCommandParser.parse("   ")
        assertNull(command2)
        val command3 = AiCommandParser.parse(null)
        assertNull(command3)
    }

    @Test
    fun testNormalNonJsonAIResponse() {
        val response = "Java is an object-oriented programming language..."
        val command = AiCommandParser.parse(response)
        assertNotNull(command)
        assertEquals(AiCommandType.TEXT, command?.type)
        assertEquals(response, command?.confirmationMessage)
    }
}
