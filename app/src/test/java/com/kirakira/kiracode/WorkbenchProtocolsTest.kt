package com.kirakira.kiracode

import com.kirakira.kiracode.domain.ai.ReasoningProbeCache
import com.kirakira.kiracode.domain.ai.ReasoningSupport
import com.kirakira.kiracode.domain.ai.ReasoningSupport.Family
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * V1.0.0 返工测试：思考强度家族判定与探测缓存。
 * P0 清理：ThinkStreamSplitter 测试已随该类删除（内联 think 标签拆分随旧 ChatEngine.send 路径移除）。
 */
class WorkbenchProtocolsTest {

    // ============ 思考强度家族判定（不依赖过时精确关键词） ============

    @Test
    fun `openai reasoning family detection covers new generations`() {
        listOf("o4-mini", "gpt-5.6-terra", "gpt-6-preview", "gpt-oss-120b", "codex-mini")
            .forEach { m -> assertEquals(m, Family.OPENAI, ReasoningSupport.heuristicFamily(m)) }
    }

    @Test
    fun `deepseek family detection covers v4 and beyond`() {
        listOf("deepseek-v4-pro", "deepseek-v5-chat", "deepseek-r2", "DeepSeek-Reasoner")
            .forEach { m -> assertEquals(m, Family.DEEPSEEK, ReasoningSupport.heuristicFamily(m)) }
    }

    @Test
    fun `hinted family for thinking-named models`() {
        listOf("qwq-32b", "minimax-m3", "some-thinking-model", "vendor-reasoner-x")
            .forEach { m -> assertEquals(m, Family.HINTED, ReasoningSupport.heuristicFamily(m)) }
    }

    @Test
    fun `non reasoning models yield null`() {
        listOf("gpt-4o", "gpt-3.5-turbo", "glm-4-flash", "claude-3-haiku", "").forEach { m ->
            assertNull(ReasoningSupport.heuristicFamily(m))
        }
    }

    @Test
    fun `unsupported error markers detected`() {
        assertTrue(ReasoningSupport.errorIndicatesUnsupported(
            """{"error":{"message":"Unknown parameter: 'reasoning_effort'"}}"""
        ))
        assertFalse(ReasoningSupport.errorIndicatesUnsupported("401 Unauthorized"))
        assertFalse(ReasoningSupport.errorIndicatesUnsupported("insufficient_quota"))
    }

    // ============ 探测缓存编解码 ============

    @Test
    fun `probe cache roundtrip and tolerance`() {
        val map = mapOf("deepseek-v4-pro" to true, "gpt-4o" to false)
        val json = ReasoningProbeCache.encode(map)
        assertEquals(map, ReasoningProbeCache.decode(json))
        assertTrue(ReasoningProbeCache.decode(null).isEmpty())
        assertTrue(ReasoningProbeCache.decode("{broken").isEmpty())
    }
}
