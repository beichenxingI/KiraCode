package com.kirakira.kiracode.ui.workspace

/**
 * Agent 运行状态（单一派生源，阶段 D 细分）。
 *
 * 优先级：等待用户 > 等待计划 > 等待 Diff > 流式中 > 工具执行中 > 失败 > 空闲。
 * 全部由真实状态计算：
 * - pendingInteraction 类型 → 三种等待态；
 * - isSending（流式占位激活）→ STREAMING；
 * - _isExecuting 且非流式（工具轮间隙）→ TOOL_RUNNING；
 * - 最近未清除错误 → ERROR；
 * - 其余 → IDLE（完成态由 IDLE 表示）。
 */
enum class AgentStatus {
    IDLE,
    STREAMING,
    TOOL_RUNNING,
    WAITING_USER,
    WAITING_PLAN,
    WAITING_DIFF,
    WAITING_BUDGET,
    ERROR
}

/** 是否处于执行中（命令栏据此显示停止入口） */
val AgentStatus.isBusy: Boolean
    get() = this == AgentStatus.STREAMING || this == AgentStatus.TOOL_RUNNING

/** 是否处于等待用户决策（输入栏据此切换回答提示） */
val AgentStatus.isWaitingForUser: Boolean
    get() = this == AgentStatus.WAITING_USER || this == AgentStatus.WAITING_PLAN ||
            this == AgentStatus.WAITING_DIFF || this == AgentStatus.WAITING_BUDGET

fun deriveAgentStatus(
    isSending: Boolean,
    isExecuting: Boolean,
    pendingInteraction: PendingInteraction?,
    errorMessage: String?
): AgentStatus = when {
    pendingInteraction is PendingInteraction.Question -> AgentStatus.WAITING_USER
    pendingInteraction is PendingInteraction.Plan -> AgentStatus.WAITING_PLAN
    pendingInteraction is PendingInteraction.Diff -> AgentStatus.WAITING_DIFF
    pendingInteraction is PendingInteraction.Budget -> AgentStatus.WAITING_BUDGET
    isSending -> AgentStatus.STREAMING
    isExecuting -> AgentStatus.TOOL_RUNNING
    errorMessage != null -> AgentStatus.ERROR
    else -> AgentStatus.IDLE
}
