package com.cognify.data.model

data class MonthlySummary(
    val month: Int,
    val year: Int,
    val tasksCompleted: Int,
    val knowledgeAdded: Int,
    val remindersFinished: Int,
    val studyStreak: Int,
    val mostActiveCategory: String?
)
