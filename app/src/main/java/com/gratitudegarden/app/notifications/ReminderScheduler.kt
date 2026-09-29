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
 *
 * The reminder's "Later" sets a second, one-shot alarm [SNOOZE] ahead on its own request
 * code, so it never replaces tomorrow's. A reboot drops it, which is fine for an hour's nudge.
 */
object ReminderScheduler {
    // Stable request code so re-scheduling replaces the existing alarm/PendingIntent.
    private const val REQUEST_CODE = 4201
    private const val SNOOZE_REQUEST_CODE = 4202
    const val ACTION_FIRE = "com.gratitudegarden.app.ACTION_REMINDER_FIRE"
    const val ACTION_SNOOZE_FIRE = "com.gratitudegarden.app.ACTION_REMINDER_SNOOZE_FIRE"
    val SNOOZE: Duration = Duration.ofHours(1)

    fun schedule(context: Context, hour: Int, minute: Int) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val triggerAtMillis = System.currentTimeMillis() + initialDelayMillis(hour, minute)
        val pending = firePendingIntent(context)

        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pending)
    }

    /** Stops the daily reminder, and a snoozed one with it. */
    fun cancel(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        alarmManager.cancel(firePendingIntent(context))
        cancelSnooze(context)
    }

    /** Remind once more, [SNOOZE] from now. Asking again moves it rather than adding one. */
    fun snooze(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val triggerAtMillis = System.currentTimeMillis() + SNOOZE.toMillis()
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, snoozePendingIntent(context))
    }

    fun cancelSnooze(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        alarmManager.cancel(snoozePendingIntent(context))
    }

    private fun firePendingIntent(context: Context): PendingIntent =
        receiverPendingIntent(context, REQUEST_CODE, ACTION_FIRE)

    private fun snoozePendingIntent(context: Context): PendingIntent =
        receiverPendingIntent(context, SNOOZE_REQUEST_CODE, ACTION_SNOOZE_FIRE)

    private fun receiverPendingIntent(context: Context, requestCode: Int, action: String): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).setAction(action)
        return PendingIntent.getBroadcast(
            context,
            requestCode,
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
