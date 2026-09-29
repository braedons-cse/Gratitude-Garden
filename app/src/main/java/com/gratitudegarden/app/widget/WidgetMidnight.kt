package com.gratitudegarden.app.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.time.ZonedDateTime

/**
 * Wakes the widget just after local midnight, when "thoughts left today" resets and a
 * streak with no entry yesterday changes. Nothing in the database changes then, so nothing
 * else would redraw it.
 *
 * Unlike the reminder this doesn't wake the device (RTC, not RTC_WAKEUP) and is inexact:
 * nobody looks at a home screen with the screen off, and the alarm is delivered the moment
 * it comes on.
 */
object WidgetMidnight {
    private const val REQUEST_CODE = 4301
    const val ACTION_MIDNIGHT = "com.gratitudegarden.app.ACTION_WIDGET_MIDNIGHT"

    /** Idempotent: re-arming replaces the pending alarm. */
    fun arm(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        alarmManager.set(AlarmManager.RTC, nextMidnightMillis(), pendingIntent(context))
    }

    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java)?.cancel(pendingIntent(context))
    }

    /** A minute into the next local day, so the new day is certainly "today" when it fires. */
    fun nextMidnightMillis(now: ZonedDateTime = ZonedDateTime.now()): Long =
        // atStartOfDay(zone), not 00:00 in the zone: where a DST change skips midnight, the
        // day starts at 01:00 instead.
        now.toLocalDate().plusDays(1).atStartOfDay(now.zone).plusMinutes(1).toInstant().toEpochMilli()

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, GardenWidgetReceiver::class.java).setAction(ACTION_MIDNIGHT)
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
