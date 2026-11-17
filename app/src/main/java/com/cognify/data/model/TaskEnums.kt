package com.cognify.data.model

enum class TaskPriority {
    LOW,
    MEDIUM,
    HIGH
}

enum class TaskStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED
}

enum class SyncStatus {
    PENDING,
    SYNCED,
    FAILED
}
