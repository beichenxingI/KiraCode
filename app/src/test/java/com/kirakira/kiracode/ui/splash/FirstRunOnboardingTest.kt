package com.kirakira.kiracode.ui.splash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FirstRunOnboardingTest {

    @Test
    fun `single onboarding is required until both flags are accepted`() {
        assertTrue(requiresFirstRunOnboarding(isFirstLaunch = true, disclaimerAccepted = false))
        assertTrue(requiresFirstRunOnboarding(isFirstLaunch = true, disclaimerAccepted = true))
        assertTrue(requiresFirstRunOnboarding(isFirstLaunch = false, disclaimerAccepted = false))
        assertFalse(requiresFirstRunOnboarding(isFirstLaunch = false, disclaimerAccepted = true))
    }

    @Test
    fun `onboarding copy contains the four promised benefits`() {
        assertEquals(4, ONBOARDING_POINTS.size)
        assertEquals(
            listOf("许愿式写卡", "你主导，它执笔", "SillyTavern V3 全字段", "你的专属文风"),
            ONBOARDING_POINTS.map { it.title }
        )
        assertTrue(ONBOARDING_POINTS[0].body.contains("剩下的交给 AI 去规划与落笔"))
        assertTrue(ONBOARDING_POINTS[1].body.contains("每一步都可以随时叫停、改写"))
        assertTrue(ONBOARDING_POINTS[2].body.contains("导出即用，无损往返"))
        assertTrue(ONBOARDING_POINTS[3].body.contains("越写越像你"))
    }

    @Test
    fun `single primary action and collapsed disclaimer copy are present`() {
        assertEquals("同意免责声明并开始创作", ONBOARDING_CTA)
        assertEquals("点击查看《开源工具免责声明》", DISCLAIMER_TOGGLE)
        assertTrue(DISCLAIMER_TEXT.contains("按“原样”提供"))
        assertTrue(DISCLAIMER_TEXT.contains("第三方 AI API 所产生的费用"))
        assertTrue(DISCLAIMER_TEXT.contains("导入或导出的角色卡数据"))
    }
}
