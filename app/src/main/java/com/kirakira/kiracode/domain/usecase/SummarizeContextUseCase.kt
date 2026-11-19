package com.kirakira.kiracode.domain.usecase

import com.kirakira.kiracode.domain.exception.NetworkException
import com.kirakira.kiracode.domain.model.Message
import com.kirakira.kiracode.domain.model.Session
import com.kirakira.kiracode.data.remote.real.RealApiService
import com.kirakira.kiracode.domain.repository.MessageRepository
import com.kirakira.kiracode.domain.repository.SessionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * 上下文自动总结用例
 * 当对话历史超过模型上下文窗口的 80% 时，自动将较早的消息总结成摘要
 */
class SummarizeContextUseCase @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val messageRepository: MessageRepository,
    private val realApiService: RealApiService,
    private val chatEngine: ChatEngine
) {

    /**
     * 执行上下文总结
     * @param sessionId 会话 ID
     * @param maxContextTokens 模型最大上下文 Token 数
     * @param thresholdPercent 触发阈值百分比 (默认 80%)
     * @return 总结后的摘要文本
     */
    suspend operator fun invoke(
        sessionId: Long,
        maxContextTokens: Int = 4096,
        thresholdPercent: Float = 0.8f
    ): Result<String> = withContext(Dispatchers.IO) {
        // 1. 获取会话信息
        val sessionResult = sessionRepository.getSession(sessionId)
        val session = sessionResult.getOrNull() ?: return@withContext Result.failure(
            NetworkException("会话不存在", errorCode = "SESSION_NOT_FOUND")
        )

        // 2. 获取当前总 Token 数
        val totalTokensResult = messageRepository.getTotalTokens(sessionId)
        val totalTokens = totalTokensResult.getOrNull() ?: 0

        // 3. 检查是否需要总结
        val threshold = (maxContextTokens * thresholdPercent).toInt()
        if (totalTokens < threshold) {
            return@withContext Result.success(session.summary) // 不需要总结，返回现有摘要
        }

        // 4. 获取需要总结的消息 (保留最近的消息，总结较早的)
        val messagesResult = messageRepository.getMessagesBySessionId(sessionId, limit = Int.MAX_VALUE)
        val allMessages = messagesResult.getOrNull() ?: emptyList()

        if (allMessages.size < 10) {
            return@withContext Result.success(session.summary) // 消息太少，不需要总结
        }

        // 保留最近 6 条消息，总结其余的
        val keepCount = 6
        val messagesToSummarize = allMessages.dropLast(keepCount)
        val recentMessages = allMessages.takeLast(keepCount)

        if (messagesToSummarize.isEmpty()) {
            return@withContext Result.success(session.summary)
        }

        // 5. 构建总结提示词
        val summaryPrompt = buildSummaryPrompt(messagesToSummarize, session)

        // 6. 调用 AI 生成真实摘要（复用激活配置；失败时保留旧摘要，不破坏数据）
        val summary = generateSummary(summaryPrompt, maxContextTokens)

        // 7. 更新会话摘要 + 从历史中移除被总结的旧消息（真正释放上下文）
        summary.onSuccess { summaryText ->
            sessionRepository.updateSummary(sessionId, summaryText)
            // 只保留最近 keepCount 条消息，其余旧消息从数据库删除 → 下次 API 请求不再携带
            val trimmed = messageRepository.trimOldMessages(sessionId, keepCount)
            android.util.Log.d(
                "KiraCard/Summarize",
                "总结完成：摘要 ${summaryText.length} 字，裁剪旧消息 $trimmed 条"
            )
        }

        summary
    }

    /**
     * 手动触发总结
     */
    suspend fun summarizeManually(sessionId: Long, maxContextTokens: Int = 4096): Result<String> = withContext(Dispatchers.IO) {
        val sessionResult = sessionRepository.getSession(sessionId)
        val session = sessionResult.getOrNull() ?: return@withContext Result.failure(
            NetworkException("会话不存在", errorCode = "SESSION_NOT_FOUND")
        )

        val messagesResult = messageRepository.getMessagesBySessionId(sessionId, limit = Int.MAX_VALUE)
        val allMessages = messagesResult.getOrNull() ?: emptyList()

        if (allMessages.isEmpty()) {
            return@withContext Result.success("")
        }

        val summaryPrompt = buildSummaryPrompt(allMessages, session)
        val summary = generateSummary(summaryPrompt, maxContextTokens)

        // 手动总结同样从历史中移除被总结的旧消息（保留最近 6 条）
        summary.onSuccess { summaryText ->
            sessionRepository.updateSummary(sessionId, summaryText)
            val trimmed = messageRepository.trimOldMessages(sessionId, 6)
            android.util.Log.d("KiraCard/Summarize", "手动总结完成，裁剪旧消息 $trimmed 条")
        }

        summary
    }

    /**
     * 构建总结提示词
     */
    private fun buildSummaryPrompt(messages: List<Message>, session: Session): String {
        val conversation = messages.map { msg ->
            val roleName = when (msg.role) {
                "user" -> "用户"
                "assistant" -> "助手"
                "system" -> "系统"
                else -> msg.role
            }
            "$roleName: ${msg.content}"
        }.joinToString("\n\n")

        return """
            请将以下对话历史总结为简洁的摘要，用于作为后续对话的上下文背景。
            
            会话主题：${session.title}
            当前系统提示词：${session.systemPrompt.ifBlank { "无" }}
            
            对话历史：
            $conversation
            
            要求：
            1. 保留关键信息：主要人物、重要事件、关键决策、情感状态变化
            2. 压缩冗余内容，保持核心脉络
            3. 使用第三人称客观叙述
            4. 长度控制在 500 字以内
            5. 只输出摘要内容，不要包含解释
        """.trimIndent()
    }

    /**
     * 生成摘要 (调用当前激活的 AI API 配置，复用 ChatEngine 的配置解析)
     */
    private suspend fun generateSummary(prompt: String, maxTokens: Int): Result<String> {
        val config = chatEngine.resolveActiveConfig()
            ?: return Result.failure(IllegalStateException("尚未配置 AI 接口"))
        return realApiService.chatCompletion(
            messages = listOf(
                com.kirakira.kiracode.data.remote.dto.ChatMessage(
                    role = "system",
                    content = "你是一个专业的对话摘要助手。将用户提供的对话历史压缩为简洁的中文摘要，保留关键人物、事件、决策与已完成的操作，控制在 500 字以内，只输出摘要正文。"
                ),
                com.kirakira.kiracode.data.remote.dto.ChatMessage(role = "user", content = prompt)
            ),
            config = config
        )
    }
}

