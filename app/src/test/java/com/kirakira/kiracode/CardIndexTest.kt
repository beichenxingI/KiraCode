package com.kirakira.kiracode

import com.kirakira.kiracode.domain.agent.CardIndex
import com.kirakira.kiracode.domain.agent.CardScale
import com.kirakira.kiracode.domain.agent.CardOperation
import com.kirakira.kiracode.domain.agent.CardOperationApplier
import com.kirakira.kiracode.domain.agent.tools.CardFunctions
import com.kirakira.kiracode.domain.model.CharacterCard
import com.kirakira.kiracode.domain.model.st.StBookEntry
import com.kirakira.kiracode.domain.model.st.StCharacterBook
import com.kirakira.kiracode.domain.model.st.StRegexScript
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 巨型角色卡按需读取验收测试（阶段 2）：
 * 1. 稳定条目 ID 修复（uid=0/重复 → 唯一；健康卡不动）；
 * 2. token 分级（SMALL/MEDIUM/LARGE）；
 * 3. 索引构建（不携带全文）；
 * 4. query/get 语义（关键词命中、未找到明确报错、limit、内容截断）；
 * 5. 按需注入段结构（含查询工具说明；不含完整正文）；
 * 6. 工具注册（17/15）。
 */
class CardIndexTest {

    private fun cardWithEntries(vararg entries: StBookEntry, regexes: List<StRegexScript> = emptyList()) =
        CharacterCard(
            name = "汐音",
            characterBook = StCharacterBook(entries = entries.toList()),
            regexScripts = regexes
        )

    // ──────────────── 1. 稳定 ID ────────────────

    @Test
    fun `ensureStableEntryIds assigns unique ids to zero and duplicate uids`() {
        val base = cardWithEntries(
            StBookEntry(id = 0, comment = "A", content = "内容A"),
            StBookEntry(id = 0, comment = "B", content = "内容B"),
            StBookEntry(id = 5, comment = "C", content = "内容C"),
            StBookEntry(id = 5, comment = "D", content = "内容D")
        )
        val repaired = CardIndex.ensureStableEntryIds(base)
        val ids = repaired.characterBook!!.entries.map { it.id }
        assertEquals(4, ids.size)
        assertEquals("ID 全部唯一", ids.size, ids.toSet().size)
        assertTrue("已有合法 ID 保留", 5L in ids)

        // 再修复一次应无变化（幂等）
        assertEquals(repaired, CardIndex.ensureStableEntryIds(repaired))
    }

    @Test
    fun `ensureStableEntryIds repairs blank or duplicate regex ids and keeps healthy card untouched`() {
        val healthy = cardWithEntries(
            StBookEntry(id = 1, comment = "A", content = "内容A"),
            regexes = listOf(StRegexScript(id = "abc", scriptName = "r1", findRegex = "x", replaceString = "y"))
        )
        assertEquals(healthy, CardIndex.ensureStableEntryIds(healthy))

        val broken = cardWithEntries(
            StBookEntry(id = 1, comment = "A", content = "内容A"),
            regexes = listOf(
                StRegexScript(id = "", scriptName = "r1", findRegex = "x", replaceString = "y"),
                StRegexScript(id = "dup", scriptName = "r2", findRegex = "x", replaceString = "y"),
                StRegexScript(id = "dup", scriptName = "r3", findRegex = "x", replaceString = "y")
            )
        )
        val repaired = CardIndex.ensureStableEntryIds(broken)
        val regexIds = repaired.regexScripts.map { it.id }
        assertTrue("全部非空", regexIds.all { it.isNotBlank() })
        assertEquals("全部唯一", regexIds.size, regexIds.toSet().size)
        // 正文未被改动
        assertEquals("内容A", repaired.characterBook!!.entries.single().content)
    }

    // ──────────────── 2. 分级 ────────────────

