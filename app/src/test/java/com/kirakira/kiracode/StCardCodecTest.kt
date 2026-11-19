package com.kirakira.kiracode

import com.kirakira.kiracode.domain.model.CharacterCard
import com.kirakira.kiracode.domain.model.st.StBookEntry
import com.kirakira.kiracode.domain.model.st.StCardCodec
import com.kirakira.kiracode.domain.model.st.StCharacterBook
import com.kirakira.kiracode.domain.model.st.StRegexScript
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SillyTavern 角色卡编解码往返测试（无损保真核心保障）。
 */
class StCardCodecTest {

    private val prettyJson = Json { prettyPrint = true }

    /** v2 包裹格式样本：含未知扩展字段、世界书、正则脚本 */
    private val v2Sample = """
    {
      "spec": "chara_card_v2",
      "spec_version": "2.0",
      "data": {
        "name": "沐瑶",
        "description": "一位来自南疆的年轻药师，性格外冷内热。",
        "personality": "冷淡寡言但心细如发",
        "scenario": "战乱年代的边城药铺",
        "first_mes": "*她头也不抬地碾着药材* ……要抓什么药？",
        "mes_example": "<START>\n{{user}}: 你还好吗\n{{char}}: *手一顿* 与你无关。",
        "creator_notes": "测试备注",
        "system_prompt": "",
        "post_history_instructions": "",
        "alternate_greetings": [" alternative 1 ", "greeting two"],
        "tags": ["药师", "南疆"],
        "creator": "tester",
        "character_version": "1.2",
        "unknown_future_field": {"keep": true},
        "character_book": {
          "name": "南疆设定集",
          "entries": [
            {
              "uid": 0,
              "id": 0,
              "keys": ["瘴气", "毒"],
              "secondary_keys": ["药"],
              "keysecondary": ["药"],
              "content": "南疆多瘴气，入林需服避毒散。",
              "comment": "瘴气设定",
              "constant": false,
              "selective": true,
              "insertion_order": 99,
              "enabled": true,
              "position": true,
              "extensions": {"position": 0, "probability": 100}
            }
          ]
        },
        "extensions": {
          "talkativeness": "0.5",
          "fav": false,
          "regex_scripts": [
            {
              "id": "r1",
              "scriptName": "去思考标签",
              "findRegex": "<think>[\\s\\S]*?</think>",
              "replaceString": "",
              "placement": [2],
              "disabled": false,
              "markdownOnly": false,
              "promptOnly": false,
              "runOnEdit": true,
              "substituteRegex": false,
              "custom_st_field": "preserve-me"
            }
          ],
          "depth_prompt": {"prompt": "保持角色", "depth": 4}
        }
      }
    }
    """.trimIndent()

    @Test
    fun `decode v2 wrapped card extracts all canonical fields`() {
        val card = StCardCodec.decode(v2Sample).getOrThrow()
        assertEquals("沐瑶", card.name)
        assertEquals("冷淡寡言但心细如发", card.personality)
        assertEquals("1.2", card.version)
        assertEquals(listOf("药师", "南疆"), card.tags)
        assertEquals(2, card.alternateGreetings.size)
        assertEquals("chara_card_v2", card.spec)
        assertNotNull(card.characterBook)
        assertEquals(1, card.characterBook!!.entries.size)
        assertEquals(1, card.regexScripts.size)
        assertEquals("去思考标签", card.regexScripts[0].scriptName)
    }

    @Test
    fun `world book entry decoded with keys content and numeric position`() {
        val card = StCardCodec.decode(v2Sample).getOrThrow()
        val entry = card.characterBook!!.entries.first()
        assertEquals(listOf("瘴气", "毒"), entry.keys)
        assertEquals(listOf("药"), entry.secondaryKeys)
        assertEquals("瘴气设定", entry.comment)
        assertTrue(entry.content.contains("避毒散"))
        assertEquals("before_char", entry.position)
        assertEquals(99, entry.insertionOrder)
        assertTrue(entry.enabled)
    }

    @Test
    fun `roundtrip preserves unknown extension fields`() {
        val original = StCardCodec.decode(v2Sample).getOrThrow()
        val out = StCardCodec.toJsonString(original, pretty = false)
        assertTrue("未知顶层字段必须保留", out.contains("\"unknown_future_field\""))
        assertTrue("depth_prompt 必须保留", out.contains("depth_prompt"))
        assertTrue("正则自定义字段必须保留", out.contains("custom_st_field"))
        assertTrue("talkativeness 必须保留", out.contains("talkativeness"))

        // 二次解码后字段仍在
        val second = StCardCodec.decode(out).getOrThrow()
        assertEquals(original.name, second.name)
        assertEquals(1, second.characterBook!!.entries.size)
        assertEquals(1, second.regexScripts.size)
        assertEquals("preserve-me",
            (second.regexScripts[0].raw?.get("custom_st_field") as? JsonPrimitive)?.content)
    }

