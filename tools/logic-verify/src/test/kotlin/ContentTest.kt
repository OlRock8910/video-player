import com.dadsvictory.domain.Country
import com.dadsvictory.domain.content.BibleVersion
import com.dadsvictory.domain.content.CravingPlan
import com.dadsvictory.domain.content.DailyPlan
import com.dadsvictory.domain.content.Facts
import com.dadsvictory.domain.content.HealthMilestones
import com.dadsvictory.domain.content.Motivation
import com.dadsvictory.domain.content.Rotation
import com.dadsvictory.domain.content.Scripture
import com.dadsvictory.domain.content.Sources
import com.dadsvictory.domain.content.Support
import com.dadsvictory.domain.content.Triggers
import com.dadsvictory.domain.content.VerseTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RotationTest {

    @Test
    fun `every item appears exactly once per cycle`() {
        val items = (1..7).map { "item$it" }
        repeat(6) { cycle ->
            val seen = (0 until items.size).map { Rotation.pick(items, (cycle * items.size + it).toLong()) }
            assertEquals(items.toSet(), seen.toSet(), "cycle $cycle did not cover every item")
            assertEquals(items.size, seen.distinct().size, "cycle $cycle repeated an item")
        }
    }

    @Test
    fun `consecutive picks never repeat, including across the cycle seam`() {
        val items = (1..5).map { "item$it" }
        val sequence = (0L until 60L).map { Rotation.pick(items, it) }
        val seam = sequence.zipWithNext().filter { (a, b) -> a == b }
        assertTrue(seam.isEmpty(), "same message twice in a row: $seam")
    }

    @Test
    fun `the same index always gives the same message`() {
        val items = (1..9).map { "item$it" }
        repeat(50) { assertEquals(Rotation.pick(items, it.toLong()), Rotation.pick(items, it.toLong())) }
    }

    @Test
    fun `negative indices are handled rather than crashing`() {
        val items = listOf("a", "b", "c")
        assertTrue(Rotation.pick(items, -5L) in items)
    }

    @Test
    fun `a single-item list is allowed`() {
        assertEquals("only", Rotation.pick(listOf("only"), 42L))
    }
}

class MotivationTest {

    @Test
    fun `there are well over a hundred messages`() {
        assertTrue(
            Motivation.totalMessageCount() >= 100,
            "only ${Motivation.totalMessageCount()} messages",
        )
    }

    @Test
    fun `each of the three daily slots has plenty so mornings do not repeat quickly`() {
        assertTrue(Motivation.MORNING.size >= 30, "morning: ${Motivation.MORNING.size}")
        assertTrue(Motivation.AFTERNOON.size >= 30, "afternoon: ${Motivation.AFTERNOON.size}")
        assertTrue(Motivation.EVENING.size >= 30, "evening: ${Motivation.EVENING.size}")
    }

    @Test
    fun `no message is duplicated inside a category`() {
        for (category in Motivation.Category.entries) {
            val messages = Motivation.messages(category)
            assertEquals(messages.size, messages.distinct().size, "duplicate in $category")
        }
    }

    @Test
    fun `no message is blank`() {
        for (category in Motivation.Category.entries) {
            assertTrue(Motivation.messages(category).none { it.isBlank() }, "blank message in $category")
        }
    }

    /**
     * The whole promise of this app is that it never shames him. This test is the
     * enforcement of that promise, so a careless line added later cannot slip in.
     */
    @Test
    fun `nothing shames him`() {
        val banned = listOf(
            "you failed", "failure", "you ruined", "worthless", "pathetic", "disappointing",
            "let everyone down", "weak-willed", "no excuse", "should be ashamed", "shameful",
            "you blew it", "wasted", "hopeless", "give up on you",
        )
        for (category in Motivation.Category.entries) {
            for (message in Motivation.messages(category)) {
                val lower = message.lowercase()
                val hit = banned.firstOrNull { lower.contains(it) }
                assertTrue(hit == null, "$category message uses shaming language ('$hit'): $message")
            }
        }
    }

    @Test
    fun `the relapse messages are recovery-shaped, never punitive`() {
        assertTrue(Motivation.RELAPSE.any { it.contains("does not erase", ignoreCase = true) })
        assertTrue(Motivation.RELAPSE.any { it.contains("honest", ignoreCase = true) })
        assertFalse(Motivation.RELAPSE.any { it.contains("start over from zero", ignoreCase = true) })
    }

    @Test
    fun `a morning notification is stable across a reboot for the same day index`() {
        assertEquals(Motivation.pick(Motivation.Category.MORNING, 400L), Motivation.pick(Motivation.Category.MORNING, 400L))
    }

