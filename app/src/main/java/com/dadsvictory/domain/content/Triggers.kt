package com.dadsvictory.domain.content

/**
 * Triggers, and a concrete plan for each one.
 *
 * The strategies are deliberately physical and specific — "stand up, brush your
 * teeth, leave the room" rather than "stay strong". A plan you can carry out
 * without deciding anything is worth more, at the moment it is needed, than a
 * plan that requires resolve you have temporarily run out of.
 */
data class Trigger(
    val id: String,
    val emoji: String,
    val label: String,
    val strategy: String,
)

object Triggers {

    val ALL: List<Trigger> = listOf(
        Trigger(
            "coffee", "☕", "Coffee",
            "Change the pairing rather than giving up the coffee. Drink it somewhere different — a " +
                "different chair, a different room, standing at the window. Hold the mug in the hand " +
                "you'd normally have free.",
        ),
        Trigger(
            "seeing_alcohol", "🍺", "Seeing alcohol",
            "Decide what you're drinking before you're in front of it, and have it in your hand within " +
                "the first minute. A full glass of something else answers the question for you and stops " +
                "anyone asking again.",
        ),
        Trigger(
            "anger", "😡", "Anger",
            "Get the adrenaline out through your body, not through your habit. Walk it off for five " +
                "minutes, or do twenty press-ups. Deal with the thing that made you angry afterwards, " +
                "when your hands are steady.",
        ),
        Trigger(
            "stress", "😰", "Stress",
            "Slow the breath first — out for longer than in, six or seven times. Stress makes the old " +
                "habit look like a solution; slower breathing makes that illusion much weaker within a " +
                "couple of minutes.",
        ),
        Trigger(
            "tiredness", "😴", "Tiredness",
            "Tired is the single easiest state to slip in. Treat the tiredness: water, ten minutes " +
                "outside, or going to bed earlier than feels reasonable. Don't make decisions after 10pm.",
        ),
        Trigger(
            "friends", "👥", "Friends",
            "Tell one person in the group before you arrive. It turns the whole evening from a test of " +
                "willpower into a thing you've already announced, and it gives you someone to stand next " +
                "to when it gets hard.",
        ),
        Trigger(
            "parties", "🎉", "Parties",
            "Arrive with your own drink, know your exit time before you go, and drive if you can. " +
                "Having a reason ready — 'I'm driving' — means you never have to have the conversation.",
        ),
        Trigger(
            "after_meals", "🍽️", "After meals",
            "Immediately stand up, brush your teeth, drink a glass of water and leave the room where " +
                "you'd normally vape. The window after a meal is short — moving through it beats " +
                "sitting in it.",
        ),
        Trigger(
            "driving", "🚗", "Driving",
            "Clear the car out completely, including the glovebox and door pockets. Put something else " +
                "in the cup holder. Have a podcast or a call queued up before you turn the key.",
        ),
        Trigger(
            "work", "💼", "Work",
            "Replace the break, don't cancel it. You still get to walk outside for five minutes — you " +
                "just do it with a coffee, a phone call or a lap of the building instead.",
        ),
        Trigger(
            "at_home", "🏠", "At home",
            "Remove it from the house entirely, and change the specific spot where it used to happen — " +
                "move the chair, open the window, sit somewhere new. The place itself is half the habit.",
        ),
        Trigger(
            "boredom", "📱", "Boredom",
            "Keep a short list of five-minute jobs on your phone and do one. Boredom cravings collapse " +
                "almost immediately once your hands are busy with something else.",
        ),
    )

    fun byId(id: String?): Trigger? = ALL.firstOrNull { it.id == id }

    fun strategiesFor(selectedIds: Set<String>): List<Trigger> = ALL.filter { it.id in selectedIds }
}

/** The six steps of craving emergency mode. */
object CravingPlan {

    const val TIMER_SECONDS: Int = 10 * 60

    const val OPENING: String = "STOP. You don't need to make the decision right now."

    data class MoveOption(val emoji: String, val label: String, val detail: String)

    val MOVE_OPTIONS: List<MoveOption> = listOf(
        MoveOption("🚶", "Walk for 5 minutes", "Out the door and back. It doesn't matter where."),
        MoveOption("🏋️", "Do 10 squats", "Enough to change how your body feels in under a minute."),
        MoveOption("🚿", "Take a shower", "Almost nobody carries a craving through a shower."),
        MoveOption("🌳", "Go outside", "Fresh air and a different view, even for two minutes."),
        MoveOption("🧹", "Do a quick task", "Washing up, a bin, a drawer. Anything with your hands."),
        MoveOption("📞", "Call someone", "You don't have to tell them why you rang."),
    )

