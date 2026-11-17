package com.cognify.util

import android.content.Context
import com.cognify.R
import com.cognify.data.model.SmartInboxCategory

object LocalizationUtils {
    fun getCategoryName(context: Context, category: SmartInboxCategory): String {
        return when (category.name) {
            "INTERNSHIPS" -> context.getString(R.string.ui_cat_internships)
            "PLACEMENTS" -> context.getString(R.string.ui_cat_placements)
            "HACKATHONS" -> context.getString(R.string.ui_cat_hackathons)
            "WORKSHOPS" -> context.getString(R.string.ui_cat_workshops)
            "SYMPOSIUMS" -> context.getString(R.string.ui_cat_symposiums)
            "SEMINARS" -> context.getString(R.string.ui_cat_seminars)
            "COLLEGE_EVENTS" -> context.getString(R.string.ui_cat_college_events)
            "COMPANY_OPPORTUNITIES" -> context.getString(R.string.ui_cat_company_opportunities)
            "IMPORTANT_DEADLINES" -> context.getString(R.string.ui_cat_important_deadlines)
            "CUSTOM" -> context.getString(R.string.ui_cat_custom)
            else -> category.displayName
        }
    }
}