    @Test
    fun `craving timer messages change as the countdown runs`() {
        val early = CravingPlan.messageForSecondsRemaining(600, seed = 3L)
        val later = CravingPlan.messageForSecondsRemaining(300, seed = 3L)
        assertTrue(early.isNotBlank() && later.isNotBlank())
        assertFalse(early == later, "the message should have moved on by the halfway point")
    }
}

class ScriptureTest {

    @Test
    fun `every theme has verses`() {
        for (theme in VerseTheme.entries) {
            assertTrue(Scripture.byTheme(theme).isNotEmpty(), "no verses for ${theme.label}")
        }
    }

    @Test
    fun `every verse has both translations and a reference`() {
        for (verse in Scripture.ALL) {
            assertTrue(verse.reference.isNotBlank(), "missing reference")
            for (version in BibleVersion.entries) {
                assertTrue(verse.text(version).isNotBlank(), "${verse.reference} missing ${version.abbreviation}")
            }
        }
    }

    @Test
    fun `the specific verses the app was asked for are present`() {
        val required = listOf(
            "James 1:4", "Philippians 4:13", "Isaiah 40:31", "Galatians 5:22-23",
            "2 Timothy 1:7", "Psalm 46:1", "Romans 12:2", "Hebrews 12:1",
        )
        for (reference in required) {
            assertNotNull(Scripture.byReference(reference), "missing $reference")
        }
    }

    @Test
    fun `references are unique`() {
        val references = Scripture.ALL.map { it.reference }
        assertEquals(references.size, references.distinct().size)
    }

    @Test
    fun `the daily verse is the same all day and different tomorrow`() {
        val today = Scripture.daily(20_000L)
        assertEquals(today, Scripture.daily(20_000L))
        assertFalse(today == Scripture.daily(20_001L))
    }

    @Test
    fun `craving verses come from the themes that suit a craving`() {
        repeat(20) { assertTrue(Scripture.forCraving(it.toLong()).theme in Scripture.CRAVING_THEMES) }
    }
}

class SourcedContentTest {

    @Test
    fun `every fact resolves to a real source`() {
        for (fact in Facts.ALL) {
            assertNotNull(fact.source, "${fact.id} points at a source that does not exist: ${fact.sourceId}")
            assertTrue(fact.headline.isNotBlank() && fact.body.isNotBlank(), "${fact.id} is incomplete")
        }
    }

    @Test
    fun `every health milestone resolves to a real source`() {
        for (milestone in HealthMilestones.ALL) {
            assertNotNull(milestone.source, "${milestone.id} has no resolvable source")
            assertTrue(milestone.body.isNotBlank())
        }
    }

    @Test
    fun `every source names an organisation and a link`() {
        for (source in Sources.ALL) {
            assertTrue(source.organisation.isNotBlank(), "${source.id} has no organisation")
            assertTrue(source.url.startsWith("https://"), "${source.id} has a non-https url")
        }
    }

    @Test
    fun `source ids are unique`() {
        assertEquals(Sources.ALL.size, Sources.ALL.map { it.id }.distinct().size)
    }

    @Test
    fun `figures that name a number also name the period and population`() {
        val quantified = listOf("alc_deaths_us", "nic_quit_intentions")
        for (id in quantified) {
            val fact = Facts.byId(id)
            assertNotNull(fact, "missing fact $id")
            assertNotNull(fact.period, "$id states a figure without a period")
            assertNotNull(fact.population, "$id states a figure without a population")
        }
    }

    @Test
    fun `the US death figure is labelled as a US figure`() {
        val fact = Facts.byId("alc_deaths_us")!!
        assertTrue(fact.population!!.contains("United States"))
        assertTrue(fact.body.contains("United States estimate"))
    }

    @Test
    fun `the app is honest that vaping science is unsettled`() {
        val fact = Facts.byId("nic_long_term_unknown")
        assertNotNull(fact)
        assertTrue(fact.headline.contains("still learning", ignoreCase = true))
    }

    @Test
    fun `milestones are relevant to what he is quitting`() {
        val nicotineOnly = HealthMilestones.timelineFor(quitNicotine = true, quitAlcohol = false)
        assertTrue(nicotineOnly.none { it.id.startsWith("alc_") })

        val alcoholOnly = HealthMilestones.timelineFor(quitNicotine = false, quitAlcohol = true)
        assertTrue(alcoholOnly.none { it.id.startsWith("nic_") })
    }

    @Test
    fun `the timeline is ordered and the next milestone is genuinely ahead`() {
        val timeline = HealthMilestones.timelineFor(true, true)
        assertEquals(timeline.map { it.afterDays }.sorted(), timeline.map { it.afterDays })

        val next = HealthMilestones.nextAhead(true, true, daysFree = 10)
        assertNotNull(next)
        assertTrue(next.afterDays > 10)
    }

