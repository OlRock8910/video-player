import com.dadsvictory.domain.Currency
import com.dadsvictory.domain.Money
import com.dadsvictory.domain.Profile
import com.dadsvictory.domain.Slip
import com.dadsvictory.domain.Streaks
import com.dadsvictory.domain.Substance
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val DAY = Streaks.MILLIS_PER_DAY
private const val START = 1_700_000_000_000L

class MoneyTest {

    /** £35/week on vaping, £70/week on alcohol. */
    private val profile = Profile(
        quitNicotine = true,
        quitAlcohol = true,
        startMillis = START,
        nicotineWeeklySpendMinor = 3_500,
        alcoholWeeklySpendMinor = 7_000,
        vapeSessionsPerDay = 8,
        drinksPerWeek = 21.0,
        currency = Currency.GBP,
    )

    @Test
    fun `saving accrues proportionally to time`() {
        val saved = Money.savedSoFar(profile, START + 7 * DAY, emptyList())
        assertEquals(3_500, saved.nicotineMinor)
        assertEquals(7_000, saved.alcoholMinor)
        assertEquals(10_500, saved.totalMinor)
    }

    @Test
    fun `saving is proportional part-way through a week`() {
        val saved = Money.savedSoFar(profile, START + DAY, emptyList())
        assertEquals(500, saved.nicotineMinor) // £35 / 7
        assertEquals(1_000, saved.alcoholMinor)
    }

    @Test
    fun `nothing is saved before the start date`() {
        assertEquals(0L, Money.savedSoFar(profile, START - DAY, emptyList()).totalMinor)
    }

    @Test
    fun `a slip deducts about one day of that substance only`() {
        val slips = listOf(Slip(atMillis = START + 3 * DAY, substances = setOf(Substance.NICOTINE)))
        val saved = Money.savedSoFar(profile, START + 7 * DAY, slips)

        assertEquals(3_500 - 500, saved.nicotineMinor, "one day of vaping money was actually spent")
        assertEquals(7_000, saved.alcoholMinor, "an alcohol slip did not happen, so nothing is deducted")
    }

    @Test
    fun `the total never goes negative even with many slips`() {
        val slips = (1..30).map {
            Slip(atMillis = START + it * (DAY / 2), substances = setOf(Substance.NICOTINE))
        }
        assertTrue(Money.savedSoFar(profile, START + 16 * DAY, slips).nicotineMinor >= 0)
    }

    @Test
    fun `a substance he is not quitting contributes nothing`() {
        val nicotineOnly = profile.copy(quitAlcohol = false)
        assertEquals(0L, Money.savedSoFar(nicotineOnly, START + 7 * DAY, emptyList()).alcoholMinor)
    }

    @Test
    fun `projection is internally consistent`() {
        val p = Money.projection(profile)
        assertEquals(10_500, p.weeklyMinor)
        assertEquals(1_500, p.dailyMinor)
        assertEquals(547_875, p.yearlyMinor) // £105.00/week * 365.25 / 7 = £5,478.75
        assertEquals(45_656, p.monthlyMinor) // a twelfth of that
        assertEquals("£5,478.75", Money.format(p.yearlyMinor, Currency.GBP))
    }

    @Test
    fun `monthly is a twelfth of yearly`() {
        val p = Money.projection(profile)
        assertTrue(kotlin.math.abs(p.monthlyMinor * 12 - p.yearlyMinor) <= 12)
    }

    @Test
    fun `currency formatting groups thousands and keeps the chosen symbol`() {
        assertEquals("£0.00", Money.format(0, Currency.GBP))
        assertEquals("£1.05", Money.format(105, Currency.GBP))
        assertEquals("£126.40", Money.format(12_640, Currency.GBP))
        assertEquals("£1,234.56", Money.format(123_456, Currency.GBP))
        assertEquals("£1,000,000.00", Money.format(100_000_000, Currency.GBP))
        assertEquals("$99.99", Money.format(9_999, Currency.USD))
        assertEquals("€5.00", Money.format(500, Currency.EUR))
        assertEquals("-£1.50", Money.format(-150, Currency.GBP))
    }

    @Test
    fun `whole formatting rounds and drops the pence`() {
        assertEquals("£126", Money.formatWhole(12_640, Currency.GBP))
        assertEquals("£127", Money.formatWhole(12_660, Currency.GBP))
        assertEquals("£1,235", Money.formatWhole(123_456, Currency.GBP))
    }

    @Test
    fun `goal progress is clamped between nothing and full`() {
        assertEquals(0f, Money.goalProgress(0, 100_000))
        assertEquals(0.25f, Money.goalProgress(25_000, 100_000))
        assertEquals(1f, Money.goalProgress(250_000, 100_000))
        assertEquals(0f, Money.goalProgress(5_000, 0), "no goal set means no progress bar")
    }

    @Test
    fun `celebration thresholds move forward and never repeat backwards`() {
        assertNull(Money.lastCelebration(100)) // £1
        assertEquals(5L, Money.lastCelebration(700))
        assertEquals(100L, Money.lastCelebration(24_999))
        assertEquals(250L, Money.lastCelebration(25_000))
        assertEquals(10L, Money.nextCelebration(700))
        assertNull(Money.nextCelebration(1_000_000))
    }

    @Test
    fun `vapes avoided uses the sessions he reported and stays an estimate`() {
        assertEquals(80, Money.vapesAvoided(profile, START + 10 * DAY, emptyList()))
        assertEquals(0, Money.vapesAvoided(profile.copy(vapeSessionsPerDay = 0), START + 10 * DAY, emptyList()))
        assertEquals(0, Money.vapesAvoided(profile, START - DAY, emptyList()))
    }

    @Test
    fun `drinks avoided uses drinks per week`() {
        assertEquals(21, Money.drinksAvoided(profile, START + 7 * DAY, emptyList()))
        assertEquals(0, Money.drinksAvoided(profile.copy(quitAlcohol = false), START + 7 * DAY, emptyList()))
    }

    @Test
    fun `someone who reported spending nothing simply sees nothing`() {
        val free = profile.copy(nicotineWeeklySpendMinor = 0, alcoholWeeklySpendMinor = 0)
        assertEquals(0L, Money.savedSoFar(free, START + 100 * DAY, emptyList()).totalMinor)
    }
}
