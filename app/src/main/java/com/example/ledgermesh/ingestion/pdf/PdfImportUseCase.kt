package com.example.ledgermesh.ingestion.pdf

import com.example.ledgermesh.data.db.entity.ImportSessionEntity
import com.example.ledgermesh.data.repository.ImportSessionRepository
import com.example.ledgermesh.data.repository.ObservationRepository
import com.example.ledgermesh.domain.model.ImportStatus
import com.example.ledgermesh.domain.model.SourceType
import com.example.ledgermesh.domain.usecase.ReconciliationEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

data class PdfImportResult(
    val sessionId: String,
    val importedCount: Int,
    val skippedCount: Int,
    val errorCount: Int,
    val detectedTableCount: Int,
    val errors: List<PdfParseError>
)

@Singleton
class PdfImportUseCase @Inject constructor(
    private val pdfTextExtractor: PdfTextExtractor,
    private val pdfParser: PdfParser,
    private val observationRepository: ObservationRepository,
    private val importSessionRepository: ImportSessionRepository,
    private val reconciliationEngine: ReconciliationEngine
) {

    suspend fun import(
        inputStream: InputStream,
        fileName: String,
        currency: String = "USD"
    ): PdfImportResult {
        val session = ImportSessionEntity(
            sourceType = SourceType.PDF,
            sourceLocator = fileName,
            status = ImportStatus.PROCESSING
        )
        importSessionRepository.insert(session)

        return try {
            // Extract text on IO dispatcher
            val extraction = withContext(Dispatchers.IO) {
                pdfTextExtractor.extract(inputStream)
            }

            // Check for scanned PDF
            if (extraction.isLikelyScanned) {
                importSessionRepository.update(
                    session.copy(
                        status = ImportStatus.FAILED,
                        errorMessage = "This appears to be a scanned/image PDF. Only digital (text-based) PDFs are supported."
                    )
                )
                return PdfImportResult(
                    sessionId = session.sessionId,
                    importedCount = 0,
                    skippedCount = 0,
                    errorCount = 0,
                    detectedTableCount = 0,
                    errors = listOf(PdfParseError(0, "Scanned/image PDF detected"))
                )
            }

            // Parse on Default dispatcher (CPU-bound)
            val parseResult = withContext(Dispatchers.Default) {
                pdfParser.parse(extraction, fileName, session.sessionId, currency)
            }

            // Insert observations (duplicates silently skipped via IGNORE)
            val insertResults = observationRepository.insertAll(parseResult.observations)
            val importedCount = insertResults.count { it != -1L }
            val skippedCount = insertResults.count { it == -1L }

            importSessionRepository.update(
                session.copy(
                    status = ImportStatus.COMPLETED,
                    totalRecords = parseResult.totalRows,
                    importedRecords = importedCount,
                    skippedRecords = skippedCount,
                    failedRecords = parseResult.errors.size,
                    completedAt = Instant.now().toEpochMilli()
                )
            )

            // Run reconciliation on newly imported observations
            reconciliationEngine.reconcileAll()

            PdfImportResult(
                sessionId = session.sessionId,
                importedCount = importedCount,
                skippedCount = skippedCount,
                errorCount = parseResult.errors.size,
                detectedTableCount = parseResult.detectedTableCount,
                errors = parseResult.errors
            )
        } catch (e: PdfImportException) {
            importSessionRepository.update(
                session.copy(
                    status = ImportStatus.FAILED,
                    errorMessage = e.message
                )
            )
            throw e
        } catch (e: Exception) {
            importSessionRepository.update(
                session.copy(
                    status = ImportStatus.FAILED,
                    errorMessage = e.message ?: "Unknown error during PDF import"
                )
            )
            throw e
        }
    }
}
