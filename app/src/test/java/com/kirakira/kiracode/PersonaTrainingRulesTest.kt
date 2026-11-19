package com.kirakira.kiracode

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P0：灵魂训练链路约束——分析与合成只沉淀抽象规律，禁止固化原卡内容。
 */
class PersonaTrainingRulesTest {

    @Test
    fun `dimension prompts cover all six dimensions`() {
        val prompts = com.kirakira.kiracode.domain.usecase.TrainPersonaUseCase.DIMENSION_PROMPTS
        com.kirakira.kiracode.domain.model.TrainDimension.entries.forEach { dimension ->
            assertTrue(
                "missing prompt for ${dimension.label}",
                prompts[dimension]?.isNotBlank() == true
            )
        }
        assertEquals(6, prompts.size)
    }

    @Test
    fun `both prompts forbid copying original card content`() {
        val rules = com.kirakira.kiracode.domain.usecase.TrainPersonaUseCase.ANALYSIS_RULES
        val synthesizer = com.kirakira.kiracode.domain.usecase.TrainPersonaUseCase.SYNTHESIZER_SYSTEM
        assertTrue(rules.contains("禁止出现原角色名"))
        assertTrue(rules.contains("不要复述原文"))
        assertTrue(synthesizer.contains("禁止写入原角色名"))
        assertTrue(synthesizer.contains("抽象规律"))
    }

    @Test
    fun `edit workflow declares original style precedence over soul`() {
        val edit = com.kirakira.kiracode.domain.prompt.BuiltinSystemPrompt
            .build(com.kirakira.kiracode.domain.model.WorkMode.AGENT, isCardEditing = true)
        assertTrue(edit.contains("原卡风格优先"))
        assertTrue(edit.contains("灵魂仅作参照"))
    }
}