package com.cognify.data.remote.gmail

import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query

interface GmailApiService {
    
    @GET("gmail/v1/users/{userId}/messages")
    suspend fun listMessages(
        @Header("Authorization") authHeader: String,
        @Path("userId") userId: String = "me",
        @Query("q") query: String? = null,
        @Query("maxResults") maxResults: Int = 50,
        @Query("pageToken") pageToken: String? = null
    ): GmailListResponse

    @GET("gmail/v1/users/{userId}/messages/{id}")
    suspend fun getMessage(
        @Header("Authorization") authHeader: String,
        @Path("userId") userId: String = "me",
        @Path("id") messageId: String,
        @Query("format") format: String = "metadata",
        @Query("metadataHeaders") metadataHeaders: List<String> = listOf("From", "Subject", "Date")
    ): GmailMessageResponse

    @GET("gmail/v1/users/{userId}/messages/{id}")
    suspend fun getFullMessage(
        @Header("Authorization") authHeader: String,
        @Path("userId") userId: String = "me",
        @Path("id") messageId: String,
        @Query("format") format: String = "full"
    ): GmailMessageResponse
}
