package com.gratitudegarden.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.gratitudegarden.app.ui.GratitudeGardenApp
import com.gratitudegarden.app.util.LogTags

class MainActivity : ComponentActivity() {

    /** A Write link is waiting for the entry sheet (see [GratitudeGardenApp]). */
    private var writeRequested by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        Log.d(LogTags.MAIN_ACTIVITY, "MainActivity onCreate()")

        // A recreated activity still holds the intent that first opened it: read it only on
        // a fresh start, or every rotation would open the sheet again.
        writeRequested = if (savedInstanceState == null) {
            isWriteLink(intent)
        } else {
            savedInstanceState.getBoolean(KEY_WRITE_REQUESTED)
        }

        setContent {
            GratitudeGardenApp(
                writeRequested = writeRequested,
                onWriteHandled = { writeRequested = false },
            )
        }
    }

    // singleTop: a Write link while the app is open arrives here instead of recreating it.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (isWriteLink(intent)) writeRequested = true
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_WRITE_REQUESTED, writeRequested)
    }

    override fun onStart() {
        super.onStart()
        Log.d(LogTags.MAIN_ACTIVITY, "MainActivity onStart()")
    }

    override fun onResume() {
        super.onResume()
        Log.d(LogTags.MAIN_ACTIVITY, "MainActivity onResume()")
    }

    override fun onPause() {
        super.onPause()
        Log.d(LogTags.MAIN_ACTIVITY, "MainActivity onPause()")
    }

    override fun onStop() {
        super.onStop()
        Log.d(LogTags.MAIN_ACTIVITY, "MainActivity onStop()")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(LogTags.MAIN_ACTIVITY, "MainActivity onDestroy()")
    }

    companion object {
        const val ACTION_WRITE = "com.gratitudegarden.app.action.WRITE"
        private const val KEY_WRITE_REQUESTED = "write_requested"

        /** Opens the app on the Garden tab with the entry sheet up. */
        fun writeIntent(context: Context): Intent =
            Intent(context, MainActivity::class.java)
                .setAction(ACTION_WRITE)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

        // Relaunched from Recents after the process died: the old link, not a new tap.
        private fun isWriteLink(intent: Intent?): Boolean =
            intent != null && intent.action == ACTION_WRITE &&
                (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) == 0
    }
}
