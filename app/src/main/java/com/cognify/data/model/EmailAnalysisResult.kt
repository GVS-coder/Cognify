package com.cognify.data.model

import com.google.gson.annotations.SerializedName

data class EmailAnalysisResult(
    @SerializedName("type")
    val type: String?, // e.g., "INTERNSHIP", "HACKATHON", "SEMINAR", "PLACEMENT", "COLLEGE_EVENT", "DEADLINE"
    
    @SerializedName("title")
    val title: String?, // Short clear title of the opportunity
    
    @SerializedName("organization")
    val organization: String?, // Company or college hosting it
    
    @SerializedName("date_time")
    val dateTime: String?, // When it happens
    
    @SerializedName("deadline")
    val deadline: String?, // Application deadline if any
    
    @SerializedName("location")
    val location: String?, // Physical location or "Online"
    
    @SerializedName("action_items")
    val actionItems: List<String>?, // What the user needs to do
    
    @SerializedName("summary")
    val summary: String? // Brief 1-2 sentence summary
)
