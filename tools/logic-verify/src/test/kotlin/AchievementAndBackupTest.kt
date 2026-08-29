import com.dadsvictory.domain.Achievements
import com.dadsvictory.domain.Currency
import com.dadsvictory.domain.backup.BackupCheckIn
import com.dadsvictory.domain.backup.BackupCodec
import com.dadsvictory.domain.backup.BackupCraving
import com.dadsvictory.domain.backup.BackupCrypto
import com.dadsvictory.domain.backup.BackupJournalEntry
import com.dadsvictory.domain.backup.BackupPayload
import com.dadsvictory.domain.backup.BackupProfile
import com.dadsvictory.domain.backup.BackupSlip
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AchievementTest {

    private fun evaluate(
        bestStreakDays: Int = 0,
        savedMinor: Long = 0,
        cravings: Int = 0,
        story: Set<String> = emptySet(),
    ) = Achievements.evaluate(bestStreakDays, savedMinor, cravings, Currency.GBP, story)

    private fun unlockedIds(progress: List<Achievements.Progress>) =
        progress.filter { it.unlocked }.map { it.achievement.id }.toSet()

    @Test
    fun `day badges unlock at their thresholds`() {
        assertTrue("day_1" in unlockedIds(evaluate(bestStreakDays = 1)))
        assertFalse("day_3" in unlockedIds(evaluate(bestStreakDays = 1)))
        assertTrue("day_3" in unlockedIds(evaluate(bestStreakDays = 3)))
        assertTrue("day_365" in unlockedIds(evaluate(bestStreakDays = 400)))
    }

    /**
     * The single most important rule in the badge system: a badge he has earned is
     * never taken back off him because of a later slip.
     */
    @Test
    fun `badges already earned survive a relapse`() {
        val afterThirtyDays = unlockedIds(evaluate(bestStreakDays = 30))
        // He slipped yesterday, so his *current* streak is 1 — but best is still 30.
        val afterASlip = unlockedIds(evaluate(bestStreakDays = 30))
        assertEquals(afterThirtyDays, afterASlip)
        assertTrue("day_30" in afterASlip)
    }

    @Test
    fun `money badges use whole currency units and carry his symbol`() {
        assertFalse("money_100" in unlockedIds(evaluate(savedMinor = 9_999)))
        assertTrue("money_100" in unlockedIds(evaluate(savedMinor = 10_000)))

        val badge = Achievements.evaluate(0, 10_000, 0, Currency.USD, emptySet())
            .first { it.achievement.id == "money_100" }
        assertTrue(badge.achievement.title.startsWith("$"))
    }

    @Test
    fun `craving badges count defeated cravings`() {
        assertTrue("crav_10" in unlockedIds(evaluate(cravings = 10)))
        assertTrue("crav_100" in unlockedIds(evaluate(cravings = 143)))
        assertFalse("crav_50" in unlockedIds(evaluate(cravings = 49)))
    }

    @Test
    fun `story badges are only ever unlocked by him, never guessed`() {
        assertFalse("story_social" in unlockedIds(evaluate(bestStreakDays = 400, savedMinor = 500_000, cravings = 900)))
        assertTrue("story_social" in unlockedIds(evaluate(story = setOf("story_social"))))
    }

    @Test
    fun `there is a badge for getting back up, because that is the hard one`() {
        assertNotNull(Achievements.byId("story_got_back_up"))
    }

    @Test
    fun `progress fraction is clamped and sensible`() {
        val day30 = evaluate(bestStreakDays = 15).first { it.achievement.id == "day_30" }
        assertEquals(0.5f, day30.fraction)
        val done = evaluate(bestStreakDays = 100).first { it.achievement.id == "day_30" }
        assertEquals(1f, done.fraction)
    }

    @Test
    fun `next up is the closest badge he has not yet reached`() {
        val next = Achievements.nextUp(evaluate(bestStreakDays = 8, savedMinor = 0, cravings = 0))
        assertNotNull(next)
        assertFalse(next.unlocked)
        assertEquals("day_14", next.achievement.id)
    }

    @Test
    fun `next up never suggests a story badge, which he cannot progress towards`() {
        val next = Achievements.nextUp(evaluate(bestStreakDays = 400, savedMinor = 500_000, cravings = 500))
        assertTrue(next == null || next.achievement.kind != Achievements.Kind.STORY)
    }

    @Test
    fun `achievement ids are unique`() {
        assertEquals(Achievements.ALL.size, Achievements.ALL.map { it.id }.distinct().size)
    }

    @Test
    fun `all the badges asked for in the brief exist`() {
        val required = listOf(
            "day_1", "day_3", "day_7", "day_14", "day_30", "day_60", "day_90", "day_100",
            "day_180", "day_365", "money_100", "money_500", "money_1000",
            "crav_10", "crav_50", "crav_100",
            "story_hard_day", "story_social", "story_stress", "story_holiday",
        )
        for (id in required) assertNotNull(Achievements.byId(id), "missing badge $id")
    }
}

