package com.kirakira.kiracode.domain.model

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * 会话领域模型
 * 表示一个完整的聊天会话，包含元数据和配置
 */
@Serializable
data class Session(
    val id: Long = 0,
    val title: String,
    val summary: String = "",           // 自动总结的上下文摘要
    val systemPrompt: String = "",      // 会话级系统提示词
    val model: String = "gpt-4o-mini",  // 使用的模型
    val temperature: Float = 0.8f,      // 温度参数
    val maxTokens: Int = 4096,          // 最大 Token
    val referenceCardId: Long? = null,  // 关联的参考卡 ID
    val uuid: String = UUID.randomUUID().toString(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val messageCount: Int = 0,          // 消息数量
    val totalTokens: Int = 0,           // 总 Token 数
    val isArchived: Boolean = false,    // 是否归档
    val isPinned: Boolean = false,      // V3.0 是否置顶
    // V2.0 工作区持久化
    val workingCardJson: String = "",   // 绑定的工作卡 JSON（StCardCodec 线格式；空串=无）
    val workMode: String = "AGENT",     // CHAT / AGENT / AUTO
    val selectedReferenceIdsJson: String = "[]", // 选定的参考卡 ID 列表 JSON
    val selectedPreferenceIdsJson: String = "[]", // 会话级偏好选项 ID 列表 JSON
    // 阶段 B：定稿事件与计划/任务快照（恢复事件流用；不持久化短期 delta）
    val eventSnapshotJson: String = "[]"
) {
    /**
     * 创建新会话的工厂方法
     */
    companion object {
        fun create(
            title: String,
            model: String = "gpt-4o-mini",
            temperature: Float = 0.8f,
            maxTokens: Int = 4096,
            referenceCardId: Long? = null
        ): Session {
            val now = System.currentTimeMillis()
            return Session(
                title = title,
                model = model,
                temperature = temperature,
                maxTokens = maxTokens,
                referenceCardId = referenceCardId,
                createdAt = now,
                updatedAt = now
            )
        }
    }

    /**
     * 更新时间戳
     */
    fun copyWithUpdatedTimestamp(): Session {
        return this.copy(updatedAt = System.currentTimeMillis())
    }

    /**
     * 获取用于显示的简短标题
     */
    fun getDisplayTitle(): String {
        return if (title.isBlank()) "新会话 ${createdAt}" else title
    }

    /**
     * 判断是否为空会话
     */
    fun isEmpty(): Boolean = messageCount == 0
}

/**
 * 消息领域模型
 * 表示会话中的单条消息
 */
@Serializable
data class Message(
    val id: Long = 0,
    val sessionId: Long,
    val role: String,                   // "system" | "user" | "assistant" | "tool"
    val content: String,
    val name: String? = null,
    val toolCallsJson: String? = null,  // tool calls 的 JSON 字符串
    val toolCallId: String? = null,     // tool call 的 ID
    val tokens: Int = 0,                // 该消息的 Token 数
    val createdAt: Long = System.currentTimeMillis(),
    val metadataJson: String = "{}",    // 扩展元数据 JSON
    val thinking: String = ""           // AI 思考过程（可持久化）
) {
    companion object {
        fun create(
            sessionId: Long,
            role: String,
            content: String,
            name: String? = null,
            tokens: Int = 0,
            metadataJson: String = "{}",
            thinking: String = ""
        ): Message {
            return Message(
                sessionId = sessionId,
                role = role,
                content = content,
                name = name,
                tokens = tokens,
                metadataJson = metadataJson,
                thinking = thinking
            )
        }

        fun createSystemMessage(sessionId: Long, content: String): Message {
            return create(sessionId, "system", content)
        }

        fun createUserMessage(sessionId: Long, content: String): Message {
            return create(sessionId, "user", content)
        }

        fun createAssistantMessage(
            sessionId: Long,
            content: String,
            tokens: Int = 0,
            thinking: String = ""
        ): Message {
            return create(sessionId, "assistant", content, tokens = tokens, thinking = thinking)
        }
    }
}