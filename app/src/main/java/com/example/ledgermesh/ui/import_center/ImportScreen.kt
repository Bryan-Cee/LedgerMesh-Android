package com.example.ledgermesh.ui.import_center

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ledgermesh.data.db.entity.ImportSessionEntity
import com.example.ledgermesh.domain.model.ImportStatus
import com.example.ledgermesh.domain.model.SourceType
import com.example.ledgermesh.ingestion.csv.CsvColumnMapping
import com.example.ledgermesh.ingestion.csv.ImportResult
import com.example.ledgermesh.ingestion.pdf.PdfImportResult
import com.example.ledgermesh.ingestion.sms.SmsImportResult
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(
    viewModel: ImportViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var showSmsRationale by remember { mutableStateOf(false) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            selectedUri = it
            val fileName = uri.lastPathSegment ?: "import.csv"
            val inputStream = context.contentResolver.openInputStream(uri)
            if (inputStream != null) {
                viewModel.previewCsv(uri, inputStream, fileName)
            }
        }
    }

    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            val fileName = uri.lastPathSegment ?: "statement.pdf"
            val inputStream = context.contentResolver.openInputStream(uri)
            if (inputStream != null) {
                viewModel.importPdf(inputStream, fileName)
            }
        }
    }

    // SMS permission request launcher
    val smsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.importSms()
        }
        // If denied, do nothing -- user can try again
    }

    /**
     * Checks READ_SMS permission and either starts the import directly,
     * shows a rationale dialog, or requests the permission.
     */
    fun handleSmsImportClick() {
        val hasSmsPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_SMS
        ) == PackageManager.PERMISSION_GRANTED

        if (hasSmsPermission) {
            viewModel.importSms()
        } else {
            // Show rationale before requesting permission
            showSmsRationale = true
        }
    }

    // SMS permission rationale dialog
    if (showSmsRationale) {
        AlertDialog(
            onDismissRequest = { showSmsRationale = false },
            icon = {
                Icon(
                    Icons.Default.Sms,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = { Text("SMS Access Required") },
            text = {
                Text(
                    "LedgerMesh needs to read your SMS messages to detect and import " +
                        "financial transactions from banks and mobile money services. " +
                        "Messages are processed entirely on your device and never leave " +
                        "your phone."
                )
            },
            confirmButton = {
                Button(onClick = {
                    showSmsRationale = false
                    smsPermissionLauncher.launch(Manifest.permission.READ_SMS)
                }) {
                    Text("Grant Access")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSmsRationale = false }) {
                    Text("Not Now")
                }
            }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Title
        item {
            Text(
                text = "Import Center",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
        }

        when (val step = uiState.step) {
            is ImportStep.Idle -> {
                // Import source cards
                item {
                    ImportSourceCard(
                        icon = Icons.Default.ChatBubble,
                        iconBackgroundColor = Color(0xFF4CAF50),
                        title = "Read SMS Messages",
                        subtitle = "Import transaction notifications from SMS",
                        enabled = true,
                        onClick = { handleSmsImportClick() }
                    )
                }
                item {
                    ImportSourceCard(
                        icon = Icons.Default.Description,
                        iconBackgroundColor = Color(0xFFFF9800),
                        title = "Import PDF Statements",
                        subtitle = "Upload bank statements and receipts",
                        enabled = true,
                        onClick = {
                            pdfPickerLauncher.launch(arrayOf("application/pdf"))
                        }
                    )
                }
                item {
                    ImportSourceCard(
                        icon = Icons.Default.GridOn,
                        iconBackgroundColor = Color(0xFF2196F3),
                        title = "Import CSV File",
                        subtitle = "Upload transaction exports from your bank",
                        enabled = true,
                        onClick = {
                            filePickerLauncher.launch(
                                arrayOf(
                                    "text/csv",
                                    "text/comma-separated-values",
                                    "application/csv",
                                    "*/*"
                                )
                            )
                        }
                    )
                }
            }

            is ImportStep.Preview, is ImportStep.MappingConfig -> {
                val preview = when (step) {
                    is ImportStep.Preview -> step.result
                    is ImportStep.MappingConfig -> step.preview
                    else -> return@LazyColumn
                }
                val mapping = when (step) {
                    is ImportStep.MappingConfig -> step.mapping
                    else -> null
                }

                item {
                    MappingConfigCard(
                        headers = preview.headers,
                        sampleRows = preview.sampleRows,
                        currentMapping = mapping,
                        onMappingChange = { viewModel.updateMapping(it) },
                        onConfirm = {
                            selectedUri?.let { uri ->
                                val inputStream = context.contentResolver.openInputStream(uri)
                                if (inputStream != null) {
                                    viewModel.confirmImport(inputStream)
                                }
                            }
                        },
                        onCancel = { viewModel.reset() }
                    )
                }
            }

            is ImportStep.Importing -> {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Importing ${step.fileName}...")
                        }
                    }
                }
            }

            is ImportStep.SmsImporting -> {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = step.message,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Matching messages against known sender profiles...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            is ImportStep.Complete -> {
                item {
                    ImportCompleteCard(
                        result = step.result,
                        onDone = { viewModel.reset() }
                    )
                }
            }

            is ImportStep.SmsComplete -> {
                item {
                    SmsImportCompleteCard(
                        result = step.result,
                        onDone = { viewModel.reset() }
                    )
                }
            }

            is ImportStep.PdfImporting -> {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Importing PDF...",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = step.fileName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            is ImportStep.PdfComplete -> {
                item {
                    PdfImportCompleteCard(
                        result = step.result,
                        onDone = { viewModel.reset() }
                    )
                }
            }

            is ImportStep.Error -> {
                item {
                    ErrorCard(
                        message = step.message,
                        onRetry = { viewModel.reset() }
                    )
                }
            }
        }

        // Recent imports section
        if (uiState.recentSessions.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Recent Imports",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            items(uiState.recentSessions, key = { it.sessionId }) { session ->
                ImportSessionRow(session)
            }
        }

        // Tip card at bottom
        item {
            Spacer(modifier = Modifier.height(4.dp))
            TipCard()
        }
    }
}

