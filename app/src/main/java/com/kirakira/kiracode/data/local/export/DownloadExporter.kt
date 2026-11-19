package com.kirakira.kiracode.data.local.export

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Download 目录导出器（V3.0 成品区）
 *
 * - API 29+：MediaStore Downloads 插入（无需权限）
 * - API 26-28：公共 Downloads 目录直接写文件
 *
 * @return 保存后的展示路径（相对描述），失败抛异常
 */
object DownloadExporter {

    /**
     * 保存字节到公共 Download 目录。
     */
    suspend fun save(
        context: Context,
        fileName: String,
        mimeType: String,
        bytes: ByteArray
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, mimeType)
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: error("无法创建下载条目")
                resolver.openOutputStream(uri)?.use { it.write(bytes) }
                    ?: error("无法写入下载流")
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                "Download/$fileName"
            } else {
                @Suppress("DEPRECATION")
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!dir.exists()) dir.mkdirs()
                var target = File(dir, fileName)
                // 重名追加序号，避免覆盖
                var n = 1
                while (target.exists()) {
                    val dot = fileName.lastIndexOf('.')
                    target = if (dot > 0) {
                        File(dir, "${fileName.substring(0, dot)}($n).${fileName.substring(dot + 1)}")
                    } else File(dir, "$fileName($n)")
                    n++
                }
                target.writeBytes(bytes)
                "Download/${target.name}"
            }
        }
    }

    /** 清理文件名中的非法字符 */
    fun sanitizeFileName(name: String): String =
        name.replace(Regex("""[\\/:*?"<>|\r\n]"""), "_").take(80).ifBlank { "card" }
}
