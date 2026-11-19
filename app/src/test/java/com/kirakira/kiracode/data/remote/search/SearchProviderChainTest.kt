package com.kirakira.kiracode.data.remote.search

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 搜索链单元测试（fake provider，不发真实网络请求）：
 * 1. Bing 成功 → 不调用 SearXNG；
 * 2. Bing 超时 → SearXNG 成功；
 * 3. 两者都失败 → 结构化失败（用户/模型双消息）；
 * 4. Bing 空结果 → SearXNG 成功；
 * 5. attempts 完整记录；
 * 6. 结果经 mergeAndRank 去重；
 * 7. 单次遍历：每个 provider 只调用一次，不无限重试。
 */
class SearchProviderChainTest {

    /** 可编程 fake：按脚本依次返回预设结果 */
    private class FakeProvider(
        override val id: String,
        private val script: MutableList<SearchProviderResult>
    ) : SearchProvider {
        var callCount = 0
            private set

        override suspend fun search(query: String, maxResults: Int): SearchProviderResult {
            callCount++
            return if (script.isNotEmpty()) script.removeAt(0) else SearchProviderResult.Failure(
                id, SearchFailureKind.UNKNOWN, "no script", retryable = false
            )
        }
    }

    private fun successHit(url: String, snippet: String = "s") = SearchHit(
        title = "t-$url", url = url, snippet = snippet, provider = "FAKE"
    )

    @Test
    fun `primary success skips fallbacks`() = runBlocking {
        val bing = FakeProvider("BING", mutableListOf(
            SearchProviderResult.Success("BING", listOf(successHit("https://a.example.com")))
        ))
        val searx = FakeProvider("SEARXNG", mutableListOf())
        val chain = SearchProviderChain(bing, listOf(searx))

        val result = chain.search("query", 5)

        assertTrue(result is SearchChainResult.SearchChainSuccess)
        assertEquals(0, searx.callCount)
        assertEquals("BING", (result as SearchChainResult.SearchChainSuccess).provider)
    }

    @Test
    fun `primary timeout falls back to searxng success`() = runBlocking {
        val bing = FakeProvider("BING", mutableListOf(
            SearchProviderResult.Failure("BING", SearchFailureKind.CONNECT_TIMEOUT, "connect timeout", retryable = true)
        ))
        val searx = FakeProvider("SEARXNG", mutableListOf(
            SearchProviderResult.Success("SEARXNG", listOf(successHit("https://b.example.com")))
        ))
        val chain = SearchProviderChain(bing, listOf(searx))

        val result = chain.search("query", 5)

        assertTrue(result is SearchChainResult.SearchChainSuccess)
        assertEquals("SEARXNG", (result as SearchChainResult.SearchChainSuccess).provider)
        assertEquals(1, bing.callCount)
        assertEquals(1, searx.callCount)
    }

    @Test
    fun `both providers failing yields structured failure with dual messages`() = runBlocking {
        val bing = FakeProvider("BING", mutableListOf(
            SearchProviderResult.Failure("BING", SearchFailureKind.CONNECT_TIMEOUT, "connect timeout", retryable = true)
        ))
        val searx = FakeProvider("SEARXNG", mutableListOf(
            SearchProviderResult.Failure("SEARXNG", SearchFailureKind.HTTP_ERROR, "HTTP 503", retryable = true)
        ))
        val chain = SearchProviderChain(bing, listOf(searx))

        val result = chain.search("query", 5)

        assertTrue(result is SearchChainResult.SearchChainFailure)
        val failure = result as SearchChainResult.SearchChainFailure
        // 用户可见：简短、含尝试轨迹
        assertTrue(failure.userMessage.contains("联网搜索失败"))
        assertTrue(failure.userMessage.contains("Bing"))
        assertTrue(failure.userMessage.contains("备用搜索源"))
        // 模型可见：明确禁止编造 + 行为指引
        assertTrue(failure.modelMessage.contains("搜索失败"))
        assertTrue(failure.modelMessage.contains("不要根据搜索失败自行编造答案"))
        assertTrue(failure.modelMessage.contains("Bing"))
        // 不暴露 stack trace / 内部异常消息正文
        assertTrue(!failure.modelMessage.contains("Exception"))
        assertTrue(!failure.userMessage.contains("Exception"))
    }

    @Test
    fun `primary empty result falls back to searxng`() = runBlocking {
        val bing = FakeProvider("BING", mutableListOf(
            SearchProviderResult.Failure("BING", SearchFailureKind.EMPTY_RESULT, "empty", retryable = true)
        ))
        val searx = FakeProvider("SEARXNG", mutableListOf(
            SearchProviderResult.Success("SEARXNG", listOf(successHit("https://c.example.com")))
        ))
        val chain = SearchProviderChain(bing, listOf(searx))

        val result = chain.search("query", 5)

        assertTrue(result is SearchChainResult.SearchChainSuccess)
        assertEquals(1, searx.callCount)
    }

    @Test
    fun `attempts are fully recorded`() = runBlocking {
        val bing = FakeProvider("BING", mutableListOf(
            SearchProviderResult.Failure("BING", SearchFailureKind.DNS_ERROR, "dns", retryable = true)
        ))
        val searx = FakeProvider("SEARXNG", mutableListOf(
            SearchProviderResult.Success("SEARXNG", listOf(successHit("https://d.example.com")))
        ))
        val chain = SearchProviderChain(bing, listOf(searx))

        val result = chain.search("query", 5) as SearchChainResult.SearchChainSuccess

        assertEquals(2, result.attempts.size)
        assertEquals("BING", result.attempts[0].provider)
        assertEquals(false, result.attempts[0].success)
        assertEquals(SearchFailureKind.DNS_ERROR, result.attempts[0].failureKind)
        assertEquals("SEARXNG", result.attempts[1].provider)
        assertEquals(true, result.attempts[1].success)
        assertTrue(result.attempts.all { it.durationMs >= 0 })
    }

    @Test
    fun `results are deduplicated by normalized url with longer snippet kept`() = runBlocking {
        val bing = FakeProvider("BING", mutableListOf(
            SearchProviderResult.Success("BING", listOf(
                successHit("https://dup.example.com/page", "短"),
                successHit("https://dup.example.com/page?utm_source=x", "这条摘要更长所以保留")
            ))
        ))
        val searx = FakeProvider("SEARXNG", mutableListOf())
        val chain = SearchProviderChain(bing, listOf(searx))

        val result = chain.search("query", 10) as SearchChainResult.SearchChainSuccess

        assertEquals("规范化 URL 去重后应保留 1 条", 1, result.results.size)
        assertTrue(result.results[0].snippet.contains("更长"))
    }

    @Test
    fun `single pass over providers - no infinite retry`() = runBlocking {
        val bing = FakeProvider("BING", mutableListOf(
            SearchProviderResult.Failure("BING", SearchFailureKind.CONNECT_TIMEOUT, "t", retryable = true)
        ))
        val searx = FakeProvider("SEARXNG", mutableListOf(
            SearchProviderResult.Failure("SEARXNG", SearchFailureKind.CONNECT_TIMEOUT, "t", retryable = true)
        ))
        val chain = SearchProviderChain(bing, listOf(searx))

        repeat(5) { chain.search("same-query", 5) }

        // 链单次遍历：每次 search 每个 provider 恰好调用一次（重试决策交给模型/用户）
        assertEquals(5, bing.callCount)
        assertEquals(5, searx.callCount)
    }
}
