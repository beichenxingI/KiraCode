package com.kirakira.kiracode.data.local

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 角色卡仓库文件存储层（V1.0.0 大修）。
 *
 * 背景：SillyTavern V3 卡可达几十 MB（大封面 base64 / 巨型世界书 / 长正文），
 * 完整 JSON 存 SQLite 会触发 `Row too big to fit into CursorWindow` 崩溃。
 * 因此完整卡 JSON 与封面图改为落盘，Room 只存元数据 + 文件路径。
 *
 * 目录：
 *   filesDir/warehouse/cards/<uuid>.json     完整卡 JSON（StCardCodec 线格式）
 *   filesDir/warehouse/covers/<uuid>.<ext>   封面图字节
 */
@Singleton
class WarehouseStorage @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val root: File get() = File(context.filesDir, "warehouse")
    private val cardsDir: File get() = File(root, "cards")
    private val coversDir: File get() = File(root, "covers")

    private fun ensureDirs() {
        cardsDir.mkdirs()
        coversDir.mkdirs()
    }

    /** 写完整卡 JSON，返回相对文件路径（如 cards/<uuid>.json）或 null */
    fun writeCard(uuid: String, json: String): String? = runCatching {
        ensureDirs()
        val f = File(cardsDir, "$uuid.json")
        f.writeText(json, Charsets.UTF_8)
        "cards/$uuid.json"
    }.getOrNull()

    /** 读完整卡 JSON；失败返回 null */
    fun readCard(relativePath: String?): String? {
        if (relativePath.isNullOrBlank()) return null
        return runCatching {
            File(root, relativePath).takeIf { it.exists() }?.readText(Charsets.UTF_8)
        }.getOrNull()
    }

    fun deleteCard(relativePath: String?) {
        if (relativePath.isNullOrBlank()) return
        runCatching { File(root, relativePath).delete() }
    }

    /** 写封面字节，返回相对路径；mime 建议 image/png|image/jpeg */
    fun writeCover(uuid: String, bytes: ByteArray, ext: String = "png"): String? = runCatching {
        ensureDirs()
        val f = File(coversDir, "$uuid.$ext")
        f.writeBytes(bytes)
        "covers/$uuid.$ext"
    }.getOrNull()

    fun readCover(relativePath: String?): ByteArray? {
        if (relativePath.isNullOrBlank()) return null
        return runCatching { File(root, relativePath).takeIf { it.exists() }?.readBytes() }.getOrNull()
    }

    /** 封面相对路径 → 绝对文件路径（供 Coil 加载）；非 covers/ 前缀原样返回 */
    fun resolveCoverPath(relativePath: String?): String? {
        if (relativePath.isNullOrBlank()) return null
        return if (relativePath.startsWith("covers/")) File(root, relativePath).absolutePath else relativePath
    }

    fun deleteCover(uuid: String) {
        runCatching {
            coversDir.listFiles()?.filter { it.name.startsWith("$uuid.") }?.forEach { it.delete() }
        }
    }

    /** 从 data URI 或路径提取封面字节，便于统一落盘 */
    fun extractCoverBytes(avatar: String?): Pair<ByteArray, String>? {
        if (avatar.isNullOrBlank()) return null
        return when {
            avatar.startsWith("data:image") -> {
                runCatching {
                    val mime = avatar.substringAfter("data:").substringBefore(";base64")
                    val b64 = avatar.substringAfter("base64,")
                    val bytes = android.util.Base64.decode(b64, android.util.Base64.NO_WRAP)
                    val ext = when {
                        mime.contains("png") -> "png"
                        mime.contains("jpeg") || mime.contains("jpg") -> "jpg"
                        mime.contains("webp") -> "webp"
                        else -> "png"
                    }
                    bytes to ext
                }.getOrNull()
            }
            avatar.startsWith("covers/") -> {
                val b = readCover(avatar)
                if (b != null) b to avatar.substringAfterLast(".") else null
            }
            else -> null // http URL 等不落盘
        }
    }
}