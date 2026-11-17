package com.cognify.data.remote.gmail

import com.google.gson.annotations.SerializedName

data class GmailListResponse(
    @SerializedName("messages") val messages: List<GmailMessageId>?,
    @SerializedName("nextPageToken") val nextPageToken: String?,
    @SerializedName("resultSizeEstimate") val resultSizeEstimate: Int?
)

data class GmailMessageId(
    @SerializedName("id") val id: String,
    @SerializedName("threadId") val threadId: String
)

data class GmailMessageResponse(
    @SerializedName("id") val id: String,
    @SerializedName("threadId") val threadId: String,
    @SerializedName("labelIds") val labelIds: List<String>?,
    @SerializedName("snippet") val snippet: String?,
    @SerializedName("payload") val payload: GmailMessagePayload?,
    @SerializedName("internalDate") val internalDate: String?
)

data class GmailMessagePayload(
    @SerializedName("mimeType") val mimeType: String?,
    @SerializedName("headers") val headers: List<GmailHeader>?,
    @SerializedName("body") val body: GmailMessageBody?,
    @SerializedName("parts") val parts: List<GmailMessagePart>?
)

data class GmailMessagePart(
    @SerializedName("partId") val partId: String?,
    @SerializedName("mimeType") val mimeType: String?,
    @SerializedName("body") val body: GmailMessageBody?,
    @SerializedName("parts") val parts: List<GmailMessagePart>?
)

data class GmailMessageBody(
    @SerializedName("size") val size: Int?,
    @SerializedName("data") val data: String?
)

data class GmailHeader(
    @SerializedName("name") val name: String,
    @SerializedName("value") val value: String
)
