package com.dadsvictory.ui.achievements

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.dadsvictory.domain.Achievements
import com.dadsvictory.ui.VictoryUiState
import com.dadsvictory.ui.VictoryViewModel
import com.dadsvictory.ui.components.InfoNote
import com.dadsvictory.ui.components.LabelledProgress
import com.dadsvictory.ui.components.SecondaryButton
import com.dadsvictory.ui.components.SectionHeader
import com.dadsvictory.ui.components.VictoryCard
import com.dadsvictory.ui.theme.ScreenPadding

@Composable
fun AchievementsScreen(
    viewModel: VictoryViewModel,
    state: VictoryUiState,
    navController: NavHostController,
) {
    val all = state.achievements
    val automatic = all.filter { it.achievement.kind != Achievements.Kind.STORY }
    val story = all.filter { it.achievement.kind == Achievements.Kind.STORY }
    val earned = all.count { it.unlocked }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = ScreenPadding, end = ScreenPadding, top = 12.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Achievements", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Text(
                "$earned of ${all.size} earned. Once a badge is yours, it stays yours — a slip " +
                    "never takes one back.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        item { SectionHeader("Earned by the numbers") }

        items(automatic, key = { it.achievement.id }) { progress ->
            AchievementCard(progress)
        }

        item { SectionHeader("Only you know these ones") }

        item {
            InfoNote(
                "The app cannot see a wedding you got through or a holiday you came back from " +
                    "clear-headed, so it does not pretend to. Tick these yourself when you have " +
                    "earned them.",
            )
        }

        items(story, key = { it.achievement.id }) { progress ->
            StoryAchievementCard(
                progress = progress,
                onToggle = { viewModel.setStoryAchievement(progress.achievement.id, it) },
            )
        }

        item {
            Spacer(Modifier.height(8.dp))
            SecondaryButton(text = "Back", onClick = { navController.popBackStack() })
        }
    }
}

@Composable
private fun AchievementCard(progress: Achievements.Progress) {
    val achievement = progress.achievement
    VictoryCard(
        containerColor = if (progress.unlocked) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(achievement.emoji, modifier = Modifier.clearAndSetSemantics {})
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        achievement.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (progress.unlocked) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                    Text(
                        if (progress.unlocked) "Earned" else "Not yet",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // Trophy or padlock — the state never depends on the colour.
                Icon(
                    imageVector = if (progress.unlocked) Icons.Filled.EmojiEvents else Icons.Filled.Lock,
                    contentDescription = if (progress.unlocked) "Earned" else "Not earned yet",
                    tint = if (progress.unlocked) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.outline
                    },
                )
            }

            Spacer(Modifier.height(10.dp))
            Text(
                achievement.description,
                style = MaterialTheme.typography.bodyMedium,
                color = if (progress.unlocked) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )

            if (!progress.unlocked && achievement.threshold > 0) {
                Spacer(Modifier.height(14.dp))
                LabelledProgress(
                    fraction = progress.fraction,
                    leadingLabel = progress.currentValue.toString(),
                    trailingLabel = "of ${achievement.threshold}",
                )
            }
        }
    }
}

@Composable
private fun StoryAchievementCard(
    progress: Achievements.Progress,
    onToggle: (Boolean) -> Unit,
) {
    val achievement = progress.achievement
    VictoryCard(
        containerColor = if (progress.unlocked) {
            MaterialTheme.colorScheme.tertiaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(achievement.emoji, modifier = Modifier.clearAndSetSemantics {})
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(achievement.title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    achievement.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(8.dp))
            Switch(
                checked = progress.unlocked,
                onCheckedChange = onToggle,
            )
        }
    }
}
