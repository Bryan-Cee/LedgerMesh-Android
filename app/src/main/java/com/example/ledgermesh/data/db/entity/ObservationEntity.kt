package com.example.ledgermesh.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.ledgermesh.domain.model.SourceType
import com.example.ledgermesh.domain.model.TransactionDirection
import java.time.Instant
import java.util.UUID

/**
 * A single raw observation of a financial transaction, as parsed from one source.
 *
 * Multiple observations may describe the same real-world transaction (e.g. an SMS
 * notification and a CSV bank statement row). The reconciliation engine links
 * matching observations into a single [AggregateEntity].
 *
 * Fingerprint columns (`fpRef`, `fpAmtTime`, `fpAmtDay`, `fpSenderAmt`) are
 * pre-computed, normalised keys used for fast candidate-pair lookups during
 * reconciliation.
 */
@Entity(
    tableName = "observations",
    indices = [
        Index(value = ["contentHash"], unique = true),
        Index(value = ["amountMinor"]),
        Index(value = ["timestamp"]),
        Index(value = ["sourceType", "sourceLocator"]),
        Index(value = ["importSessionId"]),
        Index(value = ["fpRef"]),
        Index(value = ["fpAmtTime"]),
        Index(value = ["fpAmtDay"]),
        Index(value = ["fpSenderAmt"])
    ]
)
data class ObservationEntity(
    @PrimaryKey
    val observationId: String = UUID.randomUUID().toString(),
    val sourceType: SourceType,
    val sourceLocator: String,
    val rawPayload: String,
    val amountMinor: Long,
    val currency: String,
    /** Epoch milliseconds; nullable when the source does not provide a timestamp. */
    val timestamp: Long?,
    /** True when only a calendar date (no time-of-day) was available from the source. */
    val timestampDateOnly: Boolean = false,
    val direction: TransactionDirection,
    val reference: String?,
    val counterparty: String?,
    val accountHint: String?,
    /** Parser-assigned confidence in [0.0, 1.0]. */
    val parseConfidence: Double,
    val ingestedAt: Long = Instant.now().toEpochMilli(),
    val contentHash: String,
    val importSessionId: String?,
    // -- Fingerprints for fast matching --
    /** Normalised reference string. */
    val fpRef: String?,
    /** amountMinor + rounded 5-minute time bucket. */
    val fpAmtTime: String?,
    /** amountMinor + local calendar date. */
    val fpAmtDay: String?,
    /** Sender/counterparty + amountMinor. */
    val fpSenderAmt: String?
)
