package com.cognify.data.repository

import android.content.Context
import com.cognify.data.model.GmailMessage
import com.cognify.data.model.SmartInboxCategory
import com.cognify.data.remote.gmail.GmailApiService
import com.cognify.data.remote.gmail.GmailHeader
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.util.Base64
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton
import java.text.SimpleDateFormat
import java.util.Locale

@Singleton
class GmailRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gmailApiService: GmailApiService
) : GmailRepository {

    override suspend fun getRecentMessages(
        selectedCategory: SmartInboxCategory?,
        customQuery: String?
    ): Result<List<GmailMessage>> = withContext(Dispatchers.IO) {
        try {
            val account = GoogleSignIn.getLastSignedInAccount(context)
                ?: return@withContext Result.failure(Exception("Gmail account not connected"))
            val androidAccount = account.account
                ?: return@withContext Result.failure(Exception("Could not retrieve account details"))

            // Get access token
            val token = GoogleAuthUtil.getToken(
                context, 
                androidAccount, 
                "oauth2:https://www.googleapis.com/auth/gmail.readonly"
            )

            val authHeader = "Bearer $token"

            // Build Gmail search query based on category
            val query = buildQuery(selectedCategory, customQuery)

            // Fetch message list
            val listResponse = gmailApiService.listMessages(
                authHeader = authHeader,
                query = query,
                maxResults = 50
            )

            val messageIds = listResponse.messages ?: emptyList()
            if (messageIds.isEmpty()) {
                return@withContext Result.success(emptyList())
            }

            // Fetch details for each message
            val messages = messageIds.mapNotNull { msgId ->
                try {
                    val msgDetail = gmailApiService.getMessage(authHeader = authHeader, messageId = msgId.id)
                    parseMessage(msgDetail)
                } catch (e: Exception) {
                    null // Skip failed messages
                }
            }

            // Client-side filtering in case Gmail search is too broad, and multi-category assignment
            val processedMessages = messages.map { msg ->
                val categories = determineCategories(msg, customQuery)
                msg.copy(matchedCategories = categories)
            }.filter { msg ->
                if (selectedCategory == null || selectedCategory == SmartInboxCategory.CUSTOM) {
                    true // If all or custom, show everything returned
                } else {
                    msg.matchedCategories.contains(selectedCategory)
                }
            }

            Result.success(processedMessages)
        } catch (e: HttpException) {
            val code = e.code()
            if (code == 401 || code == 403) {
                Result.failure(Exception("Gmail authorization expired or access denied. Please reconnect."))
            } else {
                Result.failure(Exception("Gmail API error: $code"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getMessageContent(messageId: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val account = GoogleSignIn.getLastSignedInAccount(context)
                ?: return@withContext Result.failure(Exception("Gmail account not connected"))
            val androidAccount = account.account
                ?: return@withContext Result.failure(Exception("Could not retrieve account details"))

            val token = GoogleAuthUtil.getToken(
                context, 
                androidAccount, 
                "oauth2:https://www.googleapis.com/auth/gmail.readonly"
            )

            val authHeader = "Bearer $token"

            val response = gmailApiService.getFullMessage(
                authHeader = authHeader,
                messageId = messageId
            )

            val bodyText = extractCleanEmailText(response.payload)
            if (bodyText.isBlank() || bodyText == "Unable to display the email content.") {
                Result.failure(Exception("Could not extract email body."))
            } else {
                Result.success(bodyText)
            }
        } catch (e: HttpException) {
            val code = e.code()
            if (code == 401 || code == 403) {
                Result.failure(Exception("Gmail authorization expired or access denied. Please reconnect."))
            } else {
                Result.failure(Exception("Gmail API error: $code"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun buildQuery(category: SmartInboxCategory?, customQuery: String?): String {
        // Base query to only look at emails from the last few months to avoid large lookups
        val base = "newer_than:3m "
        if (category == null) return base // All

        if (category == SmartInboxCategory.CUSTOM) {
            return base + (customQuery ?: "")
        }

        // Build OR query from keywords
        val keywords = category.keywords.joinToString(" OR ") { "\"$it\"" }
        return base + "($keywords)"
    }

    private fun determineCategories(message: GmailMessage, customQuery: String?): List<SmartInboxCategory> {
        val searchString = "${message.subject} ${message.snippet}".lowercase(Locale.getDefault())
        val matched = mutableListOf<SmartInboxCategory>()

        for (cat in SmartInboxCategory.entries) {
            if (cat == SmartInboxCategory.CUSTOM) {
                if (!customQuery.isNullOrBlank() && searchString.contains(customQuery.lowercase(Locale.getDefault()))) {
                    matched.add(cat)
                }
                continue
            }

            for (keyword in cat.keywords) {
                if (searchString.contains(keyword.lowercase(Locale.getDefault()))) {
                    matched.add(cat)
                    break // Next category
                }
            }
        }
        return matched
    }

    private fun parseMessage(response: com.cognify.data.remote.gmail.GmailMessageResponse): GmailMessage {
        val headers = response.payload?.headers ?: emptyList()
        val subject = getHeader(headers, "Subject")
        val from = getHeader(headers, "From")
        val dateStr = getHeader(headers, "Date")
        
        // Parse basic sender format "Name <email@domain.com>"
        var senderName = from
        var senderEmail = ""
        val emailStart = from.indexOf("<")
        val emailEnd = from.indexOf(">")
        if (emailStart != -1 && emailEnd != -1) {
            senderName = from.substring(0, emailStart).trim().replace("\"", "")
            senderEmail = from.substring(emailStart + 1, emailEnd).trim()
        }

        val receivedAt = response.internalDate?.toLongOrNull() ?: parseDate(dateStr)
        val isRead = !(response.labelIds?.contains("UNREAD") ?: false)

        return GmailMessage(
            id = response.id,
            threadId = response.threadId,
            senderName = senderName.ifEmpty { senderEmail },
            senderEmail = senderEmail,
            subject = subject,
            snippet = response.snippet ?: "",
            receivedAt = receivedAt,
            isRead = isRead,
            matchedCategories = emptyList() // Will be populated in determineCategories
        )
    }

    private fun getHeader(headers: List<GmailHeader>, name: String): String {
        return headers.firstOrNull { it.name.equals(name, ignoreCase = true) }?.value ?: ""
    }

    private fun parseDate(dateStr: String): Long {
        if (dateStr.isEmpty()) return System.currentTimeMillis()
        return try {
            val format = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US)
            format.parse(dateStr)?.time ?: System.currentTimeMillis()
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }

    fun extractCleanEmailText(payload: com.cognify.data.remote.gmail.GmailMessagePayload?): String {
        if (payload == null) return "Unable to display the email content."

        var textContent: String? = null
        var htmlContent: String? = null

        fun traversePart(mimeType: String?, bodyData: String?, parts: List<com.cognify.data.remote.gmail.GmailMessagePart>?) {
            if (mimeType.equals("text/plain", ignoreCase = true) && !bodyData.isNullOrBlank()) {
                val decoded = decodeBase64Url(bodyData)
                textContent = if (textContent == null) decoded else textContent + "\n" + decoded
            } else if (mimeType.equals("text/html", ignoreCase = true) && !bodyData.isNullOrBlank()) {
                val decoded = decodeBase64Url(bodyData)
                htmlContent = if (htmlContent == null) decoded else htmlContent + "\n" + decoded
            } else if (!parts.isNullOrEmpty()) {
                for (childPart in parts) {
                    traversePart(childPart.mimeType, childPart.body?.data, childPart.parts)
                }
            }
        }

        traversePart(payload.mimeType, payload.body?.data, payload.parts)

        var finalResult = ""

        if (!textContent.isNullOrBlank()) {
            finalResult = textContent!!.trim()
        } else if (!htmlContent.isNullOrBlank()) {
            finalResult = sanitizeEmailText(htmlContent!!)
        }

        if (finalResult.isBlank()) {
            return "Unable to display the email content."
        }

        // Final Safety Check
        val lowerCaseResult = finalResult.lowercase(Locale.getDefault())
        val indicators = listOf("<!doctype", "<html", "<head", "<body", "<style", "<script", "<meta", "@import", "font-family:", "background-color:", "-webkit-", "</style>", "</head>")
        
        var needsReSanitization = false
        for (indicator in indicators) {
            if (lowerCaseResult.contains(indicator)) {
                needsReSanitization = true
                break
            }
        }

        if (needsReSanitization) {
            finalResult = sanitizeEmailText(finalResult)
        }

        return finalResult.trim().ifEmpty { "Unable to display the email content." }
    }

    private fun decodeBase64Url(data: String): String {
        return try {
            val bytes = android.util.Base64.decode(data, android.util.Base64.URL_SAFE)
            String(bytes, Charsets.UTF_8)
        } catch (e: IllegalArgumentException) {
            try {
                val bytes = android.util.Base64.decode(data, android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING)
                String(bytes, Charsets.UTF_8)
            } catch (e2: Exception) {
                ""
            }
        } catch (e: Exception) {
            ""
        }
    }

    fun sanitizeEmailText(raw: String): String {
        var cleanHtml = raw
        
        // Remove DOCTYPE
        cleanHtml = cleanHtml.replace(Regex("<!DOCTYPE[^>]*>", RegexOption.IGNORE_CASE), "")

        // Remove completely (including content inside)
        val tagsToRemove = listOf("head", "style", "script", "noscript", "svg", "iframe")
        for (tag in tagsToRemove) {
            cleanHtml = cleanHtml.replace(Regex("<$tag[^>]*>.*?</$tag>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)), "")
        }
        
        // Remove HTML comments
        cleanHtml = cleanHtml.replace(Regex("<!--.*?-->", setOf(RegexOption.DOT_MATCHES_ALL)), "")

        // Add newlines for block elements
        cleanHtml = cleanHtml.replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
        cleanHtml = cleanHtml.replace(Regex("</div>", RegexOption.IGNORE_CASE), "\n")
        cleanHtml = cleanHtml.replace(Regex("</p>", RegexOption.IGNORE_CASE), "\n")
        cleanHtml = cleanHtml.replace(Regex("</li>", RegexOption.IGNORE_CASE), "\n")
        cleanHtml = cleanHtml.replace(Regex("</tr>", RegexOption.IGNORE_CASE), "\n")

        // Remove all remaining HTML tags
        cleanHtml = cleanHtml.replace(Regex("<[^>]+>"), "")

        // Decode HTML entities
        val spanned = android.text.Html.fromHtml(cleanHtml, android.text.Html.FROM_HTML_MODE_LEGACY)
        var result = spanned.toString()
        
        // Normalize whitespace
        result = result.replace(Regex("[ \\t]+"), " ")
        result = result.replace(Regex("\\n\\s+\\n"), "\n\n")
        result = result.replace(Regex("\\n{3,}"), "\n\n")
        
        return result.trim()
    }
}
