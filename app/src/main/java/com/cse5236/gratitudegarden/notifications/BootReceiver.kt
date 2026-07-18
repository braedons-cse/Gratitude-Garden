package com.cse5236.gratitudegarden.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Re-arms the daily reminder after events that wipe pending AlarmManager alarms:
 * a device reboot (BOOT_COMPLETED) and an app update (MY_PACKAGE_REPLACED). Without
 * this, an enabled reminder would silently stop firing after a restart.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> Unit
            else -> return
        }

        val appContext = context.applicationContext
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val settings = ReminderPreferences(appContext).current()
                if (settings.enabled) {
                    ReminderScheduler.schedule(appContext, settings.hour, settings.minute)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
