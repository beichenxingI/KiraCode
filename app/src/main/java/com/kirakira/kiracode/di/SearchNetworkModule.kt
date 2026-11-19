package com.kirakira.kiracode.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpRedirect
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import javax.inject.Singleton

/**
 * 联网模块共享 HTTP 客户端（搜索源 + 网页抓取共用）：
 *
 * - 纯文本客户端：不安装 ContentNegotiation，HTML 用 bodyAsText() 读取；
 *   SearXNG JSON 用 kotlinx.serialization 手动解析（明确、可控）；
 * - 统一默认头（桌面 UA / Accept / Accept-Language）与默认超时（20s/10s）；
 * - 个别源可用 per-request `timeout { ... }` 覆盖（如 SearXNG 15s/8s）；
 * - Hilt 单例管理生命周期，避免各 provider 重复创建 HttpClient。
 */
@Module
@InstallIn(SingletonComponent::class)
object SearchNetworkModule {

    @Provides
    @Singleton
    fun provideWebHttpClient(): HttpClient = HttpClient(CIO) {
        install(HttpTimeout) {
            requestTimeoutMillis = 20_000
            connectTimeoutMillis = 10_000
        }
        install(HttpRedirect) {
            checkHttpMethod = false
            allowHttpsDowngrade = false
        }
        defaultRequest {
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
