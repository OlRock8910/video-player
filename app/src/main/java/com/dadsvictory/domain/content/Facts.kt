package com.dadsvictory.domain.content

/**
 * The "Why Quit?" library.
 *
 * Rules applied to every entry below:
 *  - a named source, and where the source gives one, the year and the population;
 *  - no number that the source does not actually state;
 *  - hedged language ("evidence suggests", "still being studied") wherever the
 *    science itself is hedged.
 *
 * Where the honest answer is "we do not know yet", the app says that instead of
 * filling the gap with something frightening.
 */
data class Fact(
    val id: String,
    val topic: Topic,
    val headline: String,
    val body: String,
    val sourceId: String,
    /** Year, study period, or edition, exactly as the source frames it. */
    val period: String? = null,
    /** Population the figure describes, where it is specific to one. */
    val population: String? = null,
) {
    enum class Topic(val label: String, val emoji: String) {
        NICOTINE("Vaping & nicotine", "🚭"),
        ALCOHOL("Alcohol", "🍺"),
        BOTH("Quitting in general", "💪"),
    }

    val source: Source? get() = Sources.byId(sourceId)
}

object Facts {

    val NICOTINE: List<Fact> = listOf(
        Fact(
            id = "nic_addictive",
            topic = Fact.Topic.NICOTINE,
            headline = "Most e-cigarettes contain nicotine, and nicotine is highly addictive.",
            body = "This is the reason quitting is hard, and it is not a reflection of willpower or " +
                "character. Nicotine changes how the brain's reward system responds, which is exactly " +
                "what makes it difficult to stop.",
            sourceId = Sources.CDC_ECIGS.id,
        ),
        Fact(
            id = "nic_aerosol",
            topic = Fact.Topic.NICOTINE,
            headline = "Vaping aerosol is not just water vapour.",
            body = "CDC reports that e-cigarette aerosol can contain potentially harmful substances, " +
                "including cancer-causing chemicals, heavy metals and ultrafine particles that can be " +
                "breathed deep into the lungs.",
            sourceId = Sources.CDC_ECIGS.id,
        ),
        Fact(
            id = "nic_long_term_unknown",
            topic = Fact.Topic.NICOTINE,
            headline = "Scientists are still learning about the long-term effects of vaping.",
            body = "The NHS position is that vaping is far less harmful than smoking, but it is not " +
                "risk free, and the long-term health effects are not yet known because vaping has not " +
                "existed long enough to study over a lifetime. Honest uncertainty is not the same as " +
                "safety — and none of it changes the fact that nicotine is addictive.",
            sourceId = Sources.NHS_VAPING.id,
        ),
        Fact(
            id = "nic_withdrawal",
            topic = Fact.Topic.NICOTINE,
            headline = "Withdrawal symptoms are real, and they generally ease with time.",
            body = "Stopping nicotine can bring cravings, irritability, anxiety, restlessness, trouble " +
                "concentrating, disturbed sleep and increased appetite. These are recognised withdrawal " +
                "symptoms, not signs that something is going wrong. Research indicates they typically " +
                "improve over time.",
            sourceId = Sources.NIDA_NICOTINE.id,
        ),
        Fact(
            id = "nic_quit_intentions",
            topic = Fact.Topic.NICOTINE,
            headline = "Most adults who vape say they want to stop.",
            body = "In a study covering 2016–2018, about 60.1% of US adults who currently used " +
                "e-cigarettes reported plans to quit. If you have felt like the only one trying to " +
                "get out of this, you are not.",
            sourceId = Sources.CDC_ECIG_ADULTS.id,
            period = "Study period 2016–2018",
            population = "US adults who currently used e-cigarettes",
        ),
        Fact(
            id = "nic_help_available",
            topic = Fact.Topic.NICOTINE,
            headline = "Evidence-based quit aids exist, and they are worth asking about.",
            body = "A doctor or pharmacist can talk you through options such as nicotine replacement " +
                "therapy and other licensed treatments, and in the UK local stop smoking services are " +
                "free. People who use support are generally more likely to succeed than people going " +
                "it alone. Asking for help is a tactic, not a weakness.",
            sourceId = Sources.NHS_QUIT_SMOKING.id,
        ),
    )

