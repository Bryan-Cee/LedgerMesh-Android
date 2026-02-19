package com.example.ledgermesh.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Box
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ledgermesh.ingestion.sms.SenderProfile

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Dialog visibility state
    var showProfilesDialog by remember { mutableStateOf(false) }
    var showConfidenceDialog by remember { mutableStateOf(false) }
    var showTimeWindowDialog by remember { mutableStateOf(false) }
    var showAmountToleranceDialog by remember { mutableStateOf(false) }
    var showPinDialog by remember { mutableStateOf(false) }
    var showResetConfirmDialog by remember { mutableStateOf(false) }

    // SAF file creator for export
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? ->
        uri?.let { viewModel.exportLedger(it) }
    }

    // Show snackbar messages
    LaunchedEffect(uiState.message) {
        uiState.message?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearMessage()
        }
    }

    // -- Dialogs --
    if (showProfilesDialog) {
        IngestionProfilesDialog(
            profiles = uiState.senderProfiles,
            onDismiss = { showProfilesDialog = false }
        )
    }
    if (showConfidenceDialog) {
        ConfidenceThresholdDialog(
            currentValue = uiState.confidenceThreshold,
            onConfirm = {
                viewModel.updateConfidenceThreshold(it)
                showConfidenceDialog = false
            },
            onDismiss = { showConfidenceDialog = false }
        )
    }
    if (showTimeWindowDialog) {
        TimeWindowDialog(
            currentValue = uiState.timeWindowHours,
            onConfirm = {
                viewModel.updateTimeWindowHours(it)
                showTimeWindowDialog = false
            },
            onDismiss = { showTimeWindowDialog = false }
        )
    }
    if (showAmountToleranceDialog) {
        AmountToleranceDialog(
            currentValue = uiState.amountToleranceCents,
            onConfirm = {
                viewModel.updateAmountToleranceCents(it)
                showAmountToleranceDialog = false
            },
            onDismiss = { showAmountToleranceDialog = false }
        )
    }
    if (showPinDialog) {
        ChangePinDialog(
            hasExistingPin = uiState.hasPin,
            onVerifyOldPin = { viewModel.verifyPin(it) },
            onSetPin = {
                viewModel.setPin(it)
                showPinDialog = false
            },
            onClearPin = {
                viewModel.clearPin()
                showPinDialog = false
            },
            onDismiss = { showPinDialog = false }
        )
    }
    if (showResetConfirmDialog) {
        ResetConfirmDialog(
            onConfirm = {
                viewModel.resetAllData()
                showResetConfirmDialog = false
            },
            onDismiss = { showResetConfirmDialog = false }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp)
        ) {
            // Title
            item {
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }

            // -- SECURITY section --
            item { SectionHeader(title = "SECURITY") }

            item {
                SettingsToggleRow(
                    title = "Biometric Authentication",
                    checked = uiState.biometricEnabled,
                    onToggle = { viewModel.toggleBiometric(it) }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }

            item {
                SettingsChevronRow(
                    title = if (uiState.hasPin) "Change PIN" else "Set PIN",
                    onClick = { showPinDialog = true }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }

            // -- RECONCILIATION section --
            item { SectionHeader(title = "RECONCILIATION") }

            item {
                SettingsValueRow(
                    title = "Confidence Threshold",
                    value = "${uiState.confidenceThreshold}%",
                    onClick = { showConfidenceDialog = true }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }

            item {
                SettingsValueRow(
                    title = "Time Window Tolerance",
                    value = formatTimeWindow(uiState.timeWindowHours),
                    onClick = { showTimeWindowDialog = true }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }

            item {
                SettingsValueRow(
                    title = "Amount Tolerance",
                    value = formatAmountTolerance(uiState.amountToleranceCents),
                    onClick = { showAmountToleranceDialog = true }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }

            // -- INGESTION section --
            item { SectionHeader(title = "INGESTION") }

            item {
                SettingsToggleRow(
                    title = "Auto-import SMS",
                    checked = uiState.autoImportSms,
                    onToggle = { viewModel.toggleAutoImportSms(it) }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }

            item {
                SettingsChevronRow(
                    title = "Ingestion Profiles",
                    onClick = { showProfilesDialog = true }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }

            // -- DATA section --
            item { SectionHeader(title = "DATA") }

            item {
                SettingsChevronRow(
                    title = "Export Ledger",
                    onClick = { exportLauncher.launch("ledgermesh_export.csv") }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }

            item {
                SettingsChevronRow(
                    title = "Reset All Data",
                    titleColor = MaterialTheme.colorScheme.error,
                    onClick = { showResetConfirmDialog = true }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }

            // -- APPEARANCE section --
            item { SectionHeader(title = "APPEARANCE") }

            item {
                SettingsToggleRow(
                    title = "Dark Mode",
                    checked = uiState.darkMode,
                    onToggle = { viewModel.toggleDarkMode(it) }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

// ---------------------------------------------------------------------------
// Dialogs
// ---------------------------------------------------------------------------

@Composable
private fun ConfidenceThresholdDialog(
    currentValue: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var sliderValue by remember { mutableFloatStateOf(currentValue.toFloat()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Confidence Threshold") },
        text = {
            Column {
                Text(
                    text = "Transactions below this score will appear in the review queue.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "${sliderValue.toInt()}%",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
                Slider(
                    value = sliderValue,
                    onValueChange = { sliderValue = it },
                    valueRange = 10f..100f,
                    steps = 17
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("10%", style = MaterialTheme.typography.labelSmall)
                    Text("100%", style = MaterialTheme.typography.labelSmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(sliderValue.toInt()) }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun TimeWindowDialog(
    currentValue: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val options = listOf(1, 6, 12, 24, 48, 72, 168)
    var selectedIndex by remember {
        mutableIntStateOf(options.indexOf(currentValue).coerceAtLeast(0))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Time Window Tolerance") },
        text = {
            Column {
                Text(
                    text = "How far apart two observations can be in time and still be considered the same transaction.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                options.forEachIndexed { index, hours ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedIndex = index }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = formatTimeWindow(hours),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (index == selectedIndex) FontWeight.Bold else FontWeight.Normal,
                            color = if (index == selectedIndex)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        if (index == selectedIndex) {
                            Text(
                                text = "\u2713",
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(options[selectedIndex]) }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun AmountToleranceDialog(
    currentValue: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val options = listOf(0, 10, 50, 100, 500, 1000)
    var selectedIndex by remember {
        mutableIntStateOf(options.indexOf(currentValue).coerceAtLeast(0))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Amount Tolerance") },
        text = {
            Column {
                Text(
                    text = "How much two transaction amounts can differ and still match during reconciliation.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                options.forEachIndexed { index, cents ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedIndex = index }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = formatAmountTolerance(cents),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (index == selectedIndex) FontWeight.Bold else FontWeight.Normal,
                            color = if (index == selectedIndex)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        if (index == selectedIndex) {
                            Text(
                                text = "\u2713",
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(options[selectedIndex]) }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun ChangePinDialog(
    hasExistingPin: Boolean,
    onVerifyOldPin: (String) -> Boolean,
    onSetPin: (String) -> Unit,
    onClearPin: () -> Unit,
    onDismiss: () -> Unit
) {
    var step by remember { mutableStateOf(if (hasExistingPin) PinStep.VERIFY_OLD else PinStep.ENTER_NEW) }
    var pinInput by remember { mutableStateOf("") }
    var confirmInput by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when (step) {
                    PinStep.VERIFY_OLD -> "Enter Current PIN"
                    PinStep.ENTER_NEW -> if (hasExistingPin) "Enter New PIN" else "Set PIN"
                    PinStep.CONFIRM_NEW -> "Confirm PIN"
                }
            )
        },
        text = {
            Column {
                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                when (step) {
                    PinStep.VERIFY_OLD -> {
                        OutlinedTextField(
                            value = pinInput,
                            onValueChange = { if (it.length <= 6) pinInput = it },
                            label = { Text("Current PIN") },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    PinStep.ENTER_NEW -> {
                        OutlinedTextField(
                            value = pinInput,
                            onValueChange = { if (it.length <= 6) pinInput = it },
                            label = { Text("New PIN (4-6 digits)") },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    PinStep.CONFIRM_NEW -> {
                        OutlinedTextField(
                            value = confirmInput,
                            onValueChange = { if (it.length <= 6) confirmInput = it },
                            label = { Text("Confirm PIN") },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                errorMessage = null
                when (step) {
                    PinStep.VERIFY_OLD -> {
                        if (onVerifyOldPin(pinInput)) {
                            pinInput = ""
                            step = PinStep.ENTER_NEW
                        } else {
                            errorMessage = "Incorrect PIN"
                        }
                    }
                    PinStep.ENTER_NEW -> {
                        if (pinInput.length < 4) {
                            errorMessage = "PIN must be at least 4 digits"
                        } else {
                            step = PinStep.CONFIRM_NEW
                        }
                    }
                    PinStep.CONFIRM_NEW -> {
                        if (confirmInput == pinInput) {
                            onSetPin(pinInput)
                        } else {
                            errorMessage = "PINs don't match"
                            confirmInput = ""
                        }
                    }
                }
            }) {
                Text(
                    when (step) {
                        PinStep.VERIFY_OLD -> "Next"
                        PinStep.ENTER_NEW -> "Next"
                        PinStep.CONFIRM_NEW -> "Set PIN"
                    }
                )
            }
        },
        dismissButton = {
            Row {
                if (hasExistingPin && step == PinStep.VERIFY_OLD) {
                    TextButton(onClick = {
                        // Allow removing PIN after verifying old one
                        // For simplicity, just skip verification for removal
                    }) {}
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}

private enum class PinStep {
    VERIFY_OLD, ENTER_NEW, CONFIRM_NEW
}

@Composable
private fun ResetConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reset All Data") },
        text = {
            Text(
                "This will permanently delete all transactions, observations, import history, " +
                    "and operation logs. This action cannot be undone.\n\nYour settings and " +
                    "preferences will be preserved.",
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Reset", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

// ---------------------------------------------------------------------------
// Ingestion Profiles Dialog
// ---------------------------------------------------------------------------

@Composable
private fun IngestionProfilesDialog(
    profiles: List<SenderProfile>,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Ingestion Profiles",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(profiles, key = { it.id }) { profile ->
                    ProfileRow(profile)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun ProfileRow(profile: SenderProfile) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = profile.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = if (profile.enabled) "Enabled" else "Disabled",
                style = MaterialTheme.typography.labelSmall,
                color = if (profile.enabled) {
                    Color(0xFF4CAF50)
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }

        if (profile.senderAddresses.isNotEmpty()) {
            Text(
                text = "Senders: ${profile.senderAddresses.joinToString(", ")}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Text(
                text = "Content-only (no sender filter)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Row {
            Text(
                text = "${profile.patterns.size} pattern${if (profile.patterns.size != 1) "s" else ""}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "Priority: ${profile.priority}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = profile.currency,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        HorizontalDivider(
            modifier = Modifier.padding(top = 8.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
    }
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

private fun formatTimeWindow(hours: Int): String = when {
    hours == 1 -> "1 hour"
    hours < 24 -> "$hours hours"
    hours == 24 -> "1 day"
    hours == 48 -> "2 days"
    hours == 72 -> "3 days"
    hours == 168 -> "1 week"
    else -> "$hours hours"
}

private fun formatAmountTolerance(cents: Int): String = when (cents) {
    0 -> "Exact match"
    else -> "$${cents / 100}.${"%02d".format(cents % 100)}"
}

// ---------------------------------------------------------------------------
// Reusable Settings Row Components
// ---------------------------------------------------------------------------

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)
    )
}

@Composable
private fun SettingsToggleRow(
    title: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle(!checked) }
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedTrackColor = MaterialTheme.colorScheme.primary
            )
        )
    }
}

@Composable
private fun SettingsValueRow(
    title: String,
    value: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 4.dp)
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SettingsChevronRow(
    title: String,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = titleColor,
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
