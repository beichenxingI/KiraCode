package com.kirakira.kiracode.domain.model

import kotlinx.serialization.Serializable

/**
 * 灵魂训练配置。
 *
 * [modelId] 对应 [ModelConfig.id]（模型配置组），训练时由
 * ChatEngine.resolveConfigById 解析为实际 API 配置。
 */
@Serializable
data class TrainPersonaConfig(
    val modelId: String = "",
    val temperature: Float = 0.2f,
    val topP: Float = 1.0f,
    val maxTokens: Int = 8192,              // 输出 token 上限
    val contextBudget: Int = 1_000_000,     // 输入上下文预算（与 maxTokens 分离）
    val selectedDimensions: Set<TrainDimension> = TrainDimension.entries.toSet(),
    val customPrompt: String = ""
)

/**
 * 训练维度（6 个维度平权，按 [order] 顺序推进）。
 */
enum class TrainDimension(val label: String, val order: Int) {
    TECH_STACK("技术栈", 0),
    WRITING_STYLE("文风", 1),
    HABITS("写作习惯", 2),
    FRONTEND("前端工艺", 3),
    REGEX_COLLAB("正则协作", 4),
    HIGHLIGHTS("亮点技巧", 5)
}

/**
 * 训练阶段状态。
 */
sealed class TrainPhase {
    /**
     * 分析某个维度（1~5 轮）。
     * @param round 1=生成清单 2~4=AI 决策浏览 5=生成报告
     */
    data class AnalyzingDimension(
        val dimension: TrainDimension,
        val round: Int,
        val action: String
    ) : TrainPhase()

    data object Synthesizing : TrainPhase()
    data object Complete : TrainPhase()
}

/**
 * AI 浏览决策。
 *
 * 为兼容 kotlinx.serialization（不支持 IntRange），区间使用
 * [ContentSelection.rangeStart] / [ContentSelection.rangeEnd] 两个可空 Int；
 * 二者均省略表示读取全部。
 */
@Serializable
data class BrowsingDecision(
    val selections: List<ContentSelection> = emptyList(),
    val reasoning: String = ""
)

@Serializable
data class ContentSelection(
    val cardIndex: Int,
    val contentType: ContentType,
    val rangeStart: Int? = null,
    val rangeEnd: Int? = null
)

enum class ContentType {
    DESCRIPTION,
    PERSONALITY,
    FIRST_MESSAGE,
    EXAMPLE_DIALOGUE,
    SCENARIO,
    WORLD_BOOK,
    REGEX_SCRIPTS,
    TAGS
}

/**
 * 上下文爆炸异常（保留已完成维度的报告）。
 */
class ContextOverflowException(
    val dimension: TrainDimension,
    val partialResults: Map<TrainDimension, String>,
    message: String = "内容超过模型上下文限制（维度：${dimension.label}，请换用支持更大上下文的模型）"
) : Exception(message)

/**
 * 训练失败异常（保留已完成维度的报告）。
 */
class TrainingFailedException(
    val phase: TrainPhase?,
    val partialResults: Map<TrainDimension, String>,
    cause: Throwable
) : Exception("训练失败：${cause.message}", cause)