package com.kirakira.kiracode.data.remote.search

/**
 * 搜索源统一契约：Bing / SearXNG /（历史 DuckDuckGo）均实现同一接口。
 *
 * AgentController 只依赖搜索链（SearchProviderChain），不感知具体搜索源实现；
 * UI 只消费 SearchHit.provider 展示来源。
 */
interface SearchProvider {
    /** 源标识（如 BING / SEARXNG），写入 SearchHit.provider 与诊断日志 */
    val id: String

    /**
     * 执行搜索。
     * @param query 查询词（非空，由调用方校验）
     * @param maxResults 结果数量上限（1..10，实现方自行夹取）
     * @return 结构化成功/失败，不抛异常表达业务失败
     */
    suspend fun search(
        query: String,
        maxResults: Int
    ): SearchProviderResult
}
