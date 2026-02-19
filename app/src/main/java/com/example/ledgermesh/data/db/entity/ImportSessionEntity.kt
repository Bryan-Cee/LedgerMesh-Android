package com.example.ledgermesh.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.ledgermesh.domain.model.ImportStatus
import com.example.ledgermesh.domain.model.SourceType
import java.time.Instant
import java.util.UUID

/**
 * Tracks one file-import or SMS-scan operation from start to finish.
 *
 * The UI shows import history via this entity, and each [ObservationEntity]
 * references the session that created it through [ObservationEntity.importSessionId].
 */
@Entity(
    tableName = "import_sessions",
    indices = [
        Index(value = ["status"]),
        Index(value = ["createdAt"])
    ]
)
data class ImportSessionEntity(
    @PrimaryKey
    val sessionId: String = UUID.randomUUID().toString(),
    val sourceType: SourceType,
    /** Human-readable source descriptor: filename, "SMS inbox", etc. */
    val sourceLocator: String,
    val status: ImportStatus,
    val totalRecords: Int = 0,
    val importedRecords: Int = 0,
    val skippedRecords: Int = 0,
    val failedRecords: Int = 0,
    val errorMessage: String? = null,
    val createdAt: Long = Instant.now().toEpochMilli(),
    val completedAt: Long? = null
)
