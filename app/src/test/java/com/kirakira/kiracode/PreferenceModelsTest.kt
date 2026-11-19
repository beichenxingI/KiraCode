package com.kirakira.kiracode

import com.kirakira.kiracode.domain.model.DefaultPreferences
import com.kirakira.kiracode.domain.model.SessionPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PreferenceModelsTest {

    @Test
    fun `empty selection produces no injection`() {
        assertEquals("", SessionPreferences.Empty.buildInjection(DefaultPreferences.dimensions))
    }

    @Test
    fun `selected options inject their rules under header`() {
        val injection = SessionPreferences(
            selectedOptionIds = listOf("opt_companion", "opt_slowburn")
        ).buildInjection(DefaultPreferences.dimensions)

        assertTrue(injection.startsWith("【本次创作偏好】"))
        assertTrue(injection.contains("本卡定位陪伴"))
        assertTrue(injection.contains("角色与用户初始有距离感"))
        assertFalse(injection.contains("本卡有明确剧情线"))
    }

    @Test
    fun `unknown option ids are ignored`() {
        val injection = SessionPreferences(
            selectedOptionIds = listOf("missing", "opt_wholesome")
        ).buildInjection(DefaultPreferences.dimensions)

        assertTrue(injection.contains("角色关系基调温暖"))
        assertFalse(injection.contains("missing"))
    }

    @Test
    fun `catalog keeps built-in dimensions and options`() {
        assertEquals(6, DefaultPreferences.dimensions.size)
        assertTrue(DefaultPreferences.dimensions.all { it.isBuiltin })
        assertTrue(DefaultPreferences.dimensions.flatMap { it.options }.all { it.isBuiltin })
    }
}
