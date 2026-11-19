package com.kirakira.kiracode

import com.kirakira.kiracode.domain.agent.CardOperation
import com.kirakira.kiracode.domain.agent.CardOperationApplier
import com.kirakira.kiracode.domain.agent.aiGuessFlag
import com.kirakira.kiracode.domain.model.CharacterCard
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * V2/V3 交互逻辑测试：
 * 脑补标注（ai_guess）、字段读取（Diff 旧版取值）、中文标签映射、
 * 交互工具 schema（ask_user / submit_plan / complete_step）。
 */
class V2InteractionTest {

    // ==================== 脑补标注 ====================

    @Test
    fun `aiGuessFlag默认false_显式标注读取正确`() {
        val normal = CardOperation.SetField("first_mes", "你好")
        val guess = CardOperation.SetField("first_mes", "你好", isAiGuess = true)
        val guessBook = CardOperation.BookAdd(
            keys = listOf("魔法"), content = "魔法体系设定", comment = "魔法体系", isAiGuess = true
        )
        val plain = CardOperation.BookDelete(0)

        assertFalse("未标注的操作默认非脑补", normal.aiGuessFlag)
        assertTrue("SetField 显式 ai_guess=true", guess.aiGuessFlag)
        assertTrue("BookAdd 显式 ai_guess=true", guessBook.aiGuessFlag)
        assertFalse("其他操作无脑补语义", plain.aiGuessFlag)
    }

    @Test
    fun `脑补标注不改变应用行为`() {
        val card = CharacterCard(name = "艾伦")
        val applied = CardOperationApplier.apply(
            card,
            listOf(CardOperation.SetField("personality", "傲娇", isAiGuess = true))
        )
        assertEquals("脑补标注的字段照常应用", "傲娇", applied.personality)
    }

    // ==================== 字段读取（Diff 旧版取值） ====================

    @Test
    fun `readField读取全部白名单字段`() {
        val card = CharacterCard(
            name = "艾伦",
            description = "蒸汽朋克魔法师",
            personality = "傲娇",
            scenario = "魔法学院",
            firstMessage = "嘿，叫我艾伦就好",
            exampleDialogue = "{{user}}: 你好\n{{char}}: 哼",
            creatorNotes = "测试卡",
            systemPrompt = "你是艾伦",
            postHistoryInstructions = "保持人设",
            creator = "Kira",
            version = "1.0",
            nickname = "小火",
            tags = listOf("魔法", "傲娇"),
            alternateGreetings = listOf("开场白A", "开场白B")
        )
        assertEquals("艾伦", CardOperationApplier.readField(card, "name"))
        assertEquals("蒸汽朋克魔法师", CardOperationApplier.readField(card, "description"))
        assertEquals("傲娇", CardOperationApplier.readField(card, "personality"))
        assertEquals("魔法学院", CardOperationApplier.readField(card, "scenario"))
        assertEquals("嘿，叫我艾伦就好", CardOperationApplier.readField(card, "first_mes"))
        assertEquals("测试卡", CardOperationApplier.readField(card, "creator_notes"))
        assertEquals("你是艾伦", CardOperationApplier.readField(card, "system_prompt"))
        assertEquals("保持人设", CardOperationApplier.readField(card, "post_history_instructions"))
        assertEquals("Kira", CardOperationApplier.readField(card, "creator"))
        assertEquals("1.0", CardOperationApplier.readField(card, "character_version"))
        assertEquals("小火", CardOperationApplier.readField(card, "nickname"))
        assertEquals("魔法、傲娇", CardOperationApplier.readField(card, "tags"))
        assertEquals("开场白A\n开场白B", CardOperationApplier.readField(card, "alternate_greetings"))
    }

    @Test
    fun `readField空字段返回空白_Diff门槛依据`() {
        val card = CharacterCard(name = "")
        assertTrue("空 name → 无 Diff（首次创建直接应用）", CardOperationApplier.readField(card, "name").isBlank())
        assertTrue("空 description → 无 Diff", CardOperationApplier.readField(card, "description").isBlank())
        assertTrue("未知字段 → 无 Diff", CardOperationApplier.readField(card, "unknown_field").isBlank())
        val filled = CardOperationApplier.apply(card, listOf(CardOperation.SetField("name", "艾伦")))
        assertFalse("已填字段 → 弹 Diff", CardOperationApplier.readField(filled, "name").isBlank())
    }

