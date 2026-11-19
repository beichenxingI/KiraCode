package com.kirakira.kiracode

import com.kirakira.kiracode.domain.usecase.ImportCharacterCardUseCase
import com.kirakira.kiracode.domain.util.PngTextChunkCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.CRC32

/**
 * PNG tEXt 块编解码测试。
 * 构造最小合法 PNG（签名 + IHDR + tEXt + IEND）验证读取/嵌入/去重。
 */
class PngTextChunkCodecTest {

    private fun chunk(type: String, data: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(intToBE(data.size))
        out.write(type.toByteArray(Charsets.US_ASCII))
        out.write(data)
        val crc = CRC32().apply { update(type.toByteArray(Charsets.US_ASCII)); update(data) }
        out.write(intToBE(crc.value.toInt()))
        return out.toByteArray()
    }

    private fun intToBE(v: Int): ByteArray = byteArrayOf(
        ((v shr 24) and 0xFF).toByte(),
        ((v shr 16) and 0xFF).toByte(),
        ((v shr 8) and 0xFF).toByte(),
        (v and 0xFF).toByte()
    )

    /** 最小 IHDR：宽高1、8bit、RGBA */
    private val ihdr = byteArrayOf(
        0, 0, 0, 1, 0, 0, 0, 1, 8, 6, 0, 0, 0
    )

    private fun buildPng(vararg extraChunks: ByteArray): ByteArray {
        val signature = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        val out = ByteArrayOutputStream()
        out.write(signature)
        out.write(chunk("IHDR", ihdr))
        extraChunks.forEach { out.write(it) }
        out.write(chunk("IEND", ByteArray(0)))
        return out.toByteArray()
    }

    private fun textChunkData(keyword: String, text: String): ByteArray {
        val k = keyword.toByteArray(Charsets.US_ASCII)
        val t = text.toByteArray(Charsets.ISO_8859_1)
        return k + byteArrayOf(0) + t
    }

    @Test
    fun `readTextChunks extracts keyword and content`() {
        val png = buildPng(chunk("tEXt", textChunkData("chara", "aGVsbG8=")))
        val chunks = PngTextChunkCodec.readTextChunks(png)
        assertEquals(1, chunks.size)
        assertEquals("chara", chunks[0].keyword)
        assertEquals("aGVsbG8=", chunks[0].text)
    }

    @Test
    fun `extractCardJson decodes base64 and prefers ccv3`() {
        val v2json = java.util.Base64.getEncoder()
            .encodeToString("""{"spec":"chara_card_v2"}""".toByteArray())
        val v3json = java.util.Base64.getEncoder()
            .encodeToString("""{"spec":"chara_card_v3"}""".toByteArray())

        val png = buildPng(
            chunk("tEXt", textChunkData("chara", v2json)),
            chunk("tEXt", textChunkData("ccv3", v3json))
        )
        val extracted = PngTextChunkCodec.extractCardJson(png)
        assertNotNull(extracted)
        assertTrue(extracted!!.contains("chara_card_v3"))
    }

    @Test
    fun `extractCardJson returns null without card chunks`() {
        val png = buildPng(chunk("tEXt", textChunkData("other", "data")))
        assertNull(PngTextChunkCodec.extractCardJson(png))
    }

    @Test
    fun `embedTextChunk inserts before IEND and read-back succeeds`() {
        val base64 = "eyJhIjoxfQ=="
        val png = buildPng()
        val embedded = PngTextChunkCodec.embedTextChunk(png, PngTextChunkCodec.KEY_V2, base64)

        // 长度应增加且仍以 IEND 结尾
        assertTrue(embedded.size > png.size)
        val chunks = PngTextChunkCodec.readTextChunks(embedded)
        assertEquals(1, chunks.size)
        assertEquals(base64, chunks[0].text)
    }

    @Test
    fun `embedTextChunk replaces existing same-keyword chunk`() {
        val png = buildPng(chunk("tEXt", textChunkData("chara", "old")))
        val embedded = PngTextChunkCodec.embedTextChunk(png, "chara", "new")
        val chunks = PngTextChunkCodec.readTextChunks(embedded)
        assertEquals(1, chunks.size)
        assertEquals("new", chunks[0].text)
    }

    @Test
    fun `rejects non-png input`() {
        val result = runCatching {
            PngTextChunkCodec.embedTextChunk("JFIF not png".toByteArray(), "chara", "x")
        }
        assertTrue(result.isFailure)
    }

    @Test
    fun `png import embeds png itself as avatar data uri`() {
        val json = """{"spec":"chara_card_v2","data":{"name":"Test"}}"""
        val base64 = java.util.Base64.getEncoder()
            .encodeToString(json.toByteArray(Charsets.UTF_8))
        val png = buildPng(chunk("tEXt", textChunkData("chara", base64)))

        val card = ImportCharacterCardUseCase().importFromPng(png).getOrThrow()

        assertTrue(card.avatar!!.startsWith("data:image/png;base64,"))
        assertEquals(
            "data:image/png;base64," + java.util.Base64.getEncoder().encodeToString(png),
            card.avatar
        )
    }
}
