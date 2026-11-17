package com.cognify.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.room.Room
import com.cognify.data.local.AppDatabase
import com.cognify.data.model.ReminderStatus
import com.cognify.data.model.RepeatInterval
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

class ExactAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || 
            intent.action == Intent.ACTION_TIME_CHANGED || 
            intent.action == Intent.ACTION_TIMEZONE_CHANGED) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val db = AppDatabase.getDatabase(context)
                    val dao = db.reminderDao()
                    val userId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: ""
                    val activeReminders = dao.getByStatusList(ReminderStatus.SCHEDULED.name, userId)
                    activeReminders.forEach { reminder ->
                        ReminderScheduler.scheduleReminder(context, reminder)
                    }
                } finally {
                    pendingResult.finish()
                }
            }
            return
        }

        val reminderId = intent.getStringExtra("reminder_id") ?: run {
            pendingResult.finish()
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getDatabase(context)

                val dao = db.reminderDao()
                val userId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: ""
                val reminder = dao.getReminderById(reminderId, userId) ?: return@launch

                if (reminder.status == ReminderStatus.CANCELLED) {
                    return@launch
                }

                // Show notification or alarm
                if (reminder.type == com.cognify.data.model.ReminderType.GENERAL) {
                    val serviceIntent = Intent(context, AlarmRingingService::class.java).apply {
                        putExtra(NotificationHelper.EXTRA_REMINDER_ID, reminder.id)
                    }
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent)
                    } else {
                        context.startService(serviceIntent)
                    }
                } else {
                    NotificationHelper.showReminderNotification(context, reminder)
                }

                // We rely on AlarmActivity or the user dismissing the alarm to reschedule or complete it.
                // This ensures Snooze works properly by allowing the alarm to stay pending.
            } finally {
                pendingResult.finish()
            }
        }
    }
}
