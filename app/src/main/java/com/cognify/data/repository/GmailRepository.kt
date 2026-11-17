package com.cognify.data.repository

import com.cognify.data.model.GmailMessage
import com.cognify.data.model.SmartInboxCategory

interface GmailRepository {
    suspend fun getRecentMessages(selectedCategory: SmartInboxCategory?, customQuery: String?): Result<List<GmailMessage>>
    suspend fun getMessageContent(messageId: String): Result<String>
}
