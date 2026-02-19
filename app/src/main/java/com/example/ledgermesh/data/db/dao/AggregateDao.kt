package com.example.ledgermesh.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.ledgermesh.data.db.entity.AggregateEntity
import com.example.ledgermesh.data.db.entity.AggregateObservationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AggregateDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(aggregate: AggregateEntity): Long

    @Update
    suspend fun update(aggregate: AggregateEntity)

    @Query("SELECT * FROM aggregates WHERE aggregateId = :id")
    suspend fun getById(id: String): AggregateEntity?

    @Query("SELECT * FROM aggregates WHERE aggregateId = :id")
    fun getByIdFlow(id: String): Flow<AggregateEntity?>

    @Query("SELECT * FROM aggregates ORDER BY canonicalTimestamp DESC")
    fun getAllFlow(): Flow<List<AggregateEntity>>

    @Query("SELECT * FROM aggregates ORDER BY canonicalTimestamp DESC")
    suspend fun getAllOnce(): List<AggregateEntity>

    @Query("SELECT * FROM aggregates ORDER BY canonicalTimestamp DESC LIMIT :limit")
    fun getRecentFlow(limit: Int): Flow<List<AggregateEntity>>

    @Query(
        "SELECT * FROM aggregates WHERE confidenceScore < :threshold ORDER BY confidenceScore ASC"
    )
    fun getLowConfidenceFlow(threshold: Int): Flow<List<AggregateEntity>>

    @Query(
        """
        SELECT * FROM aggregates
        WHERE canonicalTimestamp BETWEEN :startMillis AND :endMillis
        ORDER BY canonicalTimestamp DESC
        """
    )
    fun getByDateRange(startMillis: Long, endMillis: Long): Flow<List<AggregateEntity>>

    @Query(
        """
        SELECT DISTINCT canonicalAccountHint FROM aggregates
        WHERE canonicalAccountHint IS NOT NULL
        ORDER BY canonicalAccountHint ASC
        """
    )
    fun getDistinctAccountsFlow(): Flow<List<String>>

    @Query(
        """
        SELECT * FROM aggregates
        WHERE (:searchQuery IS NULL OR canonicalCounterparty LIKE '%' || :searchQuery || '%'
               OR canonicalReference LIKE '%' || :searchQuery || '%'
               OR userNotes LIKE '%' || :searchQuery || '%')
        AND (:accountFilter IS NULL OR canonicalAccountHint = :accountFilter)
        AND (:directionFilter IS NULL OR canonicalDirection = :directionFilter)
        AND (:startMillis IS NULL OR canonicalTimestamp >= :startMillis)
        AND (:endMillis IS NULL OR canonicalTimestamp <= :endMillis)
        AND (:minAmountMinor IS NULL OR canonicalAmountMinor >= :minAmountMinor)
        AND (:maxAmountMinor IS NULL OR canonicalAmountMinor <= :maxAmountMinor)
        AND (:maxConfidence IS NULL OR confidenceScore < :maxConfidence)
        ORDER BY canonicalTimestamp DESC
        """
    )
    fun searchAggregates(
        searchQuery: String?,
        accountFilter: String?,
        directionFilter: String?,
        startMillis: Long?,
        endMillis: Long?,
        minAmountMinor: Long?,
        maxAmountMinor: Long?,
        maxConfidence: Int?
    ): Flow<List<AggregateEntity>>

    @Query("SELECT COUNT(*) FROM aggregates")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM aggregates WHERE confidenceScore < :threshold")
    suspend fun countLowConfidence(threshold: Int): Int

    // -- Monthly analytics queries --

    @Query(
        """
        SELECT COALESCE(SUM(canonicalAmountMinor), 0) FROM aggregates
        WHERE canonicalDirection = 'CREDIT'
        AND canonicalTimestamp BETWEEN :startMillis AND :endMillis
        """
    )
    suspend fun totalIncomeForPeriod(startMillis: Long, endMillis: Long): Long

    @Query(
        """
        SELECT COALESCE(SUM(canonicalAmountMinor), 0) FROM aggregates
        WHERE canonicalDirection = 'DEBIT'
        AND canonicalTimestamp BETWEEN :startMillis AND :endMillis
        """
    )
    suspend fun totalExpenseForPeriod(startMillis: Long, endMillis: Long): Long

    @Delete
    suspend fun delete(aggregate: AggregateEntity)

    @Query("DELETE FROM aggregates")
    suspend fun deleteAll()

    @Query("DELETE FROM aggregate_observations")
    suspend fun deleteAllLinks()

    // -- Aggregate-Observation join operations --

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun linkObservation(link: AggregateObservationEntity)

    @Query(
        "DELETE FROM aggregate_observations WHERE aggregateId = :aggregateId AND observationId = :observationId"
    )
    suspend fun unlinkObservation(aggregateId: String, observationId: String)

    @Query("SELECT * FROM aggregate_observations WHERE observationId = :observationId")
    suspend fun getLinksForObservation(observationId: String): List<AggregateObservationEntity>

    @Query(
        """
        SELECT a.* FROM aggregates a
        INNER JOIN aggregate_observations ao ON a.aggregateId = ao.aggregateId
        INNER JOIN observations o ON ao.observationId = o.observationId
        WHERE o.fpRef = :fpRef
        """
    )
    suspend fun findAggregatesByFpRef(fpRef: String): List<AggregateEntity>

    @Query(
        """
        SELECT a.* FROM aggregates a
        INNER JOIN aggregate_observations ao ON a.aggregateId = ao.aggregateId
        INNER JOIN observations o ON ao.observationId = o.observationId
        WHERE o.fpAmtDay = :fpAmtDay
        """
    )
    suspend fun findAggregatesByFpAmtDay(fpAmtDay: String): List<AggregateEntity>
}