    val ALCOHOL: List<Fact> = listOf(
        Fact(
            id = "alc_deaths_us",
            topic = Fact.Topic.ALCOHOL,
            headline = "Excessive alcohol use is linked to about 178,000 deaths a year in the US.",
            body = "CDC researchers estimated an average of 178,307 deaths per year from excessive " +
                "alcohol use during 2020–2021, up from about 137,927 per year during 2016–2017. This " +
                "is a United States estimate; it is included here for scale, not as a figure for any " +
                "other country.",
            sourceId = Sources.CDC_ALCOHOL_DEATHS.id,
            period = "Average annual deaths, 2020–2021 (compared with 2016–2017)",
            population = "United States",
        ),
        Fact(
            id = "alc_cancer",
            topic = Fact.Topic.ALCOHOL,
            headline = "Alcohol increases the risk of several cancers.",
            body = "The International Agency for Research on Cancer classifies alcoholic beverages as " +
                "a Group 1 carcinogen — the same classification category used for substances known to " +
                "cause cancer in humans. Risk rises with the amount consumed.",
            sourceId = Sources.IARC_ALCOHOL.id,
        ),
        Fact(
            id = "alc_liver",
            topic = Fact.Topic.ALCOHOL,
            headline = "Regular heavy drinking can damage the liver.",
            body = "The NHS describes alcohol-related liver disease as a consequence of drinking too " +
                "much over a long period. The liver often shows no obvious symptoms until damage is " +
                "advanced, which is part of why it is easy to underestimate.",
            sourceId = Sources.NHS_ALCOHOL.id,
        ),
        Fact(
            id = "alc_heart",
            topic = Fact.Topic.ALCOHOL,
            headline = "Excessive drinking is associated with heart disease and stroke.",
            body = "CDC lists high blood pressure, heart disease and stroke among the long-term health " +
                "risks of excessive alcohol use.",
            sourceId = Sources.CDC_ALCOHOL.id,
        ),
        Fact(
            id = "alc_mental_health",
            topic = Fact.Topic.ALCOHOL,
            headline = "Alcohol affects mood, sleep and relationships.",
            body = "CDC and NHS both describe links between excessive alcohol use and mental health " +
                "problems including depression and anxiety, as well as effects on family and social " +
                "life. Alcohol can feel like it takes the edge off in the moment while making the " +
                "underlying feeling harder to manage.",
            sourceId = Sources.CDC_ALCOHOL.id,
        ),
        Fact(
            id = "alc_less_is_better",
            topic = Fact.Topic.ALCOHOL,
            headline = "Drinking less lowers the risk. Every alcohol-free day counts.",
            body = "UK Chief Medical Officers advise that to keep health risks low, adults should not " +
                "regularly drink more than 14 units a week, spread over three or more days, with " +
                "several drink-free days. Risk reduction is a slope, not a switch: less is better, " +
                "and none is better still.",
            sourceId = Sources.UK_CMO_GUIDELINES.id,
            period = "2016 guidelines",
            population = "UK adults",
        ),
        Fact(
            id = "alc_standard_drink",
            topic = Fact.Topic.ALCOHOL,
            headline = "A 'drink' is not a fixed amount — serving sizes vary a lot.",
            body = "A UK unit is 10ml (8g) of pure alcohol. A US standard drink is about 14g. A strong " +
                "pint or a generous glass poured at home can easily be two or three of either. When you " +
                "estimate what you were drinking, it is usually more than it feels.",
            sourceId = Sources.NIAAA_STANDARD_DRINK.id,
        ),
        Fact(
            id = "alc_withdrawal_safety",
            topic = Fact.Topic.ALCOHOL,
            headline = "If you drink heavily or daily, do not stop suddenly without advice.",
            body = "For someone who has become physically dependent on alcohol, stopping abruptly can " +
                "cause withdrawal that is dangerous and occasionally life-threatening. The NHS advises " +
                "medical support for withdrawal in these cases. This is the one part of quitting where " +
                "going faster is not braver — speak to a healthcare professional first.",
            sourceId = Sources.NHS_ALCOHOL_WITHDRAWAL.id,
        ),
    )

    val GENERAL: List<Fact> = listOf(
        Fact(
            id = "gen_quitting_works",
            topic = Fact.Topic.BOTH,
            headline = "Stopping is one of the most valuable things you can do for your health.",
            body = "The 2020 Surgeon General's report on smoking cessation concluded that quitting " +
                "benefits people at any age and improves health across many different conditions. " +
                "It is never too late for it to be worth doing.",
            sourceId = Sources.SURGEON_GENERAL_CESSATION.id,
            period = "2020 report",
        ),
        Fact(
            id = "gen_relapse_normal",
            topic = Fact.Topic.BOTH,
            headline = "Most people who succeed have stopped more than once.",
            body = "Research on quitting consistently describes it as a process rather than a single " +
                "event, with many people making several attempts before it sticks. A slip is a common " +
                "part of the path, not evidence that the path is closed to you.",
            sourceId = Sources.SURGEON_GENERAL_CESSATION.id,
            period = "2020 report",
        ),
        Fact(
            id = "gen_support_helps",
            topic = Fact.Topic.BOTH,
            headline = "Support improves the odds.",
            body = "Both NHS and CDC guidance point people towards structured support — services, " +
                "helplines, medication where appropriate — rather than willpower alone. You are allowed " +
                "to use every tool available.",
            sourceId = Sources.NHS_QUIT_SMOKING.id,
        ),
    )

    val ALL: List<Fact> = NICOTINE + ALCOHOL + GENERAL

    fun byId(id: String): Fact? = ALL.firstOrNull { it.id == id }

    /** Only the topics that apply to what he is actually quitting. */
    fun relevantTo(quitNicotine: Boolean, quitAlcohol: Boolean): List<Fact> = buildList {
        if (quitNicotine) addAll(NICOTINE)
        if (quitAlcohol) addAll(ALCOHOL)
        addAll(GENERAL)
    }
}
