package com.dadsvictory.domain.content

import com.dadsvictory.domain.Country

/**
 * Country-specific help. The UK is the default and the UK experience never shows
 * US numbers, or the other way round — a wrong emergency number is worse than none.
 */
data class SupportContact(
    val id: String,
    val title: String,
    val detail: String,
    /** Dialable number, digits and + only, or null for a web-only resource. */
    val phone: String? = null,
    val url: String? = null,
    val kind: Kind,
) {
    enum class Kind { EMERGENCY, URGENT, QUIT_NICOTINE, ALCOHOL, TALK, INFO }
}

object Support {

    private val UK = listOf(
        SupportContact(
            "uk_999", "Emergency services — 999",
            "For a medical emergency: severe confusion, a seizure, hallucinations, difficulty staying " +
                "conscious, severe vomiting, or anything you believe is life-threatening.",
            phone = "999", kind = SupportContact.Kind.EMERGENCY,
        ),
        SupportContact(
            "uk_111", "NHS 111",
            "Urgent medical advice when it is not a 999 emergency. Open 24 hours.",
            phone = "111", url = "https://111.nhs.uk/", kind = SupportContact.Kind.URGENT,
        ),
        SupportContact(
            "uk_gp", "Your GP surgery",
            "The right place to talk about quitting safely, about withdrawal, and about medication " +
                "that can help. Ask for an appointment about stopping smoking or stopping drinking.",
            kind = SupportContact.Kind.URGENT,
        ),
        SupportContact(
            "uk_stop_smoking", "NHS stop smoking services",
            "Free local support in England. People who use these services are generally more likely " +
                "to succeed than people quitting unaided.",
            url = "https://www.nhs.uk/better-health/quit-smoking/find-your-local-stop-smoking-service/",
            kind = SupportContact.Kind.QUIT_NICOTINE,
        ),
        SupportContact(
            "uk_better_health", "NHS Better Health — Quit Smoking",
            "NHS advice, a quit plan and the free NHS Quit Smoking app.",
            url = "https://www.nhs.uk/better-health/quit-smoking/",
            kind = SupportContact.Kind.QUIT_NICOTINE,
        ),
        SupportContact(
            "uk_drinkline", "Drinkline — 0300 123 1110",
            "The national alcohol helpline. Free, confidential advice for anyone worried about their " +
                "own drinking or someone else's.",
            phone = "03001231110", kind = SupportContact.Kind.ALCOHOL,
        ),
        SupportContact(
            "uk_nhs_alcohol", "NHS alcohol advice",
            "NHS information on units, cutting down, alcohol misuse and treatment.",
            url = "https://www.nhs.uk/live-well/alcohol-advice/",
            kind = SupportContact.Kind.ALCOHOL,
        ),
        SupportContact(
            "uk_aa", "Alcoholics Anonymous (Great Britain) — 0800 9177 650",
            "Free national helpline, 24 hours.",
            phone = "08009177650", url = "https://www.alcoholics-anonymous.org.uk/",
            kind = SupportContact.Kind.ALCOHOL,
        ),
        SupportContact(
            "uk_alcohol_change", "Alcohol Change UK",
            "Information, tools and support for changing your relationship with alcohol.",
            url = "https://alcoholchange.org.uk/", kind = SupportContact.Kind.INFO,
        ),
        SupportContact(
            "uk_samaritans", "Samaritans — 116 123",
            "Free, 24 hours, every day. For anything at all that is troubling you. You do not have to " +
                "be in crisis to call.",
            phone = "116123", url = "https://www.samaritans.org/",
            kind = SupportContact.Kind.TALK,
        ),
    )

    private val US = listOf(
        SupportContact(
            "us_911", "Emergency services — 911",
            "For a medical emergency: severe confusion, a seizure, hallucinations, difficulty staying " +
                "conscious, severe vomiting, or anything you believe is life-threatening.",
            phone = "911", kind = SupportContact.Kind.EMERGENCY,
        ),
        SupportContact(
            "us_988", "988 Suicide & Crisis Lifeline",
            "Call or text 988 for free, confidential crisis support, 24 hours a day.",
            phone = "988", url = "https://988lifeline.org/",
            kind = SupportContact.Kind.TALK,
        ),
        SupportContact(
            "us_quitnow", "1-800-QUIT-NOW",
            "Free tobacco quitline coaching. 1-800-784-8669.",
            phone = "18007848669", url = "https://smokefree.gov/",
            kind = SupportContact.Kind.QUIT_NICOTINE,
        ),
        SupportContact(
            "us_samhsa", "SAMHSA National Helpline — 1-800-662-HELP",
            "Free, confidential, 24/7 treatment referral and information for substance use. " +
                "1-800-662-4357.",
            phone = "18006624357", url = "https://www.samhsa.gov/find-help/national-helpline",
            kind = SupportContact.Kind.ALCOHOL,
        ),
        SupportContact(
            "us_niaaa_navigator", "NIAAA Alcohol Treatment Navigator",
            "Help finding quality alcohol treatment near you.",
            url = "https://alcoholtreatment.niaaa.nih.gov/",
            kind = SupportContact.Kind.ALCOHOL,
        ),
        SupportContact(
            "us_doctor", "Your doctor",
            "The right place to talk about quitting safely, about withdrawal, and about medication " +
                "that can help.",
            kind = SupportContact.Kind.URGENT,
        ),
        SupportContact(
            "us_cdc_alcohol", "CDC — Alcohol Use and Your Health",
            "Public health information on the effects of excessive alcohol use.",
            url = "https://www.cdc.gov/alcohol/index.html", kind = SupportContact.Kind.INFO,
        ),
    )