    @Test
    fun `classify returns small for normal card and large for giant card`() {
        val small = CharacterCard(name = "小卡", description = "很短的描述")
        assertEquals(CardScale.SMALL, CardIndex.classify(small))

        // 约 6 万汉字 ≈ 9 万 token > 8000（LARGE）
        val giantText = "海".repeat(60_000)
        val giant = CharacterCard(
            name = "巨卡",
            description = giantText.take(20_000),
            personality = giantText.take(20_000),
            scenario = giantText.take(20_000)
        )
        assertEquals(CardScale.LARGE, CardIndex.classify(giant))

        // 阈值由 TokenEstimator 统一计算：估算值确实落在对应档位区间
        assertTrue(CardIndex.estimateCardTokens(small) < CardIndex.SMALL_THRESHOLD_TOKENS)
        assertTrue(CardIndex.estimateCardTokens(giant) >= CardIndex.LARGE_THRESHOLD_TOKENS)
    }

    // ──────────────── 3. 索引 ────────────────

    @Test
    fun `indexes carry stable ids and summaries but not full content`() {
        val longContent = "内容正文".repeat(500) // 2000 字
        val card = cardWithEntries(
            StBookEntry(id = 12, comment = "樱华高中", keys = listOf("校园", "高中"), content = longContent, enabled = false),
            regexes = listOf(StRegexScript(id = "aaaaaaaa-1111", scriptName = "去思考标签", findRegex = "<think>[\\s\\S]*?", replaceString = ""))
        )

        val wb = CardIndex.buildWorldBookIndex(card)
        assertEquals(1, wb.size)
        val e = wb[0]
        assertEquals(12L, e.id)
        assertEquals("樱华高中", e.title)
        assertEquals(listOf("校园", "高中"), e.keywords)
        assertEquals(2000, e.contentLength)
        assertEquals(80, e.summary.length) // 摘要截断，不是全文
        assertFalse(e.enabled)

        val rx = CardIndex.buildRegexIndex(card)
        assertEquals(1, rx.size)
        assertEquals("aaaaaaaa-1111", rx[0].id)
        assertEquals("去思考标签", rx[0].name)
        assertTrue(rx[0].patternSummary.length < 100)
    }

    // ──────────────── 4. 查询/读取 ────────────────

    @Test
    fun `query matches keywords and reports stable ids`() {
        val card = cardWithEntries(
            StBookEntry(id = 1, comment = "樱华高中", keys = listOf("校园"), content = "校舍与蝉鸣"),
            StBookEntry(id = 2, comment = "夜市祭典", keys = listOf("祭典"), content = "灯笼与街道"),
            StBookEntry(id = 3, comment = "银针医术", keys = listOf("银针"), content = "三十六根银针")
        )
        val hit = CardIndex.queryEntries(card, CardIndex.QuerySpec("worldbook", listOf("校园", "祭典"), 10))
        assertTrue(hit.contains("#1"))
        assertTrue(hit.contains("#2"))
        assertFalse(hit.contains("#3"))
        // 关键词为空 = 列出索引
        val all = CardIndex.queryEntries(card, CardIndex.QuerySpec("worldbook", emptyList(), 10))
        assertTrue(all.contains("#1") && all.contains("#2") && all.contains("#3"))
        // limit 生效
        val limited = CardIndex.queryEntries(card, CardIndex.QuerySpec("worldbook", emptyList(), 2))
        assertFalse(limited.contains("#3"))
    }

    @Test
    fun `get entry returns full content and explicit not-found error`() {
        val longContent = (1..500).joinToString("") { "字$it，" } // 约 3500 字符 < 截断阈值，尾段应完整保留
        val card = cardWithEntries(
            StBookEntry(id = 12, comment = "樱华高中", keys = listOf("校园"), content = longContent),
            regexes = listOf(StRegexScript(id = "aaaaaaaa-1111-2222-3333", scriptName = "去思考", findRegex = "a", replaceString = "b"))
        )
        // 命中（带 # 前缀也可）
        val ok = CardIndex.getEntry(card, "worldbook", "#12").getOrThrow()
        assertTrue(ok.contains("樱华高中"))
        assertTrue(ok.contains(longContent.takeLast(50)))
        // 未找到 → 明确错误
        val miss = CardIndex.getEntry(card, "worldbook", "99")
        assertTrue(miss.isFailure)
        assertTrue(miss.exceptionOrNull()!!.message!!.contains("未找到"))
        // 非法 kind → 明确错误
        assertTrue(CardIndex.getEntry(card, "book", "12").isFailure)
        // 非数字 worldbook ID → 明确错误
        assertTrue(CardIndex.getEntry(card, "worldbook", "abc").isFailure)
        // regex 短 id 唯一命中
        val rx = CardIndex.getEntry(card, "regex", "aaaaaaaa").getOrThrow()
        assertTrue(rx.contains("去思考"))
        // regex 全 id 命中
        assertTrue(CardIndex.getEntry(card, "regex", "aaaaaaaa-1111-2222-3333").isSuccess)
    }

