package com.cognify.ui.smartinbox

import com.cognify.data.model.EmailAnalysisResult
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class EmailAnalysisMapperTest {

    @Test
    fun testParseFlexibleDate_ValidDate() {
        val dateStr = "Oct 10, 2026"
        val timestamp = EmailAnalysisMapper.parseFlexibleDate(dateStr)
        
        val calendar = Calendar.getInstance().apply {
            timeInMillis = timestamp
        }
        assertEquals(2026, calendar.get(Calendar.YEAR))
        assertEquals(Calendar.OCTOBER, calendar.get(Calendar.MONTH))
        assertEquals(10, calendar.get(Calendar.DAY_OF_MONTH))
    }
    
    @Test
    fun testParseFlexibleDate_ValidDate2() {
        val dateStr = "2026-10-10"
        val timestamp = EmailAnalysisMapper.parseFlexibleDate(dateStr)
        
        val calendar = Calendar.getInstance().apply {
            timeInMillis = timestamp
        }
        assertEquals(2026, calendar.get(Calendar.YEAR))
        assertEquals(Calendar.OCTOBER, calendar.get(Calendar.MONTH))
        assertEquals(10, calendar.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun testParseFlexibleDate_MalformedDate_ReturnsMinusOne() {
        val dateStr = "Not A Real Date"
        val timestamp = EmailAnalysisMapper.parseFlexibleDate(dateStr)
        assertEquals(-1L, timestamp)
    }

    @Test
    fun testMapToTaskPrefill_WithValidDeadline() {
        val result = EmailAnalysisResult(
            type = "HACKATHON",
            title = "Global Hackathon 2026",
            organization = "Tech Corp",
            dateTime = "Oct 25, 2026",
            deadline = "2026-10-10",
            location = "Online",
            summary = "An amazing hackathon.",
            actionItems = listOf("Register online", "Form a team")
        )

        val prefill = EmailAnalysisMapper.mapToTaskPrefill(result)
        
        assertEquals("Global Hackathon 2026", prefill["prefillTitle"])
        val expectedDescription = "Org: Tech Corp\nDate: Oct 25, 2026\nSummary: An amazing hackathon.\nActions:\n- Register online\n- Form a team"
        assertEquals(expectedDescription, prefill["prefillDescription"])
        
        val timestamp = prefill["prefillDueDate"] as Long
        val calendar = Calendar.getInstance().apply { timeInMillis = timestamp }
        assertEquals(2026, calendar.get(Calendar.YEAR))
        assertEquals(Calendar.OCTOBER, calendar.get(Calendar.MONTH))
        assertEquals(10, calendar.get(Calendar.DAY_OF_MONTH))
    }
    
    @Test
    fun testMapToTaskPrefill_WithoutDeadline() {
        val result = EmailAnalysisResult(
            type = "SEMINAR",
            title = "Tech Seminar",
            organization = "University",
            dateTime = "2026-11-01",
            deadline = null,
            location = "Auditorium",
            summary = "A seminar on tech.",
            actionItems = null
        )

        val prefill = EmailAnalysisMapper.mapToTaskPrefill(result)
        
        assertEquals("Tech Seminar", prefill["prefillTitle"])
        assertEquals(-1L, prefill["prefillDueDate"])
    }

    @Test
    fun testMapToReminderPrefill_WithDateTime() {
        val result = EmailAnalysisResult(
            type = "EVENT",
            title = "Code Meetup",
            organization = "Developers",
            dateTime = "10/20/2026",
            deadline = "10/10/2026",
            location = "Office",
            summary = "Weekly meetup",
            actionItems = null
        )

        val prefill = EmailAnalysisMapper.mapToReminderPrefill(result)
        
        assertEquals("Code Meetup", prefill["prefillTitle"])
        val expectedDescription = "Org: Developers\nSummary: Weekly meetup"
        assertEquals(expectedDescription, prefill["prefillDescription"])
        
        val timestamp = prefill["prefillDateTime"] as Long
        val calendar = Calendar.getInstance().apply { timeInMillis = timestamp }
        assertEquals(2026, calendar.get(Calendar.YEAR))
        assertEquals(Calendar.OCTOBER, calendar.get(Calendar.MONTH))
        assertEquals(20, calendar.get(Calendar.DAY_OF_MONTH))
    }
    
    @Test
    fun testMapToReminderPrefill_WithoutDateTime_UsesDeadline() {
        val result = EmailAnalysisResult(
            type = "EVENT",
            title = "Code Meetup",
            organization = null,
            dateTime = null,
            deadline = "10/10/2026",
            location = "Office",
            summary = null,
            actionItems = null
        )

        val prefill = EmailAnalysisMapper.mapToReminderPrefill(result)
        
        assertEquals("Code Meetup", prefill["prefillTitle"])
        assertEquals("", prefill["prefillDescription"])
        
        val timestamp = prefill["prefillDateTime"] as Long
        val calendar = Calendar.getInstance().apply { timeInMillis = timestamp }
        assertEquals(2026, calendar.get(Calendar.YEAR))
        assertEquals(Calendar.OCTOBER, calendar.get(Calendar.MONTH))
        assertEquals(10, calendar.get(Calendar.DAY_OF_MONTH))
    }
    
    @Test
    fun testMapToReminderPrefill_MissingOptionalFields() {
        val result = EmailAnalysisResult(
            type = null,
            title = null,
            organization = null,
            dateTime = null,
            deadline = null,
            location = null,
            summary = null,
            actionItems = null
        )

        val prefill = EmailAnalysisMapper.mapToReminderPrefill(result)
        
        assertEquals("New Reminder", prefill["prefillTitle"])
        assertEquals("", prefill["prefillDescription"])
        assertEquals(-1L, prefill["prefillDateTime"])
    }
}
