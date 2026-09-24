package com.gratitudegarden.app.data

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.gratitudegarden.app.GratitudeGardenApplication
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds

/**
 * Delivers the journal outbox from WorkManager: once there's a network, and even if the
 * app isn't running by then. This is the job WorkManager is for (deferrable, must happen
 * eventually, survives process death), unlike the time-of-day reminder, which stays on
 * AlarmManager (see ReminderScheduler).
 */
class OutboxWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val repo = (applicationContext as GratitudeGardenApplication).container.gardenRepository
        return try {
            // In a fresh process the stored session is still loading; judging it now would
            // find no token and put the delivery off by a whole backoff.
            repo.awaitSessionSettled(SESSION_WAIT)
            if (repo.drainOutbox()) Result.success() else Result.retry()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private companion object {
        val SESSION_WAIT = 15.seconds
    }
}

class WorkManagerOutboxScheduler(context: Context) : OutboxScheduler {

    private val workManager by lazy { WorkManager.getInstance(context) }

    /**
     * REPLACE, not KEEP: a worker that has just drained the queue and is about to finish
     * would otherwise swallow the request for an op queued in that instant, leaving it for
     * the next write or reconnect. Replacing only cancels the worker's wait; the delivery
     * itself runs in the repository's scope and finishes, and every op is safe to resend.
     */
    override fun schedule() {
        val request = OneTimeWorkRequestBuilder<OutboxWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    override fun cancel() {
        workManager.cancelUniqueWork(WORK_NAME)
    }

    private companion object {
        const val WORK_NAME = "journal-outbox"
    }
}
