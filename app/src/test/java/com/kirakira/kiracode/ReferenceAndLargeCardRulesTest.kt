package com.kirakira.kiracode

import com.kirakira.kiracode.domain.model.WorkMode
import com.kirakira.kiracode.domain.prompt.BuiltinSystemPrompt
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P0：参考卡反照抄边界、大卡改卡工作流、子代理使用边界。
 */
class ReferenceAndLargeCardRulesTest {

    @Test
    fun `reference material rules only injected when mounted`() {
        val with = BuiltinSystemPrompt.build(WorkMode.AGENT, hasReferenceMaterial = true)
        val without = BuiltinSystemPrompt.build(WorkMode.AGENT, hasReferenceMaterial = false)
        assertTrue(with.contains("参考材料使用边界"))
        assertTrue(with.contains("禁止未经用户明确要求复制"))
        assertTrue(with.contains("独立角色"))
        assertFalse(without.contains("参考材料使用边界"))
    }

    @Test
    fun `large card edit workflow requires query before get before update`() {
        val prompt = BuiltinSystemPrompt.build(WorkMode.AGENT, isCardEditing = true, usesCardIndex = true)
        assertTrue(prompt.contains("优化大型角色卡"))
        assertTrue(prompt.contains("query_card_entries"))
        assertTrue(prompt.contains("get_card_entry"))
        assertTrue(prompt.contains("1-3 条"))
        assertTrue(prompt.contains("截断内容"))
        assertTrue(prompt.contains("整段替换"))
        // 索引模式不得宣称能读到完整 JSON
        assertFalse(prompt.contains("先基于系统消息中的卡状态（完整 JSON）"))
    }

    @Test
    fun `small card edit keeps json based workflow`() {
        val prompt = BuiltinSystemPrompt.build(WorkMode.AGENT, isCardEditing = true, usesCardIndex = false)
        assertTrue(prompt.contains("优化既有角色卡"))
        assertFalse(prompt.contains("优化大型角色卡"))
    }

    @Test
    fun `subagent rules always present in tool modes`() {
        for (mode in listOf(WorkMode.AGENT, WorkMode.YOLO)) {
            val prompt = BuiltinSystemPrompt.build(mode)
            assertTrue(prompt.contains("子代理（spawn_subagent）使用边界"))
            assertTrue(prompt.contains("不直接修改角色卡"))
            assertTrue(prompt.contains("必须亲自审核"))
        }
    }

    @Test
    fun `budget still respected with new layers`() {
        val agent = BuiltinSystemPrompt.build(WorkMode.AGENT, hasReferenceMaterial = true, usesCardIndex = true)
        assertTrue("AGENT 大卡全层提示词过大: ${agent.length} chars", agent.length < 5000)
        val yolo = BuiltinSystemPrompt.build(WorkMode.YOLO, cardMasterEnabled = true, hasReferenceMaterial = true)
        assertTrue("YOLO 全层提示词过大: ${yolo.length} chars", yolo.length < 5600)
    }
}
