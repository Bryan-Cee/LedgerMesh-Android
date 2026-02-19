package com.example.ledgermesh.ui.ledger

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ledgermesh.data.db.entity.AggregateEntity
import com.example.ledgermesh.data.repository.AggregateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import java.util.Locale
import javax.inject.Inject

// -- Filter model types --

sealed interface MonthFilter {
    data object AllTime : MonthFilter
    data class SpecificMonth(val yearMonth: YearMonth) : MonthFilter
}

enum class DirectionFilter {
    ALL, CREDITS_ONLY, DEBITS_ONLY;

    fun toSqlString(): String? = when (this) {
        ALL -> null
        CREDITS_ONLY -> "CREDIT"
        DEBITS_ONLY -> "DEBIT"
    }
}

data class AdvancedFilters(
    val direction: DirectionFilter = DirectionFilter.ALL,
    val minAmount: String = "",
    val maxAmount: String = "",
    val lowConfidenceOnly: Boolean = false
) {
    val hasActiveFilter: Boolean
        get() = direction != DirectionFilter.ALL ||
                minAmount.isNotBlank() ||
                maxAmount.isNotBlank() ||
                lowConfidenceOnly
}

private data class FilterParams(
    val searchQuery: String?,
    val accountFilter: String?,
    val monthFilter: MonthFilter,
    val advancedFilters: AdvancedFilters
)

fun MonthFilter.toMillisRange(): Pair<Long?, Long?> = when (this) {
    is MonthFilter.AllTime -> null to null
    is MonthFilter.SpecificMonth -> {
        val start = yearMonth.atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
        val end = yearMonth.atEndOfMonth().plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli() - 1
        start to end
    }
}

