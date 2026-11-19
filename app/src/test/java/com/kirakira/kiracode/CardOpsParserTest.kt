package com.kirakira.kiracode

import com.kirakira.kiracode.domain.agent.CardOperation
import com.kirakira.kiracode.domain.agent.CardOperationApplier
import com.kirakira.kiracode.domain.agent.CardOpsParser
import com.kirakira.kiracode.domain.model.CharacterCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Agent 操作协议解析与应用测试。
 */
class CardOpsParserTest {

    private val reply = """
        好的，我帮你修改名字并新增一条世界书。

        ```card-update
        [field:name]
        沐瑶
        [/field]

        [field:tags]
        药师, 南疆, 高冷
        [/field]

        [book.add]
        keys: 银针, 医术
        content: 她随身携带一套银针，可医人亦可防身。
        comment: 银针设定
        constant: false
        insertion_order: 50
        position: after_char
        [/book.add]
        ```

        请确认是否应用以上修改。
    """.trimIndent()

    @Test
    fun `parse extracts field and book operations`() {
        val ops = CardOpsParser.parse(reply)
        assertEquals(3, ops.size)
        assertTrue(ops[0] is CardOperation.SetField)
        assertEquals("name", (ops[0] as CardOperation.SetField).fieldName)
        assertEquals("沐瑶", (ops[0] as CardOperation.SetField).value)

        val tags = ops[1] as CardOperation.SetField
        assertEquals("tags", tags.fieldName)
        assertTrue(tags.value.contains("南疆"))

        val add = ops[2] as CardOperation.BookAdd
        assertEquals(listOf("银针", "医术"), add.keys)
        assertEquals("银针设定", add.comment)
        assertEquals(50, add.insertionOrder)
        assertEquals("after_char", add.position)
    }

    @Test
    fun `stripUpdateBlocks removes blocks keeping prose`() {
        val stripped = CardOpsParser.stripUpdateBlocks(reply)
        assertTrue(stripped.contains("好的，我帮你修改名字"))
        assertTrue(stripped.contains("请确认是否应用以上修改。"))
        assertTrue(!stripped.contains("[field:name]"))
        assertTrue(!stripped.contains("```card-update"))
    }

    @Test
    fun `apply set field and tags`() {
        val card = CharacterCard(name = "旧名")
        val newCard = CardOperationApplier.apply(card, listOf(
            CardOperation.SetField("name", "新名"),
            CardOperation.SetField("tags", "a,b,c"),
            CardOperation.SetField("first_mes", "你好呀")
        ))
        assertEquals("新名", newCard.name)
        assertEquals(listOf("a", "b", "c", com.kirakira.kiracode.domain.model.KiraWatermark.VISIBLE_TAG), newCard.tags)
        assertEquals("你好呀", newCard.firstMessage)
    }

    @Test
    fun `apply book lifecycle add update delete`() {
        val card = CharacterCard(name = "X")
        val added = CardOperationApplier.apply(card, listOf(
            CardOperation.BookAdd(
                keys = listOf("k1"), content = "c1", comment = "条目A"
            )
        ))
        val entry = added.characterBook!!.entries.single()
        assertEquals(0L, entry.id)

        val updated = CardOperationApplier.apply(added, listOf(
            CardOperation.BookUpdate(uid = 0, content = "c2", enabled = false)
        ))
        assertEquals("c2", updated.characterBook!!.entries.single().content)
        assertTrue(!updated.characterBook!!.entries.single().enabled)

        val deleted = CardOperationApplier.apply(updated, listOf(CardOperation.BookDelete(uid = 0)))
        assertTrue(deleted.characterBook!!.entries.isEmpty())
    }

    @Test
    fun `apply regex add and delete by name`() {
        val card = CharacterCard(name = "X")
        val added = CardOperationApplier.apply(card, listOf(
            CardOperation.RegexAdd(
                scriptName = "去思考",
                findRegex = "<think>[\\s\\S]*?</think>",
                replaceString = "",
                placement = listOf(2)
            )
        ))
        assertEquals(1, added.regexScripts.size)
        val deleted = CardOperationApplier.apply(added, listOf(
            CardOperation.RegexDelete(idOrName = "去思考")
        ))
        assertEquals(0, deleted.regexScripts.size)
    }

    @Test
    fun `unknown fields in block are ignored safely`() {
        val weird = """
            ```card-update
            [field:not_allowed_field]
            x
            [/field]
            [book.update uid=999]
            content: no such uid
            [/book.update]
            ```
        """.trimIndent()
        val ops = CardOpsParser.parse(weird)
        // 未白名单字段被解析为 SetField 但应用时被忽略；不存在的 uid 更新为空操作
        val card = CharacterCard(name = "base")
        val applied = CardOperationApplier.apply(card, ops.filterIsInstance<CardOperation.SetField>())
        assertEquals("base", applied.name)
    }
/**
 * 回归测试：自闭合指令（系统提示词规定的删除/清空语法）必须可被解析。
 * 此前解析器仅支持成对闭合，导致真实 AI 输出的删除指令被静默丢弃。
 */
@org.junit.Test
fun `self closing delete and clear directives are parsed`() {
    val reply = """
        ```card-update
        [book.delete uid=3]
        [regex.delete name=去思考]
        [book.clear]
        ```
    """.trimIndent()
    val ops = CardOpsParser.parse(reply)
    org.junit.Assert.assertEquals(3, ops.size)
    org.junit.Assert.assertTrue(ops[0] is com.kirakira.kiracode.domain.agent.CardOperation.BookDelete)
    org.junit.Assert.assertEquals(3L, (ops[0] as com.kirakira.kiracode.domain.agent.CardOperation.BookDelete).uid)
    org.junit.Assert.assertEquals("去思考", (ops[1] as com.kirakira.kiracode.domain.agent.CardOperation.RegexDelete).idOrName)
    org.junit.Assert.assertTrue(ops[2] is com.kirakira.kiracode.domain.agent.CardOperation.BookClear)
}

/**
 * 回归测试：AI 可能按提示词旧格式「竖线内联分隔键值对」输出 book.add，
 * 此前解析器只认换行，导致 keys 吞下整段、content 为空（字段拼接根因）。
 * 现在解析器对「| 后紧跟 key:」做换行归一化，键值正确分离。
 */
@org.junit.Test
fun `pipe-separated book add is parsed with separated keys and content`() {
    val reply = """
        ```card-update
        [book.add]comment:樱华高中|keys:樱华高中,学校,校园|secondary_keys:樱华|content:位于城市东郊的一所老牌高中，钟楼是它的标志|constant:false|selective:true|insertion_order:100|position:before_char[/book.add]
        ```
    """.trimIndent()
    val ops = CardOpsParser.parse(reply)
    org.junit.Assert.assertEquals(1, ops.size)
    val add = ops[0] as com.kirakira.kiracode.domain.agent.CardOperation.BookAdd
    org.junit.Assert.assertEquals(listOf("樱华高中", "学校", "校园"), add.keys)
    org.junit.Assert.assertFalse(add.keys.contains("content"))
    org.junit.Assert.assertEquals("位于城市东郊的一所老牌高中，钟楼是它的标志", add.content)
    org.junit.Assert.assertEquals(false, add.constant)
    org.junit.Assert.assertEquals("before_char", add.position)
}
}