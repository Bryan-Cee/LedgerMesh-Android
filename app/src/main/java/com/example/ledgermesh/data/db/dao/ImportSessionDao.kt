package com.example.ledgermesh.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.ledgermesh.data.db.entity.ImportSessionEntity
import com.example.ledgermesh.domain.model.ImportStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface ImportSessionDao {

    @Insert
    suspend fun insert(session: ImportSessionEntity)

    @Update
    suspend fun update(session: ImportSessionEntity)

    @Query("SELECT * FROM import_sessions WHERE sessionId = :id")
    suspend fun getById(id: String): ImportSessionEntity?

    @Query("SELECT * FROM import_sessions ORDER BY createdAt DESC")
    fun getAllFlow(): Flow<List<ImportSessionEntity>>

    @Query("SELECT * FROM import_sessions ORDER BY createdAt DESC LIMIT :limit")
    fun getRecentFlow(limit: Int): Flow<List<ImportSessionEntity>>

    @Query(
        "UPDATE import_sessions SET status = :status, completedAt = :completedAt WHERE sessionId = :sessionId"
    )
    suspend fun updateStatus(sessionId: String, status: ImportStatus, completedAt: Long? = null)

    @Query("UPDATE import_sessions SET importedRecords = :count WHERE sessionId = :sessionId")
    suspend fun updateImportedCount(sessionId: String, count: Int)

    @Delete
    suspend fun delete(session: ImportSessionEntity)

    @Query("DELETE FROM import_sessions")
    suspend fun deleteAll()
}
