package com.dadsvictory.domain

import kotlin.math.max
import kotlin.math.roundToLong

/**
 * Money and "avoided" estimates.
 *
 * Everything here is an estimate built from numbers he typed in during setup, and
 * the UI is required to say so. The arithmetic is done in minor units (pence or
 * cents) as Long, never in floating point, so the total shown never drifts.
 *
 * How a slip is handled: the running total is proportional to elapsed time, and
 * each recorded slip deducts roughly one day of spending — because on a day he
 * bought it, he did spend the money. It is accounting, not punishment: the total
 * keeps its history and carries on climbing from where it was.
 */
object Money {

    data class Saved(
        val nicotineMinor: Long,
        val alcoholMinor: Long,
    ) {
        val totalMinor: Long get() = nicotineMinor + alcoholMinor
    }

    fun dailySpendMinor(weeklySpendMinor: Long): Long = (weeklySpendMinor.toDouble() / 7.0).roundToLong()

    private fun savedForSubstance(
        substance: Substance,
        weeklySpendMinor: Long,
        profile: Profile,
        nowMillis: Long,
        slips: List<Slip>,
    ): Long {
        if (weeklySpendMinor <= 0L) return 0L
        if (substance !in profile.substances) return 0L
        val abstinent = Streaks.abstinentMillis(substance, profile.startMillis, nowMillis, slips)
        if (abstinent <= 0L) return 0L

        val gross = weeklySpendMinor.toDouble() * abstinent.toDouble() / (7.0 * Streaks.MILLIS_PER_DAY)
        val slipCount = slips.count {
            substance in it.substances && it.atMillis in (profile.startMillis + 1) until nowMillis
        }
        val deduction = dailySpendMinor(weeklySpendMinor) * slipCount
        return max(0L, gross.roundToLong() - deduction)
    }

    fun savedSoFar(profile: Profile, nowMillis: Long, slips: List<Slip>): Saved = Saved(
        nicotineMinor = savedForSubstance(
            Substance.NICOTINE, profile.nicotineWeeklySpendMinor, profile, nowMillis, slips,
        ),
        alcoholMinor = savedForSubstance(
            Substance.ALCOHOL, profile.alcoholWeeklySpendMinor, profile, nowMillis, slips,
        ),
    )

    /** Combined weekly outlay he described, used for the daily/weekly/monthly/yearly breakdown. */
    fun weeklyTotalMinor(profile: Profile): Long {
        var total = 0L
        if (profile.quitNicotine) total += profile.nicotineWeeklySpendMinor
        if (profile.quitAlcohol) total += profile.alcoholWeeklySpendMinor
        return total
    }

    data class Projection(
        val dailyMinor: Long,
        val weeklyMinor: Long,
        val monthlyMinor: Long,
        val yearlyMinor: Long,
    )

    /** Uses a 365.25-day year and the twelfth of that for a month, so the two agree. */
    fun projection(profile: Profile): Projection {
        val weekly = weeklyTotalMinor(profile)
        val yearly = (weekly.toDouble() * 365.25 / 7.0).roundToLong()
        return Projection(
            dailyMinor = dailySpendMinor(weekly),
            weeklyMinor = weekly,
            monthlyMinor = (yearly.toDouble() / 12.0).roundToLong(),
            yearlyMinor = yearly,
        )
    }

    /**
     * Estimated vaping sessions not taken, from the sessions-per-day figure he gave.
     * Deliberately not converted into milligrams of nicotine: the app cannot know
     * that, and pretending otherwise would be a fabricated health number.
     */
    fun vapesAvoided(profile: Profile, nowMillis: Long, slips: List<Slip>): Long {
        if (!profile.quitNicotine || profile.vapeSessionsPerDay <= 0) return 0L
        val abstinent = Streaks.abstinentMillis(Substance.NICOTINE, profile.startMillis, nowMillis, slips)
        if (abstinent <= 0L) return 0L
        return (profile.vapeSessionsPerDay.toDouble() * abstinent.toDouble() / Streaks.MILLIS_PER_DAY).toLong()
    }

    /** Estimated drinks not had, on whichever basis he chose. */
    fun drinksAvoided(profile: Profile, nowMillis: Long, slips: List<Slip>): Long {
        if (!profile.quitAlcohol || profile.drinksPerWeek <= 0.0) return 0L
        val abstinent = Streaks.abstinentMillis(Substance.ALCOHOL, profile.startMillis, nowMillis, slips)
        if (abstinent <= 0L) return 0L
        return (profile.drinksPerWeek * abstinent.toDouble() / (7.0 * Streaks.MILLIS_PER_DAY)).toLong()
    }

