package com.dadsvictory.domain

import kotlin.math.max

/**
 * Streak arithmetic.
 *
 * Design note, because it drives how the whole app feels:
 *
 * Streaks are measured in *elapsed time* from an instant, not in calendar dates.
 * A day is a completed 24-hour period. That makes the numbers immune to time
 * zones, daylight saving and the "did midnight already happen?" ambiguity, and
 * it means the count can never jump backwards when the clock changes.
 *
 * A slip is an instant, not a period. It ends the current segment and starts a
 * new one. Nothing else is thrown away: previous segments still count towards
 * the best streak and towards the total free days, which is the whole point —
 * one bad moment does not erase the progress.
 */
object Streaks {

    const val MILLIS_PER_DAY: Long = 24L * 60L * 60L * 1000L
    const val MILLIS_PER_HOUR: Long = 60L * 60L * 1000L
    const val MILLIS_PER_MINUTE: Long = 60L * 1000L

    /** One unbroken stretch of freedom, from [startMillis] until a slip or until now. */
    data class Segment(val startMillis: Long, val endMillis: Long) {
        val durationMillis: Long get() = max(0L, endMillis - startMillis)
        val wholeDays: Int get() = (durationMillis / MILLIS_PER_DAY).toInt()
    }

    data class Stats(
        /** Live length of the stretch he is in right now. */
        val currentStreakMillis: Long,
        val currentStreakDays: Int,
        /** Longest stretch he has ever put together, including the one in progress. */
        val bestStreakDays: Int,
        /** Whole free days added up across every stretch. Slips cost the part-day, not the history. */
        val totalFreeDays: Int,
        val slipCount: Int,
        /** When the current stretch began: the journey start, or his most recent slip. */
        val currentSegmentStartMillis: Long,
        /** True before the chosen start date has arrived, for a future-dated quit. */
        val notStartedYet: Boolean,
    ) {
        /** Remainder hours inside the current day, so day zero still shows movement. */
        val currentStreakHoursPart: Int
            get() = ((currentStreakMillis % MILLIS_PER_DAY) / MILLIS_PER_HOUR).toInt()

        val currentStreakMinutesPart: Int
            get() = ((currentStreakMillis % MILLIS_PER_HOUR) / MILLIS_PER_MINUTE).toInt()
    }

    /**
     * Split the journey into segments of freedom for one substance.
     *
     * Slips before the start instant or after [nowMillis] are ignored rather than
     * allowed to produce negative time; a clock that has been wound backwards
     * should not be able to corrupt the record.
     */
    fun segmentsFor(
        substance: Substance,
        startMillis: Long,
        nowMillis: Long,
        slips: List<Slip>,
    ): List<Segment> {
        if (nowMillis <= startMillis) return emptyList()
        val relevant = slips
            .filter { substance in it.substances }
            .map { it.atMillis }
            .filter { it in (startMillis + 1) until nowMillis }
            .sorted()

        val boundaries = buildList {
            add(startMillis)
            addAll(relevant)
        }
        return boundaries.mapIndexed { index, from ->
            val to = boundaries.getOrNull(index + 1) ?: nowMillis
            Segment(from, to)
        }
    }

    fun statsFor(
        substance: Substance,
        startMillis: Long,
        nowMillis: Long,
        slips: List<Slip>,
    ): Stats {
        val segments = segmentsFor(substance, startMillis, nowMillis, slips)
        val slipCount = slips.count { substance in it.substances && it.atMillis in (startMillis + 1) until nowMillis }

        if (segments.isEmpty()) {
            return Stats(
                currentStreakMillis = 0L,
                currentStreakDays = 0,
                bestStreakDays = 0,
                totalFreeDays = 0,
                slipCount = slipCount,
                currentSegmentStartMillis = startMillis,
                notStartedYet = nowMillis < startMillis,
            )
        }

        val current = segments.last()
        return Stats(
            currentStreakMillis = current.durationMillis,
            currentStreakDays = current.wholeDays,
            bestStreakDays = segments.maxOf { it.wholeDays },
            totalFreeDays = segments.sumOf { it.wholeDays },
            slipCount = slipCount,
            currentSegmentStartMillis = current.startMillis,
            notStartedYet = false,
        )
    }

    /**
     * Total abstinent time for one substance, used by the money and "vapes avoided"
     * estimates. With instantaneous slips this equals the elapsed journey; the
     * estimates deduct per-slip separately, in [Money].
     */
    fun abstinentMillis(
        substance: Substance,
        startMillis: Long,
        nowMillis: Long,
        slips: List<Slip>,
    ): Long = segmentsFor(substance, startMillis, nowMillis, slips).sumOf { it.durationMillis }

    /** Days since he began, regardless of slips. He is still on the journey either way. */
    fun journeyDays(startMillis: Long, nowMillis: Long): Int =
        if (nowMillis <= startMillis) 0 else ((nowMillis - startMillis) / MILLIS_PER_DAY).toInt()

    /**
     * The headline number across everything he is quitting: the shorter of the
     * active streaks, so the dashboard never overstates where he is.
     */
    fun headlineStreakDays(profile: Profile, nowMillis: Long, slips: List<Slip>): Int {
        val values = profile.substances.map {
            statsFor(it, profile.startMillis, nowMillis, slips).currentStreakDays
        }
        return values.minOrNull() ?: 0
    }

    /** "17 days", "14 hours", "just started" — a phrase that always reads honestly. */
    fun describe(stats: Stats): String = when {
        stats.notStartedYet -> "Not started yet"
        stats.currentStreakDays >= 1 ->
            "${stats.currentStreakDays} ${if (stats.currentStreakDays == 1) "day" else "days"}"
        stats.currentStreakHoursPart >= 1 ->
            "${stats.currentStreakHoursPart} ${if (stats.currentStreakHoursPart == 1) "hour" else "hours"}"
        else ->
            "${stats.currentStreakMinutesPart} ${if (stats.currentStreakMinutesPart == 1) "minute" else "minutes"}"
    }
}
