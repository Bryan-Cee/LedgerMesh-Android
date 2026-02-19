package com.example.ledgermesh.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.ledgermesh.data.db.entity.ObservationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ObservationDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(observation: ObservationEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(observations: List<ObservationEntity>): List<Long>

    @Query("SELECT * FROM observations WHERE observationId = :id")
    suspend fun getById(id: String): ObservationEntity?

    @Query("SELECT * FROM observations WHERE contentHash = :hash")
    suspend fun getByContentHash(hash: String): ObservationEntity?

    @Query("SELECT * FROM observations WHERE importSessionId = :sessionId")
    fun getByImportSession(sessionId: String): Flow<List<ObservationEntity>>

    @Query("SELECT * FROM observations ORDER BY ingestedAt DESC")
    fun getAllFlow(): Flow<List<ObservationEntity>>

    @Query("SELECT * FROM observations WHERE fpRef = :fpRef")
    suspend fun findByFpRef(fpRef: String): List<ObservationEntity>

    @Query("SELECT * FROM observations WHERE fpAmtTime = :fpAmtTime")
    suspend fun findByFpAmtTime(fpAmtTime: String): List<ObservationEntity>

    @Query("SELECT * FROM observations WHERE fpAmtDay = :fpAmtDay")
    suspend fun findByFpAmtDay(fpAmtDay: String): List<ObservationEntity>

    @Query("SELECT * FROM observations WHERE fpSenderAmt = :fpSenderAmt")
    suspend fun findByFpSenderAmt(fpSenderAmt: String): List<ObservationEntity>

    @Query("SELECT COUNT(*) FROM observations")
    suspend fun count(): Int

    @Query(
        """
        SELECT o.* FROM observations o
        INNER JOIN aggregate_observations ao ON o.observationId = ao.observationId
        WHERE ao.aggregateId = :aggregateId
        ORDER BY o.ingestedAt ASC
        """
    )
    fun getObservationsForAggregate(aggregateId: String): Flow<List<ObservationEntity>>

    @Query(
        """
        SELECT o.* FROM observations o
        INNER JOIN aggregate_observations ao ON o.observationId = ao.observationId
        WHERE ao.aggregateId = :aggregateId
        ORDER BY o.ingestedAt ASC
        """
    )
    suspend fun getObservationsForAggregateOnce(aggregateId: String): List<ObservationEntity>

    @Query(
        """
        SELECT o.* FROM observations o
        LEFT JOIN aggregate_observations ao ON o.observationId = ao.observationId
        WHERE ao.aggregateId IS NULL
        """
    )
    suspend fun getUnlinkedObservations(): List<ObservationEntity>

    @Delete
    suspend fun delete(observation: ObservationEntity)

    @Query("DELETE FROM observations")
    suspend fun deleteAll()
}
