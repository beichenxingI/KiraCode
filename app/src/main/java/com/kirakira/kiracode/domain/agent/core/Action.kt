package com.kirakira.kiracode.domain.agent.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface Action : AgentEvent {
    override val source: EventSource
        get() = EventSource.AGENT
}

@Serializable
@SerialName("think")
data class ThinkAction(
    override val id: String = generateEventId(),
    override val timestamp: Long = System.currentTimeMillis(),
    val thought: String,
    /** 本轮 token 用量（流式路径可能为 null；由上层做上下文统计） */
    @kotlinx.serialization.Transient
    val promptTokens: Int? = null,
    @kotlinx.serialization.Transient
    val completionTokens: Int? = null,
    /** 非流式路径的缓存命中 token（流式路径由 UsageEvent 提供） */
    @kotlinx.serialization.Transient
    val cachedTokens: Int? = null
) : Action

@Serializable
@SerialName("finish")
data class FinishAction(
    override val id: String = generateEventId(),
    override val timestamp: Long = System.currentTimeMillis(),
    val outputs: Map<String, String>
) : Action
