package com.kirakira.kiracode.domain.usecase

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import com.kirakira.kiracode.domain.exception.ExportException
import com.kirakira.kiracode.domain.model.CharacterCard
import com.kirakira.kiracode.domain.model.ExportFormat
import com.kirakira.kiracode.domain.model.st.StCardCodec
import com.kirakira.kiracode.domain.model.st.StSpec
import com.kirakira.kiracode.domain.util.PngTextChunkCodec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject

/**
 * 导出角色卡用例 (V2.0)
 * 通过 StCardCodec 无损序列化为 SillyTavern 线格式；
 * PNG 导出同时嵌入 chara(v2) 与 ccv3(v3) 文本块，最大化兼容。
 */
class ExportCharacterCardUseCase @Inject constructor(
    private val context: Context
) {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        allowSpecialFloatingPointValues = true
    }

    /**
     * 导出为指定格式文件
     * @param card 要导出的角色卡
     * @param fileName 文件名（不含扩展名）
     * @param format 导出格式
     * @return 文件 URI
     */
    suspend operator fun invoke(
        card: CharacterCard,
        fileName: String,
        format: ExportFormat = ExportFormat.JSON
    ): Result<Uri> = withContext(Dispatchers.IO) {
        runCatching {
            when (format) {
                ExportFormat.JSON -> exportJson(card, fileName)
                ExportFormat.PNG -> exportPngWithMetadata(card, fileName)
            }
        }.recoverCatching { e ->
            throw ExportException(
                "导出 ${format.name} 失败: ${e.message}",
                format = format.name,
                cause = e
            )
        }
    }

    /**
     * V3.0 成品区：渲染并嵌入元数据，返回完整 PNG 字节（不落盘）。
     */
    suspend fun renderToPngBytes(card: CharacterCard): ByteArray = withContext(Dispatchers.IO) {
        val bitmap = renderCardToBitmap(card, resolveAvatar(card))
        val pngBytes = ByteArrayOutputStream().use { baos ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos)
            baos.toByteArray()
        }
        val jsonString = StCardCodec.toJsonString(card, pretty = false)
        val base64Json = android.util.Base64.encodeToString(
            jsonString.toByteArray(Charsets.UTF_8),
            android.util.Base64.NO_WRAP
        )
        var embedded = PngTextChunkCodec.embedTextChunk(pngBytes, PngTextChunkCodec.KEY_V2, base64Json)
        if (StSpec.fromWire(card.spec) == StSpec.V3) {
            embedded = PngTextChunkCodec.embedTextChunk(embedded, PngTextChunkCodec.KEY_V3, base64Json)
        }
        embedded
    }

    /**
     * 导出 JSON 文件（SillyTavern 线格式，无损）
     */
    private fun exportJson(card: CharacterCard, fileName: String): Uri {
        val jsonString = StCardCodec.toJsonString(card, pretty = true)
        val file = File(context.getExternalFilesDir(null), "exports/$fileName.json")
        file.parentFile?.mkdirs()
        file.writeText(jsonString, Charsets.UTF_8)
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    /**
     * 导出 PNG：渲染角色卡为图片 + 嵌入 JSON 元数据到 tEXt chunk
     * v2 卡嵌入 "chara"；v3 卡同时嵌入 "chara" 与 "ccv3"（SillyTavern 兼容）
     */
    private fun exportPngWithMetadata(card: CharacterCard, fileName: String): Uri {
        // 1. 渲染角色卡为 Bitmap
        val bitmap = renderCardToBitmap(card, kotlinx.coroutines.runBlocking { resolveAvatar(card) })

        // 2. Bitmap -> PNG 字节流
        val pngBytes = ByteArrayOutputStream().use { baos ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos)
            baos.toByteArray()
        }

        // 3. JSON -> Base64 -> 嵌入 tEXt 块
        val jsonString = StCardCodec.toJsonString(card, pretty = false)
        val base64Json = android.util.Base64.encodeToString(
            jsonString.toByteArray(Charsets.UTF_8),
            android.util.Base64.NO_WRAP
        )
        var embedded = PngTextChunkCodec.embedTextChunk(pngBytes, PngTextChunkCodec.KEY_V2, base64Json)
        if (StSpec.fromWire(card.spec) == StSpec.V3) {
            embedded = PngTextChunkCodec.embedTextChunk(embedded, PngTextChunkCodec.KEY_V3, base64Json)
        }

        // 4. 写入文件
        val file = File(context.getExternalFilesDir(null), "exports/$fileName.png")
        file.parentFile?.mkdirs()
        file.writeBytes(embedded)

        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    /**
     * 简化版角色卡渲染：用 Canvas 绘制封面、名称、描述、标签等
     */
    /** 头像解析：data URI 直接用；http(s) URL 下载转 data URI（失败返回原值由渲染器忽略） */
    private suspend fun resolveAvatar(card: CharacterCard): String? {
        val a = card.avatar ?: return null
        if (a.startsWith("data:image")) return a
        if (a.startsWith("http")) {
            return com.kirakira.kiracode.ui.common.CoverFetcher.fetchAsDataUri(a) ?: a
        }
        // 仓库恢复的工作卡头像常为绝对路径；相对 covers/ 路径按 warehouse 根目录解析
        val file = when {
            a.startsWith("/") -> File(a)
            a.startsWith("covers/") -> File(context.filesDir, "warehouse/$a")
            else -> null
        } ?: return null
        if (!file.isFile) return null
        val bytes = runCatching { file.readBytes() }.getOrNull() ?: return null
        val mime = when (file.extension.lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            else -> "image/png"
        }
        return "data:$mime;base64," +
            android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
    }

    private fun renderCardToBitmap(card: CharacterCard, avatarDataUri: String?): Bitmap {
        val width = 1080
        val height = 1920
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)

        // 有封面：封面铺满整图（等比放大、居中裁剪），不叠加任何文字；
        // JSON 仅通过 tEXt 元数据嵌入，不渲染到像素上。
        val cover = avatarDataUri?.let { dataUri ->
            runCatching {
                val b64 = dataUri.substringAfter("base64,")
                val imgBytes = android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
                android.graphics.BitmapFactory.decodeByteArray(imgBytes, 0, imgBytes.size)
            }.getOrNull()
        }
        if (cover != null) {
            canvas.drawColor(android.graphics.Color.BLACK)
            // 中心裁剪铺满
            val srcW: Float; val srcH: Float
            val scale = (width.toFloat() / cover.width).coerceAtLeast(height.toFloat() / cover.height)
            srcW = width / scale
            srcH = height / scale
            val srcLeft = (cover.width - srcW) / 2f
            val srcTop = (cover.height - srcH) / 2f
            val src = android.graphics.Rect(
                srcLeft.toInt(), srcTop.toInt(),
                (srcLeft + srcW).toInt(), (srcTop + srcH).toInt()
            )
            val dst = android.graphics.Rect(0, 0, width, height)
            canvas.drawBitmap(cover, src, dst, null)
            return bitmap
        }

        // 无封面：占位文字卡片
        canvas.drawColor(android.graphics.Color.WHITE)
        val titlePaint = android.graphics.Paint().apply {
            color = android.graphics.Color.BLACK
            textSize = 64f
            isAntiAlias = true
            isFakeBoldText = true
        }
        val bodyPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.DKGRAY
            textSize = 36f
            isAntiAlias = true
        }
        val labelPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.GRAY
            textSize = 28f
            isAntiAlias = true
            isFakeBoldText = true
        }
        val margin = 64f
        var y = 300f
        canvas.drawText("KiraCard Character Card", margin, y, labelPaint); y += 100
        canvas.drawText(card.name, margin, y, titlePaint); y += 120
        canvas.drawText("【标签】", margin, y, labelPaint); y += 50
        y = drawWrapped(canvas, card.tags.take(8).joinToString("、").ifBlank { "（无）" }, margin, y, width - 2 * margin, bodyPaint, 48f); y += 40
        canvas.drawText("【创建者】${card.creator}", margin, y, labelPaint)

        return bitmap
    }

    /**
     * 简易文字换行绘制
     */
    private fun drawWrapped(
        canvas: android.graphics.Canvas,
        text: String,
        x: Float,
        startY: Float,
        maxWidth: Float,
        paint: android.graphics.Paint,
        lineHeight: Float
    ): Float {
        var y = startY
        val paragraphs = text.split("\n")
        for (para in paragraphs) {
            val words = para.splitToSequence("").toList() // 按字符切（中文友好）
            val sb = StringBuilder()
            for (ch in words) {
                if (ch.isEmpty()) continue
                val tryLine = sb.toString() + ch
                if (paint.measureText(tryLine) > maxWidth) {
                    canvas.drawText(sb.toString(), x, y, paint)
                    y += lineHeight
                    sb.setLength(0)
                    sb.append(ch)
                } else {
                    sb.append(ch)
                }
            }
            if (sb.isNotEmpty()) {
                canvas.drawText(sb.toString(), x, y, paint)
                y += lineHeight
            }
        }
        return y
    }

}

