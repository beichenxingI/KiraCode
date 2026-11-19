package com.kirakira.kiracode.data.remote

import com.kirakira.kiracode.util.AppLogger
import io.ktor.client.*
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 网页抓取与正文提取服务（配合 web_search 的深度调研工具）。
 *
 * 零外部依赖：HTTP GET 抓取 HTML → 正则清理 script/style 等 → 纯文本 → 10K 截断。
 * 使用 Hilt 共享 web HTTP 客户端（默认超时 20s/10s，见 SearchNetworkModule）。
 * 只处理公开可访问页面；错误分类为 404/403/429/超时/域名解析失败。
 */
@Singleton
class WebpageFetcher @Inject constructor(
    private val client: HttpClient
) {

    /** 单次回注给模型的正文上限（约 10K 字符，避免撑爆上下文） */
    private val maxContentChars = 10_000

    /**
     * 抓取网页并提取正文。
     * @return 「来源：$url + 清理后的正文」，截断到 10K 字符
     * @throws Exception 网络错误 / HTTP 错误 / 提取失败（消息已分类，可直接回注模型）
     */
    suspend fun fetch(url: String): String = withContext(Dispatchers.IO) {
        // SSRF 防护：拒绝非 http(s)、内网/回环/link-local 地址（web_search 与 fetch_webpage 共用同一策略）
        if (!UrlSafety.isAllowed(url)) {
            throw Exception("URL 不允许抓取（仅支持公网 http/https 地址）：${url.take(80)}")
        }
        AppLogger.d(TAG, "开始抓取: $url")
        try {
            val response = client.get(url)
            if (!response.status.isSuccess()) {
                throw when (response.status) {
                    HttpStatusCode.NotFound -> Exception("页面不存在（404）")
                    HttpStatusCode.Forbidden -> Exception("访问被拒绝（403），站点可能屏蔽了自动抓取")
                    HttpStatusCode.TooManyRequests -> Exception("请求过于频繁（429），请稍后重试")
                    else -> Exception("HTTP ${response.status.value}: ${response.status.description}")
                }
            }
            val html = response.bodyAsText()
            val text = extractMainContent(html)
            AppLogger.d(TAG, "抓取成功 url=$url chars=${text.length}")
            "来源：$url\n\n$text"
        } catch (e: HttpRequestTimeoutException) {
            throw Exception("请求超时，页面加载过慢（20s 上限）")
        } catch (e: io.ktor.client.network.sockets.ConnectTimeoutException) {
            throw Exception("连接超时，目标服务器无响应（10s 上限）")
        } catch (e: UnknownHostException) {
            throw Exception("域名无法解析：${e.message ?: "未知主机"}")
        } catch (e: IllegalStateException) {
            throw e
        } catch (e: Exception) {
            // 已分类的业务错误（HTTP 4xx/5xx、提取失败）直接上抛，避免二次包装丢失语义
            if (e.message?.startsWith("HTTP ") == true ||
                e.message?.contains("404") == true ||
                e.message?.contains("403") == true ||
                e.message?.contains("429") == true ||
                e.message?.startsWith("正文提取失败") == true
            ) {
                throw e
            }
            throw Exception("抓取失败：${e.message ?: "未知错误"}")
        }
    }

    // ============ 正文提取（轻量正则，避免引入 jsoup） ============

    private fun extractMainContent(html: String): String {
        try {
            val text = html
                // 注释与无正文价值的块级标签
                .replace(Regex("<!--([\\s\\S]*?)-->"), " ")
                .replace(Regex("""<(script|style|noscript|svg|iframe|head)[^>]*>[\s\S]*?</\1\s*>""", RegexOption.IGNORE_CASE), " ")
                // 剩余标签 → 空格（保留词间分隔）
                .replace(Regex("""<[^>]+>"""), " ")
                // 实体解码
                .let(::decodeEntities)
                // 空白归一
                .replace(Regex("""\s+"""), " ")
                .trim()
            if (text.isBlank()) {
                throw Exception("正文提取失败：页面无可提取文本（可能是 JS 渲染的 SPA 页面）")
            }
            return if (text.length > maxContentChars) {
                text.take(maxContentChars) + "\n\n[内容过长，已截断至 ${maxContentChars} 字符]"
            } else {
                text
            }
        } catch (e: Exception) {
            if (e.message?.startsWith("正文提取失败") == true) throw e
            AppLogger.e(TAG, "正文提取失败", e)
            throw Exception("正文提取失败：${e.message ?: "未知错误"}")
        }
    }

    private fun decodeEntities(text: String): String = text
        .replace("&nbsp;", " ")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#x27;", "'")
        .replace("&#39;", "'")
        .replace("&mdash;", "—")
        .replace("&ndash;", "–")
        .replace("&", "&") // last: decode ampersand last

    companion object {
        private const val TAG = "WebpageFetcher"
    }
}
