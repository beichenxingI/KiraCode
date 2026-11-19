package com.kirakira.kiracode

import com.kirakira.kiracode.domain.model.BrowsingDecision
import com.kirakira.kiracode.domain.model.ContentType
import com.kirakira.kiracode.domain.model.TrainPersonaConfig
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 灵魂训练 · 多轮浏览决策的数据契约测试。
 */
class TrainPersonaMultiRoundTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Test
    fun `decision decodes range fields`() {
        val raw =
            """{"selections":[{"cardIndex":0,"contentType":"WORLD_BOOK","rangeStart":0,"rangeEnd":7}],"reasoning":"x"}"""
        val decision = json.decodeFromString<BrowsingDecision>(raw)
        assertEquals(1, decision.selections.size)
        assertEquals(ContentType.WORLD_BOOK, decision.selections[0].contentType)
        assertEquals(0, decision.selections[0].rangeStart)
        assertEquals(7, decision.selections[0].rangeEnd)
    }

    @Test
    fun `decision tolerates legacy range string and missing ranges`() {
        val raw =
            """{"selections":[{"cardIndex":1,"contentType":"DESCRIPTION","range":"0-7"},{"cardIndex":0,"contentType":"TAGS"}]}"""
        val decision = json.decodeFromString<BrowsingDecision>(raw)
        assertEquals(2, decision.selections.size)
        assertNull(decision.selections[0].rangeStart)
        assertNull(decision.selections[0].rangeEnd)
        assertNull(decision.selections[1].rangeStart)
    }

    @Test
    fun `decision defaults selections when omitted`() {
        val decision = json.decodeFromString<BrowsingDecision>("""{"reasoning":"done"}""")
        assertTrue(decision.selections.isEmpty())
    }

    @Test
    fun `context budget is independent from output max tokens`() {
        val config = TrainPersonaConfig()
        assertEquals(1_000_000, config.contextBudget)
        assertEquals(8192, config.maxTokens)
    }
}