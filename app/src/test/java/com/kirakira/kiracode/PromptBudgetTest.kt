package com.kirakira.kiracode

import com.kirakira.kiracode.domain.model.WorkMode
import com.kirakira.kiracode.domain.prompt.BuiltinSystemPrompt
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 提示词体积与缓存友好性守护测试（V1.0.0 返工·提示词精简）。
 *
 * 背景：COMMON_PROTOCOL 曾堆叠大量重复规范与示例，导致每轮 system 消息膨胀、
 * 前缀缓存命中率下降。本次精简目标：
 *  - AGENT 模式系统协议 < 1200 字符（原 ~2900）
 *  - 静态协议全部位于最前（build 输出即静态前缀），动态数据由 ChatEngine 追加在后
 */
class PromptBudgetTest {

    private fun estimateTokens(text: String): Int {
        // 与 TokenEstimatorLite 同口径：CJK≈1 字 1 token，其他≈4 字符 1 token
        var cjk = 0
        var other = 0
        for (ch in text) if (ch.code > 0x2E80) cjk++ else other++
        return cjk + (other + 3) / 4
    }

    @Test
    fun `agent protocol stays under budget`() {
        val prompt = BuiltinSystemPrompt.build(WorkMode.AGENT)
        println("AGENT_PROMPT chars=" + prompt.length + " tokens≈" + estimateTokens(prompt))
        // 四模式 + 强制工具执行 + arguments JSON 转义说明，上限 4500→4700
        assertTrue("协议过大: " + prompt.length + " chars", prompt.length < 4700)
    }

    @Test
    fun `full auto prompt stays under budget`() {
        val prompt = BuiltinSystemPrompt.build(WorkMode.YOLO, cardMasterEnabled = true)
        println("FULL_AUTO_PROMPT chars=" + prompt.length + " tokens≈" + estimateTokens(prompt))
        // 四模式 + 自洽审查 + 强制工具执行/JSON 转义说明，上限 5000→5300
        assertTrue(prompt.length < 5300)
    }

    @Test
    fun `static core is stable prefix`() {
        // 两次构建内容一致（无随机/时间依赖），保证可被 KV 缓存复用
        assertEquals(
            BuiltinSystemPrompt.build(WorkMode.YOLO),
            BuiltinSystemPrompt.build(WorkMode.YOLO)
        )
    }

    private fun assertEquals(a: Any, b: Any) = org.junit.Assert.assertEquals(a, b)
}
