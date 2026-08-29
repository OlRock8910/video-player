package com.dadsvictory.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.dadsvictory.data.prefs.AppSettings
import com.dadsvictory.domain.NotificationSchedule
import com.dadsvictory.domain.NotificationSlot
import java.time.ZoneId

/**
 * Schedules the three daily encouragements.
 *
 * Why AlarmManager and not WorkManager: these need to arrive at a time of day he
 * chose, and WorkManager's windows drift. Why [AlarmManager.setAndAllowWhileIdle]
 * and not an exact alarm: exact alarms need a special permission on Android 12+
 * that is reserved for alarm-clock-grade timing, and a morning encouragement that
 * lands a few minutes late is fine. It survives Doze, which is the part that
 * actually matters.
 *
 * Each firing schedules the next one, and [BootReceiver] re-arms everything after
 * a restart, because alarms do not survive a reboot.
 */
object DailyAlarmScheduler {

    const val ACTION_DAILY = "com.dadsvictory.action.DAILY_NOTIFICATION"
    const val EXTRA_SLOT = "slot"

    private const val REQUEST_BASE = 1000

    private fun pendingIntent(context: Context, slot: NotificationSlot): PendingIntent {
        val intent = Intent(context, DailyNotificationReceiver::class.java).apply {
            action = ACTION_DAILY
            putExtra(EXTRA_SLOT, slot.id)
            // Distinct data keeps the three PendingIntents from collapsing into one.
            setPackage(context.packageName)
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_BASE + slot.ordinal,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** Re-arms every slot from the current settings. Safe to call as often as you like. */
    fun rescheduleAll(context: Context, settings: AppSettings, nowMillis: Long = System.currentTimeMillis()) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val zone = ZoneId.systemDefault()

        for (slot in NotificationSlot.entries) {
            val setting = settings.notifications[slot] ?: continue
            val intent = pendingIntent(context, slot)

            if (!setting.enabled || !settings.onboardingComplete) {
                alarmManager.cancel(intent)
                continue
            }

            val triggerAt = NotificationSchedule.nextTriggerMillis(setting.minuteOfDay, nowMillis, zone)
            runCatching {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, intent)
            }
        }
    }

    /** Schedules only the next occurrence of one slot — used right after it fires. */
    fun scheduleNext(context: Context, slot: NotificationSlot, minuteOfDay: Int, nowMillis: Long = System.currentTimeMillis()) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val triggerAt = NotificationSchedule.nextTriggerMillis(minuteOfDay, nowMillis, ZoneId.systemDefault())
        runCatching {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent(context, slot))
        }
    }

    fun cancelAll(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        for (slot in NotificationSlot.entries) {
            alarmManager.cancel(pendingIntent(context, slot))
        }
    }
}
