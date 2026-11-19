package com.kirakira.kiracode

import com.kirakira.kiracode.domain.agent.CardOperation
import com.kirakira.kiracode.domain.agent.CardOperationApplier
import com.kirakira.kiracode.domain.model.CharacterCard
import com.kirakira.kiracode.domain.model.GenerationRequest
import com.kirakira.kiracode.domain.model.RegexGenerationMode
import com.kirakira.kiracode.domain.model.WorkMode
import com.kirakira.kiracode.domain.prompt.BuiltinSystemPrompt
import com.kirakira.kiracode.domain.prompt.RegexGenerationPrompts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RegexGenerationPromptsTest {

    @Test
    fun `default mode is foldable tags and generation request carries it`() {
        val request = GenerationRequest(
            referenceCard = CharacterCard(name = "参考"),
            newCharacterDescription = "新角色"
        )

        assertEquals(RegexGenerationMode.FOLDABLE_TAGS, RegexGenerationMode.DEFAULT)
        assertEquals(RegexGenerationMode.FOLDABLE_TAGS, request.regexGenerationMode)
        assertEquals("语义折叠", RegexGenerationMode.DEFAULT.label)
        assertEquals(4, RegexGenerationMode.entries.size)
    }

    @Test
    fun `full frontend prompt requires complete document and keeps aesthetics advisory`() {
        val prompt = RegexGenerationPrompts.promptFor(RegexGenerationMode.FULL_FRONTEND)

        assertTrue(prompt.contains("状态前端"))
        assertTrue(prompt.contains("真实换行的 ```html"))
        assertTrue(prompt.contains("<!DOCTYPE html>"))
        assertTrue(prompt.contains("<html lang=\"zh-CN\">"))
        assertTrue(prompt.contains("id=\"raw-scene\">$1</script>"))
        assertTrue(prompt.contains("必须使用真实换行"))
        assertTrue(prompt.contains("视觉设计不固定"))
        assertTrue(prompt.contains("建议，不是固定模板"))
        assertTrue(prompt.contains("用户明确提出的审美要求优先"))
        assertFalse(prompt.contains("必须使用 glassmorphism"))
        assertFalse(prompt.contains("核心色不得超过"))
        assertFalse(prompt.contains("必须固定 320px"))
    }

    @Test
    fun `foldable tags prompt defines three independent inline scripts`() {
        val prompt = RegexGenerationPrompts.promptFor(RegexGenerationMode.FOLDABLE_TAGS)

        assertTrue(prompt.contains("输出 3 条独立正则脚本"))
        assertTrue(prompt.contains("心理活动"))
        assertTrue(prompt.contains("动作描写"))
        assertTrue(prompt.contains("中文对话"))
        assertTrue(prompt.contains("/\\*([^*\\r\\n]{1,300})\\*/g"))
        assertTrue(prompt.contains("/「([^」\\r\\n]{1,200})」/g"))
        assertTrue(prompt.contains("局部 HTML 替换规则"))
        assertTrue(prompt.contains("不要求"))
        assertTrue(prompt.contains("视觉样式由模型根据题材和用户要求自由设计"))
    }

    @Test
    fun `reasoning and lightweight prompts keep their scope`() {
        val reasoning = RegexGenerationPrompts.promptFor(RegexGenerationMode.FOLDABLE_REASONING)
        val lightweight = RegexGenerationPrompts.promptFor(RegexGenerationMode.LIGHTWEIGHT)

        assertTrue(reasoning.contains("只输出一条"))
        assertTrue(reasoning.contains("局部 <details> 元素"))
        assertTrue(reasoning.contains("视觉样式由题材和用户要求决定"))
        assertTrue(reasoning.contains("markdownOnly: true"))
        assertTrue(lightweight.contains("最多输出 3 条"))
        assertTrue(lightweight.contains("只处理有明确定界符的内容"))
        assertTrue(lightweight.contains("不要求固定颜色、字体、布局或动效"))
    }

    @Test
    fun `builtin prompt injects selected mode for create and edit`() {
        val createDefault = BuiltinSystemPrompt.build(WorkMode.AGENT, isCardEditing = false)
        val editFrontend = BuiltinSystemPrompt.build(
            WorkMode.AGENT,
            isCardEditing = true,
            regexGenerationMode = RegexGenerationMode.FULL_FRONTEND
        )

        assertTrue(createDefault.contains("你正在为角色卡生成「语义折叠」正则"))
        assertTrue(editFrontend.contains("生成一套完整的状态前端正则"))
        assertFalse(createDefault.contains("system_prompt 或 post_history_instructions"))
    }

    @Test
    fun `every mode ends with mandatory tool execution and forbids text-only scripts`() {
        RegexGenerationMode.entries.forEach { mode ->
            listOf(
                RegexGenerationPrompts.promptFor(mode),
                RegexGenerationPrompts.agentPromptFor(mode)
            ).forEach { prompt ->
                assertTrue(prompt.contains("必须逐条调用 update_card_regex_add 工具写入当前角色卡"))
                assertTrue(prompt.contains("每条正则对应一次工具调用"))
                assertTrue(prompt.contains("禁止用 Markdown 代码块或列表展示正则内容"))
                assertTrue(
                    "执行要求必须位于 ${mode.name} 提示词末尾",
                    prompt.endsWith("只能看到工具调用后写入角色卡的结果。")
                )
            }
        }

        val builtin = BuiltinSystemPrompt.build(WorkMode.AGENT)
        assertTrue(builtin.contains("正则脚本生成任务中，你必须立即调用 update_card_regex_add 逐条写入"))
        assertTrue(builtin.contains("而不是用文本形式列出 findRegex/replaceString 让用户自己复制"))
    }

    @Test
    fun `text fallback also carries selected mode and display flags`() {
        val prompt = BuiltinSystemPrompt.build(
            WorkMode.AGENT,
            toolProtocol = com.kirakira.kiracode.domain.prompt.ToolProtocol.TEXT_FALLBACK,
            regexGenerationMode = RegexGenerationMode.FOLDABLE_REASONING
        )

        assertTrue(prompt.contains("你正在为角色卡生成「折叠思维链」正则"))
        assertTrue(prompt.contains("markdown_only: true"))
        assertTrue(prompt.contains("prompt_only: false"))
    }

    @Test
    fun `full frontend example is fenced by explicit anti-copy boundaries`() {
        val prompt = RegexGenerationPrompts.promptFor(RegexGenerationMode.FULL_FRONTEND)

        val warningBefore = prompt.indexOf("下方示范是给你理解结构用的参考")
        val exampleStart = prompt.indexOf("━━━━━━━━ 示范开始 ━━━━━━━━")
        val skeleton = prompt.indexOf("<!DOCTYPE html>", exampleStart)
        val exampleEnd = prompt.indexOf("━━━━━━━━ 示范结束 ━━━━━━━━")
        val warningAfter = prompt.indexOf("上方示范是给你理解结构用的参考")

        assertTrue(warningBefore >= 0)
        assertTrue(warningBefore < exampleStart)
        assertTrue(exampleStart < skeleton)
        assertTrue(skeleton < exampleEnd)
        assertTrue(exampleEnd < warningAfter)
        assertTrue(prompt.contains("不是角色卡的输出协议"))
        assertTrue(prompt.contains("不能将示范内容原样粘贴进 description 或任何角色卡字段"))
        assertTrue(prompt.contains("禁止原样复制到角色卡"))
        assertTrue(prompt.contains("以上内容不得出现在你生成的角色卡中"))
    }

    @Test
    fun `description protocols match modes and stay at the end`() {
        val full = RegexGenerationPrompts.appendDescriptionProtocol(
            "角色设定正文",
            RegexGenerationMode.FULL_FRONTEND
        )
        val tags = RegexGenerationPrompts.appendDescriptionProtocol(
            "角色设定正文",
            RegexGenerationMode.FOLDABLE_TAGS
        )

        assertTrue(full.startsWith("角色设定正文\n\n━━━━━━━━ 输出协议 ━━━━━━━━"))
        assertTrue(full.contains("<scene_body>"))
        assertTrue(full.contains("<div id=\"reader\">") == false)
        assertTrue(full.endsWith("━━━━━━━━━━━━━━━━━━━━━━"))
        assertTrue(tags.contains("━━━━━━━━ 输出风格协议 ━━━━━━━━"))
        assertTrue(tags.contains("<think>在这里写内心独白，折叠显示</think>"))
        assertTrue(tags.contains("{角色名}「有什么我能帮到你的？」"))
    }

    @Test
    fun `protocol append is idempotent and mode switch replaces old block`() {
        val once = RegexGenerationPrompts.appendDescriptionProtocol(
            "角色设定正文",
            RegexGenerationMode.FOLDABLE_TAGS
        )
        val twice = RegexGenerationPrompts.appendDescriptionProtocol(
            once,
            RegexGenerationMode.FOLDABLE_TAGS
        )
        val switched = RegexGenerationPrompts.appendDescriptionProtocol(
            twice,
            RegexGenerationMode.FULL_FRONTEND
        )

        assertEquals(once, twice)
        assertFalse(switched.contains("输出风格协议"))
        assertEquals(1, Regex("━━━━━━━━ 输出协议 ━━━━━━━━").findAll(switched).count())
        assertTrue(switched.endsWith(RegexGenerationPrompts.descriptionProtocolFor(RegexGenerationMode.FULL_FRONTEND)))
    }

    @Test
    fun `invalid replace card json leaves old description untouched`() {
        val old = CharacterCard(name = "旧卡", description = "不要修改")

        val updated = CardOperationApplier.apply(
            old,
            listOf(
                CardOperation.ReplaceCard(
                    json = "not-json",
                    regexGenerationMode = RegexGenerationMode.FULL_FRONTEND
                )
            )
        )

        assertEquals("不要修改", updated.description)
    }

    @Test
    fun `replace card operation applies selected protocol to decoded description`() {
        val source = CharacterCard(name = "新卡", description = "原始描述")
        val json = com.kirakira.kiracode.domain.model.st.StCardCodec.toJsonString(source, pretty = false)

        val updated = CardOperationApplier.apply(
            CharacterCard(name = "旧卡"),
            listOf(
                CardOperation.ReplaceCard(
                    json = json,
                    regexGenerationMode = RegexGenerationMode.FOLDABLE_REASONING
                )
            )
        )

        assertTrue(updated.description.startsWith("原始描述\n\n━━━━━━━━ 输出风格协议 ━━━━━━━━"))
        assertTrue(updated.description.endsWith(RegexGenerationPrompts.descriptionProtocolFor(RegexGenerationMode.FOLDABLE_REASONING)))
    }
}
