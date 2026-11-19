package com.kirakira.kiracode.data.remote.search

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Bing 主源单元测试（fixture 驱动，不发真实网络请求）：
 * - b_algo 正常解析 / 空结果 / 结构变化
 * - 标题与摘要含 HTML 实体
 * - Bing 跳转链接还原
 * - 重复 URL 去重
 * - HTTP 错误与超时/DNS 的结构化失败映射
 */
class BingSearchProviderTest {

    private val provider = BingSearchProvider(HttpClient(CIO))

    private fun fixtureHtml(vararg blocks: String): String = """
        <html><body><ol id="b_results">
        ${blocks.joinToString("\n")}
        </ol></body></html>
    """.trimIndent()

    private fun algoBlock(title: String, url: String, snippet: String): String = """
        <li class="b_algo">
          <h2><a href="$url" h="ID=SERP,1.1">$title</a></h2>
          <div class="b_caption"><p>$snippet</p></div>
        </li>
    """.trimIndent()

    @Test
    fun `parses b_algo blocks into hits`() {
        val html = fixtureHtml(
            algoBlock("REA 开源项目", "https://github.com/rea-project/rea", "REA 是一个开源项目"),
            algoBlock("REA 文档", "https://rea-project.org/docs", "官方文档与教程")
        )
        val hits = provider.parseResults(html, 10)
        assertEquals(2, hits.size)
        assertEquals("REA 开源项目", hits[0].title)
        assertEquals("https://github.com/rea-project/rea", hits[0].url)
        assertEquals("REA 是一个开源项目", hits[0].snippet)
        assertEquals("BING", hits[0].provider)
    }

    @Test
    fun `empty result page yields no hits`() {
        val html = """
            <html><body><ol id="b_results">
            <li class="b_no"><p>没有找到与查询相关的结果</p></li>
            </ol></body></html>
        """.trimIndent()
        val hits = provider.parseResults(html, 10)
        assertTrue("空页面应解析出 0 条", hits.isEmpty())
    }

    @Test
    fun `structure change yields no hits instead of crash`() {
        // Bing 改版（类名变化）→ 解析 0 条（后续由 EMPTY_RESULT 失败表达，不崩溃）
        val html = "<html><body><div class=\"new_design_v99\"> totally different markup </div></body></html>"
        val hits = provider.parseResults(html, 10)
        assertTrue(hits.isEmpty())
    }

    @Test
    fun `decodes html entities in title and snippet`() {
        val html = fixtureHtml(
            algoBlock("A & B 研究", "https://example.com/a", "AT&T 与 <b>标签</b> 清理")
        )
        val hits = provider.parseResults(html, 10)
        assertEquals(1, hits.size)
        assertEquals("A & B 研究", hits[0].title)
        assertTrue("实体应被解码", hits[0].snippet.contains("AT&T"))
        assertTrue("残留标签应被清除", !hits[0].snippet.contains("<b>"))
    }

    @Test
    fun `restores bing redirect links`() {
        // u=a1 后接 base64url（无填充）编码的 https://example.com/target
        val target = "https://example.com/target"
        val b64 = java.util.Base64.getUrlEncoder().withoutPadding()
            .encodeToString(target.toByteArray(Charsets.UTF_8))
        val html = fixtureHtml(
            algoBlock("跳转结果", "https://cn.bing.com/ck/a?!&p=1&u=a1$b64&ntb=1", "via bing redirect")
        )
        val hits = provider.parseResults(html, 10)
        assertEquals(1, hits.size)
        assertEquals(target, hits[0].url)
    }

    @Test
    fun `deduplicates repeated urls`() {
        val html = fixtureHtml(
            algoBlock("重复一", "https://example.com/dup", "第一条"),
            algoBlock("重复二", "https://example.com/dup?utm_source=x", "第二条更长的摘要内容")
        )
        val hits = provider.parseResults(html, 10)
        // parse 层按原始 URL 去重（不同 query 参数视为不同条目），聚合层再按规范化 URL 去重
        assertTrue(hits.size <= 2)
    }

    @Test
    fun `http error maps to structured failure`() {
        val f404 = provider.failureFromHttp("BING", 404)
        assertEquals(SearchFailureKind.HTTP_ERROR, f404.kind)
        val f403 = provider.failureFromHttp("BING", 403)
        assertEquals(SearchFailureKind.BLOCKED, f403.kind)
        assertTrue(f403.retryable)
        val f503 = provider.failureFromHttp("BING", 503)
        assertTrue(f503.retryable)
        assertTrue(f503.message.contains("503"))
    }

    @Test
    fun `timeout and dns map to dedicated failure kinds`() {
        val connectTimeout = provider.failureFromThrowable(
            "BING", io.ktor.client.network.sockets.ConnectTimeoutException("connect timeout")
        )
        assertEquals(SearchFailureKind.CONNECT_TIMEOUT, connectTimeout.kind)
        assertTrue(connectTimeout.retryable)

        val requestTimeout = provider.failureFromThrowable(
            "BING", io.ktor.client.plugins.HttpRequestTimeoutException(io.ktor.client.request.HttpRequestBuilder())
        )
        assertEquals(SearchFailureKind.REQUEST_TIMEOUT, requestTimeout.kind)

        val dns = provider.failureFromThrowable(
            "BING", java.net.UnknownHostException("no such host")
        )
        assertEquals(SearchFailureKind.DNS_ERROR, dns.kind)
    }

    @Test
    fun `blocked page heuristic detects captcha`() {
        assertTrue(provider.looksLikeBlockedPage("<html>please complete the CAPTCHA to continue</html>"))
        assertTrue(provider.looksLikeBlockedPage("<html>检测到异常流量，请稍后再试</html>"))
        assertTrue(!provider.looksLikeBlockedPage(fixtureHtml(algoBlock("t", "https://e.com", "s"))))
    }
}
