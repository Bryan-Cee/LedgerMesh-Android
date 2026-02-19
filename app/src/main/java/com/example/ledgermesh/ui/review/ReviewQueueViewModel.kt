package com.example.ledgermesh.ui.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ledgermesh.data.AppPreferences
import com.example.ledgermesh.data.db.entity.AggregateEntity
import com.example.ledgermesh.data.db.entity.ObservationEntity
import com.example.ledgermesh.data.repository.AggregateRepository
import com.example.ledgermesh.data.repository.ObservationRepository
import com.example.ledgermesh.domain.model.TransactionDirection
import com.example.ledgermesh.domain.usecase.TransactionOperationsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * A low-confidence aggregate bundled with its source observations and
 * the human-readable reasons it was flagged for manual review.
 */
data class ReviewItem(
    val aggregate: AggregateEntity,
    val observations: List<ObservationEntity>,
    val flagReasons: List<String>
)

/**
 * UI state for the Review Queue screen.
 *
 * @property items Low-confidence aggregates awaiting manual review.
 * @property isLoading True while the initial data load is in progress.
 * @property confidenceThreshold Aggregates at or below this score appear in the queue.
 * @property message One-shot user-facing feedback (snackbar text).
 */
data class ReviewQueueUiState(
    val items: List<ReviewItem> = emptyList(),
    val isLoading: Boolean = true,
    val confidenceThreshold: Int = 75,
    val message: String? = null
)

/**
 * ViewModel backing the Review Queue screen.
 *
 * Loads all aggregates whose [AggregateEntity.confidenceScore] falls at or below
 * [ReviewQueueUiState.confidenceThreshold], enriches each with its linked
 * observations and flag reasons, and exposes manual approve / dismiss actions.
 */
@HiltViewModel
class ReviewQueueViewModel @Inject constructor(
    private val aggregateRepository: AggregateRepository,
    private val observationRepository: ObservationRepository,
    private val transactionOps: TransactionOperationsUseCase,
    private val appPreferences: AppPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        ReviewQueueUiState(confidenceThreshold = appPreferences.confidenceThreshold)
    )
    val uiState: StateFlow<ReviewQueueUiState> = _uiState.asStateFlow()

    init {
        loadReviewItems()
    }

    private fun loadReviewItems() {
        viewModelScope.launch {
            aggregateRepository.getLowConfidenceFlow(_uiState.value.confidenceThreshold)
                .collect { aggregates ->
                    val items = aggregates.map { agg ->
                        val obs = observationRepository.getObservationsForAggregateOnce(agg.aggregateId)
                        val reasons = buildFlagReasons(agg, obs)
                        ReviewItem(agg, obs, reasons)
                    }
                    _uiState.update { it.copy(items = items, isLoading = false) }
                }
        }
    }

    /**
     * Derive human-readable flag reasons from aggregate + observation data.
     */
    private fun buildFlagReasons(
        aggregate: AggregateEntity,
        observations: List<ObservationEntity>
    ): List<String> {
        val reasons = mutableListOf<String>()

        // Single source observation
        if (observations.size == 1) {
            reasons.add("Only one source observation")
        }

        // Mixed direction signals
        if (aggregate.canonicalDirection == TransactionDirection.MIXED) {
            reasons.add("Mixed debit/credit signals")
        }

        // Low parse confidence
        if (observations.isNotEmpty()) {
            val avgParse = observations.map { it.parseConfidence }.average()
            if (avgParse < 0.7) {
                reasons.add("Low parse confidence (${(avgParse * 100).toInt()}%)")
            }
        }

        // Amount disagreement across source observations
        val amounts = observations.map { it.amountMinor }.distinct()
        if (amounts.size > 1) {
            reasons.add("Amount disagreement across sources")
        }

        // No transaction reference found in any observation
        val refs = observations.mapNotNull { it.reference }.filter { it.isNotBlank() }
        if (refs.isEmpty()) {
            reasons.add("No transaction reference found")
        }

        // Timestamp spread exceeds 1 hour
        val timestamps = observations.mapNotNull { it.timestamp }
        if (timestamps.size >= 2) {
            val spread = timestamps.max() - timestamps.min()
            if (spread > 3_600_000L) { // > 1 hour
                reasons.add("Timestamp spread > 1 hour")
            }
        }

        // Fallback when no specific reason was identified
        if (reasons.isEmpty()) {
            reasons.add("Below confidence threshold")
        }

        return reasons
    }

    /**
     * Approve an item -- adds a "reviewed and approved" user note.
     * In a future iteration this could boost the confidence score directly.
     */
    fun approveItem(aggregateId: String) {
        viewModelScope.launch {
            try {
                transactionOps.editField(
                    aggregateId,
                    "userNotes",
                    null,
                    "Manually reviewed and approved"
                )
                _uiState.update { it.copy(message = "Transaction approved") }
            } catch (e: Exception) {
                _uiState.update { it.copy(message = "Error: ${e.message}") }
            }
        }
    }

    /**
     * Dismiss an item from the queue -- the user confirms it is correct as-is.
     */
    fun dismissItem(aggregateId: String) {
        viewModelScope.launch {
            try {
                transactionOps.editField(
                    aggregateId,
                    "userNotes",
                    null,
                    "Reviewed - no action needed"
                )
                _uiState.update { it.copy(message = "Item dismissed") }
            } catch (e: Exception) {
                _uiState.update { it.copy(message = "Error: ${e.message}") }
            }
        }
    }

    /** Clear the one-shot snackbar message after it has been shown. */
    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }
}
