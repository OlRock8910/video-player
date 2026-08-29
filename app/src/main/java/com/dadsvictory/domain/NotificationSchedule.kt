package com.dadsvictory.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * When the next daily encouragement should fire.
 *
 * Pure so it can be tested properly: getting this wrong means a notification at
 * 3am, or none at all, and both would quietly break the most important habit the
 * app is trying to build.
 */
object NotificationSchedule {

    /**
     * The next occurrence of [minuteOfDay] strictly after [nowMillis], in [zone].
     *
     * Uses [ZonedDateTime] rather than fixed 24-hour arithmetic so that clock
     * changes are handled by the calendar: on the day the clocks go forward, a
     * time that does not exist is moved to the nearest valid instant by java.time
     * rather than being skipped entirely.
     */
    fun nextTriggerMillis(minuteOfDay: Int, nowMillis: Long, zone: ZoneId): Long {
        val safeMinute = minuteOfDay.coerceIn(0, 24 * 60 - 1)
        val now = Instant.ofEpochMilli(nowMillis).atZone(zone)
        val todayAt = candidate(now.toLocalDate(), safeMinute, zone)
        return if (todayAt > nowMillis) todayAt else candidate(now.toLocalDate().plusDays(1), safeMinute, zone)
    }

    /**
     * Builds the wall-clock time directly rather than adding minutes to the start
     * of the day. Adding to a [ZonedDateTime] adds *elapsed* time, which on a
     * clock-change day would move an 08:00 alarm to 09:00 in spring and 07:00 in
     * autumn. Resolving the local time instead keeps 08:00 meaning 08:00, and lets
     * java.time apply its normal rules for a time that is skipped or repeated.
     */
    private fun candidate(date: LocalDate, minuteOfDay: Int, zone: ZoneId): Long =
        date.atTime(minuteOfDay / 60, minuteOfDay % 60)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()

    /** The day number used to rotate content, so every day gets a different message. */
    fun rotationIndex(nowMillis: Long, zone: ZoneId): Long =
        Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate().toEpochDay()

    /** Which greeting the dashboard should use right now. */
    fun greeting(nowMillis: Long, zone: ZoneId): String {
        val hour = Instant.ofEpochMilli(nowMillis).atZone(zone).hour
        return when (hour) {
            in 0..11 -> "Good morning, Dad ❤️"
            in 12..17 -> "Good afternoon, Dad ❤️"
            else -> "Good evening, Dad ❤️"
        }
    }

    fun formatTime(minuteOfDay: Int, use24Hour: Boolean): String {
        val safe = minuteOfDay.coerceIn(0, 24 * 60 - 1)
        val hour = safe / 60
        val minute = safe % 60
        val minuteText = minute.toString().padStart(2, '0')
        if (use24Hour) return "${hour.toString().padStart(2, '0')}:$minuteText"

        val suffix = if (hour < 12) "AM" else "PM"
        val display = when {
            hour == 0 -> 12
            hour > 12 -> hour - 12
            else -> hour
        }
        return "$display:$minuteText $suffix"
    }
}
