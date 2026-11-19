package com.kirakira.kiracode.data.remote.search

import com.kirakira.kiracode.util.AppLogger
import io.ktor.client.*
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bing 主搜索源（HTML 端点，免 API Key）。
 *
 * - GET https://cn.bing.com/search?q=...，bodyAsText() 读取 HTML（不走 JSON）
 * - 使用 Hilt 共享 web HTTP 客户端（默认超时 20s/10s，见 SearchNetworkModule）
 * - 解析 li.b_algo 结果块：标题链接 + 摘要
 * - 失败一律返回结构化 Failure（CONNECT_TIMEOUT/REQUEST_TIMEOUT/HTTP_ERROR/
 *   PARSE_ERROR/EMPTY_RESULT/DNS_ERROR/BLOCKED），不抛业务异常
 * - 日志只记录 queryLength/status/resultCount/failureKind，不记录结果正文
 */
@Singleton
class BingSearchProvider @Inject constructor(
    private val client: HttpClient
) : SearchProvider {

    override val id: String = "BING"

    override suspend fun search(query: String, maxResults: Int): SearchProviderResult =
        withContext(Dispatchers.IO) {
            val capped = maxResults.coerceIn(1, 10)
            val started = System.currentTimeMillis()
            var status: Int? = null
            try {
                val response = client.get("https://cn.bing.com/search") {
                    parameter("q", query)
                    parameter("setlang", "zh-CN")
                }
                status = response.status.value
                if (!response.status.isSuccess()) {
                    return@withContext logAndFail(
                        failure = failureFromHttp(id, response.status.value),
                        queryLength = query.length,
                        started = started
                    )
                }
                val html = response.bodyAsText()
                val hits = parseResults(html, capped)
                when {
                    hits.isEmpty() && looksLikeBlockedPage(html) ->
                        return@withContext logAndFail(
                            failure = SearchProviderResult.Failure(
                                id, SearchFailureKind.BLOCKED, "Bing 返回拦截/验证码页", retryable = true
                            ),
                            queryLength = query.length, started = started
                        )
                    hits.isEmpty() ->
                        return@withContext logAndFail(
                            failure = SearchProviderResult.Failure(
                                id, SearchFailureKind.EMPTY_RESULT, "Bing 未返回结果", retryable = true
                            ),
                            queryLength = query.length, started = started
                        )
                    else -> {
                        AppLogger.d(
                            TAG,
                            "provider=BING queryLength=${query.length} status=$status resultCount=${hits.size}"
                        )
                        SearchProviderResult.Success(id, hits)
                    }
                }
            } catch (e: Exception) {
                return@withContext logAndFail(
                    failure = failureFromThrowable(id, e),
                    queryLength = query.length,
                    started = started,
                    error = e
                )
            }
        }

    // ============ HTML 解析（轻量正则，避免引入 jsoup） ============

    private val RESULT_BLOCK_REGEX = Regex(
        pattern = """<li[^>]+class="b_algo"[^>]*>([\s\S]*?)</li>""",
        options = setOf(RegexOption.IGNORE_CASE)
    )
    private val TITLE_LINK_REGEX = Regex(
        pattern = """<h2[^>]*>\s*<a[^>]+href="([^"]+)"[^>]*>([\s\S]*?)</a>""",
        options = setOf(RegexOption.IGNORE_CASE)
    )
    private val ANY_LINK_REGEX = Regex(
        pattern = """<a[^>]+href="([^"]+)"[^>]*>([\s\S]*?)</a>""",
        options = setOf(RegexOption.IGNORE_CASE)
    )
    private val SNIPPET_REGEX = Regex(
        pattern = """<p[^>]*>([\s\S]*?)</p>""",
        options = setOf(RegexOption.IGNORE_CASE)
    )
    private val TAG_REGEX = Regex("""<[^>]+>""")

    internal fun parseResults(html: String, maxResults: Int): List<SearchHit> {
        val hits = mutableListOf<SearchHit>()
        for (block in RESULT_BLOCK_REGEX.findAll(html)) {
            if (hits.size >= maxResults) break
            val body = block.value
            val link = TITLE_LINK_REGEX.find(body) ?: ANY_LINK_REGEX.find(body) ?: continue
            val rawUrl = decodeHtml(link.groupValues[1]).trim()
            val url = restoreBingRedirect(rawUrl)
            val title = stripTags(link.groupValues[2])
            if (title.isBlank() || url.isBlank() || !url.startsWith("http")) continue
            val snippet = SNIPPET_REGEX.find(body)?.let { stripTags(it.groupValues[1]) }.orEmpty()
            hits += SearchHit(title = title, url = url, snippet = snippet, provider = id)
        }
        return hits.distinctBy { it.url }
    }

    /**
     * 还原 Bing 跳转链接：/ck/a?...&u=a1<base64url> → 原始 URL。
     * 委托 SearchUrlNormalizer（聚合与解析共用同一实现）。
     */
    internal fun restoreBingRedirect(url: String): String =
        SearchUrlNormalizer.restoreBingRedirect(url)

    /** 拦截页启发式：无结果且页面含验证码特征 */
    internal fun looksLikeBlockedPage(html: String): Boolean {
        val lower = html.lowercase()
        return lower.contains("captcha") ||
            lower.contains("verify you are") ||
            lower.contains("异常流量")
    }

    private fun stripTags(htmlText: String): String =
        TAG_REGEX.replace(decodeHtml(htmlText), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()

    internal fun decodeHtml(text: String): String = text
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#x27;", "'")
        .replace("&#39;", "'")
        .replace("&nbsp;", " ")

    // ============ 结构化失败映射（internal 供单元测试） ============

    internal fun failureFromHttp(provider: String, statusCode: Int): SearchProviderResult.Failure =
        SearchProviderResult.Failure(
            provider = provider,
            kind = when (statusCode) {
                403, 429 -> SearchFailureKind.BLOCKED
                else -> SearchFailureKind.HTTP_ERROR
            },
            message = "Bing 返回 HTTP $statusCode",
            retryable = statusCode >= 500 || statusCode == 403 || statusCode == 429
        )

    internal fun failureFromThrowable(provider: String, e: Throwable): SearchProviderResult.Failure {
        val kind = when (e) {
            is io.ktor.client.network.sockets.ConnectTimeoutException -> SearchFailureKind.CONNECT_TIMEOUT
            is HttpRequestTimeoutException -> SearchFailureKind.REQUEST_TIMEOUT
            is UnknownHostException -> SearchFailureKind.DNS_ERROR
            else -> SearchFailureKind.UNKNOWN
        }
        return SearchProviderResult.Failure(
            provider = provider,
            kind = kind,
            message = "Bing 请求失败：${e.message ?: e::class.simpleName ?: "未知错误"}",
            retryable = kind == SearchFailureKind.CONNECT_TIMEOUT ||
                kind == SearchFailureKind.REQUEST_TIMEOUT ||
                kind == SearchFailureKind.DNS_ERROR
        )
    }

    private fun logAndFail(
        failure: SearchProviderResult.Failure,
        queryLength: Int,
        started: Long,
        error: Throwable? = null
    ): SearchProviderResult.Failure {
        val duration = System.currentTimeMillis() - started
        val line = "provider=BING queryLength=$queryLength " +
            "resultCount=0 failureKind=${failure.kind} durationMs=$duration msg=${failure.message.take(80)}"
        error?.let { AppLogger.e(TAG, line, it) } ?: AppLogger.w(TAG, line)
        return failure
    }

    companion object {
        private const val TAG = "BingSearchProvider"
    }
}
