package com.example.ledgermesh.ui.detail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Merge
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ledgermesh.data.db.entity.AggregateEntity
import com.example.ledgermesh.data.db.entity.ObservationEntity
import com.example.ledgermesh.data.db.entity.OpsLogEntity
import com.example.ledgermesh.domain.model.OperationType
import com.example.ledgermesh.domain.model.SourceType
import com.example.ledgermesh.domain.model.TransactionDirection
import com.example.ledgermesh.ui.dashboard.ConfidenceBadge
import com.example.ledgermesh.ui.dashboard.formatAmount
import com.example.ledgermesh.ui.dashboard.formatTimestamp

// -- Colors matching the design system --
private val PositiveGreen = Color(0xFF4CAF50)
private val NegativeRed = Color(0xFFE53935)
private val AmberYellow = Color(0xFFFFC107)
private val HeroCardBackground = Color(0xFF1B2838)

/**
 * Transaction Detail screen showing the canonical aggregate, linked observations,
 * operation history, and action buttons for edit/split/merge.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionDetailScreen(
    onNavigateBack: () -> Unit,
    viewModel: TransactionDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Show snackbar messages
    LaunchedEffect(uiState.message) {
        uiState.message?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Transaction Detail") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    if (uiState.isEditing) {
                        IconButton(onClick = { viewModel.saveEdits() }) {
                            Icon(Icons.Filled.Check, contentDescription = "Save")
                        }
                        IconButton(onClick = { viewModel.cancelEditing() }) {
                            Icon(Icons.Filled.Close, contentDescription = "Cancel")
                        }
                    } else {
                        IconButton(onClick = { viewModel.startEditing() }) {
                            Icon(Icons.Filled.Edit, contentDescription = "Edit")
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        val aggregate = uiState.aggregate

        if (aggregate == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Transaction not found",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // -- Canonical Transaction Card --
            item(key = "canonical_card") {
                CanonicalTransactionCard(
                    aggregate = aggregate,
                    isEditing = uiState.isEditing,
                    editNotes = uiState.editNotes,
                    editCounterparty = uiState.editCounterparty,
                    onNotesChange = viewModel::updateEditNotes,
                    onCounterpartyChange = viewModel::updateEditCounterparty
                )
            }

            // -- Confidence Explanation --
            item(key = "confidence_section") {
                ConfidenceExplanationSection(
                    aggregate = aggregate,
                    observations = uiState.observations
                )
            }

            // -- Observations Audit Trail --
            item(key = "observations_header") {
                SectionHeader(
                    title = "Observations (${uiState.observations.size})",
                    icon = Icons.Filled.Description
                )
            }

            if (uiState.observations.isEmpty()) {
                item(key = "observations_empty") {
                    Text(
                        text = "No linked observations",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            }

            items(uiState.observations, key = { it.observationId }) { observation ->
                ObservationCard(
                    observation = observation,
                    onMarkDuplicate = { viewModel.markDuplicate(observation.observationId) }
                )
            }

            // -- Operations History --
            item(key = "history_header") {
                SectionHeader(
                    title = "History",
                    icon = Icons.Filled.History
                )
            }

            if (uiState.opsHistory.isEmpty()) {
                item(key = "history_empty") {
                    Text(
                        text = "No manual operations yet",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            }

            items(uiState.opsHistory, key = { it.opId }) { opsLog ->
                OpsLogCard(opsLog = opsLog)
            }

            // -- Action Buttons --
            item(key = "actions") {
                ActionButtonsRow(
                    observationCount = uiState.observations.size,
                    onSplitClick = { viewModel.showSplitDialog() },
                    onEditClick = { viewModel.startEditing() },
                    isEditing = uiState.isEditing
                )
            }

            // Bottom spacer for FAB clearance
            item(key = "bottom_spacer") {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }

        // -- Split Dialog --
        if (uiState.showSplitDialog) {
            SplitDialog(
                observations = uiState.observations,
                selectedIds = uiState.selectedObservationIds,
                onToggle = viewModel::toggleObservationSelection,
                onConfirm = viewModel::confirmSplit,
                onDismiss = viewModel::dismissSplitDialog
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Canonical Transaction Card
// ---------------------------------------------------------------------------

@Composable
private fun CanonicalTransactionCard(
    aggregate: AggregateEntity,
    isEditing: Boolean,
    editNotes: String,
    editCounterparty: String,
    onNotesChange: (String) -> Unit,
    onCounterpartyChange: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = HeroCardBackground),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Direction icon
            val directionIcon = when (aggregate.canonicalDirection) {
                TransactionDirection.CREDIT -> Icons.Filled.ArrowDownward
                TransactionDirection.DEBIT -> Icons.Filled.ArrowUpward
                else -> Icons.Filled.SwapVert
            }
            val directionColor = when (aggregate.canonicalDirection) {
                TransactionDirection.CREDIT -> PositiveGreen
                TransactionDirection.DEBIT -> NegativeRed
                else -> Color.White.copy(alpha = 0.7f)
            }
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(directionColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = directionIcon,
                    contentDescription = aggregate.canonicalDirection.name,
                    tint = directionColor,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Amount
            val prefix = when (aggregate.canonicalDirection) {
                TransactionDirection.CREDIT -> "+"
                TransactionDirection.DEBIT -> "-"
                else -> ""
            }
            Text(
                text = "$prefix${formatAmount(aggregate.canonicalAmountMinor, aggregate.canonicalCurrency)}",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = directionColor
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Timestamp
            Text(
                text = formatTimestamp(aggregate.canonicalTimestamp),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.7f)
            )
            if (aggregate.isApproxTime) {
                Text(
                    text = "(approximate time)",
                    style = MaterialTheme.typography.labelSmall,
                    color = AmberYellow.copy(alpha = 0.8f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.15f))
            Spacer(modifier = Modifier.height(16.dp))

            // Counterparty
            if (isEditing) {
                OutlinedTextField(
                    value = editCounterparty,
                    onValueChange = onCounterpartyChange,
                    label = { Text("Counterparty") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.White)
                )
            } else {
                DetailRow(label = "Counterparty", value = aggregate.canonicalCounterparty ?: "Unknown")
            }

            // Reference
            if (aggregate.canonicalReference != null) {
                Spacer(modifier = Modifier.height(8.dp))
                DetailRow(label = "Reference", value = aggregate.canonicalReference)
            }

            // Account hint
            if (aggregate.canonicalAccountHint != null) {
                Spacer(modifier = Modifier.height(8.dp))
                DetailRow(label = "Account", value = aggregate.canonicalAccountHint)
            }

            // Category
            if (aggregate.categoryId != null) {
                Spacer(modifier = Modifier.height(8.dp))
                DetailRow(label = "Category", value = aggregate.categoryId)
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Confidence badge + score
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                ConfidenceBadge(score = aggregate.confidenceScore)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${aggregate.confidenceScore}/100",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = Color.White.copy(alpha = 0.1f)
                ) {
                    Text(
                        text = "${aggregate.observationCount} source${if (aggregate.observationCount != 1) "s" else ""}",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }
            }

            // Notes
            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.15f))
            Spacer(modifier = Modifier.height(12.dp))

            if (isEditing) {
                OutlinedTextField(
                    value = editNotes,
                    onValueChange = onNotesChange,
                    label = { Text("Notes") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White)
                )
            } else {
                val notesText = aggregate.userNotes
                if (notesText.isNullOrBlank()) {
                    Text(
                        text = "No notes. Tap edit to add.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.4f)
                    )
                } else {
                    DetailRow(label = "Notes", value = notesText)
                }
            }
        }
    }
}

/** A label-value row displayed within the hero card. */
@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.5f),
            modifier = Modifier.width(100.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White,
            modifier = Modifier.weight(1f),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ---------------------------------------------------------------------------
// Confidence Explanation Section
// ---------------------------------------------------------------------------

@Composable
private fun ConfidenceExplanationSection(
    aggregate: AggregateEntity,
    observations: List<ObservationEntity>
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Why this confidence?",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    // Number of sources
                    val sourceCount = observations.size
                    val sourceTypes = observations.map { it.sourceType }.distinct()
                    ConfidenceFactorRow(
                        label = "Source count",
                        value = "$sourceCount observation${if (sourceCount != 1) "s" else ""} from ${sourceTypes.joinToString(", ") { it.name }}",
                        isPositive = sourceCount > 1
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Reference match status
                    val hasReference = aggregate.canonicalReference != null
                    val referencesAgree = observations.mapNotNull { it.reference }.distinct().size <= 1
                    ConfidenceFactorRow(
                        label = "Reference match",
                        value = when {
                            !hasReference -> "No reference available"
                            referencesAgree -> "All sources agree"
                            else -> "References differ across sources"
                        },
                        isPositive = hasReference && referencesAgree
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Timestamp dispersion
                    val timestamps = observations.mapNotNull { it.timestamp }
                    val dispersion = if (timestamps.size >= 2) {
                        val spread = timestamps.max() - timestamps.min()
                        when {
                            spread < 60_000 -> "Within 1 minute"
                            spread < 3_600_000 -> "Within ${spread / 60_000} minutes"
                            spread < 86_400_000 -> "Within ${spread / 3_600_000} hours"
                            else -> "Spread across ${spread / 86_400_000} days"
                        }
                    } else {
                        "Single timestamp"
                    }
                    val tightTimestamp = timestamps.size < 2 ||
                        (timestamps.max() - timestamps.min()) < 3_600_000
                    ConfidenceFactorRow(
                        label = "Timestamp dispersion",
                        value = dispersion,
                        isPositive = tightTimestamp
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Amount agreement
                    val amounts = observations.map { it.amountMinor }.distinct()
                    ConfidenceFactorRow(
                        label = "Amount agreement",
                        value = if (amounts.size == 1) "All sources agree" else "${amounts.size} different amounts",
                        isPositive = amounts.size == 1
                    )
                }
            }
        }
    }
}

