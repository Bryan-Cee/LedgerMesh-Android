package com.example.ledgermesh.ui.review

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ledgermesh.data.db.entity.AggregateEntity
import com.example.ledgermesh.domain.model.TransactionDirection
import com.example.ledgermesh.ui.dashboard.ConfidenceBadge
import com.example.ledgermesh.ui.dashboard.formatAmount
import com.example.ledgermesh.ui.dashboard.formatTimestamp

// -- Shared color palette matching the rest of the app --
private val PositiveGreen = Color(0xFF4CAF50)
private val NegativeRed = Color(0xFFE53935)
private val AmberOrange = Color(0xFFFF9800)
private val FlagChipBackground = Color(0xFFFFF3E0) // light amber for flag chips

@Composable
fun ReviewQueueScreen(
    onNavigateToDetail: (String) -> Unit = {},
    onNavigateBack: () -> Unit = {},
    viewModel: ReviewQueueViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Show one-shot messages via snackbar
    LaunchedEffect(uiState.message) {
        uiState.message?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearMessage()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (uiState.items.isEmpty() && !uiState.isLoading) {
            EmptyReviewState()
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                item {
                    Column {
                        Text(
                            text = "Review Queue",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${uiState.items.size} item${if (uiState.items.size != 1) "s" else ""} need attention",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Review items
                items(uiState.items, key = { it.aggregate.aggregateId }) { item ->
                    ReviewItemCard(
                        item = item,
                        onApprove = { viewModel.approveItem(item.aggregate.aggregateId) },
                        onViewDetails = { onNavigateToDetail(item.aggregate.aggregateId) },
                        onDismiss = { viewModel.dismissItem(item.aggregate.aggregateId) }
                    )
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

/**
 * Empty state shown when no items need review.
 */
@Composable
private fun EmptyReviewState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = "All clear",
                tint = PositiveGreen,
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "All clear!",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "No items need review.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Card for a single review queue item showing transaction summary, flag reasons, and actions.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReviewItemCard(
    item: ReviewItem,
    onApprove: () -> Unit,
    onViewDetails: () -> Unit,
    onDismiss: () -> Unit
) {
    val aggregate = item.aggregate

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Top row: icon + amount/counterparty + confidence badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Direction icon
                val iconTint = when (aggregate.canonicalDirection) {
                    TransactionDirection.CREDIT -> PositiveGreen
                    TransactionDirection.DEBIT -> NegativeRed
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                val icon = when (aggregate.canonicalDirection) {
                    TransactionDirection.CREDIT -> Icons.Filled.ArrowDownward
                    TransactionDirection.DEBIT -> Icons.Filled.ArrowUpward
                    else -> Icons.Filled.SwapVert
                }
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(iconTint.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = aggregate.canonicalDirection.name,
                        tint = iconTint,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Amount and counterparty
                Column(modifier = Modifier.weight(1f)) {
                    val prefix = when (aggregate.canonicalDirection) {
                        TransactionDirection.CREDIT -> "+"
                        TransactionDirection.DEBIT -> ""
                        else -> ""
                    }
                    val amountColor = when (aggregate.canonicalDirection) {
                        TransactionDirection.CREDIT -> PositiveGreen
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                    Text(
                        text = "$prefix${formatAmount(aggregate.canonicalAmountMinor, aggregate.canonicalCurrency)}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = amountColor
                    )
                    Text(
                        text = aggregate.canonicalCounterparty
                            ?: aggregate.canonicalReference
                            ?: "Unknown",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Confidence badge and date
                Column(horizontalAlignment = Alignment.End) {
                    ConfidenceBadge(score = aggregate.confidenceScore)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = formatTimestamp(aggregate.canonicalTimestamp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Source observation count
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "${item.observations.size} source observation${if (item.observations.size != 1) "s" else ""}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Flag reason chips
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                item.flagReasons.forEach { reason ->
                    FlagReasonChip(reason = reason)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(8.dp))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onApprove,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PositiveGreen
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Approve", color = Color.White)
                }

                OutlinedButton(
                    onClick = onViewDetails,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Details")
                }

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        "Dismiss",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * Small amber/orange chip displaying a single flag reason.
 */
@Composable
private fun FlagReasonChip(reason: String) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = FlagChipBackground
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                tint = AmberOrange,
                modifier = Modifier.size(12.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = reason,
                style = MaterialTheme.typography.labelSmall,
                color = AmberOrange,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
