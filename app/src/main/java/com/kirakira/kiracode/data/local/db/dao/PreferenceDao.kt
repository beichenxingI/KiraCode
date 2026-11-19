package com.kirakira.kiracode.data.local.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.kirakira.kiracode.data.local.db.entity.PreferenceDimensionEntity
import com.kirakira.kiracode.data.local.db.entity.PreferenceOptionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PreferenceDao {

    @Query("SELECT * FROM preference_dimensions ORDER BY sortOrder")
    fun observeDimensions(): Flow<List<PreferenceDimensionEntity>>

    @Query("SELECT * FROM preference_options ORDER BY sortOrder")
    fun observeAllOptions(): Flow<List<PreferenceOptionEntity>>

    @Query("SELECT * FROM preference_dimensions ORDER BY sortOrder")
    suspend fun getAllDimensions(): List<PreferenceDimensionEntity>

    @Query("SELECT * FROM preference_dimensions WHERE id = :id")
    suspend fun getDimensionById(id: String): PreferenceDimensionEntity?

    @Query("SELECT * FROM preference_options WHERE dimensionId = :dimensionId ORDER BY sortOrder")
    suspend fun getOptionsForDimension(dimensionId: String): List<PreferenceOptionEntity>

    @Query("SELECT * FROM preference_options WHERE id = :id")
    suspend fun getOptionById(id: String): PreferenceOptionEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertDimension(entity: PreferenceDimensionEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertOption(entity: PreferenceOptionEntity)

    @Update
    suspend fun updateDimension(entity: PreferenceDimensionEntity)

    @Update
    suspend fun updateOption(entity: PreferenceOptionEntity)

    @Delete
    suspend fun deleteDimension(entity: PreferenceDimensionEntity)

    @Delete
    suspend fun deleteOption(entity: PreferenceOptionEntity)

    @Query("DELETE FROM preference_options WHERE dimensionId = :dimensionId")
    suspend fun deleteOptionsForDimension(dimensionId: String)
}
