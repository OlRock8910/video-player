import com.dadsvictory.domain.Profile
import com.dadsvictory.domain.Slip
import com.dadsvictory.domain.Streaks
import com.dadsvictory.domain.Substance
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val DAY = Streaks.MILLIS_PER_DAY
private const val HOUR = Streaks.MILLIS_PER_HOUR

/** A fixed instant so nothing here depends on when the test happens to run. */
private const val START = 1_700_000_000_000L

class StreakTest {

    private fun stats(now: Long, slips: List<Slip> = emptyList(), substance: Substance = Substance.NICOTINE) =
        Streaks.statsFor(substance, START, now, slips)

    private fun slip(offsetMillis: Long, vararg substances: Substance) =
        Slip(atMillis = START + offsetMillis, substances = substances.toSet())

    @Test
    fun `a clean run counts whole elapsed days`() {
        assertEquals(17, stats(START + 17 * DAY).currentStreakDays)
        assertEquals(17, stats(START + 17 * DAY).bestStreakDays)
        assertEquals(17, stats(START + 17 * DAY).totalFreeDays)
        assertEquals(0, stats(START + 17 * DAY).slipCount)
    }

    @Test
    fun `a day only counts once it is complete`() {
        assertEquals(0, stats(START + 23 * HOUR).currentStreakDays)
        assertEquals(1, stats(START + 24 * HOUR).currentStreakDays)
        assertEquals(1, stats(START + 24 * HOUR + 1).currentStreakDays)
    }

    @Test
    fun `partial day is reported as hours and minutes so day zero still moves`() {
        val s = stats(START + 5 * HOUR + 30 * Streaks.MILLIS_PER_MINUTE)
        assertEquals(0, s.currentStreakDays)
        assertEquals(5, s.currentStreakHoursPart)
        assertEquals(30, s.currentStreakMinutesPart)
        assertEquals("5 hours", Streaks.describe(s))
    }

    @Test
    fun `singular wording for one day and one hour`() {
        assertEquals("1 day", Streaks.describe(stats(START + DAY)))
        assertEquals("1 hour", Streaks.describe(stats(START + HOUR)))
        assertEquals("1 minute", Streaks.describe(stats(START + Streaks.MILLIS_PER_MINUTE)))
    }

    /**
     * The count is elapsed-time based, so it cannot jump when the clock moves for
     * daylight saving or a time-zone change. Ten days is ten days everywhere.
     */
    @Test
    fun `streak is unaffected by midnight, daylight saving and time zones`() {
        val tenDays = stats(START + 10 * DAY).currentStreakDays
        // Same elapsed time, an hour shifted either way by a clock change.
        assertEquals(tenDays, Streaks.statsFor(Substance.NICOTINE, START - HOUR, START - HOUR + 10 * DAY, emptyList()).currentStreakDays)
        assertEquals(tenDays, Streaks.statsFor(Substance.NICOTINE, START + HOUR, START + HOUR + 10 * DAY, emptyList()).currentStreakDays)
    }

    @Test
    fun `crossing midnight mid-day does not add a day early`() {
        // Start at 23:00 on some day; four hours later it is "tomorrow" but not a day yet.
        val s = stats(START + 4 * HOUR)
        assertEquals(0, s.currentStreakDays)
    }

    @Test
    fun `a slip restarts the current streak but keeps the best and the history`() {
        val slips = listOf(slip(20 * DAY, Substance.NICOTINE))
        val s = stats(START + 25 * DAY, slips)

        assertEquals(5, s.currentStreakDays, "current streak runs from the slip")
        assertEquals(20, s.bestStreakDays, "the 20 days he did are not taken away")
        assertEquals(25, s.totalFreeDays)
        assertEquals(1, s.slipCount)
        assertEquals(START + 20 * DAY, s.currentSegmentStartMillis)
    }

