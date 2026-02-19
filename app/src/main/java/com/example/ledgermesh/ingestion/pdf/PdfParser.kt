package com.example.ledgermesh.ingestion.pdf

import com.example.ledgermesh.data.db.entity.ObservationEntity
import com.example.ledgermesh.domain.model.SourceType
import com.example.ledgermesh.domain.model.TransactionDirection
import com.example.ledgermesh.domain.usecase.FingerprintGenerator
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import javax.inject.Inject
import javax.inject.Singleton

data class PdfParseResult(
    val observations: List<ObservationEntity>,
    val totalRows: Int,
    val errors: List<PdfParseError>,
    val detectedTableCount: Int
)

data class PdfParseError(
    val line: Int,
    val message: String
)

/**
 * Detected column layout within a PDF bank statement table.
 */
private data class ColumnLayout(
    val dateCol: Int,
    val descriptionCol: Int,
    val debitCol: Int?,
    val creditCol: Int?,
    val amountCol: Int?,
    val balanceCol: Int?,
    val referenceCol: Int?,
    val headerLineIndex: Int
)

/**
 * Heuristic parser for text-based PDF bank statements.
 *
 * Strategy:
 * 1. Split extracted text into lines
 * 2. Score each line for header keywords to find table headers
 * 3. Derive column boundaries from keyword positions
 * 4. Parse subsequent rows until a stop condition
 * 5. Build ObservationEntity for each valid row
 */
