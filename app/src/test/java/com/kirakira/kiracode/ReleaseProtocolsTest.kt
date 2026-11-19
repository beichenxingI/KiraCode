package com.kirakira.kiracode

import com.kirakira.kiracode.data.remote.AnnouncementClient
import com.kirakira.kiracode.domain.model.Persona
import com.kirakira.kiracode.domain.model.PersonaCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 正式版 V1.0.0 协议测试：公告解析、人格编解码。
 */
class ReleaseProtocolsTest {

    @Test
    fun `announcement json parses all fields`() {
        val body = """
            {
              "version": "1.2.0",
              "title": "KiraKira 新版本发布",
              "content": "修复了若干问题，优化体验。",
              "imageUrl": "https://example.com/banner.png",
              "downloadUrl": "https://example.com/kirakira.apk",
              "forceUpdate": true,
              "minAppVersion": "1.0.0"
            }
        """.trimIndent()
        val a = AnnouncementClient.parseAnnouncement(body)
        assertEquals("1.2.0", a.version)
        assertEquals("KiraKira 新版本发布", a.title)
        assertTrue(a.forceUpdate)
        assertEquals("1.0.0", a.minAppVersion)
        assertEquals("https://example.com/kirakira.apk", a.downloadUrl)
    }

    @Test
    fun `announcement with missing optional fields defaults`() {
        val body = """{"version":"2.0","title":"t","content":"c"}"""
        val a = AnnouncementClient.parseAnnouncement(body)
        assertEquals("", a.downloadUrl)
        assertEquals(false, a.forceUpdate)
        assertEquals(null, a.imageUrl)
    }

    @Test
    fun `persona codec roundtrip`() {
        val list = listOf(
            Persona(name = "赛博朋克制卡师", description = "霓虹与义体", prompt = "你的文风冷峻…"),
            Persona(name = "古风制卡师", prompt = "你惯用留白…")
        )
        val json = PersonaCodec.encodeList(list)
        val decoded = PersonaCodec.decodeList(json)
        assertEquals(list, decoded)
    }

    @Test
    fun `persona codec tolerates garbage`() {
        assertTrue(PersonaCodec.decodeList(null).isEmpty())
        assertTrue(PersonaCodec.decodeList("").isEmpty())
        assertTrue(PersonaCodec.decodeList("not-json").isEmpty())
        assertEquals("[]", PersonaCodec.encodeList(emptyList()))
    }
}