    @Test
    fun `there is a disclaimer that this is not medical advice`() {
        assertTrue(Sources.DISCLAIMER.contains("does not replace medical advice"))
        assertTrue(Support.NOT_A_DOCTOR.contains("not a doctor"))
    }
}

class SupportTest {

    @Test
    fun `the UK experience shows 999 and never a US number`() {
        val uk = Support.contactsFor(Country.UK)
        assertEquals("999", Support.emergencyFor(Country.UK).phone)
        assertTrue(uk.none { it.phone == "911" || it.phone == "988" || it.phone == "18007848669" })
        assertTrue(uk.any { it.phone == "111" }, "NHS 111 should be offered")
        assertTrue(uk.any { it.phone == "116123" }, "Samaritans should be offered")
    }

    @Test
    fun `the US experience shows 911, 988 and the quitline`() {
        val us = Support.contactsFor(Country.US)
        assertEquals("911", Support.emergencyFor(Country.US).phone)
        assertTrue(us.any { it.phone == "988" })
        assertTrue(us.any { it.phone == "18007848669" }, "1-800-QUIT-NOW should be offered")
        assertTrue(us.none { it.phone == "999" || it.phone == "111" })
    }

    @Test
    fun `every country has an emergency route and something to read`() {
        for (country in Country.entries) {
            assertTrue(Support.contactsFor(country).isNotEmpty())
            Support.emergencyFor(country) // throws if missing
        }
    }

    @Test
    fun `phone numbers are dialable digits only`() {
        for (country in Country.entries) {
            for (contact in Support.contactsFor(country)) {
                val phone = contact.phone ?: continue
                assertTrue(phone.all { it.isDigit() || it == '+' }, "${contact.id} has an undialable number: $phone")
            }
        }
    }

    @Test
    fun `the alcohol safety screen triggers on heavy or daily drinking`() {
        assertTrue(Support.shouldShowAlcoholSafetyScreen(drinksPerWeek = 21.0, drinkingDaysPerWeek = 3))
        assertTrue(Support.shouldShowAlcoholSafetyScreen(drinksPerWeek = 6.0, drinkingDaysPerWeek = 7))
        assertTrue(Support.shouldShowAlcoholSafetyScreen(drinksPerWeek = 14.0, drinkingDaysPerWeek = 2))
        assertFalse(Support.shouldShowAlcoholSafetyScreen(drinksPerWeek = 4.0, drinkingDaysPerWeek = 1))
    }

    @Test
    fun `the withdrawal warning says exactly what it needs to say`() {
        assertTrue(Support.ALCOHOL_WITHDRAWAL_WARNING.contains("dangerous withdrawal"))
        assertTrue(Support.ALCOHOL_WITHDRAWAL_WARNING.contains("before stopping abruptly"))
        assertTrue(Support.EMERGENCY_GUIDANCE.contains("seizures"))
        assertTrue(Support.EMERGENCY_GUIDANCE.contains("emergency medical help immediately"))
    }
}

class PlanAndTriggerTest {

    @Test
    fun `every trigger has a concrete strategy`() {
        for (trigger in Triggers.ALL) {
            assertTrue(trigger.strategy.length > 40, "${trigger.id} strategy is too vague")
        }
        assertEquals(Triggers.ALL.size, Triggers.ALL.map { it.id }.distinct().size)
    }

    @Test
    fun `the after-meals strategy is the specific one the brief asked for`() {
        val strategy = Triggers.byId("after_meals")!!.strategy
        assertTrue(strategy.contains("stand up", ignoreCase = true))
        assertTrue(strategy.contains("brush your teeth", ignoreCase = true))
        assertTrue(strategy.contains("water", ignoreCase = true))
        assertTrue(strategy.contains("leave the room", ignoreCase = true))
    }

    @Test
    fun `the daily plan drops tasks that do not apply`() {
        val nicotineOnly = DailyPlan.defaultsFor(quitNicotine = true, quitAlcohol = false)
        assertTrue(nicotineOnly.none { it.id == "m_alcohol" })
        assertTrue(nicotineOnly.any { it.id == "m_nicotine" })

        val alcoholOnly = DailyPlan.defaultsFor(quitNicotine = false, quitAlcohol = true)
        assertTrue(alcoholOnly.none { it.id == "m_nicotine" })
    }

    @Test
    fun `the plan covers all three parts of the day`() {
        val slots = DailyPlan.defaultsFor(true, true).map { it.slot }.toSet()
        assertEquals(DailyPlan.Slot.entries.toSet(), slots)
    }

    @Test
    fun `the craving timer is ten minutes`() {
        assertEquals(600, CravingPlan.TIMER_SECONDS)
        assertEquals(10, CravingPlan.BREATH_COUNT)
        assertTrue(CravingPlan.MOVE_OPTIONS.size >= 6)
        assertTrue(CravingPlan.OPENING.startsWith("STOP."))
    }
}
