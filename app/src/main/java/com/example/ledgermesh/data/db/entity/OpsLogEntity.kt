package com.example.ledgermesh.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.ledgermesh.domain.model.OperationType
import java.time.Instant
import java.util.UUID

/**
 * Immutable audit log of every user or system operation that mutates aggregates.
 *
 * This powers the "undo" / history trail, letting the user see (and eventually
 * reverse) merge, split, duplicate-marking, and field-edit actions.
 */
@Entity(
    tableName = "ops_log",
    indices = [
        Index(value = ["operationType"]),
        Index(value = ["createdAt"]),
        Index(value = ["targetAggregateId"])
    ]
)
data class OpsLogEntity(
    @PrimaryKey
    val opId: String = UUID.randomUUID().toString(),
    val operationType: OperationType,
    val targetAggregateId: String,
    /** For merge operations: the aggregate that was absorbed. */
    val secondaryAggregateId: String? = null,
    /** Comma-separated observation IDs affected by a split operation. */
    val affectedObservationIds: String? = null,
    /** For field-edit operations: which field was changed. */
    val fieldName: String? = null,
    val oldValue: String? = null,
    val newValue: String? = null,
    val createdAt: Long = Instant.now().toEpochMilli()
)
