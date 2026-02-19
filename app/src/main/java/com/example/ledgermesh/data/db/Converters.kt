package com.example.ledgermesh.data.db

import androidx.room.TypeConverter
import com.example.ledgermesh.domain.model.ImportStatus
import com.example.ledgermesh.domain.model.OperationType
import com.example.ledgermesh.domain.model.SourceType
import com.example.ledgermesh.domain.model.TransactionDirection

/**
 * Room [TypeConverter]s for persisting domain enums as their [String] names.
 */
class Converters {

    @TypeConverter
    fun fromSourceType(value: SourceType): String = value.name

    @TypeConverter
    fun toSourceType(value: String): SourceType = SourceType.valueOf(value)

    @TypeConverter
    fun fromTransactionDirection(value: TransactionDirection): String = value.name

    @TypeConverter
    fun toTransactionDirection(value: String): TransactionDirection =
        TransactionDirection.valueOf(value)

    @TypeConverter
    fun fromImportStatus(value: ImportStatus): String = value.name

    @TypeConverter
    fun toImportStatus(value: String): ImportStatus = ImportStatus.valueOf(value)

    @TypeConverter
    fun fromOperationType(value: OperationType): String = value.name

    @TypeConverter
    fun toOperationType(value: String): OperationType = OperationType.valueOf(value)
}
