package com.example.ledgermesh.ui.ledger

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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

// -- Colors matching the dashboard design --
private val PositiveGreen = Color(0xFF4CAF50)
private val NegativeRed = Color(0xFFE53935)

@Composable
fun LedgerScreen(
    onTransactionClick: (String) -> Unit = {},
    viewModel: LedgerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        // Title
        Text(
            text = "Ledger",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)
        )

        // Filter chips row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = uiState.accountFilter != null,
                onClick = { viewModel.showAccountPicker() },
                label = { Text(uiState.accountChipLabel) }
            )

            FilterChip(
                selected = uiState.monthFilter !is MonthFilter.AllTime,
                onClick = { viewModel.showMonthPicker() },
                label = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(uiState.monthChipLabel)
                        Icon(
                            imageVector = Icons.Filled.ArrowDropDown,
                            contentDescription = "Select month",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            )

            Spacer(modifier = Modifier.weight(1f))

            IconButton(
                onClick = { viewModel.showAdvancedFilters() },
                colors = if (uiState.hasActiveAdvancedFilter) {
                    IconButtonDefaults.iconButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                } else {
                    IconButtonDefaults.iconButtonColors()
                }
            ) {
                Icon(
                    imageVector = Icons.Filled.FilterList,
                    contentDescription = "Advanced filters"
                )
            }
        }

        // Search bar
        OutlinedTextField(
            value = uiState.searchQuery,
            onValueChange = { viewModel.updateSearchQuery(it) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text("Search transactions...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
            singleLine = true
        )

        if (uiState.transactions.isEmpty() && !uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = emptyStateMessage(uiState),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            uiState.transactions.forEach { (dateGroup, transactions) ->
                // Smart date group header
                item(key = "header_$dateGroup") {
                    Text(
                        text = dateGroup,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                    )
                }

                items(transactions, key = { it.aggregateId }) { aggregate ->
                    LedgerTransactionRow(
                        aggregate = aggregate,
                        onClick = { onTransactionClick(aggregate.aggregateId) }
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 52.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }

    // -- Dialogs / Sheets --

    if (uiState.showAccountPicker) {
        AccountPickerDialog(
            availableAccounts = uiState.availableAccounts,
            selectedAccount = uiState.accountFilter,
            onSelect = { viewModel.updateAccountFilter(it) },
            onDismiss = { viewModel.dismissAccountPicker() }
        )
    }

    if (uiState.showMonthPicker) {
        MonthPickerDialog(
            selectedMonth = uiState.monthFilter,
            onSelect = { viewModel.updateMonthFilter(it) },
            onDismiss = { viewModel.dismissMonthPicker() }
        )
    }

    if (uiState.showAdvancedFilters) {
        AdvancedFilterSheet(
            currentFilters = uiState.advancedFilters,
            onApply = { viewModel.updateAdvancedFilters(it) },
            onDismiss = { viewModel.dismissAdvancedFilters() }
        )
    }
}

private fun emptyStateMessage(state: LedgerUiState): String {
    if (state.searchQuery.isNotBlank()) {
        return "No matches for \"${state.searchQuery}\""
    }
    if (state.accountFilter != null) {
        return "No transactions for ${state.accountFilter}"
    }
    if (state.monthFilter is MonthFilter.SpecificMonth) {
        return "No transactions in ${state.monthChipLabel}"
    }
    if (state.hasActiveAdvancedFilter) {
        return "No transactions match the current filters"
    }
    return "No transactions yet"
}

/**
 * List-style transaction row for the Ledger screen matching the mockup:
 * [icon] [amount + counterparty] ... [confidence badge + date]
 */
@Composable
fun LedgerTransactionRow(aggregate: AggregateEntity, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Circular icon
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
}
