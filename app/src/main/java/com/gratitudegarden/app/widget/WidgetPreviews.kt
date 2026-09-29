package com.gratitudegarden.app.widget

import android.content.Context
import android.os.Build
import androidx.glance.appwidget.GlanceAppWidgetManager
import com.gratitudegarden.app.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Hands the launcher the widget's generated preview ([GardenWidget.providePreview]) on
 * Android 15+, once per app version: the platform rate-limits it to a couple of calls an
 * hour. Older versions show the static `widget_preview` image instead.
 */
fun publishPreviews(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return
    val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    if (prefs.getInt(KEY_VERSION, -1) == BuildConfig.VERSION_CODE) return
    CoroutineScope(Dispatchers.Default).launch {
        val accepted = runCatching {
            GlanceAppWidgetManager(context).setWidgetPreviews(GardenWidgetReceiver::class) ==
                GlanceAppWidgetManager.SET_WIDGET_PREVIEWS_RESULT_SUCCESS
        }.getOrDefault(false)
        // Rate-limited or refused: try again on the next launch.
        if (accepted) prefs.edit().putInt(KEY_VERSION, BuildConfig.VERSION_CODE).apply()
    }
}

private const val PREFS = "widget_prefs"
private const val KEY_VERSION = "previews_version"
