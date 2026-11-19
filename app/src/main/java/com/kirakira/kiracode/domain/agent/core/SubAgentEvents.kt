package com.kirakira.kiracode.domain.agent.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 子代理（Tool-as-Agent）生命周期事件：spawn_subagent 工具触发的独立上下文执行。
 *
 * 语义：
 * - 子代理只返回候选文本/分析结果，不直接修改角色卡；
 * - 结果以 tool role 回注主 Agent，由主 Agent 审核后决定是否用卡片工具写入；
 * - Started → Completed/Failed 构成一次完整生命周期（subagentId 关联三段）；
 * - 主循环取消时 Started 后可能没有终态事件，ViewModel 取消分支负责收敛运行中状态。
 */

/** 子代理开始执行（独立 LLM 请求发起前） */
@Serializable
@SerialName("subagent_started")
data class SubAgentStartedEvent(
    override val id: String = generateEventId(),
    override val timestamp: Long = System.currentTimeMillis(),
    override val source: EventSource = EventSource.AGENT,
    /** 本次子代理执行的唯一标识（Started/Completed/Failed 三段共享） */
    val subagentId: String,
    /** 子任务描述（单一、明确） */
    val task: String,
    /** 执行开始时间（与 timestamp 同源，显式命名以满足生命周期语义） */
    val startedAt: Long = System.currentTimeMillis(),
    /** 触发本子代理的 spawn_subagent tool_call.id */
    val toolCallId: String = "",
    /** 归属主 Agent 标识（当前单主 Agent 架构下为固定值，预留多主 Agent 扩展） */
    val parentAgentId: String = "main"
) : AgentEvent

/** 子代理完成（返回候选内容；尚未写入角色卡） */
@Serializable
@SerialName("subagent_completed")
data class SubAgentCompletedEvent(
    override val id: String = generateEventId(),
    override val timestamp: Long = System.currentTimeMillis(),
    override val source: EventSource = EventSource.AGENT,
    val subagentId: String,
    val task: String,
    /** 子代理产出（UI 展示用，已按卡片预算截断） */
    val output: String,
    /** 一行预览（时间轴默认展示） */
    val outputPreview: String = "",
    val toolCallId: String = "",
    val startedAt: Long,
    val finishedAt: Long,
    val parentAgentId: String = "main"
) : AgentEvent

/** 子代理失败（错误原因回注主 Agent，由主 Agent 决定重试或自行完成） */
@Serializable
@SerialName("subagent_failed")
data class SubAgentFailedEvent(
    override val id: String = generateEventId(),
    override val timestamp: Long = System.currentTimeMillis(),
    override val source: EventSource = EventSource.AGENT,
    val subagentId: String,
    val task: String,
    val errorMessage: String,
    val toolCallId: String = "",
    val startedAt: Long,
    val finishedAt: Long,
    val parentAgentId: String = "main"
) : AgentEvent
