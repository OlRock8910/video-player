package com.dadsvictory.ui.settings

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.dadsvictory.data.prefs.ThemeMode
import com.dadsvictory.domain.Country
import com.dadsvictory.domain.Currency
import com.dadsvictory.domain.Money
import com.dadsvictory.domain.NotificationSchedule
import com.dadsvictory.domain.NotificationSlot
import com.dadsvictory.domain.backup.BackupCodec
import com.dadsvictory.domain.content.Motivation
import com.dadsvictory.domain.content.Sources
import com.dadsvictory.notifications.Notifications
import com.dadsvictory.ui.VictoryUiState
import com.dadsvictory.ui.VictoryViewModel
import com.dadsvictory.ui.components.BigButton
import com.dadsvictory.ui.components.ConfirmDialog
import com.dadsvictory.ui.components.InfoNote
import com.dadsvictory.ui.components.NavigationRow
import com.dadsvictory.ui.components.SafetyBanner
import com.dadsvictory.ui.components.SecondaryButton
import com.dadsvictory.ui.components.SectionHeader
import com.dadsvictory.ui.components.SelectableRow
import com.dadsvictory.ui.components.VictoryCard
import com.dadsvictory.ui.nav.Routes
import com.dadsvictory.ui.theme.ScreenPadding
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun SettingsScreen(
    viewModel: VictoryViewModel,
    state: VictoryUiState,
    navController: NavHostController,
) {
    val context = LocalContext.current
    val settings = state.settings
    val use24Hour = remember { DateFormat.is24HourFormat(context) }

    var editingSlot by remember { mutableStateOf<NotificationSlot?>(null) }
    var showDeleteAll by remember { mutableStateOf(false) }
    var showPinDialog by remember { mutableStateOf(false) }
    var showBackup by remember { mutableStateOf<BackupMode?>(null) }
    var editingSpend by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = ScreenPadding, end = ScreenPadding, top = 12.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        }

        // The crisis route, discreet but never more than one tap away.
        item {
            VictoryCard(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                onClick = { navController.navigate(Routes.HELP) },
            ) {
                Column {
                    Text(
                        "Need help?",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Someone to talk to, medical advice, or emergency help for " +
                            "${state.profile.country.label}.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
        }

        item { SectionHeader("Daily encouragement") }

        if (!Notifications.areEnabled(context)) {
            item {
                SafetyBanner(
                    title = "Notifications are switched off",
                    body = "Android is currently blocking notifications for this app, so the three " +
                        "daily messages will not arrive. You can turn them back on in your phone's " +
                        "Settings → Apps → Dad's Victory → Notifications.",
                )
            }
        }

        items(NotificationSlot.entries.toList(), key = { it.id }) { slot ->
            val setting = settings.notifications[slot] ?: return@items
            VictoryCard {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(slot.emoji)
                        Spacer(Modifier.padding(horizontal = 6.dp))
                        Column(Modifier.weight(1f)) {
                            Text(slot.label, style = MaterialTheme.typography.titleMedium)
                            Text(
                                NotificationSchedule.formatTime(setting.minuteOfDay, use24Hour),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = setting.enabled,
                            onCheckedChange = {
                                viewModel.setNotificationSlot(slot, it, setting.minuteOfDay)
                            },
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    SecondaryButton(
                        text = "Change the time",
                        onClick = { editingSlot = slot },
                    )
                }
            }
        }

        item {
            InfoNote(
                "Three a day is the whole of it. This app will never send you a fourth unless you " +
                    "open the craving timer yourself.",
            )
        }

        item { SectionHeader("Where you are") }

        items(Country.entries.toList(), key = { "country_" + it.id }) { country ->
            SelectableRow(
                label = country.label,
                emoji = country.flag,
                selected = state.profile.country == country,
                onToggle = { viewModel.setCountry(country) },
            )
        }

        item {
            Spacer(Modifier.height(4.dp))
            Text("Currency", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for (currency in Currency.entries) {
                    SelectableRow(
                        label = currency.symbol,
                        selected = state.currency == currency,
                        onToggle = { viewModel.setCurrency(currency) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        item { SectionHeader("Appearance & accessibility") }

        item {
            VictoryCard {
                Column {
                    Text("Theme", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(10.dp))
                    for (mode in ThemeMode.entries) {
                        SelectableRow(
                            label = mode.label,
                            selected = settings.themeMode == mode,
                            onToggle = { viewModel.setThemeMode(mode) },
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }

        item {
            VictoryCard {
                Column {
                    ToggleRow(
                        title = "High contrast",
                        subtitle = "Stronger separation between text and background.",
                        checked = settings.highContrast,
                        onChange = { viewModel.setHighContrast(it) },
                    )
                    ToggleRow(
                        title = "Reduce motion",
                        subtitle = "Stops the breathing circle animating and gives the timing in words instead.",
                        checked = settings.reducedMotion,
                        onChange = { viewModel.setReducedMotion(it) },
                    )
                    ToggleRow(
                        title = "Use my phone's colours",
                        subtitle = "Material You. Off by default so the app keeps its own palette.",
                        checked = settings.dynamicColour,
                        onChange = { viewModel.setDynamicColour(it) },
                    )
                }
            }
        }

        item {
            InfoNote(
                "Text size follows your phone's display settings, and every screen has been built " +
                    "to work with a screen reader.",
            )
        }

        item { SectionHeader("Your journey") }

        item {
            VictoryCard {
                Column {
                    NavigationRow("❤️", "Family, reasons & photo", null) {
                        navController.navigate(Routes.FAMILY)
                    }
                    NavigationRow("🎯", "My triggers", null) {
                        navController.navigate(Routes.TRIGGERS)
                    }
                    NavigationRow("💰", "Money & savings goal", null) {
                        navController.navigate(Routes.MONEY)
                    }
                    NavigationRow("🔁", "Record a slip", "Nothing gets wiped") {
                        navController.navigate(Routes.RELAPSE)
                    }
                }
            }
        }

        item {
            SecondaryButton(
                text = "Change what I was spending",
                onClick = { editingSpend = true },
            )
        }

        item { SectionHeader("Journal lock") }

        item {
            VictoryCard {
                Column {
                    ToggleRow(
                        title = "Lock my journal",
                        subtitle = if (settings.journalLockEnabled) {
                            "A PIN is set. Turning this off clears the PIN."
                        } else {
                            "Ask for a PIN before opening the journal."
                        },
                        checked = settings.journalLockEnabled,
                        onChange = { wanted ->
                            if (wanted) showPinDialog = true else viewModel.removeJournalLock()
                        },
                    )
                    if (settings.journalLockEnabled) {
                        ToggleRow(
                            title = "Allow fingerprint unlock",
                            subtitle = "Uses your phone's biometric prompt. The PIN still works.",
                            checked = settings.biometricUnlock,
                            onChange = { viewModel.setBiometricUnlock(it) },
                        )
                        Spacer(Modifier.height(6.dp))
                        SecondaryButton(text = "Change PIN", onClick = { showPinDialog = true })
                    }
                }
            }
        }

        item { SectionHeader("Privacy") }

        item {
            VictoryCard(containerColor = MaterialTheme.colorScheme.tertiaryContainer) {
                Column {
                    Text(
                        "Your data stays on this device unless you choose to export it.",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                    Spacer(Modifier.height(12.dp))
                    for (line in PRIVACY_POINTS) {
                        Text(
                            "• $line",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                        Spacer(Modifier.height(4.dp))
                    }
                }
            }
        }

        item {
            VictoryCard {
                Column {
                    Text("Backup", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Export everything to a single encrypted file you control — useful for " +
                            "moving to a new phone. It is locked with a passphrase you choose, and " +
                            "there is no way to recover it if you forget it.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(14.dp))
                    SecondaryButton(text = "Export an encrypted backup", onClick = { showBackup = BackupMode.EXPORT })
                    Spacer(Modifier.height(8.dp))
                    SecondaryButton(text = "Restore from a backup", onClick = { showBackup = BackupMode.IMPORT })
                }
            }
        }

        item {
            VictoryCard {
                Column {
                    Text("Delete everything", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Removes your journal, check-ins, streaks, photo and settings from this " +
                            "phone. It cannot be undone.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(14.dp))
                    BigButton(
                        text = "Delete all my data",
                        onClick = { showDeleteAll = true },
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    )
                }
            }
        }

        item { SectionHeader("About") }

        item {
            VictoryCard {
                Column {
                    NavigationRow("📊", "Why quit?", "Sourced health information") {
                        navController.navigate(Routes.FACTS)
                    }
                    NavigationRow("📚", "Sources & health information", null) {
                        navController.navigate(Routes.SOURCES)
                    }
                }
            }
        }

        item {
            InfoNote(Sources.DISCLAIMER)
            Spacer(Modifier.height(12.dp))
            Text(
                Motivation.CORE_MESSAGE,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Dad's Victory · ${Motivation.totalMessageCount()} encouragements · " +
                    "made for one dad in particular",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    editingSlot?.let { slot ->
        val setting = settings.notifications[slot]
        if (setting != null) {
            TimePickerDialog(
                title = "${slot.emoji} ${slot.label} encouragement",
                initialHour = setting.hour,
                initialMinute = setting.minute,
                use24Hour = use24Hour,
                onConfirm = { hour, minute ->
                    viewModel.setNotificationSlot(slot, setting.enabled, hour * 60 + minute)
                    editingSlot = null
                },
                onDismiss = { editingSlot = null },
            )
        }
    }

    if (showPinDialog) {
        PinDialog(
            onSet = {
                viewModel.setJournalPin(it)
                showPinDialog = false
            },
            onDismiss = { showPinDialog = false },
        )
    }

    if (showDeleteAll) {
        ConfirmDialog(
            title = "Delete everything?",
            body = "Your streaks, journal, check-ins, photo and settings will be removed from this " +
                "phone. This cannot be undone, and there is no copy anywhere else.",
            confirmText = "Delete everything",
            destructive = true,
            onConfirm = {
                showDeleteAll = false
                viewModel.deleteAllData { }
            },
            onDismiss = { showDeleteAll = false },
        )
    }

    showBackup?.let { mode ->
        BackupDialog(
            mode = mode,
            viewModel = viewModel,
            onDismiss = { showBackup = null },
        )
    }

    if (editingSpend) {
        SpendDialog(
            state = state,
            onSave = { nicotine, alcohol ->
                viewModel.updateProfile(
                    state.profile.copy(
                        nicotineWeeklySpendMinor = nicotine,
                        alcoholWeeklySpendMinor = alcohol,
                    ),
                )
                editingSpend = false
            },
            onDismiss = { editingSpend = false },
        )
    }
}

private val PRIVACY_POINTS = listOf(
    "No account, and no sign-in.",
    "No advertising, no tracking and no analytics.",
    "The app has no internet permission at all, so it could not send your data anywhere even if it tried.",
    "Your journal, check-ins and photo are stored only in this app's private storage.",
    "Automatic cloud backup and phone-to-phone transfer are both switched off.",
    "Exporting a backup is the only way anything leaves this device, and only when you choose to.",
)

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.padding(horizontal = 6.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(
    title: String,
    initialHour: Int,
    initialMinute: Int,
    use24Hour: Boolean,
    onConfirm: (Int, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val pickerState = rememberTimePickerState(
        initialHour = initialHour,
        initialMinute = initialMinute,
        is24Hour = use24Hour,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TimePicker(state = pickerState)
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(pickerState.hour, pickerState.minute) }) { Text("Set") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun PinDialog(onSet: (String) -> Unit, onDismiss: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    val valid = pin.length >= 4 && pin == confirm

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set a journal PIN") },
        text = {
            Column {
                Text(
                    "At least four digits. The PIN itself is never stored — only a scrambled " +
                        "version of it — so if you forget it there is no way to get back in " +
                        "except by resetting the lock.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it.filter { ch -> ch.isDigit() }.take(12) },
                    label = { Text("PIN") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = confirm,
                    onValueChange = { confirm = it.filter { ch -> ch.isDigit() }.take(12) },
                    label = { Text("Enter it again") },
                    singleLine = true,
                    isError = confirm.isNotEmpty() && confirm != pin,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { onSet(pin) }) { Text("Set PIN") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private enum class BackupMode { EXPORT, IMPORT }

@Composable
private fun BackupDialog(
    mode: BackupMode,
    viewModel: VictoryViewModel,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var passphrase by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    var pendingBytes by remember { mutableStateOf<ByteArray?>(null) }

    val today = remember { LocalDate.now(ZoneId.systemDefault()) }
    val suggestedName = remember(today) {
        BackupCodec.suggestedFileName(today.year, today.monthValue, today.dayOfMonth)
    }

    // The system file picker: no storage permission needed, and he chooses where it goes.
    val createFile = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        val bytes = pendingBytes
        if (uri != null && bytes != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
            }.onSuccess { status = "Backup saved." }
                .onFailure { status = "Could not write that file." }
        }
        pendingBytes = null
    }

    val openFile = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val bytes = runCatching {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                }.getOrNull()

                if (bytes == null) {
                    status = "Could not read that file."
                    return@launch
                }
                runCatching { viewModel.restoreBackup(bytes, passphrase.toCharArray()) }
                    .onSuccess { status = "Restored. Everything is back." }
                    .onFailure { status = it.message ?: "That backup could not be restored." }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (mode == BackupMode.EXPORT) "Export a backup" else "Restore a backup") },
        text = {
            Column {
                Text(
                    if (mode == BackupMode.EXPORT) {
                        "Choose a passphrase. You will need exactly this passphrase to restore the " +
                            "file, and nobody — including this app — can recover it for you."
                    } else {
                        "Enter the passphrase you used when you exported the file.\n\nRestoring " +
                            "replaces everything currently in the app."
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = passphrase,
                    onValueChange = { passphrase = it },
                    label = { Text("Passphrase") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (status != null) {
                    Spacer(Modifier.height(12.dp))
                    Text(status!!, style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = passphrase.isNotBlank(),
                onClick = {
                    if (mode == BackupMode.EXPORT) {
                        scope.launch {
                            runCatching { viewModel.exportBackup(passphrase.toCharArray()) }
                                .onSuccess {
                                    pendingBytes = it
                                    createFile.launch(suggestedName)
                                }
                                .onFailure { status = "Could not build the backup." }
                        }
                    } else {
                        openFile.launch(arrayOf("*/*"))
                    }
                },
            ) { Text(if (mode == BackupMode.EXPORT) "Choose where to save" else "Choose a file") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun SpendDialog(
    state: VictoryUiState,
    onSave: (Long, Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var nicotine by remember {
        mutableStateOf(
            if (state.profile.nicotineWeeklySpendMinor == 0L) {
                ""
            } else {
                Money.toEditableText(state.profile.nicotineWeeklySpendMinor)
            },
        )
    }
    var alcohol by remember {
        mutableStateOf(
            if (state.profile.alcoholWeeklySpendMinor == 0L) {
                ""
            } else {
                Money.toEditableText(state.profile.alcoholWeeklySpendMinor)
            },
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Weekly spending") },
        text = {
            Column {
                Text(
                    "Roughly what you were spending in a normal week. This only affects the " +
                        "savings estimate.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(16.dp))
                if (state.profile.quitNicotine) {
                    OutlinedTextField(
                        value = nicotine,
                        onValueChange = { nicotine = it },
                        label = { Text("Vaping / nicotine per week") },
                        prefix = { Text(state.currency.symbol) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(12.dp))
                }
                if (state.profile.quitAlcohol) {
                    OutlinedTextField(
                        value = alcohol,
                        onValueChange = { alcohol = it },
                        label = { Text("Alcohol per week") },
                        prefix = { Text(state.currency.symbol) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        Money.parseToMinor(nicotine) ?: 0L,
                        Money.parseToMinor(alcohol) ?: 0L,
                    )
                },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
