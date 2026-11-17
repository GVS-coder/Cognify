package com.cognify.ui.clock

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.room.Room
import com.cognify.R
import com.cognify.data.local.AppDatabase
import com.cognify.data.model.ReminderStatus
import com.cognify.databinding.ActivityAlarmBinding
import com.cognify.notification.NotificationHelper
import com.cognify.notification.ReminderScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class AlarmActivity : AppCompatActivity() {

    @Inject
    lateinit var repository: com.cognify.data.repository.ReminderRepository

    private lateinit var binding: ActivityAlarmBinding
    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    
    private val closeReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
            if (intent?.action == "com.cognify.ACTION_CLOSE_ALARM_ACTIVITY") {
                finish()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Register close receiver
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(closeReceiver, android.content.IntentFilter("com.cognify.ACTION_CLOSE_ALARM_ACTIVITY"), Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(closeReceiver, android.content.IntentFilter("com.cognify.ACTION_CLOSE_ALARM_ACTIVITY"))
        }
        
        // Setup Full Screen Window Flags
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as android.app.KeyguardManager
            keyguardManager.requestDismissKeyguard(this, null)
        } else {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        binding = ActivityAlarmBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val reminderId = intent.getStringExtra(NotificationHelper.EXTRA_REMINDER_ID)
        
        if (reminderId == null) {
            finish()
            return
        }

        // We will start ringing inside the coroutine after loading the reminder
        lifecycleScope.launch(Dispatchers.IO) {
            val reminder = repository.getById(reminderId)

            withContext(Dispatchers.Main) {
                if (reminder != null) {
                    val cal = java.util.Calendar.getInstance().apply { timeInMillis = reminder.dateTime }
                    val is24Hour = android.text.format.DateFormat.is24HourFormat(this@AlarmActivity)
                    val format = if (is24Hour) java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()) else java.text.SimpleDateFormat("hh:mm", java.util.Locale.getDefault())
                    val amPmFormat = java.text.SimpleDateFormat("a", java.util.Locale.getDefault())
                    
                    binding.tvAlarmTime.text = format.format(cal.time)
                    binding.tvAlarmAmPm.text = if (is24Hour) "" else amPmFormat.format(cal.time)
                    binding.tvAlarmAmPm.visibility = if (is24Hour) android.view.View.GONE else android.view.View.VISIBLE
                    
                    binding.tvAlarmTitle.text = reminder.title.ifBlank { "Alarm" }
                    
                    val title = reminder.title.ifBlank { "Alarm" }
                    binding.tvAlarmTitle.text = title
                    
                    binding.btnSnooze.text = getString(R.string.alarm_snooze_duration, reminder.snoozeDurationMinutes)

                    if (reminder.id == "cognify_timer_id") {
                        binding.btnSnooze.visibility = android.view.View.GONE
                    }

                    binding.btnDismiss.setOnClickListener {
                        lifecycleScope.launch(Dispatchers.IO) {
                            if (reminder.repeatInterval != com.cognify.data.model.RepeatInterval.NONE) {
                                // Advance the repeating alarm to the next occurrence.
                                // We calculate it based on the CURRENT time to ensure we skip the current day.
                                val nextTime = calculateNextTrigger(System.currentTimeMillis(), reminder)
                                val nextReminder = reminder.copy(
                                    dateTime = nextTime,
                                    status = ReminderStatus.SCHEDULED,
                                    updatedAt = System.currentTimeMillis()
                                )
                                repository.saveReminder(nextReminder)
                                ReminderScheduler.scheduleReminder(this@AlarmActivity, nextReminder)
                            } else {
                                if (reminder.id != "cognify_timer_id") {
                                    repository.updateReminderStatus(reminder.id, ReminderStatus.COMPLETED)
                                } else {
                                    repository.deleteReminder(reminder.id)
                                    ReminderScheduler.cancelReminder(this@AlarmActivity, reminder.id, reminder.notificationId)
                                }
                            }
                            NotificationHelper.cancelNotification(this@AlarmActivity, reminder.notificationId)
                            stopRingingAndFinish()
                        }
                    }

                    binding.btnSnooze.setOnClickListener {
                        lifecycleScope.launch(Dispatchers.IO) {
                            // Do not update the database, so the original schedule remains intact for repeating alarms.
                            // Just schedule a one-off temporary trigger.
                            val snoozeTime = System.currentTimeMillis() + (reminder.snoozeDurationMinutes * 60 * 1000L)
                            val snoozedReminder = reminder.copy(
                                dateTime = snoozeTime
                            )
                            ReminderScheduler.scheduleReminder(this@AlarmActivity, snoozedReminder)
                            NotificationHelper.cancelNotification(this@AlarmActivity, reminder.notificationId)
                            stopRingingAndFinish()
                        }
                    }
                } else {
                    finish()
                }
            }
        }
    }

    private fun calculateNextTrigger(currentTime: Long, reminder: com.cognify.data.local.ReminderEntity): Long {
        val originalCal = java.util.Calendar.getInstance().apply { timeInMillis = reminder.dateTime }
        val targetHour = originalCal.get(java.util.Calendar.HOUR_OF_DAY)
        val targetMinute = originalCal.get(java.util.Calendar.MINUTE)

        val cal = java.util.Calendar.getInstance().apply { timeInMillis = currentTime }
        cal.set(java.util.Calendar.HOUR_OF_DAY, targetHour)
        cal.set(java.util.Calendar.MINUTE, targetMinute)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)

        // If the calculated time for today is already in the past or now, push to tomorrow before applying repeat logic
        if (cal.timeInMillis <= currentTime) {
            cal.add(java.util.Calendar.DAY_OF_YEAR, 1)
        }

        when (reminder.repeatInterval) {
            com.cognify.data.model.RepeatInterval.DAILY -> {
                // Already handled by the +1 day above if needed
            }
            com.cognify.data.model.RepeatInterval.WEEKLY -> {
                // If it's weekly, just add 7 days (or 6 if we already added 1)
                // Actually, if we already added 1, it's tomorrow, but it needs to be 1 week from the *original* day.
                // Simple hack: if repeat is weekly, just push it a week from the original trigger?
                // But if they missed it for a week...
                // Better: keep adding 1 day until it matches the original Day Of Week.
                while (cal.get(java.util.Calendar.DAY_OF_WEEK) != originalCal.get(java.util.Calendar.DAY_OF_WEEK)) {
                    cal.add(java.util.Calendar.DAY_OF_YEAR, 1)
                }
            }
            com.cognify.data.model.RepeatInterval.NONE -> {
                // Do nothing
            }
            else -> {
                // If custom weekdays (e.g. "Mon, Wed, Fri" -> "2,4,6")
                val days = reminder.selectedWeekdays.split(",").mapNotNull { it.toIntOrNull() }
                if (days.isNotEmpty()) {
                    while (!days.contains(cal.get(java.util.Calendar.DAY_OF_WEEK))) {
                        cal.add(java.util.Calendar.DAY_OF_YEAR, 1)
                    }
                }
            }
        }
        return cal.timeInMillis
    }

    private fun startRinging(playSound: Boolean = true, playVibrate: Boolean = true) {
        // Now handled by AlarmRingingService
    }

    private fun stopRingingAndFinish() {
        val stopIntent = android.content.Intent(this, com.cognify.notification.AlarmRingingService::class.java).apply {
            action = com.cognify.notification.AlarmRingingService.ACTION_STOP_ALARM
        }
        startService(stopIntent)
        finish()
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(closeReceiver)
        } catch (e: Exception) {
            // Ignored
        }
        super.onDestroy()
    }
}
