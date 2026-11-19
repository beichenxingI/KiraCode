package com.kirakira.kiracode.data.remote.search

import com.kirakira.kiracode.util.AppLogger

/** 单次源尝试记录（诊断用，不含结果正文） */
data class SearchAttempt(
    val provider: String,
    val success: Boolean,
    val failureKind: SearchFailureKind?,
    val statusCode: Int?,
    val durationMs: Long
)

/** 搜索链结果：成功（含尝试轨迹）或全链失败（区分用户可见/模型可见消息） */
sealed interface SearchChainResult {
    data class SearchChainSuccess(
        val provider: String,
        val results: List<SearchHit>,
        val attempts: List<SearchAttempt>
    ) : SearchChainResult

    data class SearchChainFailure(
        val attempts: List<SearchAttempt>,
        val userMessage: String,
        val modelMessage: String
    ) : SearchChainResult
}

/**
 * Bing 主源 → SearXNG 备用源的顺序搜索链（默认不并行）。
 *
 * 规则：
 * 1. 依次调用 primary → fallbacks；任一源成功且结果非空即返回，不再调用后续源；
 * 2. 全部失败：生成结构化失败（不抛异常、不自动重试、不无限循环）；
 * 3. 不把内部 stack trace 暴露给模型或用户；
 * 4. attempts 完整记录每次尝试（provider/kind/status/duration），供诊断与 UI。
 */
class SearchProviderChain(
    private val primary: SearchProvider,
    private val fallbacks: List<SearchProvider>,
    /** 当前仅支持顺序策略；PARALLEL_MERGE 必须显式配置且未来实现，当前按顺序执行并告警 */
    private val strategy: SearchStrategy = SearchStrategy.PRIMARY_THEN_FALLBACK
) {

    init {
        if (strategy != SearchStrategy.PRIMARY_THEN_FALLBACK) {
            AppLogger.w(TAG, "strategy=$STRATEGY_PARALLEL 尚未实现，已回退为顺序策略 PRIMARY_THEN_FALLBACK")
        }
    }

    suspend fun search(query: String, maxResults: Int): SearchChainResult {
        val attempts = mutableListOf<SearchAttempt>()
        val providers = listOf(primary) + fallbacks
        for (provider in providers) {
            val started = System.currentTimeMillis()
            when (val result = provider.search(query, maxResults)) {
                is SearchProviderResult.Success -> {
                    attempts += SearchAttempt(
                        provider = result.provider, success = true,
                        failureKind = null, statusCode = null,
                        durationMs = System.currentTimeMillis() - started
                    )
                    AppLogger.d(
                        TAG,
                        "chain=OK provider=${result.provider} queryLength=${query.length} resultCount=${result.results.size} attempts=${attempts.size}"
                    )
                    return SearchChainResult.SearchChainSuccess(
                        provider = result.provider,
                        results = mergeAndRank(result.results, maxResults),
                        attempts = attempts.toList()
                    )
                }
                is SearchProviderResult.Failure -> {
                    attempts += SearchAttempt(
                        provider = result.provider, success = false,
                        failureKind = result.kind, statusCode = null,
                        durationMs = System.currentTimeMillis() - started
                    )
                    AppLogger.w(
                        TAG,
                        "chain=FAIL provider=${result.provider} queryLength=${query.length} failureKind=${result.kind} attempts=${attempts.size}"
                    )
                }
            }
        }
        return SearchChainResult.SearchChainFailure(
            attempts = attempts.toList(),
            userMessage = buildUserMessage(attempts),
            modelMessage = buildModelMessage(attempts)
        )
    }

    /** 结果规范化：URL 还原/去追踪参数/去重/保序/截断（并行聚合的未来统一出口） */
    internal fun mergeAndRank(results: List<SearchHit>, maxResults: Int): List<SearchHit> =
        SearchUrlNormalizer.mergeAndRank(results, maxResults)

    /** 用户可见失败消息：简短、不暴露内部细节 */
    private fun buildUserMessage(attempts: List<SearchAttempt>): String {
        val detail = attempts.joinToString("，") { attempt ->
            "${providerLabel(attempt.provider)}${describeKind(attempt.failureKind)}"
        }
        return "联网搜索失败：$detail。"
    }

    /** 模型可见失败消息：完整尝试轨迹 + 明确行为指引（不得编造结果） */
    private fun buildModelMessage(attempts: List<SearchAttempt>): String {
        val lines = attempts.joinToString("\n") { attempt ->
            "- ${providerLabel(attempt.provider)}：${describeKind(attempt.failureKind)}"
        }
        return "搜索失败。已尝试：\n$lines\n\n" +
            "本次没有获得可靠搜索结果。不要根据搜索失败自行编造答案。\n" +
            "你可以换一个更具体的查询词重试一次，或直接告诉用户当前无法联网。"
    }

    private fun providerLabel(provider: String): String = when (provider) {
        "BING" -> "Bing"
        "SEARXNG" -> "备用搜索源"
        else -> provider
    }

    private fun describeKind(kind: SearchFailureKind?): String = when (kind) {
        SearchFailureKind.CONNECT_TIMEOUT -> "连接超时"
        SearchFailureKind.REQUEST_TIMEOUT -> "请求超时"
        SearchFailureKind.HTTP_ERROR -> "服务暂时不可用"
        SearchFailureKind.PARSE_ERROR -> "返回格式异常"
        SearchFailureKind.EMPTY_RESULT -> "没有检索到结果"
        SearchFailureKind.DNS_ERROR -> "网络连接失败"
        SearchFailureKind.BLOCKED -> "被服务方限制访问"
        null -> "未知状态"
        SearchFailureKind.UNKNOWN -> "未知错误"
    }

    companion object {
        private const val TAG = "SearchProviderChain"
        private const val STRATEGY_PARALLEL = "PARALLEL_MERGE"
    }
}
