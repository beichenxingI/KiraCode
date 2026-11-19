package com.kirakira.kiracode

import com.kirakira.kiracode.domain.model.WorkMode
import com.kirakira.kiracode.domain.prompt.BuiltinSystemPrompt
import com.kirakira.kiracode.domain.prompt.ToolProtocol
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P0 协议守护测试：正常工具路径只教 function calling，文本协议只存在于降级路径。
 */
class BuiltinSystemPromptProtocolTest {

    /** 正常工具路径（FUNCTION_CALLING）下不得出现的旧文本协议标记 */
    private val textProtocolMarkers = listOf(
        "[field:", "[book.add]", "[book.update", "[regex.add]", "[card.replace]",
        "card-update", "[plan.", "[task.", "[subagent.request]", "[web.search"
    )

    @Test
    fun `function calling path contains no text protocol teaching`() {
        for (mode in listOf(WorkMode.AGENT, WorkMode.YOLO)) {
            for (editing in listOf(true, false)) {
                val prompt = BuiltinSystemPrompt.build(
                    mode = mode,
                    isCardEditing = editing,
                    webSearchEnabled = true
                )
                textProtocolMarkers.forEach { marker ->
                    assertFalse(
                        "FUNCTION_CALLING 提示词不得包含文本协议标记 $marker（mode=$mode editing=$editing）",
                        prompt.contains(marker)
                    )
                }
            }
        }
    }

    @Test
    fun `function calling path keeps core layers`() {
        val prompt = BuiltinSystemPrompt.build(WorkMode.AGENT)
        assertTrue(prompt.contains("制卡助手"))
        assertTrue(prompt.contains("诚实与边界"))
        assertTrue(prompt.contains("运行规则"))
        assertTrue(prompt.contains("工具协议"))
        assertTrue(prompt.contains("代理人"))
        assertTrue(prompt.contains("从零创作新卡"))
        assertTrue(prompt.contains("角色卡质量规范"))
        assertTrue(prompt.contains("停止调用工具"))
    }

    @Test
    fun `fallback path teaches only parser-supported protocol`() {
        val notice = BuiltinSystemPrompt.fallbackNotice(WorkMode.AGENT)
        assertTrue(notice.contains("card-update"))
        assertTrue(notice.contains("[field:"))
        assertTrue(notice.contains("[book.add]"))
        assertTrue(notice.contains("[book.update"))
        assertTrue(notice.contains("[regex.add]"))
        assertTrue(notice.contains("[card.replace]"))
        assertTrue(notice.contains("[plan."))
        assertTrue(notice.contains("[task."))
        // 从未实现/已无执行器的协议不得出现在降级教学中
        assertFalse(notice.contains("[subagent.request]"))
        assertFalse(notice.contains("[web.search]"))
        // 降级协议不得宣传双协议可选（解释"不支持 function calling"除外）
        assertFalse(notice.contains("任选"))
        assertFalse(notice.contains("也可以使用 function calling"))
    }

    @Test
    fun `fallback protocol aligns with executor semantics`() {
        val notice = BuiltinSystemPrompt.fallbackNotice(WorkMode.YOLO)
        // alternate_greetings 按换行切分（CardOperationApplier.applySetField）
        assertTrue(notice.contains("alternate_greetings 每行一条"))
        // position 仅 before_char/after_char（工具 schema 枚举一致）
        assertTrue(notice.contains("position: before_char"))
        assertFalse(notice.contains("extensions.position"))
        // book.update 是整段替换，必须提示保留未修改部分
        assertTrue(notice.contains("整段替换"))
        assertTrue(notice.contains("保留未修改部分"))
    }

    @Test
    fun `chat mode has no tool protocol`() {
        val prompt = BuiltinSystemPrompt.build(WorkMode.CHAT)
        textProtocolMarkers.forEach { marker ->
            assertFalse("CHAT 提示词不得包含协议标记 $marker", prompt.contains(marker))
        }
        assertTrue(prompt.contains("创作顾问"))
    }

    @Test
    fun `edit workflow differs from create workflow`() {
        val create = BuiltinSystemPrompt.build(WorkMode.AGENT, isCardEditing = false)
        val edit = BuiltinSystemPrompt.build(WorkMode.AGENT, isCardEditing = true)
        assertTrue(create.contains("从零创作新卡"))
        assertTrue(edit.contains("优化既有角色卡"))
        assertTrue(edit.contains("update_card_book_update"))
    }

    @Test
    fun `mode semantics are explicit`() {
        val yolo = BuiltinSystemPrompt.build(WorkMode.YOLO)
        assertTrue(yolo.contains("ai_guess"))
        assertTrue(yolo.contains("不询问用户"))

        val agent = BuiltinSystemPrompt.build(WorkMode.AGENT)
        assertTrue(agent.contains("ask_user"))
        assertTrue(agent.contains("submit_plan"))
    }
}
