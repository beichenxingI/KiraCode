package com.kirakira.kiracode.domain.model

import kotlinx.serialization.Serializable

/**
 * AI 生成请求参数模型
 */
@Serializable
data class GenerationRequest(
    val referenceCard: CharacterCard,   // 参考卡
    val newCharacterDescription: String, // 用户输入的新角色描述
    val styleTemplate: String? = null,  // 可选风格模板 ID
    val model: String = "mock",         // 模型标识
    val temperature: Float = 0.8f,
    val maxTokens: Int = 4096,
    val regexGenerationMode: RegexGenerationMode = RegexGenerationMode.DEFAULT
) {
    /**
     * 验证请求参数是否有效
     */
    fun isValid(): Boolean {
        return referenceCard.isValid() && newCharacterDescription.isNotBlank()
    }
}

/**
 * 风格模板数据类
 */
@Serializable
data class StyleTemplate(
    val id: String,
    val name: String,
    val description: String
)

/**
 * AI 生成结果模型 (密封接口)
 */
@Serializable
sealed interface GenerationResult {
    @Serializable
    data class Success(val card: CharacterCard) : GenerationResult

    @Serializable
    data class Failure(val error: String, val rawResponse: String? = null) : GenerationResult
}

/**
 * 导出格式枚举
 */
enum class ExportFormat {
    JSON,  // 标准 SillyTavern JSON 格式
    PNG    // 角色卡图片 + 嵌入 JSON 元数据
}

/**
 * 模型信息模型
 */
@Serializable
data class ModelInfo(
    val id: String,
    val name: String,
    val provider: String,
    val maxTokens: Int,
    val capabilities: List<String> = listOf("chat", "character_generation")
)

/**
 * 预设风格模板列表
 */
val STYLE_TEMPLATES = listOf(
    StyleTemplate("default", "默认平衡", "平衡细节与留白，通用型"),
    StyleTemplate("tsundere", "傲娇", "强化\"口嫌体正直\"、反差萌、语气强硬实则在意"),
    StyleTemplate("gentle", "温柔治愈", "语气柔和、共情能力强、动作细腻、环境描写丰富"),
    StyleTemplate("cold", "高冷禁欲", "语气简短、情感内敛、理性至上、隐藏深情"),
    StyleTemplate("chaotic", "疯批/混乱", "语气跳跃、行为不可预测、打破第四面墙、元叙事"),
    StyleTemplate("lore_heavy", "设定党", "世界观极深、专有名词多、时间线完整、组织关系复杂")
)
