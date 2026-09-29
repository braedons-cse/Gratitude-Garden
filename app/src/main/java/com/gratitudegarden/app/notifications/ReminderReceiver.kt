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
 * The alarm may cold-start the app process, so this reads the reminder settings
 * fresh from DataStore. That read is suspending, so we hop off the main thread via
 * [goAsync] + a coroutine and only finish the broadcast once the work completes.
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ReminderScheduler.ACTION_FIRE) return

        val appContext = context.applicationContext
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val prefs = ReminderPreferences(appContext)
                val settings = prefs.current()
                // The user may have disabled reminders after this alarm was set; if
                // so, post nothing and don't re-arm — the chain stops here.
                if (!settings.enabled) return@launch
                // Re-arm first, so nothing below can break tomorrow's reminder.
                ReminderScheduler.schedule(appContext, settings.hour, settings.minute)

                val repo = (appContext as GratitudeGardenApplication).container.gardenRepository
                val session = repo.awaitReady(SESSION_WAIT)
                val wroteToday = session is AppSession.SignedIn &&
                    runCatching { repo.wroteToday() }.getOrNull() == true
                if (wroteToday) return@launch

                // Pick a fresh message (never the same one two days running).
                val index = ReminderNotifications.pickMessageIndex(prefs.lastMessageIndex())
                ReminderNotifications.postReminder(appContext, index)
                prefs.setLastMessageIndex(index)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private companion object {
        // Well inside goAsync's ~10 s. Past it the reminder posts: better than none.
        val SESSION_WAIT = 3.seconds
    }
}
