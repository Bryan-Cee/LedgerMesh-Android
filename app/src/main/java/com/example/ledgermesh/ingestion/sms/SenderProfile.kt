package com.example.ledgermesh.ingestion.sms

import kotlinx.serialization.Serializable

/**
 * Describes a known SMS sender (e.g. M-PESA, a specific bank) and the
 * set of regex patterns used to extract transaction data from its messages.
 *
 * Profiles are serializable so they can be persisted to DataStore or
 * exported/imported as JSON for user customization.
 *
 * @property id Stable identifier used for persistence and deduplication.
 * @property name Human-readable display name.
 * @property senderAddresses SMS sender addresses that identify this profile
 *   (matched case-insensitively against the SMS `address` field).
 *   An empty list means the profile is a content-only fallback.
 * @property enabled Whether this profile is active. Disabled profiles are
 *   skipped during parsing.
 * @property currency ISO 4217 currency code for amounts parsed by this profile.
 * @property patterns Ordered list of regex patterns to try. The first match wins.
 * @property priority Higher values are checked first when multiple profiles
 *   match the same sender address.
 */
@Serializable
data class SenderProfile(
    val id: String,
    val name: String,
    val senderAddresses: List<String>,
    val enabled: Boolean = true,
    val currency: String = "KES",
    val patterns: List<SmsPattern>,
    val priority: Int = 0
)

/**
 * A single regex pattern within a [SenderProfile] that extracts transaction
 * fields from an SMS body.
 *
 * @property name Short identifier for this pattern (e.g. "send_money", "buy_goods").
 * @property regex Java-style regex applied to the SMS body. Named or indexed
 *   capture groups hold the extracted fields.
 * @property direction Fixed transaction direction for messages matching this
 *   pattern: "DEBIT", "CREDIT", or "UNKNOWN".
 * @property captureGroups Maps regex group indices to transaction fields.
 */
@Serializable
data class SmsPattern(
    val name: String,
    val regex: String,
    val direction: String,
    val captureGroups: CaptureGroups
)

/**
 * Maps regex capture group indices to the transaction fields they represent.
 *
 * All indices are 1-based (group 0 is the full match). Nullable fields are
 * optional -- the parser will skip them if the group index is null or the
 * group did not participate in the match.
 */
@Serializable
data class CaptureGroups(
    val amount: Int,
    val reference: Int? = null,
    val counterparty: Int? = null,
    val accountHint: Int? = null,
    val timestamp: Int? = null,
    val balance: Int? = null
)
