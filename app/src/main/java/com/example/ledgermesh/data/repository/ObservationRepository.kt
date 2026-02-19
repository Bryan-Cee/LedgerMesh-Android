package com.example.ledgermesh.data.repository

import com.example.ledgermesh.data.db.dao.ObservationDao
import com.example.ledgermesh.data.db.entity.ObservationEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ObservationRepository @Inject constructor(
    private val observationDao: ObservationDao
) {
    suspend fun insert(observation: ObservationEntity): Long = observationDao.insert(observation)

    suspend fun insertAll(observations: List<ObservationEntity>): List<Long> =
        observationDao.insertAll(observations)

    suspend fun getById(id: String): ObservationEntity? = observationDao.getById(id)

    suspend fun getByContentHash(hash: String): ObservationEntity? =
        observationDao.getByContentHash(hash)

    fun getByImportSession(sessionId: String): Flow<List<ObservationEntity>> =
        observationDao.getByImportSession(sessionId)

    fun getAllFlow(): Flow<List<ObservationEntity>> = observationDao.getAllFlow()

    fun getObservationsForAggregate(aggregateId: String): Flow<List<ObservationEntity>> =
        observationDao.getObservationsForAggregate(aggregateId)

    suspend fun getObservationsForAggregateOnce(aggregateId: String): List<ObservationEntity> =
        observationDao.getObservationsForAggregateOnce(aggregateId)

    suspend fun getUnlinkedObservations(): List<ObservationEntity> =
        observationDao.getUnlinkedObservations()

    suspend fun findByFpRef(fpRef: String): List<ObservationEntity> =
        observationDao.findByFpRef(fpRef)

    suspend fun findByFpAmtDay(fpAmtDay: String): List<ObservationEntity> =
        observationDao.findByFpAmtDay(fpAmtDay)

    suspend fun count(): Int = observationDao.count()
}
