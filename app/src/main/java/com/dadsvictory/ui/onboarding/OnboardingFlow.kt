package com.dadsvictory.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dadsvictory.domain.Country
import com.dadsvictory.domain.Currency
import com.dadsvictory.domain.DrinkBasis
import com.dadsvictory.domain.Money
import com.dadsvictory.domain.Profile
import com.dadsvictory.domain.Reason
import com.dadsvictory.domain.content.Motivation
import com.dadsvictory.domain.content.Sources
import com.dadsvictory.domain.content.Support
import com.dadsvictory.ui.components.BigButton
import com.dadsvictory.ui.components.InfoNote
import com.dadsvictory.ui.components.SafetyBanner
import com.dadsvictory.ui.components.SelectableRow
import com.dadsvictory.ui.components.VictoryCard
import com.dadsvictory.ui.theme.ScreenPadding
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** What the flow has collected so far. */
private data class Draft(
    val quitNicotine: Boolean = true,
    val quitAlcohol: Boolean = true,
    val country: Country = Country.UK,
    val currency: Currency = Currency.GBP,
    val startMillis: Long = System.currentTimeMillis(),
    val reasonIds: Set<String> = emptySet(),
    val customReason: String = "",
    val nicotineWeeklyMinor: Long = 0L,
    val alcoholWeeklyMinor: Long = 0L,
    val vapeSessionsPerDay: Int = 0,
    val puffsPerDay: Int = 0,
    val nicotineStrength: Double = 0.0,
    val drinkBasis: DrinkBasis = DrinkBasis.UK_UNITS,
    val drinksPerWeek: Double = 0.0,
    val drinkingDaysPerWeek: Int = 0,
) {
    fun toProfile(): Profile = Profile(
        quitNicotine = quitNicotine,
        quitAlcohol = quitAlcohol,
        startMillis = startMillis,
        reasonIds = reasonIds,
        customReason = customReason,
        country = country,
        currency = currency,
        nicotineWeeklySpendMinor = if (quitNicotine) nicotineWeeklyMinor else 0L,
        alcoholWeeklySpendMinor = if (quitAlcohol) alcoholWeeklyMinor else 0L,
        vapeSessionsPerDay = vapeSessionsPerDay,
        puffsPerDay = puffsPerDay,
        nicotineStrengthMgPerMl = nicotineStrength,
        drinkBasis = drinkBasis,
        drinksPerWeek = drinksPerWeek,
    )
}

private enum class Step {
    WELCOME,
    WHAT,
    WHERE,
    WHEN,
    WHY,
    SPENDING,
    VAPING,
    DRINKING,
    ALCOHOL_SAFETY,
    NOTIFICATIONS,
}

@Composable
fun OnboardingFlow(onFinished: (Profile, Int) -> Unit) {
    var draft by rememberSaveable(stateSaver = DraftSaver) { mutableStateOf(Draft()) }
    var index by rememberSaveable { mutableIntStateOf(0) }

    // The flow adapts: someone quitting only alcohol never sees the vaping questions,
    // and the safety screen only appears when the answers suggest it should.
    val steps = remember(draft.quitNicotine, draft.quitAlcohol, draft.drinksPerWeek, draft.drinkingDaysPerWeek) {
        buildList {
            add(Step.WELCOME)
            add(Step.WHAT)
            add(Step.WHERE)
            add(Step.WHEN)
            add(Step.WHY)
            add(Step.SPENDING)
            if (draft.quitNicotine) add(Step.VAPING)
            if (draft.quitAlcohol) add(Step.DRINKING)
            if (draft.quitAlcohol &&
                Support.shouldShowAlcoholSafetyScreen(draft.drinksPerWeek, draft.drinkingDaysPerWeek)
            ) {
                add(Step.ALCOHOL_SAFETY)
            }
            add(Step.NOTIFICATIONS)
        }
    }

    val safeIndex = index.coerceIn(0, steps.lastIndex)
    val step = steps[safeIndex]

    Scaffold { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = ScreenPadding),
        ) {
            LinearProgressIndicator(
                progress = { (safeIndex + 1f) / steps.size },
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            )

            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            ) {
                when (step) {
                    Step.WELCOME -> WelcomeStep()
                    Step.WHAT -> WhatStep(draft) { draft = it }
                    Step.WHERE -> WhereStep(draft) { draft = it }
                    Step.WHEN -> WhenStep(draft) { draft = it }
                    Step.WHY -> WhyStep(draft) { draft = it }
                    Step.SPENDING -> SpendingStep(draft) { draft = it }
                    Step.VAPING -> VapingStep(draft) { draft = it }
                    Step.DRINKING -> DrinkingStep(draft) { draft = it }
                    Step.ALCOHOL_SAFETY -> AlcoholSafetyStep()
                    Step.NOTIFICATIONS -> NotificationsStep()
                }
                Spacer(Modifier.height(24.dp))
            }

            val canContinue = when (step) {
                Step.WHAT -> draft.quitNicotine || draft.quitAlcohol
                else -> true
            }

            Column(Modifier.padding(bottom = 20.dp)) {
                BigButton(
                    text = if (safeIndex == steps.lastIndex) "START MY JOURNEY" else "Continue",
                    enabled = canContinue,
                    onClick = {
                        if (safeIndex == steps.lastIndex) {
                            onFinished(draft.toProfile(), draft.drinkingDaysPerWeek)
                        } else {
                            index = safeIndex + 1
                        }
                    },
                )
                if (safeIndex > 0) {
                    Spacer(Modifier.height(6.dp))
                    TextButton(
                        onClick = { index = safeIndex - 1 },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Back") }
                }
            }
        }
    }
}

