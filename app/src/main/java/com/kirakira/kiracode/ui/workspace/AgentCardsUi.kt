package com.kirakira.kiracode.ui.workspace

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Agent 事件的结构化卡片（V2/V3）：渲染在聊天消息流中，
 * 让"工具调用 → observation → 追问 → 计划 → Diff"以可交互卡片呈现。
 *
 * 卡片按事件产生顺序追加；交互卡片（Question/Plan/Diff）在用户响应后
 * 记录决定状态（按钮变为状态徽标）。
 * @Serializable：会话事件快照持久化（阶段 B）。
 */
@Serializable
sealed class AgentCardUi {
    abstract val id: String
    abstract val timestamp: Long

    /** 工具调用卡片：修改字段描述；脑补字段带可点击标注 */
    @Serializable
    @SerialName("tool_call")
    data class ToolCall(
        override val id: String,
        override val timestamp: Long = System.currentTimeMillis(),
        val desc: String,
        val functionName: String,
        val isAiGuess: Boolean = false,
        val fieldKey: String = "",
        val fieldLabel: String = ""
    ) : AgentCardUi()

    /** observation 卡片：操作成功 / 失败 */
    @Serializable
    @SerialName("observation")
    data class Observation(
        override val id: String,
        override val timestamp: Long = System.currentTimeMillis(),
        val content: String,
        val isError: Boolean = false
    ) : AgentCardUi()

    /** 追问卡片：问题 + 选项按钮（点选回答）+ 跳过 */
    @Serializable
    @SerialName("question")
    data class Question(
        override val id: String,
        override val timestamp: Long = System.currentTimeMillis(),
        val question: String,
        val options: List<String>,
        val about: String = "",
        val answered: String? = null,
        val skipped: Boolean = false
    ) : AgentCardUi()

    /** 计划卡片：制卡计划（共 N 步）+ 确认/修改按钮 */
    @Serializable
    @SerialName("plan")
    data class Plan(
        override val id: String,
        override val timestamp: Long = System.currentTimeMillis(),
        val steps: List<PlanStepUi>,
        val status: String = STATUS_PENDING
    ) : AgentCardUi() {
        companion object {
            const val STATUS_PENDING = "PENDING"
            const val STATUS_CONFIRMED = "CONFIRMED"
        }
    }

    /** 进度卡片：已完成 N/M 步（只保留最新一条） */
    @Serializable
    @SerialName("progress")
    data class Progress(
        override val id: String,
        override val timestamp: Long = System.currentTimeMillis(),
        val done: Int,
        val total: Int,
        val title: String = "",
        val summary: String = ""
    ) : AgentCardUi()

    /** 预算决策卡片：迭代耗尽时让用户选择继续/保存/放弃 */
    @Serializable
    @SerialName("budget_decision")
    data class BudgetDecision(
        override val id: String,
        override val timestamp: Long = System.currentTimeMillis(),
        val completedSummary: String,
        val pendingSteps: List<String>,
        val iterationsUsed: Int
    ) : AgentCardUi()

    /** Diff 对比卡片：删除线旧版 + 绿色新版 + 接受/拒绝/继续改 */
    @Serializable
    @SerialName("diff")
    data class Diff(
        override val id: String,
        override val timestamp: Long = System.currentTimeMillis(),
        val fieldKey: String,
        val fieldLabel: String,
        val oldValue: String,
        val newValue: String,
        val decision: String? = null
    ) : AgentCardUi() {
        companion object {
            const val ACCEPTED = "accepted"
            const val REJECTED = "rejected"
            const val CONTINUED = "continued"
        }
    }

    /** 信息卡片：系统提示 / 流式降级 / 执行失败（kind 区分视觉与优先级） */
    @Serializable
    @SerialName("info")
    data class Info(
        override val id: String,
        override val timestamp: Long = System.currentTimeMillis(),
        val text: String,
        val kind: Kind = Kind.INFO
    ) : AgentCardUi() {
        enum class Kind { INFO, DEGRADED, FAIL }
    }

    /** 搜索结果卡片：web_search 成功后展示（点击打开结果预览浮窗） */
    @Serializable
    @SerialName("search")
    data class Search(
        override val id: String,
        override val timestamp: Long = System.currentTimeMillis(),
        val query: String,
        val resultCount: Int,
        val provider: String = "",
        val results: List<SearchResultItemUi> = emptyList(),
        val digest: String = ""
    ) : AgentCardUi()