data class LedgerUiState(
    val transactions: Map<String, List<AggregateEntity>> = emptyMap(),
    val searchQuery: String = "",
    val accountFilter: String? = null,
    val monthFilter: MonthFilter = MonthFilter.AllTime,
    val advancedFilters: AdvancedFilters = AdvancedFilters(),
    val availableAccounts: List<String> = emptyList(),
    val showAccountPicker: Boolean = false,
    val showMonthPicker: Boolean = false,
    val showAdvancedFilters: Boolean = false,
    val isLoading: Boolean = true
) {
    val accountChipLabel: String
        get() = accountFilter ?: "All Accounts"

    val monthChipLabel: String
        get() = when (monthFilter) {
            is MonthFilter.AllTime -> "All Time"
            is MonthFilter.SpecificMonth -> {
                val fmt = DateTimeFormatter.ofPattern("MMM yyyy")
                monthFilter.yearMonth.atDay(1).format(fmt)
            }
        }

    val hasActiveAdvancedFilter: Boolean
        get() = advancedFilters.hasActiveFilter
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LedgerViewModel @Inject constructor(
    private val aggregateRepository: AggregateRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _accountFilter = MutableStateFlow<String?>(null)
    private val _monthFilter = MutableStateFlow<MonthFilter>(MonthFilter.AllTime)
    private val _advancedFilters = MutableStateFlow(AdvancedFilters())
    private val _showAccountPicker = MutableStateFlow(false)
    private val _showMonthPicker = MutableStateFlow(false)
    private val _showAdvancedFilters = MutableStateFlow(false)

    // Step 1: combine 4 filter flows into FilterParams
    private val filterParams = combine(
        _searchQuery,
        _accountFilter,
        _monthFilter,
        _advancedFilters
    ) { query, account, month, advanced ->
        FilterParams(
            searchQuery = query.ifBlank { null },
            accountFilter = account,
            monthFilter = month,
            advancedFilters = advanced
        )
    }

    // Step 2: combine FilterParams with accounts + dialog states, flatMapLatest into search
    val uiState: StateFlow<LedgerUiState> = combine(
        filterParams,
        aggregateRepository.getDistinctAccountsFlow(),
        _showAccountPicker,
        _showMonthPicker,
        _showAdvancedFilters
    ) { params, accounts, showAcct, showMonth, showAdv ->
        data class CombinedInput(
            val params: FilterParams,
            val accounts: List<String>,
            val showAcct: Boolean,
            val showMonth: Boolean,
            val showAdv: Boolean
        )
        CombinedInput(params, accounts, showAcct, showMonth, showAdv)
    }
        .flatMapLatest { input ->
            val (startMillis, endMillis) = input.params.monthFilter.toMillisRange()
            val adv = input.params.advancedFilters
            val minMinor = adv.minAmount.toDoubleOrNull()?.let { (it * 100).toLong() }
            val maxMinor = adv.maxAmount.toDoubleOrNull()?.let { (it * 100).toLong() }

            aggregateRepository.searchAggregates(
                query = input.params.searchQuery,
                accountFilter = input.params.accountFilter,
                directionFilter = adv.direction.toSqlString(),
                startMillis = startMillis,
                endMillis = endMillis,
                minAmountMinor = minMinor,
                maxAmountMinor = maxMinor,
                maxConfidence = if (adv.lowConfidenceOnly) 40 else null
            ).map { aggregates ->
                LedgerUiState(
                    transactions = groupBySmartLabel(aggregates),
                    searchQuery = _searchQuery.value,
                    accountFilter = _accountFilter.value,
                    monthFilter = _monthFilter.value,
                    advancedFilters = _advancedFilters.value,
                    availableAccounts = input.accounts,
                    showAccountPicker = input.showAcct,
                    showMonthPicker = input.showMonth,
                    showAdvancedFilters = input.showAdv,
                    isLoading = false
                )
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = LedgerUiState()
        )

    // -- Actions --

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun updateAccountFilter(account: String?) {
        _accountFilter.value = account
    }

    fun updateMonthFilter(filter: MonthFilter) {
        _monthFilter.value = filter
    }

    fun updateAdvancedFilters(filters: AdvancedFilters) {
        _advancedFilters.value = filters
    }

    fun clearAllFilters() {
        _searchQuery.value = ""
        _accountFilter.value = null
        _monthFilter.value = MonthFilter.AllTime
        _advancedFilters.value = AdvancedFilters()
    }

    fun showAccountPicker() { _showAccountPicker.value = true }
    fun dismissAccountPicker() { _showAccountPicker.value = false }
    fun showMonthPicker() { _showMonthPicker.value = true }
    fun dismissMonthPicker() { _showMonthPicker.value = false }
    fun showAdvancedFilters() { _showAdvancedFilters.value = true }
    fun dismissAdvancedFilters() { _showAdvancedFilters.value = false }

    /**
     * Groups aggregates into smart date labels: TODAY, THIS WEEK, EARLIER THIS MONTH,
     * then by month name (e.g. "JANUARY 2026") for older transactions.
     */
    private fun groupBySmartLabel(aggregates: List<AggregateEntity>): Map<String, List<AggregateEntity>> {
        val today = LocalDate.now()
        val zone = ZoneId.systemDefault()
        val weekFields = WeekFields.of(Locale.getDefault())
        val currentWeek = today.get(weekFields.weekOfWeekBasedYear())
        val currentMonth = today.monthValue
        val currentYear = today.year
        val monthYearFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())

        val result = linkedMapOf<String, MutableList<AggregateEntity>>()

        for (aggregate in aggregates) {
            val txDate = aggregate.canonicalTimestamp?.let {
                Instant.ofEpochMilli(it).atZone(zone).toLocalDate()
            }

            val label = when {
                txDate == null -> "UNKNOWN DATE"
                txDate == today -> "TODAY"
                txDate.get(weekFields.weekOfWeekBasedYear()) == currentWeek
                        && txDate.year == currentYear
                        && txDate.monthValue == currentMonth -> "THIS WEEK"
                txDate.monthValue == currentMonth && txDate.year == currentYear -> "EARLIER THIS MONTH"
                else -> txDate.format(monthYearFormatter).uppercase(Locale.getDefault())
            }

            result.getOrPut(label) { mutableListOf() }.add(aggregate)
        }

        return result
    }
}
