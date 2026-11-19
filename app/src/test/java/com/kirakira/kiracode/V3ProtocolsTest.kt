package com.kirakira.kiracode

import com.kirakira.kiracode.domain.agent.PlanBook
import com.kirakira.kiracode.domain.agent.PlanOp
import com.kirakira.kiracode.domain.agent.PlanOpsParser
import com.kirakira.kiracode.domain.agent.PlanPart
import com.kirakira.kiracode.domain.model.RefSelection
import com.kirakira.kiracode.domain.model.WorkMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * V3.0 协议测试：计划书(plan.*)、联网搜索(web.search)、参考选择编码、五模式行为。
 */
class V3ProtocolsTest {

    // ============ 计划书 ============

    @Test
    fun `parse plan goal and add`() {
        val reply = """
            收到！先制定计划书。

            ```card-update
            [plan.goal]打造一张完整的赛博朋克都市传说角色卡[/plan.goal]
            [plan.add]
            title: 第一部分：世界观框架
            detail: 城市分区/势力/时间线
            [/plan.add]
            [plan.add]
            title: 第二部分：核心人设
            [/plan.add]
            ```
        """.trimIndent()
        val ops = PlanOpsParser.parse(reply)
        assertEquals(3, ops.size)
        assertTrue(ops[0] is PlanOp.SetGoal)
        assertEquals("打造一张完整的赛博朋克都市传说角色卡", (ops[0] as PlanOp.SetGoal).goal)

        var book = PlanBook()
        ops.forEach { book = it.applyTo(book) }
        assertEquals(2, book.parts.size)
        assertEquals("P1", book.parts[0].id)
        assertEquals("第一部分：世界观框架", book.parts[0].title)
        assertEquals("城市分区/势力/时间线", book.parts[0].detail)
    }

    @Test
    fun `parse plan status self closing and apply`() {
        val reply = "```card-update\n[plan.status id=P1 status=RUNNING]\n```"
        val ops = PlanOpsParser.parse(reply)
        assertEquals(1, ops.size)

        var book = PlanBook()
        book = PlanOp.Add(title = "第一阶段").applyTo(book)
        book = PlanOp.Add(title = "第二阶段").applyTo(book)
        book = ops[0].applyTo(book)
        assertEquals(PlanPart.STATUS_RUNNING, book.parts[0].status)
        assertEquals(PlanPart.STATUS_PENDING, book.parts[1].status)

        book = PlanOpsParser.parse("[plan.status id=P2 status=DONE]").firstOrNull()!!.applyTo(book)
        assertTrue(book.isComplete.not())
        book = PlanOpsParser.parse("[plan.status id=P1 status=DONE]").firstOrNull()!!.applyTo(book)
        assertTrue(book.isComplete)
        assertEquals("2/2", book.progressText)
    }

    @Test
    fun `invalid plan status rejected`() {
        val ops = PlanOpsParser.parse("[plan.status id=P1 status=WAT]")
        // SetStatus 仍会生成，但 applyTo 时无效状态被拒绝，板不变
        var book = PlanBook()
        book = PlanOp.Add("x").applyTo(book)
        if (ops.isNotEmpty()) book = ops[0].applyTo(book)
        assertEquals(PlanPart.STATUS_PENDING, book.parts[0].status)
    }

    @Test
    fun `plan title by fuzzy match`() {
        var book = PlanBook()
        book = PlanOp.Add(title = "第二部分：世界观框架").applyTo(book)
        book = PlanOpsParser.parse("[plan.status id=第二部分 status=DONE]").first().applyTo(book)
        assertEquals(PlanPart.STATUS_DONE, book.parts[0].status)
    }

    // ============ 参考选择编码（新旧格式兼容） ============
    // P0 清理：[web.search] 文本协议解析测试已随 WebSearchParser 删除（联网改走 web_search 工具）

    @Test
    fun `ref selection codec roundtrip`() {
        val list = listOf(
            RefSelection.whole("uuid-a"),
            RefSelection(uuid = "uuid-b", granularity = RefSelection.GRAN_STYLE),
            RefSelection(uuid = "uuid-c", entries = listOf("银针", "regex:去思考"))
        )
        val json = RefSelection.encodeList(list)
        val decoded = RefSelection.decodeList(json)
        assertEquals(list, decoded)
    }

    @Test
    fun `legacy plain uuid list decodes as whole refs`() {
        val legacyJson = """["uuid-1","uuid-2"]"""
        val decoded = RefSelection.decodeList(legacyJson)
        assertEquals(2, decoded.size)
        assertEquals("uuid-1", decoded[0].uuid)
        assertEquals(RefSelection.GRAN_WHOLE, decoded[0].granularity)
    }

    @Test
    fun `describe reflects granularity`() {
        assertEquals("整卡参考", RefSelection.whole("u").describe())
        assertEquals("仅风格参考", RefSelection("u", RefSelection.GRAN_STYLE).describe())
        assertEquals("仅技术参考", RefSelection("u", RefSelection.GRAN_TECH).describe())
        assertEquals("指定条目(银针)", RefSelection("u", entries = listOf("银针")).describe())
    }

    // ============ 模式行为（正式版：四模式 + 制卡师开关） ============

    @Test
    fun `work mode behaviors`() {
        assertFalse(WorkMode.CHAT.allowsOps)
        WorkMode.entries.filter { it != WorkMode.CHAT }.forEach { assertTrue(it.allowsOps) }

        assertTrue(WorkMode.AGENT.requiresConfirmation)
        assertFalse(WorkMode.YOLO.requiresConfirmation)
        assertFalse(WorkMode.YOLO.requiresConfirmation)

        assertTrue(WorkMode.YOLO.useStallWatchdog)
        assertFalse(WorkMode.AGENT.useStallWatchdog)

        assertNotNullWarning(WorkMode.YOLO)
        assertNotNullWarning(WorkMode.YOLO)
        assertNullWarning(WorkMode.CHAT)
    }

    @Test
    fun `self consistency toggle injects decision and critique rules for any mode`() {
        val promptWith = com.kirakira.kiracode.domain.prompt.BuiltinSystemPrompt.build(
            WorkMode.AGENT, cardMasterEnabled = true
        )
        val promptWithout = com.kirakira.kiracode.domain.prompt.BuiltinSystemPrompt.build(
            WorkMode.AGENT, cardMasterEnabled = false
        )
        assertTrue(promptWith.contains("自洽审查"))
        assertTrue(promptWith.contains("关键抉择"))
        assertTrue(promptWith.contains("反驳指南"))
        assertFalse(promptWithout.contains("自洽审查"))
        assertFalse(promptWithout.contains("反驳指南"))
        // 与全自动模式也可共存
        val fullAutoWith = com.kirakira.kiracode.domain.prompt.BuiltinSystemPrompt.build(
            WorkMode.YOLO, cardMasterEnabled = true
        )
        assertTrue(fullAutoWith.contains("计划书") && fullAutoWith.contains("自洽审查"))
        assertTrue(fullAutoWith.contains("反驳指南"))
    }

    private fun assertNotNullWarning(mode: WorkMode) {
        assertTrue(mode.warningText != null)
    }

    private fun assertNullWarning(mode: WorkMode) {
        assertFalse(mode.warningText != null)
    }
}
