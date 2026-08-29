package com.dadsvictory.ui.family

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import kotlinx.coroutines.launch
import com.dadsvictory.data.local.PhotoStore
import com.dadsvictory.domain.Reason
import com.dadsvictory.ui.VictoryUiState
import com.dadsvictory.ui.VictoryViewModel
import com.dadsvictory.ui.components.ConfirmDialog
import com.dadsvictory.ui.components.EmptyState
import com.dadsvictory.ui.components.InfoNote
import com.dadsvictory.ui.components.SecondaryButton
import com.dadsvictory.ui.components.SectionHeader
import com.dadsvictory.ui.components.SelectableRow
import com.dadsvictory.ui.components.VictoryCard
import com.dadsvictory.ui.theme.ScreenPadding

/**
 * Family, reasons and the photo.
 *
 * The photo is copied into the app's private storage and shown only here and
 * during a craving. It is never uploaded — the app holds no INTERNET permission,
 * so there is no code path that could send it anywhere.
 */
@Composable
fun FamilyScreen(
    viewModel: VictoryViewModel,
    state: VictoryUiState,
    navController: NavHostController,
) {
    val context = LocalContext.current
    val messages by viewModel.familyMessages.collectAsState(initial = emptyList())
    var newMessage by remember { mutableStateOf("") }
    var photo by remember { mutableStateOf<Bitmap?>(null) }
    var confirmRemovePhoto by remember { mutableStateOf(false) }
    var customReason by remember { mutableStateOf(state.profile.customReason) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(state.settings.hasFamilyPhoto) {
        photo = if (state.settings.hasFamilyPhoto) PhotoStore.load(context) else null
    }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            // The copy and decode happen off the main thread inside PhotoStore.
            scope.launch {
                if (PhotoStore.save(context, uri)) {
                    viewModel.setHasFamilyPhoto(true)
                    photo = PhotoStore.load(context)
                }
            }
        }
    }

    if (confirmRemovePhoto) {
        ConfirmDialog(
            title = "Remove this photo?",
            body = "It will be deleted from the app. Your original photo is untouched.",
            confirmText = "Remove",
            destructive = true,
            onConfirm = {
                PhotoStore.delete(context)
                viewModel.setHasFamilyPhoto(false)
                photo = null
                confirmRemovePhoto = false
            },
            onDismiss = { confirmRemovePhoto = false },
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = ScreenPadding, end = ScreenPadding, top = 12.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                "Family & reasons",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "These come back to you during a craving, when they are hardest to remember and " +
                    "matter most.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        item { SectionHeader("Your reason") }

        item {
            val current = photo
            if (current != null) {
                VictoryCard {
                    Column {
                        Image(
                            bitmap = current.asImageBitmap(),
                            contentDescription = "Your photo",
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 180.dp, max = 320.dp)
                                .clip(RoundedCornerShape(14.dp)),
                            contentScale = ContentScale.Crop,
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            SecondaryButton(
                                text = "Change",
                                onClick = {
                                    picker.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                    )
                                },
                                modifier = Modifier.weight(1f),
                            )
                            SecondaryButton(
                                text = "Remove",
                                onClick = { confirmRemovePhoto = true },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            } else {
                VictoryCard {
                    Column {
                        Text("Add a photo", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "A picture of the people you are doing this for. It stays on this phone.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(14.dp))
                        SecondaryButton(
                            text = "Choose a photo",
                            onClick = {
                                picker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                )
                            },
                        )
                    }
                }
            }
        }

        item { SectionHeader("Why you're doing this") }

        items(Reason.entries.toList(), key = { it.id }) { reason ->
            SelectableRow(
                label = reason.label,
                emoji = "❤️",
                selected = reason.id in state.profile.reasonIds,
                onToggle = {
                    val ids = state.profile.reasonIds.toMutableSet()
                    if (!ids.add(reason.id)) ids.remove(reason.id)
                    viewModel.updateProfile(state.profile.copy(reasonIds = ids))
                },
            )
        }

        item {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = customReason,
                onValueChange = { customReason = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("My own reason") },
                minLines = 2,
            )
            Spacer(Modifier.height(8.dp))
            SecondaryButton(
                text = "Save my reason",
                onClick = { viewModel.updateProfile(state.profile.copy(customReason = customReason.trim())) },
            )
        }

        item { SectionHeader("Messages to yourself") }

        item {
            VictoryCard {
                Column {
                    OutlinedTextField(
                        value = newMessage,
                        onValueChange = { newMessage = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Write something for a hard day") },
                        placeholder = { Text("\"My family needs me healthy.\"") },
                        minLines = 2,
                    )
                    Spacer(Modifier.height(10.dp))
                    SecondaryButton(
                        text = "Add message",
                        enabled = newMessage.isNotBlank(),
                        onClick = {
                            viewModel.addFamilyMessage(newMessage.trim())
                            newMessage = ""
                        },
                    )
                }
            }
        }

        if (messages.isEmpty()) {
            item {
                EmptyState(
                    emoji = "❤️",
                    title = "Nothing written yet",
                    body = "One sentence in your own words carries more weight on a bad day than " +
                        "anything this app can write for you.",
                )
            }
        } else {
            items(messages, key = { it.id }) { message ->
                VictoryCard(containerColor = MaterialTheme.colorScheme.primaryContainer) {
                    Column {
                        Text(
                            message.text,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = { viewModel.deleteFamilyMessage(message) }) {
                            Text("Delete", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }

        item {
            Spacer(Modifier.height(8.dp))
            InfoNote(
                "Your photo is stored inside this app's private folder, where no other app can " +
                    "read it. It is not backed up to any cloud and it is never sent anywhere.",
            )
            Spacer(Modifier.height(8.dp))
            SecondaryButton(text = "Back", onClick = { navController.popBackStack() })
        }
    }
}
