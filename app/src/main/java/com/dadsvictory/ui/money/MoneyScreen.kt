package com.dadsvictory.ui.money

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.dadsvictory.domain.Money
import com.dadsvictory.domain.Substance
import com.dadsvictory.domain.content.Motivation
import com.dadsvictory.ui.VictoryUiState
import com.dadsvictory.ui.VictoryViewModel
import com.dadsvictory.ui.components.InfoNote
import com.dadsvictory.ui.components.LabelledProgress
import com.dadsvictory.ui.components.SecondaryButton
import com.dadsvictory.ui.components.SectionHeader
import com.dadsvictory.ui.components.StatTile
import com.dadsvictory.ui.components.VictoryCard
import com.dadsvictory.ui.theme.ScreenPadding

@Composable
fun MoneyScreen(
    viewModel: VictoryViewModel,
    state: VictoryUiState,
    navController: NavHostController,
) {
    val saved = state.moneySaved
    val currency = state.currency
    val projection = remember(state.profile) { Money.projection(state.profile) }

    var goalName by remember { mutableStateOf(state.profile.savingsGoalName) }
    var goalAmount by remember {
        mutableStateOf(
            if (state.profile.savingsGoalMinor == 0L) "" else Money.toEditableText(state.profile.savingsGoalMinor),
        )
    }

    val lastCelebration = Money.lastCelebration(saved.totalMinor)
    val nextCelebration = Money.nextCelebration(saved.totalMinor)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = ScreenPadding, end = ScreenPadding, top = 12.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Money saved", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        }

        item {
            VictoryCard(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        "Since quitting you've saved approximately",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        Money.format(saved.totalMinor, currency),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        if (lastCelebration != null) {
            item {
                VictoryCard(containerColor = MaterialTheme.colorScheme.primaryContainer) {
                    Column {
                        Text(
                            "🎉 You've saved ${currency.symbol}$lastCelebration!",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            Motivation.pick(Motivation.Category.MONEY, state.rotationIndex),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }
        }

        if (state.profile.quitNicotine && state.profile.quitAlcohol) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile(
                        emoji = "🚭",
                        label = Substance.NICOTINE.displayName,
                        value = Money.format(saved.nicotineMinor, currency),
                        modifier = Modifier.weight(1f),
                    )
                    StatTile(
                        emoji = "🍺",
                        label = Substance.ALCOHOL.displayName,
                        value = Money.format(saved.alcoholMinor, currency),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        item { SectionHeader("What it was costing you") }

        item {
            VictoryCard {
                Column {
                    CostRow("A day", Money.format(projection.dailyMinor, currency))
                    Spacer(Modifier.height(10.dp))
                    CostRow("A week", Money.format(projection.weeklyMinor, currency))
                    Spacer(Modifier.height(10.dp))
                    CostRow("A month", Money.format(projection.monthlyMinor, currency))
                    Spacer(Modifier.height(10.dp))
                    CostRow("A year", Money.format(projection.yearlyMinor, currency))
                }
            }
        }

        item { SectionHeader("Your savings goal") }

        item {
            VictoryCard {
                Column {
                    if (state.profile.savingsGoalMinor > 0) {
                        LabelledProgress(
                            fraction = Money.goalProgress(saved.totalMinor, state.profile.savingsGoalMinor),
                            leadingLabel = Money.format(saved.totalMinor, currency),
                            trailingLabel = "of " + Money.format(state.profile.savingsGoalMinor, currency),
                        )
                        Spacer(Modifier.height(16.dp))
                    }

                    OutlinedTextField(
                        value = goalName,
                        onValueChange = { goalName = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("What are you saving for?") },
                        placeholder = { Text("Holiday, new bike, family day out…") },
                        singleLine = true,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = goalAmount,
                        onValueChange = { goalAmount = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("How much?") },
                        prefix = { Text(currency.symbol) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                    Spacer(Modifier.height(14.dp))
                    SecondaryButton(
                        text = "Save goal",
                        onClick = {
                            viewModel.setSavingsGoal(
                                goalName.trim(),
                                Money.parseToMinor(goalAmount) ?: 0L,
                            )
                        },
                    )
                }
            }
        }

        if (nextCelebration != null) {
            item {
                Text(
                    "Next milestone: ${currency.symbol}$nextCelebration",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            Spacer(Modifier.height(8.dp))
            InfoNote(
                "This is an estimate built from the weekly figures you entered during setup, " +
                    "counted proportionally to the time you have been free. Each slip you record " +
                    "takes off roughly a day's spend, because on that day the money did go out.",
            )
            InfoNote("You can change what you were spending in Settings at any time.")
            Spacer(Modifier.height(8.dp))
            SecondaryButton(text = "Back", onClick = { navController.popBackStack() })
        }
    }
}

@Composable
private fun CostRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
    }
}