@Composable
private fun StepHeading(title: String, subtitle: String? = null) {
    Spacer(Modifier.height(8.dp))
    Text(title, style = MaterialTheme.typography.headlineMedium)
    if (subtitle != null) {
        Spacer(Modifier.height(8.dp))
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Spacer(Modifier.height(20.dp))
}

@Composable
private fun WelcomeStep() {
    Spacer(Modifier.height(40.dp))
    Text("🌄", style = MaterialTheme.typography.displayLarge)
    Spacer(Modifier.height(16.dp))
    Text(
        "Welcome to Dad's Victory",
        style = MaterialTheme.typography.headlineLarge,
        fontWeight = FontWeight.Bold,
    )
    Spacer(Modifier.height(12.dp))
    Text(
        "Every day you choose your health, your family, and your future.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(28.dp))
    VictoryCard {
        Column {
            Text(
                Motivation.CORE_MESSAGE,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    Spacer(Modifier.height(20.dp))
    InfoNote(
        "Everything you enter stays on this phone. There is no account, no advertising and " +
            "no tracking. " + Sources.DISCLAIMER,
    )
}

@Composable
private fun WhatStep(draft: Draft, onChange: (Draft) -> Unit) {
    StepHeading("What are you quitting?", "Pick one or both. You can change this later.")

    SelectableRow(
        label = "Vaping / nicotine",
        emoji = "🚭",
        selected = draft.quitNicotine,
        onToggle = { onChange(draft.copy(quitNicotine = !draft.quitNicotine)) },
    )
    Spacer(Modifier.height(10.dp))
    SelectableRow(
        label = "Alcohol",
        emoji = "🍺",
        selected = draft.quitAlcohol,
        onToggle = { onChange(draft.copy(quitAlcohol = !draft.quitAlcohol)) },
    )
    Spacer(Modifier.height(10.dp))
    SelectableRow(
        label = "Both",
        emoji = "💪",
        supporting = "Track them separately, on one journey",
        selected = draft.quitNicotine && draft.quitAlcohol,
        onToggle = {
            val both = !(draft.quitNicotine && draft.quitAlcohol)
            onChange(draft.copy(quitNicotine = both, quitAlcohol = both))
        },
    )

    if (!draft.quitNicotine && !draft.quitAlcohol) {
        Spacer(Modifier.height(16.dp))
        InfoNote("Choose at least one to carry on.")
    }
}

@Composable
private fun WhereStep(draft: Draft, onChange: (Draft) -> Unit) {
    StepHeading(
        "Where are you?",
        "This decides which health services and emergency numbers the app shows you. " +
            "Getting this right matters — the wrong emergency number is worse than none.",
    )

    for (country in Country.entries) {
        SelectableRow(
            label = country.label,
            emoji = country.flag,
            selected = draft.country == country,
            onToggle = {
                onChange(
                    draft.copy(
                        country = country,
                        currency = Currency.defaultFor(country),
                        drinkBasis = DrinkBasis.defaultFor(country),
                    ),
                )
            },
        )
        Spacer(Modifier.height(10.dp))
    }

    Spacer(Modifier.height(12.dp))
    Text("Currency", style = MaterialTheme.typography.titleSmall)
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        for (currency in Currency.entries) {
            SelectableRow(
                label = currency.symbol,
                selected = draft.currency == currency,
                onToggle = { onChange(draft.copy(currency = currency)) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WhenStep(draft: Draft, onChange: (Draft) -> Unit) {
    StepHeading("When do you want to begin?")

    val zone = ZoneId.systemDefault()
    val now = System.currentTimeMillis()
    val isToday = kotlin.math.abs(draft.startMillis - now) < 60_000L

    SelectableRow(
        label = "Today — starting now",
        emoji = "☀️",
        selected = isToday,
        onToggle = { onChange(draft.copy(startMillis = System.currentTimeMillis())) },
    )
    Spacer(Modifier.height(10.dp))

    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    val chosen = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(draft.startMillis), zone)

    SelectableRow(
        label = "A different date and time",
        emoji = "📅",
        supporting = "%02d/%02d/%d at %02d:%02d".format(
            chosen.dayOfMonth, chosen.monthValue, chosen.year, chosen.hour, chosen.minute,
        ),
        selected = !isToday,
        onToggle = { showDatePicker = true },
    )

    Spacer(Modifier.height(16.dp))
    InfoNote(
        "You can pick a date in the past if you already stopped, or a date in the future if " +
            "you are getting ready. A future date shows as 'not started yet' until it arrives.",
    )

    if (showDatePicker) {
        val state = androidx.compose.material3.rememberDatePickerState(
            initialSelectedDateMillis = draft.startMillis,
        )
        androidx.compose.material3.DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val picked = state.selectedDateMillis
                    if (picked != null) {
                        // The picker works in UTC; keep the wall-clock date he chose.
                        val date = java.time.Instant.ofEpochMilli(picked).atZone(ZoneId.of("UTC")).toLocalDate()
                        val time = chosen.toLocalTime()
                        onChange(draft.copy(startMillis = date.atTime(time).atZone(zone).toInstant().toEpochMilli()))
                    }
                    showDatePicker = false
                    showTimePicker = true
                }) { Text("Next: time") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } },
        ) {
            androidx.compose.material3.DatePicker(state = state)
        }
    }

    if (showTimePicker) {
        val state = androidx.compose.material3.rememberTimePickerState(
            initialHour = chosen.hour,
            initialMinute = chosen.minute,
        )
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val date: LocalDate = chosen.toLocalDate()
                    val time = LocalTime.of(state.hour, state.minute)
                    onChange(draft.copy(startMillis = date.atTime(time).atZone(zone).toInstant().toEpochMilli()))
                    showTimePicker = false
                }) { Text("Done") }
            },
            dismissButton = { TextButton(onClick = { showTimePicker = false }) { Text("Cancel") } },
            title = { Text("What time?") },
            text = {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    androidx.compose.material3.TimePicker(state = state)
                }
            },
        )
    }
}

