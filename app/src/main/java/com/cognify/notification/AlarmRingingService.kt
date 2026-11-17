package com.cognify.notification

import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.cognify.data.local.AppDatabase
import com.cognify.R

class AlarmRingingService : Service() {

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null
    
    private val timeoutHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var timeoutRunnable: Runnable? = null

    override fun onCreate() {
        super.onCreate()
        Log.d("AlarmRingingService", "Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_ALARM) {
            stopSelf()
            return START_NOT_STICKY
        }

        val reminderId = intent?.getStringExtra(NotificationHelper.EXTRA_REMINDER_ID)
        if (reminderId == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        acquireWakeLock()

        CoroutineScope(Dispatchers.IO).launch {
            val db = AppDatabase.getDatabase(applicationContext)
            val userId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: ""
            val reminder = db.reminderDao().getReminderById(reminderId, userId)

            if (reminder == null) {
                withContext(Dispatchers.Main) { stopSelf() }
                return@launch
            }

            val notification = NotificationHelper.buildAlarmNotification(applicationContext, reminder)
            
            withContext(Dispatchers.Main) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(reminder.notificationId, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
                } else {
                    startForeground(reminder.notificationId, notification)
                }
                
                var playSound = true
                var playVibrate = true
                
                // Fallback for json config if fields are not present
                try {
                    if (reminder.description.startsWith("{")) {
                        val json = org.json.JSONObject(reminder.description)
                        playSound = json.optBoolean("sound", true)
                        playVibrate = json.optBoolean("vibrate", true)
                    }
                } catch (e: Exception) {}

                // Override with new database fields if applicable
                playSound = reminder.isEnabled && playSound
                if (reminder.soundId == "silent") {
                    playSound = false
                }
                playVibrate = reminder.isVibrationEnabled

                startRinging(playSound, playVibrate, reminder)
            }
        }

        return START_STICKY
    }

    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "Cognify::AlarmWakeLock"
            )
            wakeLock?.acquire(10 * 60 * 1000L /*10 minutes*/)
        }
    }

    private fun releaseWakeLock() {
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
    }

    private fun startRinging(playSound: Boolean, playVibrate: Boolean, reminder: com.cognify.data.local.ReminderEntity) {
        val soundId = reminder.soundId
        Log.d("AlarmRingingService", "ALARM_AUDIO: alarm triggered, soundId=$soundId")
        
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
        val alarmVolume = audioManager.getStreamVolume(android.media.AudioManager.STREAM_ALARM)
        val alarmMaxVolume = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_ALARM)
        Log.d("AlarmRingingService", "ALARM_AUDIO: alarmVolume=$alarmVolume, alarmMaxVolume=$alarmMaxVolume")

        if (playSound && soundId != "silent") {
            try {
                mediaPlayer = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    isLooping = true
                }
                Log.d("AlarmRingingService", "ALARM_AUDIO: playerCreated=true, AudioAttributes configured")

                var sourceSet = false
                
                if (soundId == "custom" && !reminder.customSoundUri.isNullOrEmpty()) {
                    Log.d("AlarmRingingService", "ALARM_AUDIO: sound type selected = custom URI")
                    try {
                        val uri = android.net.Uri.parse(reminder.customSoundUri)
                        val pfd = contentResolver.openFileDescriptor(uri, "r")
                        if (pfd != null) {
                            mediaPlayer?.setDataSource(pfd.fileDescriptor)
                            pfd.close()
                            sourceSet = true
                            Log.d("AlarmRingingService", "ALARM_AUDIO: custom audio successfully loaded")
                        } else {
                            Log.e("AlarmRingingService", "ALARM_AUDIO: custom audio FileDescriptor is null")
                        }
                    } catch (e: Exception) {
                        Log.e("AlarmRingingService", "ALARM_AUDIO: custom audio preparation error", e)
                    }
                }

                if (!sourceSet && (soundId == "alarm_default" || soundId == "custom")) {
                    Log.d("AlarmRingingService", "ALARM_AUDIO: sound type selected = system default fallback")
                    var uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    if (uri == null) uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                    if (uri == null) uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                    
                    if (uri != null) {
                        try {
                            mediaPlayer?.setDataSource(applicationContext, uri)
                            sourceSet = true
                            Log.d("AlarmRingingService", "ALARM_AUDIO: resource/file successfully loaded from RingtoneManager URI")
                        } catch (e: Exception) {
                            Log.e("AlarmRingingService", "ALARM_AUDIO: MediaPlayer preparation error for default URI", e)
                        }
                    }
                }
                
                if (!sourceSet) {
                    val rawName = if (soundId.isEmpty() || soundId == "custom") "alarm_default" else soundId
                    Log.d("AlarmRingingService", "ALARM_AUDIO: sound type selected = bundled raw resource ($rawName)")
                    val resId = when (rawName) {
                        "alarm_beep" -> R.raw.alarm_beep
                        "alarm_classic" -> R.raw.alarm_classic
                        "alarm_default" -> R.raw.alarm_default
                        "alarm_digital" -> R.raw.alarm_digital
                        "alarm_gentle" -> R.raw.alarm_gentle
                        else -> 0
                    }
                    Log.d("AlarmRingingService", "ALARM_AUDIO: resourceId=$resId")
                    
                    if (resId != 0) {
                        val afd = resources.openRawResourceFd(resId)
                        if (afd != null) {
                            mediaPlayer?.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                            afd.close()
                            sourceSet = true
                            Log.d("AlarmRingingService", "ALARM_AUDIO: resource/file successfully loaded from RAW")
                        } else {
                            Log.e("AlarmRingingService", "ALARM_AUDIO: resource decode error (afd is null)")
                        }
                    } else {
                        Log.e("AlarmRingingService", "ALARM_AUDIO: resource decode error (resId is 0)")
                    }
                }

                if (sourceSet) {
                    mediaPlayer?.prepare()
                    mediaPlayer?.start()
                    Log.d("AlarmRingingService", "ALARM_AUDIO: playerStarted=true")
                } else {
                    Log.e("AlarmRingingService", "ALARM_AUDIO: MediaPlayer start error - no valid audio source")
                }
            } catch (e: Exception) {
                Log.e("AlarmRingingService", "ALARM_AUDIO: Exception during audio setup/playback", e)
            }
        } else {
            Log.d("AlarmRingingService", "ALARM_AUDIO: silent playback chosen or playSound=false")
        }

        if (playVibrate) {
            vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }

            if (vibrator?.hasVibrator() == true) {
                val pattern = longArrayOf(0, 1000, 1000)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(pattern, 0)
                }
            }
        }
        
        // Setup 60-second auto-snooze
        timeoutRunnable = Runnable {
            autoSnoozeAlarm(reminder)
        }
        timeoutHandler.postDelayed(timeoutRunnable!!, 60_000L)
    }
    
    private fun autoSnoozeAlarm(reminder: com.cognify.data.local.ReminderEntity) {
        Log.d("AlarmRingingService", "Auto-snoozing alarm due to timeout")
        // Broadcast to close the AlarmActivity if it is open
        val closeIntent = Intent("com.cognify.ACTION_CLOSE_ALARM_ACTIVITY")
        closeIntent.setPackage(packageName)
        sendBroadcast(closeIntent)
        
        CoroutineScope(Dispatchers.IO).launch {
            val db = AppDatabase.getDatabase(applicationContext)
            // Re-fetch to ensure we have latest state
            val userId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: ""
            val latest = db.reminderDao().getReminderById(reminder.id, userId)
            if (latest != null) {
                val snoozeMinutes = latest.snoozeDurationMinutes
                val newTime = System.currentTimeMillis() + (snoozeMinutes * 60 * 1000)
                
                val snoozedReminder = latest.copy(
                    dateTime = newTime
                )
                
                withContext(Dispatchers.Main) {
                    ReminderScheduler.scheduleReminder(applicationContext, snoozedReminder)
                    NotificationHelper.cancelNotification(applicationContext, reminder.notificationId)
                    stopSelf()
                }
            } else {
                withContext(Dispatchers.Main) { stopSelf() }
            }
        }
    }

    override fun onDestroy() {
        Log.d("AlarmRingingService", "Service destroyed")
        timeoutRunnable?.let { timeoutHandler.removeCallbacks(it) }
        
        Log.d("AlarmRingingService", "ALARM_AUDIO: playback stopped")
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        Log.d("AlarmRingingService", "ALARM_AUDIO: playback released")
        
        vibrator?.cancel()
        vibrator = null
        
        releaseWakeLock()
        
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_STOP_ALARM = "com.cognify.ACTION_STOP_ALARM"
    }
}
