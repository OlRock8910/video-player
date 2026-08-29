package com.dadsvictory.domain

/**
 * Badges. Two kinds, and the difference matters:
 *
 *  - Automatic badges are earned by numbers the app can actually see.
 *  - Story badges ("first social event without drinking") are ones only he knows
 *    he has reached, so he marks them himself. The app does not guess at them,
 *    because a badge awarded for something that did not happen is worth nothing.
 */
object Achievements {

    enum class Kind { DAYS_FREE, MONEY_SAVED, CRAVINGS_DEFEATED, STORY }

    data class Achievement(
        val id: String,
        val emoji: String,
        val title: String,
        val description: String,
        val kind: Kind,
        /** Days, whole currency units, or a craving count, depending on [kind]. */
        val threshold: Long = 0L,
    )

    data class Progress(
        val achievement: Achievement,
        val unlocked: Boolean,
        /** 0f..1f towards the threshold. Always 0f or 1f for story badges. */
        val fraction: Float,
        val currentValue: Long,
    )

    val DAY_BADGES: List<Achievement> = listOf(
        Achievement("day_1", "🌅", "First Day", "One full day free. This is the one everything else is built on.", Kind.DAYS_FREE, 1),
        Achievement("day_3", "🏆", "3 Days", "Three days. The hardest stretch for most people.", Kind.DAYS_FREE, 3),
        Achievement("day_7", "🏆", "One Week", "A whole week of choosing differently.", Kind.DAYS_FREE, 7),
        Achievement("day_14", "🏆", "Two Weeks", "Two weeks. This is becoming who you are.", Kind.DAYS_FREE, 14),
        Achievement("day_30", "🏆", "30 Days", "A full month free.", Kind.DAYS_FREE, 30),
        Achievement("day_60", "🏆", "60 Days", "Two months. Look how far back the start line is.", Kind.DAYS_FREE, 60),
        Achievement("day_90", "🏆", "90 Days", "Three months of daily decisions.", Kind.DAYS_FREE, 90),
        Achievement("day_100", "💯", "100 Days", "One hundred days. Not luck — a hundred choices.", Kind.DAYS_FREE, 100),
        Achievement("day_180", "🏔", "6 Months", "Half a year free.", Kind.DAYS_FREE, 180),
        Achievement("day_365", "👑", "One Year", "A year. Every season, every hard day, still standing.", Kind.DAYS_FREE, 365),
    )

    val MONEY_BADGES: List<Achievement> = listOf(
        Achievement("money_100", "💰", "100 Saved", "Money that stayed with your family.", Kind.MONEY_SAVED, 100),
        Achievement("money_500", "💰", "500 Saved", "That is a real amount of money.", Kind.MONEY_SAVED, 500),
        Achievement("money_1000", "💰", "1,000 Saved", "Four figures kept out of the shop till.", Kind.MONEY_SAVED, 1_000),
    )

    val CRAVING_BADGES: List<Achievement> = listOf(
        Achievement("crav_10", "💪", "10 Cravings Defeated", "Ten times you sat still and let it pass.", Kind.CRAVINGS_DEFEATED, 10),
        Achievement("crav_50", "💪", "50 Cravings Defeated", "Fifty. You know how to do this now.", Kind.CRAVINGS_DEFEATED, 50),
        Achievement("crav_100", "💪", "100 Cravings Defeated", "One hundred urges that came and went without you.", Kind.CRAVINGS_DEFEATED, 100),
    )

    /** He ticks these himself, when he knows he has been through it. */
    val STORY_BADGES: List<Achievement> = listOf(
        Achievement("story_hard_day", "🌧", "First difficult day defeated", "A day that was genuinely hard, and you got through it free.", Kind.STORY),
        Achievement("story_social", "🥂", "First social event without drinking", "You went, you stayed, you came home yourself.", Kind.STORY),
        Achievement("story_stress", "😤", "First stressful day without vaping", "The old reflex showed up and you didn't follow it.", Kind.STORY),
        Achievement("story_holiday", "✈️", "First holiday without alcohol", "A whole break away, and you remember all of it.", Kind.STORY),
        Achievement("story_told_someone", "🗣", "Told someone you're quitting", "Saying it out loud makes it real.", Kind.STORY),
        Achievement("story_got_back_up", "🔁", "Got back up after a slip", "The badge that most people never give themselves. This one counts.", Kind.STORY),
    )

    val ALL: List<Achievement> = DAY_BADGES + MONEY_BADGES + CRAVING_BADGES + STORY_BADGES

    fun byId(id: String): Achievement? = ALL.firstOrNull { it.id == id }

    /**
     * Evaluates every badge.
     *
     * [bestStreakDays] rather than the current streak is used for day badges on
     * purpose: a badge he has already earned is not taken back off him because he
     * had a bad week. That is the whole philosophy of the app in one decision.
     */
    fun evaluate(
        bestStreakDays: Int,
        savedMinor: Long,
        cravingsDefeated: Int,
        currency: Currency,
        manuallyUnlockedIds: Set<String>,
    ): List<Progress> = ALL.map { achievement ->
        when (achievement.kind) {
            Kind.DAYS_FREE -> progress(achievement, bestStreakDays.toLong())
            Kind.MONEY_SAVED -> progress(achievement, savedMinor / 100)
            Kind.CRAVINGS_DEFEATED -> progress(achievement, cravingsDefeated.toLong())
            Kind.STORY -> {
                val unlocked = achievement.id in manuallyUnlockedIds
                Progress(achievement, unlocked, if (unlocked) 1f else 0f, if (unlocked) 1 else 0)
            }
        }.let { p ->
            // Money badge titles carry the currency symbol he chose.
            if (achievement.kind == Kind.MONEY_SAVED) {
                p.copy(achievement = achievement.copy(title = currency.symbol + achievement.title))
            } else {
                p
            }
        }
    }

    private fun progress(achievement: Achievement, current: Long): Progress {
        val fraction = if (achievement.threshold <= 0L) {
            0f
        } else {
            (current.toDouble() / achievement.threshold.toDouble()).coerceIn(0.0, 1.0).toFloat()
        }
        return Progress(achievement, current >= achievement.threshold, fraction, current)
    }

    fun unlockedCount(progress: List<Progress>): Int = progress.count { it.unlocked }

    /** The badge he is closest to but has not reached — the one worth showing on the dashboard. */
    fun nextUp(progress: List<Progress>): Progress? = progress
        .filter { !it.unlocked && it.achievement.kind != Kind.STORY }
        .maxByOrNull { it.fraction }
}
