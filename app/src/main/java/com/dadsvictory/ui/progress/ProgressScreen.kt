package com.dadsvictory.ui.progress

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.dadsvictory.domain.CheckIn
import com.dadsvictory.domain.Money
import com.dadsvictory.domain.Mood
import com.dadsvictory.domain.content.HealthMilestones
import com.dadsvictory.domain.content.Milestone
import com.dadsvictory.ui.VictoryUiState
import com.dadsvictory.ui.VictoryViewModel
import com.dadsvictory.ui.components.InfoNote
import com.dadsvictory.ui.components.MiniBarChart
import com.dadsvictory.ui.components.NavigationRow
import com.dadsvictory.ui.components.SectionHeader
import com.dadsvictory.ui.components.SourceLine
import com.dadsvictory.ui.components.StatTile
import com.dadsvictory.ui.components.VictoryCard
import com.dadsvictory.ui.nav.Routes
import com.dadsvictory.ui.openUrl
import com.dadsvictory.ui.theme.ScreenPadding
import java.time.LocalDate

@Composable
fun ProgressScreen(
    viewModel: VictoryViewModel,
    state: VictoryUiState,
    navController: NavHostController,
) {
    val context = LocalContext.current
    val timeline = HealthMilestones.timelineFor(state.profile.quitNicotine, state.profile.quitAlcohol)
    val recent = state.checkIns.sortedBy { it.epochDay }.takeLast(7)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = ScreenPadding, end = ScreenPadding, top = 12.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Your progress", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        }

        item { SectionHeader("The numbers") }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(
                    emoji = "🔥",
                    label = "Current streak",
                    value = "${state.headlineStreakDays} days",
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    emoji = "🏔",
                    label = "Longest streak",
                    value = "${state.bestStreakDays} days",
                    modifier = Modifier.weight(1f),
                )
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(
                    emoji = "✅",
                    label = "Total free days",
                    value = state.totalFreeDays.toString(),
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    emoji = "💪",
                    label = "Cravings defeated",
                    value = state.cravingsDefeated.toString(),
                    modifier = Modifier.weight(1f),
                )
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(
                    emoji = "🗓",
                    label = "On the journey",
                    value = "${state.journeyDays} days",
                    modifier = Modifier.weight(1f),
                    caption = "Since you started",
                )
                StatTile(
                    emoji = "🔁",
                    label = "Slips recorded",
                    value = state.slipCount.toString(),
                    modifier = Modifier.weight(1f),
                    caption = "Tracked separately",
                )
            }
        }

        if (state.slipCount > 0) {
            item {
                VictoryCard(containerColor = MaterialTheme.colorScheme.secondaryContainer) {
                    Column {
                        Text(
                            "You're still on the journey.",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Slips are counted separately from your free days on purpose. They are " +
                                "information about what to change, not marks against you — and every " +
                                "day you did stay free is still on the board.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
            }
        }

        item { SectionHeader("Check-in history") }

        if (recent.isEmpty()) {
            item {
                VictoryCard(onClick = { navController.navigate(Routes.CHECK_IN) }) {
                    Column {
                        Text("No check-ins yet", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Do one a day and these charts start telling you which days are hard " +
                                "and why. Tap to do today's.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        } else {
            item { ChartCard("Mood", recent) { it.moodScore.toFloat() } }
            item { ChartCard("Craving level", recent) { it.cravingLevel.toFloat() } }
            item { ChartCard("Stress", recent) { it.stressLevel.toFloat() } }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (state.profile.quitNicotine) {
                        StatTile(
                            emoji = "🚭",
                            label = "Nicotine-free days",
                            value = state.nicotineFreeCheckInDays.toString(),
                            modifier = Modifier.weight(1f),
                            caption = "From your check-ins",
                        )
                    }
                    if (state.profile.quitAlcohol) {
                        StatTile(
                            emoji = "🍺",
                            label = "Alcohol-free days",
                            value = state.alcoholFreeCheckInDays.toString(),
                            modifier = Modifier.weight(1f),
                            caption = "From your check-ins",
                        )
                    }
                }
            }
        }

        item { SectionHeader("Money") }

        item {
            VictoryCard(onClick = { navController.navigate(Routes.MONEY) }) {
                Column {
                    Text(
                        Money.format(state.moneySaved.totalMinor, state.currency),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "saved so far — estimated from what you told us you were spending",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item { SectionHeader("Health milestones") }

        item { InfoNote(HealthMilestones.CAUTION) }

        items(timeline, key = { it.id }) { milestone ->
            MilestoneRow(
                milestone = milestone,
                reached = HealthMilestones.reached(milestone, state.headlineStreakDays),
                onOpenSource = { url -> openUrl(context, url) },
            )
        }

        item { SectionHeader("More") }

        item {
            VictoryCard {
                Column {
                    NavigationRow("🏆", "Achievements", "${state.achievements.count { it.unlocked }} earned") {
                        navController.navigate(Routes.ACHIEVEMENTS)
                    }
                    NavigationRow("📊", "Why quit?", "Sourced health information") {
                        navController.navigate(Routes.FACTS)
                    }
                    NavigationRow("🎯", "My triggers", "And what to do about them") {
                        navController.navigate(Routes.TRIGGERS)
                    }
                    NavigationRow("📝", "Do today's check-in", null) {
                        navController.navigate(Routes.CHECK_IN)
                    }
                }
            }
        }
    }
}

@Composable
private fun ChartCard(
    title: String,
    checkIns: List<CheckIn>,
    value: (CheckIn) -> Float,
) {
    val max = if (title == "Mood") 5f else 10f
    val rows = checkIns.map { checkIn ->
        val label = LocalDate.ofEpochDay(checkIn.epochDay)
            .dayOfWeek
            .getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault())
        label to value(checkIn)
    }

    VictoryCard {
        Column {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    "out of ${max.toInt()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(12.dp))
            MiniBarChart(values = rows, maxValue = max)

            if (title == "Mood") {
                Spacer(Modifier.height(8.dp))
                Text(
                    "1 = ${Mood.VERY_DIFFICULT.label}, 5 = ${Mood.GREAT.label}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun MilestoneRow(
    milestone: Milestone,
    reached: Boolean,
    onOpenSource: (String) -> Unit,
) {
    VictoryCard {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // A filled tick versus an empty circle, plus the word — never colour alone.
                Icon(
                    imageVector = if (reached) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                    contentDescription = if (reached) "Reached" else "Not reached yet",
                    tint = if (reached) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        MaterialTheme.colorScheme.outline
                    },
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.size(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(milestone.title, style = MaterialTheme.typography.titleSmall)
                    Text(
                        if (reached) "Reached" else "Day ${milestone.afterDays}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(milestone.body, style = MaterialTheme.typography.bodyMedium)
            val source = milestone.source
            if (source != null) {
                SourceLine(
                    organisation = source.organisation,
                    detail = source.title,
                    onOpen = { onOpenSource(source.url) },
                )
            }
        }
    }
}
