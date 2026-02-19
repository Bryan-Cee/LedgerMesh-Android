package com.example.ledgermesh.ui.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

data class MonthlyData(
    val month: String, // "Jan", "Feb" etc
    val income: Long,
    val expense: Long
)

data class CategoryBreakdown(
    val category: String,
    val amount: Long,
    val percentage: Float
)

data class AnalyticsUiState(
    val monthlyData: List<MonthlyData> = emptyList(),
    val categoryBreakdown: List<CategoryBreakdown> = emptyList(),
    val totalIncome: Long = 0,
    val totalExpense: Long = 0,
    val currency: String = "KES",
    val isLoading: Boolean = true
)

@HiltViewModel
class AnalyticsViewModel @Inject constructor(
    private val aggregateRepository: AggregateRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AnalyticsUiState())
    val uiState: StateFlow<AnalyticsUiState> = _uiState.asStateFlow()

    init {
        loadAnalytics()
    }

    fun loadAnalytics() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            // Compute last 6 months of data
            val now = LocalDate.now()
            val monthlyData = mutableListOf<MonthlyData>()
            var totalIncome = 0L
            var totalExpense = 0L

            for (i in 5 downTo 0) {
                val month = now.minusMonths(i.toLong())
                val start = month.withDayOfMonth(1)
                    .atStartOfDay(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli()
                val end = month.plusMonths(1).withDayOfMonth(1)
                    .atStartOfDay(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli() - 1

                val income = aggregateRepository.totalIncomeForPeriod(start, end)
                val expense = aggregateRepository.totalExpenseForPeriod(start, end)

                monthlyData.add(
                    MonthlyData(
                        month = month.month.name.take(3),
                        income = income,
                        expense = expense
                    )
                )
                totalIncome += income
                totalExpense += expense
            }

            _uiState.update {
                it.copy(
                    monthlyData = monthlyData,
                    totalIncome = totalIncome,
                    totalExpense = totalExpense,
                    isLoading = false
                )
            }
        }

        // Category breakdown from all transactions
        viewModelScope.launch {
            aggregateRepository.getAllFlow().collect { aggregates ->
                val debits = aggregates.filter {
                    it.canonicalDirection == TransactionDirection.DEBIT
                }
                val totalDebit = debits.sumOf { it.canonicalAmountMinor }

                val breakdown = debits
                    .groupBy { it.canonicalCounterparty ?: "Uncategorized" }
                    .map { (category, txns) ->
                        val amount = txns.sumOf { it.canonicalAmountMinor }
                        CategoryBreakdown(
                            category = category,
                            amount = amount,
                            percentage = if (totalDebit > 0) {
                                (amount.toFloat() / totalDebit) * 100f
                            } else {
                                0f
                            }
                        )
                    }
                    .sortedByDescending { it.amount }
                    .take(10)

                _uiState.update { it.copy(categoryBreakdown = breakdown) }
            }
        }
    }
}
