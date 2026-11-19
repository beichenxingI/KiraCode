package com.kirakira.kiracode.domain.model

import kotlinx.serialization.Serializable

/**
 * 对话工作区的工作模式（V3.1 任务7：收敛为 3 档——仅会话/代理人/全局）。
 * 旧档位 AUTO/FULL_AUTO/MAX 由 parseMode 迁移到 YOLO。
 */
enum class WorkMode(val displayName: String, val description: String, val warningText: String? = null) {
    CHAT("仅会话", "AI 仅提供建议，不修改任何角色卡数据"),
    AGENT("代理人", "AI 每步工具调用都会弹确认卡，你接受后才执行"),
    YOLO("全局（YOLO）", "AI 自动完成任务，无需用户同意，Diff 仅展示自动应用", warningText = "该模式下 AI 可以进行任何操作，无需用户同意");

    /** 是否允许结构化操作块 */
    val allowsOps: Boolean get() = this != CHAT
    /** 操作是否需要用户确认 */
    val requiresConfirmation: Boolean get() = this == AGENT
    /** 是否启用流式超时自动继续 */
    val useStallWatchdog: Boolean get() = this == YOLO

    companion object {
        /** V3.1：旧会话持久化的 AUTO/FULL_AUTO/MAX 名称迁移 */
        fun fromLegacyName(name: String?): WorkMode? = when (name) {
            "AUTO", "FULL_AUTO", "MAX" -> YOLO
            else -> entries.firstOrNull { it.name == name }
        }
    }
}

/**
 * 新建会话时选择的模式
 * V3.0：创作模式（从零创建）与改卡模式（基于已有卡优化迭代）
 */
enum class SessionCreationMode(val displayName: String, val description: String) {
    CREATE("创作模式", "从零开始创作一张全新角色卡，可选参考卡辅助"),
    EDIT_CARD("改卡模式", "选择一张已有角色卡，在其基础上优化迭代")
}

/**
 * 参考卡选择（V3.0 细粒度参考）
 *
 * @param uuid 参考卡 UUID
 * @param granularity WHOLE=整卡 / STYLE=风格(语气行文) / TECH=技术(世界书与正则写法)
 * @param entries 追加选择的单条目标签：世界书条目 comment/uid 或正则 script_name
 */
@Serializable
data class RefSelection(
    val uuid: String,
    val granularity: String = GRAN_WHOLE,
    val entries: List<String> = emptyList()
) {
    companion object {
        const val GRAN_WHOLE = "WHOLE"
        const val GRAN_STYLE = "STYLE"
        const val GRAN_TECH = "TECH"

        fun whole(uuid: String) = RefSelection(uuid, GRAN_WHOLE)

        /** 解码会话持久化的 JSON：兼容旧格式 ["uuid"] 与新格式 [{"uuid":...}] */
        fun decodeList(json: String?): List<RefSelection> {
            if (json.isNullOrBlank()) return emptyList()
            return runCatching {
                val element = kotlinx.serialization.json.Json.parseToJsonElement(json)
                when (element) {
                    is kotlinx.serialization.json.JsonArray -> element.mapNotNull { item ->
                        when (item) {
                            is kotlinx.serialization.json.JsonPrimitive ->
                                if (item.content.isNotBlank()) RefSelection(item.content) else null
                            is kotlinx.serialization.json.JsonObject ->
                                runCatching {
                                    kotlinx.serialization.json.Json.decodeFromJsonElement(serializer(), item)
                                }.getOrNull()
                            else -> null
                        }
                    }
                    else -> emptyList()
                }
            }.getOrDefault(emptyList())
        }

        fun encodeList(list: List<RefSelection>): String = runCatching {
            kotlinx.serialization.json.Json.encodeToString(
                kotlinx.serialization.builtins.ListSerializer(serializer()), list
            )
        }.getOrDefault("[]")
    }

    /** 人类可读描述（注入提示词用） */
    fun describe(): String = when {
        entries.isNotEmpty() -> "指定条目(${entries.joinToString("/")})"
        granularity == GRAN_STYLE -> "仅风格参考"
        granularity == GRAN_TECH -> "仅技术参考"
        else -> "整卡参考"
    }
}

/**
 * 上下文占用信息（用于数据控制面板）
 */
@Serializable
data class ContextInfo(
    val totalTokens: Int = 0,
    val maxTokens: Int = 128000,
    val cacheHitRate: Float = 0f,
    val processedTokens: Int = 0,
    val messageCount: Int = 0,
    // V3.0 数据面板细分统计
    val promptTokensTotal: Long = 0L,
    val completionTokensTotal: Long = 0L,
    val requestCount: Int = 0
) {
    val usagePercentage: Float
        get() = if (maxTokens > 0) totalTokens.toFloat() / maxTokens else 0f

    val isHighUsage: Boolean
        get() = usagePercentage > 0.8f

    val usagePercentInt: Int
        get() = (usagePercentage * 100).toInt().coerceIn(0, 100)
}

/**
 * 模型配置信息（用于多 Base/API 管理）
 */
@Serializable
data class ModelConfig(
    val id: String = "",
    val name: String = "",
    val baseUrl: String = "",
    val apiKey: String = "",
    val models: List<String> = emptyList(),
    val selectedModel: String? = null,
    val isActive: Boolean = false
)
