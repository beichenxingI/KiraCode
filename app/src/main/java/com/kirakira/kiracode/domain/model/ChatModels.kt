package com.kirakira.kiracode.domain.model

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * 聊天消息 UI 模型
 * 用于聊天界面显示，包含动画状态等 UI 相关字段
 */
@Serializable
data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val sessionId: Long,
    val role: String,           // "user" | "assistant" | "system"
    val content: String,        // 消息内容
    val isStreaming: Boolean = false,  // 是否正在流式输出
    val timestamp: Long = System.currentTimeMillis(),
    val metadata: String = "{}", // 扩展元数据 JSON
    /** AI 思考过程（工作台可视化；仅内存展示，不持久化） */
    val thinking: String = ""
) {
    companion object {
        fun fromMessage(message: Message): ChatMessage {
            return ChatMessage(
                id = if (message.id > 0) message.id.toString() else java.util.UUID.randomUUID().toString(),
                sessionId = message.sessionId,
                role = message.role,
                content = message.content,
                timestamp = message.createdAt,
                thinking = message.thinking
            )
        }
        
        fun createUserMessage(sessionId: Long, content: String): ChatMessage {
            return ChatMessage(
                sessionId = sessionId,
                role = "user",
                content = content
            )
        }
        
        fun createAssistantMessage(sessionId: Long, content: String = "", isStreaming: Boolean = true): ChatMessage {
            return ChatMessage(
                sessionId = sessionId,
                role = "assistant",
                content = content,
                isStreaming = isStreaming
            )
        }
        
        fun createSystemMessage(sessionId: Long, content: String): ChatMessage {
            return ChatMessage(
                sessionId = sessionId,
                role = "system",
                content = content
            )
        }
    }
    
    fun copyWithContent(newContent: String, isStreaming: Boolean = false, thinking: String? = null): ChatMessage {
        return this.copy(
            content = newContent,
            isStreaming = isStreaming,
            thinking = thinking ?: this.thinking
        )
    }
    
    fun copyAsFinished(): ChatMessage {
        return this.copy(isStreaming = false)
    }
}

/**
 * AI 工作台活动条目（OpenCode 风格过程可视化）。
 */
data class ActivityEntry(
    val id: String = UUID.randomUUID().toString(),
    val type: ActivityType,
    val title: String,
    val detail: String = "",
    val status: ActivityStatus = ActivityStatus.DONE,
    val timestamp: Long = System.currentTimeMillis()
) {
    /** 运行中的同主题条目可被更新（按 tag 匹配） */
    val tag: String get() = "${type.name}:$title"
}

enum class ActivityType { THINKING, GENERATION, ACTION, TOOL, PLAN, SYSTEM }

enum class ActivityStatus { RUNNING, DONE, FAILED }

/**
 * 思考强度探测状态（供 UI 展示）
 */
data class ReasoningUiState(
    val supported: Boolean,
    val probing: Boolean = false,
    val familyLabel: String = ""
)

/**
 * 会话 UI 状态
 * 用于聊天界面的会话列表显示
 */
data class ChatSession(
    val id: Long,
    val uuid: String,
    val title: String,
    val lastMessage: String? = null,
    val lastMessageTime: Long = 0,
    val messageCount: Int = 0,
    val isArchived: Boolean = false,
    val unreadCount: Int = 0,
    val isPinned: Boolean = false
) {
    fun getDisplayTitle(): String = if (title.isBlank()) "新会话" else title
}