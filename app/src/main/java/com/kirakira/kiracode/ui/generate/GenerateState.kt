package com.kirakira.kiracode.ui.generate

import com.kirakira.kiracode.domain.model.CharacterCard
import com.kirakira.kiracode.domain.model.StyleTemplate

/**
 * 生成页面 UI 状态：单一 sealed interface 表达当前阶段
 */
sealed interface GenerateState {
    data class Idle(
        val referenceCard: CharacterCard? = null,
        val description: String = "",
        val styleTemplate: StyleTemplate? = null,
        val temperature: Float = 0.8f,
        val maxTokens: Int = 4096
    ) : GenerateState

    data class Loading(
        val referenceCard: CharacterCard,
        val description: String,
        val styleTemplate: StyleTemplate?,
        val progressMessage: String = "正在生成角色卡..."
    ) : GenerateState

    data class Success(
        val generatedCard: CharacterCard,
        val referenceCard: CharacterCard,
        val description: String
    ) : GenerateState

    data class Error(
        val message: String,
        val referenceCard: CharacterCard? = null,
        val description: String = "",
        val styleTemplate: StyleTemplate? = null
    ) : GenerateState
}
