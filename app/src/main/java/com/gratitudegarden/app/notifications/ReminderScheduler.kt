package com.gratitudegarden.app.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Schedules the daily reminder with [AlarmManager]. Each alarm fires at the chosen
 * local time and [ReminderReceiver] re-arms the next day's alarm when it runs.
 *
 * Why AlarmManager (not WorkManager): WorkManager is designed for *deferrable*
 * background work and is throttled by Doze/app-standby, so a daily reminder could
 * arrive late — or not until the phone was next used. AlarmManager's
 * `...AndAllowWhileIdle` variants are the supported way to wake the device for a
 * user-facing, time-of-day notification even while idle.
 *
 * Inexact on purpose: [AlarmManager.setAndAllowWhileIdle] still wakes the device from
 * Doze, it just lets the OS batch the alarm, so it can arrive a few minutes after the
 * chosen time. A gratitude nudge doesn't need to-the-minute timing, and exact alarms
 * need `SCHEDULE_EXACT_ALARM`, which Play reviews (roadmap 0.8) and Android 14+ denies
 * to new installs by default anyway. So the app no longer asks for it.
 *
 * Reboot: AlarmManager alarms do NOT survive a restart, so [BootReceiver] re-arms
 * them on BOOT_COMPLETED. Callers also re-arm on app launch (see MeViewModel).
 */
object ReminderScheduler {
    // Stable request code so re-scheduling replaces the existing alarm/PendingIntent.
    private const val REQUEST_CODE = 4201
    const val ACTION_FIRE = "com.gratitudegarden.app.ACTION_REMINDER_FIRE"

    fun schedule(context: Context, hour: Int, minute: Int) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val triggerAtMillis = System.currentTimeMillis() + initialDelayMillis(hour, minute)
        val pending = firePendingIntent(context)

        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pending)
    }

    fun cancel(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        alarmManager.cancel(firePendingIntent(context))
    }

    private fun firePendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).setAction(ACTION_FIRE)
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
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
