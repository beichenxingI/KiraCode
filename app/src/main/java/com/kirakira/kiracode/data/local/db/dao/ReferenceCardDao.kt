package com.kirakira.kiracode.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Delete
import androidx.room.Update
import com.kirakira.kiracode.data.local.db.entity.ReferenceCardEntity
import kotlinx.coroutines.flow.Flow

/**
 * 参考卡 DAO
 * 提供参考卡相关数据库操作
 */
@Dao
interface ReferenceCardDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(card: ReferenceCardEntity): Long

    @Update
    suspend fun update(card: ReferenceCardEntity): Int

    @Delete
    suspend fun delete(card: ReferenceCardEntity): Int

    @Query("DELETE FROM reference_cards WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    @Query("SELECT * FROM reference_cards WHERE id = :id")
    suspend fun getById(id: Long): ReferenceCardEntity?

    @Query("SELECT * FROM reference_cards WHERE uuid = :uuid LIMIT 1")
    suspend fun getByUuid(uuid: String): ReferenceCardEntity?

    @Query("SELECT * FROM reference_cards ORDER BY updatedAt DESC LIMIT :limit OFFSET :offset")
    suspend fun getAll(limit: Int, offset: Int): List<ReferenceCardEntity>

    @Query("SELECT * FROM reference_cards ORDER BY updatedAt DESC")
    fun getAllFlow(): Flow<List<ReferenceCardEntity>>

    @Query("SELECT COUNT(*) FROM reference_cards")
    suspend fun getCount(): Int

    @Query("SELECT * FROM reference_cards WHERE source = :source ORDER BY updatedAt DESC")
    suspend fun getBySource(source: String): List<ReferenceCardEntity>

    @Query("DELETE FROM reference_cards")
    suspend fun deleteAll(): Int

    @Query("UPDATE reference_cards SET styleFeaturesJson = :styleFeaturesJson, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateStyleFeatures(id: Long, styleFeaturesJson: String, updatedAt: Long): Int
}