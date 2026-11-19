package com.kirakira.kiracode.domain.repository

import com.kirakira.kiracode.domain.model.Message
import com.kirakira.kiracode.domain.model.Session
import kotlinx.coroutines.flow.Flow

/**
 * 会话仓库接口
 * 管理聊天会话的持久化
 */
interface SessionRepository {

    // ============ 会话管理 ============

    /**
     * 创建新会话
     * @return 新会话的 ID
     */
    suspend fun createSession(session: Session): Result<Long>

    /**
     * 根据 ID 查询会话
     */
    suspend fun getSession(id: Long): Result<Session?>

    /**
     * 根据 UUID 查询会话
     */
    suspend fun getSessionByUuid(uuid: String): Result<Session?>

    /**
     * 获取所有活跃会话 (按更新时间倒序，支持分页)
     */
    suspend fun getActiveSessions(limit: Int = 50, offset: Int = 0): Result<List<Session>>

    /**
     * 获取所有归档会话
     */
    suspend fun getArchivedSessions(limit: Int = 50, offset: Int = 0): Result<List<Session>>

    /**
     * 观察所有活跃会话 (Flow)
     */
    fun observeActiveSessions(): Flow<List<Session>>

    /**
     * 获取活跃会话总数
     */
    suspend fun getActiveCount(): Result<Int>

    /**
     * 更新会话标题
     */
    suspend fun updateTitle(id: Long, title: String): Result<Unit>

    /**
     * 更新会话摘要 (用于上下文总结)
     */
    suspend fun updateSummary(id: Long, summary: String): Result<Unit>

    /**
     * 更新会话统计信息
     */
    suspend fun updateStats(id: Long, messageCount: Int, totalTokens: Int): Result<Unit>

    /**
     * 原子增量统计：messageCount +1，totalTokens + deltaTokens，更新 updatedAt。
     * 避免 updateStats 的读-写竞争与查询失败降级。
     */
    suspend fun incrementStats(id: Long, deltaTokens: Int): Result<Unit>

    /**
     * 设置归档状态
     */
    suspend fun setArchived(id: Long, archived: Boolean): Result<Unit>

    /**
     * V3.0 设置置顶状态
     */
    suspend fun setPinned(id: Long, pinned: Boolean): Result<Unit>

    /**
     * 更新系统提示词
     */
    suspend fun updateSystemPrompt(id: Long, systemPrompt: String): Result<Unit>

    /**
     * 更新会话工作区（工作卡 JSON / 工作模式 / 参考卡选择）
     */
    suspend fun updateWorkspace(
        id: Long,
        workingCardJson: String,
        workMode: String,
        referenceIdsJson: String
    ): Result<Unit>

    /**
     * 更新会话模型参数
     */
    suspend fun updateModelSettings(id: Long, model: String, temperature: Float, maxTokens: Int): Result<Unit>

    /**
     * 更新会话级创作偏好选择（选项 ID 列表 JSON）
     */
    suspend fun updateSelectedPreferences(id: Long, preferenceIdsJson: String): Result<Unit>

    /**
     * 更新会话事件快照（定稿事件/计划/任务 JSON）
     */
    suspend fun updateEventSnapshot(id: Long, snapshotJson: String): Result<Unit>

    /**
     * 删除会话 (级联删除消息)
     */
    suspend fun deleteSession(id: Long): Result<Unit>

    /**
     * 删除所有归档会话
     */
    suspend fun deleteAllArchived(): Result<Int>
}

/**
 * 消息仓库接口
 * 管理会话消息的持久化
 */
interface MessageRepository {

    /**
     * 插入单条消息
     */
    suspend fun insertMessage(message: Message): Result<Long>

    /**
     * 批量插入消息
     */
    suspend fun insertMessages(messages: List<Message>): Result<List<Long>>

    /**
     * 根据会话 ID 获取消息 (分页)
     */
    suspend fun getMessagesBySessionId(sessionId: Long, limit: Int = 100, offset: Int = 0): Result<List<Message>>

    /**
     * 获取会话最近的消息
     */
    suspend fun getRecentMessages(sessionId: Long, limit: Int): Result<List<Message>>

    /**
     * 观察会话消息 (Flow，实时更新)
     */
    fun observeMessages(sessionId: Long): Flow<List<Message>>

    /**
     * 获取会话消息总数
     */
    suspend fun getMessageCount(sessionId: Long): Result<Int>

    /**
     * 获取会话总 Token 数
     */
    suspend fun getTotalTokens(sessionId: Long): Result<Int>

    /**
     * 删除会话的所有消息
     */
    suspend fun deleteMessagesBySessionId(sessionId: Long): Result<Unit>

    /**
     * 修剪旧消息 (保留最近 N 条)
     */
    suspend fun trimOldMessages(sessionId: Long, keepCount: Int): Result<Int>
}

/**
 * 参考卡仓库接口
 * 管理用于风格模仿的参考角色卡
 */
interface ReferenceCardRepository {

    /**
     * 保存参考卡
     */
    suspend fun saveReferenceCard(card: com.kirakira.kiracode.domain.model.CharacterCard, source: String): Result<Long>

    /**
     * 根据 ID 查询参考卡
     */
    suspend fun getReferenceCard(id: Long): Result<com.kirakira.kiracode.domain.model.CharacterCard?>

    /**
     * 根据 UUID 查询参考卡
     */
    suspend fun getReferenceCardByUuid(uuid: String): Result<com.kirakira.kiracode.domain.model.CharacterCard?>

    /**
     * 获取所有参考卡 (按更新时间倒序，支持分页)
     */
    suspend fun getAllReferenceCards(limit: Int = 100, offset: Int = 0): Result<List<com.kirakira.kiracode.domain.model.CharacterCard>>

    /**
     * 观察所有参考卡 (Flow)
     */
    fun observeReferenceCards(): Flow<List<com.kirakira.kiracode.domain.model.CharacterCard>>

    /**
     * 根据来源获取参考卡
     */
    suspend fun getReferenceCardsBySource(source: String): Result<List<com.kirakira.kiracode.domain.model.CharacterCard>>

    /**
     * 更新风格特征
     */
    suspend fun updateStyleFeatures(id: Long, styleFeaturesJson: String): Result<Unit>

    /**
     * 删除参考卡
     */
    suspend fun deleteReferenceCard(id: Long): Result<Unit>

    /**
     * 按 UUID 删除参考卡
     */
    suspend fun deleteReferenceCardByUuid(uuid: String): Result<Unit>

    /**
     * 删除所有参考卡
     */
    suspend fun deleteAllReferenceCards(): Result<Int>

    /**
     * 获取参考卡总数
     */
    suspend fun getReferenceCardCount(): Result<Int>
}