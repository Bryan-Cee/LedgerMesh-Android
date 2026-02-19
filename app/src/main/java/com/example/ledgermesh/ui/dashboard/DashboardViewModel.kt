package com.example.ledgermesh.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ledgermesh.data.AppPreferences
import com.example.ledgermesh.data.db.entity.AggregateEntity
import com.example.ledgermesh.data.repository.AggregateRepository
import com.example.ledgermesh.domain.model.TransactionDirection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/**
 * Represents a spending category chip on the dashboard.
 *
 * @property name Display label (counterparty or "Income").
 * @property amount Total amount in minor units for this category.
 * @property isIncome True if this represents aggregated income rather than spending.
 */
data class CategoryChip(
    val name: String,
    val amount: Long,
    val isIncome: Boolean = false
)

/**
 * UI state for the Dashboard screen.
 *
 * @property monthlyIncome Total CREDIT amount (in minor units) for the current month.
 * @property monthlyExpense Total DEBIT amount (in minor units) for the current month.
 * @property previousMonthIncome Total CREDIT amount for the previous month.
 * @property previousMonthExpense Total DEBIT amount for the previous month.
 * @property currency ISO 4217 currency code used for display formatting.
 * @property categories Category chips for the horizontal scroll row.
 * @property recentTransactions The most recent reconciled aggregates.
 * @property reviewCount Number of low-confidence aggregates awaiting manual review.
 * @property isLoading Whether the dashboard data is still loading.
 */
data class DashboardUiState(
    val monthlyIncome: Long = 0,
    val monthlyExpense: Long = 0,
    val previousMonthIncome: Long = 0,
    val previousMonthExpense: Long = 0,
    val currency: String = "KES",
    val categories: List<CategoryChip> = emptyList(),
    val recentTransactions: List<AggregateEntity> = emptyList(),
    val reviewCount: Int = 0,
    val isLoading: Boolean = true
) {
    /** Net balance for the current month (income - expense). */
    val netBalance: Long get() = monthlyIncome - monthlyExpense

    /** Previous month's net balance. */
    val previousNetBalance: Long get() = previousMonthIncome - previousMonthExpense

    /**
     * Month-over-month trend percentage based on net balance.
     * Null when there is no previous month data to compare against.
     */
    val trendPercentage: Double?
        get() {
            if (previousMonthIncome == 0L && previousMonthExpense == 0L) return null
            val prevNet = previousNetBalance
            if (prevNet == 0L) return null
            return ((netBalance - prevNet).toDouble() / kotlin.math.abs(prevNet.toDouble())) * 100.0
        }
}

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val aggregateRepository: AggregateRepository,
    private val appPreferences: AppPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        loadDashboard()
    }

    fun loadDashboard() {
        viewModelScope.launch {
            val now = LocalDate.now()

            // Current month boundaries
            val startOfMonth = now.withDayOfMonth(1)
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
            val endOfMonth = now.plusMonths(1).withDayOfMonth(1)
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli() - 1

            // Previous month boundaries
            val startOfPrevMonth = now.minusMonths(1).withDayOfMonth(1)
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
            val endOfPrevMonth = startOfMonth - 1

            val income = aggregateRepository.totalIncomeForPeriod(startOfMonth, endOfMonth)
            val expense = aggregateRepository.totalExpenseForPeriod(startOfMonth, endOfMonth)
            val prevIncome = aggregateRepository.totalIncomeForPeriod(startOfPrevMonth, endOfPrevMonth)
            val prevExpense = aggregateRepository.totalExpenseForPeriod(startOfPrevMonth, endOfPrevMonth)

            _uiState.update {
                it.copy(
                    monthlyIncome = income,
                    monthlyExpense = expense,
                    previousMonthIncome = prevIncome,
                    previousMonthExpense = prevExpense,
                    isLoading = false
                )
            }
        }

        // Category chips from current month transactions
        viewModelScope.launch {
            aggregateRepository.getAllFlow().collect { allAggregates ->
                val now = LocalDate.now()
                val startOfMonth = now.withDayOfMonth(1)
                    .atStartOfDay(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli()

                val thisMonth = allAggregates.filter { agg ->
                    (agg.canonicalTimestamp ?: 0) >= startOfMonth
                }

                val chips = mutableListOf<CategoryChip>()

                // Income chip
                val totalIncome = thisMonth
                    .filter { it.canonicalDirection == TransactionDirection.CREDIT }
                    .sumOf { it.canonicalAmountMinor }
                if (totalIncome > 0) {
                    chips.add(CategoryChip("Income", totalIncome, isIncome = true))
                }

                // Expense categories (grouped by counterparty)
                val expenseCategories = thisMonth
                    .filter { it.canonicalDirection == TransactionDirection.DEBIT }
                    .groupBy { it.canonicalCounterparty ?: "Other" }
                    .map { (name, txns) ->
                        CategoryChip(name, txns.sumOf { it.canonicalAmountMinor })
                    }
                    .sortedByDescending { it.amount }

                chips.addAll(expenseCategories)

                _uiState.update { it.copy(categories = chips) }
            }
        }

        // Recent transactions
        viewModelScope.launch {
            aggregateRepository.getRecentFlow(10).collect { recent ->
                _uiState.update { it.copy(recentTransactions = recent) }
            }
        }

        // Review queue count (low-confidence items at or below configurable threshold)
        viewModelScope.launch {
            val threshold = appPreferences.confidenceThreshold
            aggregateRepository.getLowConfidenceFlow(threshold)
                .collect { lowConfItems ->
                    _uiState.update { it.copy(reviewCount = lowConfItems.size) }
                }
        }
    }
}
