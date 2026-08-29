package com.dadsvictory.domain

/**
 * Pure domain model. Nothing in the `com.dadsvictory.domain` package may import
 * anything from `android.*` or `androidx.*` — the whole package is compiled and
 * unit-tested on a plain JVM by the build in `tools/logic-verify`.
 */

/** The things this app helps someone step away from. */
enum class Substance {
    NICOTINE,
    ALCOHOL,
    ;

    val displayName: String
        get() = when (this) {
            NICOTINE -> "Nicotine"
            ALCOHOL -> "Alcohol"
        }
}

/** Why he is doing this. Shown back to him in the hardest moments. */
enum class Reason(val id: String, val label: String, val cravingLine: String) {
    FAMILY("family", "Family", "You're doing this for your family."),
    HEALTH("health", "Health", "You're doing this so your body has a fair chance."),
    FAITH("faith", "Faith", "You're doing this because you were made for more than this."),
    MONEY("money", "Money", "You're doing this so your money goes somewhere that matters."),
    FREEDOM("freedom", "Freedom", "You're doing this because you want to be free, not owned."),
    FITNESS("fitness", "Fitness", "You're doing this so you can breathe easier and move better."),
    BEING_THERE("being_there", "Being there for the people I love", "You're doing this so you can be there for the people you love."),
    EXAMPLE("example", "Setting an example", "You're doing this because someone is watching how you fight."),
    LIVING_LONGER("living_longer", "Living longer", "You're doing this for more years with the people who need you."),
    FEELING_BETTER("feeling_better", "Feeling better", "You're doing this because you deserve to feel well again."),
    ;

    companion object {
        fun fromId(id: String): Reason? = entries.firstOrNull { it.id == id }
    }
}

/** Which country's health and emergency information the app should show. */
enum class Country(val id: String, val label: String, val flag: String) {
    UK("uk", "United Kingdom", "🇬🇧"),
    US("us", "United States", "🇺🇸"),
    OTHER("other", "Other / rest of world", "🌍"),
    ;

    companion object {
        fun fromId(id: String?): Country = entries.firstOrNull { it.id == id } ?: UK
    }
}

enum class Currency(val id: String, val symbol: String, val label: String) {
    GBP("gbp", "£", "Pounds (£)"),
    USD("usd", "$", "Dollars ($)"),
    EUR("eur", "€", "Euros (€)"),
    ;

    companion object {
        fun fromId(id: String?): Currency = entries.firstOrNull { it.id == id } ?: GBP

        fun defaultFor(country: Country): Currency = when (country) {
            Country.UK -> GBP
            Country.US -> USD
            Country.OTHER -> GBP
        }
    }
}

/** How he counts drinks. Serving sizes genuinely differ between countries. */
enum class DrinkBasis(val id: String, val label: String, val explainer: String) {
    UK_UNITS(
        "uk_units",
        "UK units",
        "In the UK, one unit is 10ml (8g) of pure alcohol. A pint of lower-strength lager is about 2 units; " +
            "a 175ml glass of wine is about 2 units; a single 25ml spirit measure is 1 unit.",
    ),
    US_STANDARD_DRINKS(
        "us_standard",
        "US standard drinks",
        "In the US, one standard drink contains about 14g of pure alcohol: roughly a 12oz regular beer, " +
            "a 5oz glass of wine, or a 1.5oz shot of spirits.",
    ),
    ;

    companion object {
        fun fromId(id: String?): DrinkBasis = entries.firstOrNull { it.id == id } ?: UK_UNITS

        fun defaultFor(country: Country): DrinkBasis =
            if (country == Country.US) US_STANDARD_DRINKS else UK_UNITS
    }
}

/** The three daily encouragement slots. */
enum class NotificationSlot(val id: String, val label: String, val emoji: String, val defaultMinuteOfDay: Int) {
    MORNING("morning", "Morning", "☀️", 8 * 60),
    AFTERNOON("afternoon", "Afternoon", "🌤", 14 * 60),
    EVENING("evening", "Evening", "🌙", 20 * 60),
    ;

    companion object {
        fun fromId(id: String?): NotificationSlot = entries.firstOrNull { it.id == id } ?: MORNING
    }
}

/** How a craving episode ended. A craving is never "failed" — it is survived or supported. */
enum class CravingOutcome(val id: String) {
    WON("won"),
    NEEDED_HELP("needed_help"),
    LEFT_EARLY("left_early"),
    ;

    companion object {
        fun fromId(id: String?): CravingOutcome = entries.firstOrNull { it.id == id } ?: LEFT_EARLY
    }
}

/**
 * A recorded slip. Deliberately not called a "failure" anywhere in the codebase,
 * because the words we use in code leak into the words we use on screen.
 */
data class Slip(
    val id: Long = 0L,
    val atMillis: Long,
    val substances: Set<Substance>,
    val triggerId: String? = null,
    val reflection: String? = null,
    val nextChange: String? = null,
)

data class CravingEvent(
    val id: Long = 0L,
    val atMillis: Long,
    val outcome: CravingOutcome,
    val secondsHeld: Int,
    val triggerId: String? = null,
)

/** Mood on the daily check-in, worst to best, so it charts naturally. */
enum class Mood(val score: Int, val emoji: String, val label: String) {
    VERY_DIFFICULT(1, "😣", "Very difficult"),
    STRUGGLING(2, "😟", "Struggling"),
    OKAY(3, "😐", "Okay"),
    GOOD(4, "🙂", "Good"),
    GREAT(5, "😀", "Great"),
    ;

    companion object {
        fun fromScore(score: Int): Mood = entries.firstOrNull { it.score == score } ?: OKAY
    }
}

data class CheckIn(
    val epochDay: Long,
    val moodScore: Int,
    val cravingLevel: Int,
    val stressLevel: Int,
    val stayedNicotineFree: Boolean?,
    val stayedAlcoholFree: Boolean?,
    val note: String = "",
)

/**
 * Everything the app knows about him. All of it lives on the device.
 */
data class Profile(
    val quitNicotine: Boolean = true,
    val quitAlcohol: Boolean = true,
    val startMillis: Long = 0L,
    val reasonIds: Set<String> = emptySet(),
    val customReason: String = "",
    val country: Country = Country.UK,
    val currency: Currency = Currency.GBP,
    /** What he told us he was spending on nicotine each week, in minor units (pence/cents). */
    val nicotineWeeklySpendMinor: Long = 0L,
    val alcoholWeeklySpendMinor: Long = 0L,
    val vapeSessionsPerDay: Int = 0,
    val puffsPerDay: Int = 0,
    val nicotineStrengthMgPerMl: Double = 0.0,
    val drinkBasis: DrinkBasis = DrinkBasis.UK_UNITS,
    val drinksPerWeek: Double = 0.0,
    val savingsGoalName: String = "",
    val savingsGoalMinor: Long = 0L,
) {
    val substances: Set<Substance>
        get() = buildSet {
            if (quitNicotine) add(Substance.NICOTINE)
            if (quitAlcohol) add(Substance.ALCOHOL)
        }

    /** The reasons he picked, in a stable order, with his own reason last. */
    fun reasonLines(): List<String> {
        val chosen = Reason.entries.filter { it.id in reasonIds }.map { it.cravingLine }
        return if (customReason.isBlank()) chosen else chosen + customReason
    }

    fun reasonLabels(): List<String> {
        val chosen = Reason.entries.filter { it.id in reasonIds }.map { it.label }
        return if (customReason.isBlank()) chosen else chosen + customReason
    }
}
