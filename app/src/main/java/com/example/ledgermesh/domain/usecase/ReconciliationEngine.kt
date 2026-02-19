package com.example.ledgermesh.domain.usecase

import com.example.ledgermesh.data.AppPreferences
import com.example.ledgermesh.data.db.entity.AggregateEntity
import com.example.ledgermesh.data.db.entity.ObservationEntity
import com.example.ledgermesh.data.repository.AggregateRepository
import com.example.ledgermesh.data.repository.ObservationRepository
import com.example.ledgermesh.domain.model.SourceType
import com.example.ledgermesh.domain.model.TransactionDirection
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * A candidate aggregate that an observation might belong to, along with
 * a numeric score indicating match strength and a human-readable reason.
 */
data class MatchCandidate(
    val aggregate: AggregateEntity,
    val score: Int,
    val reason: String
)

/**
 * Core reconciliation logic that links [ObservationEntity] records to
 * [AggregateEntity] records.
 *
 * For each unlinked observation the engine:
 * 1. Searches for existing aggregates whose linked observations share a
 *    fingerprint (reference, amount+day).
 * 2. Scores each candidate and picks the best match (if any).
 * 3. Either merges the observation into the winning aggregate and recomputes
 *    the canonical projection, or creates a brand-new aggregate.
 *
 * The algorithm is deterministic: given the same set of unlinked observations
 * processed in sorted order, it always produces the same result.
 */
