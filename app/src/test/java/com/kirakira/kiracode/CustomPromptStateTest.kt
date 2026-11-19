package com.kirakira.kiracode

import com.kirakira.kiracode.domain.model.CustomPromptState
import com.kirakira.kiracode.domain.usecase.ChatEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P0：自定义提示词 opt-in 测试。
 * 覆盖：新用户不注入 / 启用后注入 / 清空后不注入 / 关闭后不注入但保留文本 /
 * 重新开启恢复 / 状态与制卡师人格层互不重复。
 */
class CustomPromptStateTest {

    @Test
    fun `new user is not configured`() {
        val st = CustomPromptState.from(enabled = null, newText = null, legacyText = null)
        assertEquals(CustomPromptState.NotConfigured, st)
        assertNull(ChatEngine.customPromptSection(st))
    }

    @Test
    fun `legacy saved key migrates to enabled`() {
        // 旧版本语义：只要保存过 customPrompt 就一直注入 → 迁移为 Enabled
        val st = CustomPromptState.from(enabled = null, newText = null, legacyText = "中文、文学化行文")
        assertTrue(st is CustomPromptState.Enabled)
        assertEquals("中文、文学化行文", (st as CustomPromptState.Enabled).text)
        assertTrue(ChatEngine.customPromptSection(st)!!.contains("【我的创作规则】"))
    }

    @Test
    fun `legacy cleared key migrates to not configured`() {
        val st = CustomPromptState.from(enabled = null, newText = null, legacyText = "")
        assertEquals(CustomPromptState.NotConfigured, st)
        assertNull(ChatEngine.customPromptSection(st))
    }

    @Test
    fun `user enabled template is injected`() {
        val st = CustomPromptState.from(enabled = true, newText = "规则A", legacyText = null)
        assertTrue(st is CustomPromptState.Enabled)
        assertTrue(ChatEngine.customPromptSection(st)!!.contains("规则A"))
    }

    @Test
    fun `cleared content is not injected`() {
        // 清空文本（enabled 可能仍为 true）→ 不注入
        val st = CustomPromptState.from(enabled = true, newText = "", legacyText = null)
        assertEquals(CustomPromptState.NotConfigured, st)
        assertNull(ChatEngine.customPromptSection(st))
        val st2 = CustomPromptState.from(enabled = true, newText = "   ", legacyText = null)
        assertEquals(CustomPromptState.NotConfigured, st2)
    }

    @Test
    fun `disabled keeps text but does not inject`() {
        val st = CustomPromptState.from(enabled = false, newText = "规则B", legacyText = null)
        assertTrue(st is CustomPromptState.Disabled)
        assertEquals("规则B", (st as CustomPromptState.Disabled).text)
        assertNull(ChatEngine.customPromptSection(st))
    }

    @Test
    fun `re-enable restores saved text`() {
        val disabled = CustomPromptState.from(enabled = false, newText = "规则C", legacyText = null)
        val reEnabled = CustomPromptState.from(enabled = true, newText = (disabled as CustomPromptState.Disabled).text, legacyText = null)
        assertTrue(reEnabled is CustomPromptState.Enabled)
        assertEquals("规则C", (reEnabled as CustomPromptState.Enabled).text)
    }

    @Test
    fun `custom section is independent from persona layer`() {
        // 自定义规则段与灵魂段使用不同标题与不同来源，
        // 同时启用时不会出现同段重复注入（灵魂层由 personaPrompt 注入，规则层由本函数注入）
        val section = ChatEngine.customPromptSection(
            CustomPromptState.from(enabled = true, newText = "单条≤300字", legacyText = null)
        )!!
        assertTrue(section.contains("【我的创作规则】"))
        assertFalse(section.contains("灵魂"))
    }

    @Test
    fun `new text takes precedence over legacy`() {
        val st = CustomPromptState.from(enabled = true, newText = "新规则", legacyText = "旧规则")
        assertEquals("新规则", (st as CustomPromptState.Enabled).text)
    }
}
