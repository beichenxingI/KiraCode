package com.kirakira.kiracode.ui.workspace

import com.kirakira.kiracode.domain.agent.AgentTask
import com.kirakira.kiracode.domain.agent.PlanBook
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * 会话事件快照：定稿事件卡 + 计划书 + 任务板（阶段 B）。
 *
 * 只持久化定稿事件（不持久化短期 delta 与流式占位），
 * 用于打开已有工作记录时恢复事件流、监控与任务板。
 */
@Serializable
data class WorkspaceEventSnapshot(
    val cards: List<AgentCardUi> = emptyList(),
    val planBook: PlanBook = PlanBook(),
    val tasks: List<AgentTask> = emptyList(),
    // V3.1 批次6任务：Token 统计持久化（会话切换/重启后恢复；全部默认值向后兼容旧 JSON）
    val promptTokensTotal: Long = 0L,
    val completionTokensTotal: Long = 0L,
    val requestCount: Int = 0,
    val cacheHitRate: Float = 0f,
    val processedTokens: Int = 0
) {
    companion object {
        /** 快照事件卡上限（控制体积：只保留最近 100 条定稿事件） */
        const val CARD_LIMIT = 100

        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        fun encode(snapshot: WorkspaceEventSnapshot): String = runCatching {
            json.encodeToString(WorkspaceEventSnapshot.serializer(), snapshot)
        }.getOrDefault("[]")

        fun decode(jsonText: String?): WorkspaceEventSnapshot {
            if (jsonText.isNullOrBlank() || jsonText.trim() == "[]") return WorkspaceEventSnapshot()
            return runCatching {
                json.decodeFromString(WorkspaceEventSnapshot.serializer(), jsonText)
            }.getOrDefault(WorkspaceEventSnapshot())
        }

        /** 由当前工作区状态构造快照（事件卡只保留最近 CARD_LIMIT 条） */
        fun of(cards: List<AgentCardUi>, planBook: PlanBook?, tasks: List<AgentTask>): WorkspaceEventSnapshot =
            WorkspaceEventSnapshot(
                cards = cards.takeLast(CARD_LIMIT),
                planBook = planBook ?: PlanBook(),
                tasks = tasks
            )
    }
}
