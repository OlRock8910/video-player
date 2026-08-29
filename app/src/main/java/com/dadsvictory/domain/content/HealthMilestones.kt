package com.dadsvictory.domain.content

import com.dadsvictory.domain.Substance

/**
 * The "Your Progress" timeline.
 *
 * Deliberately conservative. Popular quit-timelines promise very specific things
 * at very specific hours ("at 20 minutes X happens, at 12 hours Y happens"), and
 * the evidence behind those precise claims is weaker than the confident phrasing
 * suggests. So the milestones here mark *his* progress at a given point and pair
 * it with something a named health body actually says. Where the science is not
 * settled, the entry says so rather than inventing certainty.
 */
data class Milestone(
    val id: String,
    val afterDays: Int,
    /** null means the entry applies whatever he is quitting. */
    val substance: Substance?,
    val title: String,
    val body: String,
    val sourceId: String,
) {
    val source: Source? get() = Sources.byId(sourceId)
}

object HealthMilestones {

    val ALL: List<Milestone> = listOf(
        Milestone(
            id = "start",
            afterDays = 0,
            substance = null,
            title = "You've started",
            body = "The decision is the part that has to happen first, and it has happened. " +
                "Health bodies including the NHS describe stopping as one of the most valuable things " +
                "a person can do for their health, at any age.",
            sourceId = Sources.SURGEON_GENERAL_CESSATION.id,
        ),
        Milestone(
            id = "nic_day_1",
            afterDays = 1,
            substance = Substance.NICOTINE,
            title = "One full day without nicotine",
            body = "Your body begins adjusting after stopping nicotine. Research describes withdrawal " +
                "symptoms — cravings, irritability, restlessness, poor concentration, disturbed sleep — " +
                "as typically beginning within the first day. If today felt rough, that is the expected " +
                "shape of this, not a sign it isn't working.",
            sourceId = Sources.NIDA_NICOTINE.id,
        ),
        Milestone(
            id = "alc_day_1",
            afterDays = 1,
            substance = Substance.ALCOHOL,
            title = "One alcohol-free day",
            body = "Every alcohol-free day is a day without alcohol-related exposure. UK Chief Medical " +
                "Officers specifically recommend drink-free days as a way of reducing risk.",
            sourceId = Sources.UK_CMO_GUIDELINES.id,
        ),
        Milestone(
            id = "nic_day_3",
            afterDays = 3,
            substance = Substance.NICOTINE,
            title = "Three days free of nicotine",
            body = "Research on nicotine withdrawal describes symptoms that generally peak in the " +
                "first few days and then ease over the following weeks. The hardest part is usually " +
                "not permanent — and it is usually near the beginning.",
            sourceId = Sources.NIDA_NICOTINE.id,
        ),
        Milestone(
            id = "week_1",
            afterDays = 7,
            substance = null,
            title = "One week",
            body = "A week of decisions, not one decision. Withdrawal symptoms often become easier " +
                "with time, though the pattern differs from person to person. If yours are still hard, " +
                "a doctor or pharmacist can talk you through evidence-based options.",
            sourceId = Sources.NHS_QUIT_SMOKING.id,
        ),
        Milestone(
            id = "week_2",
            afterDays = 14,
            substance = null,
            title = "Two weeks",
            body = "Habits are held in place by cues as much as by chemistry. Two weeks of meeting " +
                "those cues without giving in is real rewiring, and it is why the third week is " +
                "usually easier than the first.",
            sourceId = Sources.SURGEON_GENERAL_CESSATION.id,
        ),
        Milestone(
            id = "month_1",
            afterDays = 30,
            substance = null,
            title = "One month",
            body = "The 2020 Surgeon General's report concluded that quitting benefits health at any " +
                "age and across many conditions. A month in, you are no longer 'trying to quit'. " +
                "You have quit, and you are maintaining it.",
            sourceId = Sources.SURGEON_GENERAL_CESSATION.id,
        ),
        Milestone(
            id = "alc_month_1",
            afterDays = 30,
            substance = Substance.ALCOHOL,
            title = "A month of alcohol-free days",
            body = "Risk from alcohol rises with the amount consumed, so reducing it lowers risk — " +
                "less is better, and none is better still. Thirty days is thirty days of that.",
            sourceId = Sources.CDC_ALCOHOL.id,
        ),
        Milestone(
            id = "month_3",
            afterDays = 90,
            substance = null,
            title = "Three months",
            body = "Quitting is described in the evidence as a process rather than a single event, " +
                "and three months is well past the point where most people expected to have stopped " +
                "thinking about it every hour.",
            sourceId = Sources.SURGEON_GENERAL_CESSATION.id,
        ),
        Milestone(
            id = "month_6",
            afterDays = 180,
            substance = null,
            title = "Six months",
            body = "Half a year. Long-term risk reduction from stopping accumulates over time rather " +
                "than arriving on a particular date, which is exactly why staying stopped is the thing " +
                "that matters most now.",
            sourceId = Sources.SURGEON_GENERAL_CESSATION.id,
        ),
        Milestone(
            id = "year_1",
            afterDays = 365,
            substance = null,
            title = "One year",
            body = "A full year, through every season, every celebration and every bad week. " +
                "The benefits of stopping continue to build the longer it lasts.",
            sourceId = Sources.SURGEON_GENERAL_CESSATION.id,
        ),
    )

    /** Only the entries that apply to what he is quitting, in order. */
    fun timelineFor(quitNicotine: Boolean, quitAlcohol: Boolean): List<Milestone> = ALL
        .filter {
            when (it.substance) {
                null -> true
                Substance.NICOTINE -> quitNicotine
                Substance.ALCOHOL -> quitAlcohol
            }
        }
        .sortedBy { it.afterDays }

    fun reached(milestone: Milestone, daysFree: Int): Boolean = daysFree >= milestone.afterDays

    /** The next one ahead of him, for the "coming up" line on the dashboard. */
    fun nextAhead(quitNicotine: Boolean, quitAlcohol: Boolean, daysFree: Int): Milestone? =
        timelineFor(quitNicotine, quitAlcohol).firstOrNull { it.afterDays > daysFree }

    const val CAUTION: String =
        "These are markers on your journey paired with guidance from named health organisations. " +
            "Bodies recover differently, and this app deliberately avoids promising that a specific " +
            "change happens at a specific hour. For anything about your own health, talk to a doctor."
}
