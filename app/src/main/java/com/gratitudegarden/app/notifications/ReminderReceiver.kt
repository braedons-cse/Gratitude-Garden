package com.gratitudegarden.app.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.gratitudegarden.app.GratitudeGardenApplication
import com.gratitudegarden.app.data.AppSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

/**
 * Fires when the daily reminder alarm goes off. Re-arms the alarm for the next day and
 * posts the notification, if the reminder is still enabled and nothing was written today:
 * it's a streak reminder, and the streak is already safe.
 *
 * Also the reminder's "Later": [ACTION_LATER] clears it and sets a one-shot alarm
 * ([ReminderScheduler.snooze]), and [ReminderScheduler.ACTION_SNOOZE_FIRE] posts it again
 * under the same rules, without touching tomorrow's.
 *
 * The alarm may cold-start the app process, so this reads the reminder settings
 * fresh from DataStore. That read is suspending, so we hop off the main thread via
 * [goAsync] + a coroutine and only finish the broadcast once the work completes.
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val appContext = context.applicationContext
        when (intent.action) {
            ACTION_LATER -> {
                ReminderNotifications.dismiss(appContext)
                ReminderScheduler.snooze(appContext)
            }
            ReminderScheduler.ACTION_FIRE -> remind(appContext, snoozed = false)
            ReminderScheduler.ACTION_SNOOZE_FIRE -> remind(appContext, snoozed = true)
        }
    }

    private fun remind(appContext: Context, snoozed: Boolean) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val prefs = ReminderPreferences(appContext)
                val settings = prefs.current()
                // The user may have disabled reminders after this alarm was set; if
                // so, post nothing and don't re-arm — the chain stops here.
                if (!settings.enabled) return@launch
                // Re-arm first, so nothing below can break tomorrow's reminder. A snooze
                // is a one-off on its own alarm; tomorrow's is already set.
                if (!snoozed) ReminderScheduler.schedule(appContext, settings.hour, settings.minute)

                val repo = (appContext as GratitudeGardenApplication).container.gardenRepository
                val signedIn = repo.awaitReady(SESSION_WAIT) is AppSession.SignedIn
                // A snooze was asked for by whoever was signed in; after a sign-out it's moot.
                if (snoozed && !signedIn) return@launch
                val wroteToday = signedIn && runCatching { repo.wroteToday() }.getOrNull() == true
                if (wroteToday) return@launch

                // Pick a fresh message (never the same one two days running).
                val index = ReminderNotifications.pickMessageIndex(prefs.lastMessageIndex())
                ReminderNotifications.postReminder(appContext, index, canReply = signedIn)
                prefs.setLastMessageIndex(index)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_LATER = "com.gratitudegarden.app.ACTION_REMINDER_LATER"

        // Well inside goAsync's ~10 s. Past it the reminder posts: better than none.
        private val SESSION_WAIT = 3.seconds
    }
}
