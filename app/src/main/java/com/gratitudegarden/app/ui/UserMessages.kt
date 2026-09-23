package com.gratitudegarden.app.ui

import android.util.Log
import com.gratitudegarden.app.util.LogTags
import io.github.jan.supabase.exceptions.HttpRequestException
import java.io.IOException

internal const val OFFLINE_MESSAGE =
    "We can't reach the garden right now. Check your connection and try again."

/** Network failures: supabase-kt's own wrapper, plus Ktor timeouts (which are IOExceptions). */
internal fun isOffline(e: Throwable): Boolean = e is HttpRequestException || e is IOException

/**
 * The one sentence a screen shows when a call fails. Never the exception's own message:
 * supabase-kt folds the request URL and headers into it. [known] maps the server errors we
 * raise on purpose (`raise exception '...'` in the RPCs) to friendly copy; anything it doesn't
 * recognise gets [fallback].
 */
internal fun userMessage(
    e: Throwable,
    fallback: String,
    known: (String) -> String? = { null },
): String = when {
    isOffline(e) -> OFFLINE_MESSAGE
    else -> e.message?.let(known) ?: fallback
}

/** [userMessage], logging the full exception first so the detail isn't lost. */
internal fun Throwable.toUserMessage(fallback: String, known: (String) -> String? = { null }): String {
    Log.w(LogTags.APP_LOGIC, fallback, this)
    return userMessage(this, fallback, known)
}
