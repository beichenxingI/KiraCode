package com.kirakira.kiracode.data.remote

import com.kirakira.kiracode.data.remote.real.RealApiService
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * KiraKira 公告/版本信息（与 KiraCore 公告接口同源）
 */
@Serializable
data class Announcement(
    val version: String = "",
    val title: String = "",
    val content: String = "",
    val imageUrl: String? = null,
    val downloadUrl: String = "",
    @SerialName("forceUpdate") val forceUpdate: Boolean = false,
    @SerialName("minAppVersion") val minAppVersion: String = ""
)

/**
 * 公告客户端：从 Gitee 公告仓库获取最新版本信息。
 *
 * 接口：<a href="https://gitee.com/kirakira-config/kirakira-announcements/raw/main/announcement.json">announcement.json</a>
 */
object AnnouncementClient {

    const val ANNOUNCEMENT_URL =
        "https://gitee.com/kirakira-config/kirakira-announcements/raw/main/announcement.json"

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** 解析公告 JSON（internal 便于单测） */
    internal fun parseAnnouncement(body: String): Announcement =
        json.decodeFromString(Announcement.serializer(), body)

    /**
     * 拉取最新公告；失败返回 Result.failure（调用方展示友好错误）。
     */
    suspend fun fetch(): Result<Announcement> = withContext(Dispatchers.IO) {
        runCatching {
            val client = RealApiService.createClient()
            try {
                val response: HttpResponse = client.get(ANNOUNCEMENT_URL) {
                    header("User-Agent", "KiraCard/1.0.0")
                }
                if (!response.status.isSuccess()) error("公告服务返回 HTTP ${response.status.value}")
                val body = response.bodyAsText()
                if (body.isBlank()) error("公告内容为空")
                parseAnnouncement(body)
            } finally {
                client.close()
            }
        }.recoverCatching { e ->
            throw com.kirakira.kiracode.domain.exception.NetworkException(
                "获取公告失败：${e.message}",
                errorCode = "ANNOUNCEMENT_FETCH_FAILED",
                cause = e
            )
        }
    }
}
