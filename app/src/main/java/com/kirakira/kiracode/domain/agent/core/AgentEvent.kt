package com.kirakira.kiracode.domain.agent.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
sealed interface AgentEvent {
    val id: String
    val timestamp: Long
    val source: EventSource
}

enum class EventSource {
    AGENT, USER, ENVIRONMENT, HOOK
}

/** 流式增量（LLM 输出 delta，用于实时渲染） */
@Serializable
@SerialName("stream_delta")
data class StreamDeltaEvent(
    override val id: String = generateEventId(),
    override val timestamp: Long = System.currentTimeMillis(),
    override val source: EventSource = EventSource.AGENT,
    val text: String
) : AgentEvent

/** 思维链流式增量（ViewModel 收集到独立缓冲实时渲染，不进事件列表） */
@Serializable
@SerialName("reasoning_delta")
data class ReasoningDeltaEvent(
    override val id: String = generateEventId(),
    override val timestamp: Long = System.currentTimeMillis(),
    override val source: EventSource = EventSource.AGENT,
    val text: String
) : AgentEvent

/** 思维链定稿（一轮结束时携带完整思维文本，渲染为可折叠卡片） */
@Serializable
@SerialName("reasoning_complete")
data class ReasoningCompleteEvent(
    override val id: String = generateEventId(),
    override val timestamp: Long = System.currentTimeMillis(),
    override val source: EventSource = EventSource.AGENT,
    val text: String
) : AgentEvent

/** 助手正文定稿（工具调用轮的响应文本；流式卡片收束后以此保留正文） */
@Serializable
@SerialName("assistant_text")
data class AssistantTextEvent(
    override val id: String = generateEventId(),
    override val timestamp: Long = System.currentTimeMillis(),
    override val source: EventSource = EventSource.AGENT,
    val text: String
) : AgentEvent

/** 迭代预算警告（软着陆：剩余轮次不足时提醒模型加速收尾） */
@Serializable
@SerialName("budget_warning")
data class BudgetWarningEvent(
    override val id: String = generateEventId(),
    override val timestamp: Long = System.currentTimeMillis(),
    override val source: EventSource = EventSource.AGENT,
    val remainingIterations: Int,
    val message: String
) : AgentEvent

/** V3.1 任务5：流式 usage 尾帧事件（精确 token 统计，替代估算） */
@Serializable
@SerialName("usage_event")
data class UsageEvent(
    override val id: String = generateEventId(),
    override val timestamp: Long = System.currentTimeMillis(),
    override val source: EventSource = EventSource.AGENT,
    val promptTokens: Int,
    val completionTokens: Int,
    val totalTokens: Int,
    val cachedTokens: Int = 0
) : AgentEvent

/** V3.1 强制流式：系统提示事件（流式失败降级非流式时提示用户） */
@Serializable
@SerialName("system_notice")
data class SystemNoticeEvent(
    override val id: String = generateEventId(),
    override val timestamp: Long = System.currentTimeMillis(),
    override val source: EventSource = EventSource.AGENT,
    val message: String
) : AgentEvent

fun generateEventId(): String = UUID.randomUUID().toString()
