package com.gratitudegarden.app.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import com.gratitudegarden.app.GratitudeGardenApplication
import com.gratitudegarden.app.data.AppSession
import com.gratitudegarden.app.data.SubmitResult
import com.gratitudegarden.app.data.streakNow
import com.gratitudegarden.app.ui.garden.submitMessage
import com.gratitudegarden.app.ui.garden.toSubmitMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

/**
 * A thought written straight into the reminder. It goes through the journal like any other
 * (Room first, then the outbox), so it is safe offline and in a process the reply just
 * started. The reminder is then replaced by what the Garden would have said.
 *
 * Never starts an activity: Android 12+ doesn't let a notification action's receiver do that.
 */
class ReplyReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_REPLY) return
        val text = RemoteInput.getResultsFromIntent(intent)
            ?.getCharSequence(ReminderNotifications.KEY_REPLY_TEXT)
            ?.toString()?.trim()

        val appContext = context.applicationContext
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            var outcome = Outcome(FAILED, saved = false)
            try {
                // The thought is in: an hour's nudge would only nag.
                ReminderScheduler.cancelSnooze(appContext)
                outcome = plant(appContext, text)
            } finally {
                // Always, or the reply field keeps spinning. A thought that wasn't saved is
                // shown back, since the notification is then its only copy.
                ReminderNotifications.postReplyResult(
                    appContext,
                    outcome.message,
                    written = if (outcome.saved) null else text,
                )
                pendingResult.finish()
            }
        }
    }

    /** [saved]: the thought is in the journal, whether or not it has reached the server. */
    private data class Outcome(val message: String, val saved: Boolean)

    private suspend fun plant(context: Context, text: String?): Outcome {
        if (text.isNullOrBlank()) return Outcome("Write a few words first.", saved = false)
        val repo = (context as GratitudeGardenApplication).container.gardenRepository
        // The whole budget stays inside goAsync's ~10 s. Whatever isn't delivered by then
        // goes from the outbox's WorkManager job, so "saved" is the truth.
        if (repo.awaitReady(SESSION_WAIT) !is AppSession.SignedIn) {
            return Outcome("Sign in to plant your thoughts.", saved = false)
        }
        // As on the Garden: a held streak is bridged by this entry, if it's the day's first.
        val freezing = repo.observeStats().first()?.streakNow?.heldByFreeze == true
        return try {
            val result = repo.submitEntry(text, voice = false, wait = SUBMIT_WAIT)
            Outcome(submitMessage(result, freezing), saved = result !is SubmitResult.Refused)
        } catch (e: Exception) {
            Outcome(e.toSubmitMessage(), saved = false)
        }
    }

    companion object {
        const val ACTION_REPLY = "com.gratitudegarden.app.ACTION_REMINDER_REPLY"
        private const val FAILED = "Couldn't save your thought"
        private val SESSION_WAIT = 3.seconds
        private val SUBMIT_WAIT = 4.seconds
    }
}
