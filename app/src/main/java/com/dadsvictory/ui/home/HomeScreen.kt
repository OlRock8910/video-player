package com.dadsvictory.ui.home

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.dadsvictory.domain.Money
import com.dadsvictory.domain.NotificationSchedule
import com.dadsvictory.domain.Streaks
import com.dadsvictory.domain.content.Motivation
import com.dadsvictory.domain.content.Scripture
import com.dadsvictory.notifications.Notifications
import com.dadsvictory.ui.VictoryUiState
import com.dadsvictory.ui.VictoryViewModel
import com.dadsvictory.ui.components.BigButton
import com.dadsvictory.ui.components.InfoNote
import com.dadsvictory.ui.components.LabelledProgress
import com.dadsvictory.ui.components.NavigationRow
import com.dadsvictory.ui.components.SectionHeader
import com.dadsvictory.ui.components.StatTile
import com.dadsvictory.ui.components.VictoryCard
import com.dadsvictory.ui.nav.Routes
import com.dadsvictory.ui.theme.ScreenPadding
import com.dadsvictory.ui.theme.StreakNumberStyle

@Composable
fun HomeScreen(
    viewModel: VictoryViewModel,
    state: VictoryUiState,
    navController: NavHostController,
) {
    val context = LocalContext.current

    // Ask once, on the first visit to the dashboard, rather than interrupting
    // the setup questions with a system dialog.
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { viewModel.rescheduleNotifications() }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !Notifications.hasPermission(context)) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val profile = state.profile
    val greeting = NotificationSchedule.greeting(state.nowMillis, state.zone)
    val verse = Scripture.daily(state.rotationIndex)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = ScreenPadding, end = ScreenPadding, top = 12.dp, bottom = 32.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(greeting, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        }

        item { StreakHeadline(state) }

        item {
            BigButton(
                text = "I'M HAVING A CRAVING",
                onClick = { navController.navigate(Routes.CRAVING) },
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
            )
        }

        item {
            VictoryCard {
                Column {
                    Text(
                        Motivation.pick(Motivation.Category.MORNING, state.rotationIndex),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }

        item { SectionHeader("Where you are") }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (profile.quitNicotine) {
                    StatTile(
                        emoji = "🚭",
                        label = "Nicotine",
                        value = Streaks.describe(state.nicotineStats) + " free",
                        modifier = Modifier.weight(1f),
                        caption = "Best: ${state.nicotineStats.bestStreakDays} days",
                    )
                }
                if (profile.quitAlcohol) {
                    StatTile(
                        emoji = "🍺",
                        label = "Alcohol",
                        value = Streaks.describe(state.alcoholStats) + " free",
                        modifier = Modifier.weight(1f),
                        caption = "Best: ${state.alcoholStats.bestStreakDays} days",
                    )
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(
                    emoji = "💰",
                    label = "Money saved",
                    value = Money.format(state.moneySaved.totalMinor, state.currency),
                    modifier = Modifier.weight(1f),
                    caption = "Estimated",
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
                    emoji = "❤️",
                    label = "Days chosen for health",
                    value = state.totalFreeDays.toString(),
                    modifier = Modifier.weight(1f),
                )
                if (profile.quitNicotine && profile.vapeSessionsPerDay > 0) {
                    StatTile(
                        emoji = "🚫",
                        label = "Vapes avoided",
                        value = state.vapesAvoided.toString(),
                        modifier = Modifier.weight(1f),
                        caption = "Estimated",
                    )
                } else if (profile.quitAlcohol && profile.drinksPerWeek > 0) {
                    StatTile(
                        emoji = "🚫",
                        label = "Drinks avoided",
                        value = state.drinksAvoided.toString(),
                        modifier = Modifier.weight(1f),
                        caption = "Estimated",
                    )
                }
            }
        }

        // Savings goal, only once he has set one.
        if (profile.savingsGoalMinor > 0) {
            item {
                VictoryCard(onClick = { navController.navigate(Routes.MONEY) }) {
                    Column {
                        Text(
                            profile.savingsGoalName.ifBlank { "Savings goal" },
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(Modifier.height(12.dp))
                        LabelledProgress(
                            fraction = Money.goalProgress(state.moneySaved.totalMinor, profile.savingsGoalMinor),
                            leadingLabel = Money.format(state.moneySaved.totalMinor, state.currency),
                            trailingLabel = "of " + Money.format(profile.savingsGoalMinor, state.currency),
                        )
                    }
                }
            }
        }

        if (!state.hasCheckedInToday) {
            item {
                VictoryCard(onClick = { navController.navigate(Routes.CHECK_IN) }) {
                    Column {
                        Text("How are you doing today?", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Takes about thirty seconds. It builds the charts on your Progress tab.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        item {
            VictoryCard(onClick = { navController.navigate(Routes.FAITH) }) {
                Column {
                    Text("📖 Today's verse", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(10.dp))
                    Text("\"${verse.text(state.settings.bibleVersion)}\"", style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "— ${verse.reference} (${state.settings.bibleVersion.abbreviation})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item { SectionHeader("More") }

        item {
            VictoryCard {
                Column {
                    NavigationRow("✅", "Today's Victory Plan", "Your checklist for the day") {
                        navController.navigate(Routes.PLAN)
                    }
                    NavigationRow("📓", "Journal", "Private, and lockable") {
                        navController.navigate(Routes.JOURNAL)
                    }
                    NavigationRow("🏆", "Achievements", "${state.achievements.count { it.unlocked }} earned so far") {
                        navController.navigate(Routes.ACHIEVEMENTS)
                    }
                    NavigationRow("❤️", "Family & reasons", "The things that come back to you in a craving") {
                        navController.navigate(Routes.FAMILY)
                    }
                    NavigationRow("🎯", "My triggers", "Know them, plan for them") {
                        navController.navigate(Routes.TRIGGERS)
                    }
                    NavigationRow("📊", "Why quit?", "Sourced health information") {
                        navController.navigate(Routes.FACTS)
                    }
                    NavigationRow("💰", "Money saved", "Goals and projections") {
                        navController.navigate(Routes.MONEY)
                    }
                }
            }
        }

        item {
            VictoryCard(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
                Column {
                    Text(
                        Motivation.CORE_MESSAGE,
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        item {
            Column {
                InfoNote("Money and 'avoided' figures are estimates from what you entered during setup.")
                InfoNote("Slipped? Tap here — nothing gets wiped, and nobody is going to tell you off.")
                Spacer(Modifier.height(4.dp))
                com.dadsvictory.ui.components.SecondaryButton(
                    text = "I had a slip",
                    onClick = { navController.navigate(Routes.RELAPSE) },
                )
            }
        }
    }
}

/**
 * The big number. It reads "17 DAYS FREE" once there is a day on the board, and
 * counts in hours before that, so day one never looks like nothing is happening.
 */
@Composable
private fun StreakHeadline(state: VictoryUiState) {
    val stats = state.headlineStats
    val bothChosen = state.profile.quitNicotine && state.profile.quitAlcohol

    val (number, unit) = when {
        stats.notStartedYet -> "—" to "NOT STARTED YET"
        stats.currentStreakDays >= 1 ->
            stats.currentStreakDays.toString() to
                if (stats.currentStreakDays == 1) "DAY FREE" else "DAYS FREE"
        stats.currentStreakHoursPart >= 1 ->
            stats.currentStreakHoursPart.toString() to
                if (stats.currentStreakHoursPart == 1) "HOUR FREE" else "HOURS FREE"
        else ->
            stats.currentStreakMinutesPart.toString() to
                if (stats.currentStreakMinutesPart == 1) "MINUTE FREE" else "MINUTES FREE"
    }

    val supporting = when {
        stats.notStartedYet -> "Your start date hasn't arrived yet. That's fine — it's set."
        state.slipCount > 0 -> "Keep going. Best so far: ${state.bestStreakDays} days."
        else -> "Keep going."
    }

    VictoryCard(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "$number $unit. $supporting" },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clearAndSetSemantics {},
            ) {
                Text(
                    number,
                    style = StreakNumberStyle,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    unit,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(supporting, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)

            if (bothChosen && state.nicotineStats.currentStreakDays != state.alcoholStats.currentStreakDays) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Showing the shorter of your two streaks.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }

            val next = state.nextMilestone
            if (next != null && !stats.notStartedYet) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "Next up: ${next.title} in ${next.afterDays - state.headlineStreakDays} days",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
