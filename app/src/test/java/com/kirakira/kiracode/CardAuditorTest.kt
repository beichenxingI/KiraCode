package com.kirakira.kiracode

import com.kirakira.kiracode.domain.agent.CardAuditor
import com.kirakira.kiracode.domain.model.AuditIssueCodes
import com.kirakira.kiracode.domain.model.AuditScope
import com.kirakira.kiracode.domain.model.AuditSeverity
import com.kirakira.kiracode.domain.model.CharacterCard
import com.kirakira.kiracode.domain.model.st.StBookEntry
import com.kirakira.kiracode.domain.model.st.StCharacterBook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CardAuditorTest {

    private fun cardWithEntries(vararg entries: StBookEntry) = CharacterCard(
        name = "汐音",
        description = "港口城市的年轻领航员。",
        firstMessage = "她把海图摊在桌上，抬眼看你。",
        characterBook = StCharacterBook(entries = entries.toList())
    )

    private fun issueCodes(card: CharacterCard, scope: AuditScope = AuditScope.ALL) =
        CardAuditor.audit(card, scope).issues.map { it.issueCode }

    @Test
    fun `audit reports missing description and greeting`() {
        val report = CardAuditor.audit(CharacterCard(name = "空卡"))

        assertTrue(report.issues.any { it.issueCode == AuditIssueCodes.CARD_NO_DESC })
        assertTrue(report.issues.any { it.issueCode == AuditIssueCodes.CARD_NO_GREETING })
        assertEquals(AuditSeverity.ERROR, report.issues.first { it.issueCode == AuditIssueCodes.CARD_NO_DESC }.severity)
    }

    @Test
    fun `audit reports empty content and empty keys`() {
        val card = cardWithEntries(
            StBookEntry(id = 1L, comment = "空内容", content = ""),
            StBookEntry(id = 2L, comment = "空触发", content = "港口每天有 12 艘船进出，关税 3 成。", keys = emptyList())
        )
        val codes = issueCodes(card)

        assertTrue(codes.contains(AuditIssueCodes.EMPTY_CONTENT))
        assertTrue(codes.contains(AuditIssueCodes.EMPTY_KEYS))
    }

    @Test
    fun `audit reports cliche opening and ending`() {
        val card = cardWithEntries(
            StBookEntry(
                id = 1L,
                comment = "古城",
                keys = listOf("古城"),
                content = "在奇幻大陆的世界里，有一座古老城市。人口 12000，城墙高 30 米，驻军 800 人。总之，这是一个充满机遇的地方。"
            )
        )
        val codes = issueCodes(card)

        assertTrue(codes.contains(AuditIssueCodes.CLICHE_OPENING))
        assertTrue(codes.contains(AuditIssueCodes.CLICHE_ENDING))
    }

    @Test
    fun `audit reports duplicate entries`() {
        val text = "港口由三座灯塔守护。夜间每 20 分钟交替闪光一次，领航员据此校正航向。北塔负责外海，南塔负责内湾，东塔只在浓雾时点亮。潮汐表贴在灯塔门口，每天更新两次。"
        val card = cardWithEntries(
            StBookEntry(id = 1L, comment = "港口", keys = listOf("港口"), content = text),
            StBookEntry(id = 2L, comment = "港口副本", keys = listOf("码头"), content = text)
        )
        val duplicate = CardAuditor.audit(card).issues
            .firstOrNull { it.issueCode == AuditIssueCodes.DUPLICATE }

        assertTrue(duplicate != null)
        assertEquals(1L, duplicate!!.entryUid ?: -1L)
        assertEquals(2L, duplicate.pairUid ?: -1L)
    }

    @Test
    fun `fields scope skips worldbook issues`() {
        val card = cardWithEntries(
            StBookEntry(id = 1L, comment = "空触发", content = "有内容但没有触发词。", keys = emptyList())
        )
        val codes = issueCodes(card, AuditScope.FIELDS)

        assertFalse(codes.contains(AuditIssueCodes.EMPTY_KEYS))
    }

    @Test
    fun `worldbook scope skips card field issues`() {
        val card = CharacterCard(
            name = "空卡",
            characterBook = StCharacterBook(
                entries = listOf(
                    StBookEntry(id = 1L, comment = "空触发", content = "有内容但没有触发词。", keys = emptyList())
                )
            )
        )
        val codes = issueCodes(card, AuditScope.WORLDBOOK)

        assertFalse(codes.contains(AuditIssueCodes.CARD_NO_DESC))
        assertFalse(codes.contains(AuditIssueCodes.CARD_NO_GREETING))
        assertTrue(codes.contains(AuditIssueCodes.EMPTY_KEYS))
    }

    @Test
    fun `empty worldbook reports info`() {
        val report = CardAuditor.audit(CharacterCard(name = "无世界书", description = "描述", firstMessage = "开场"))

        assertTrue(report.issues.any {
            it.issueCode == AuditIssueCodes.NO_WORLDBOOK && it.severity == AuditSeverity.INFO
        })
        assertEquals(0, report.entryCount)
    }
}
