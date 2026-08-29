package com.dadsvictory.domain.content

import com.dadsvictory.domain.Currency
import com.dadsvictory.domain.Money
import com.dadsvictory.domain.NotificationSlot
import com.dadsvictory.domain.Profile

/**
 * Builds the text of the three daily notifications.
 *
 * Kept pure so the exact wording can be tested, and so the same day index always
 * produces the same message — which matters after a reboot, when the alarm is
 * rebuilt from scratch and should still say what it was going to say.
 */
object NotificationContent {

    data class Message(
        val title: String,
        val body: String,
        /** Longer text for the expanded notification, or null to reuse the body. */
        val expanded: String? = null,
        val showCravingAction: Boolean = false,
    )

    fun build(
        slot: NotificationSlot,
        profile: Profile,
        currentStreakDays: Int,
        savedMinor: Long,
        currency: Currency,
        bibleVersion: BibleVersion,
        dayIndex: Long,
    ): Message = when (slot) {
        NotificationSlot.MORNING -> morning(dayIndex, bibleVersion)
        NotificationSlot.AFTERNOON -> afternoon(dayIndex)
        NotificationSlot.EVENING -> evening(profile, currentStreakDays, savedMinor, currency, bibleVersion, dayIndex)
    }

    private fun morning(dayIndex: Long, version: BibleVersion): Message {
        val line = Motivation.pick(Motivation.Category.MORNING, dayIndex)
        val verse = Scripture.daily(dayIndex)
        return Message(
            title = "Good morning, Dad ☀️",
            body = line,
            expanded = "$line\n\n\"${verse.text(version)}\"\n— ${verse.reference} (${version.abbreviation})",
        )
    }

    private fun afternoon(dayIndex: Long): Message {
        val line = Motivation.pick(Motivation.Category.AFTERNOON, dayIndex)
        return Message(
            title = "Halfway through 🌤",
            body = line,
            expanded = "$line\n\nIf a craving hits, open the app and start the 10-minute timer. " +
                "You don't have to decide anything right now.",
            showCravingAction = true,
        )
    }

    private fun evening(
        profile: Profile,
        currentStreakDays: Int,
        savedMinor: Long,
        currency: Currency,
        version: BibleVersion,
        dayIndex: Long,
    ): Message {
        val line = Motivation.pick(Motivation.Category.EVENING, dayIndex)
        val verse = Scripture.daily(dayIndex)
        val reason = profile.reasonLines().let {
            if (it.isEmpty()) null else Rotation.pick(it, dayIndex)
        }

        val parts = buildList {
            add(line)
            add("")
            add("🔥 ${streakLine(currentStreakDays)}")
            if (savedMinor > 0) add("💰 ${Money.format(savedMinor, currency)} saved")
            if (reason != null) add("❤️ $reason")
            add("")
            add("📖 \"${verse.text(version)}\"")
            add("— ${verse.reference} (${version.abbreviation})")
        }

        return Message(
            title = "Another day of choosing freedom 🌙",
            body = line,
            expanded = parts.joinToString("\n"),
        )
    }

    private fun streakLine(days: Int): String = when (days) {
        0 -> "Day one is under way"
        1 -> "1 day free"
        else -> "$days days free"
    }
}