@Composable
private fun WhyStep(draft: Draft, onChange: (Draft) -> Unit) {
    StepHeading(
        "Why are you doing this?",
        "Pick as many as you like. These come back to you when a craving hits, which is " +
            "exactly when they are hardest to remember on your own.",
    )

    for (reason in Reason.entries) {
        SelectableRow(
            label = reason.label,
            emoji = "❤️",
            selected = reason.id in draft.reasonIds,
            onToggle = {
                val ids = draft.reasonIds.toMutableSet()
                if (!ids.add(reason.id)) ids.remove(reason.id)
                onChange(draft.copy(reasonIds = ids))
            },
        )
        Spacer(Modifier.height(8.dp))
    }

    Spacer(Modifier.height(12.dp))
    Text("❤️ My own reason", style = MaterialTheme.typography.titleSmall)
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
        value = draft.customReason,
        onValueChange = { onChange(draft.copy(customReason = it)) },
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text("In your own words…") },
        minLines = 2,
    )
}

@Composable
private fun SpendingStep(draft: Draft, onChange: (Draft) -> Unit) {
    StepHeading(
        "How much were you spending?",
        "A rough weekly figure is fine. It is only used to estimate what you are saving, " +
            "and you can change it later.",
    )

    if (draft.quitNicotine) {
        MoneyField(
            label = "Vaping / nicotine, per week",
            symbol = draft.currency.symbol,
            valueMinor = draft.nicotineWeeklyMinor,
            onValueChange = { onChange(draft.copy(nicotineWeeklyMinor = it)) },
            helper = "Pods, disposables, liquid, coils — everything, added up for a normal week.",
        )
        Spacer(Modifier.height(20.dp))
    }

    if (draft.quitAlcohol) {
        MoneyField(
            label = "Alcohol, per week",
            symbol = draft.currency.symbol,
            valueMinor = draft.alcoholWeeklyMinor,
            onValueChange = { onChange(draft.copy(alcoholWeeklyMinor = it)) },
            helper = "Shopping, the pub, rounds — a typical week rather than your best week.",
        )
        Spacer(Modifier.height(20.dp))
    }

    val weekly = (if (draft.quitNicotine) draft.nicotineWeeklyMinor else 0L) +
        (if (draft.quitAlcohol) draft.alcoholWeeklyMinor else 0L)

    if (weekly > 0) {
        VictoryCard {
            Column {
                Text("That works out at roughly", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                val yearly = (weekly * 365.25 / 7.0).toLong()
                Text(
                    Money.format(yearly, draft.currency) + " a year",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }

    Spacer(Modifier.height(8.dp))
    InfoNote("Leave a field at zero if it does not apply. Nothing here is judged.")
}

@Composable
private fun MoneyField(
    label: String,
    symbol: String,
    valueMinor: Long,
    onValueChange: (Long) -> Unit,
    helper: String,
) {
    var text by remember { mutableStateOf(if (valueMinor == 0L) "" else Money.toEditableText(valueMinor)) }

    Column {
        Text(label, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = text,
            onValueChange = {
                text = it
                onValueChange(Money.parseToMinor(it) ?: 0L)
            },
            modifier = Modifier.fillMaxWidth(),
            prefix = { Text(symbol) },
            placeholder = { Text("0.00") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            helper,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun NumberField(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    helper: String? = null,
    optional: Boolean = false,
) {
    var text by remember { mutableStateOf(if (value == 0) "" else value.toString()) }
    Column {
        Text(
            if (optional) "$label (optional)" else label,
            style = MaterialTheme.typography.titleSmall,
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = text,
            onValueChange = {
                val digits = it.filter { ch -> ch.isDigit() }.take(6)
                text = digits
                onValueChange(digits.toIntOrNull() ?: 0)
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        if (helper != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                helper,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun VapingStep(draft: Draft, onChange: (Draft) -> Unit) {
    StepHeading("How often were you vaping?", "Estimates are fine. Nobody counts exactly.")

    NumberField(
        label = "Vaping sessions per day",
        value = draft.vapeSessionsPerDay,
        onValueChange = { onChange(draft.copy(vapeSessionsPerDay = it)) },
        helper = "A 'session' is one time you picked it up, however long for.",
    )
    Spacer(Modifier.height(20.dp))

    NumberField(
        label = "Puffs per day",
        value = draft.puffsPerDay,
        onValueChange = { onChange(draft.copy(puffsPerDay = it)) },
        optional = true,
        helper = "Only if your device counts them.",
    )
    Spacer(Modifier.height(20.dp))

    var strengthText by remember {
        mutableStateOf(if (draft.nicotineStrength <= 0.0) "" else draft.nicotineStrength.toString())
    }
    Text("Nicotine strength in mg/ml (optional)", style = MaterialTheme.typography.titleSmall)
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
        value = strengthText,
        onValueChange = {
            strengthText = it
            onChange(draft.copy(nicotineStrength = it.replace(',', '.').toDoubleOrNull() ?: 0.0))
        },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        placeholder = { Text("e.g. 20") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
    )

    Spacer(Modifier.height(16.dp))
    InfoNote(
        "This app will not try to calculate how much nicotine you took in. It cannot know that, " +
            "and a made-up number would be worse than no number. These figures are only used for " +
            "a rough 'sessions avoided' count.",
    )
}

@Composable
private fun DrinkingStep(draft: Draft, onChange: (Draft) -> Unit) {
    StepHeading("How much were you drinking?", "A typical week, roughly. Honest beats flattering.")

    Text("How do you want to count?", style = MaterialTheme.typography.titleSmall)
    Spacer(Modifier.height(10.dp))
    for (basis in DrinkBasis.entries) {
        SelectableRow(
            label = basis.label,
            selected = draft.drinkBasis == basis,
            onToggle = { onChange(draft.copy(drinkBasis = basis)) },
        )
        Spacer(Modifier.height(8.dp))
    }

    Spacer(Modifier.height(8.dp))
    InfoNote(draft.drinkBasis.explainer)

    Spacer(Modifier.height(20.dp))
    var perWeekText by remember {
        mutableStateOf(if (draft.drinksPerWeek <= 0.0) "" else draft.drinksPerWeek.toString())
    }
    Text(
        "About how many ${if (draft.drinkBasis == DrinkBasis.UK_UNITS) "units" else "standard drinks"} a week?",
        style = MaterialTheme.typography.titleSmall,
    )
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
        value = perWeekText,
        onValueChange = {
            perWeekText = it
            onChange(draft.copy(drinksPerWeek = it.replace(',', '.').toDoubleOrNull() ?: 0.0))
        },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        placeholder = { Text("e.g. 20") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
    )

    Spacer(Modifier.height(20.dp))
    NumberField(
        label = "On how many days a week did you drink?",
        value = draft.drinkingDaysPerWeek,
        onValueChange = { onChange(draft.copy(drinkingDaysPerWeek = it.coerceIn(0, 7))) },
        helper = "0 to 7. This is only used to decide whether to show you a safety message.",
    )

    Spacer(Modifier.height(16.dp))
    InfoNote(
        "Serving sizes vary a lot, and a glass poured at home is usually bigger than a measure. " +
            "If you are not sure, round up rather than down.",
    )
}

/**
 * Shown when the answers suggest heavy or daily drinking. It does not diagnose
 * anything, and it says so: it is a prompt to talk to a professional before
 * stopping suddenly, because that is the one part of this where getting it wrong
 * can be dangerous.
 */
@Composable
private fun AlcoholSafetyStep() {
    Spacer(Modifier.height(8.dp))
    Text("Before you stop", style = MaterialTheme.typography.headlineMedium)
    Spacer(Modifier.height(20.dp))

    SafetyBanner(
        title = "Please read this first",
        body = Support.ALCOHOL_WITHDRAWAL_WARNING,
    )

    Spacer(Modifier.height(16.dp))
    VictoryCard {
        Column {
            Text(
                "Alcohol withdrawal can be dangerous for people who are physically dependent on " +
                    "alcohol. If you drink heavily or every day, speak with a healthcare " +
                    "professional before stopping suddenly. They can help you stop safely, and in " +
                    "some cases that means medication or supervision.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(16.dp))
            Text(Support.NOT_A_DOCTOR, style = MaterialTheme.typography.bodySmall)
        }
    }

    Spacer(Modifier.height(16.dp))
    SafetyBanner(
        title = "Emergency",
        body = Support.EMERGENCY_GUIDANCE,
    )

    Spacer(Modifier.height(16.dp))
    InfoNote(
        "You can carry on and use the app either way — nothing here is locked. This screen is " +
            "here because it would be wrong not to say it.",
    )
}

@Composable
private fun NotificationsStep() {
    StepHeading(
        "Daily encouragement",
        "Three messages a day: one to start well, one for the middle of the day when cravings " +
            "usually land, and one to close the day. That is all — this app will not nag you.",
    )

    VictoryCard {
        Column {
            NotificationPreviewRow("☀️", "Morning", "8:00 AM", "Start the day strong")
            Spacer(Modifier.height(14.dp))
            NotificationPreviewRow("🌤", "Afternoon", "2:00 PM", "Help through a craving")
            Spacer(Modifier.height(14.dp))
            NotificationPreviewRow("🌙", "Evening", "8:00 PM", "Reflect on the day you just won")
        }
    }

    Spacer(Modifier.height(16.dp))
    InfoNote(
        "You can change these times, or turn any of them off, in Settings at any time. " +
            "Android will ask your permission to send notifications after this.",
    )
}

@Composable
private fun NotificationPreviewRow(emoji: String, label: String, time: String, blurb: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(emoji)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleSmall)
            Text(
                blurb,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(time, style = MaterialTheme.typography.titleSmall)
    }
}

/**
 * Keeps the answers if Android recreates the activity mid-setup — a rotation or a
 * memory trim should not make him start the questions again.
 */
private val DraftSaver = androidx.compose.runtime.saveable.listSaver<Draft, Any>(
    save = {
        listOf(
            it.quitNicotine, it.quitAlcohol, it.country.id, it.currency.id, it.startMillis,
            it.reasonIds.toList(), it.customReason, it.nicotineWeeklyMinor, it.alcoholWeeklyMinor,
            it.vapeSessionsPerDay, it.puffsPerDay, it.nicotineStrength, it.drinkBasis.id,
            it.drinksPerWeek, it.drinkingDaysPerWeek,
        )
    },
    restore = {
        @Suppress("UNCHECKED_CAST")
        Draft(
            quitNicotine = it[0] as Boolean,
            quitAlcohol = it[1] as Boolean,
            country = Country.fromId(it[2] as String),
            currency = Currency.fromId(it[3] as String),
            startMillis = it[4] as Long,
            reasonIds = (it[5] as List<String>).toSet(),
            customReason = it[6] as String,
            nicotineWeeklyMinor = it[7] as Long,
            alcoholWeeklyMinor = it[8] as Long,
            vapeSessionsPerDay = it[9] as Int,
            puffsPerDay = it[10] as Int,
            nicotineStrength = it[11] as Double,
            drinkBasis = DrinkBasis.fromId(it[12] as String),
            drinksPerWeek = it[13] as Double,
            drinkingDaysPerWeek = it[14] as Int,
        )
    },
)
