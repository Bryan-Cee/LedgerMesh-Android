package com.example.ledgermesh.domain.usecase

import com.example.ledgermesh.data.db.entity.AggregateEntity
import com.example.ledgermesh.data.db.entity.OpsLogEntity
import com.example.ledgermesh.data.repository.AggregateRepository
import com.example.ledgermesh.data.repository.ObservationRepository
import com.example.ledgermesh.data.repository.OpsLogRepository
import com.example.ledgermesh.domain.model.OperationType
import com.example.ledgermesh.domain.model.TransactionDirection
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provides manual transaction operations (merge, split, mark duplicate, edit)
 * with full audit logging via [OpsLogEntity].
 *
 * Every mutation records an immutable ops-log entry so that the user can
 * review (and eventually undo) their manual adjustments.
 */
@Singleton
class TransactionOperationsUseCase @Inject constructor(
    private val aggregateRepository: AggregateRepository,
    private val observationRepository: ObservationRepository,
    private val opsLogRepository: OpsLogRepository,
    private val reconciliationEngine: ReconciliationEngine
) {

    /**
     * Force-merge two aggregates into one.
     *
     * All observations from [sourceAggregateId] are moved to [targetAggregateId].
     * The source aggregate is deleted after the merge. The target aggregate's
     * canonical projection is recomputed, preserving any user-edited fields
     * (category, notes) from the target.
     *
     * @param targetAggregateId the surviving aggregate
     * @param sourceAggregateId the aggregate to absorb and delete
     * @return the updated target [AggregateEntity]
     */
    suspend fun forceMerge(targetAggregateId: String, sourceAggregateId: String): AggregateEntity {
        // Get all observations from the source
        val sourceObservations = observationRepository.getObservationsForAggregateOnce(sourceAggregateId)

        // Move each observation to the target
        for (obs in sourceObservations) {
            aggregateRepository.unlinkObservation(sourceAggregateId, obs.observationId)
            aggregateRepository.linkObservation(targetAggregateId, obs.observationId)
        }

        // Delete the now-empty source aggregate
        val sourceAggregate = aggregateRepository.getById(sourceAggregateId)
        if (sourceAggregate != null) {
            aggregateRepository.delete(sourceAggregate)
        }

        // Recompute canonical projection for target
        val allObservations = observationRepository.getObservationsForAggregateOnce(targetAggregateId)
        val updated = reconciliationEngine.computeCanonicalProjection(targetAggregateId, allObservations)

        // Preserve user-edited fields (category, notes) from the target
        val existingTarget = aggregateRepository.getById(targetAggregateId)
        val finalAggregate = updated.copy(
            categoryId = existingTarget?.categoryId ?: updated.categoryId,
            userNotes = existingTarget?.userNotes ?: updated.userNotes
        )
        aggregateRepository.update(finalAggregate)

        // Record operation
        opsLogRepository.insert(
            OpsLogEntity(
                operationType = OperationType.MERGE,
                targetAggregateId = targetAggregateId,
                secondaryAggregateId = sourceAggregateId,
                affectedObservationIds = sourceObservations.joinToString(",") { it.observationId }
            )
        )

        return finalAggregate
    }

    /**
     * Split one or more observations out of an aggregate into a new aggregate.
     *
     * The selected observations are detached from the source and a brand-new
     * aggregate is created for them. Both aggregates are recomputed.
     * User-edited fields on the source aggregate are preserved.
     *
     * @param sourceAggregateId the aggregate to split from
     * @param observationIdsToSplit observation IDs to move into the new aggregate
     * @return a [Pair] of (updated source aggregate, newly created aggregate)
     * @throws IllegalArgumentException if the split list is empty, would leave the
     *         source empty, or none of the IDs belong to the source aggregate
     */
    suspend fun split(
        sourceAggregateId: String,
        observationIdsToSplit: List<String>
    ): Pair<AggregateEntity, AggregateEntity> {
        require(observationIdsToSplit.isNotEmpty()) { "Must specify at least one observation to split" }

        val allObservations = observationRepository.getObservationsForAggregateOnce(sourceAggregateId)
        val splitIdSet = observationIdsToSplit.toSet()
        val remaining = allObservations.filter { it.observationId !in splitIdSet }
        require(remaining.isNotEmpty()) { "Cannot split all observations - at least one must remain" }

        val splitObs = allObservations.filter { it.observationId in splitIdSet }
        require(splitObs.isNotEmpty()) { "None of the specified observations belong to this aggregate" }

        // Create new aggregate for the split observations
        val newAggregateId = UUID.randomUUID().toString()
        val newAggregate = reconciliationEngine.computeCanonicalProjection(newAggregateId, splitObs)
        aggregateRepository.insert(newAggregate)

        // Move observations to new aggregate
        for (obs in splitObs) {
            aggregateRepository.unlinkObservation(sourceAggregateId, obs.observationId)
            aggregateRepository.linkObservation(newAggregate.aggregateId, obs.observationId)
        }

        // Recompute source aggregate, preserving user-edited fields
        val updatedSource = reconciliationEngine.computeCanonicalProjection(sourceAggregateId, remaining)
        val existingSource = aggregateRepository.getById(sourceAggregateId)
        val finalSource = updatedSource.copy(
            categoryId = existingSource?.categoryId,
            userNotes = existingSource?.userNotes
        )
        aggregateRepository.update(finalSource)

        // Record operation
        opsLogRepository.insert(
            OpsLogEntity(
                operationType = OperationType.SPLIT,
                targetAggregateId = sourceAggregateId,
                secondaryAggregateId = newAggregate.aggregateId,
                affectedObservationIds = observationIdsToSplit.joinToString(",")
            )
        )

        return Pair(finalSource, newAggregate)
    }

    /**
     * Mark an observation as a duplicate within an aggregate.
     *
     * The observation stays linked but the action is recorded in the ops log
     * for audit purposes. A future "resolve duplicates" pass can use these
     * entries to surface or auto-unlink duplicates.
     *
     * @param aggregateId the aggregate containing the observation
     * @param observationId the observation to flag as duplicate
     */
    suspend fun markDuplicate(aggregateId: String, observationId: String) {
        opsLogRepository.insert(
            OpsLogEntity(
                operationType = OperationType.MARK_DUPLICATE,
                targetAggregateId = aggregateId,
                affectedObservationIds = observationId
            )
        )
    }

    /**
     * Edit a canonical field on an aggregate with last-write-wins (LWW) semantics.
     *
     * Supported fields: `categoryId`, `userNotes`, `canonicalCounterparty`,
     * `canonicalDirection`. Unsupported field names are silently ignored.
     *
     * @param aggregateId the aggregate to edit
     * @param fieldName the canonical field name to change
     * @param oldValue the previous value (recorded for audit, not enforced)
     * @param newValue the new value to set
     */
    suspend fun editField(
        aggregateId: String,
        fieldName: String,
        oldValue: String?,
        newValue: String?
    ) {
        val aggregate = aggregateRepository.getById(aggregateId) ?: return

        val now = Instant.now().toEpochMilli()
        val updated = when (fieldName) {
            "categoryId" -> aggregate.copy(categoryId = newValue, updatedAt = now)
            "userNotes" -> aggregate.copy(userNotes = newValue, updatedAt = now)
            "canonicalCounterparty" -> aggregate.copy(canonicalCounterparty = newValue, updatedAt = now)
            "canonicalDirection" -> {
                val direction = try {
                    TransactionDirection.valueOf(newValue ?: "UNKNOWN")
                } catch (_: IllegalArgumentException) {
                    TransactionDirection.UNKNOWN
                }
                aggregate.copy(canonicalDirection = direction, updatedAt = now)
            }
            else -> return // unsupported field, no-op
        }

        aggregateRepository.update(updated)

        opsLogRepository.insert(
            OpsLogEntity(
                operationType = OperationType.EDIT_FIELD,
                targetAggregateId = aggregateId,
                fieldName = fieldName,
                oldValue = oldValue,
                newValue = newValue
            )
        )
    }

    /**
     * Retrieve the full operation history for a given aggregate as a reactive [Flow].
     *
     * @param aggregateId the aggregate whose history to fetch
     * @return a [Flow] emitting the list of [OpsLogEntity] entries
     */
    fun getOpsHistory(aggregateId: String) = opsLogRepository.getForAggregate(aggregateId)
}
