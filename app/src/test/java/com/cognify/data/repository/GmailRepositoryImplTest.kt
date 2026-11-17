package com.cognify.data.repository

import android.os.Build
import androidx.test.core.app.ApplicationProvider
import com.cognify.data.remote.gmail.GmailApiService
import com.cognify.data.remote.gmail.GmailMessageBody
import com.cognify.data.remote.gmail.GmailMessagePart
import com.cognify.data.remote.gmail.GmailMessagePayload
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Base64

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.O])
class GmailRepositoryImplTest {

    private lateinit var repository: GmailRepositoryImpl

    @Before
    fun setup() {
        val mockService = mock(GmailApiService::class.java)
        repository = GmailRepositoryImpl(ApplicationProvider.getApplicationContext(), mockService)
    }

    private fun encodeBase64(text: String): String {
        return Base64.getUrlEncoder().encodeToString(text.toByteArray())
    }

    @Test
    fun testExtractCleanEmailText_PlainMime() {
        val plainText = "This is a plain text email."
        val payload = GmailMessagePayload(
            mimeType = "text/plain",
            headers = null,
            body = GmailMessageBody(size = plainText.length, data = encodeBase64(plainText)),
            parts = null
        )
        val result = repository.extractCleanEmailText(payload)
        assertTrue(result.contains(plainText))
    }

    @Test
    fun testExtractCleanEmailText_RealisticBrokenHtml() {
        val htmlContent = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
            <meta http-equiv="Content-Type" content="text/html; charset=utf-8" />
            <meta http-equiv="X-UA-Compatible" content="IE=edge" />
            <title>General</title>
            <meta name="author" content="" />
            <meta name="viewport" content="width=device-width, initial-scale=1.0" />
            <meta name="x-apple-disable-message-reformatting">
            <style type="text/css">
            @import url(https://fonts.googleapis.com/css?family=Open+Sans);
            body { background-color: #F6F8FA; }
            </style>
            </head>
            <body>
            <div class="content">
            <p>Here is an internship opportunity.</p>
            </div>
            </body>
            </html>
        """.trimIndent()

        val payload = GmailMessagePayload(
            mimeType = "text/html",
            headers = null,
            body = GmailMessageBody(size = htmlContent.length, data = encodeBase64(htmlContent)),
            parts = null
        )

        val result = repository.extractCleanEmailText(payload)

        // Must not contain any raw HTML metadata or styles
        assertFalse(result.contains("<!DOCTYPE"))
        assertFalse(result.contains("<html"))
        assertFalse(result.contains("<head"))
        assertFalse(result.contains("<style"))
        assertFalse(result.contains("<meta"))
        assertFalse(result.contains("@import"))
        assertFalse(result.contains("background-color"))
        assertFalse(result.contains("font-family"))

        // Must contain the actual text
        assertTrue(result.contains("Here is an internship opportunity."))
    }

    @Test
    fun testExtractCleanEmailText_MultipartAlternative() {
        val plainText = "Plain text part"
        val htmlContent = "<html><body><p>HTML part</p></body></html>"
        
        val payload = GmailMessagePayload(
            mimeType = "multipart/alternative",
            headers = null,
            body = null,
            parts = listOf(
                GmailMessagePart(partId = "1", mimeType = "text/plain", body = GmailMessageBody(size = plainText.length, data = encodeBase64(plainText)), parts = null),
                GmailMessagePart(partId = "2", mimeType = "text/html", body = GmailMessageBody(size = htmlContent.length, data = encodeBase64(htmlContent)), parts = null)
            )
        )
        
        val result = repository.extractCleanEmailText(payload)
        // Should prefer plain text
        assertTrue(result.contains("Plain text part"))
    }

    @Test
    fun testExtractCleanEmailText_NestedMultipart() {
        val htmlContent = "<script>alert(1);</script><html><body><p>Deep nested HTML</p></body></html>"
        val payload = GmailMessagePayload(
            mimeType = "multipart/mixed",
            headers = null,
            body = null,
            parts = listOf(
                GmailMessagePart(
                    partId = "1",
                    mimeType = "multipart/alternative",
                    body = null,
                    parts = listOf(
                        GmailMessagePart(partId = "1.1", mimeType = "text/html", body = GmailMessageBody(size = htmlContent.length, data = encodeBase64(htmlContent)), parts = null)
                    )
                )
            )
        )
        
        val result = repository.extractCleanEmailText(payload)
        assertTrue(result.contains("Deep nested HTML"))
        assertFalse(result.contains("<script>"))
    }

    @Test
    fun testSanitizeEmailText_AllTagsRemoved() {
        val input = """
            <!DOCTYPE html>
            <html>
            <head><title>Test</title></head>
            <body>
            <!-- comment -->
            <svg><path d="M10 10"/></svg>
            <iframe src="test"></iframe>
            <div class="row"><p>Test <b>Bold</b></p></div>
            <br/>
            <ul><li>Item 1</li><li>Item 2</li></ul>
            <table><tr><td>Row 1</td></tr></table>
            </body>
            </html>
        """.trimIndent()
        
        val result = repository.sanitizeEmailText(input)
        
        assertFalse(result.contains("<!DOCTYPE"))
        assertFalse(result.contains("<html>"))
        assertFalse(result.contains("<head>"))
        assertFalse(result.contains("<svg>"))
        assertFalse(result.contains("<iframe>"))
        assertFalse(result.contains("<!--"))
        assertTrue(result.contains("Test"))
        assertTrue(result.contains("Bold"))
        assertTrue(result.contains("Item 1"))
        assertTrue(result.contains("Item 2"))
        assertTrue(result.contains("Row 1"))
    }
}