@Singleton
class PdfParser @Inject constructor(
    private val fingerprintGenerator: FingerprintGenerator
) {

    companion object {
        private const val PARSE_CONFIDENCE = 0.7

        private val HEADER_KEYWORDS = listOf(
            "date", "description", "narration", "particulars", "details",
            "debit", "credit", "amount", "withdrawal", "deposit",
            "balance", "reference", "ref", "value", "transaction"
        )

        private val STOP_KEYWORDS = listOf(
            "total", "closing balance", "opening balance", "statement summary",
            "page total", "brought forward", "carried forward", "end of statement"
        )

        private val DATE_FORMATTERS = listOf(
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("dd/MM/yy"),
            DateTimeFormatter.ofPattern("dd-MM-yy"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd"),
            DateTimeFormatter.ofPattern("dd MMM yyyy"),
            DateTimeFormatter.ofPattern("dd-MMM-yyyy"),
            DateTimeFormatter.ofPattern("dd MMM yy"),
            DateTimeFormatter.ofPattern("dd-MMM-yy"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy"),
            DateTimeFormatter.ofPattern("MMM dd, yyyy"),
            DateTimeFormatter.ofPattern("d/M/yyyy"),
            DateTimeFormatter.ofPattern("d-M-yyyy")
        )

        private val AMOUNT_REGEX = Regex("""[\d,]+\.\d{2}""")
        private val REFERENCE_REGEX = Regex("""[A-Z]{2,4}\d{8,16}""")
        private val DR_CR_SUFFIX = Regex("""(DR|CR)\s*$""", RegexOption.IGNORE_CASE)
    }

    fun parse(
        extraction: PdfExtractionResult,
        sourceLocator: String,
        importSessionId: String,
        currency: String = "USD"
    ): PdfParseResult {
        val allLines = extraction.fullText.lines()
        val observations = mutableListOf<ObservationEntity>()
        val errors = mutableListOf<PdfParseError>()
        var detectedTableCount = 0

        // Find all table headers in the document
        val headerIndices = findHeaderLines(allLines)
        detectedTableCount = headerIndices.size

        for (headerIdx in headerIndices) {
            val layout = detectColumnLayout(allLines, headerIdx) ?: continue
            val tableObs = parseTable(allLines, layout, sourceLocator, importSessionId, currency, errors)
            observations.addAll(tableObs)
        }

        return PdfParseResult(
            observations = observations,
            totalRows = observations.size + errors.size,
            errors = errors,
            detectedTableCount = detectedTableCount
        )
    }

    /**
     * Find lines that look like table headers by scoring keyword matches.
     */
    private fun findHeaderLines(lines: List<String>): List<Int> {
        val headers = mutableListOf<Int>()
        var lastHeaderIdx = -10 // avoid detecting the same table twice

        for ((idx, line) in lines.withIndex()) {
            if (idx - lastHeaderIdx < 3) continue // too close to previous header
            val lower = line.lowercase()
            val score = HEADER_KEYWORDS.count { keyword -> lower.contains(keyword) }
            if (score >= 2) {
                headers.add(idx)
                lastHeaderIdx = idx
            }
        }
        return headers
    }

    /**
     * Analyze a header line to determine which columns exist and their approximate positions.
     */
    private fun detectColumnLayout(lines: List<String>, headerIdx: Int): ColumnLayout? {
        val header = lines[headerIdx].lowercase()

        // Find positions of key columns
        val datePos = findKeywordPos(header, listOf("date", "value date", "txn date"))
        val descPos = findKeywordPos(header, listOf("description", "narration", "particulars", "details"))
        val debitPos = findKeywordPos(header, listOf("debit", "withdrawal", "dr"))
        val creditPos = findKeywordPos(header, listOf("credit", "deposit", "cr"))
        val amountPos = findKeywordPos(header, listOf("amount"))
        val balancePos = findKeywordPos(header, listOf("balance"))
        val refPos = findKeywordPos(header, listOf("reference", "ref"))

        // Must have at least a date column
        if (datePos == null) return null

        val descColPos = descPos ?: (datePos + 12) // estimate if not found

        return ColumnLayout(
            dateCol = datePos,
            descriptionCol = descColPos,
            debitCol = debitPos,
            creditCol = creditPos,
            amountCol = if (debitPos == null && creditPos == null) amountPos else null,
            balanceCol = balancePos,
            referenceCol = refPos,
            headerLineIndex = headerIdx
        )
    }

    private fun findKeywordPos(header: String, keywords: List<String>): Int? {
        for (keyword in keywords) {
            val idx = header.indexOf(keyword)
            if (idx >= 0) return idx
        }
        return null
    }

    /**
     * Parse data rows following a detected header until a stop condition.
     */
    private fun parseTable(
        lines: List<String>,
        layout: ColumnLayout,
        sourceLocator: String,
        importSessionId: String,
        currency: String,
        errors: MutableList<PdfParseError>
    ): List<ObservationEntity> {
        val observations = mutableListOf<ObservationEntity>()
        var currentDate: LocalDate? = null
        var currentDesc = StringBuilder()
        var currentAmount: Long? = null
        var currentDirection: TransactionDirection? = null
        var currentReference: String? = null
        var currentRawLine: String? = null
        var currentLineNum = layout.headerLineIndex + 1
        var consecutiveBlanks = 0

        fun flushCurrent() {
            val date = currentDate ?: return
            val amount = currentAmount ?: return
            val direction = currentDirection ?: TransactionDirection.DEBIT

            val description = currentDesc.toString().trim()
            val rawPayload = currentRawLine ?: description
            val timestampMillis = date.atStartOfDay(ZoneId.systemDefault())
                .toInstant().toEpochMilli()

            // Extract reference from description if not already found
            val ref = currentReference ?: extractReference(description)

            val contentHash = fingerprintGenerator.generateContentHash(
                SourceType.PDF.name, sourceLocator, "$date|$amount|$direction|$description"
            )

            observations.add(
                ObservationEntity(
                    sourceType = SourceType.PDF,
                    sourceLocator = sourceLocator,
                    rawPayload = rawPayload,
                    amountMinor = amount,
                    currency = currency,
                    timestamp = timestampMillis,
                    timestampDateOnly = true,
                    direction = direction,
                    reference = ref,
                    counterparty = description.ifBlank { null },
                    accountHint = null,
                    parseConfidence = PARSE_CONFIDENCE,
                    contentHash = contentHash,
                    importSessionId = importSessionId,
                    fpRef = fingerprintGenerator.generateFpRef(ref),
                    fpAmtTime = fingerprintGenerator.generateFpAmtTime(amount, timestampMillis),
                    fpAmtDay = fingerprintGenerator.generateFpAmtDay(amount, timestampMillis),
                    fpSenderAmt = fingerprintGenerator.generateFpSenderAmt(sourceLocator, amount)
                )
            )

            // Reset state
            currentDate = null
            currentDesc = StringBuilder()
            currentAmount = null
            currentDirection = null
            currentReference = null
            currentRawLine = null
        }

        for (i in (layout.headerLineIndex + 1) until lines.size) {
            val line = lines[i]
            val trimmed = line.trim()

            // Stop conditions
            if (trimmed.isEmpty()) {
                consecutiveBlanks++
                if (consecutiveBlanks >= 3) {
                    flushCurrent()
                    break
                }
                continue
            }
            consecutiveBlanks = 0

            val lowerTrimmed = trimmed.lowercase()
            if (STOP_KEYWORDS.any { lowerTrimmed.startsWith(it) }) {
                flushCurrent()
                break
            }

            // Try to parse a date at the beginning of the line
            val parsedDate = tryParseDate(trimmed)

            if (parsedDate != null) {
                // New row starting - flush previous
                flushCurrent()
                currentDate = parsedDate
                currentRawLine = trimmed
                currentLineNum = i

                // Extract amounts from this line
                val amounts = AMOUNT_REGEX.findAll(trimmed).toList()
                val (amount, direction) = extractAmountAndDirection(trimmed, amounts, layout)
                currentAmount = amount
                currentDirection = direction

                // Extract description: text between date and amounts
                val desc = extractDescription(trimmed, parsedDate, amounts)
                currentDesc.append(desc)

                // Check for reference
                currentReference = extractReference(trimmed)
            } else if (currentDate != null) {
                // Continuation line (multi-line description)
                // Check if this line has amounts we missed
                val amounts = AMOUNT_REGEX.findAll(trimmed).toList()
                if (currentAmount == null && amounts.isNotEmpty()) {
                    val (amount, direction) = extractAmountAndDirection(trimmed, amounts, layout)
                    currentAmount = amount
                    currentDirection = direction
                } else {
                    currentDesc.append(" ").append(trimmed)
                }

                // Check for reference in continuation lines too
                if (currentReference == null) {
                    currentReference = extractReference(trimmed)
                }
            }
            // Lines before any date found are ignored (pre-table content)
        }

        // Flush last row
        flushCurrent()

        return observations
    }

    /**
     * Try to parse the beginning of a line as a date.
     */
    private fun tryParseDate(line: String): LocalDate? {
        // Take the first token(s) that could be a date
        val candidates = listOf(
            line.substringBefore(" ").substringBefore("\t"), // single token
            line.split(Regex("\\s+")).take(3).joinToString(" "), // up to 3 tokens (e.g. "01 Jan 2024")
            line.split(Regex("\\s+")).take(2).joinToString(" ")  // 2 tokens
        )

        for (candidate in candidates) {
            val trimCandidate = candidate.trim()
            if (trimCandidate.isEmpty()) continue
            for (formatter in DATE_FORMATTERS) {
                try {
                    return LocalDate.parse(trimCandidate, formatter)
                } catch (_: DateTimeParseException) {
                    // Try next format
                }
            }
        }
        return null
    }

    /**
     * Extract amount and direction from a line based on the column layout.
     */
    private fun extractAmountAndDirection(
        line: String,
        amounts: List<MatchResult>,
        layout: ColumnLayout
    ): Pair<Long?, TransactionDirection?> {
        if (amounts.isEmpty()) return null to null

        // Check for DR/CR suffix
        val drCrMatch = DR_CR_SUFFIX.find(line)
        if (drCrMatch != null) {
            val amountStr = amounts.first().value
            val amountMinor = parseAmountToMinor(amountStr)
            val direction = if (drCrMatch.groupValues[1].uppercase() == "DR")
                TransactionDirection.DEBIT else TransactionDirection.CREDIT
            return amountMinor to direction
        }

        // Separate debit/credit columns
        if (layout.debitCol != null && layout.creditCol != null && amounts.size >= 1) {
            // Determine which column the amount falls in based on position
            if (amounts.size >= 2) {
                // Last amount is likely balance, second to last is the transaction
                // Try to figure out based on column positions
                val leftmostAmountPos = amounts.first().range.first
                val debitCenter = layout.debitCol
                val creditCenter = layout.creditCol

                for (amount in amounts.dropLast(if (layout.balanceCol != null) 1 else 0)) {
                    val pos = amount.range.first
                    val distToDebit = kotlin.math.abs(pos - debitCenter)
                    val distToCredit = kotlin.math.abs(pos - creditCenter)

                    if (distToDebit < distToCredit) {
                        return parseAmountToMinor(amount.value) to TransactionDirection.DEBIT
                    } else {
                        return parseAmountToMinor(amount.value) to TransactionDirection.CREDIT
                    }
                }
            }

            // Single amount - check position relative to debit/credit columns
            val amountPos = amounts.first().range.first
            val distToDebit = kotlin.math.abs(amountPos - layout.debitCol)
            val distToCredit = kotlin.math.abs(amountPos - layout.creditCol)

            val direction = if (distToDebit <= distToCredit)
                TransactionDirection.DEBIT else TransactionDirection.CREDIT
            return parseAmountToMinor(amounts.first().value) to direction
        }

        // Single amount column - check for negative sign
        val amountStr = amounts.first().value
        val amountMinor = parseAmountToMinor(amountStr)
        val precedingText = if (amounts.first().range.first > 0)
            line.substring(0, amounts.first().range.first) else ""
        val direction = if (precedingText.trimEnd().endsWith("-"))
            TransactionDirection.DEBIT else TransactionDirection.DEBIT // default to debit

        return amountMinor to direction
    }

    /**
     * Parse a formatted amount string (e.g., "1,234.56") to minor units (cents).
     */
    private fun parseAmountToMinor(amountStr: String): Long {
        val cleaned = amountStr.replace(",", "")
        return (cleaned.toDouble() * 100).toLong()
    }

    /**
     * Extract description text from a line, removing the date and amount portions.
     */
    private fun extractDescription(
        line: String,
        date: LocalDate,
        amounts: List<MatchResult>
    ): String {
        var desc = line

        // Remove amounts from end
        for (amount in amounts.reversed()) {
            desc = desc.removeRange(amount.range)
        }

        // Remove DR/CR suffix
        desc = desc.replace(DR_CR_SUFFIX, "")

        // Remove the date from beginning - find first non-date content
        val tokens = desc.trim().split(Regex("\\s+"))
        // Skip tokens that are part of the date
        var startIdx = 0
        val dateStr = date.toString() // yyyy-MM-dd
        for ((idx, token) in tokens.withIndex()) {
            val isDatePart = token.all { it.isDigit() || it == '/' || it == '-' }
                    || token.length == 3 && token.first().isLetter() // month abbreviation like "Jan"
                    || token == ","
            if (!isDatePart) {
                startIdx = idx
                break
            }
            startIdx = idx + 1
        }

        return tokens.drop(startIdx).joinToString(" ").trim()
    }

    /**
     * Try to extract a transaction reference from text.
     */
    private fun extractReference(text: String): String? {
        val match = REFERENCE_REGEX.find(text)
        return match?.value
    }
}
