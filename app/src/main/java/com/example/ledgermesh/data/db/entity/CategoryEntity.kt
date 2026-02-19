package com.example.ledgermesh.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * A user-defined or default spending/income category that can be assigned
 * to [AggregateEntity] records for analytics and filtering.
 */
@Entity(
    tableName = "categories",
    indices = [
        Index(value = ["name"], unique = true)
    ]
)
data class CategoryEntity(
    @PrimaryKey
    val categoryId: String = UUID.randomUUID().toString(),
    val name: String,
    /** Material icon name (e.g. "restaurant", "shopping_cart"). */
    val icon: String? = null,
    /** Hex colour string including '#' (e.g. "#FF5722"). */
    val colorHex: String? = null,
    val isDefault: Boolean = false
)
