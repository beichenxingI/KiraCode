package com.kirakira.kiracode

import com.kirakira.kiracode.domain.agent.tools.CardFunctions
import com.kirakira.kiracode.domain.model.WorkMode
import com.kirakira.kiracode.domain.prompt.BuiltinSystemPrompt
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P0 冒烟回归（结构性检查）：
 * 对应 T1 从零创作 / T3 参考卡风格 / T4 YOLO / T5 导入改卡 / T8 大卡查询 / T12 联网。
 * 这些检查验证应用侧前置条件（提示词与工具注册）；模型行为本身仍需人工 A/B（见任务交接）。
 */
class P0SmokeTest {

    private val names = CardFunctions.getAllFunctions(webSearchEnabled = false, includeInteractiveTools = false)
        .map { it["function"]!!.jsonObject["name"]!!.jsonPrimitive.content }

    // ── T1：从零创作 ──
    @Test
    fun `T1 create from zero uses function calling only`() {
        val prompt = BuiltinSystemPrompt.build(WorkMode.AGENT)
        // 不输出旧文本协议
        assertFalse(prompt.contains("[field:"))
        assertFalse(prompt.contains("[book.add]"))
        assertFalse(prompt.contains("card-update"))
        // 正确调用 function tools + name 优先 + 适度追问 + 完成后停止
        assertTrue(prompt.contains("update_card_field"))
        assertTrue(prompt.contains("name 必须最先设置"))
        assertTrue(prompt.contains("ask_user"))
        assertTrue(prompt.contains("停止调用工具"))
        assertTrue(names.contains("update_card_field") && names.contains("update_card_book_add"))
    }

    // ── T3：参考卡风格 ──
    @Test
    fun `T3 reference cards teach style extraction not copying`() {
        val prompt = BuiltinSystemPrompt.build(WorkMode.AGENT, hasReferenceMaterial = true)
        assertTrue(prompt.contains("参考材料使用边界"))
        assertTrue(prompt.contains("禁止未经用户明确要求复制"))
        assertTrue(prompt.contains("原角色名"))
        assertTrue(prompt.contains("原剧情"))
        assertTrue(prompt.contains("独立角色"))
        assertTrue(prompt.contains("参考卡 N"))
    }

    // ── T4：YOLO 全自动 ──
    @Test
    fun `T4 yolo is non-interactive with ai_guess marking`() {
        val prompt = BuiltinSystemPrompt.build(WorkMode.YOLO)
        assertTrue(prompt.contains("不询问用户"))
        assertTrue(prompt.contains("无需提交计划书"))
        assertTrue(prompt.contains("ai_guess"))
        // 工具注册层不提供交互工具
        assertFalse(names.contains("ask_user"))
        assertFalse(names.contains("submit_plan"))
        assertFalse(names.contains("complete_step"))
        // 修改类工具照常
        assertTrue(names.contains("update_card_field"))
    }

    // ── T5：导入改卡 ──
    @Test
    fun `T5 edit card enforces minimal change and diff`() {
        val prompt = BuiltinSystemPrompt.build(WorkMode.AGENT, isCardEditing = true)
        assertTrue(prompt.contains("优化既有角色卡"))
        assertTrue(prompt.contains("最小改动"))
        assertTrue(prompt.contains("保持原值"))
        assertTrue(prompt.contains("update_card_book_update"))
        // 修改经 Diff 审查（AGENT 模式语义）
        val agent = BuiltinSystemPrompt.build(WorkMode.AGENT, isCardEditing = true)
        assertTrue(agent.contains("Diff") || agent.contains("确认"))
    }

    // ── T8：大型卡查询 ──
    @Test
    fun `T8 large card uses query then get with budget`() {
        val prompt = BuiltinSystemPrompt.build(WorkMode.AGENT, isCardEditing = true, usesCardIndex = true)
        assertTrue(prompt.contains("优化大型角色卡"))
        assertTrue(prompt.contains("不要假设能读到整卡"))
        assertTrue(prompt.contains("query_card_entries"))
        assertTrue(prompt.contains("get_card_entry"))
        assertTrue(prompt.contains("1-3 条"))
        assertTrue(prompt.contains("截断内容"))
        assertTrue(prompt.contains("条目 ID"))
    }

    // ── T12：联网调研 ──
    @Test
    fun `T12 web research rules only when enabled and honest on failure`() {
        val on = BuiltinSystemPrompt.build(WorkMode.AGENT, webSearchEnabled = true)
        val off = BuiltinSystemPrompt.build(WorkMode.AGENT, webSearchEnabled = false)
        assertTrue(on.contains("web_search"))
        assertTrue(on.contains("fetch_webpage"))
        assertTrue(on.contains("不编造搜索结果或页面内容"))
        assertTrue(on.contains("抓取失败时如实告知"))
        assertFalse(off.contains("web_search"))
        // 工具注册随开关
        val withWeb = CardFunctions.getAllFunctions(webSearchEnabled = true, includeInteractiveTools = false)
            .map { it["function"]!!.jsonObject["name"]!!.jsonPrimitive.content }
        assertTrue(withWeb.contains("web_search") && withWeb.contains("fetch_webpage"))
    }
}
