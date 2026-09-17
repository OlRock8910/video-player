package com.mono.music

import java.util.Calendar
import kotlin.math.max

/**
 * Listening statistics — the same arithmetic the desktop build does, ported so
 * the phone answers "how much have I listened to this week?" too.
 *
 * The play counts Mono already kept cannot answer that: they record how many
 * times a track has ever been played and when it was last played, which
 * collapses all history into one number and one timestamp. So playback also
 * writes an append-only log of listening stretches, and everything here is
 * derived from it.
 *
 * Time is time actually listened — wall clock while audio was playing — not
 * track length, so skipping through an album does not bank forty minutes.
 *
 * Nothing in this file touches an Android type, which is what lets it be tested
 * on the JVM.
 */

/** One stretch of listening: when it began, and how long audio actually played. */
data class Play(val docId: String, val startedAt: Long, val ms: Long)

/** What Stats needs to know about a track, so the library type stays out. */
data class TrackRef(val docId: String, val title: String, val artist: String)

/** Bucket size for the chart: one column per hour, day or month. */
enum class Grain { HOUR, DAY, MONTH }

data class StatsPeriod(val id: String, val label: String)

data class StatsRange(val from: Long, val to: Long, val grain: Grain)

data class BucketSlice(val start: Long, val label: String, val ms: Long)

data class TrackTotal(
    val docId: String,
    val title: String,
    val artist: String,
    val ms: Long,
    val count: Int,
)

data class ArtistTotal(val artist: String, val ms: Long, val count: Int)

data class StatsSummary(
    val totalMs: Long,
    val playCount: Int,
    val uniqueCount: Int,
    val activeDays: Int,
    val buckets: List<BucketSlice>,
    val topTracks: List<TrackTotal>,
    val topArtists: List<ArtistTotal>,
)

object Stats {

    val periods = listOf(
        StatsPeriod("today", "Today"),
        StatsPeriod("week", "7 days"),
        StatsPeriod("month", "30 days"),
        StatsPeriod("year", "12 months"),
        StatsPeriod("all", "All time"),
    )

    const val DEFAULT_PERIOD = "week"

    private const val HOUR_MS = 3_600_000L
    private const val DAY_MS = 86_400_000L

    private val WEEKDAYS = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
    private val MONTHS =
        listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

    private fun calendarAt(ms: Long): Calendar = Calendar.getInstance().apply { timeInMillis = ms }

    private fun startOfDay(ms: Long): Long = calendarAt(ms).apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun startOfMonth(ms: Long): Long = calendarAt(startOfDay(ms)).apply {
        set(Calendar.DAY_OF_MONTH, 1)
    }.timeInMillis

    private fun addMonths(ms: Long, count: Int): Long =
        calendarAt(ms).apply { add(Calendar.MONTH, count) }.timeInMillis

    /** The window a period covers, and the bucket size its chart should use. */
    fun rangeFor(
        id: String,
        plays: List<Play> = emptyList(),
        now: Long = System.currentTimeMillis(),
    ): StatsRange = when (id) {
        "today" -> StatsRange(startOfDay(now), now, Grain.HOUR)
        "week" -> StatsRange(startOfDay(now - 6 * DAY_MS), now, Grain.DAY)
        "month" -> StatsRange(startOfDay(now - 29 * DAY_MS), now, Grain.DAY)
        "year" -> StatsRange(addMonths(startOfMonth(now), -11), now, Grain.MONTH)
        else -> {
            val earliest = plays.minOfOrNull { it.startedAt } ?: now
            StatsRange(startOfMonth(earliest), now, Grain.MONTH)
        }
    }

    private fun bucketStarts(from: Long, to: Long, grain: Grain): List<Long> {
        val out = mutableListOf<Long>()
        when (grain) {
            Grain.HOUR -> {
                var t = from
                while (t <= to) {
                    out.add(t)
                    t += HOUR_MS
                }
            }

            Grain.DAY -> {
                var t = from
                while (t <= to) {
                    out.add(startOfDay(t))
                    t += DAY_MS
                }
            }

            Grain.MONTH -> {
                var t = startOfMonth(from)
                while (t <= to) {
                    out.add(t)
                    t = addMonths(t, 1)
                }
            }
        }
        return out
    }