    @Test
    fun `fieldLabel中文映射完整`() {
        assertEquals("名字", CardOperationApplier.fieldLabel("name"))
        assertEquals("人设描述", CardOperationApplier.fieldLabel("description"))
        assertEquals("性格", CardOperationApplier.fieldLabel("personality"))
        assertEquals("场景", CardOperationApplier.fieldLabel("scenario"))
        assertEquals("开场白", CardOperationApplier.fieldLabel("first_mes"))
        assertEquals("示例对话", CardOperationApplier.fieldLabel("mes_example"))
        assertEquals("系统提示词", CardOperationApplier.fieldLabel("system_prompt"))
        assertEquals("标签", CardOperationApplier.fieldLabel("tags"))
        assertEquals("未知字段原样返回", "some_field", CardOperationApplier.fieldLabel("some_field"))
    }

    // ==================== Diff 决定后的应用/回滚 ====================

    @Test
    fun `接受修改-应用新值`() {
        val card = CharacterCard(name = "艾伦", firstMessage = "你好，我是艾伦，一位魔法师。")
        val applied = CardOperationApplier.apply(
            card,
            listOf(CardOperation.SetField("first_mes", "嘿，叫我艾伦就好。魔法？嗯…算是会一点吧。"))
        )
        assertEquals("接受后应用新版", "嘿，叫我艾伦就好。魔法？嗯…算是会一点吧。", applied.firstMessage)
    }

    @Test
    fun `拒绝修改-回滚即不应用`() {
        val card = CharacterCard(name = "艾伦", firstMessage = "你好，我是艾伦，一位魔法师。")
        // 拒绝 = 操作不进入应用列表 → 原卡保持原值
        assertEquals("拒绝后保持原值", "你好，我是艾伦，一位魔法师。", card.firstMessage)
    }

    @Test
    fun `世界书更新-Diff后应用新内容`() {
        val card = CharacterCard(name = "艾伦").let { c ->
            CardOperationApplier.apply(
                c,
                listOf(
                    CardOperation.BookAdd(
                        keys = listOf("童年"), content = "从小在贵族家庭长大", comment = "童年经历"
                    )
                )
            )
        }
        val entry = card.characterBook!!.entries.first()
        assertEquals("从小在贵族家庭长大", entry.content)

        val updated = CardOperationApplier.apply(
            card,
            listOf(
                CardOperation.BookUpdate(uid = entry.id, content = "生于没落贵族，幼年家道中落")
            )
        )
        assertEquals("Diff 接受后条目内容更新", "生于没落贵族，幼年家道中落", updated.characterBook!!.entries.first().content)
    }

    // ==================== 交互工具 schema ====================

    @Test
    fun `ask_user_submit_plan_complete_step存在且结构正确`() {
        val functions = com.kirakira.kiracode.domain.agent.tools.CardFunctions.getAllFunctions()
        val names = functions.mapNotNull {
            it["function"]?.jsonObject?.get("name")?.jsonPrimitive?.content
        }
        assertTrue("必须有 ask_user", "ask_user" in names)
        assertTrue("必须有 submit_plan", "submit_plan" in names)
        assertTrue("必须有 complete_step", "complete_step" in names)

        val askUser = functions.first {
            it["function"]?.jsonObject?.get("name")?.jsonPrimitive?.content == "ask_user"
        }
        val props = askUser["function"]?.jsonObject
            ?.get("parameters")?.jsonObject?.get("properties")?.jsonObject
        assertEquals("ask_user 必须有 question", true, props?.get("question") != null)
        assertEquals("ask_user 必须有 options", true, props?.get("options") != null)

        val submitPlan = functions.first {
            it["function"]?.jsonObject?.get("name")?.jsonPrimitive?.content == "submit_plan"
        }
        val planProps = submitPlan["function"]?.jsonObject
            ?.get("parameters")?.jsonObject?.get("properties")?.jsonObject
        assertEquals("submit_plan 必须有 steps", true, planProps?.get("steps") != null)
    }

    @Test
    fun `update_card_field支持ai_guess参数`() {
        val fieldTool = com.kirakira.kiracode.domain.agent.tools.CardFunctions.getAllFunctions()
            .mapNotNull { it["function"]?.jsonObject }
            .first { it["name"]?.jsonPrimitive?.content == "update_card_field" }
        val props = fieldTool["parameters"]?.jsonObject?.get("properties")?.jsonObject
        val aiGuess = props?.get("ai_guess")?.jsonObject
        assertEquals("必须有 ai_guess 参数", true, aiGuess != null)
        assertEquals("ai_guess 类型为 boolean", "boolean", aiGuess?.get("type")?.jsonPrimitive?.content)
    }
}
