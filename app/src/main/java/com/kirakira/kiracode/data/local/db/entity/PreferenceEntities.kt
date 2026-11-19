package com.kirakira.kiracode.data.local.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "preference_dimensions")
data class PreferenceDimensionEntity(
    @PrimaryKey val id: String,
    val label: String,
    val isBuiltin: Boolean,
    val sortOrder: Int
)

@Entity(
    tableName = "preference_options",
    indices = [Index(value = ["dimensionId"], name = "idx_preference_options_dimensionId")]
)
data class PreferenceOptionEntity(
    @PrimaryKey val id: String,
    val dimensionId: String,
    val label: String,
    val rule: String,
    val isBuiltin: Boolean,
    val sortOrder: Int
)