/**
 * An outlined card representing a single import source option.
 * Each card shows a colored circular icon, a bold title, and a subtitle description.
 */
@Composable
private fun ImportSourceCard(
    icon: ImageVector,
    iconBackgroundColor: Color,
    title: String,
    subtitle: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val cardAlpha = if (enabled) 1f else 0.6f

    OutlinedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled,
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Colored circle icon
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(iconBackgroundColor.copy(alpha = if (enabled) 0.15f else 0.08f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconBackgroundColor.copy(alpha = cardAlpha),
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = cardAlpha)
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = cardAlpha)
                )
            }

            if (!enabled) {
                Text(
                    text = "Soon",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MappingConfigCard(
    headers: List<String>,
    @Suppress("UNUSED_PARAMETER") sampleRows: List<List<String>>,
    currentMapping: CsvColumnMapping?,
    onMappingChange: (CsvColumnMapping) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    var dateCol by remember { mutableIntStateOf(currentMapping?.dateColumn ?: 0) }
    var descCol by remember { mutableStateOf(currentMapping?.descriptionColumn) }
    var amountCol by remember { mutableStateOf(currentMapping?.amountColumn) }
    var debitCol by remember { mutableStateOf(currentMapping?.debitColumn) }
    var creditCol by remember { mutableStateOf(currentMapping?.creditColumn) }
    var refCol by remember { mutableStateOf(currentMapping?.referenceColumn) }
    var currency by remember { mutableStateOf(currentMapping?.currency ?: "KES") }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Column Mapping", style = MaterialTheme.typography.titleMedium)
            Text(
                "Map your CSV columns to transaction fields",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            ColumnDropdown("Date Column *", headers, dateCol) { dateCol = it }
            ColumnDropdown("Description", headers, descCol ?: -1) {
                descCol = if (it >= 0) it else null
            }
            ColumnDropdown("Amount (single column)", headers, amountCol ?: -1) {
                amountCol = if (it >= 0) it else null
            }
            ColumnDropdown("Debit Column", headers, debitCol ?: -1) {
                debitCol = if (it >= 0) it else null
            }
            ColumnDropdown("Credit Column", headers, creditCol ?: -1) {
                creditCol = if (it >= 0) it else null
            }
            ColumnDropdown("Reference", headers, refCol ?: -1) {
                refCol = if (it >= 0) it else null
            }

            OutlinedTextField(
                value = currency,
                onValueChange = { currency = it.uppercase().take(3) },
                label = { Text("Currency (ISO)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                    Text("Cancel")
                }
                Button(
                    onClick = {
                        val mapping = CsvColumnMapping(
                            dateColumn = dateCol,
                            descriptionColumn = descCol,
                            amountColumn = amountCol,
                            debitColumn = debitCol,
                            creditColumn = creditCol,
                            referenceColumn = refCol,
                            currency = currency
                        )
                        onMappingChange(mapping)
                        onConfirm()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Import")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ColumnDropdown(
    label: String,
    headers: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val options = listOf("(none)") + headers

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = if (selectedIndex in headers.indices) headers[selectedIndex] else "(none)",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEachIndexed { index, option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSelect(index - 1)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun ImportCompleteCard(result: ImportResult, onDone: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp)
            )
            Text("Import Complete!", style = MaterialTheme.typography.titleLarge)
            Text("${result.importedCount} transactions imported")
            if (result.skippedCount > 0) {
                Text(
                    "${result.skippedCount} duplicates skipped",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (result.errorCount > 0) {
                Text(
                    "${result.errorCount} rows had errors",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = onDone) { Text("Done") }
        }
    }
}

/**
 * Results card shown after a successful SMS import, displaying
 * total scanned, imported, skipped (duplicate), and unmatched counts.
 */
@Composable
private fun SmsImportCompleteCard(result: SmsImportResult, onDone: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                tint = Color(0xFF4CAF50),
                modifier = Modifier.size(48.dp)
            )
            Text("SMS Import Complete!", style = MaterialTheme.typography.titleLarge)

            Text("${result.totalScanned} messages scanned")

            Text(
                "${result.importedCount} transactions imported",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )

            if (result.skippedCount > 0) {
                Text(
                    "${result.skippedCount} duplicates skipped",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (result.unmatchedCount > 0) {
                Text(
                    "${result.unmatchedCount} messages could not be parsed",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (result.errors.isNotEmpty()) {
                Text(
                    "${result.errors.size} errors encountered",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = onDone) { Text("Done") }
        }
    }
}

@Composable
private fun PdfImportCompleteCard(result: PdfImportResult, onDone: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                tint = Color(0xFFFF9800),
                modifier = Modifier.size(48.dp)
            )
            Text("PDF Import Complete!", style = MaterialTheme.typography.titleLarge)

            if (result.detectedTableCount > 0) {
                Text(
                    "${result.detectedTableCount} table(s) detected",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                "${result.importedCount} transactions imported",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )

            if (result.skippedCount > 0) {
                Text(
                    "${result.skippedCount} duplicates skipped",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (result.errorCount > 0) {
                Text(
                    "${result.errorCount} rows had errors",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = onDone) { Text("Done") }
        }
    }
}

@Composable
private fun ErrorCard(message: String, onRetry: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                Icons.Default.Error,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(48.dp)
            )
            Text("Import Error", style = MaterialTheme.typography.titleMedium)
            Text(message, style = MaterialTheme.typography.bodySmall)
            Button(onClick = onRetry) { Text("Try Again") }
        }
    }
}

/**
 * A single row in the Recent Imports list showing status icon, import type, date,
 * and transaction count or failure indicator.
 */
@Composable
private fun ImportSessionRow(session: ImportSessionEntity) {
    val isSuccess = session.status == ImportStatus.COMPLETED
    val isFailed = session.status == ImportStatus.FAILED

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Status icon
            Icon(
                imageVector = if (isFailed) Icons.Default.Cancel else Icons.Default.CheckCircle,
                contentDescription = if (isFailed) "Failed" else "Success",
                tint = if (isFailed) MaterialTheme.colorScheme.error else Color(0xFF4CAF50),
                modifier = Modifier.size(24.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Import type and date
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = formatImportTypeName(session.sourceType),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = formatImportDate(session.createdAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Transaction count or failure status
            if (isFailed) {
                Text(
                    text = "Failed",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.error
                )
            } else if (isSuccess) {
                Text(
                    text = "${session.importedRecords} transactions",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = session.status.name.lowercase()
                        .replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/**
 * Informational tip card displayed at the bottom of the import screen.
 */
@Composable
private fun TipCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFE3F2FD)
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = Color(0xFF1976D2),
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "Tip: Enable automatic SMS reading in Settings for real-time transaction detection.",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF1565C0)
            )
        }
    }
}

/**
 * Maps a [SourceType] to a human-readable import name for display in the recent imports list.
 */
private fun formatImportTypeName(sourceType: SourceType): String = when (sourceType) {
    SourceType.SMS -> "SMS Import"
    SourceType.PDF -> "PDF Import"
    SourceType.CSV -> "CSV Import"
    SourceType.XLSX -> "XLSX Import"
}

/**
 * Formats an epoch-millis timestamp into a readable date for the recent imports list.
 */
private fun formatImportDate(epochMillis: Long): String {
    val instant = Instant.ofEpochMilli(epochMillis)
    val formatter = DateTimeFormatter.ofPattern("MMM dd, yyyy")
        .withZone(ZoneId.systemDefault())
    return formatter.format(instant)
}
