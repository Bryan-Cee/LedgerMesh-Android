package com.example.ledgermesh.di

import android.content.Context
import androidx.room.Room
import com.example.ledgermesh.data.db.LedgerMeshDatabase
import com.example.ledgermesh.data.db.dao.AggregateDao
import com.example.ledgermesh.data.db.dao.CategoryDao
import com.example.ledgermesh.data.db.dao.ImportSessionDao
import com.example.ledgermesh.data.db.dao.ObservationDao
import com.example.ledgermesh.data.db.dao.OpsLogDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory
import javax.inject.Singleton

/**
 * Hilt module that provides the SQLCipher-encrypted Room database and all DAOs
 * as singleton-scoped dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): LedgerMeshDatabase {
        // TODO: In production, derive the passphrase from the Android Keystore
        //  so that it is hardware-backed and not present in the APK.
        val passphrase = SQLiteDatabase.getBytes("ledgermesh-dev-key".toCharArray())
        val factory = SupportFactory(passphrase)

        return Room.databaseBuilder(
            context,
            LedgerMeshDatabase::class.java,
            "ledgermesh.db"
        )
            .openHelperFactory(factory)
            .build()
    }

    @Provides
    fun provideObservationDao(db: LedgerMeshDatabase): ObservationDao = db.observationDao()

    @Provides
    fun provideAggregateDao(db: LedgerMeshDatabase): AggregateDao = db.aggregateDao()

    @Provides
    fun provideImportSessionDao(db: LedgerMeshDatabase): ImportSessionDao =
        db.importSessionDao()

    @Provides
    fun provideOpsLogDao(db: LedgerMeshDatabase): OpsLogDao = db.opsLogDao()

    @Provides
    fun provideCategoryDao(db: LedgerMeshDatabase): CategoryDao = db.categoryDao()
}