    private val OTHER = listOf(
        SupportContact(
            "other_emergency", "Your local emergency number",
            "In much of Europe this is 112. Elsewhere it differs — it is worth knowing yours before " +
                "you need it. Call it for severe confusion, a seizure, hallucinations, difficulty " +
                "staying conscious, severe vomiting, or anything life-threatening.",
            kind = SupportContact.Kind.EMERGENCY,
        ),
        SupportContact(
            "other_doctor", "A doctor or pharmacist",
            "A pharmacist can usually be seen the same day and can advise on nicotine replacement. " +
                "A doctor is the right person to speak to before stopping alcohol if you drink heavily " +
                "or daily.",
            kind = SupportContact.Kind.URGENT,
        ),
        SupportContact(
            "other_iasp", "Find a crisis centre near you",
            "The International Association for Suicide Prevention lists crisis centres worldwide.",
            url = "https://www.iasp.info/crisis-centres-helplines/", kind = SupportContact.Kind.TALK,
        ),
        SupportContact(
            "other_who", "World Health Organization — tobacco and alcohol",
            "International public health information.",
            url = "https://www.who.int/health-topics/tobacco", kind = SupportContact.Kind.INFO,
        ),
    )

    fun contactsFor(country: Country): List<SupportContact> = when (country) {
        Country.UK -> UK
        Country.US -> US
        Country.OTHER -> OTHER
    }

    fun emergencyFor(country: Country): SupportContact =
        contactsFor(country).first { it.kind == SupportContact.Kind.EMERGENCY }

    fun ofKind(country: Country, kind: SupportContact.Kind): List<SupportContact> =
        contactsFor(country).filter { it.kind == kind }

    /** The four routes offered by the "Need help?" button. */
    enum class HelpRoute(val id: String, val title: String, val blurb: String) {
        TALK(
            "talk", "I need someone to talk to",
            "Someone will listen, right now, without judging you and without you having to explain " +
                "your whole life first.",
        ),
        MEDICAL(
            "medical", "I need medical advice",
            "About quitting safely, about withdrawal, or about anything that doesn't feel right.",
        ),
        WITHDRAWAL(
            "withdrawal", "I think I'm having dangerous withdrawal symptoms",
            "Alcohol withdrawal can be serious. This is not something to wait out at home on your own.",
        ),
        DANGER(
            "danger", "I am in immediate danger",
            "Call emergency services now.",
        ),
    }

    const val EMERGENCY_GUIDANCE: String =
        "If you or someone else has severe confusion, seizures, hallucinations, difficulty staying " +
            "conscious, severe vomiting, or another medical emergency, seek emergency medical help " +
            "immediately."

    const val ALCOHOL_WITHDRAWAL_WARNING: String =
        "If you drink heavily or every day, stopping alcohol suddenly can sometimes cause dangerous " +
            "withdrawal. Speak with a doctor or healthcare professional before stopping abruptly."

    const val NOT_A_DOCTOR: String =
        "Dad's Victory is not a doctor and cannot diagnose anything. It does not know whether you are " +
            "dependent on alcohol — only a healthcare professional can help you work that out."

    /**
     * Whether to put the "Before you stop" safety screen in front of him during setup.
     *
     * This is a prompt to get advice, not an assessment. It deliberately triggers
     * easily and says so on screen: the cost of showing it unnecessarily is a
     * screen tap, and the cost of not showing it could be serious.
     */
    fun shouldShowAlcoholSafetyScreen(drinksPerWeek: Double, drinkingDaysPerWeek: Int): Boolean =
        drinksPerWeek >= 14.0 || drinkingDaysPerWeek >= 5
}
