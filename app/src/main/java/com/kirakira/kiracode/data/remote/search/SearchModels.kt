package com.kirakira.kiracode.data.remote.search

import kotlinx.serialization.Serializable

/**
 * 统一搜索结果条目（provider 中立）。
 *
 * 从 WebSearchService（DuckDuckGo 版）迁移而来；新增 provider 字段
 * 以支撑多搜索源架构（结果来源对 AI 与 UI 均可见）。
 */
@Serializable
data class SearchHit(
    val title: String,
    val url: String,
    val snippet: String,
    val provider: String
)

/** 单个搜索源的失败类别（结构化错误协议，不以字符串异常为唯一通道） */
enum class SearchFailureKind {
    CONNECT_TIMEOUT,
    REQUEST_TIMEOUT,
    HTTP_ERROR,
    PARSE_ERROR,
    EMPTY_RESULT,
    DNS_ERROR,
    BLOCKED,
    UNKNOWN
}

/**
 * 单个搜索源的执行结果：成功携带结果列表，失败携带结构化原因。
 * 不依靠抛出普通字符串异常作为唯一错误协议。
 */
sealed interface SearchProviderResult {
    data class Success(
        val provider: String,
        val results: List<SearchHit>
    ) : SearchProviderResult

    data class Failure(
        val provider: String,
        val kind: SearchFailureKind,
        val message: String,
        val retryable: Boolean
    ) : SearchProviderResult
}
