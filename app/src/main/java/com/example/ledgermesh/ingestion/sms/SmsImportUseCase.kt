package com.example.ledgermesh.ingestion.sms

import com.example.ledgermesh.data.db.entity.ImportSessionEntity
import com.example.ledgermesh.data.db.entity.ObservationEntity
import com.example.ledgermesh.data.repository.ImportSessionRepository
import com.example.ledgermesh.data.repository.ObservationRepository
import com.example.ledgermesh.domain.model.ImportStatus
import com.example.ledgermesh.domain.model.SourceType
import com.example.ledgermesh.domain.usecase.ReconciliationEngine
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Summary of a completed SMS import operation.
 *
 * @property sessionId The import session identifier for tracking in the UI.
 * @property totalScanned Total SMS messages read from the inbox.
 * @property importedCount Observations successfully inserted (new records).
 * @property skippedCount Observations skipped because they already exist
 *   (deduplicated via content hash unique constraint).
 * @property unmatchedCount Messages that could not be parsed by any profile.
 * @property errors Human-readable error messages for diagnostic display.
 */
data class SmsImportResult(
    val sessionId: String,
    val totalScanned: Int,
    val importedCount: Int,
    val skippedCount: Int,
    val unmatchedCount: Int,
    val errors: List<String>
)

/**
 * Orchestrates the full SMS import workflow:
 *
 * 1. Reads matching SMS messages from the device inbox via [SmsReader].
 * 2. Parses each message into an [ObservationEntity] via [SmsParser].
 * 3. Batch-inserts observations (duplicates silently skipped by Room's
 *    `OnConflictStrategy.IGNORE` on the content hash unique index).
 * 4. Updates the [ImportSessionEntity] with final counts.
 * 5. Runs [ReconciliationEngine.reconcileAll] to link new observations
 *    into aggregates.
 */
@Singleton
class SmsImportUseCase @Inject constructor(
    private val smsReader: SmsReader,
    private val smsParser: SmsParser,
    private val observationRepository: ObservationRepository,
    private val importSessionRepository: ImportSessionRepository,
    private val reconciliationEngine: ReconciliationEngine
) {

    /**
     * Full inbox scan -- reads all SMS matching known sender profiles.
     */
    suspend fun importAll(): SmsImportResult {
        val knownSenders = smsReader.getKnownSenders()
        val messages = smsReader.readMessages(senderAddresses = knownSenders)
        return processMessages(messages, "SMS inbox (full scan)")
    }

    /**
     * Incremental scan -- reads only SMS newer than [afterTimestamp].
     */
    suspend fun importSince(afterTimestamp: Long): SmsImportResult {
        val knownSenders = smsReader.getKnownSenders()
        val messages = smsReader.readMessages(
            senderAddresses = knownSenders,
            afterTimestamp = afterTimestamp
        )
        return processMessages(messages, "SMS inbox (incremental)")
    }

    private suspend fun processMessages(
        messages: List<SmsMessage>,
        sourceDescription: String
    ): SmsImportResult {
        val session = ImportSessionEntity(
            sourceType = SourceType.SMS,
            sourceLocator = sourceDescription,
            status = ImportStatus.PROCESSING,
            totalRecords = messages.size
        )
        importSessionRepository.insert(session)

        val observations = mutableListOf<ObservationEntity>()
        val errors = mutableListOf<String>()
        var unmatchedCount = 0

        for (sms in messages) {
            val result = smsParser.parse(sms, session.sessionId)
            if (result.observation != null) {
                observations.add(result.observation)
            } else if (result.error != null) {
                unmatchedCount++
            }
        }

        // Batch insert; duplicates are silently skipped via IGNORE conflict strategy
        val insertResults = observationRepository.insertAll(observations)
        val importedCount = insertResults.count { it != -1L }
        val skippedCount = insertResults.count { it == -1L }

        importSessionRepository.update(
            session.copy(
                status = ImportStatus.COMPLETED,
                importedRecords = importedCount,
                skippedRecords = skippedCount,
                failedRecords = unmatchedCount,
                completedAt = Instant.now().toEpochMilli()
            )
        )

        // Link new observations into aggregates
        reconciliationEngine.reconcileAll()

        return SmsImportResult(
            sessionId = session.sessionId,
            totalScanned = messages.size,
            importedCount = importedCount,
            skippedCount = skippedCount,
            unmatchedCount = unmatchedCount,
            errors = errors
        )
    }
}
