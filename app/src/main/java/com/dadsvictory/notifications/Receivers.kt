package com.dadsvictory.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.dadsvictory.data.VictoryRepository
import com.dadsvictory.domain.Money
import com.dadsvictory.domain.NotificationSchedule
import com.dadsvictory.domain.NotificationSlot
import com.dadsvictory.domain.Streaks
import com.dadsvictory.domain.content.NotificationContent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.ZoneId

/**
 * Fires one daily encouragement, then arms the next one.
 *
 * The text depends on his streak and savings, so this has to read the database.
 * [goAsync] holds the broadcast open while that happens, and the work is finished
 * on a background dispatcher rather than the main thread.
 */
class DailyNotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DailyAlarmScheduler.ACTION_DAILY) return
        val slot = NotificationSlot.fromId(intent.getStringExtra(DailyAlarmScheduler.EXTRA_SLOT))
        val appContext = context.applicationContext
        val pendingResult = goAsync()

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                deliver(appContext, slot)
            } catch (_: Exception) {
                // A notification that fails must never take the app down with it.
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun deliver(context: Context, slot: NotificationSlot) {
        val repository = VictoryRepository(context)
        val settings = repository.settingsStore.current()

        // Always re-arm first, so a failure further down cannot end the daily habit.
        val minuteOfDay = settings.notifications[slot]?.minuteOfDay ?: slot.defaultMinuteOfDay
        DailyAlarmScheduler.scheduleNext(context, slot, minuteOfDay)

        if (!settings.onboardingComplete) return
        if (settings.notifications[slot]?.enabled != true) return

        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val slips = repository.slips.first()
        val profile = settings.profile

        val message = NotificationContent.build(
            slot = slot,
            profile = profile,
            currentStreakDays = Streaks.headlineStreakDays(profile, now, slips),
            savedMinor = Money.savedSoFar(profile, now, slips).totalMinor,
            currency = profile.currency,
            bibleVersion = settings.bibleVersion,
            dayIndex = NotificationSchedule.rotationIndex(now, zone),
        )

        Notifications.createChannels(context)
        Notifications.show(context, slot, message)
    }
}

/**
 * Alarms are dropped when the phone restarts, so they are rebuilt here. Without
 * this, notifications would silently stop after the first reboot — which is the
 * classic way a habit app quietly stops working.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val relevant = action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == Intent.ACTION_LOCKED_BOOT_COMPLETED ||
            action == "android.intent.action.QUICKBOOT_POWERON"
        if (!relevant) return

        val appContext = context.applicationContext
        val pendingResult = goAsync()

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val settings = VictoryRepository(appContext).settingsStore.current()
                Notifications.createChannels(appContext)
                DailyAlarmScheduler.rescheduleAll(appContext, settings)
            } catch (_: Exception) {
                // Nothing useful to do here; the next app launch reschedules anyway.
            } finally {
                pendingResult.finish()
            }
        }
    }
}

/**
 * Time zone and manual clock changes move when "8am" is. Re-arming on these keeps
 * the morning notification in the morning after a flight.
 */
class TimeChangedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_TIMEZONE_CHANGED && action != Intent.ACTION_TIME_CHANGED) return

        val appContext = context.applicationContext
        val pendingResult = goAsync()

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val settings = VictoryRepository(appContext).settingsStore.current()
                DailyAlarmScheduler.rescheduleAll(appContext, settings)
            } catch (_: Exception) {
                // Ignored: the next launch reschedules.
            } finally {
                pendingResult.finish()
            }
        }
    }
}
