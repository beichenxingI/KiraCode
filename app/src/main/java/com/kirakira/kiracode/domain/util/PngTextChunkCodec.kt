package com.kirakira.kiracode.domain.util

import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.CRC32

/**
 * PNG tEXt 文本块编解码器（纯 Kotlin 实现，便于单元测试）。
 *
 * SillyTavern 角色卡 PNG 通过 tEXt chunk 内嵌 Base64 编码的 JSON：
 * - 关键字 "chara"：v2 卡
 * - 关键字 "ccv3"：v3 卡
 *
 * PNG 结构：8 字节签名 + chunk 序列；每个 chunk = length(4,BE) + type(4) + data(length) + crc(4,BE)
 */
object PngTextChunkCodec {

    const val KEY_V2 = "chara"
    const val KEY_V3 = "ccv3"

    private val SIGNATURE = byteArrayOf(
        0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    )

    /** PNG 文件本身作为封面 data URI（供卡片库/会话头像使用） */
    fun toDataUri(pngBytes: ByteArray): String =
        "data:image/png;base64," + Base64.getEncoder().encodeToString(pngBytes)

    /** 单个文本块 */
    data class TextChunk(val keyword: String, val text: String)

    /**
     * 从 PNG 字节流读取全部 tEXt 块。
     * @return 关键字到内容的列表；非 PNG 或无文本块返回空列表
     */
    fun readTextChunks(pngBytes: ByteArray): List<TextChunk> {
        if (pngBytes.size < 8 || !pngBytes.copyOfRange(0, 8).contentEquals(SIGNATURE)) {
            return emptyList()
        }
        val chunks = mutableListOf<TextChunk>()
        var pos = 8
        while (pos + 12 <= pngBytes.size) {
            val length = readIntBE(pngBytes, pos)
            val type = String(pngBytes, pos + 4, 4, Charsets.US_ASCII)
            val dataStart = pos + 8
            if (length < 0 || dataStart + length + 4 > pngBytes.size) break
            if (type == "tEXt") {
                val data = pngBytes.copyOfRange(dataStart, dataStart + length)
                val sep = data.indexOf(0)
                if (sep > 0) {
                    val keyword = String(data, 0, sep, Charsets.US_ASCII)
                    val text = String(data, sep + 1, data.size - sep - 1, Charsets.ISO_8859_1)
                    chunks.add(TextChunk(keyword, text))
                }
            }
            pos = dataStart + length + 4
        }
        return chunks
    }

    /**
     * 提取角色卡 JSON（Base64 解码）。
     * 优先 ccv3（v3），其次 chara（v2）。
     * @return JSON 文本或 null
     */
    fun extractCardJson(pngBytes: ByteArray): String? {
        val chunks = readTextChunks(pngBytes)
        val order = listOf(KEY_V3, KEY_V2)
        for (key in order) {
            val chunk = chunks.firstOrNull { it.keyword == key } ?: continue
            return try {
                String(java.util.Base64.getDecoder().decode(chunk.text), Charsets.UTF_8)
            } catch (e: IllegalArgumentException) {
                // 某些导出工具不 Base64 编码，尝试原文
                chunk.text
            }
        }
        return null
    }

    /**
     * 向 PNG 字节流 IEND 之前插入一个 tEXt 块。
     * 若已存在同关键字块则先移除再插入（避免重复）。
     *
     * @param keyword tEXt 关键字（ASCII，如 "chara"）
     * @param text 内容（Base64 后为 ASCII 安全）
     */
    fun embedTextChunk(pngBytes: ByteArray, keyword: String, text: String): ByteArray {
        require(pngBytes.size >= 8 && pngBytes.copyOfRange(0, 8).contentEquals(SIGNATURE)) {
            "不是有效的 PNG 文件"
        }
        // 移除同名旧块
        val stripped = stripTextChunks(pngBytes, keyword)

        // 构造新块：data = keyword + 0x00 + text
        val keywordBytes = keyword.toByteArray(Charsets.US_ASCII)
        val textBytes = text.toByteArray(Charsets.ISO_8859_1)
        val chunkData = ByteArrayOutputStream().apply {
            write(keywordBytes); write(0); write(textBytes)
        }.toByteArray()

        val chunk = ByteArrayOutputStream().apply {
            write(intToBE(chunkData.size))
            write(byteArrayOf('t'.code.toByte(), 'E'.code.toByte(), 'X'.code.toByte(), 't'.code.toByte()))
            write(chunkData)
            val crc = CRC32().apply {
                update(byteArrayOf('t'.code.toByte(), 'E'.code.toByte(), 'X'.code.toByte(), 't'.code.toByte()))
                update(chunkData)
            }
            write(intToBE(crc.value.toInt()))
        }.toByteArray()

        // 找到 IEND 起始位置，插入其前
        val iendStart = findIendStart(stripped) ?: return stripped + chunk
        val result = ByteArray(iendStart + chunk.size + (stripped.size - iendStart))
        System.arraycopy(stripped, 0, result, 0, iendStart)
        System.arraycopy(chunk, 0, result, iendStart, chunk.size)
        System.arraycopy(stripped, iendStart, result, iendStart + chunk.size, stripped.size - iendStart)
        return result
    }

    /** 移除指定关键字的全部 tEXt 块，返回新字节流 */
    private fun stripTextChunks(pngBytes: ByteArray, keyword: String): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(pngBytes, 0, 8)
        var pos = 8
        while (pos + 12 <= pngBytes.size) {
            val length = readIntBE(pngBytes, pos)
            val type = String(pngBytes, pos + 4, 4, Charsets.US_ASCII)
            val total = 12 + length
            if (length < 0 || pos + total > pngBytes.size) break
            if (type == "tEXt") {
                val data = pngBytes.copyOfRange(pos + 8, pos + 8 + length)
                val sep = data.indexOf(0)
                val kw = if (sep > 0) String(data, 0, sep, Charsets.US_ASCII) else ""
                if (kw == keyword) { pos += total; continue } // 跳过即删除
            }
            out.write(pngBytes, pos, total)
            pos += total
        }
        return out.toByteArray()
    }

    private fun findIendStart(data: ByteArray): Int? {
        var pos = 8
        while (pos + 12 <= data.size) {
            val length = readIntBE(data, pos)
            if (length < 0 || pos + 12 + length > data.size) return null
            if (String(data, pos + 4, 4, Charsets.US_ASCII) == "IEND") return pos
            pos += 12 + length
        }
        return null
    }

    private fun readIntBE(data: ByteArray, offset: Int): Int =
        ((data[offset].toInt() and 0xFF) shl 24) or
        ((data[offset + 1].toInt() and 0xFF) shl 16) or
        ((data[offset + 2].toInt() and 0xFF) shl 8) or
        (data[offset + 3].toInt() and 0xFF)

    private fun intToBE(value: Int): ByteArray = byteArrayOf(
        ((value shr 24) and 0xFF).toByte(),
        ((value shr 16) and 0xFF).toByte(),
        ((value shr 8) and 0xFF).toByte(),
        (value and 0xFF).toByte()
    )
}
