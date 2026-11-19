package com.kirakira.kiracode.domain.agent.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 交互式 Agent 事件（V2/V3）：
 * ask_user 追问 / submit_plan 制卡计划 / complete_step 进度 / Diff 审查。
 *
 * 语义：这三类 Action 发出后内核循环【挂起】等待用户回复
 * （CompletableDeferred，由 ViewModel 端 respondToAgent 完成），
 * 回复以 tool role 回注后循环继续——保证 AI 不会在用户未决策时自顾自推进。
 */

/** 计划步骤（submit_plan 的结构化载荷） */
@Serializable
data class PlanStepData(
    val title: String,
    val detail: String = ""
)

/** 追问动作：AI 向用户提问（可带候选选项），挂起等待回答 */
@Serializable
@SerialName("ask_user")
data class AskUserAction(
    override val id: String = generateEventId(),
    override val timestamp: Long = System.currentTimeMillis(),
    val question: String,
    /** 候选选项（可空；跳过后 AI 会给出 3 个选项） */
    val options: List<String> = emptyList(),
    /** 问题主题（如"性格成因"，用于跳过按钮提示） */
    val about: String = "",
    val toolCallId: String = ""
) : Action

/** 制卡计划动作：AI 提交步骤清单，挂起等待用户确认/修改 */
@Serializable
@SerialName("submit_plan")
data class PlanAction(
    override val id: String = generateEventId(),
    override val timestamp: Long = System.currentTimeMillis(),
    val steps: List<PlanStepData>,
    val toolCallId: String = ""
) : Action

/**
 * 预算耗尽动作：内核挂起等待用户决定继续/保存/放弃。
 * 复用现有 awaitUserResponse 通道，避免重新派发丢失工具历史。
 */
@Serializable
@SerialName("budget_exceeded_action")
data class BudgetExceededAction(
    override val id: String = generateEventId(),
    override val timestamp: Long = System.currentTimeMillis(),
    val completedSummary: String,
    val pendingSteps: List<String>,
    val iterationsUsed: Int,
    val toolCallId: String = ""
) : Action

/** 步骤进度事件（complete_step 触发；"已完成 N/M 步"） */
@Serializable
@SerialName("step_progress")
data class StepProgressEvent(
    override val id: String = generateEventId(),
    override val timestamp: Long = System.currentTimeMillis(),
    override val source: EventSource = EventSource.AGENT,
    val done: Int,
    val total: Int,
    /** 当前完成步骤的标题（空=仅摘要） */
    val title: String = "",
    val summary: String = "",
    /** 本次完成的步骤序号（1-based；0=旧版按 done 顺序推断） */
    val stepIndex: Int = 0
) : AgentEvent

/** Diff 审查动作：覆盖已有非空字段前展示"删除线旧版+绿色新版"，挂起等待用户决定 */
@Serializable
@SerialName("diff_proposal")
data class DiffProposalAction(
    override val id: String = generateEventId(),
    override val timestamp: Long = System.currentTimeMillis(),
    /** 字段 key（snake_case，如 first_mes） */
    val fieldKey: String,
    /** 中文标签（如"开场白"） */
    val fieldLabel: String,
    val oldValue: String,
    val newValue: String,
    val toolCallId: String = ""
) : Action

/** 自动草稿模式事件（强制推进：连续无有效输入后 AI 直接补全并标注脑补） */
@Serializable
@SerialName("auto_draft")
data class AutoDraftEvent(
    override val id: String = generateEventId(),
    override val timestamp: Long = System.currentTimeMillis(),
    override val source: EventSource = EventSource.AGENT,
    val message: String
) : AgentEvent
