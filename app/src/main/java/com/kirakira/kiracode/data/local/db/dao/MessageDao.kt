package com.kirakira.kiracode.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Delete
import androidx.room.Update
import com.kirakira.kiracode.data.local.db.entity.MessageEntity
import kotlinx.coroutines.flow.Flow

/**
 * 消息 DAO
 * 提供消息相关数据库操作
 */
@Dao
interface MessageDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: MessageEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(messages: List<MessageEntity>): List<Long>

    @Update
    suspend fun update(message: MessageEntity): Int

    @Delete
    suspend fun delete(message: MessageEntity): Int

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    @Query("DELETE FROM messages WHERE sessionId = :sessionId")
    suspend fun deleteBySessionId(sessionId: Long): Int

    @Query("SELECT * FROM messages WHERE id = :id")
    suspend fun getById(id: Long): MessageEntity?

    @Query("SELECT * FROM messages WHERE sessionId = :sessionId ORDER BY createdAt ASC LIMIT :limit OFFSET :offset")
    suspend fun getBySessionId(sessionId: Long, limit: Int, offset: Int): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE sessionId = :sessionId ORDER BY createdAt ASC")
    fun getBySessionIdFlow(sessionId: Long): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE sessionId = :sessionId ORDER BY createdAt ASC LIMIT :limit")
    suspend fun getRecentBySessionId(sessionId: Long, limit: Int): List<MessageEntity>

    @Query("SELECT COUNT(*) FROM messages WHERE sessionId = :sessionId")
    suspend fun getCountBySessionId(sessionId: Long): Int

    @Query("SELECT SUM(tokens) FROM messages WHERE sessionId = :sessionId")
    suspend fun getTotalTokensBySessionId(sessionId: Long): Int?

    @Query("DELETE FROM messages WHERE sessionId = :sessionId AND id NOT IN (SELECT id FROM messages WHERE sessionId = :sessionId ORDER BY createdAt DESC LIMIT :keepCount)")
    suspend fun trimOldMessages(sessionId: Long, keepCount: Int): Int
}