package com.kirakira.kiracode.domain.repository

import com.kirakira.kiracode.domain.model.PreferenceDimension
import com.kirakira.kiracode.domain.model.PreferenceOption
import kotlinx.coroutines.flow.Flow

interface PreferenceRepository {
    fun observeDimensions(): Flow<List<PreferenceDimension>>
    suspend fun ensureDefaults()
    suspend fun addDimension(label: String): Result<PreferenceDimension>
    suspend fun updateDimension(id: String, label: String): Result<Unit>
    suspend fun deleteDimension(id: String): Result<Unit>
    suspend fun addOption(dimensionId: String, label: String, rule: String): Result<PreferenceOption>
    suspend fun updateOption(id: String, label: String, rule: String): Result<Unit>
    suspend fun deleteOption(id: String): Result<Unit>
}
