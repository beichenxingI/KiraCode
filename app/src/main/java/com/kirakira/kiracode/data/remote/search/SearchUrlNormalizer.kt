package com.kirakira.kiracode.data.remote.search

import java.net.URI

/**
 * 搜索策略：
 * - PRIMARY_THEN_FALLBACK：主源 → 备用源顺序执行（当前默认，链中启用）
 * - PARALLEL_MERGE：并行请求多源后聚合（未来扩展，必须显式配置启用，当前不实现）
 */
enum class SearchStrategy {
    PRIMARY_THEN_FALLBACK,
    PARALLEL_MERGE
}

/**
 * 搜索结果统一处理器：URL 规范化 + 去重 + 排序保留。
 *
 * 规则（按任务约束）：
 * 1. 还原 Bing 跳转链接（/ck/a?...&u=a1<base64url>）；
 * 2. 去掉常见追踪参数（utm_* / fbclid / gclid 等）；
 * 3. 统一 URL 尾部斜杠（非根路径去尾 /）；
 * 4. 同一规范化 URL 去重，保留摘要更长的一条（不因标题相似误删不同页面）；
 * 5. 保持输入顺序（主源结果优先）；
 * 6. 按 maxResults 截断。
 */
object SearchUrlNormalizer {

    /** 视为追踪参数的键（小写比较） */
    private val TRACKING_PARAMS = setOf(
        "utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content",
        "utm_id", "fbclid", "gclid", "igshid", "msclkid", "ref_src", "ref_url",
        "spm", "scm", "share_token", "share_source"
    )

    /** 还原 Bing 跳转链接；非重定向链接原样返回（BingSearchProvider 与聚合共用） */
    fun restoreBingRedirect(url: String): String {
        val marker = "u=a1"
        val idx = url.indexOf(marker)
        if (idx < 0) return url
        val encoded = url.substring(idx + marker.length).substringBefore('&')
        return runCatching {
            val padded = encoded.replace('-', '+').replace('_', '/')
            val base64 = padded + "=".repeat((4 - padded.length % 4) % 4)
            String(java.util.Base64.getDecoder().decode(base64), Charsets.UTF_8)
                .takeIf { it.startsWith("http") }
        }.getOrNull() ?: url
    }

    /** 规范化单条 URL：还原重定向 + 去追踪参数 + 尾斜杠统一；失败时原样返回 */
    fun normalizeUrl(rawUrl: String): String {
        val url = restoreBingRedirect(rawUrl.trim())
        return runCatching {
            val uri = URI(url)
            val scheme = uri.scheme?.lowercase() ?: return url
            val host = uri.host?.lowercase() ?: return url
            val port = when {
                uri.port == -1 || (scheme == "http" && uri.port == 80) || (scheme == "https" && uri.port == 443) -> ""
                else -> ":${uri.port}"
            }
            val path = uri.path.orEmpty().let { if (it.length > 1 && it.endsWith("/")) it.trimEnd('/') else it }
            val keptQuery = uri.rawQuery?.split('&')
                ?.filter { it.isNotBlank() }
                ?.filterNot { param ->
                    val key = param.substringBefore('=').substringBefore('%').lowercase()
                    key in TRACKING_PARAMS
                }
                ?.joinToString("&")
                .orEmpty()
            buildString {
                append(scheme).append("://").append(host).append(port)
                if (path.isNotEmpty() && path != "/") append(path)
                if (keptQuery.isNotEmpty()) append('?').append(keptQuery)
                // fragment 对搜索结果归并无意义，丢弃
            }
        }.getOrDefault(url)
    }

    /**
     * 聚合入口：规范化 + 去重（同 URL 保留摘要更长的一条）+ 保序 + 截断。
     * 当前默认单源结果也经此处理，为未来并行聚合预留同一出口。
     */
    fun mergeAndRank(results: List<SearchHit>, maxResults: Int): List<SearchHit> {
        val best = linkedMapOf<String, SearchHit>()
        for (hit in results) {
            val key = normalizeUrl(hit.url)
            if (key.isBlank()) continue
            val existing = best[key]
            if (existing == null || hit.snippet.length > existing.snippet.length) {
                best[key] = hit
            }
        }
        return best.values.take(maxResults.coerceIn(1, 10))
    }
}
