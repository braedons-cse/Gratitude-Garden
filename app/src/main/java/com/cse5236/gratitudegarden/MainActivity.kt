package com.cse5236.gratitudegarden

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.cse5236.gratitudegarden.ui.GratitudeGardenApp
import com.cse5236.gratitudegarden.util.LogTags

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        Log.d(LogTags.MAIN_ACTIVITY, "MainActivity onCreate()")

        setContent {
            GratitudeGardenApp()
        }
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
}