    @Test
    fun `total free days loses only the part-day at each slip`() {
        // Slip halfway through a day, twice.
        val slips = listOf(
            slip(10 * DAY + 12 * HOUR, Substance.NICOTINE),
            slip(20 * DAY + 6 * HOUR, Substance.NICOTINE),
        )
        val s = stats(START + 30 * DAY, slips)
        // Segments: 10.5d -> 10, 9.75d -> 9, 9.75d -> 9
        assertEquals(28, s.totalFreeDays)
        assertEquals(10, s.bestStreakDays)
        assertEquals(2, s.slipCount)
    }

    @Test
    fun `best streak includes the run currently in progress`() {
        val slips = listOf(slip(3 * DAY, Substance.NICOTINE))
        assertEquals(40, stats(START + 43 * DAY, slips).bestStreakDays)
    }

    @Test
    fun `nicotine and alcohol are tracked separately`() {
        val slips = listOf(slip(10 * DAY, Substance.ALCOHOL))
        val now = START + 15 * DAY

        assertEquals(15, stats(now, slips, Substance.NICOTINE).currentStreakDays)
        assertEquals(5, stats(now, slips, Substance.ALCOHOL).currentStreakDays)
    }

    @Test
    fun `a slip on both restarts both`() {
        val slips = listOf(slip(10 * DAY, Substance.NICOTINE, Substance.ALCOHOL))
        val now = START + 15 * DAY
        assertEquals(5, stats(now, slips, Substance.NICOTINE).currentStreakDays)
        assertEquals(5, stats(now, slips, Substance.ALCOHOL).currentStreakDays)
    }

    @Test
    fun `a future start date reads as not started rather than as negative time`() {
        val s = stats(START - 3 * DAY)
        assertTrue(s.notStartedYet)
        assertEquals(0, s.currentStreakDays)
        assertEquals(0L, s.currentStreakMillis)
        assertEquals("Not started yet", Streaks.describe(s))
    }

    @Test
    fun `slips outside the journey window are ignored`() {
        val slips = listOf(
            Slip(atMillis = START - 5 * DAY, substances = setOf(Substance.NICOTINE)), // before the start
            Slip(atMillis = START + 99 * DAY, substances = setOf(Substance.NICOTINE)), // in the future
        )
        val s = stats(START + 10 * DAY, slips)
        assertEquals(10, s.currentStreakDays)
        assertEquals(0, s.slipCount)
    }

    @Test
    fun `unordered slips still produce ordered segments`() {
        val slips = listOf(slip(20 * DAY, Substance.NICOTINE), slip(5 * DAY, Substance.NICOTINE))
        val segments = Streaks.segmentsFor(Substance.NICOTINE, START, START + 30 * DAY, slips)
        assertEquals(3, segments.size)
        assertTrue(segments.zipWithNext().all { (a, b) -> a.endMillis == b.startMillis })
        assertTrue(segments.all { it.durationMillis >= 0 })
    }

    @Test
    fun `headline streak takes the shorter of the two so it never overstates`() {
        val profile = Profile(quitNicotine = true, quitAlcohol = true, startMillis = START)
        val slips = listOf(slip(12 * DAY, Substance.ALCOHOL))
        assertEquals(5, Streaks.headlineStreakDays(profile, START + 17 * DAY, slips))
    }

    @Test
    fun `headline streak for a single substance uses that substance`() {
        val profile = Profile(quitNicotine = true, quitAlcohol = false, startMillis = START)
        val slips = listOf(slip(12 * DAY, Substance.ALCOHOL))
        assertEquals(17, Streaks.headlineStreakDays(profile, START + 17 * DAY, slips))
    }

    @Test
    fun `journey days keep running even through slips`() {
        val slips = listOf(slip(5 * DAY, Substance.NICOTINE), slip(9 * DAY, Substance.NICOTINE))
        assertEquals(30, Streaks.journeyDays(START, START + 30 * DAY))
        assertEquals(3, Streaks.segmentsFor(Substance.NICOTINE, START, START + 30 * DAY, slips).size)
    }

    @Test
    fun `a clock wound backwards cannot produce negative time`() {
        val s = stats(START - 1000)
        assertEquals(0L, s.currentStreakMillis)
        assertFalse(s.currentStreakDays < 0)
    }
}
