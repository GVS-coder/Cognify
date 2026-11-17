package com.cognify.data.model

data class GmailMessage(
    val id: String,
    val threadId: String,
    val senderName: String,
    val senderEmail: String,
    val subject: String,
    val snippet: String,
    val receivedAt: Long, // timestamp
    val isRead: Boolean,
    val matchedCategories: List<SmartInboxCategory>
)

enum class SmartInboxCategory(val displayName: String, val keywords: List<String>) {
    INTERNSHIPS("Internships", listOf("internship", "intern", "interns", "summer internship", "industrial training")),
    PLACEMENTS("Placements", listOf("placement", "campus placement", "campus drive", "recruitment", "hiring", "job opportunity", "career")),
    HACKATHONS("Hackathons", listOf("hackathon", "hack", "coding contest", "innovation challenge")),
    WORKSHOPS("Workshops", listOf("workshop", "hands-on workshop", "training program")),
    SYMPOSIUMS("Symposiums", listOf("symposium")),
    SEMINARS("Seminars", listOf("seminar", "webinar", "guest lecture")),
    COLLEGE_EVENTS("College Events", listOf("college event", "department event", "technical event", "cultural event", "fest", "conference")),
    COMPANY_OPPORTUNITIES("Company Opportunities", listOf("hiring", "recruitment", "career opportunity", "job opening", "walk-in", "off campus")),
    IMPORTANT_DEADLINES("Important Deadlines", listOf("deadline", "last date", "apply before", "registration closes", "application closes", "due date")),
    CUSTOM("Custom", listOf()) // Handled specially
}
