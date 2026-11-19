package com.kirakira.kiracode.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Delete
import androidx.room.Update
import com.kirakira.kiracode.data.local.db.entity.CharacterCardEntity
import kotlinx.coroutines.flow.Flow

/**
 * 角色卡 DAO
 * 提历史记录相关数据库操作
 */
@Dao
interface CharacterCardDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(card: CharacterCardEntity): Long

    @Update
    suspend fun update(card: CharacterCardEntity): Int

    @Delete
    suspend fun delete(card: CharacterCardEntity): Int

    @Query("DELETE FROM character_cards WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    @Query("SELECT * FROM character_cards WHERE id = :id")
    suspend fun getById(id: Long): CharacterCardEntity?

    @Query("SELECT * FROM character_cards WHERE uuid = :uuid LIMIT 1")
    suspend fun getByUuid(uuid: String): CharacterCardEntity?

    @Query("SELECT * FROM character_cards ORDER BY updatedAt DESC LIMIT :limit OFFSET :offset")
    suspend fun getAll(limit: Int, offset: Int): List<CharacterCardEntity>

    @Query("SELECT * FROM character_cards ORDER BY updatedAt DESC")
    fun getAllFlow(): Flow<List<CharacterCardEntity>>

    @Query("SELECT COUNT(*) FROM character_cards")
    suspend fun getCount(): Int

    @Query("DELETE FROM character_cards")
    suspend fun deleteAll(): Int
}