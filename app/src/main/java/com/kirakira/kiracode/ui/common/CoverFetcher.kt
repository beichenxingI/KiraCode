package com.kirakira.kiracode.ui.common

import android.util.Base64
import com.kirakira.kiracode.data.remote.real.RealApiService
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 封面抓取器：把 AI 给出的图片 URL 下载并转为 data URI，
 * 使封面可以随卡持久化并嵌入 PNG 导出。
 */
object CoverFetcher {

    /** 支持的图片类型 → MIME */
    private val MIME_MAP = mapOf(
        "png" to "image/png",
        "jpg" to "image/jpeg",
        "jpeg" to "image/jpeg",
        "webp" to "image/webp",
        "gif" to "image/gif"
    )

    /**
     * 下载图片为 data URI；失败返回 null。
     */
    suspend fun fetchAsDataUri(url: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val client = RealApiService.createClient()
            try {
                val response: HttpResponse = client.get(url)
                if (!response.status.isSuccess()) return@runCatching null
                val bytes: ByteArray = response.body()
                if (bytes.isEmpty() || bytes.size > 8 * 1024 * 1024) return@runCatching null // 上限 8MB
                val mime = guessMime(url, bytes)
                val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                "data:$mime;base64,$b64"
            } finally {
                client.close()
            }
        }.getOrNull()
    }

    private fun guessMime(url: String, bytes: ByteArray): String {
        val ext = url.substringBefore('?').substringAfterLast('.', "").lowercase()
        MIME_MAP[ext]?.let { return it }
        return when {
            bytes.size > 8 && bytes[0] == 0x89.toByte() && bytes[1] == 'P'.code.toByte() -> "image/png"
            bytes.size > 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() -> "image/jpeg"
            else -> "image/png"
        }
    }
}
