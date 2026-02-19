package com.example.ledgermesh.ingestion.sms

import com.example.ledgermesh.data.db.entity.ObservationEntity
import com.example.ledgermesh.domain.model.SourceType
import com.example.ledgermesh.domain.model.TransactionDirection
import com.example.ledgermesh.domain.usecase.FingerprintGenerator
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A raw SMS message to be parsed into an [ObservationEntity].
 *
 * @property id Device-local SMS `_ID` from the content provider.
 * @property sender The SMS `address` field (e.g. "MPESA", "+254...").
 * @property body Full text of the SMS message.
 * @property dateMillis Epoch milliseconds when the SMS was received.
 */
data class SmsMessage(
    val id: Long,
    val sender: String,
    val body: String,
    val dateMillis: Long
)

/**
 * Result of attempting to parse a single [SmsMessage].
 *
 * Exactly one of [observation] or [error] will be non-null. When parsing
 * succeeds, [matchedProfile] and [matchedPattern] identify which profile
 * and pattern produced the result.
 */
data class SmsParseResult(
    val observation: ObservationEntity?,
    val matchedProfile: String?,
    val matchedPattern: String?,
    val error: String? = null
)

/**
 * Parses SMS messages into [ObservationEntity] records by matching the
 * sender address and body against a prioritised list of [SenderProfile]s.
 *
 * The parser tries each pattern within the matched profile in order and
 * returns the first successful extraction. If no sender-matched profile
 * is found, it falls back to content-only profiles (those with empty
 * [SenderProfile.senderAddresses]).
 *
 * Thread-safe: the [profiles] list is replaced atomically via [updateProfiles].
 */
@Singleton
class SmsParser @Inject constructor(
    private val fingerprintGenerator: FingerprintGenerator
) {

    @Volatile
    private var profiles: List<SenderProfile> = DefaultProfiles.getAll()

    /**
     * Replace the active profile list. Profiles are re-sorted by priority
     * descending so that higher-priority profiles are checked first.
     */
    fun updateProfiles(newProfiles: List<SenderProfile>) {
        profiles = newProfiles.sortedByDescending { it.priority }
    }

    /**
     * Attempt to parse a single SMS message into an [ObservationEntity].
     *
     * @param sms The raw SMS to parse.
     * @param importSessionId The import session this observation belongs to.
     * @return A [SmsParseResult] with either a parsed observation or an error message.
     */
    fun parse(sms: SmsMessage, importSessionId: String): SmsParseResult {
        val activeProfiles = profiles.filter { it.enabled }

        // 1. Try profiles whose sender addresses match the SMS sender
        val senderMatchedProfile = activeProfiles
            .filter { it.senderAddresses.isNotEmpty() }
            .firstOrNull { profile ->
                profile.senderAddresses.any { addr ->
                    sms.sender.equals(addr, ignoreCase = true) ||
                        sms.sender.contains(addr, ignoreCase = true)
                }
            }

        if (senderMatchedProfile != null) {
            return tryParseWithProfile(sms, senderMatchedProfile, importSessionId)
        }

        // 2. Fall back to content-only profiles (empty sender list)
        val genericProfile = activeProfiles
            .filter { it.senderAddresses.isEmpty() }
            .firstOrNull { profile ->
                profile.patterns.any { pattern ->
                    Regex(pattern.regex, RegexOption.IGNORE_CASE).containsMatchIn(sms.body)
                }
            }

        if (genericProfile != null) {
            return tryParseWithProfile(sms, genericProfile, importSessionId)
        }

        return SmsParseResult(
            observation = null,
            matchedProfile = null,
            matchedPattern = null,
            error = "No matching profile for sender: ${sms.sender}"
        )
    }

    /**
     * Try each pattern in [profile] against the SMS body and return the
     * first successful parse, or an error if no pattern matches.
     */
    private fun tryParseWithProfile(
        sms: SmsMessage,
        profile: SenderProfile,
        importSessionId: String
    ): SmsParseResult {
        for (pattern in profile.patterns) {
            val regex = Regex(pattern.regex, RegexOption.IGNORE_CASE)
            val match = regex.find(sms.body) ?: continue

            try {
                val amountStr = match.groupValues.getOrNull(pattern.captureGroups.amount)
                    ?: continue
                val amount = parseAmount(amountStr, MINOR_UNIT_MULTIPLIER)
                if (amount == 0L) continue

                val reference = pattern.captureGroups.reference?.let {
                    match.groupValues.getOrNull(it)?.trim()?.ifBlank { null }
                }
                val counterparty = pattern.captureGroups.counterparty?.let {
                    match.groupValues.getOrNull(it)?.trim()?.ifBlank { null }
                }
                val accountHint = pattern.captureGroups.accountHint?.let {
                    match.groupValues.getOrNull(it)?.trim()?.ifBlank { null }
                }

                val direction = when (pattern.direction) {
                    "DEBIT" -> TransactionDirection.DEBIT
                    "CREDIT" -> TransactionDirection.CREDIT
                    else -> TransactionDirection.UNKNOWN
                }

                val contentHash = fingerprintGenerator.generateContentHash(
                    SourceType.SMS.name, sms.sender, sms.body
                )

                val observation = ObservationEntity(
                    sourceType = SourceType.SMS,
                    sourceLocator = sms.sender,
                    rawPayload = sms.body,
                    amountMinor = amount,
                    currency = profile.currency,
                    timestamp = sms.dateMillis,
                    timestampDateOnly = false,
                    direction = direction,
                    reference = reference,
                    counterparty = counterparty,
                    accountHint = accountHint ?: profile.name,
                    parseConfidence = SMS_PARSE_CONFIDENCE,
                    contentHash = contentHash,
                    importSessionId = importSessionId,
                    fpRef = fingerprintGenerator.generateFpRef(reference),
                    fpAmtTime = fingerprintGenerator.generateFpAmtTime(amount, sms.dateMillis),
                    fpAmtDay = fingerprintGenerator.generateFpAmtDay(amount, sms.dateMillis),
                    fpSenderAmt = fingerprintGenerator.generateFpSenderAmt(sms.sender, amount)
                )

                return SmsParseResult(
                    observation = observation,
                    matchedProfile = profile.id,
                    matchedPattern = pattern.name
                )
            } catch (_: Exception) {
                // Pattern matched but extraction failed; try the next pattern
                continue
            }
        }

        return SmsParseResult(
            observation = null,
            matchedProfile = profile.id,
            matchedPattern = null,
            error = "No pattern matched for message from ${profile.name}"
        )
    }

    /**
     * Parse an amount string (e.g. "1,500.00") into minor units (Long).
     * Strips thousand separators and multiplies by [multiplier].
     */
    private fun parseAmount(amountStr: String, multiplier: Int): Long {
        val cleaned = amountStr.replace(",", "").trim()
        val value = cleaned.toDoubleOrNull() ?: return 0L
        return (value * multiplier).toLong()
    }

    companion object {
        /** KES to cents (minor units). */
        private const val MINOR_UNIT_MULTIPLIER = 100

        /**
         * Default parse confidence for SMS-extracted observations.
         * Lower than CSV (0.8) because SMS regex extraction is more fragile.
         */
        private const val SMS_PARSE_CONFIDENCE = 0.85
    }
}
