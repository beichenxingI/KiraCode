package com.kirakira.kiracode.data.remote.search

import com.kirakira.kiracode.util.AppLogger
import io.ktor.client.*
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton

/** SearXNG 实例配置（公共实例可用性随时间变化，列表可整体替换） */
data class SearxngInstance(
    val baseUrl: String,
    val displayName: String
)

/**
 * SearXNG 备用搜索源（JSON API，免 API Key）。
 *
 * - GET {instance}/search?q=...&format=json&language=zh-CN
 * - 使用 Hilt 共享 web HTTP 客户端；per-request timeout 覆盖为 15s/8s
 * - 明确 JSON 解析（kotlinx.serialization 手动 decodeFromString，不装 ContentNegotiation）
 * - 多实例依次尝试：单实例失败继续下一个；全部失败返回结构化 Failure
 * - 注意：默认实例为公共实例，可用性未经真机长期验证（详见提交与验收报告），
 *   不把任何实例当成永久可靠服务；生产可经配置替换。
 */
@Singleton
class SearxngSearchProvider @Inject constructor(
    private val client: HttpClient
) : SearchProvider {

    override val id: String = "SEARXNG"

    /** 默认实例列表（代码提供，允许后续替换；JSON format 需实例开启） */
    private val defaultInstances: List<SearxngInstance> = listOf(
        SearxngInstance("https://searx.be", "searx.be"),
        SearxngInstance("https://searx.tiekoetter.com", "searx.tiekoetter.com")
    )

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Serializable
    data class SearxngResponseDto(
        val results: List<SearxngResultDto>? = null
    )

    @Serializable
    data class SearxngResultDto(
        val title: String? = null,
        val url: String? = null,
        val content: String? = null
    )

    override suspend fun search(query: String, maxResults: Int): SearchProviderResult =
        withContext(Dispatchers.IO) {
            val capped = maxResults.coerceIn(1, 10)
            var lastFailure: SearchProviderResult.Failure? = null
            for (instance in defaultInstances) {
                val started = System.currentTimeMillis()
                val result = tryInstance(instance, query, capped, started)
                when (result) {
                    is SearchProviderResult.Success -> return@withContext result
                    is SearchProviderResult.Failure -> lastFailure = result
                }
            }
            lastFailure ?: SearchProviderResult.Failure(
                id, SearchFailureKind.UNKNOWN, "SearXNG 未配置可用实例", retryable = true
            )
        }

    // ============ JSON 解析（internal 供单元测试，fixture 驱动） ============

    /** 解析 SearXNG JSON 响应；非法 JSON 抛出（调用方转 PARSE_ERROR） */
    internal fun parseJson(body: String): SearxngResponseDto =
        json.decodeFromString(SearxngResponseDto.serializer(), body)

    /** DTO → 统一 SearchHit：缺 title/url 或非 http 的条目丢弃，按 URL 去重 */
    internal fun mapResults(dto: SearxngResponseDto): List<SearchHit> =
        (dto.results ?: emptyList())
            .mapNotNull { r ->
                val url = r.url?.trim().orEmpty()
                val title = r.title?.trim().orEmpty()
                if (title.isBlank() || !url.startsWith("http")) return@mapNotNull null
                SearchHit(title = title, url = url, snippet = r.content.orEmpty(), provider = id)
            }
            .distinctBy { it.url }

    private suspend fun tryInstance(
        instance: SearxngInstance,
        query: String,
        maxResults: Int,
        started: Long
    ): SearchProviderResult {
        var status: Int? = null
        try {
            val response = client.get("${instance.baseUrl}/search") {
                parameter("q", query)
                parameter("format", "json")
                parameter("language", "zh-CN")
                // SearXNG 用更短的超时（15s/8s）：备用源快速失败，避免拖慢整链
                timeout {
                    requestTimeoutMillis = 15_000
                    connectTimeoutMillis = 8_000
                }
            }
            status = response.status.value
            if (!response.status.isSuccess()) {
                return logAndFail(
                    instance,
                    SearchProviderResult.Failure(
                        id,
                        if (response.status.value == 403 || response.status.value == 429) SearchFailureKind.BLOCKED
                        else SearchFailureKind.HTTP_ERROR,
                        "SearXNG(${instance.displayName}) 返回 HTTP ${response.status.value}",
                        retryable = response.status.value >= 500 || response.status.value == 403 || response.status.value == 429
                    ),
                    query, started
                )
            }
            val body = response.bodyAsText()
            val dto = try {
                parseJson(body)
            } catch (e: Exception) {
                return logAndFail(
                    instance,
                    SearchProviderResult.Failure(
                        id, SearchFailureKind.PARSE_ERROR,
                        "SearXNG(${instance.displayName}) JSON 解析失败", retryable = true
                    ),
                    query, started, e
                )
            }
            val hits = mapResults(dto).take(maxResults)
            if (hits.isEmpty()) {
                return logAndFail(
                    instance,
                    SearchProviderResult.Failure(
                        id, SearchFailureKind.EMPTY_RESULT,
                        "SearXNG(${instance.displayName}) 未返回结果", retryable = true
                    ),
                    query, started
                )
            }
            AppLogger.d(
                TAG,
                "provider=SEARXNG instance=${instance.displayName} queryLength=${query.length} " +
                    "status=$status resultCount=${hits.size}"
            )
            return SearchProviderResult.Success(id, hits)
        } catch (e: Exception) {
            val failure = when (e) {
                is io.ktor.client.network.sockets.ConnectTimeoutException ->
                    SearchProviderResult.Failure(id, SearchFailureKind.CONNECT_TIMEOUT, "SearXNG(${instance.displayName}) 连接超时", retryable = true)
                is HttpRequestTimeoutException ->
                    SearchProviderResult.Failure(id, SearchFailureKind.REQUEST_TIMEOUT, "SearXNG(${instance.displayName}) 请求超时", retryable = true)
                is UnknownHostException ->
                    SearchProviderResult.Failure(id, SearchFailureKind.DNS_ERROR, "SearXNG(${instance.displayName}) 域名无法解析", retryable = true)
                else ->
                    SearchProviderResult.Failure(id, SearchFailureKind.UNKNOWN, "SearXNG(${instance.displayName}) 请求失败：${e.message ?: "未知错误"}", retryable = true)
            }
            return logAndFail(instance, failure, query, started, e)
        }
    }

    private fun logAndFail(
        instance: SearxngInstance,
        failure: SearchProviderResult.Failure,
        query: String,
        started: Long,
        error: Throwable? = null
    ): SearchProviderResult.Failure {
        val duration = System.currentTimeMillis() - started
        val line = "provider=SEARXNG instance=${instance.displayName} queryLength=${query.length} " +
            "resultCount=0 failureKind=${failure.kind} durationMs=$duration msg=${failure.message.take(80)}"
        error?.let { AppLogger.e(TAG, line, it) } ?: AppLogger.w(TAG, line)
        return failure
    }

    companion object {
        private const val TAG = "SearxngSearchProvider"
    }
}
