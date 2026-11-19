package com.kirakira.kiracode.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Delete
import androidx.room.Update
import com.kirakira.kiracode.data.local.db.entity.DraftEntity
import kotlinx.coroutines.flow.Flow

/**
 * 草稿 DAO
 * 提供草稿相关数据库操作
 */
@Dao
interface DraftDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(draft: DraftEntity): Long

    @Update
    suspend fun update(draft: DraftEntity): Int

    @Delete
    suspend fun delete(draft: DraftEntity): Int

    @Query("DELETE FROM drafts WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    @Query("SELECT * FROM drafts WHERE id = :id")
    suspend fun getById(id: Long): DraftEntity?

    @Query("SELECT * FROM drafts WHERE uuid = :uuid LIMIT 1")
    suspend fun getByUuid(uuid: String): DraftEntity?

    @Query("SELECT * FROM drafts ORDER BY updatedAt DESC LIMIT :limit OFFSET :offset")
    suspend fun getAll(limit: Int, offset: Int): List<DraftEntity>

    @Query("SELECT * FROM drafts ORDER BY updatedAt DESC")
    fun getAllFlow(): Flow<List<DraftEntity>>

    @Query("SELECT COUNT(*) FROM drafts")
    suspend fun getCount(): Int

    @Query("DELETE FROM drafts")
    suspend fun deleteAll(): Int
}