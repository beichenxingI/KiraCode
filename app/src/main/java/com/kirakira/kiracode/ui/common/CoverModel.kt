package com.kirakira.kiracode.ui.common

import java.io.File

/**
 * 封面展示模型统一入口：
 * - data URI 解码为 ByteArray（Coil 2.6 无内置 DataUriFetcher，ByteArray 走 ByteBufferFetcher）；
 * - 本地绝对路径转为 File，避免 Coil 把 /data/... 当成无 scheme URL；
 * - http URL / covers/ 相对路径原样交给 Coil。
 *
 * 使用 java.util.Base64（minSdk 26），MimeDecoder 兼容带换行的 Base64。
 */
fun resolveCoverCoilModel(avatar: String?): Any? {
    if (avatar.isNullOrBlank()) return null
    return when {
        avatar.startsWith("data:") -> runCatching {
            val b64 = avatar.substringAfter("base64,", "")
            require(b64.isNotBlank()) { "data URI 不含 base64 数据" }
            java.util.Base64.getMimeDecoder().decode(b64)
        }.getOrNull()
        avatar.startsWith("/") -> File(avatar)
        else -> avatar
    }
}
