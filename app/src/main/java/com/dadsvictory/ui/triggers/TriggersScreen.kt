package com.dadsvictory.ui.triggers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.dadsvictory.domain.content.Trigger
import com.dadsvictory.domain.content.Triggers
import com.dadsvictory.ui.VictoryUiState
import com.dadsvictory.ui.VictoryViewModel
import com.dadsvictory.ui.components.EmptyState
import com.dadsvictory.ui.components.InfoNote
import com.dadsvictory.ui.components.SecondaryButton
import com.dadsvictory.ui.components.SectionHeader
import com.dadsvictory.ui.components.SelectableRow
import com.dadsvictory.ui.components.VictoryCard
import com.dadsvictory.ui.theme.ScreenPadding

/**
 * Trigger tracker.
 *
 * Picking a trigger reveals a specific plan for it, rather than a general
 * encouragement — the plan is the point, not the label.
 */
@Composable
fun TriggersScreen(
    viewModel: VictoryViewModel,
    state: VictoryUiState,
    navController: NavHostController,
) {
    val selected = state.selectedTriggerIds
    val strategies = Triggers.strategiesFor(selected)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = ScreenPadding, end = ScreenPadding, top = 12.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("My triggers", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Text(
                "Pick the situations where it is hardest for you. For each one you choose, you get " +
                    "a plan you can follow without having to think.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        item { SectionHeader("Which of these set you off?") }

        items(Triggers.ALL, key = { it.id }) { trigger ->
            SelectableRow(
                label = trigger.label,
                emoji = trigger.emoji,
                selected = trigger.id in selected,
                onToggle = { viewModel.setTriggerSelected(trigger.id, trigger.id !in selected) },
            )
        }

        item { SectionHeader("Your plan") }

        if (strategies.isEmpty()) {
            item {
                EmptyState(
                    emoji = "🎯",
                    title = "Nothing picked yet",
                    body = "Choose one or two above — the ones you already know about — and your " +
                        "personal strategies appear here.",
                )
            }
        } else {
            items(strategies, key = { "strategy_" + it.id }) { trigger ->
                StrategyCard(trigger)
            }
        }

        item {
            Spacer(Modifier.height(8.dp))
            InfoNote(
                "When you record a slip, the app asks which trigger was involved, so over time this " +
                    "list becomes about your actual week rather than a generic one.",
            )
            Spacer(Modifier.height(8.dp))
            SecondaryButton(text = "Back", onClick = { navController.popBackStack() })
        }
    }
}

@Composable
private fun StrategyCard(trigger: Trigger) {
    VictoryCard(containerColor = MaterialTheme.colorScheme.secondaryContainer) {
        Column {
            Text(
                "${trigger.emoji} ${trigger.label}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                trigger.strategy,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}
