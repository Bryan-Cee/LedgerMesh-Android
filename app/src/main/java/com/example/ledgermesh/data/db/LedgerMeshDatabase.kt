package com.example.ledgermesh.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.ledgermesh.data.db.dao.AggregateDao
import com.example.ledgermesh.data.db.dao.CategoryDao
import com.example.ledgermesh.data.db.dao.ImportSessionDao
import com.example.ledgermesh.data.db.dao.ObservationDao
import com.example.ledgermesh.data.db.dao.OpsLogDao
import com.example.ledgermesh.data.db.entity.AggregateEntity
import com.example.ledgermesh.data.db.entity.AggregateObservationEntity
import com.example.ledgermesh.data.db.entity.CategoryEntity
import com.example.ledgermesh.data.db.entity.ImportSessionEntity
import com.example.ledgermesh.data.db.entity.ObservationEntity
import com.example.ledgermesh.data.db.entity.OpsLogEntity

@Database(
    entities = [
        ObservationEntity::class,
        AggregateEntity::class,
        AggregateObservationEntity::class,
        ImportSessionEntity::class,
        OpsLogEntity::class,
        CategoryEntity::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class LedgerMeshDatabase : RoomDatabase() {
    abstract fun observationDao(): ObservationDao
    abstract fun aggregateDao(): AggregateDao
    abstract fun importSessionDao(): ImportSessionDao
    abstract fun opsLogDao(): OpsLogDao
    abstract fun categoryDao(): CategoryDao
}
