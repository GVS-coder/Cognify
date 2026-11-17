package com.cognify

import android.app.Application
import com.google.firebase.FirebaseApp
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

@HiltAndroidApp
class CognifyApplication : Application() {
    companion object {
        lateinit var appContext: android.content.Context
    }

    override fun onCreate() {
        super.onCreate()
        appContext = this
        FirebaseApp.initializeApp(this)
        
        // Initialize Timber for logging
        Timber.plant(Timber.DebugTree())
    }
}