    /**
     * Formats minor units without pulling in a locale-dependent formatter, so the
     * symbol he picked is always the symbol he sees.
     */
    fun format(minor: Long, currency: Currency, showPence: Boolean = true): String {
        val negative = minor < 0
        val abs = if (negative) -minor else minor
        val whole = abs / 100
        val part = (abs % 100).toInt()
        val grouped = groupThousands(whole)
        val body = if (showPence) {
            "$grouped.${part.toString().padStart(2, '0')}"
        } else {
            grouped
        }
        return (if (negative) "-" else "") + currency.symbol + body
    }

    /** Rounded to the nearest whole unit — used where pence would just be noise. */
    fun formatWhole(minor: Long, currency: Currency): String =
        currency.symbol + groupThousands((minor.toDouble() / 100.0).roundToLong())

    private fun groupThousands(value: Long): String {
        val digits = value.toString()
        if (digits.length <= 3) return digits
        val builder = StringBuilder()
        for ((index, ch) in digits.withIndex()) {
            if (index > 0 && (digits.length - index) % 3 == 0) builder.append(',')
            builder.append(ch)
        }
        return builder.toString()
    }

    /**
     * Parses what he types into minor units.
     *
     * Forgiving on purpose: a currency symbol, spaces, thousands separators and
     * either a dot or a comma as the decimal mark all work, because being told
     * "invalid input" while setting up a quit app is a reason to close it.
     * Returns null only when there is genuinely no number in there.
     */
    fun parseToMinor(text: String): Long? {
        val cleaned = text.trim()
            .filter { it.isDigit() || it == '.' || it == ',' }
            .let { stripThousandsSeparators(it) }
        if (cleaned.isEmpty()) return null

        val parts = cleaned.split('.')
        if (parts.size > 2) return null

        val whole = parts[0].ifEmpty { "0" }
        if (whole.length > 12) return null // absurd input, not worth overflowing for
        val fraction = parts.getOrNull(1).orEmpty().padEnd(2, '0').take(2)

        val wholeValue = whole.toLongOrNull() ?: return null
        val fractionValue = fraction.toLongOrNull() ?: return null
        return wholeValue * 100 + fractionValue
    }

    /**
     * Decides which of "1,234.56", "1.234,56" and "1234,56" is meant, then
     * normalises to a single dot decimal mark.
     */
    private fun stripThousandsSeparators(input: String): String {
        val lastDot = input.lastIndexOf('.')
        val lastComma = input.lastIndexOf(',')
        return when {
            lastDot >= 0 && lastComma >= 0 ->
                // Whichever comes last is the decimal mark.
                if (lastDot > lastComma) input.replace(",", "") else input.replace(".", "").replace(',', '.')
            lastComma >= 0 -> {
                // A lone comma is a decimal mark unless it is grouping digits (1,234).
                val after = input.length - lastComma - 1
                if (after == 3 && input.count { it == ',' } >= 1 && lastComma > 0 && input.length > 4) {
                    input.replace(",", "")
                } else {
                    input.replace(',', '.')
                }
            }
            else -> input
        }
    }

    /** Turns minor units back into something editable, without the currency symbol. */
    fun toEditableText(minor: Long): String =
        if (minor % 100 == 0L) (minor / 100).toString() else "%d.%02d".format(minor / 100, minor % 100)

    /** Progress towards his savings goal, clamped to 0..1 so the bar can never overflow. */
    fun goalProgress(savedMinor: Long, goalMinor: Long): Float {
        if (goalMinor <= 0L) return 0f
        return (savedMinor.toDouble() / goalMinor.toDouble()).coerceIn(0.0, 1.0).toFloat()
    }

    /** Celebration thresholds, in whole currency units. */
    val CELEBRATION_THRESHOLDS: List<Long> = listOf(5, 10, 25, 50, 100, 250, 500, 1_000, 2_000)

    /** The largest milestone he has passed, or null before the first one. */
    fun lastCelebration(savedMinor: Long): Long? =
        CELEBRATION_THRESHOLDS.lastOrNull { it * 100 <= savedMinor }

    fun nextCelebration(savedMinor: Long): Long? =
        CELEBRATION_THRESHOLDS.firstOrNull { it * 100 > savedMinor }
}
