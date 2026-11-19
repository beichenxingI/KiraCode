package com.kirakira.kiracode.data.remote

import com.kirakira.kiracode.data.remote.real.RealApiService
import com.kirakira.kiracode.data.remote.search.SearchHit
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.HttpRedirect
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URLDecoder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 联网搜索服务（V3.0，历史 DuckDuckGo HTML 实现）。
 *
 * 注意：多搜索源架构（search 包）上线后，AgentController 已改走
 * SearchProviderChain（Bing → SearXNG）；本类保留作为参考实现与回退储备，
 * 不再扩展 DuckDuckGo 相关逻辑。
 *
 * 通过 DuckDuckGo HTML 端点检索资料（无需 API Key），
 * 供 AI 在创作角色卡时按需获取外部知识。
 */
@Singleton
class WebSearchService @Inject constructor() {

    private val client by lazy {
        HttpClient(CIO) {
            // 纯 GET HTML 客户端：不安装 ContentNegotiation（避免 JSON 反序列化干扰）
            install(HttpTimeout) {
                requestTimeoutMillis = 15_000
                connectTimeoutMillis = 10_000
            }
            install(HttpRedirect) {
                checkHttpMethod = false
                allowHttpsDowngrade = false
            }
            defaultRequest {
                // 桌面 Chrome UA：降低被搜索引擎反爬拦截的概率
                header(
                    HttpHeaders.UserAgent,
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                        "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                )
                header(HttpHeaders.Accept, "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                header(HttpHeaders.AcceptLanguage, "zh-CN,zh;q=0.9,en;q=0.8")
            }
        }
    }

    /**
     * 检索资料，返回前 N 条结果。
     */
    suspend fun search(query: String, maxResults: Int = 5): Result<List<SearchHit>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val response = client.get("https://html.duckduckgo.com/html/") {
                    parameter("q", query)
                    parameter("kl", "cn-zh")
                }
                if (!response.status.isSuccess()) {
                    error("搜索服务返回 HTTP ${response.status.value}")
                }
                parseResults(response.bodyAsText(), maxResults)
                    .ifEmpty { error("未检索到相关结果") }
            }.recoverCatching { e ->
                throw com.kirakira.kiracode.domain.exception.NetworkException(
                    "联网搜索失败：${e.message}",
                    errorCode = "WEB_SEARCH_FAILED",
                    cause = e
                )
            }
        }

    /** 将结果格式化为可注入对话的文本摘要 */
    fun formatDigest(hits: List<SearchHit>): String = buildString {
        appendLine("共 ${hits.size} 条结果：")
        hits.forEachIndexed { i, h ->
            appendLine("${i + 1}. ${h.title}")
            if (h.snippet.isNotBlank()) appendLine("   ${h.snippet.take(220)}")
            appendLine("   来源: ${h.url}")
        }
    }.trim()

    // ============ HTML 解析（轻量正则，避免引入 jsoup） ============

    private val RESULT_BLOCK_REGEX = Regex(
        pattern = """class="result__body"([\s\S]*?)</div>\s*</div>""",
        options = setOf(RegexOption.IGNORE_CASE)
    )
    private val LINK_REGEX = Regex(
        pattern = """<a[^>]+class="result__a"[^>]+href="([^"]+)"[^>]*>([\s\S]*?)</a>""",
        options = setOf(RegexOption.IGNORE_CASE)
    )
    private val SNIPPET_REGEX = Regex(
        pattern = """class="result__snippet"[^>]*>([\s\S]*?)</a>""",
        options = setOf(RegexOption.IGNORE_CASE)
    )
    private val TAG_REGEX = Regex("""<[^>]+>""")
    private val UDDG_REGEX = Regex("""uddg=([^&]+)""")

    internal fun parseResults(html: String, maxResults: Int): List<SearchHit> {
        val hits = mutableListOf<SearchHit>()
        val blocks = RESULT_BLOCK_REGEX.findAll(html).toList()
        for (block in blocks) {
            if (hits.size >= maxResults) break
            val body = block.value
            val link = LINK_REGEX.find(body) ?: continue
            val rawUrl = decodeHtml(link.groupValues[1])
            val url = resolveDdgRedirect(rawUrl)
            val title = stripTags(link.groupValues[2])
            if (title.isBlank() || url.isBlank()) continue
            val snippet = SNIPPET_REGEX.find(body)?.let { stripTags(it.groupValues[1]) }.orEmpty()
            hits += SearchHit(title = title, url = url, snippet = snippet, provider = "DUCKDUCKGO")
        }
        return hits.distinctBy { it.url }
    }

    /** DuckDuckGo 跳转链接还原真实 URL */
    private fun resolveDdgRedirect(url: String): String {
        val m = UDDG_REGEX.find(url) ?: return url
        return runCatching { URLDecoder.decode(m.groupValues[1], "UTF-8") }.getOrDefault(url)
    }

    private fun stripTags(htmlText: String): String =
        TAG_REGEX.replace(decodeHtml(htmlText), "").trim()

    private fun decodeHtml(text: String): String = text
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#x27;", "'")
        .replace("&#39;", "'")
        .replace("&nbsp;", " ")
}