/**
 * 渲染角色卡为 Bitmap 用例 (PNG 导出核心)
 * 使用 Canvas 绘制生成角色卡图片，并嵌入 JSON 元数据
 *
 * MVP 阶段：实际的 PNG 渲染与嵌入逻辑统一由 ExportCharacterCardUseCase 处理，
 * 本类保留为对外 API（便于测试 Mock 或未来替换为 Compose 离屏渲染实现）。
 */
class RenderCardToBitmapUseCase @Inject constructor() {

    /**
     * 将角色卡渲染为 Bitmap（简化版本，下方实际渲染方法在 ExportCharacterCardUseCase 中）
     * @param card 要渲染的角色卡
     * @param width 图片宽度
     * @param height 图片高度
     * @param includeAvatar 是否包含头像（MVP 忽略）
     * @return 渲染后的 Bitmap
     */
    suspend operator fun invoke(
        card: CharacterCard,
        width: Int = 1080,
        height: Int = 1920,
        includeAvatar: Boolean = true
    ): Result<Bitmap> = withContext(Dispatchers.Default) {
        runCatching {
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            canvas.drawColor(android.graphics.Color.WHITE)
            val paint = android.graphics.Paint().apply {
                color = android.graphics.Color.BLACK
                textSize = 48f
                isAntiAlias = true
            }
            canvas.drawText(card.name, 100f, 200f, paint)
            canvas.drawText("Generated by KiraCard", 100f, 300f, paint)
            bitmap
        }.recoverCatching { e ->
            throw ExportException("渲染 Bitmap 失败: ${e.message}", format = "PNG", cause = e)
        }
    }
}
