package com.cse5236.gratitudegarden.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Fires when the daily reminder alarm goes off. Posts the notification (if the
 * reminder is still enabled) and re-arms the alarm for the next day.
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
                if (settings.enabled) {
                    // Pick a fresh message (never the same one two days running).
                    val index = ReminderNotifications.pickMessageIndex(prefs.lastMessageIndex())
                    ReminderNotifications.postReminder(appContext, index)
                    prefs.setLastMessageIndex(index)
                    ReminderScheduler.schedule(appContext, settings.hour, settings.minute)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
