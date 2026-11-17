package com.cognify.util

import com.cognify.R
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import java.util.Locale

object SpeechToTextHelper {

    fun launchSpeechInput(
        context: Context,
        launcher: ActivityResultLauncher<Intent>,
        promptMessage: String = "Speak to Cognify AI..."
    ) {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, promptMessage)
        }

        try {
            launcher.launch(intent)
        } catch (e: Exception) {
            Toast.makeText(context, context.getString(R.string.speech_not_supported), Toast.LENGTH_SHORT).show()
        }
    }

    fun handleSpeechResult(resultCode: Int, data: Intent?, onResult: (String) -> Unit) {
        if (resultCode == Activity.RESULT_OK && data != null) {
            val results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spokenText = results?.firstOrNull()
            if (!spokenText.isNullOrBlank()) {
                onResult(spokenText)
            }
        }
    }
}
