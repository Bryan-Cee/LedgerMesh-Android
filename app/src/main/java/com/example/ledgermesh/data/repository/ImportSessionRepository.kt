package com.example.ledgermesh.data.repository

import com.example.ledgermesh.data.db.dao.ImportSessionDao
import com.example.ledgermesh.data.db.entity.ImportSessionEntity
import com.example.ledgermesh.domain.model.ImportStatus
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ImportSessionRepository @Inject constructor(
    private val importSessionDao: ImportSessionDao
) {
    suspend fun insert(session: ImportSessionEntity) = importSessionDao.insert(session)

    suspend fun update(session: ImportSessionEntity) = importSessionDao.update(session)

    suspend fun getById(id: String): ImportSessionEntity? = importSessionDao.getById(id)

    fun getAllFlow(): Flow<List<ImportSessionEntity>> = importSessionDao.getAllFlow()

    fun getRecentFlow(limit: Int = 10): Flow<List<ImportSessionEntity>> =
        importSessionDao.getRecentFlow(limit)

    suspend fun markCompleted(sessionId: String) {
        importSessionDao.updateStatus(
            sessionId,
            ImportStatus.COMPLETED,
            Instant.now().toEpochMilli()
        )
    }

    suspend fun markFailed(sessionId: String) {
        importSessionDao.updateStatus(sessionId, ImportStatus.FAILED)
    }

    suspend fun updateImportedCount(sessionId: String, count: Int) {
        importSessionDao.updateImportedCount(sessionId, count)
    }
}