class BackupTest {

    private val payload = BackupPayload(
        exportedAtMillis = 1_700_000_000_000L,
        profile = BackupProfile(
            quitNicotine = true,
            quitAlcohol = true,
            startMillis = 1_699_000_000_000L,
            reasonIds = listOf("family", "health"),
            customReason = "For my grandkids",
            nicotineWeeklySpendMinor = 3_500,
            alcoholWeeklySpendMinor = 7_000,
            savingsGoalName = "Family holiday",
            savingsGoalMinor = 100_000,
        ),
        slips = listOf(BackupSlip(1_699_500_000_000L, listOf("NICOTINE"), "stress", "Bad day at work", "Walk instead")),
        cravings = listOf(BackupCraving(1_699_600_000_000L, "won", 600, "after_meals")),
        checkIns = listOf(BackupCheckIn(20_000L, 4, 3, 5, true, true, "Good day")),
        journal = listOf(BackupJournalEntry(1_699_700_000_000L, "What am I proud of today?", "Stayed free.")),
        favouriteVerses = listOf("Philippians 4:13", "James 1:4"),
        selectedTriggerIds = listOf("stress", "after_meals"),
        storyAchievementIds = listOf("story_hard_day"),
    )

    @Test
    fun `a backup survives a full export and import round trip`() {
        val file = BackupCodec.export(payload, "correct horse battery".toCharArray())
        val restored = BackupCodec.import(file, "correct horse battery".toCharArray())
        assertEquals(payload, restored)
    }

    @Test
    fun `the exported file is genuinely encrypted, not just encoded`() {
        val file = BackupCodec.export(payload, "passphrase".toCharArray())
        val asText = String(file, Charsets.ISO_8859_1)
        assertFalse(asText.contains("For my grandkids"), "personal text is readable in the export")
        assertFalse(asText.contains("Family holiday"))
        assertFalse(asText.contains("Bad day at work"))
        assertFalse(asText.contains("quitNicotine"))
    }

    @Test
    fun `the wrong passphrase is refused with a clear message`() {
        val file = BackupCodec.export(payload, "right".toCharArray())
        val error = assertFailsWith<BackupCrypto.BackupFormatException> {
            BackupCodec.import(file, "wrong".toCharArray())
        }
        assertTrue(error.message!!.contains("Wrong passphrase"))
    }

    @Test
    fun `a tampered file is rejected rather than silently accepted`() {
        val file = BackupCodec.export(payload, "passphrase".toCharArray())
        file[file.size - 1] = (file[file.size - 1] + 1).toByte()
        assertFailsWith<BackupCrypto.BackupFormatException> {
            BackupCodec.import(file, "passphrase".toCharArray())
        }
    }

    @Test
    fun `a file that is not a backup at all is rejected by name`() {
        val notABackup = "just a text file, honestly".toByteArray()
        val error = assertFailsWith<BackupCrypto.BackupFormatException> {
            BackupCodec.import(notABackup, "passphrase".toCharArray())
        }
        assertTrue(error.message!!.contains("doesn't look like") || error.message!!.contains("too small"))
    }

    @Test
    fun `an empty file is rejected without crashing`() {
        assertFailsWith<BackupCrypto.BackupFormatException> {
            BackupCodec.import(ByteArray(0), "passphrase".toCharArray())
        }
    }

    @Test
    fun `two exports of the same data differ, because salt and iv are random`() {
        val a = BackupCodec.export(payload, "passphrase".toCharArray())
        val b = BackupCodec.export(payload, "passphrase".toCharArray())
        assertFalse(a.contentEquals(b))
        // ...but both still restore to the same thing.
        assertEquals(
            BackupCodec.import(a, "passphrase".toCharArray()),
            BackupCodec.import(b, "passphrase".toCharArray()),
        )
    }

    @Test
    fun `an empty passphrase is refused`() {
        assertFailsWith<IllegalArgumentException> { BackupCodec.export(payload, CharArray(0)) }
    }

    @Test
    fun `an older backup missing newer fields still loads`() {
        val minimal = """{"formatVersion":1,"exportedAtMillis":123,"unknownFutureField":"ignored"}"""
        val restored = BackupCodec.fromBytes(minimal.toByteArray())
        assertEquals(123L, restored.exportedAtMillis)
        assertTrue(restored.slips.isEmpty())
    }

    @Test
    fun `raw encryption round trips arbitrary bytes`() {
        val secret = ByteArray(1000) { (it % 256).toByte() }
        val encrypted = BackupCrypto.encrypt(secret, "pw".toCharArray())
        assertContentEquals(secret, BackupCrypto.decrypt(encrypted, "pw".toCharArray()))
    }

    @Test
    fun `the suggested filename sorts by date and says what it is`() {
        assertEquals("dads-victory-backup-2026-03-07.dvbk", BackupCodec.suggestedFileName(2026, 3, 7))
    }
}
