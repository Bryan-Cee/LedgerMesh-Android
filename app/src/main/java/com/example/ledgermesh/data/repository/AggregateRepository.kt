package com.example.ledgermesh.data.repository

import com.example.ledgermesh.data.db.dao.AggregateDao
import com.example.ledgermesh.data.db.dao.ObservationDao
import com.example.ledgermesh.data.db.entity.AggregateEntity
import com.example.ledgermesh.data.db.entity.AggregateObservationEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AggregateRepository @Inject constructor(
    private val aggregateDao: AggregateDao,
    private val observationDao: ObservationDao
) {
    suspend fun insert(aggregate: AggregateEntity): Long = aggregateDao.insert(aggregate)

    suspend fun update(aggregate: AggregateEntity) = aggregateDao.update(aggregate)

    suspend fun getById(id: String): AggregateEntity? = aggregateDao.getById(id)

    fun getByIdFlow(id: String): Flow<AggregateEntity?> = aggregateDao.getByIdFlow(id)

    fun getAllFlow(): Flow<List<AggregateEntity>> = aggregateDao.getAllFlow()

    suspend fun getAllOnce(): List<AggregateEntity> = aggregateDao.getAllOnce()

    fun getRecentFlow(limit: Int = 10): Flow<List<AggregateEntity>> =
        aggregateDao.getRecentFlow(limit)

    fun getLowConfidenceFlow(threshold: Int = 50): Flow<List<AggregateEntity>> =
        aggregateDao.getLowConfidenceFlow(threshold)

    fun getByDateRange(startMillis: Long, endMillis: Long): Flow<List<AggregateEntity>> =
        aggregateDao.getByDateRange(startMillis, endMillis)

    fun getDistinctAccountsFlow(): Flow<List<String>> =
        aggregateDao.getDistinctAccountsFlow()

    fun searchAggregates(
        query: String?,
        accountFilter: String?,
        directionFilter: String? = null,
        startMillis: Long? = null,
        endMillis: Long? = null,
        minAmountMinor: Long? = null,
        maxAmountMinor: Long? = null,
        maxConfidence: Int? = null
    ): Flow<List<AggregateEntity>> = aggregateDao.searchAggregates(
        query, accountFilter, directionFilter,
        startMillis, endMillis, minAmountMinor, maxAmountMinor, maxConfidence
    )

    suspend fun totalIncomeForPeriod(startMillis: Long, endMillis: Long): Long =
        aggregateDao.totalIncomeForPeriod(startMillis, endMillis)

    suspend fun totalExpenseForPeriod(startMillis: Long, endMillis: Long): Long =
        aggregateDao.totalExpenseForPeriod(startMillis, endMillis)

    suspend fun linkObservation(aggregateId: String, observationId: String) {
        aggregateDao.linkObservation(
            AggregateObservationEntity(
                aggregateId = aggregateId,
                observationId = observationId
            )
        )
    }

    suspend fun unlinkObservation(aggregateId: String, observationId: String) {
        aggregateDao.unlinkObservation(aggregateId, observationId)
    }

    suspend fun findAggregatesByFpRef(fpRef: String): List<AggregateEntity> =
        aggregateDao.findAggregatesByFpRef(fpRef)

    suspend fun findAggregatesByFpAmtDay(fpAmtDay: String): List<AggregateEntity> =
        aggregateDao.findAggregatesByFpAmtDay(fpAmtDay)

    suspend fun count(): Int = aggregateDao.count()

    suspend fun countLowConfidence(threshold: Int = 50): Int =
        aggregateDao.countLowConfidence(threshold)

    suspend fun delete(aggregate: AggregateEntity) = aggregateDao.delete(aggregate)
}
