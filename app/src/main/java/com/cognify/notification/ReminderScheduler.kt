package com.cognify.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.cognify.data.local.ReminderEntity
import com.cognify.data.model.ReminderType
import java.util.concurrent.TimeUnit

object ReminderScheduler {

    fun scheduleReminder(context: Context, reminder: ReminderEntity) {
        val delay = reminder.dateTime - System.currentTimeMillis()
        if (delay <= 0) {
            // Trigger time has passed or is now, cancel any existing work
            cancelReminder(context, reminder.id, reminder.notificationId)
            return
        }

        if (reminder.type == ReminderType.GENERAL) {
            scheduleExactAlarm(context, reminder)
        } else {
            scheduleWorkManager(context, reminder, delay)
        }
    }

    private fun scheduleExactAlarm(context: Context, reminder: ReminderEntity) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ExactAlarmReceiver::class.java).apply {
            putExtra("reminder_id", reminder.id)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            reminder.notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                reminder.dateTime,
                pendingIntent
            )
        } catch (e: SecurityException) {
            // Permission not granted, fallback to WorkManager
            scheduleWorkManager(context, reminder, reminder.dateTime - System.currentTimeMillis())
        }
    }

    private fun scheduleWorkManager(context: Context, reminder: ReminderEntity, delay: Long) {
        val data = Data.Builder()
            .putString("reminder_id", reminder.id)
            .build()

        val workRequest = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(data)
            .build()

        val uniqueWorkName = "reminder_work_${reminder.id}"
        WorkManager.getInstance(context).enqueueUniqueWork(
            uniqueWorkName,
            ExistingWorkPolicy.REPLACE,
            workRequest
        )
    }

    fun cancelReminder(context: Context, reminderId: String, notificationId: Int) {
        // Cancel WorkManager
        val uniqueWorkName = "reminder_work_${reminderId}"
        WorkManager.getInstance(context).cancelUniqueWork(uniqueWorkName)
        
        // Cancel AlarmManager
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ExactAlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
        
        NotificationHelper.cancelNotification(context, notificationId)
    }
}
