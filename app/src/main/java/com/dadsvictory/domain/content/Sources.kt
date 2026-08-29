package com.dadsvictory.domain.content

/**
 * Every health statement shown anywhere in this app points at one of these.
 *
 * The rule the app is built on: if a claim cannot be attributed to a named
 * organisation, it does not go in. Nothing here is invented, and nothing is
 * quantified more precisely than the source quantifies it.
 */
data class Source(
    val id: String,
    val organisation: String,
    val title: String,
    val detail: String,
    val url: String,
)

object Sources {

    val CDC_ECIGS = Source(
        id = "cdc_ecigs",
        organisation = "Centers for Disease Control and Prevention (CDC), USA",
        title = "E-cigarettes (vapes)",
        detail = "CDC public health guidance on e-cigarettes and nicotine.",
        url = "https://www.cdc.gov/tobacco/e-cigarettes/index.html",
    )

    val CDC_ECIG_ADULTS = Source(
        id = "cdc_ecig_adults",
        organisation = "Centers for Disease Control and Prevention (CDC), USA",
        title = "E-Cigarette Use Among Adults",
        detail = "Includes findings on quit intentions among US adults who currently used e-cigarettes, 2016–2018.",
        url = "https://www.cdc.gov/tobacco/e-cigarettes/adults.html",
    )

    val CDC_ALCOHOL_DEATHS = Source(
        id = "cdc_alcohol_deaths",
        organisation = "CDC / MMWR, USA",
        title = "Deaths from Excessive Alcohol Use — United States, 2016–2021",
        detail = "MMWR Vol. 73, No. 8, published February 2024. Esser MB, Sherk A, Liu Y, Naimi TS.",
        url = "https://www.cdc.gov/mmwr/volumes/73/wr/mm7308a1.htm",
    )

    val CDC_ALCOHOL = Source(
        id = "cdc_alcohol",
        organisation = "Centers for Disease Control and Prevention (CDC), USA",
        title = "Alcohol Use and Your Health",
        detail = "CDC guidance on the health effects of excessive alcohol use.",
        url = "https://www.cdc.gov/alcohol/index.html",
    )

    val NHS_QUIT_SMOKING = Source(
        id = "nhs_quit",
        organisation = "NHS (United Kingdom)",
        title = "Better Health — Quit Smoking",
        detail = "NHS stop smoking advice and local stop smoking services.",
        url = "https://www.nhs.uk/better-health/quit-smoking/",
    )

    val NHS_VAPING = Source(
        id = "nhs_vaping",
        organisation = "NHS (United Kingdom)",
        title = "Vaping to quit smoking",
        detail = "NHS position on vaping: far less harmful than smoking, but not risk free, and the long-term effects are not yet known.",
        url = "https://www.nhs.uk/better-health/quit-smoking/vaping-to-quit-smoking/",
    )

    val NHS_ALCOHOL = Source(
        id = "nhs_alcohol",
        organisation = "NHS (United Kingdom)",
        title = "Alcohol advice",
        detail = "NHS guidance on alcohol units, alcohol misuse and getting support.",
        url = "https://www.nhs.uk/live-well/alcohol-advice/",
    )

    val UK_CMO_GUIDELINES = Source(
        id = "uk_cmo",
        organisation = "UK Chief Medical Officers",
        title = "Low Risk Drinking Guidelines (2016)",
        detail = "UK CMOs' advice on weekly alcohol consumption for adults.",
        url = "https://www.gov.uk/government/publications/alcohol-consumption-advice-on-low-risk-drinking",
    )

    val NIAAA_STANDARD_DRINK = Source(
        id = "niaaa_drink",
        organisation = "National Institute on Alcohol Abuse and Alcoholism (NIAAA), USA",
        title = "What is a standard drink?",
        detail = "Definition of a US standard drink and its alcohol content.",
        url = "https://www.niaaa.nih.gov/alcohols-effects-health/what-standard-drink",
    )

    val NIDA_NICOTINE = Source(
        id = "nida_nicotine",
        organisation = "National Institute on Drug Abuse (NIDA), USA",
        title = "Tobacco, Nicotine, and E-Cigarettes Research Report",
        detail = "Research summary on nicotine addiction and withdrawal.",
        url = "https://nida.nih.gov/publications/research-reports/tobacco-nicotine-e-cigarettes",
    )

    val SURGEON_GENERAL_CESSATION = Source(
        id = "sg_cessation",
        organisation = "US Surgeon General",
        title = "Smoking Cessation: A Report of the Surgeon General (2020)",
        detail = "Comprehensive review of the benefits of stopping smoking.",
        url = "https://www.ncbi.nlm.nih.gov/books/NBK555591/",
    )

    val IARC_ALCOHOL = Source(
        id = "iarc_alcohol",
        organisation = "International Agency for Research on Cancer (IARC), WHO",
        title = "Alcohol consumption and ethyl carbamate (Monographs Vol. 100E)",
        detail = "Alcoholic beverages are classified by IARC as a Group 1 carcinogen (carcinogenic to humans).",
        url = "https://monographs.iarc.who.int/list-of-classifications",
    )

    val NHS_ALCOHOL_WITHDRAWAL = Source(
        id = "nhs_withdrawal",
        organisation = "NHS (United Kingdom)",
        title = "Alcohol misuse — treatment",
        detail = "NHS information on alcohol dependence and why medically supported withdrawal may be needed.",
        url = "https://www.nhs.uk/conditions/alcohol-misuse/treatment/",
    )

    val ALL: List<Source> = listOf(
        CDC_ECIGS,
        CDC_ECIG_ADULTS,
        CDC_ALCOHOL_DEATHS,
        CDC_ALCOHOL,
        NHS_QUIT_SMOKING,
        NHS_VAPING,
        NHS_ALCOHOL,
        UK_CMO_GUIDELINES,
        NIAAA_STANDARD_DRINK,
        NIDA_NICOTINE,
        SURGEON_GENERAL_CESSATION,
        IARC_ALCOHOL,
        NHS_ALCOHOL_WITHDRAWAL,
    )

    fun byId(id: String): Source? = ALL.firstOrNull { it.id == id }

    const val DISCLAIMER: String =
        "Health information in this app is educational and does not replace medical advice. " +
            "This app is not a doctor. For advice about your own health, speak to a doctor, " +
            "pharmacist or another qualified healthcare professional."

    const val SCRIPTURE_LICENSING: String =
        "Bible passages are shown from the World English Bible (WEB), which is in the public domain, " +
            "and the King James Version (KJV), which is in the public domain in the United States. " +
            "In the United Kingdom the KJV is subject to Crown copyright, under which short quotations " +
            "of the kind used here are permitted. Only short passages are included; no complete " +
            "copyrighted translation is reproduced."
}
