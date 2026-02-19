package com.example.ledgermesh.ingestion.csv

import com.example.ledgermesh.data.db.entity.ImportSessionEntity
import com.example.ledgermesh.data.repository.ImportSessionRepository
import com.example.ledgermesh.data.repository.ObservationRepository
import com.example.ledgermesh.domain.model.ImportStatus
import com.example.ledgermesh.domain.model.SourceType
import java.io.InputStream
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Summary of a completed CSV import operation.
 */
data class ImportResult(
    val sessionId: String,
    val importedCount: Int,
    val skippedCount: Int,
    val errorCount: Int,
    val errors: List<CsvParseError>
)

/**
 * Orchestrates the full CSV import workflow: creates an import session,
 * parses the CSV file, inserts observations (deduplicating via content hash),
 * and updates the session status.
 */
@Singleton
class CsvImportUseCase @Inject constructor(
    private val csvParser: CsvParser,
    private val observationRepository: ObservationRepository,
    private val importSessionRepository: ImportSessionRepository
) {

    /**
     * Preview a CSV file without importing. Returns headers, sample rows,
     * detected delimiter, and a suggested column mapping.
     */
    fun preview(inputStream: InputStream, fileName: String): CsvPreviewResult {
        return csvParser.preview(inputStream, fileName)
    }

    /**
     * Import a CSV file using the provided column mapping.
     *
     * Creates an [ImportSessionEntity] to track progress, parses all rows,
     * bulk-inserts observations (duplicates are silently skipped via
     * `OnConflictStrategy.IGNORE` on the content hash unique index),
     * and updates the session with final counts.
     *
     * @throws Exception if a fatal error occurs; the session is marked FAILED.
     */
    suspend fun import(
        inputStream: InputStream,
        fileName: String,
        mapping: CsvColumnMapping
    ): ImportResult {
        val session = ImportSessionEntity(
            sourceType = SourceType.CSV,
            sourceLocator = fileName,
            status = ImportStatus.PROCESSING
        )
        importSessionRepository.insert(session)

        return try {
            val result = csvParser.parse(inputStream, fileName, mapping, session.sessionId)

            val insertResults = observationRepository.insertAll(result.observations)
            val importedCount = insertResults.count { it != -1L }
            val skippedCount = insertResults.count { it == -1L } // duplicates skipped by IGNORE

            importSessionRepository.update(
                session.copy(
                    status = ImportStatus.COMPLETED,
                    totalRecords = result.totalRows,
                    importedRecords = importedCount,
                    skippedRecords = skippedCount,
                    failedRecords = result.errors.size,
                    completedAt = Instant.now().toEpochMilli()
                )
            )

            ImportResult(
                sessionId = session.sessionId,
                importedCount = importedCount,
                skippedCount = skippedCount,
                errorCount = result.errors.size,
                errors = result.errors
            )
        } catch (e: Exception) {
            importSessionRepository.update(
                session.copy(
                    status = ImportStatus.FAILED,
                    errorMessage = e.message
                )
            )
            throw e
        }
    }
}
