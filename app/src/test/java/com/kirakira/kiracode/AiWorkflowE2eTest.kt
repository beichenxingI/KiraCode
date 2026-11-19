package com.kirakira.kiracode

import com.kirakira.kiracode.domain.agent.CardOperation
import com.kirakira.kiracode.domain.agent.CardOperationApplier
import com.kirakira.kiracode.domain.agent.CardOpsParser
import com.kirakira.kiracode.domain.model.WorkMode
import com.kirakira.kiracode.domain.model.st.StCardCodec
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * AI 功能端到端验证：
 * 模拟真实 LLM 对三类创作指令的回复文本 → 协议解析 → 应用到卡 → 导出 SillyTavern 线格式，
 * 验证「AI 写设定 / 写世界书 / 写角色正则」全链路可正常落库。
 */
class AiWorkflowE2eTest {

    /** 指令 1：「帮我创建一个南疆女药师角色」——AI 的典型完整回复 */
    private val createReply = """
        好的！我来为你创建南疆女药师「沐瑶」。她外冷内热，精通毒理与医术。

        ```card-update
        [field:name]
        沐瑶
        [/field]

        [field:description]
        {{char}}是南疆边城「回春堂」的年轻药师，常年与药材瘴气为伴。眉眼清冷，左腕缠一圈银针，
        因幼年目睹瘟疫失去家人而立志习医。看似冷淡寡言，实则心细如发，会默默记住每位病人的忌口。
        [/field]

        [field:personality]
        外冷内热；言辞简短但行动周到；对病患极其耐心；厌恶浪费药材；谈及瘟疫往事时会沉默。
        [/field]

        [field:scenario]
        战乱年代的南疆边城，回春堂药铺，暮色中飘着苦涩的药香。
        [/field]

        [field:first_mes]
        *碾槽声不紧不慢*
        ……站门口做什么？要看病就进来，抓药报方子。
        *头也不抬*
        [/field]

        [book.add]
        keys: 银针, 医术
        content: {{char}}随身携带三十六根银针，可医人亦可防身。针法承自其师「药翁」，讲究快、准、稳。
        comment: 银针与医术
        constant: false
        insertion_order: 100
        position: before_char
        [/book.add]

        [book.add]
        keys: 南疆, 回春堂
        content: 南疆多瘴气，入林需服避毒散。回春堂是边城唯一药铺，柜台下暗格藏有解毒奇方。
        comment: 南疆世界观
        constant: true
        insertion_order: 50
        position: before_char
        [/book.add]

        [regex.add]
        script_name: 去思考标签
        find_regex: <think>[\s\S]*?</think>
        replace_string:
        placement: 2
        [/regex.add]
        ```

        以上是初始版本，你可以让我调整性格、补充世界书或继续细化开场白。
    """.trimIndent()

    @Test
    fun `ai can create character with settings worldbook and regex`() {
        // 1. 解析操作
        val ops = CardOpsParser.parse(createReply)
        assertTrue("应解析出操作", ops.isNotEmpty())
        assertEquals(8, ops.size) // 5 field + 2 book.add + 1 regex.add

        // 2. 应用到空卡（全局/代理人模式确认后）
        val base = com.kirakira.kiracode.domain.model.CharacterCard(name = "")
        val card = CardOperationApplier.apply(base, ops)

        // 设定写入
        assertEquals("沐瑶", card.name)
        assertTrue(card.description.contains("回春堂"))
        assertTrue(card.firstMessage.contains("碾槽"))
        assertTrue(card.personality.contains("外冷内热"))

        // 世界书写入（SillyTavern 结构）
        val book = card.characterBook
        assertNotNull(book)
        assertEquals(2, book!!.entries.size)
        val silver = book.entries.first { it.comment == "银针与医术" }
        assertEquals(listOf("银针", "医术"), silver.keys)
        assertEquals("before_char", silver.position)
        assertTrue(silver.content.contains("{{char}}"))
        val lore = book.entries.first { it.constant }
        assertEquals(50, lore.insertionOrder)

        // 正则写入
        assertEquals(1, card.regexScripts.size)
        assertEquals("<think>[\\s\\S]*?</think>", card.regexScripts[0].findRegex)

        // 3. 导出为 SillyTavern v2 线格式并复核结构
        val json = StCardCodec.toJsonString(card, pretty = false)
        val root = StCardCodec.json.parseToJsonElement(json).jsonObject
        assertEquals(
            "chara_card_v2",
            (root["spec"] as kotlinx.serialization.json.JsonPrimitive).content
        )
        val data = root["data"]!!.jsonObject
        assertNotNull(data["character_book"])
        val entries = (data["character_book"]!!.jsonObject["entries"] as kotlinx.serialization.json.JsonArray)
        assertEquals(2, entries.size)
        val ext = data["extensions"]!!.jsonObject
        val scripts = ext["regex_scripts"] as kotlinx.serialization.json.JsonArray
        assertEquals(1, scripts.size)

        // 4. 再导入往返无损
        val re = StCardCodec.decode(json).getOrThrow()
        assertEquals("沐瑶", re.name)
        assertEquals(2, re.characterBook!!.entries.size)
        assertEquals(1, re.regexScripts.size)
    }

    /** 指令 2：对既有卡的增量修改（改设定 + 改世界书 + 删正则） */
    @Test
    fun `ai can edit existing card incrementally`() {
        // 先构建一张已有卡
        val initial = CardOperationApplier.apply(
            com.kirakira.kiracode.domain.model.CharacterCard(name = "沐瑶"),
            listOf(
                CardOperation.BookAdd(keys = listOf("旧词"), content = "旧内容", comment = "待更新条目"),
                CardOperation.RegexAdd(scriptName = "旧规则", findRegex = "a", replaceString = "b")
            )
        )
        val uid = initial.characterBook!!.entries.single().id

        val editReply = """
            收到，我把她的语气调得更冷一些，更新世界书条目，并移除旧正则。

            ```card-update
            [field:first_mes]
            ……有事？
            [/field]

            [book.update uid=$uid]
            content: 更新后的条目正文。
            enabled: true
            [/book.update]

            [regex.delete name=旧规则]
            ```
        """.trimIndent()

        val ops = CardOpsParser.parse(editReply)
        assertEquals(3, ops.size)
        val updated = CardOperationApplier.apply(initial, ops)

        assertEquals("……有事？", updated.firstMessage)
        assertEquals("更新后的条目正文。", updated.characterBook!!.entries.single().content)
        assertEquals(0, updated.regexScripts.size)
    }

    /** 模式语义验证：CHAT 模式引擎层不产生操作（P0：旧 ChatEngine.send 已移除，此处直接断言协议层等价行为） */
    @Test
    fun `chat mode yields no operations`() {
        // P0：CHAT 模式无工具、不解析不执行 card-update 文本协议——此处断言解析器可被完全剥离
        val stripped = CardOpsParser.stripUpdateBlocks(createReply)
        assertTrue(!stripped.contains("[field:"))
        // UI 层在 CHAT 模式忽略 ops（见 ChatViewModel.finishAssistant），此处验证解析器可被完全剥离
        val ops = CardOpsParser.parse(stripped)
        assertEquals(0, ops.size)
    }
}
