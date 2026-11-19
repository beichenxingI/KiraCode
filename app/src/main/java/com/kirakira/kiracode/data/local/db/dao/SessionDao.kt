package com.kirakira.kiracode.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Delete
import androidx.room.Update
import com.kirakira.kiracode.data.local.db.entity.SessionEntity
import kotlinx.coroutines.flow.Flow

/**
 * 会话 DAO
 * 提供会话相关数据库操作
 */
@Dao
interface SessionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: SessionEntity): Long

    @Update
    suspend fun update(session: SessionEntity): Int

    @Delete
    suspend fun delete(session: SessionEntity): Int

    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun getById(id: Long): SessionEntity?

    @Query("SELECT * FROM sessions WHERE uuid = :uuid")
    suspend fun getByUuid(uuid: String): SessionEntity?

    @Query("SELECT * FROM sessions WHERE isArchived = 0 ORDER BY isPinned DESC, updatedAt DESC LIMIT :limit OFFSET :offset")
    suspend fun getActiveSessions(limit: Int, offset: Int): List<SessionEntity>

    @Query("SELECT * FROM sessions WHERE isArchived = 1 ORDER BY isPinned DESC, updatedAt DESC LIMIT :limit OFFSET :offset")
    suspend fun getArchivedSessions(limit: Int, offset: Int): List<SessionEntity>

    @Query("SELECT * FROM sessions ORDER BY updatedAt DESC")
    fun getAllSessionsFlow(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE isArchived = 0 ORDER BY updatedAt DESC")
    fun getActiveSessionsFlow(): Flow<List<SessionEntity>>

    @Query("SELECT COUNT(*) FROM sessions WHERE isArchived = 0")
    suspend fun getActiveCount(): Int

    @Query("SELECT COUNT(*) FROM sessions")
    suspend fun getTotalCount(): Int

    @Query("UPDATE sessions SET title = :title, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateTitle(id: Long, title: String, updatedAt: Long): Int

    @Query("UPDATE sessions SET summary = :summary, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateSummary(id: Long, summary: String, updatedAt: Long): Int

    @Query("UPDATE sessions SET messageCount = :messageCount, totalTokens = :totalTokens, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateStats(id: Long, messageCount: Int, totalTokens: Int, updatedAt: Long): Int

    /**
     * 原子增量：messageCount +1，totalTokens 加估算值，更新 updatedAt。
     * 不做读-写，避免并发竞争和查询失败时把 messageCount 覆写回 0。
     */
    @Query(
        """
        UPDATE sessions
        SET messageCount = messageCount + 1,
            totalTokens  = totalTokens  + :deltaTokens,
            updatedAt    = :updatedAt
        WHERE id = :id
        """
    )
    suspend fun incrementStats(id: Long, deltaTokens: Int, updatedAt: Long): Int

    @Query("UPDATE sessions SET isArchived = :archived, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setArchived(id: Long, archived: Boolean, updatedAt: Long): Int

    @Query("UPDATE sessions SET isPinned = :pinned WHERE id = :id")
    suspend fun setPinned(id: Long, pinned: Boolean): Int

    @Query("UPDATE sessions SET systemPrompt = :systemPrompt, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateSystemPrompt(id: Long, systemPrompt: String, updatedAt: Long): Int

    @Query(
        "UPDATE sessions SET workingCardJson = :workingCardJson, workMode = :workMode, " +
                "selectedReferenceIdsJson = :referenceIdsJson, updatedAt = :updatedAt WHERE id = :id"
    )
    suspend fun updateWorkspace(
        id: Long,
        workingCardJson: String,
        workMode: String,
        referenceIdsJson: String,
        updatedAt: Long
    ): Int

    @Query("UPDATE sessions SET selectedPreferenceIdsJson = :json, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateSelectedPreferences(id: Long, json: String, updatedAt: Long): Int

    @Query("UPDATE sessions SET model = :model, temperature = :temperature, maxTokens = :maxTokens WHERE id = :id")
    suspend fun updateModelSettings(id: Long, model: String, temperature: Float, maxTokens: Int): Int

    @Query("UPDATE sessions SET eventSnapshotJson = :json, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateEventSnapshot(id: Long, json: String, updatedAt: Long): Int

    @Query("DELETE FROM sessions WHERE isArchived = 1")
    suspend fun deleteAllArchived(): Int
}