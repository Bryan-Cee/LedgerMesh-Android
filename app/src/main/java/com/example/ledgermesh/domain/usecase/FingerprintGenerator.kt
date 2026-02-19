package com.example.ledgermesh.domain.usecase

import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Generates normalised fingerprint keys used for fast candidate-pair lookups
 * during reconciliation. Each fingerprint captures a different dimension of
 * a transaction so the reconciliation engine can find potential matches
 * without full-table scans.
 */
@Singleton
class FingerprintGenerator @Inject constructor() {

    /**
     * fp_ref: normalised reference string, or null if no reference is available.
     *
     * Strips all non-alphanumeric characters and uppercases, so that minor
     * formatting differences between sources do not prevent matching.
     */
    fun generateFpRef(reference: String?): String? {
        if (reference.isNullOrBlank()) return null
        val normalized = reference.trim().uppercase().replace(Regex("[^A-Z0-9]"), "")
        return if (normalized.isNotEmpty()) "ref:$normalized" else null
    }

    /**
     * fp_amt_time: amountMinor combined with a rounded 5-minute time bucket.
     *
     * Two observations with the same amount that occur within the same
     * 5-minute window will share this fingerprint.
     */
    fun generateFpAmtTime(amountMinor: Long, timestampMillis: Long?): String? {
        if (timestampMillis == null) return null
        val fiveMinBucket = timestampMillis / (5 * 60 * 1000)
        return "at:${amountMinor}:${fiveMinBucket}"
    }

    /**
     * fp_amt_day: amountMinor combined with the local calendar date.
     *
     * Useful for matching observations that agree on amount and date but
     * may differ on the exact time (e.g. CSV rows that only have a date).
     */
    fun generateFpAmtDay(amountMinor: Long, timestampMillis: Long?): String? {
        if (timestampMillis == null) return null
        val localDate = Instant.ofEpochMilli(timestampMillis)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
        return "ad:${amountMinor}:${localDate}"
    }

    /**
     * fp_sender_amt: normalised source locator combined with amountMinor.
     *
     * Captures the "who sent this" + "how much" pair for matching across
     * different observation types from the same source.
     */
    fun generateFpSenderAmt(sourceLocator: String, amountMinor: Long): String {
        val normalizedSender = sourceLocator.trim().uppercase()
        return "sa:${normalizedSender}:${amountMinor}"
    }

    /**
     * Content hash for idempotency: SHA-256 of sourceType + sourceLocator + rawPayload.
     *
     * Ensures that re-importing the same file does not create duplicate
     * observations. The hash is stored in a unique-indexed column.
     */
    fun generateContentHash(sourceType: String, sourceLocator: String, rawPayload: String): String {
        val input = "$sourceType|$sourceLocator|$rawPayload"
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
}