    val BREATHING_INSTRUCTION: String = "Breathe in as the circle grows. Breathe out as it shrinks."

    const val BREATH_COUNT: Int = 10
    const val BREATH_IN_SECONDS: Int = 4
    const val BREATH_HOLD_SECONDS: Int = 2
    const val BREATH_OUT_SECONDS: Int = 6

    const val WATER_STEP: String =
        "Drink a glass of water. Slowly, all of it. It gives your hands and your mouth something to " +
            "do and it buys you another minute."

    const val VICTORY: String = "YOU WON THIS CRAVING."

    const val VICTORY_SUB: String =
        "That urge came, and it went, and you're still free. That is exactly how this is done."

    /** Messages shown as the countdown runs, roughly one per minute. */
    val TIMER_MESSAGES: List<String> = Motivation.CRAVING

    /**
     * Picks the line for a given point in the countdown, so the message changes as
     * the clock runs down instead of sitting there for ten minutes.
     */
    fun messageForSecondsRemaining(secondsRemaining: Int, seed: Long): String {
        val elapsed = (TIMER_SECONDS - secondsRemaining).coerceAtLeast(0)
        val step = elapsed / 30 // a new line every 30 seconds
        return Rotation.pick(TIMER_MESSAGES, seed + step)
    }
}

/** "Today's Victory Plan" — the default checklist, which he can edit. */
object DailyPlan {

    enum class Slot(val id: String, val label: String, val emoji: String) {
        MORNING("morning", "Morning", "☀️"),
        AFTERNOON("afternoon", "Afternoon", "🌤"),
        EVENING("evening", "Evening", "🌙"),
    }

    data class Task(val id: String, val slot: Slot, val title: String)

    val DEFAULTS: List<Task> = listOf(
        Task("m_read", Slot.MORNING, "Read today's encouragement"),
        Task("m_water", Slot.MORNING, "Drink a glass of water"),
        Task("m_reason", Slot.MORNING, "Review my reason"),
        Task("m_nicotine", Slot.MORNING, "Stay nicotine-free"),
        Task("m_alcohol", Slot.MORNING, "Stay alcohol-free"),
        Task("a_cravings", Slot.AFTERNOON, "Check in on cravings"),
        Task("a_move", Slot.AFTERNOON, "Move for 5 minutes"),
        Task("a_read", Slot.AFTERNOON, "Read encouragement"),
        Task("e_checkin", Slot.EVENING, "Do the daily check-in"),
        Task("e_victory", Slot.EVENING, "Record one victory"),
        Task("e_verse", Slot.EVENING, "Read a Bible verse"),
        Task("e_tomorrow", Slot.EVENING, "Prepare for tomorrow"),
    )

    /** Drops the tasks that don't apply to what he's actually quitting. */
    fun defaultsFor(quitNicotine: Boolean, quitAlcohol: Boolean): List<Task> = DEFAULTS.filter {
        when (it.id) {
            "m_nicotine" -> quitNicotine
            "m_alcohol" -> quitAlcohol
            else -> true
        }
    }
}

/** Journal prompts. He can always write freely instead. */
object JournalPrompts {

    val ALL: List<String> = listOf(
        "What am I proud of today?",
        "What triggered me today?",
        "What helped?",
        "Why do I want to stay free?",
        "What will I do differently tomorrow?",
        "What was the hardest moment, and how did it end?",
        "Who am I doing this for?",
        "What has got easier that used to be hard?",
        "What would I tell someone else starting today?",
        "What do I want to be true a year from now?",
    )

    fun forDay(epochDay: Long): String = Rotation.pick(ALL, epochDay)
}

/** The reasons offered on the relapse screen. Neutral words, no blame in any of them. */
object SlipReasons {

    val ALL: List<Pair<String, String>> = listOf(
        "stress" to "Stress",
        "social" to "Social situation",
        "alcohol_trigger" to "Alcohol was around",
        "nicotine_craving" to "Nicotine craving",
        "family_work" to "Family or work pressure",
        "habit" to "Habit — it just happened",
        "tired" to "Tiredness",
        "low_mood" to "Low mood",
        "celebration" to "Celebration",
        "other" to "Something else",
    )

    fun labelFor(id: String?): String? = ALL.firstOrNull { it.first == id }?.second
}
