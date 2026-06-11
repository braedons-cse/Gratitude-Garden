package com.cse5236.gratitudegarden

import android.app.Application
import com.cse5236.gratitudegarden.di.AppContainer

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
    }
}
