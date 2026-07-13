package com.cse5236.gratitudegarden.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationManagerCompat

/**
 * Notification channel + OS-state helpers for the daily gratitude reminder.
 *
 * minSdk is 28, so notification channels (API 26+) always exist — no version
 * guards needed. The channel is (re)created idempotently from
 * [GratitudeGardenApplication] on every launch and, defensively, from the worker.
 */
object ReminderNotifications {
    const val CHANNEL_ID = "daily_reminder"
    const val NOTIFICATION_ID = 1001

    /** Idempotent — safe to call repeatedly. */
    fun ensureChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Daily gratitude reminder",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "A gentle daily nudge to keep your gratitude streak going."
        }
        context.getSystemService(NotificationManager::class.java)
            ?.createNotificationChannel(channel)
    }

    /**
     * Whether the OS will actually surface our reminders. False when the user has
     * turned off notifications for the app, or muted our channel specifically.
     * Drives the "notifications are off at the system level" UI state on the Me screen.
     */
    fun enabledAtOsLevel(context: Context): Boolean {
        val mgr = NotificationManagerCompat.from(context)
        if (!mgr.areNotificationsEnabled()) return false
        val channel = mgr.getNotificationChannel(CHANNEL_ID)
        return channel == null || channel.importance != NotificationManager.IMPORTANCE_NONE
    }
}
