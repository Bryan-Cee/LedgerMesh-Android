package com.example.ledgermesh.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.ledgermesh.domain.model.TransactionDirection
import java.time.Instant
import java.util.UUID

/**
 * A canonical, reconciled transaction formed by merging one or more
 * [ObservationEntity] records that describe the same real-world event.
 *
 * The "canonical" fields represent the best-known values after merging.
 * [confidenceScore] (0-100) indicates how certain the system is that
 * all linked observations truly belong together.
 */
@Entity(
    tableName = "aggregates",
    indices = [
        Index(value = ["canonicalTimestamp"]),
        Index(value = ["canonicalAmountMinor"]),
        Index(value = ["categoryId"]),
        Index(value = ["confidenceScore"])
    ]
)
data class AggregateEntity(
    @PrimaryKey
    val aggregateId: String = UUID.randomUUID().toString(),
    val canonicalAmountMinor: Long,
    val canonicalCurrency: String,
    /** Epoch milliseconds of the best-known transaction time. */
    val canonicalTimestamp: Long?,
    /** True when the timestamp was inferred or only date-level precision is available. */
    val isApproxTime: Boolean = false,
    val canonicalDirection: TransactionDirection,
    val canonicalReference: String?,
    val canonicalCounterparty: String?,
    val canonicalAccountHint: String?,
    /** Reconciliation confidence, 0 (lowest) to 100 (highest / user-confirmed). */
    val confidenceScore: Int,
    val categoryId: String?,
    val userNotes: String?,
    val observationCount: Int = 1,
    val createdAt: Long = Instant.now().toEpochMilli(),
    val updatedAt: Long = Instant.now().toEpochMilli()
)