/**
 * Token 估算工具
 * 用于估算文本的 Token 数量，用于上下文窗口管理
 */
object TokenEstimator {

    /**
     * 估算文本的 Token 数
     * 中文约 1.5 token/字，英文约 1 token/词，代码约 1 token/4字符
     * 这是近似值，实际应使用 tiktoken 等专业 tokenizer
     */
    fun estimateTokens(text: String): Int {
        if (text.isBlank()) return 0
        
        val chineseChars = text.count { it in '一'..'龥' || it in 'ぁ'..'ん' || it in 'ァ'..'ヶ' }
        val otherChars = text.length - chineseChars
        
        // 中文按 1.5 token/字，其他按 0.25 token/字符 (约 4 字符/token)
        return (chineseChars * 1.5 + otherChars * 0.25).toInt()
    }

    /**
     * 估算消息列表的总 Token 数
     */
    fun estimateMessagesTokens(messages: List<Message>): Int {
        return messages.sumOf { msg ->
            estimateTokens(msg.content) + 
            (msg.name?.let { estimateTokens(it) } ?: 0) +
            4 // 每条消息的角色标记等开销约 4 token
        }
    }

    /**
     * 检查是否超过上下文窗口阈值
     */
    fun shouldSummarize(messages: List<Message>, maxContextTokens: Int, thresholdPercent: Float = 0.8f): Boolean {
        val totalTokens = estimateMessagesTokens(messages)
        val threshold = (maxContextTokens * thresholdPercent).toInt()
        return totalTokens >= threshold
    }

    /**
     * 计算需要保留的最近消息数量以适应上下文窗口
     */
    fun calculateKeepCount(messages: List<Message>, maxContextTokens: Int, thresholdPercent: Float = 0.8f): Int {
        val threshold = (maxContextTokens * thresholdPercent).toInt()
        var tokenSum = 0
        var keepCount = 0
        
        // 从最新消息开始累加，直到达到阈值
        for (msg in messages.reversed()) {
            val msgTokens = estimateTokens(msg.content) + 4
            if (tokenSum + msgTokens > threshold && keepCount > 0) {
                break
            }
            tokenSum += msgTokens
            keepCount++
        }
        
        return keepCount.coerceAtLeast(4) // 至少保留 4 条
    }
}