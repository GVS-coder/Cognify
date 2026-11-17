package com.cognify.ui.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cognify.R
import com.cognify.data.local.AnalyticsEventEntity
import com.cognify.data.local.AnalyticsEventType
import com.cognify.data.local.KnowledgeEntity
import com.cognify.data.local.ReminderEntity
import com.cognify.data.local.TaskEntity
import com.cognify.data.model.TaskStatus
import com.cognify.data.repository.AnalyticsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.util.Calendar
import javax.inject.Inject

@HiltViewModel
class AnalyticsViewModel @Inject constructor(
    private val analyticsRepository: AnalyticsRepository
) : ViewModel() {

    val uiState: StateFlow<AnalyticsUiState> = combine(
        analyticsRepository.allTasks,
        analyticsRepository.allKnowledge,
        analyticsRepository.allReminders,
        analyticsRepository.allEvents
    ) { tasks, knowledge, reminders, events ->
        
        val now = Calendar.getInstance()
        val todayStart = getStartOfDay(now)
        val yesterdayStart = todayStart - 86400000L

        // Productivity %
        val todayTasks = tasks.filter { it.dueDate != null && it.dueDate!! >= todayStart && it.dueDate!! < (todayStart + 86400000L) }
        val completedToday = todayTasks.count { it.status == TaskStatus.COMPLETED }
        val productivityPercentage = if (todayTasks.isNotEmpty()) (completedToday * 100) / todayTasks.size else 0

        // Tasks Completed & Knowledge Entries (Total)
        val tasksCompleted = tasks.count { it.status == TaskStatus.COMPLETED }
        val knowledgeEntries = knowledge.size

        // Study Streak (Simplistic approach: unique days with activity)
        val studyStreak = calculateStreak(events)

        // AI Insight
        val aiInsight = generateInsight(tasks, knowledge, events, todayStart)

        // Timeline Items
        val timelineItems = buildTimeline(events, tasks, knowledge, reminders, todayStart, yesterdayStart)

        // Chart Data
        val weeklyTasksData = calculateWeeklyTasks(tasks)
        val monthlyTasksData = calculateMonthlyTasks(tasks)
        val knowledgeGrowthData = calculateKnowledgeGrowth(knowledge)
        val studyActivityData = calculateStudyActivity(events)

        AnalyticsUiState.Success(
            productivityPercentage = productivityPercentage,
            studyStreak = studyStreak,
            tasksCompleted = tasksCompleted,
            knowledgeEntries = knowledgeEntries,
            aiInsight = aiInsight,
            weeklyTasksData = weeklyTasksData,
            monthlyTasksData = monthlyTasksData,
            knowledgeGrowthData = knowledgeGrowthData,
            studyActivityData = studyActivityData,
            timelineItems = timelineItems
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AnalyticsUiState.Loading)

    private fun getStartOfDay(cal: Calendar): Long {
        val c = cal.clone() as Calendar
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    private fun calculateStreak(events: List<AnalyticsEventEntity>): Int {
        // Simplified streak: just return 1 for now if there is activity today.
        // A real implementation would iterate backwards day by day.
        val todayStart = getStartOfDay(Calendar.getInstance())
        val hasActivityToday = events.any { it.eventTime >= todayStart }
        return if (hasActivityToday) 1 else 0
    }

    private fun generateInsight(
        tasks: List<TaskEntity>, 
        knowledge: List<KnowledgeEntity>, 
        events: List<AnalyticsEventEntity>,
        todayStart: Long
    ): String {
        val weekStart = todayStart - (7 * 86400000L)
        val completedThisWeek = tasks.count { it.status == TaskStatus.COMPLETED && it.updatedAt >= weekStart }
        return completedThisWeek.toString()
    }

    private fun buildTimeline(
        events: List<AnalyticsEventEntity>,
        tasks: List<TaskEntity>,
        knowledge: List<KnowledgeEntity>,
        reminders: List<ReminderEntity>,
        todayStart: Long,
        yesterdayStart: Long
    ): List<TimelineItem> {
        return events.map { event ->
            val group = when {
                event.eventTime >= todayStart -> TimelineGroup.TODAY
                event.eventTime >= yesterdayStart -> TimelineGroup.YESTERDAY
                else -> TimelineGroup.EARLIER
            }
            val titleRes = when(event.type) {
                AnalyticsEventType.TASK_CREATED -> com.cognify.R.string.ui_task_created
                AnalyticsEventType.TASK_COMPLETED -> com.cognify.R.string.ui_task_completed
                AnalyticsEventType.KNOWLEDGE_ADDED -> com.cognify.R.string.ui_knowledge_added
                AnalyticsEventType.KNOWLEDGE_VIEWED -> com.cognify.R.string.ui_knowledge_viewed
                AnalyticsEventType.REMINDER_COMPLETED -> com.cognify.R.string.ui_reminder_completed
                AnalyticsEventType.AI_CHAT_MESSAGE -> com.cognify.R.string.ui_ai_chat
                AnalyticsEventType.SEARCH_QUERY -> com.cognify.R.string.ui_searched
            }
            val descRes = if (event.metadata == null && event.referenceId == null) com.cognify.R.string.ui_activity_logged else null
            val title = ""
            val description = event.metadata ?: event.referenceId ?: ""
            val icon = android.R.drawable.ic_menu_info_details // Placeholder icon
            
            TimelineItem(
                id = event.id,
                title = title,
                description = description,
                timestamp = event.eventTime,
                group = group,
                typeIconRes = icon,
                titleRes = titleRes,
                descRes = descRes
            )
        }.sortedByDescending { it.timestamp }
    }

    private fun calculateWeeklyTasks(tasks: List<TaskEntity>): List<Pair<String, Int>> {
        val format = java.text.SimpleDateFormat("EEE", java.util.Locale.getDefault())
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
        
        val days = mutableListOf<Pair<String, Int>>()
        for (i in 0..6) {
            val dayStart = cal.timeInMillis
            val dayEnd = dayStart + 86400000L - 1
            val dayLabel = format.format(cal.time)
            val count = tasks.count {
                it.status == TaskStatus.COMPLETED &&
                it.updatedAt >= dayStart &&
                it.updatedAt <= dayEnd
            }
            days.add(Pair(dayLabel, count))
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return days
    }

    private fun calculateMonthlyTasks(tasks: List<TaskEntity>): List<Pair<String, Int>> = emptyList()
    private fun calculateKnowledgeGrowth(knowledge: List<KnowledgeEntity>): List<Pair<String, Int>> = emptyList()
    private fun calculateStudyActivity(events: List<AnalyticsEventEntity>): List<Pair<String, Int>> = emptyList()
}
