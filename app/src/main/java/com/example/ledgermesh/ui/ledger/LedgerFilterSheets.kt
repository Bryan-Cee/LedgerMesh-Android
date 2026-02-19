package com.example.ledgermesh.ui.ledger

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.time.YearMonth
import java.time.format.DateTimeFormatter

@Composable
fun AccountPickerDialog(
    availableAccounts: List<String>,
    selectedAccount: String?,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Account") },
        text = {
            LazyColumn {
                item(key = "all") {
                    ListItem(
                        headlineContent = { Text("All Accounts") },
                        trailingContent = {
                            if (selectedAccount == null) {
                                Icon(Icons.Default.Check, contentDescription = "Selected")
                            }
                        },
                        modifier = Modifier.clickable {
                            onSelect(null)
                            onDismiss()
                        }
                    )
                }
                items(availableAccounts, key = { it }) { account ->
                    ListItem(
                        headlineContent = { Text(account) },
                        trailingContent = {
                            if (account == selectedAccount) {
                                Icon(Icons.Default.Check, contentDescription = "Selected")
                            }
                        },
                        modifier = Modifier.clickable {
                            onSelect(account)
                            onDismiss()
                        }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun MonthPickerDialog(
    selectedMonth: MonthFilter,
    onSelect: (MonthFilter) -> Unit,
    onDismiss: () -> Unit
) {
    val formatter = DateTimeFormatter.ofPattern("MMM yyyy")
    val current = YearMonth.now()
    val months = (0L..12L).map { current.minusMonths(it) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Period") },
        text = {
            LazyColumn {
                item(key = "all_time") {
                    ListItem(
                        headlineContent = { Text("All Time") },
                        trailingContent = {
                            if (selectedMonth is MonthFilter.AllTime) {
                                Icon(Icons.Default.Check, contentDescription = "Selected")
                            }
                        },
                        modifier = Modifier.clickable {
                            onSelect(MonthFilter.AllTime)
                            onDismiss()
                        }
                    )
                }
                items(months, key = { it.toString() }) { ym ->
                    val label = ym.atDay(1).format(formatter)
                    val isSelected = selectedMonth is MonthFilter.SpecificMonth &&
                            selectedMonth.yearMonth == ym
                    ListItem(
                        headlineContent = { Text(label) },
                        trailingContent = {
                            if (isSelected) {
                                Icon(Icons.Default.Check, contentDescription = "Selected")
                            }
                        },
                        modifier = Modifier.clickable {
                            onSelect(MonthFilter.SpecificMonth(ym))
                            onDismiss()
                        }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AdvancedFilterSheet(
    currentFilters: AdvancedFilters,
    onApply: (AdvancedFilters) -> Unit,
    onDismiss: () -> Unit
) {
    var draft by remember(currentFilters) { mutableStateOf(currentFilters) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "Advanced Filters",
                style = MaterialTheme.typography.titleLarge
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Direction filter
            Text(
                text = "Direction",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DirectionFilter.entries.forEach { dir ->
                    FilterChip(
                        selected = draft.direction == dir,
                        onClick = { draft = draft.copy(direction = dir) },
                        label = {
                            Text(
                                when (dir) {
                                    DirectionFilter.ALL -> "All"
                                    DirectionFilter.CREDITS_ONLY -> "Credits"
                                    DirectionFilter.DEBITS_ONLY -> "Debits"
                                }
                            )
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Amount range
            Text(
                text = "Amount Range",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = draft.minAmount,
                    onValueChange = { draft = draft.copy(minAmount = it) },
                    label = { Text("Min") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = draft.maxAmount,
                    onValueChange = { draft = draft.copy(maxAmount = it) },
                    label = { Text("Max") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Low confidence toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Low confidence only",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = "Show items scoring below 40",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = draft.lowConfidenceOnly,
                    onCheckedChange = { draft = draft.copy(lowConfidenceOnly = it) }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        onApply(AdvancedFilters())
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Clear")
                }
                Button(
                    onClick = {
                        onApply(draft)
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Apply")
                }
            }
        }
    }
}