    /** 网页抓取卡片：fetch_webpage 成功后展示（URL + 字符数 + 正文预览） */
    @Serializable
    @SerialName("webpage_fetch")
    data class WebpageFetch(
        override val id: String,
        override val timestamp: Long = System.currentTimeMillis(),
        val url: String,
        val contentLength: Int,
        val preview: String = ""
    ) : AgentCardUi()

    /**
     * 子代理卡片：spawn_subagent 生命周期（运行中/已完成/失败）。
     * Started 追加运行中卡片，Completed/Failed 原位更新状态（subagentId 关联）。
     */
    @Serializable
    @SerialName("subagent")
    data class SubAgent(
        override val id: String,
        override val timestamp: Long = System.currentTimeMillis(),
        /** 子代理执行标识（三段事件共享） */
        val subagentId: String,
        val task: String,
        val status: String = STATUS_RUNNING,
        val startedAt: Long = System.currentTimeMillis(),
        val finishedAt: Long? = null,
        /** 产出全文（UI 展开用，已按卡片预算截断；默认折叠） */
        val output: String = "",
        /** 一行预览（时间轴默认展示） */
        val outputPreview: String = "",
        val errorMessage: String = "",
        /** 归属主 Agent 标识（当前单主 Agent，预留多主 Agent 扩展） */
        val parentAgentId: String = "main"
    ) : AgentCardUi() {
        companion object {
            const val STATUS_RUNNING = "RUNNING"
            const val STATUS_DONE = "DONE"
            const val STATUS_FAILED = "FAILED"
        }
    }

    /** 审计卡片：卡级统计 + 可展开的问题列表（完整报告只在此展示，不进 ToolMessage） */
    @Serializable
    @SerialName("audit")
    data class Audit(
        override val id: String = java.util.UUID.randomUUID().toString(),
        override val timestamp: Long = System.currentTimeMillis(),
        val cardName: String,
        val entryCount: Int,
        val estimatedTokens: Int,
        val errorCount: Int,
        val warningCount: Int,
        val infoCount: Int,
        val issues: List<SerializableAuditIssue> = emptyList()
    ) : AgentCardUi()
}

@Serializable
data class SerializableAuditIssue(
    val severity: String,
    val entryUid: Long? = null,
    val fieldName: String? = null,
    val entryComment: String? = null,
    val pairUid: Long? = null,
    val issueCode: String,
    val description: String,
    val suggestion: String
)

/**
 * 任务取消时收敛运行中的子代理卡片（避免僵尸"运行中"状态）。
 * 纯函数：运行中 → 失败（errorMessage="任务已取消"），其它卡片原样保留。
 */
fun List<AgentCardUi>.failRunningSubAgents(errorMessage: String = "任务已取消"): List<AgentCardUi> =
    map { card ->
        if (card is AgentCardUi.SubAgent && card.status == AgentCardUi.SubAgent.STATUS_RUNNING) {
            card.copy(
                status = AgentCardUi.SubAgent.STATUS_FAILED,
                finishedAt = System.currentTimeMillis(),
                errorMessage = errorMessage
            )
        } else card
    }

/** 搜索结果条目（UI 卡片/浮窗展示用；与 AgentCardUi.Search 配套） */
@Serializable
data class SearchResultItemUi(
    val title: String,
    val url: String,
    val snippet: String = ""
)

@Serializable
data class PlanStepUi(
    val title: String,
    val detail: String = ""
)

/**
 * 当前挂起的交互（等待用户回复）。
 * 输入栏据此路由：回答自由文本 → respondToAgent；按钮直连专用方法。
 */
sealed interface PendingInteraction {
    val cardId: String

    data class Question(
        override val cardId: String,
        val question: String,
        val options: List<String>
    ) : PendingInteraction

    data class Plan(override val cardId: String) : PendingInteraction

    data class Budget(override val cardId: String) : PendingInteraction

    data class Diff(
        override val cardId: String,
        val fieldKey: String,
        val fieldLabel: String
    ) : PendingInteraction
}
