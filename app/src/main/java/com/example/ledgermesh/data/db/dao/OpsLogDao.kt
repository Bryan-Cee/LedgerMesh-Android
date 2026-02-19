package com.example.ledgermesh.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.ledgermesh.data.db.entity.OpsLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface OpsLogDao {

    @Insert
    suspend fun insert(op: OpsLogEntity)

    @Query("SELECT * FROM ops_log WHERE targetAggregateId = :aggregateId ORDER BY createdAt DESC")
    fun getForAggregate(aggregateId: String): Flow<List<OpsLogEntity>>

    @Query("SELECT * FROM ops_log ORDER BY createdAt DESC")
    fun getAllFlow(): Flow<List<OpsLogEntity>>

    @Query("SELECT * FROM ops_log ORDER BY createdAt DESC LIMIT :limit")
    fun getRecentFlow(limit: Int): Flow<List<OpsLogEntity>>

    @Query("DELETE FROM ops_log")
    suspend fun deleteAll()
}