@Singleton
class ReconciliationEngine @Inject constructor(
    private val observationRepository: ObservationRepository,
    private val aggregateRepository: AggregateRepository,
    private val appPreferences: AppPreferences
) {

    /**
     * Reconcile a single observation: find a matching aggregate or create a new one.
     *
     * @return the aggregateId the observation was linked to.
     */
    suspend fun reconcileObservation(observation: ObservationEntity): String {
        val candidates = findCandidates(observation)

        return if (candidates.isNotEmpty()) {
            val bestMatch = candidates.first()
            mergeIntoAggregate(bestMatch.aggregate, observation)
            bestMatch.aggregate.aggregateId
        } else {
            createNewAggregate(observation)
        }
    }

    /**
     * Reconcile all unlinked observations. Idempotent -- observations that
     * are already linked to an aggregate are skipped.
     */
    suspend fun reconcileAll() {
        val unlinked = observationRepository.getUnlinkedObservations()
        // Sort deterministically for consistent results regardless of DB order
        val sorted = unlinked.sortedBy { it.observationId }
        for (obs in sorted) {
            reconcileObservation(obs)
        }
    }

    /**
     * Find candidate aggregates that an observation might belong to,
     * sorted by match quality (best first).
     */
    private suspend fun findCandidates(observation: ObservationEntity): List<MatchCandidate> {
        val candidates = mutableMapOf<String, MatchCandidate>()
        val toleranceCents = appPreferences.amountToleranceCents.toLong()
        val timeWindowMillis = appPreferences.timeWindowHours.toLong() * 60 * 60 * 1000L

        // 1. Reference match (strongest signal)
        observation.fpRef?.let { fpRef ->
            val refMatches = aggregateRepository.findAggregatesByFpRef(fpRef)
            for (agg in refMatches) {
                if (agg.canonicalCurrency == observation.currency) {
                    val amountDiff = abs(agg.canonicalAmountMinor - observation.amountMinor)
                    val exactMatch = amountDiff == 0L
                    val withinTolerance = amountDiff <= toleranceCents
                    val score = when {
                        exactMatch -> 100
                        withinTolerance -> 85
                        else -> 80
                    }
                    candidates[agg.aggregateId] = MatchCandidate(agg, score, "reference_match")
                }
            }
        }

        // 2. Amount + day match
        observation.fpAmtDay?.let { fpAmtDay ->
            val dayMatches = aggregateRepository.findAggregatesByFpAmtDay(fpAmtDay)
            for (agg in dayMatches) {
                if (agg.canonicalCurrency == observation.currency &&
                    agg.aggregateId !in candidates
                ) {
                    // Check direction compatibility
                    if (isDirectionCompatible(agg.canonicalDirection, observation.direction)) {
                        // Check timestamp proximity (within configurable time window)
                        val timeDiff = if (agg.canonicalTimestamp != null &&
                            observation.timestamp != null
                        ) {
                            abs(agg.canonicalTimestamp - observation.timestamp)
                        } else {
                            Long.MAX_VALUE
                        }
                        if (timeDiff < timeWindowMillis) {
                            val score = 60
                            candidates[agg.aggregateId] =
                                MatchCandidate(agg, score, "amount_day_match")
                        }
                    }
                }
            }
        }

        // Sort deterministically: by score desc, then closest timestamp, then lowest aggregateId
        return candidates.values
            .sortedWith(
                compareByDescending<MatchCandidate> { it.score }
                    .thenBy {
                        if (it.aggregate.canonicalTimestamp != null &&
                            observation.timestamp != null
                        ) {
                            abs(it.aggregate.canonicalTimestamp - observation.timestamp)
                        } else {
                            Long.MAX_VALUE
                        }
                    }
                    .thenBy { it.aggregate.aggregateId }
            )
    }

    private fun isDirectionCompatible(
        aggDirection: TransactionDirection,
        obsDirection: TransactionDirection
    ): Boolean {
        if (aggDirection == obsDirection) return true
        if (aggDirection == TransactionDirection.UNKNOWN ||
            obsDirection == TransactionDirection.UNKNOWN
        ) return true
        return false // DEBIT vs CREDIT = not compatible
    }

    private suspend fun mergeIntoAggregate(
        aggregate: AggregateEntity,
        observation: ObservationEntity
    ) {
        // Link the observation to the aggregate
        aggregateRepository.linkObservation(aggregate.aggregateId, observation.observationId)

        // Recompute canonical projection from all linked observations
        val observations =
            observationRepository.getObservationsForAggregateOnce(aggregate.aggregateId) +
                    observation
        val updated = computeCanonicalProjection(aggregate.aggregateId, observations)
        aggregateRepository.update(updated)
    }

    private suspend fun createNewAggregate(observation: ObservationEntity): String {
        val aggregate = AggregateEntity(
            canonicalAmountMinor = observation.amountMinor,
            canonicalCurrency = observation.currency,
            canonicalTimestamp = observation.timestamp,
            isApproxTime = observation.timestampDateOnly,
            canonicalDirection = observation.direction,
            canonicalReference = observation.reference,
            canonicalCounterparty = observation.counterparty,
            canonicalAccountHint = observation.accountHint,
            confidenceScore = computeConfidence(listOf(observation)),
            categoryId = null,
            userNotes = null,
            observationCount = 1
        )

        aggregateRepository.insert(aggregate)
        aggregateRepository.linkObservation(aggregate.aggregateId, observation.observationId)
        return aggregate.aggregateId
    }

    /**
     * Deterministic canonical projection from a set of observations.
     *
     * Each canonical field is derived by a voting/priority scheme so that
     * the result is stable regardless of observation insertion order.
     */
    fun computeCanonicalProjection(
        aggregateId: String,
        observations: List<ObservationEntity>
    ): AggregateEntity {
        require(observations.isNotEmpty()) { "Cannot project from empty observations" }

        // Amount: most frequent, tie-break by source priority
        val canonicalAmount = observations
            .groupBy { it.amountMinor }
            .maxByOrNull { (_, obs) -> obs.size * 1000 + sourcePriority(obs.first()) }
            ?.key ?: observations.first().amountMinor

        // Currency: most frequent
        val canonicalCurrency = observations
            .groupBy { it.currency }
            .maxByOrNull { it.value.size }
            ?.key ?: observations.first().currency

        // Timestamp: median of non-null timestamps
        val timestamps = observations.mapNotNull { it.timestamp }.sorted()
        val canonicalTimestamp = if (timestamps.isNotEmpty()) {
            timestamps[timestamps.size / 2]
        } else {
            null
        }

        val isApproxTime = observations.all { it.timestampDateOnly }

        // Direction: all same = that direction; mixed = MIXED
        val directions = observations.map { it.direction }.distinct()
            .filter { it != TransactionDirection.UNKNOWN }
        val canonicalDirection = when {
            directions.isEmpty() -> TransactionDirection.UNKNOWN
            directions.size == 1 -> directions.first()
            directions.contains(TransactionDirection.DEBIT) &&
                    directions.contains(TransactionDirection.CREDIT) ->
                TransactionDirection.MIXED
            else -> directions.first()
        }

        // Reference: exact match wins, else longest available
        val references = observations.mapNotNull { it.reference?.trim() }
            .filter { it.isNotBlank() }
        val canonicalReference = if (references.distinct().size == 1) {
            references.first()
        } else {
            references.maxByOrNull { it.length }
        }

        // Counterparty: most frequent non-null (case-insensitive grouping)
        val canonicalCounterparty = observations
            .mapNotNull { it.counterparty?.trim() }
            .filter { it.isNotBlank() }
            .groupBy { it.lowercase() }
            .maxByOrNull { it.value.size }
            ?.value?.first()

        // AccountHint: most frequent non-null
        val canonicalAccountHint = observations
            .mapNotNull { it.accountHint?.trim() }
            .filter { it.isNotBlank() }
            .groupBy { it }
            .maxByOrNull { it.value.size }
            ?.key

        return AggregateEntity(
            aggregateId = aggregateId,
            canonicalAmountMinor = canonicalAmount,
            canonicalCurrency = canonicalCurrency,
            canonicalTimestamp = canonicalTimestamp,
            isApproxTime = isApproxTime,
            canonicalDirection = canonicalDirection,
            canonicalReference = canonicalReference,
            canonicalCounterparty = canonicalCounterparty,
            canonicalAccountHint = canonicalAccountHint,
            confidenceScore = computeConfidence(observations),
            categoryId = null,
            userNotes = null,
            observationCount = observations.size,
            updatedAt = Instant.now().toEpochMilli()
        )
    }

    /**
     * Confidence score in the range 0-100 based on weighted factors:
     *
     * - Number of distinct source types (max 30 pts)
     * - Exact reference match across all observations (20 pts)
     * - Timestamp agreement / dispersion (20 pts)
     * - Average parse confidence (20 pts)
     * - Amount agreement (10 pts)
     */
    fun computeConfidence(observations: List<ObservationEntity>): Int {
        if (observations.isEmpty()) return 0

        var score = 0.0

        // Number of distinct source types (max 30 points)
        val sourceTypes = observations.map { it.sourceType }.distinct().size
        score += minOf(30.0, sourceTypes * 15.0)

        // Exact reference match (20 points)
        val refs = observations.mapNotNull { it.reference?.trim() }.filter { it.isNotBlank() }
        if (refs.distinct().size == 1 && refs.isNotEmpty()) score += 20.0

        // Timestamp agreement (20 points)
        val timestamps = observations.mapNotNull { it.timestamp }
        if (timestamps.size >= 2) {
            val dispersion = timestamps.max() - timestamps.min()
            val dispersionMinutes = dispersion / 60_000.0
            score += when {
                dispersionMinutes < 5 -> 20.0
                dispersionMinutes < 60 -> 15.0
                dispersionMinutes < 1440 -> 10.0
                else -> 5.0
            }
        } else if (timestamps.size == 1) {
            score += 10.0
        }

        // Parse confidence average (20 points)
        val avgConfidence = observations.map { it.parseConfidence }.average()
        score += avgConfidence * 20.0

        // Amount agreement (10 points)
        val amounts = observations.map { it.amountMinor }.distinct()
        if (amounts.size == 1) score += 10.0

        return minOf(100, score.toInt())
    }

    private fun sourcePriority(observation: ObservationEntity): Int {
        return when (observation.sourceType) {
            SourceType.PDF -> 3
            SourceType.CSV -> 3
            SourceType.XLSX -> 3
            SourceType.SMS -> 1
        }
    }
}
