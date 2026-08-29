package com.dadsvictory.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.dadsvictory.MainActivity
import com.dadsvictory.R
import com.dadsvictory.domain.NotificationSlot
import com.dadsvictory.domain.content.NotificationContent

/**
 * Notification channels and posting.
 *
 * Three a day, and that is the whole of it. There is deliberately no code path
 * that posts an extra motivational notification, because an app that nags is an
 * app that gets its notifications turned off — and then it helps with nothing.
 */
object Notifications {

    const val CHANNEL_DAILY = "daily_encouragement"
    const val CHANNEL_CRAVING = "craving_support"

    const val EXTRA_OPEN_CRAVING = "com.dadsvictory.OPEN_CRAVING"

    private const val REQUEST_OPEN_APP = 900
    private const val REQUEST_OPEN_CRAVING = 901

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        val daily = NotificationChannel(
            CHANNEL_DAILY,
            "Daily encouragement",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Three encouragements a day: morning, afternoon and evening."
            setShowBadge(false)
        }

        val craving = NotificationChannel(
            CHANNEL_CRAVING,
            "Craving support",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Support while a craving is passing."
            setShowBadge(false)
        }

        manager.createNotificationChannel(daily)
        manager.createNotificationChannel(craving)
    }

    fun hasPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun areEnabled(context: Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()

    private fun openAppIntent(context: Context, openCraving: Boolean): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (openCraving) putExtra(EXTRA_OPEN_CRAVING, true)
        }
        return PendingIntent.getActivity(
            context,
            if (openCraving) REQUEST_OPEN_CRAVING else REQUEST_OPEN_APP,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun show(context: Context, slot: NotificationSlot, message: NotificationContent.Message) {
        if (!hasPermission(context)) return

        val builder = NotificationCompat.Builder(context, CHANNEL_DAILY)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(message.title)
            .setContentText(message.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message.expanded ?: message.body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(context, openCraving = false))

        if (message.showCravingAction) {
            builder.addAction(
                R.drawable.ic_notification,
                "I'M HAVING A CRAVING",
                openAppIntent(context, openCraving = true),
            )
        }

        // The permission is re-checked inline here rather than only through
        // hasPermission() above: lint cannot follow the guard through a helper, and
        // it is right to insist — the check and the call want to be next to each
        // other so a future edit cannot separate them. The catch covers the gap
        // where permission is revoked between the two.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        try {
            NotificationManagerCompat.from(context).notify(slot.ordinal, builder.build())
        } catch (_: SecurityException) {
            // Permission was revoked between the check and the call. Nothing to do:
            // a missed encouragement must never crash the app.
        }
    }
}
