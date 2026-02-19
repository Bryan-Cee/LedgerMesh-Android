package com.example.ledgermesh.ingestion.csv

import com.example.ledgermesh.data.db.entity.ObservationEntity
import com.example.ledgermesh.domain.model.SourceType
import com.example.ledgermesh.domain.model.TransactionDirection
import com.example.ledgermesh.domain.usecase.FingerprintGenerator
import com.opencsv.CSVParserBuilder
import com.opencsv.CSVReaderBuilder
import java.io.InputStream
import java.io.InputStreamReader
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Describes how columns in a CSV file map to transaction fields.
 *
 * Either [amountColumn] (single signed amount) or [debitColumn]/[creditColumn]
 * (separate debit and credit columns) must be provided.
 */
data class CsvColumnMapping(
    val dateColumn: Int,
    val descriptionColumn: Int?,
    val amountColumn: Int?,
    val debitColumn: Int?,
    val creditColumn: Int?,
    val referenceColumn: Int?,
    val currency: String = "KES",
    val dateFormat: String = "yyyy-MM-dd",
    /** Multiplier to convert parsed decimal amounts to minor units (e.g., 100 for cents). */
    val amountMultiplier: Int = 100
)

/**
 * Result of previewing a CSV file before import.
 *
 * Contains header names, sample data rows, detected delimiter, and an
 * auto-suggested column mapping if common header names were found.
 */
data class CsvPreviewResult(
    val headers: List<String>,
    val sampleRows: List<List<String>>,
    val detectedDelimiter: Char,
    val totalRows: Int,
    val suggestedMapping: CsvColumnMapping?
)

/**
 * Result of parsing a CSV file into [ObservationEntity] records.
 */
data class CsvParseResult(
    val observations: List<ObservationEntity>,
    val errors: List<CsvParseError>,
    val totalRows: Int
)

/**
 * Describes a single row-level parsing error.
 */
data class CsvParseError(
    val rowIndex: Int,
    val message: String
)

/**
 * Parses CSV files into [ObservationEntity] records for import.
 *
 * Supports preview (header detection + sample rows + auto-mapping) and
 * full parse (all rows into observation entities with fingerprints).
 * Handles multiple delimiters (comma, semicolon, tab, pipe) and common
 * date formats.
 */