    @Test
    fun `get entry truncates oversized content`() {
        val huge = "海".repeat(CardIndex.ENTRY_CONTENT_MAX + 5000)
        val card = cardWithEntries(StBookEntry(id = 1, comment = "巨条目", content = huge))
        val text = CardIndex.getEntry(card, "worldbook", "1").getOrThrow()
        assertTrue(text.contains("已截断"))
        assertTrue(text.length < huge.length)
    }

    // ──────────────── 5. 按需注入段 ────────────────

    @Test
    fun `on-demand section has indexes and tool guidance but no full entry content`() {
        val longContent = (1..3000).joinToString("") { "句$it。" } // 全文约 2 万字
        val card = cardWithEntries(
            StBookEntry(id = 7, comment = "樱华高中", keys = listOf("校园"), content = longContent)
        )
        val section = CardIndex.buildOnDemandSection(card)
        assertTrue(section.contains("query_card_entries"))
        assertTrue(section.contains("get_card_entry"))
        assertTrue(section.contains("#7"))
        assertTrue(section.contains("樱华高中"))
        // 全文不注入：尾段不得出现
        assertFalse(section.contains(longContent.takeLast(100)))
    }

    // ──────────────── 6. 工具注册 ────────────────

    @Test
    fun `query and get tools are always registered`() {
        val namesOn = CardFunctions.getAllFunctions(webSearchEnabled = true)
            .map { it["function"]!!.jsonObject["name"]!!.jsonPrimitive.content }
        val namesOff = CardFunctions.getAllFunctions(webSearchEnabled = false)
            .map { it["function"]!!.jsonObject["name"]!!.jsonPrimitive.content }
        assertTrue("query_card_entries" in namesOn && "query_card_entries" in namesOff)
        assertTrue("get_card_entry" in namesOn && "get_card_entry" in namesOff)
        assertEquals(18, namesOn.size)
        assertEquals(16, namesOff.size)
        // get_card_entry 的 kind/entry_id 必填
        val getTool = CardFunctions.getAllFunctions()
            .first { it["function"]!!.jsonObject["name"]!!.jsonPrimitive.content == "get_card_entry" }
        val required = getTool["function"]!!.jsonObject["parameters"]!!.jsonObject["required"].toString()
        assertTrue(required.contains("kind") && required.contains("entry_id"))
    }

    // ──────────────── 7. 稳定 ID 与既有修改工具协作 ────────────────

    @Test
    fun `repaired ids work with existing book_update tool`() {
        val broken = cardWithEntries(
            StBookEntry(id = 0, comment = "A", content = "旧内容"),
            StBookEntry(id = 0, comment = "B", content = "旧内容B")
        )
        val repaired = CardIndex.ensureStableEntryIds(broken)
        val targetId = repaired.characterBook!!.entries.first { it.comment == "A" }.id
        val updated = CardOperationApplier.apply(
            repaired,
            listOf(CardOperation.BookUpdate(uid = targetId, content = "新内容"))
        )
        val updatedEntry = updated.characterBook!!.entries.first { it.comment == "A" }
        assertEquals("新内容", updatedEntry.content)
        // 另一条不受影响
        assertNotEquals("新内容", updated.characterBook!!.entries.first { it.comment == "B" }.content)
    }
}