    @Test
    fun `roundtrip keeps book and regex structures intact`() {
        val original = StCardCodec.decode(v2Sample).getOrThrow()
        val out = StCardCodec.toJsonString(original, pretty = false)
        val re = StCardCodec.decode(out).getOrThrow()

        val e1 = original.characterBook!!.entries[0]
        val e2 = re.characterBook!!.entries[0]
        assertEquals(e1.keys, e2.keys)
        assertEquals(e1.content, e2.content)
        assertEquals(e1.comment, e2.comment)

        assertEquals(original.regexScripts[0].findRegex, re.regexScripts[0].findRegex)
        assertEquals(original.regexScripts[0].replaceString, re.regexScripts[0].replaceString)
    }

    @Test
    fun `decode flat v1 style json`() {
        val v1 = """
            {"name":"老王","description":"隔壁邻居","personality":"热心","first_mes":"吃了吗？"}
        """.trimIndent()
        val card = StCardCodec.decode(v1).getOrThrow()
        assertEquals("老王", card.name)
        assertEquals("热心", card.personality)
    }

    @Test
    fun `edit then export reflects modifications without losing raws`() {
        val card = StCardCodec.decode(v2Sample).getOrThrow()
        val edited = card.copy(
            name = "沐瑶·改",
            characterBook = card.characterBook!!.copy(
                entries = card.characterBook!!.entries + StBookEntry(
                    id = 1, keys = listOf("银针"), content = "随身银针，医人亦防身。", comment = "银针"
                )
            ),
            regexScripts = emptyList()
        )
        val out = StCardCodec.toJsonString(edited, pretty = false)
        assertTrue(out.contains("沐瑶·改"))
        assertTrue(out.contains("银针"))
        // 正则被清空但 extensions 其他键保留
        val re = StCardCodec.decode(out).getOrThrow()
        assertEquals(0, re.regexScripts.size)
        assertNotNull(re.rawData?.get("extensions")?.jsonObject?.get("depth_prompt"))
    }

    @Test
    fun `invalid json returns failure not crash`() {
        val result = StCardCodec.decode("{not valid json")
        assertTrue(result.isFailure)
    }

    @Test
    fun `v3 spec roundtrip`() {
        val card = CharacterCard(
            name = "Aria",
            description = "test",
            spec = "chara_card_v3",
            specVersion = "3.0",
            rawData = null,
            characterBook = StCharacterBook(name = "book", entries = listOf(
                StBookEntry(id = 0, keys = listOf("k"), content = "c")
            )),
            regexScripts = listOf(StRegexScript(scriptName = "s", findRegex = "f", replaceString = "r"))
        )
        val out = StCardCodec.toJsonString(card, pretty = false)
        val obj = prettyJson.parseToJsonElement(out).jsonObject
        assertEquals("chara_card_v3", (obj["spec"] as JsonPrimitive).content)
        val back = StCardCodec.decode(out).getOrThrow()
        assertEquals("chara_card_v3", back.spec)
        assertEquals(1, back.characterBook!!.entries.size)
    }

    @Test
    fun `entry extensions fields modeled and substituteRegex is int`() {
        // 增量1 A2：extensions 常用字段建模（v2Sample 的 extensions.probability=100）
        val card = StCardCodec.decode(v2Sample).getOrThrow()
        assertEquals(100, card.characterBook!!.entries.first().probability)

        // 增量1 A1：caseSensitive 此前从不赋值，现在可从 extensions 读取
        val withExt = """
            {"name":"test","entries":[{"uid":0,"keys":["k"],"content":"c","extensions":{"case_sensitive":true,"match_whole_words":false,"useProbability":true,"selectiveLogic":2,"group":"g1","group_override":true,"group_weight":50,"use_group_scoring":true,"exclude_recursion":true,"prevent_recursion":true}}]}
        """.trimIndent()
        val book = StCardCodec.decodeBook(prettyJson.parseToJsonElement(withExt))!!
        val e = book.entries.first()
        assertTrue(e.caseSensitive == true)
        assertTrue(e.matchWholeWords == false)
        assertTrue(e.useProbability == true)
        assertEquals(2, e.selectiveLogic)
        assertEquals("g1", e.group)
        assertEquals(true, e.groupOverride)
        assertEquals(50, e.groupWeight)
        assertEquals(true, e.useGroupScoring)
        assertEquals(true, e.excludeRecursion)
        assertEquals(true, e.preventRecursion)

        // encode 回写 extensions（键名与 ST 读取约定一致：useProbability 驼峰）
        val out = StCardCodec.encodeBook(book).toString()
        assertTrue(out.contains("\"case_sensitive\":true"))
        assertTrue(out.contains("\"useProbability\":true"))

        // 增量1 A1：substituteRegex Int（ST 1=RAW 不再被吞成 false）+ placement 数组不再丢失
        val regexJson = """
            [{"id":"r1","scriptName":"s","findRegex":"f","replaceString":"","placement":[1,2],"substituteRegex":1}]
        """.trimIndent()
        val scripts = StCardCodec.decodeRegexScripts(prettyJson.parseToJsonElement(regexJson))
        assertEquals(1, scripts[0].substituteRegex)
        assertEquals(listOf(1, 2), scripts[0].placement)

        // 增量1 A1：REASONING=6 常量存在
        assertEquals(6, com.kirakira.kiracode.domain.model.st.RegexPlacement.REASONING)
    }
}
