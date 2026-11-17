package com.cognify.ui.analytics

sealed class AnalyticsUiState {
    object Loading : AnalyticsUiState()
    data class Success(
        val productivityPercentage: Int,
        val studyStreak: Int,
        val tasksCompleted: Int,
        val knowledgeEntries: Int,
        
        val aiInsight: String,
        
        // Chart Data (simplified for View to plot)
        val weeklyTasksData: List<Pair<String, Int>>, // e.g. Mon -> 5
        val monthlyTasksData: List<Pair<String, Int>>, 
        val knowledgeGrowthData: List<Pair<String, Int>>,
        val studyActivityData: List<Pair<String, Int>>,

        val timelineItems: List<TimelineItem>
    ) : AnalyticsUiState()
    data class Error(val message: String) : AnalyticsUiState()
}

enum class TimelineGroup {
    TODAY, YESTERDAY, EARLIER
}

data class TimelineItem(
    val id: String,
    val title: String,
    val description: String,
    val timestamp: Long,
    val group: TimelineGroup,
    val typeIconRes: Int,
    val titleRes: Int? = null,
    val descRes: Int? = null
)