@Singleton
class CsvParser @Inject constructor(
    private val fingerprintGenerator: FingerprintGenerator
) {

    /**
     * Preview a CSV file: detect delimiter, read headers and sample rows,
     * and attempt to auto-suggest a column mapping.
     */
    fun preview(inputStream: InputStream, fileName: String): CsvPreviewResult {
        val reader = InputStreamReader(inputStream, Charsets.UTF_8)
        val content = reader.readText()
        val delimiter = detectDelimiter(content)

        val csvParser = CSVParserBuilder().withSeparator(delimiter).build()
        val csvReader = CSVReaderBuilder(content.reader()).withCSVParser(csvParser).build()

        val allRows = csvReader.readAll()
        val headers = if (allRows.isNotEmpty()) allRows[0].toList() else emptyList()
        val sampleRows = allRows.drop(1).take(5).map { it.toList() }

        val suggestedMapping = suggestMapping(headers)

        return CsvPreviewResult(
            headers = headers,
            sampleRows = sampleRows,
            detectedDelimiter = delimiter,
            totalRows = maxOf(0, allRows.size - 1),
            suggestedMapping = suggestedMapping
        )
    }

    /**
     * Parse the entire CSV file into [ObservationEntity] records using the
     * provided column mapping. Rows that cannot be parsed are recorded as
     * errors rather than aborting the import.
     */
    fun parse(
        inputStream: InputStream,
        fileName: String,
        mapping: CsvColumnMapping,
        importSessionId: String
    ): CsvParseResult {
        val reader = InputStreamReader(inputStream, Charsets.UTF_8)
        val content = reader.readText()
        val delimiter = detectDelimiter(content)

        val csvParser = CSVParserBuilder().withSeparator(delimiter).build()
        val csvReader = CSVReaderBuilder(content.reader()).withCSVParser(csvParser).build()

        val allRows = csvReader.readAll()
        val dataRows = allRows.drop(1) // skip header

        val observations = mutableListOf<ObservationEntity>()
        val errors = mutableListOf<CsvParseError>()

        for ((index, row) in dataRows.withIndex()) {
            try {
                val observation = parseRow(row, index, mapping, fileName, importSessionId)
                if (observation != null) {
                    observations.add(observation)
                }
            } catch (e: Exception) {
                errors.add(CsvParseError(index + 2, e.message ?: "Unknown parsing error"))
            }
        }

        return CsvParseResult(
            observations = observations,
            errors = errors,
            totalRows = dataRows.size
        )
    }

    private fun parseRow(
        row: Array<String>,
        rowIndex: Int,
        mapping: CsvColumnMapping,
        fileName: String,
        importSessionId: String
    ): ObservationEntity? {
        val dateStr = row.getOrNull(mapping.dateColumn)?.trim()
        if (dateStr.isNullOrBlank()) return null

        val timestamp = parseTimestamp(dateStr, mapping.dateFormat)
        val isDateOnly = !dateStr.contains(Regex("[Tt: ]\\d{1,2}:"))

        val amount: Long
        val direction: TransactionDirection

        if (mapping.amountColumn != null) {
            // Single signed amount column
            val amountStr = row.getOrNull(mapping.amountColumn)?.trim() ?: return null
            val parsed = parseAmount(amountStr, mapping.amountMultiplier)
            amount = kotlin.math.abs(parsed)
            direction = when {
                parsed < 0 -> TransactionDirection.DEBIT
                parsed > 0 -> TransactionDirection.CREDIT
                else -> TransactionDirection.UNKNOWN
            }
        } else if (mapping.debitColumn != null || mapping.creditColumn != null) {
            // Separate debit/credit columns
            val debitStr = mapping.debitColumn?.let { row.getOrNull(it)?.trim() }
            val creditStr = mapping.creditColumn?.let { row.getOrNull(it)?.trim() }
            val debitAmt = debitStr?.let { parseAmount(it, mapping.amountMultiplier) } ?: 0L
            val creditAmt = creditStr?.let { parseAmount(it, mapping.amountMultiplier) } ?: 0L

            if (debitAmt != 0L) {
                amount = kotlin.math.abs(debitAmt)
                direction = TransactionDirection.DEBIT
            } else if (creditAmt != 0L) {
                amount = kotlin.math.abs(creditAmt)
                direction = TransactionDirection.CREDIT
            } else {
                return null // no amount in either column
            }
        } else {
            return null // no amount columns mapped
        }

        val description = mapping.descriptionColumn?.let { row.getOrNull(it)?.trim() }
        val reference = mapping.referenceColumn?.let { row.getOrNull(it)?.trim() }

        val rawPayload = row.joinToString(",")
        val contentHash = fingerprintGenerator.generateContentHash(
            SourceType.CSV.name, fileName, rawPayload
        )

        val timestampMillis = timestamp?.atZone(ZoneId.systemDefault())?.toInstant()?.toEpochMilli()

        return ObservationEntity(
            sourceType = SourceType.CSV,
            sourceLocator = fileName,
            rawPayload = rawPayload,
            amountMinor = amount,
            currency = mapping.currency,
            timestamp = timestampMillis,
            timestampDateOnly = isDateOnly,
            direction = direction,
            reference = reference,
            counterparty = description,
            accountHint = null,
            parseConfidence = 0.8, // CSV is generally reliable
            contentHash = contentHash,
            importSessionId = importSessionId,
            fpRef = fingerprintGenerator.generateFpRef(reference),
            fpAmtTime = fingerprintGenerator.generateFpAmtTime(amount, timestampMillis),
            fpAmtDay = fingerprintGenerator.generateFpAmtDay(amount, timestampMillis),
            fpSenderAmt = fingerprintGenerator.generateFpSenderAmt(fileName, amount)
        )
    }

    /**
     * Detect the most likely delimiter by counting occurrences of common
     * delimiters in the first line of the file.
     */
    private fun detectDelimiter(content: String): Char {
        val firstLine = content.lineSequence().firstOrNull() ?: return ','
        val commaCount = firstLine.count { it == ',' }
        val semicolonCount = firstLine.count { it == ';' }
        val tabCount = firstLine.count { it == '\t' }
        val pipeCount = firstLine.count { it == '|' }

        return when (maxOf(commaCount, semicolonCount, tabCount, pipeCount)) {
            semicolonCount -> ';'
            tabCount -> '\t'
            pipeCount -> '|'
            else -> ','
        }
    }

    /**
     * Attempt to auto-detect column mappings from common header names
     * (date, description/narration, amount, debit/credit, reference).
     */
    private fun suggestMapping(headers: List<String>): CsvColumnMapping? {
        if (headers.isEmpty()) return null

        val lowerHeaders = headers.map { it.lowercase().trim() }
        var dateCol: Int? = null
        var descCol: Int? = null
        var amountCol: Int? = null
        var debitCol: Int? = null
        var creditCol: Int? = null
        var refCol: Int? = null

        for ((i, h) in lowerHeaders.withIndex()) {
            when {
                dateCol == null && h.contains("date") -> dateCol = i
                refCol == null && (h.contains("ref") || h.contains("transaction id") ||
                        h.contains("receipt")) -> refCol = i
                descCol == null && (h.contains("desc") || h.contains("detail") ||
                        h.contains("narration") || h.contains("particular")) -> descCol = i
                debitCol == null && (h.contains("debit") || h.contains("withdrawal")) ->
                    debitCol = i
                creditCol == null && (h.contains("credit") || h.contains("deposit")) ->
                    creditCol = i
                amountCol == null && (h.contains("amount") || h.contains("value")) ->
                    amountCol = i
            }
        }

        if (dateCol == null) return null

        return CsvColumnMapping(
            dateColumn = dateCol,
            descriptionColumn = descCol,
            amountColumn = if (debitCol == null && creditCol == null) amountCol else null,
            debitColumn = debitCol,
            creditColumn = creditCol,
            referenceColumn = refCol
        )
    }

    /**
     * Parse a date/datetime string using the specified format, falling back
     * to a list of common formats if the primary format fails.
     */
    private fun parseTimestamp(dateStr: String, format: String): LocalDateTime? {
        return try {
            val formatter = DateTimeFormatter.ofPattern(format)
            try {
                LocalDateTime.parse(dateStr, formatter)
            } catch (e: DateTimeParseException) {
                LocalDate.parse(dateStr, formatter).atTime(12, 0)
            }
        } catch (e: Exception) {
            tryCommonFormats(dateStr)
        }
    }

    private fun tryCommonFormats(dateStr: String): LocalDateTime? {
        val formats = listOf(
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd",
            "dd/MM/yyyy",
            "MM/dd/yyyy",
            "dd-MM-yyyy",
            "dd/MM/yyyy HH:mm:ss",
            "yyyy/MM/dd",
            "d/M/yyyy"
        )
        for (fmt in formats) {
            try {
                val formatter = DateTimeFormatter.ofPattern(fmt)
                return try {
                    LocalDateTime.parse(dateStr, formatter)
                } catch (e: DateTimeParseException) {
                    LocalDate.parse(dateStr, formatter).atTime(12, 0)
                }
            } catch (_: Exception) {
                continue
            }
        }
        return null
    }

    /**
     * Parse an amount string into minor units (Long).
     *
     * Strips currency symbols and thousand separators, then multiplies
     * by [multiplier] to convert to minor units (e.g., cents).
     */
    private fun parseAmount(amountStr: String, multiplier: Int): Long {
        if (amountStr.isBlank()) return 0L
        val cleaned = amountStr
            .replace(Regex("[^\\d.,-]"), "")
            .replace(",", "")
        val value = cleaned.toDoubleOrNull() ?: return 0L
        return (value * multiplier).toLong()
    }
}
