package com.cognify.notification

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.cognify.data.local.AppDatabase
import com.cognify.data.model.ReminderStatus
import com.cognify.data.model.RepeatInterval
import androidx.room.Room
import java.util.Calendar

class ReminderWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val reminderId = inputData.getString("reminder_id") ?: return Result.failure()

        // Get database instance
        val db = AppDatabase.getDatabase(applicationContext)

        val dao = db.reminderDao()
        val userId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: ""
        val reminder = dao.getReminderById(reminderId, userId) ?: return Result.failure()

        if (reminder.status == ReminderStatus.CANCELLED) {
            return Result.success()
        }

        // Show notification
        NotificationHelper.showReminderNotification(applicationContext, reminder)

        val now = System.currentTimeMillis()

        if (reminder.repeatInterval != RepeatInterval.NONE) {
            // Reschedule next repeating instance
            val nextTime = calculateNextTrigger(reminder.dateTime, reminder.repeatInterval)
            val nextReminder = reminder.copy(
                dateTime = nextTime,
                status = ReminderStatus.SCHEDULED,
                updatedAt = now
            )
            dao.updateReminder(nextReminder)
            ReminderScheduler.scheduleReminder(applicationContext, nextReminder)
        } else {
            // Mark as completed
            dao.updateStatus(reminder.id, ReminderStatus.COMPLETED.name, now, userId)
        }

        return Result.success()
    }

    private fun calculateNextTrigger(currentTime: Long, repeat: RepeatInterval): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = currentTime }
        when (repeat) {
            RepeatInterval.DAILY -> cal.add(Calendar.DAY_OF_YEAR, 1)
            RepeatInterval.WEEKLY -> cal.add(Calendar.WEEK_OF_YEAR, 1)
            RepeatInterval.MONTHLY -> cal.add(Calendar.MONTH, 1)
            RepeatInterval.YEARLY -> cal.add(Calendar.YEAR, 1)
            RepeatInterval.NONE -> {}
        }
        return cal.timeInMillis
    }
}
