package com.cse5236.gratitudegarden.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.cse5236.gratitudegarden.MainActivity
import kotlin.random.Random

/** One reminder variant (notification title + body). */
data class ReminderMessage(val title: String, val body: String)

/**
 * Notification channel + OS-state helpers for the daily gratitude reminder.
 *
 * minSdk is 28, so notification channels (API 26+) always exist — no version
 * guards needed. The channel is (re)created idempotently from
 * [GratitudeGardenApplication] on every launch and, defensively, before each post.
 */
object ReminderNotifications {
    const val CHANNEL_ID = "daily_reminder"
    const val NOTIFICATION_ID = 1001

    /**
     * Pool of reminder variants. One is picked each time the alarm fires (see
     * [pickMessageIndex]) so the daily nudge doesn't read identically every day.
     */
    val MESSAGES: List<ReminderMessage> = listOf(
        ReminderMessage(
            "Keep your streak going 🌱",
            "Take a moment to type or speak one thing you're grateful for today.",
        ),
        ReminderMessage(
            "Your garden misses you 🌼",
            "Plant one grateful thought and watch it grow.",
        ),
        ReminderMessage(
            "A moment for gratitude",
            "Pause and name one thing you're thankful for right now.",
        ),
        ReminderMessage(
            "One kind word a day 💛",
            "What's one good thing from today? Add it to your garden.",
        ),
        ReminderMessage(
            "Time to grow 🌿",
            "A single grateful thought keeps your garden blooming.",
        ),
        ReminderMessage(
            "Don't let your streak wilt 🍂",
            "Log one thing you're grateful for before the day ends.",
        ),
    )

    /**
     * Pick the next message index at random, never repeating [lastIndex] (so the
     * reminder never shows the same text two days running). Pure/testable; pass a
     * seeded [random] in tests. Returns 0 when there's only one message, and treats
     * a [lastIndex] of -1 (nothing shown yet) as "anything goes".
     *
     * Optimization: instead of materializing a filtered `List` of candidate indices
     * on every call (`MESSAGES.indices.filter { it != lastIndex }` — one throwaway
     * list + boxing per invocation), draw uniformly from the `n - 1` messages that
     * differ from [lastIndex] and shift the result past the gap. Same output set and
     * uniform distribution, but O(1) and allocation-free. See
     * docs/unit-test-optimizations.md.
     */
    fun pickMessageIndex(lastIndex: Int, random: Random = Random.Default): Int {
        val n = MESSAGES.size
        if (n <= 1) return 0
        // -1 ("nothing shown yet") or any out-of-range value: every message is fair game.
        if (lastIndex !in 0 until n) return random.nextInt(n)
        // Draw from [0, n-2] — the n-1 indices that aren't lastIndex — then hop over
        // the gap so lastIndex itself can never be produced.
        val draw = random.nextInt(n - 1)
        return if (draw < lastIndex) draw else draw + 1
    }

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
     * Build and post the daily reminder notification using [MESSAGES]`[index]`.
     * Tapping it opens the app. Called from [ReminderReceiver] when the alarm fires.
     * Safe to call from a cold-started process — the channel is (re)created first.
     */
    fun postReminder(context: Context, index: Int) {
        ensureChannel(context)

        // API 33+: silently skip if the runtime grant is missing or was revoked.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val openApp = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            openApp,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val message = MESSAGES[index.coerceIn(MESSAGES.indices)]
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(message.title)
            .setContentText(message.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message.body))
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
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
