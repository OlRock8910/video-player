package com.dadsvictory.ui.journal

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavHostController
import com.dadsvictory.data.db.JournalEntity
import com.dadsvictory.domain.content.JournalPrompts
import com.dadsvictory.ui.VictoryUiState
import com.dadsvictory.ui.VictoryViewModel
import com.dadsvictory.ui.components.BigButton
import com.dadsvictory.ui.components.ConfirmDialog
import com.dadsvictory.ui.components.EmptyState
import com.dadsvictory.ui.components.InfoNote
import com.dadsvictory.ui.components.SecondaryButton
import com.dadsvictory.ui.components.SectionHeader
import com.dadsvictory.ui.components.VictoryCard
import com.dadsvictory.ui.theme.ScreenPadding
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun JournalScreen(
    viewModel: VictoryViewModel,
    state: VictoryUiState,
    navController: NavHostController,
) {
    val unlocked by viewModel.journalUnlocked.collectAsState()
    val locked = state.settings.journalLockEnabled && !unlocked

    // Re-lock on the way out. A lock that stays open for the rest of the app
    // session is not really a lock — leaving the screen should close it again.
    DisposableEffect(Unit) {
        onDispose { viewModel.lockJournal() }
    }

    if (locked) {
        JournalLockScreen(viewModel, state, onBack = { navController.popBackStack() })
    } else {
        JournalList(viewModel, state, navController)
    }
}

/**
 * The lock.
 *
 * The PIN is checked against a salted PBKDF2 hash — the PIN itself is never stored,
 * so there is no way to recover it and no way for anything reading the database to
 * find it. Fingerprint unlock sits on top of that as a convenience.
 */
@Composable
private fun JournalLockScreen(
    viewModel: VictoryViewModel,
    state: VictoryUiState,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var pin by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    val activity = context as? FragmentActivity
    val biometricAvailable = remember(activity) {
        activity != null &&
            BiometricManager.from(context).canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_WEAK,
            ) == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun promptBiometric() {
        val host = activity ?: return
        val prompt = BiometricPrompt(
            host,
            ContextCompat.getMainExecutor(context),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    viewModel.unlockJournal()
                }
            },
        )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock your journal")
                .setSubtitle("This stays on your phone.")
                .setNegativeButtonText("Use PIN instead")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
                .build(),
        )
    }

    // Offer the fingerprint straight away if he turned it on.
    LaunchedEffect(biometricAvailable, state.settings.biometricUnlock) {
        if (biometricAvailable && state.settings.biometricUnlock) promptBiometric()
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(ScreenPadding),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("🔒", style = MaterialTheme.typography.displayMedium)
        Spacer(Modifier.height(16.dp))
        Text("Your journal is locked", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            "Enter your PIN to open it.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = pin,
            onValueChange = {
                pin = it.filter { ch -> ch.isDigit() }.take(12)
                error = null
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("PIN") },
            singleLine = true,
            isError = error != null,
            supportingText = { if (error != null) Text(error!!) },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        )

        Spacer(Modifier.height(20.dp))
        BigButton(
            text = "Unlock",
            enabled = pin.isNotEmpty(),
            onClick = {
                if (viewModel.verifyJournalPin(pin)) {
                    viewModel.unlockJournal()
                } else {
                    error = "That PIN doesn't match. Try again."
                    pin = ""
                }
            },
        )

        if (biometricAvailable && state.settings.biometricUnlock) {
            Spacer(Modifier.height(10.dp))
            SecondaryButton(text = "Use fingerprint", onClick = { promptBiometric() })
        }

        Spacer(Modifier.height(10.dp))
        TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back") }

        Spacer(Modifier.height(20.dp))
        InfoNote(
            "There is no way to recover a forgotten PIN — that is what makes the lock worth " +
                "anything. If you have forgotten it, you can reset the lock in Settings, which " +
                "clears the journal.",
        )
    }
}

