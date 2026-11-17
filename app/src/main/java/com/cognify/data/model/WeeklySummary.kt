package com.cognify.data.model

data class WeeklySummary(
    val weekNumber: Int,
    val year: Int,
    val tasksCompleted: Int,
    val knowledgeAdded: Int,
    val remindersFinished: Int,
    val studyStreak: Int,
    val mostActiveCategory: String?
)
