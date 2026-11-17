package com.cognify.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.room.Room
import com.cognify.data.local.AppDatabase
import com.cognify.data.model.ReminderStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getStringExtra(NotificationHelper.EXTRA_REMINDER_ID) ?: return
        val notificationId = intent.getIntExtra(NotificationHelper.EXTRA_NOTIFICATION_ID, -1)

        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getDatabase(context)

                val dao = db.reminderDao()

                when (intent.action) {
                    NotificationHelper.ACTION_MARK_COMPLETE -> {
                        val userId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: ""
                        val now = System.currentTimeMillis()
                        dao.updateStatus(reminderId, ReminderStatus.COMPLETED.name, now, userId)
                        if (notificationId != -1) {
                            NotificationHelper.cancelNotification(context, notificationId)
                        }
                        val stopIntent = Intent(context, AlarmRingingService::class.java).apply {
                            action = AlarmRingingService.ACTION_STOP_ALARM
                        }
                        context.startService(stopIntent)
                    }
                    NotificationHelper.ACTION_SNOOZE -> {
                        val userId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: ""
                        val reminder = dao.getReminderById(reminderId, userId)
                        if (reminder != null) {
                            val snoozeTime = System.currentTimeMillis() + (15 * 60 * 1000) // 15 mins
                            val snoozedReminder = reminder.copy(
                                dateTime = snoozeTime,
                                status = ReminderStatus.SCHEDULED,
                                updatedAt = System.currentTimeMillis()
                            )
                            dao.updateReminder(snoozedReminder)
                            ReminderScheduler.scheduleReminder(context, snoozedReminder)
                            if (notificationId != -1) {
                                NotificationHelper.cancelNotification(context, notificationId)
                            }
                            val stopIntent = Intent(context, AlarmRingingService::class.java).apply {
                                action = AlarmRingingService.ACTION_STOP_ALARM
                            }
                            context.startService(stopIntent)
                        }
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
