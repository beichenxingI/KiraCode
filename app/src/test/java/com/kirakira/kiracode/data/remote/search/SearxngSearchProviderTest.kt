package com.kirakira.kiracode.data.remote.search

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SearXNG 备用源单元测试（fixture JSON 驱动，不发真实网络请求）：
 * - 多结果映射（title/url/content → SearchHit）
 * - 空结果 / results 缺失 / 缺字段条目丢弃
 * - 非法 JSON → 抛出（由调用方转 PARSE_ERROR）
 * - 解析失败不产出假成功
 */
class SearxngSearchProviderTest {

    private val provider = SearxngSearchProvider(HttpClient(CIO))

    @Test
    fun `maps multiple results to unified hits`() {
        val json = """
            {"query":"REA","results":[
              {"title":"REA 项目","url":"https://github.com/rea/rea","content":"开源项目主页"},
              {"title":"REA 文档","url":"https://rea.dev/docs","content":"使用文档"}
            ]}
        """.trimIndent()
        val hits = provider.mapResults(provider.parseJson(json))
        assertEquals(2, hits.size)
        assertEquals("REA 项目", hits[0].title)
        assertEquals("https://github.com/rea/rea", hits[0].url)
        assertEquals("开源项目主页", hits[0].snippet)
        assertEquals("SEARXNG", hits[0].provider)
    }

    @Test
    fun `empty results list yields no hits`() {
        val json = """{"query":"x","results":[]}"""
        val hits = provider.mapResults(provider.parseJson(json))
        assertTrue(hits.isEmpty())
    }

    @Test
    fun `missing results field yields no hits`() {
        val json = """{"query":"x"}"""
        val hits = provider.mapResults(provider.parseJson(json))
        assertTrue(hits.isEmpty())
    }

    @Test
    fun `entries with missing title or non-http url are dropped`() {
        val json = """
            {"results":[
              {"url":"https://good.example.com","content":"有标题缺失 url 正常"},
              {"title":"无 url"},
              {"title":"坏 url","url":"javascript:alert(1)","content":"x"},
              {"title":"好条目","url":"https://ok.example.com","content":"ok"}
            ]}
        """.trimIndent()
        val hits = provider.mapResults(provider.parseJson(json))
        assertEquals(1, hits.size)
        assertEquals("好条目", hits[0].title)
    }

    @Test
    fun `invalid json throws for caller to map to PARSE_ERROR`() {
        val threw = try {
            provider.parseJson("this is not json {{{")
            false
        } catch (e: Exception) {
            true
        }
        assertTrue("非法 JSON 必须抛出（转 PARSE_ERROR），不得伪装成功", threw)
    }

    @Test
    fun `unknown fields are tolerated`() {
        // SearXNG 实际响应含 engines/score 等额外字段，ignoreUnknownKeys 必须生效
        val json = """
            {"query":"x","number_of_results":2,"results":[
              {"title":"t","url":"https://u.example.com","content":"c","engines":["bing"],"score":1.5}
            ]}
        """.trimIndent()
        val hits = provider.mapResults(provider.parseJson(json))
        assertEquals(1, hits.size)
    }
}
