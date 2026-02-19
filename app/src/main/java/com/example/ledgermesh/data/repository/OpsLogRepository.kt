package com.example.ledgermesh.data.repository

import com.example.ledgermesh.data.db.dao.OpsLogDao
import com.example.ledgermesh.data.db.entity.OpsLogEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OpsLogRepository @Inject constructor(
    private val opsLogDao: OpsLogDao
) {
    suspend fun insert(op: OpsLogEntity) = opsLogDao.insert(op)

    fun getForAggregate(aggregateId: String): Flow<List<OpsLogEntity>> =
        opsLogDao.getForAggregate(aggregateId)

    fun getAllFlow(): Flow<List<OpsLogEntity>> = opsLogDao.getAllFlow()

    fun getRecentFlow(limit: Int = 20): Flow<List<OpsLogEntity>> =
        opsLogDao.getRecentFlow(limit)
}
