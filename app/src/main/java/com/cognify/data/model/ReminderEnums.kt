package com.cognify.data.model

enum class ReminderType {
    GENERAL,
    TASK_DEADLINE,
    BIRTHDAY,
    EVENT
}

enum class RepeatInterval(val displayName: String) {
    NONE("Does not repeat"),
    DAILY("Daily"),
    WEEKLY("Weekly"),
    MONTHLY("Monthly"),
    YEARLY("Yearly")
}

enum class ReminderStatus {
    PENDING,
    SCHEDULED,
    COMPLETED,
    MISSED,
    CANCELLED
}

enum class ReminderPriority {
    LOW,
    MEDIUM,
    HIGH
}

enum class LinkedItemType {
    TASK,
    KNOWLEDGE,
    EVENT,
    NONE
}
