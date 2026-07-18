package com.cse5236.gratitudegarden.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
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
 * Exact vs. inexact: we use [AlarmManager.setExactAndAllowWhileIdle] when the OS
 * grants exact-alarm access (`canScheduleExactAlarms()`), otherwise we fall back to
 * the inexact [AlarmManager.setAndAllowWhileIdle] (still Doze-proof, just fires
 * within a maintenance window rather than to the minute). This keeps us clear of the
 * Play-restricted `USE_EXACT_ALARM` permission and never crashes when exact access
 * is denied.
 *
 * Reboot: AlarmManager alarms do NOT survive a restart, so [BootReceiver] re-arms
 * them on BOOT_COMPLETED. Callers also re-arm on app launch (see MeViewModel).
 */
object ReminderScheduler {
    // Stable request code so re-scheduling replaces the existing alarm/PendingIntent.
    private const val REQUEST_CODE = 4201
    const val ACTION_FIRE = "com.cse5236.gratitudegarden.ACTION_REMINDER_FIRE"

    fun schedule(context: Context, hour: Int, minute: Int) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val triggerAtMillis = System.currentTimeMillis() + initialDelayMillis(hour, minute)
        val pending = firePendingIntent(context)

        if (canScheduleExact(context)) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP, triggerAtMillis, pending,
            )
        } else {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP, triggerAtMillis, pending,
            )
        }
    }

    fun cancel(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        alarmManager.cancel(firePendingIntent(context))
    }

    /**
     * Whether the OS will let us set to-the-minute exact alarms. Always true below
     * API 31 (no permission existed); on API 31+ it reflects the user's
     * "Alarms & reminders" special-access grant. When false we still schedule — just
     * inexactly — so reminders never silently stop.
     */
    fun canScheduleExact(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return false
        return alarmManager.canScheduleExactAlarms()
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
