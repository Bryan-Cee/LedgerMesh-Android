package com.example.ledgermesh.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * Join table linking [AggregateEntity] to the [ObservationEntity] records
 * that were merged to form it. A many-to-many relationship: one aggregate
 * contains many observations, and (theoretically, during re-reconciliation)
 * an observation could move between aggregates.
 */
@Entity(
    tableName = "aggregate_observations",
    primaryKeys = ["aggregateId", "observationId"],
    foreignKeys = [
        ForeignKey(
            entity = AggregateEntity::class,
            parentColumns = ["aggregateId"],
            childColumns = ["aggregateId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = ObservationEntity::class,
            parentColumns = ["observationId"],
            childColumns = ["observationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["observationId"]),
        Index(value = ["aggregateId"])
    ]
)
data class AggregateObservationEntity(
    val aggregateId: String,
    val observationId: String,
    val linkedAt: Long = System.currentTimeMillis()
)
