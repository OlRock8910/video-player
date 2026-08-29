package com.dadsvictory.ui.checkin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.dadsvictory.domain.CheckIn
import com.dadsvictory.domain.Mood
import com.dadsvictory.ui.VictoryUiState
import com.dadsvictory.ui.VictoryViewModel
import com.dadsvictory.ui.components.BigButton
import com.dadsvictory.ui.components.InfoNote
import com.dadsvictory.ui.components.ScaleSelector
import com.dadsvictory.ui.components.SecondaryButton
import com.dadsvictory.ui.components.SectionHeader
import com.dadsvictory.ui.components.SelectableRow
import com.dadsvictory.ui.theme.ScreenPadding

/**
 * The daily check-in. Deliberately short — five taps and a box he can ignore.
 *
 * Note the wording of the two questions: "Did you stay nicotine-free today?" has a
 * "No" that leads to the slip screen, not to a telling-off.
 */
@Composable
fun CheckInScreen(
    viewModel: VictoryViewModel,
    state: VictoryUiState,
    navController: NavHostController,
) {
    val existing by viewModel.checkInForDay(state.todayEpochDay).collectAsState(initial = null)

    var mood by rememberSaveable { mutableIntStateOf(Mood.OKAY.score) }
    var craving by rememberSaveable { mutableIntStateOf(0) }
    var stress by rememberSaveable { mutableIntStateOf(0) }
    var nicotineFree by rememberSaveable { mutableStateOf<Boolean?>(null) }
    var alcoholFree by rememberSaveable { mutableStateOf<Boolean?>(null) }
    var note by rememberSaveable { mutableStateOf("") }
    var loadedFor by rememberSaveable { mutableStateOf(-1L) }

    // Editing today's check-in again should show what he already said.
    LaunchedEffect(existing, state.todayEpochDay) {
        val saved = existing
        if (saved != null && loadedFor != saved.epochDay) {
            mood = saved.moodScore
            craving = saved.cravingLevel
            stress = saved.stressLevel
            nicotineFree = saved.stayedNicotineFree
            alcoholFree = saved.stayedAlcoholFree
            note = saved.note
            loadedFor = saved.epochDay
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(ScreenPadding),
    ) {
        Text(
            "How are you doing today?",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
        )
        if (existing != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                "You've already checked in today. Changing anything just updates it.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(24.dp))
        SectionHeader("Mood")
        MoodRow(selected = mood, onSelect = { mood = it })

        Spacer(Modifier.height(24.dp))
        SectionHeader("Cravings today")
        ScaleSelector(value = craving, onValueChange = { craving = it }, lowLabel = "None", highLabel = "Constant")

        Spacer(Modifier.height(24.dp))
        SectionHeader("Stress today")
        ScaleSelector(value = stress, onValueChange = { stress = it }, lowLabel = "Calm", highLabel = "Overwhelmed")

        if (state.profile.quitNicotine) {
            Spacer(Modifier.height(24.dp))
            SectionHeader("Did you stay nicotine-free today?")
            YesNoRow(
                value = nicotineFree,
                onChange = { nicotineFree = it },
            )
        }

        if (state.profile.quitAlcohol) {
            Spacer(Modifier.height(24.dp))
            SectionHeader("Did you stay alcohol-free today?")
            YesNoRow(
                value = alcoholFree,
                onChange = { alcoholFree = it },
            )
        }

        if (nicotineFree == false || alcoholFree == false) {
            Spacer(Modifier.height(16.dp))
            InfoNote(
                "Thank you for being honest — that is genuinely the hard part. Save this, then " +
                    "record it on the slip screen if you'd like to work out what to change.",
            )
        }

        Spacer(Modifier.height(24.dp))
        SectionHeader("Anything you want to note?")
        OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
            placeholder = { Text("Optional.") },
        )

        Spacer(Modifier.height(28.dp))
        BigButton(
            text = if (existing == null) "Save today's check-in" else "Update today's check-in",
            onClick = {
                viewModel.saveCheckIn(
                    CheckIn(
                        epochDay = state.todayEpochDay,
                        moodScore = mood,
                        cravingLevel = craving,
                        stressLevel = stress,
                        stayedNicotineFree = if (state.profile.quitNicotine) nicotineFree else null,
                        stayedAlcoholFree = if (state.profile.quitAlcohol) alcoholFree else null,
                        note = note.trim(),
                    ),
                )
                navController.popBackStack()
            },
        )
        Spacer(Modifier.height(8.dp))
        SecondaryButton(text = "Not now", onClick = { navController.popBackStack() })
        Spacer(Modifier.height(24.dp))
    }
}

/** Emoji plus the word, so the scale never depends on reading a face. */
@Composable
private fun MoodRow(selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        for (mood in Mood.entries) {
            val isSelected = mood.score == selected
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 76.dp)
                    .selectable(selected = isSelected, onClick = { onSelect(mood.score) })
                    .semantics { contentDescription = if (isSelected) "${mood.label}, selected" else mood.label },
                shape = RoundedCornerShape(14.dp),
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainer
                },
            ) {
                Column(
                    Modifier.padding(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(mood.emoji, style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        mood.label,
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun YesNoRow(value: Boolean?, onChange: (Boolean?) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SelectableRow(
            label = "Yes",
            emoji = "✅",
            selected = value == true,
            onToggle = { onChange(if (value == true) null else true) },
            modifier = Modifier.weight(1f),
        )
        SelectableRow(
            label = "No",
            emoji = "🔁",
            selected = value == false,
            onToggle = { onChange(if (value == false) null else false) },
            modifier = Modifier.weight(1f),
        )
    }
    if (value == null) {
        Spacer(Modifier.height(6.dp))
        Text(
            "Leave this blank if you'd rather not say.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
