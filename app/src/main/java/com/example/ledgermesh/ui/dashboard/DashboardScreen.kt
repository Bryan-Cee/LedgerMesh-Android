package com.example.ledgermesh.ui.dashboard

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
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
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Currency
import java.util.Locale
import kotlin.math.abs

// -- Colors used in the dashboard design --
private val HeroCardBackground = Color(0xFF1B2838)
private val PositiveGreen = Color(0xFF4CAF50)
private val NegativeRed = Color(0xFFE53935)
private val AmberYellow = Color(0xFFFFC107)

@Composable
fun DashboardScreen(
    onTransactionClick: (String) -> Unit = {},
    onNavigateToReview: () -> Unit = {},
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Title
        item {
            Text(
                text = "LedgerMesh",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // Hero balance card
        item {
            HeroBalanceCard(
                netBalance = uiState.netBalance,
                income = uiState.monthlyIncome,
                expense = uiState.monthlyExpense,
                trendPercentage = uiState.trendPercentage,
                currency = uiState.currency
            )
        }

        // Review queue banner (only shown when items need review)
        if (uiState.reviewCount > 0) {
            item {
                ReviewQueueBanner(
                    count = uiState.reviewCount,
                    onClick = onNavigateToReview
                )
            }
        }

        // Categories section
        if (uiState.categories.isNotEmpty()) {
            item {
                Text(
                    text = "Categories",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.categories) { chip ->
                        CategoryChipCard(chip = chip, currency = uiState.currency)
                    }
                }
            }
        }

        // Recent Transactions header
        item {
            Text(
                text = "Recent Transactions",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }

        if (uiState.recentTransactions.isEmpty() && !uiState.isLoading) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Text(
                        text = "No transactions yet. Import a CSV or scan SMS to get started.",
                        modifier = Modifier.padding(24.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        items(uiState.recentTransactions, key = { it.aggregateId }) { aggregate ->
            TransactionRow(
                aggregate = aggregate,
                onClick = { onTransactionClick(aggregate.aggregateId) }
            )
        }
    }
}

/**
 * Clickable banner card prompting the user to review low-confidence transactions.
 * Displayed on the dashboard when [count] > 0.
 */
@Composable
private fun ReviewQueueBanner(count: Int, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = AmberYellow.copy(alpha = 0.12f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                tint = AmberYellow,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Review Queue",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "$count item${if (count != 1) "s" else ""} need attention",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "Open review queue",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * Hero card showing net balance, month-over-month trend, and income/expense breakdown.
 */
@Composable
private fun HeroBalanceCard(
    netBalance: Long,
    income: Long,
    expense: Long,
    trendPercentage: Double?,
    currency: String
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
            // Net balance
            Text(
                text = formatAmount(netBalance, currency),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "THIS MONTH",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.7f)
            )

            // Trend indicator
            if (trendPercentage != null) {
                Spacer(modifier = Modifier.height(8.dp))
                val isPositive = trendPercentage >= 0
                val trendColor = if (isPositive) PositiveGreen else NegativeRed
                val trendIcon = if (isPositive) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward
                val sign = if (isPositive) "+" else ""

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = trendIcon,
                        contentDescription = if (isPositive) "Trending up" else "Trending down",
                        tint = trendColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "$sign${"%.1f".format(abs(trendPercentage))}% vs last month",
                        style = MaterialTheme.typography.bodySmall,
                        color = trendColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            HorizontalDivider(color = Color.White.copy(alpha = 0.2f))

            Spacer(modifier = Modifier.height(16.dp))

            // Income and Expense row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Income",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "+${formatAmount(income, currency)}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = PositiveGreen
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Expenses",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "-${formatAmount(expense, currency)}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = NegativeRed
                    )
                }
            }
        }
    }
}

/**
 * Outlined card chip for a spending/income category.
 */
@Composable
private fun CategoryChipCard(chip: CategoryChip, currency: String) {
    OutlinedCard {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                text = chip.name,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            val prefix = if (chip.isIncome) "+" else ""
            val color = if (chip.isIncome) PositiveGreen else MaterialTheme.colorScheme.onSurface
            Text(
                text = "$prefix${formatAmount(chip.amount, currency)}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Transaction row matching the mockup design:
 * [icon] [amount + counterparty] ... [confidence badge + date]
 */
@Composable
fun TransactionRow(aggregate: AggregateEntity, onClick: () -> Unit) {
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

/**
 * Colored pill badge showing reconciliation confidence as a text label.
 *
 * - score >= 75: "High" in green
 * - score >= 40: "Medium" in amber
 * - score < 40: "Low" in red
 */
@Composable
fun ConfidenceBadge(score: Int) {
    val (label, color) = when {
        score >= 75 -> "High" to PositiveGreen
        score >= 40 -> "Medium" to AmberYellow
        else -> "Low" to NegativeRed
    }
    Surface(
        shape = MaterialTheme.shapes.small,
        color = color.copy(alpha = 0.12f)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = color
        )
    }
}

/**
 * Formats a minor-unit amount (e.g. cents) into a locale-aware currency string.
 */
fun formatAmount(amountMinor: Long, currency: String): String {
    val formatter = NumberFormat.getCurrencyInstance(Locale.getDefault())
    try {
        formatter.currency = Currency.getInstance(currency)
    } catch (_: Exception) {
        // Fall back to default currency if the code is not recognized
    }
    return formatter.format(amountMinor / 100.0)
}

/**
 * Formats an epoch-millis timestamp into a human-readable date string.
 */
fun formatTimestamp(epochMillis: Long?): String {
    if (epochMillis == null) return "Unknown date"
    val instant = Instant.ofEpochMilli(epochMillis)
    val formatter = DateTimeFormatter.ofPattern("MMM dd, yyyy")
        .withZone(ZoneId.systemDefault())
    return formatter.format(instant)
}