@Composable
private fun ConfidenceFactorRow(label: String, value: String, isPositive: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        val indicatorColor = if (isPositive) PositiveGreen else AmberYellow
        Box(
            modifier = Modifier
                .padding(top = 4.dp)
                .size(8.dp)
                .clip(CircleShape)
                .background(indicatorColor)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Section Header
// ---------------------------------------------------------------------------

@Composable
private fun SectionHeader(title: String, icon: ImageVector) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

// ---------------------------------------------------------------------------
// Observation Card
// ---------------------------------------------------------------------------

@Composable
private fun ObservationCard(
    observation: ObservationEntity,
    onMarkDuplicate: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Source type icon
                val sourceIcon = sourceTypeIcon(observation.sourceType)
                val sourceLabel = observation.sourceType.name
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = sourceIcon,
                        contentDescription = sourceLabel,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = sourceLabel,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = formatAmount(observation.amountMinor, observation.currency),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Parse confidence
                val parseConfidenceLabel = "${"%.0f".format(observation.parseConfidence * 100)}%"
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = parseConfidenceColor(observation.parseConfidence).copy(alpha = 0.12f)
                ) {
                    Text(
                        text = parseConfidenceLabel,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = parseConfidenceColor(observation.parseConfidence)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Timestamp
            Text(
                text = formatTimestamp(observation.timestamp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Raw payload preview
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(4.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Text(
                    text = observation.rawPayload.take(200),
                    modifier = Modifier.padding(8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Mark duplicate button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onMarkDuplicate) {
                    Icon(
                        imageVector = Icons.Filled.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Mark Duplicate", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

/** Map a [SourceType] to an appropriate Material icon. */
private fun sourceTypeIcon(sourceType: SourceType): ImageVector = when (sourceType) {
    SourceType.SMS -> Icons.Filled.Email
    SourceType.CSV -> Icons.Filled.TableChart
    SourceType.PDF -> Icons.Filled.Description
    SourceType.XLSX -> Icons.Filled.TableChart
}

/** Color for parse confidence percentage badge. */
private fun parseConfidenceColor(confidence: Double): Color = when {
    confidence >= 0.75 -> PositiveGreen
    confidence >= 0.4 -> AmberYellow
    else -> NegativeRed
}

// ---------------------------------------------------------------------------
// Ops Log Card
// ---------------------------------------------------------------------------

@Composable
private fun OpsLogCard(opsLog: OpsLogEntity) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Operation icon
            val (opIcon, opLabel) = operationDisplay(opsLog.operationType)
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = opIcon,
                    contentDescription = opLabel,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(16.dp)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = opLabel,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = operationDescription(opsLog),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = formatTimestamp(opsLog.createdAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun operationDisplay(type: OperationType): Pair<ImageVector, String> = when (type) {
    OperationType.MERGE -> Icons.Filled.Merge to "Merge"
    OperationType.SPLIT -> Icons.AutoMirrored.Filled.CallSplit to "Split"
    OperationType.MARK_DUPLICATE -> Icons.Filled.ContentCopy to "Mark Duplicate"
    OperationType.EDIT_FIELD -> Icons.Filled.Edit to "Edit"
}

private fun operationDescription(opsLog: OpsLogEntity): String = when (opsLog.operationType) {
    OperationType.MERGE -> "Merged with aggregate ${opsLog.secondaryAggregateId?.take(8) ?: "unknown"}"
    OperationType.SPLIT -> {
        val count = opsLog.affectedObservationIds?.split(",")?.size ?: 0
        "$count observation${if (count != 1) "s" else ""} split to new aggregate"
    }
    OperationType.MARK_DUPLICATE -> "Observation flagged as duplicate"
    OperationType.EDIT_FIELD -> {
        val field = opsLog.fieldName ?: "field"
        "Changed $field"
    }
}

// ---------------------------------------------------------------------------
// Action Buttons
// ---------------------------------------------------------------------------

@Composable
private fun ActionButtonsRow(
    observationCount: Int,
    onSplitClick: () -> Unit,
    onEditClick: () -> Unit,
    isEditing: Boolean
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onEditClick,
                modifier = Modifier.weight(1f),
                enabled = !isEditing
            ) {
                Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Edit")
            }

            OutlinedButton(
                onClick = onSplitClick,
                modifier = Modifier.weight(1f),
                enabled = observationCount > 1
            ) {
                Icon(Icons.AutoMirrored.Filled.CallSplit, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Split")
            }

            OutlinedButton(
                onClick = { /* TODO: merge picker */ },
                modifier = Modifier.weight(1f),
                enabled = false
            ) {
                Icon(Icons.Filled.Merge, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Merge")
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Split Dialog
// ---------------------------------------------------------------------------

@Composable
private fun SplitDialog(
    observations: List<ObservationEntity>,
    selectedIds: Set<String>,
    onToggle: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val canConfirm = selectedIds.isNotEmpty() && selectedIds.size < observations.size

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Split Observations") },
        text = {
            Column {
                Text(
                    text = "Select observations to split into a new transaction. At least one must remain with the original.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                observations.forEach { obs ->
                    val isSelected = obs.observationId in selectedIds
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onToggle(obs.observationId) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = { onToggle(obs.observationId) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${obs.sourceType.name} - ${formatAmount(obs.amountMinor, obs.currency)}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = obs.rawPayload.take(80),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    if (obs != observations.last()) {
                        HorizontalDivider(modifier = Modifier.padding(start = 48.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = canConfirm) {
                Text("Split (${selectedIds.size})")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
