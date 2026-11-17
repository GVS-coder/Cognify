package com.cognify.data.model

data class ReminderSettings(
    val notificationSound: String = "default",
    val vibrationEnabled: Boolean = true,
    val defaultReminderTime: String = "09:00",
    val snoozeDurationMinutes: Int = 15
)
