package com.dadsvictory.ui.relapse

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.dadsvictory.domain.Substance
import com.dadsvictory.domain.content.Motivation
import com.dadsvictory.domain.content.SlipReasons
import com.dadsvictory.domain.content.Triggers
import com.dadsvictory.ui.VictoryUiState
import com.dadsvictory.ui.VictoryViewModel
import com.dadsvictory.ui.components.BigButton
import com.dadsvictory.ui.components.InfoNote
import com.dadsvictory.ui.components.SecondaryButton
import com.dadsvictory.ui.components.SectionHeader
import com.dadsvictory.ui.components.SelectableRow
import com.dadsvictory.ui.components.StatTile
import com.dadsvictory.ui.components.VictoryCard
import com.dadsvictory.ui.nav.Routes
import com.dadsvictory.ui.theme.ScreenPadding

private enum class Stage { WHAT, WHY, LEARN, DONE }

/**
 * The slip screen.
 *
 * Every word on it was chosen against a list of things it must never say: "failed",
 * "ruined", "back to zero", "wasted". What it does instead is thank him for being
 * honest, show him what he actually built, ask what he learned, and put a single
 * button in front of him: start again now.
 */
@Composable
fun RelapseScreen(
    viewModel: VictoryViewModel,
    state: VictoryUiState,
    navController: NavHostController,
) {
    var stage by rememberSaveable { mutableStateOf(Stage.WHAT) }
    var nicotine by rememberSaveable { mutableStateOf(state.profile.quitNicotine) }
    var alcohol by rememberSaveable { mutableStateOf(state.profile.quitAlcohol) }
    var reasonId by rememberSaveable { mutableStateOf<String?>(null) }
    var triggerId by rememberSaveable { mutableStateOf<String?>(null) }
    var reflection by rememberSaveable { mutableStateOf("") }
    var nextChange by rememberSaveable { mutableStateOf("") }

    // Captured on first composition, before the slip is written, so the screen can
    // show him what he had actually built rather than the post-slip numbers.
    val streakBefore = rememberSaveable { state.headlineStreakDays }
    val bestBefore = rememberSaveable { state.bestStreakDays }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(ScreenPadding),
    ) {
        when (stage) {
            Stage.WHAT -> {
                Text(
                    "Thank you for being honest.",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "That took more than most people manage. One slip does not erase the progress " +
                        "you've made — nothing you have built gets deleted here.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(24.dp))
                SectionHeader("What happened?")

                if (state.profile.quitNicotine) {
                    SelectableRow(
                        label = "Nicotine / vaping",
                        emoji = "🚭",
                        selected = nicotine,
                        onToggle = { nicotine = !nicotine },
                    )
                    Spacer(Modifier.height(10.dp))
                }
                if (state.profile.quitAlcohol) {
                    SelectableRow(
                        label = "Alcohol",
                        emoji = "🍺",
                        selected = alcohol,
                        onToggle = { alcohol = !alcohol },
                    )
                }

                Spacer(Modifier.height(28.dp))
                BigButton(
                    text = "Continue",
                    enabled = nicotine || alcohol,
                    onClick = { stage = Stage.WHY },
                )
                Spacer(Modifier.height(8.dp))
                SecondaryButton(text = "Actually, go back", onClick = { navController.popBackStack() })
            }

            Stage.WHY -> {
                Text("What was going on?", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(10.dp))
                Text(
                    "There is no wrong answer, and this is not a confession. It is how the app " +
                        "learns which situations are hardest for you.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(20.dp))
                for ((id, label) in SlipReasons.ALL) {
                    SelectableRow(
                        label = label,
                        selected = reasonId == id,
                        onToggle = { reasonId = if (reasonId == id) null else id },
                    )
                    Spacer(Modifier.height(8.dp))
                }

                Spacer(Modifier.height(16.dp))
                SectionHeader("Was one of your triggers involved?")
                for (trigger in Triggers.ALL) {
                    SelectableRow(
                        label = trigger.label,
                        emoji = trigger.emoji,
                        selected = triggerId == trigger.id,
                        onToggle = { triggerId = if (triggerId == trigger.id) null else trigger.id },
                    )
                    Spacer(Modifier.height(8.dp))
                }

                Spacer(Modifier.height(24.dp))
                BigButton(text = "Continue", onClick = { stage = Stage.LEARN })
                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = { stage = Stage.LEARN },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Skip this") }
            }

            Stage.LEARN -> {
                Text("You still learned something.", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(20.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile(
                        emoji = "🔥",
                        label = "You had built",
                        value = "$streakBefore days",
                        modifier = Modifier.weight(1f),
                    )
                    StatTile(
                        emoji = "🏔",
                        label = "Your best",
                        value = "$bestBefore days",
                        modifier = Modifier.weight(1f),
                        caption = "Still yours",
                    )
                }

                Spacer(Modifier.height(16.dp))
                if (triggerId != null) {
                    val trigger = Triggers.byId(triggerId)
                    if (trigger != null) {
                        VictoryCard(containerColor = MaterialTheme.colorScheme.secondaryContainer) {
                            Column {
                                Text(
                                    "${trigger.emoji} ${trigger.label}",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    trigger.strategy,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                    }
                }

                SectionHeader("What happened, in your words?")
                OutlinedTextField(
                    value = reflection,
                    onValueChange = { reflection = it },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 90.dp),
                    placeholder = { Text("Optional.") },
                )

                Spacer(Modifier.height(20.dp))
                SectionHeader("What can we change next time?")
                OutlinedTextField(
                    value = nextChange,
                    onValueChange = { nextChange = it },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 90.dp),
                    placeholder = { Text("One small, specific thing beats a big promise.") },
                )

                Spacer(Modifier.height(28.dp))
                BigButton(
                    text = "I'M STARTING AGAIN NOW",
                    onClick = {
                        val substances = buildSet {
                            if (nicotine) add(Substance.NICOTINE)
                            if (alcohol) add(Substance.ALCOHOL)
                        }
                        viewModel.recordSlip(
                            substances = substances,
                            triggerId = triggerId ?: reasonId,
                            reflection = reflection.trim().ifBlank { null },
                            nextChange = nextChange.trim().ifBlank { null },
                        )
                        stage = Stage.DONE
                    },
                )
                Spacer(Modifier.height(24.dp))
            }

            Stage.DONE -> {
                Spacer(Modifier.height(32.dp))
                Text("🌅", style = MaterialTheme.typography.displayLarge)
                Spacer(Modifier.height(16.dp))
                Text(
                    "You're still on the journey.",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    Motivation.pick(Motivation.Category.RELAPSE, state.rotationIndex),
                    style = MaterialTheme.typography.bodyLarge,
                )

                Spacer(Modifier.height(24.dp))
                VictoryCard {
                    Column {
                        Text("What is still true", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(10.dp))
                        Text("• Your best streak of $bestBefore days is still your best streak.", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(6.dp))
                        Text("• Every badge you earned is still earned.", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(6.dp))
                        Text("• Every craving you defeated still counts.", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(6.dp))
                        Text("• Your clock has restarted from right now, and that is all.", style = MaterialTheme.typography.bodyMedium)
                    }
                }

                Spacer(Modifier.height(24.dp))
                Text(
                    "What is your next decision?",
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(16.dp))
                BigButton(
                    text = "Back to the dashboard",
                    onClick = {
                        navController.popBackStack()
                    },
                )
                Spacer(Modifier.height(10.dp))
                SecondaryButton(
                    text = "Show me who I can talk to",
                    onClick = { navController.navigate(Routes.HELP) },
                )

                Spacer(Modifier.height(20.dp))
                InfoNote(
                    "You have just earned the 'Got back up after a slip' badge. It is the one most " +
                        "people never give themselves.",
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
