package com.cse5236.gratitudegarden.util

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Logs a composable's enter/leave plus the host lifecycle events under [tag].
 * Ported from the team's early MVP so the Compose-lifecycle-logging demo
 * survives the move to the full Supabase-backed screens.
 */
@Composable
fun LogComposableLifecycle(tag: String) {
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(Unit) {
        Log.d(tag, "Composable entered (≈ onCreateView).")
    }

    DisposableEffect(lifecycleOwner) {
        Log.d(tag, "DisposableEffect started — screen active.")
        val observer = LifecycleEventObserver { _, event ->
            Log.d(tag, "lifecycle: $event")
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            Log.d(tag, "Composable left (≈ onDestroyView).")
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
}
