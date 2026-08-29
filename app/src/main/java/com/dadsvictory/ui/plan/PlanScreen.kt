package com.dadsvictory.ui.plan

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.dadsvictory.data.db.PlanTaskEntity
import com.dadsvictory.domain.content.DailyPlan
import com.dadsvictory.ui.VictoryUiState
import com.dadsvictory.ui.VictoryViewModel
import com.dadsvictory.ui.components.LabelledProgress
import com.dadsvictory.ui.components.SecondaryButton
import com.dadsvictory.ui.components.SectionHeader
import com.dadsvictory.ui.components.SelectableRow
import com.dadsvictory.ui.components.VictoryCard
import com.dadsvictory.ui.theme.ScreenPadding

/**
 * "Today's Victory Plan".
 *
 * Completions are stored per day, so the boxes clear themselves at midnight without
 * losing the record of what he did yesterday.
 */
@Composable
fun PlanScreen(
    viewModel: VictoryViewModel,
    state: VictoryUiState,
    navController: NavHostController,
) {
    val tasks by viewModel.planTasks.collectAsState(initial = emptyList())
    val done by viewModel.planCompletions(state.todayEpochDay).collectAsState(initial = emptySet())
    var showAdd by remember { mutableStateOf(false) }
    var addSlot by remember { mutableStateOf(DailyPlan.Slot.MORNING) }
    var newTitle by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { viewModel.seedPlanIfNeeded() }

    val active = tasks.filter { it.enabled }
    val completedCount = active.count { it.taskId in done }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = ScreenPadding, end = ScreenPadding, top = 12.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                "Today's Victory Plan",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }

        item {
            VictoryCard(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
                LabelledProgress(
                    fraction = if (active.isEmpty()) 0f else completedCount.toFloat() / active.size,
                    leadingLabel = "$completedCount of ${active.size} done",
                    trailingLabel = if (completedCount == active.size && active.isNotEmpty()) {
                        "All done 🎉"
                    } else {
                        "Keep going"
                    },
                )
            }
        }

        for (slot in DailyPlan.Slot.entries) {
            val slotTasks = active.filter { it.slotId == slot.id }.sortedBy { it.sortOrder }
            if (slotTasks.isEmpty()) continue

            item { SectionHeader("${slot.emoji} ${slot.label}") }
            item {
                VictoryCard {
                    Column {
                        for (task in slotTasks) {
                            PlanRow(
                                task = task,
                                done = task.taskId in done,
                                onToggle = {
                                    viewModel.setPlanTaskDone(
                                        state.todayEpochDay,
                                        task.taskId,
                                        task.taskId !in done,
                                    )
                                },
                                onDelete = { viewModel.deletePlanTask(task.taskId) },
                            )
                        }
                    }
                }
            }
        }

        item {
            Spacer(Modifier.height(8.dp))
            SecondaryButton(text = "Add my own task", onClick = { showAdd = true })
            Spacer(Modifier.height(8.dp))
            SecondaryButton(text = "Back", onClick = { navController.popBackStack() })
        }
    }

    if (showAdd) {
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("Add a task") },
            text = {
                Column {
                    OutlinedTextField(
                        value = newTitle,
                        onValueChange = { newTitle = it },
                        label = { Text("What do you want to do?") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(16.dp))
                    Text("When?", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    for (slot in DailyPlan.Slot.entries) {
                        SelectableRow(
                            label = slot.label,
                            emoji = slot.emoji,
                            selected = addSlot == slot,
                            onToggle = { addSlot = slot },
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = newTitle.isNotBlank(),
                    onClick = {
                        viewModel.addPlanTask(addSlot.id, newTitle.trim())
                        newTitle = ""
                        showAdd = false
                    },
                ) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("Cancel") } },
        )
    }
}

/** Tick box plus a struck-through label, so "done" reads two ways. */
@Composable
private fun PlanRow(
    task: PlanTaskEntity,
    done: Boolean,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clickable(onClick = onToggle)
            .semantics { contentDescription = if (done) "${task.title}, done" else "${task.title}, not done" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (done) Icons.Filled.CheckBox else Icons.Filled.CheckBoxOutlineBlank,
            contentDescription = null,
            tint = if (done) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outline,
        )
        Spacer(Modifier.padding(horizontal = 6.dp))
        Text(
            text = task.title,
            style = MaterialTheme.typography.bodyLarge,
            textDecoration = if (done) TextDecoration.LineThrough else TextDecoration.None,
            color = if (done) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier.weight(1f),
        )
        if (task.taskId.startsWith("custom_")) {
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Remove ${task.title}",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
