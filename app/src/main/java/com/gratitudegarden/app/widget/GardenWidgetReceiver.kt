package com.gratitudegarden.app.widget

import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import com.gratitudegarden.app.GratitudeGardenApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * The widget's entry point from the system. Besides Glance's own updates it takes the
 * midnight alarm ([WidgetMidnight]) and a changed clock or time zone, the moments "today"
 * moves without the database noticing.
 */
class GardenWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = GardenWidget()

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            WidgetMidnight.ACTION_MIDNIGHT,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            -> refresh(context.applicationContext)
        }
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        sync(context).start()
        WidgetMidnight.arm(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        WidgetMidnight.cancel(context)
    }

    private fun refresh(appContext: Context) {
        WidgetMidnight.arm(appContext)
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val sync = sync(appContext)
                sync.start()
                sync.refreshNow()
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun sync(context: Context): WidgetSync =
        (context.applicationContext as GratitudeGardenApplication).container.widgetSync
}
