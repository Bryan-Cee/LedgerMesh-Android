package com.example.ledgermesh.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ledgermesh.data.db.entity.AggregateEntity
import com.example.ledgermesh.data.db.entity.ObservationEntity
import com.example.ledgermesh.data.db.entity.OpsLogEntity
import com.example.ledgermesh.data.repository.AggregateRepository
import com.example.ledgermesh.data.repository.ObservationRepository
import com.example.ledgermesh.domain.usecase.TransactionOperationsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * UI state for the Transaction Detail screen.
 */
data class TransactionDetailUiState(
    val aggregate: AggregateEntity? = null,
    val observations: List<ObservationEntity> = emptyList(),
    val opsHistory: List<OpsLogEntity> = emptyList(),
    val isEditing: Boolean = false,
    val editNotes: String = "",
    val editCounterparty: String = "",
    val showSplitDialog: Boolean = false,
    val showMergeDialog: Boolean = false,
    val selectedObservationIds: Set<String> = emptySet(),
    val message: String? = null
)

/**
 * ViewModel for the Transaction Detail screen.
 *
 * Loads the aggregate, its linked observations, and the operation history
 * as reactive flows. Provides edit, split, and duplicate-marking actions
 * that delegate to [TransactionOperationsUseCase].
 */
@HiltViewModel
class TransactionDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val aggregateRepository: AggregateRepository,
    private val observationRepository: ObservationRepository,
    private val transactionOps: TransactionOperationsUseCase
) : ViewModel() {

    private val aggregateId: String = savedStateHandle.get<String>("aggregateId") ?: ""

    private val _uiState = MutableStateFlow(TransactionDetailUiState())
    val uiState: StateFlow<TransactionDetailUiState> = _uiState.asStateFlow()

    init {
        // Collect aggregate updates
        viewModelScope.launch {
            aggregateRepository.getByIdFlow(aggregateId).collect { agg ->
                _uiState.update {
                    it.copy(
                        aggregate = agg,
                        editNotes = if (!it.isEditing) agg?.userNotes.orEmpty() else it.editNotes,
                        editCounterparty = if (!it.isEditing) {
                            agg?.canonicalCounterparty.orEmpty()
                        } else {
                            it.editCounterparty
                        }
                    )
                }
            }
        }
        // Collect linked observations
        viewModelScope.launch {
            observationRepository.getObservationsForAggregate(aggregateId).collect { obs ->
                _uiState.update { it.copy(observations = obs) }
            }
        }
        // Collect operation history
        viewModelScope.launch {
            transactionOps.getOpsHistory(aggregateId).collect { ops ->
                _uiState.update { it.copy(opsHistory = ops) }
            }
        }
    }

    /** Enter edit mode for counterparty and notes fields. */
    fun startEditing() {
        _uiState.update { it.copy(isEditing = true) }
    }

    /** Cancel editing and restore original values from the aggregate. */
    fun cancelEditing() {
        val agg = _uiState.value.aggregate
        _uiState.update {
            it.copy(
                isEditing = false,
                editNotes = agg?.userNotes.orEmpty(),
                editCounterparty = agg?.canonicalCounterparty.orEmpty()
            )
        }
    }

    fun updateEditNotes(notes: String) {
        _uiState.update { it.copy(editNotes = notes) }
    }

    fun updateEditCounterparty(counterparty: String) {
        _uiState.update { it.copy(editCounterparty = counterparty) }
    }

    /** Persist edits via [TransactionOperationsUseCase.editField] with audit logging. */
    fun saveEdits() {
        val state = _uiState.value
        val agg = state.aggregate ?: return

        viewModelScope.launch {
            if (state.editNotes != agg.userNotes.orEmpty()) {
                transactionOps.editField(
                    aggregateId,
                    "userNotes",
                    agg.userNotes,
                    state.editNotes.ifBlank { null }
                )
            }
            if (state.editCounterparty != agg.canonicalCounterparty.orEmpty()) {
                transactionOps.editField(
                    aggregateId,
                    "canonicalCounterparty",
                    agg.canonicalCounterparty,
                    state.editCounterparty.ifBlank { null }
                )
            }
            _uiState.update { it.copy(isEditing = false, message = "Changes saved") }
        }
    }

    /** Toggle observation selection for the split dialog. */
    fun toggleObservationSelection(observationId: String) {
        _uiState.update {
            val current = it.selectedObservationIds
            val updated = if (observationId in current) current - observationId else current + observationId
            it.copy(selectedObservationIds = updated)
        }
    }

    fun showSplitDialog() {
        _uiState.update { it.copy(showSplitDialog = true) }
    }

    fun dismissSplitDialog() {
        _uiState.update { it.copy(showSplitDialog = false, selectedObservationIds = emptySet()) }
    }

    /** Execute a split, moving selected observations into a new aggregate. */
    fun confirmSplit() {
        val selected = _uiState.value.selectedObservationIds.toList()
        if (selected.isEmpty()) return

        viewModelScope.launch {
            try {
                transactionOps.split(aggregateId, selected)
                _uiState.update {
                    it.copy(
                        showSplitDialog = false,
                        selectedObservationIds = emptySet(),
                        message = "Split complete"
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(message = "Split failed: ${e.message}") }
            }
        }
    }

    /** Mark a single observation as a duplicate within this aggregate. */
    fun markDuplicate(observationId: String) {
        viewModelScope.launch {
            try {
                transactionOps.markDuplicate(aggregateId, observationId)
                _uiState.update { it.copy(message = "Marked as duplicate") }
            } catch (e: Exception) {
                _uiState.update { it.copy(message = "Failed: ${e.message}") }
            }
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }
}
