package com.gratitudegarden.app.util

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Logs the lifecycle of a Compose "screen" under [tag], mapping Compose
 * composition + host lifecycle events onto the classic Activity/Fragment
 * callback names (onCreate / onStart / onResume / onPause / onStop / onDestroy,
 * plus onCreateView / onDestroyView) so the screen flow is easy to follow in
 * Logcat.
 *
 * For a Navigation-Compose destination, [LocalLifecycleOwner] is that
 * destination's NavBackStackEntry, so navigating between screens produces real
 * per-screen lifecycle transitions — the declarative-UI equivalent of Fragment
 * lifecycles.
 */
@Composable
fun LogComposableLifecycle(tag: String) {
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        // Entering the composition is the Compose analogue of onCreateView().
        Log.d(tag, "onCreateView() — composable entered composition")

        val observer = LifecycleEventObserver { _, event ->
            val callback = when (event) {
                Lifecycle.Event.ON_CREATE -> "onCreate()"
                Lifecycle.Event.ON_START -> "onStart()"
                Lifecycle.Event.ON_RESUME -> "onResume()"
                Lifecycle.Event.ON_PAUSE -> "onPause()"
                Lifecycle.Event.ON_STOP -> "onStop()"
                Lifecycle.Event.ON_DESTROY -> "onDestroy()"
                else -> event.name
            }
            Log.d(tag, callback)
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            // Leaving the composition is the Compose analogue of onDestroyView().
            Log.d(tag, "onDestroyView() — composable left composition")
        }
    }
}
