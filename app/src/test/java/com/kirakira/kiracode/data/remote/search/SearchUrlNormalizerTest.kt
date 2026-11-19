package com.kirakira.kiracode.data.remote.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 搜索结果规范化与去重测试（W5/W8）：
 * - Bing 跳转还原
 * - 追踪参数剥离
 * - 尾斜杠统一
 * - 同规范化 URL 去重（保留摘要更长的一条），标题相似的不同页面不误删
 * - maxResults 截断
 */
class SearchUrlNormalizerTest {

    @Test
    fun `restores bing redirect with base64url`() {
        val target = "https://example.com/article?id=42"
        val b64 = java.util.Base64.getUrlEncoder().withoutPadding()
            .encodeToString(target.toByteArray(Charsets.UTF_8))
        assertEquals(
            target,
            SearchUrlNormalizer.restoreBingRedirect("https://cn.bing.com/ck/a?!&p=1&u=a1$b64&ntb=1")
        )
    }

    @Test
    fun `keeps plain url untouched`() {
        assertEquals(
            "https://example.com/page",
            SearchUrlNormalizer.restoreBingRedirect("https://example.com/page")
        )
    }

    @Test
    fun `strips tracking params`() {
        assertEquals(
            "https://example.com/post?id=7",
            SearchUrlNormalizer.normalizeUrl("https://example.com/post?utm_source=weibo&utm_medium=social&id=7&fbclid=abc")
        )
    }

    @Test
    fun `normalizes trailing slash and default port`() {
        assertEquals(
            "https://example.com/path",
            SearchUrlNormalizer.normalizeUrl("https://example.com/path/")
        )
        assertEquals(
            "https://example.com",
            SearchUrlNormalizer.normalizeUrl("https://example.com/")
        )
        assertEquals(
            "https://example.com/x",
            SearchUrlNormalizer.normalizeUrl("https://example.com:443/x/")
        )
    }

    @Test
    fun `merge keeps longer snippet for same normalized url`() {
        val merged = SearchUrlNormalizer.mergeAndRank(
            listOf(
                SearchHit("t1", "https://dup.example.com/a?utm_source=x", "短", "BING"),
                SearchHit("t2", "https://dup.example.com/a", "这是一条更长的摘要，应当被保留", "BING")
            ),
            10
        )
        assertEquals(1, merged.size)
        assertTrue(merged[0].snippet.contains("更长"))
    }

    @Test
    fun `similar titles but different pages are both kept`() {
        val merged = SearchUrlNormalizer.mergeAndRank(
            listOf(
                SearchHit("REA 项目主页", "https://rea.dev", "a", "BING"),
                SearchHit("REA 项目文档", "https://rea.dev/docs", "b", "BING")
            ),
            10
        )
        assertEquals("不同页面不得因标题相似被误删", 2, merged.size)
    }

    @Test
    fun `input order preserved and truncated to maxResults`() {
        val merged = SearchUrlNormalizer.mergeAndRank(
            (1..8).map { SearchHit("t$it", "https://e$it.example.com", "s$it", "BING") },
            5
        )
        assertEquals(5, merged.size)
        assertEquals("https://e1.example.com", merged[0].url)
        assertEquals("https://e5.example.com", merged[4].url)
    }
}
