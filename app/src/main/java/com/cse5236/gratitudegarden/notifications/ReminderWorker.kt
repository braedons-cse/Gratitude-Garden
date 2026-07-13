package com.cse5236.gratitudegarden.notifications

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.cse5236.gratitudegarden.MainActivity

/**
 * Posts the daily gratitude reminder, then re-arms itself for the next day.
 *
 * Instantiated by WorkManager's default reflective [androidx.work.WorkerFactory],
 * so no manifest registration is needed beyond the auto-installed WorkManager
 * initializer.
 */
class ReminderWorker(
    private val appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val settings = ReminderPreferences(appContext).current()

        // The user may have disabled reminders after this run was enqueued; if so,
        // post nothing and don't chain a successor — the chain stops here.
        if (settings.enabled) {
            postNotification()
            ReminderScheduler.schedule(appContext, settings.hour, settings.minute)
        }
        return Result.success()
    }

    private fun postNotification() {
        ReminderNotifications.ensureChannel(appContext)

        // API 33+: silently skip if the runtime grant is missing or was revoked.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val openApp = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentIntent = PendingIntent.getActivity(
            appContext,
            0,
            openApp,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val body = "Take a moment to type or speak one thing you're grateful for today " +
            "— keep your streak growing. 🌱"
        val notification = NotificationCompat.Builder(appContext, ReminderNotifications.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("Keep your gratitude streak going")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        NotificationManagerCompat.from(appContext)
            .notify(ReminderNotifications.NOTIFICATION_ID, notification)
    }
}
