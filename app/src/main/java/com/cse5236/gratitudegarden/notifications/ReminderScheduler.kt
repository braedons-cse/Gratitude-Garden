package com.cse5236.gratitudegarden.notifications

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/**
 * Schedules the daily reminder as a self-chaining [OneTimeWorkRequest]. Each run
 * fires at the chosen local time, then enqueues the next day's run (see
 * [ReminderWorker]). A one-time chain (rather than a PeriodicWorkRequest) lets us
 * target an exact time-of-day and re-target instantly when the user changes it.
 *
 * Reboot behaviour: WorkManager persists its queue and reschedules enqueued work
 * after a device restart on its own, so the reminder keeps firing across reboots
 * without any BootReceiver. (What would NOT survive a reboot is an AlarmManager
 * alarm — that's the tradeoff we avoided by choosing WorkManager.)
 */
object ReminderScheduler {
    const val WORK_NAME = "daily_gratitude_reminder"

    fun schedule(context: Context, hour: Int, minute: Int) {
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(initialDelayMillis(hour, minute), TimeUnit.MILLISECONDS)
            .addTag(WORK_NAME)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_NAME,
            // REPLACE so changing the time cancels the old pending run and re-arms.
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    /** Millis from [now] until the next occurrence of [hour]:[minute] in local time. */
    fun initialDelayMillis(
        hour: Int,
        minute: Int,
        now: LocalDateTime = LocalDateTime.now(),
    ): Long {
        var next = now.toLocalDate().atTime(LocalTime.of(hour, minute))
        if (!next.isAfter(now)) next = next.plusDays(1)
        return Duration.between(now, next).toMillis()
    }
}