@Composable
private fun JournalList(
    viewModel: VictoryViewModel,
    state: VictoryUiState,
    navController: NavHostController,
) {
    val entries by viewModel.journal.collectAsState(initial = emptyList())
    var editingId by rememberSaveable { mutableLongStateOf(-1L) }
    var composing by rememberSaveable { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<JournalEntity?>(null) }

    val todaysPrompt = JournalPrompts.forDay(state.rotationIndex)

    if (composing || editingId >= 0) {
        val existing = entries.firstOrNull { it.id == editingId }
        JournalEditor(
            initialPrompt = existing?.prompt ?: todaysPrompt,
            initialBody = existing?.body.orEmpty(),
            onSave = { prompt, body ->
                if (existing != null) {
                    viewModel.updateJournalEntry(existing.id, prompt, body)
                } else {
                    viewModel.addJournalEntry(prompt, body)
                }
                composing = false
                editingId = -1L
            },
            onCancel = {
                composing = false
                editingId = -1L
            },
        )
        return
    }

    pendingDelete?.let { entry ->
        ConfirmDialog(
            title = "Delete this entry?",
            body = "This cannot be undone.",
            confirmText = "Delete",
            destructive = true,
            onConfirm = {
                viewModel.deleteJournalEntry(entry)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null },
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = ScreenPadding, end = ScreenPadding, top = 12.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Journal", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text(
                "Private, and stored only on this phone.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        item {
            VictoryCard(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Column {
                    Text("Today's prompt", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(8.dp))
                    Text(todaysPrompt, style = MaterialTheme.typography.titleMedium)
                }
            }
        }

        item {
            BigButton(text = "Write an entry", onClick = { composing = true })
        }

        if (entries.isEmpty()) {
            item {
                EmptyState(
                    emoji = "📓",
                    title = "Nothing written yet",
                    body = "A few lines at the end of the day is enough. It is often the thing that " +
                        "shows you the pattern.",
                )
            }
        } else {
            item { SectionHeader("${entries.size} ${if (entries.size == 1) "entry" else "entries"}") }
            items(entries, key = { it.id }) { entry ->
                JournalCard(
                    entry = entry,
                    onEdit = { editingId = entry.id },
                    onDelete = { pendingDelete = entry },
                )
            }
        }

        item {
            Spacer(Modifier.height(8.dp))
            SecondaryButton(text = "Back", onClick = { navController.popBackStack() })
        }
    }
}

@Composable
private fun JournalCard(entry: JournalEntity, onEdit: () -> Unit, onDelete: () -> Unit) {
    val formatter = remember {
        DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.getDefault())
    }
    val when_ = remember(entry.createdAtMillis) {
        Instant.ofEpochMilli(entry.createdAtMillis).atZone(ZoneId.systemDefault()).format(formatter)
    }

    VictoryCard(onClick = onEdit) {
        Column {
            Text(
                when_,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (entry.prompt.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(entry.prompt, style = MaterialTheme.typography.titleSmall)
            }
            Spacer(Modifier.height(8.dp))
            Text(entry.body, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onEdit) { Text("Edit") }
                TextButton(onClick = onDelete) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun JournalEditor(
    initialPrompt: String,
    initialBody: String,
    onSave: (String, String) -> Unit,
    onCancel: () -> Unit,
) {
    var prompt by rememberSaveable { mutableStateOf(initialPrompt) }
    var body by rememberSaveable { mutableStateOf(initialBody) }
    var showPromptPicker by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .padding(ScreenPadding),
    ) {
        Text("Write", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = prompt,
            onValueChange = { prompt = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Prompt (you can change it)") },
            singleLine = true,
        )
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = { showPromptPicker = true }) { Text("Choose a different prompt") }

        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = body,
            onValueChange = { body = it },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            label = { Text("Your entry") },
            placeholder = { Text("However much or little you want.") },
        )

        Spacer(Modifier.height(16.dp))
        BigButton(
            text = "Save",
            enabled = body.isNotBlank(),
            onClick = { onSave(prompt.trim(), body.trim()) },
        )
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
    }

    if (showPromptPicker) {
        AlertDialog(
            onDismissRequest = { showPromptPicker = false },
            title = { Text("Pick a prompt") },
            text = {
                Column {
                    for (option in JournalPrompts.ALL) {
                        TextButton(
                            onClick = {
                                prompt = option
                                showPromptPicker = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(option) }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPromptPicker = false }) { Text("Close") }
            },
        )
    }
}
