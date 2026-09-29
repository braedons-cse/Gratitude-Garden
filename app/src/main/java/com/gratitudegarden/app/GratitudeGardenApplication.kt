package com.gratitudegarden.app

import android.app.Application
import com.gratitudegarden.app.di.AppContainer
import com.gratitudegarden.app.notifications.ReminderNotifications
import com.gratitudegarden.app.widget.WidgetSync

/**
 * Process-wide owner of app dependencies. [container] is built once and read by
 * ViewModels via the Application instance (manual DI — no Hilt).
 */
class GratitudeGardenApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Register the reminder channel up front so notifications can post
        // (and so the channel shows in system settings) from any entry point.
        ReminderNotifications.ensureChannel(this)
        // Keep a widget on the home screen in step with whatever this process writes.
        if (WidgetSync.hasWidgets(this)) container.widgetSync.start()
    }
}