    private fun endOf(start: Long, grain: Grain): Long = when (grain) {
        Grain.HOUR -> start + HOUR_MS
        Grain.DAY -> start + DAY_MS
        Grain.MONTH -> addMonths(start, 1)
    }

    private fun bucketLabel(start: Long, grain: Grain, spanDays: Int): String {
        val day = calendarAt(start)
        return when (grain) {
            // Built by hand rather than with String.format, which would pick up
            // the device locale and could render the hour in other digits.
            Grain.HOUR -> {
                val hour = day.get(Calendar.HOUR_OF_DAY)
                if (hour < 10) "0$hour:00" else "$hour:00"
            }
            Grain.MONTH -> MONTHS[day.get(Calendar.MONTH)]
            // A week reads better by weekday; a month of days needs the date.
            Grain.DAY -> if (spanDays <= 7) {
                WEEKDAYS[day.get(Calendar.DAY_OF_WEEK) - 1]
            } else {
                "${day.get(Calendar.DAY_OF_MONTH)} ${MONTHS[day.get(Calendar.MONTH)]}"
            }
        }
    }

    /** "2 hr 14 min", "47 min", "38 sec" — the long form used for totals. */
    fun formatSpan(ms: Long): String {
        val total = max(0L, (ms + 500) / 1000)
        if (total < 60) return "$total sec"
        val hours = total / 3600
        val minutes = ((total % 3600) + 30) / 60
        if (hours == 0L) return "$minutes min"
        if (minutes == 0L) return "$hours hr"
        return "$hours hr $minutes min"
    }

    fun summarise(
        plays: List<Play>,
        tracks: List<TrackRef>,
        from: Long,
        to: Long,
        grain: Grain,
    ): StatsSummary {
        val inRange = plays.filter { it.startedAt in from..to }
        val byDocId = tracks.associateBy { it.docId }

        val starts = bucketStarts(from, to, grain)
        val spanDays = ((to - from) / DAY_MS).toInt() + 1
        val labels = starts.map { bucketLabel(it, grain, spanDays) }
        val totals = LongArray(starts.size)

        val perTrack = LinkedHashMap<String, TrackTotal>()
        var totalMs = 0L

        for (play in inRange) {
            totalMs += play.ms

            // Buckets are contiguous and ordered, so a scan from the end finds
            // the owning bucket without a lookup table.
            for (i in starts.indices.reversed()) {
                if (play.startedAt >= starts[i] && play.startedAt < endOf(starts[i], grain)) {
                    totals[i] += play.ms
                    break
                }
            }

            val known = byDocId[play.docId]
            val seen = perTrack[play.docId] ?: TrackTotal(
                docId = play.docId,
                // A track played and since removed from the folder still
                // belongs in the totals; fall back to its file name.
                title = known?.title ?: play.docId.substringAfterLast('/'),
                artist = known?.artist ?: "Unknown artist",
                ms = 0L,
                count = 0,
            )
            perTrack[play.docId] = seen.copy(ms = seen.ms + play.ms, count = seen.count + 1)
        }

        val byArtist = LinkedHashMap<String, ArtistTotal>()
        for (track in perTrack.values) {
            val seen = byArtist[track.artist] ?: ArtistTotal(track.artist, 0L, 0)
            byArtist[track.artist] = seen.copy(ms = seen.ms + track.ms, count = seen.count + track.count)
        }

        return StatsSummary(
            totalMs = totalMs,
            playCount = inRange.size,
            uniqueCount = perTrack.size,
            // Averaged over days that had listening, not over the calendar — a
            // daily average that counts silent days says more about the window
            // than about the habit.
            activeDays = inRange.map { startOfDay(it.startedAt) }.toSet().size,
            buckets = starts.indices.map { BucketSlice(starts[it], labels[it], totals[it]) },
            topTracks = perTrack.values.sortedByDescending { it.ms }.take(5),
            topArtists = byArtist.values.sortedByDescending { it.ms }.take(5),
        )
    }
}
