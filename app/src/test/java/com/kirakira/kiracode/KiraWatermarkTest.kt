package com.kirakira.kiracode

import com.kirakira.kiracode.domain.agent.CardOperation
import com.kirakira.kiracode.domain.agent.CardOperationApplier
import com.kirakira.kiracode.domain.model.CharacterCard
import com.kirakira.kiracode.domain.model.KiraWatermark
import com.kirakira.kiracode.domain.model.st.StCardCodec
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * KiraCard 角色卡水印测试（隐形 + 明显，且不破坏 SillyTavern 规范）。
 */
class KiraWatermarkTest {

    @Test
    fun `apply injects invisible and visible watermark`() {
        val card = CharacterCard(name = "测试角色")
        val marked = KiraWatermark.apply(card)

        assertTrue(KiraWatermark.hasWatermark(marked))
        assertEquals("KiraCard", marked.creator)
        assertTrue(marked.creatorNotes.startsWith(KiraWatermark.VISIBLE_NOTE_PREFIX))
        assertTrue(marked.tags.contains(KiraWatermark.VISIBLE_TAG))

        val meta = KiraWatermark.kiraMetadata(marked)!!
        assertEquals("KiraCard", meta["generated_by"]?.jsonPrimitive?.contentOrNull)
        assertEquals("1.0.0", meta["version"]?.jsonPrimitive?.contentOrNull)
        assertNotNull(meta["timestamp"])
        assertEquals(KiraWatermark.APP_URL, meta["app"]?.jsonPrimitive?.contentOrNull)
    }

    @Test
    fun `apply is idempotent`() {
        val card = CharacterCard(name = "X")
        val once = KiraWatermark.apply(card)
        val twice = KiraWatermark.apply(once)

        assertEquals(once.creatorNotes, twice.creatorNotes)
        assertEquals(once.tags, twice.tags)
        assertEquals(once.extensions, twice.extensions)
    }

    @Test
    fun `apply preserves user-customized fields`() {
        val card = CharacterCard(name = "Y", creator = "自定义作者", tags = listOf("已有标签"))
        val marked = KiraWatermark.apply(card)

        assertEquals("自定义作者", marked.creator)
        assertEquals(listOf("已有标签", KiraWatermark.VISIBLE_TAG), marked.tags)
    }

    @Test
    fun `apply merges into existing extensions without overwriting unknown keys`() {
        val existingExt = JsonObject(mapOf("depth_prompt" to JsonObject(mapOf("key" to JsonPrimitive("value")))))
        val card = CharacterCard(name = "Z", extensions = existingExt)
        val marked = KiraWatermark.apply(card)

        val ext = marked.extensions as JsonObject
        assertTrue(ext.containsKey("depth_prompt"))
        assertTrue(ext.containsKey("kira_metadata"))
    }

    @Test
    fun `imported foreign card has no watermark and is not auto-watermarked`() {
        val foreignJson = """
            {"spec":"chara_card_v2","spec_version":"2.0","data":{
                "name":"外部角色","creator":"SomeoneElse","tags":["x"],
                "extensions":{"custom":"keep"}
            }}
        """.trimIndent()
        val card = StCardCodec.decode(foreignJson).getOrThrow()
        assertFalse(KiraWatermark.hasWatermark(card))
        assertEquals("SomeoneElse", card.creator)
    }

    @Test
    fun `watermark survives encode-decode roundtrip`() {
        val card = KiraWatermark.apply(CharacterCard(name = "往返"))
        val json = StCardCodec.toJsonString(card, pretty = false)
        val back = StCardCodec.decode(json).getOrThrow()

        assertTrue(KiraWatermark.hasWatermark(back))
        assertEquals("KiraCard", back.creator)
        assertTrue(back.creatorNotes.startsWith(KiraWatermark.VISIBLE_NOTE_PREFIX))
        assertTrue(back.tags.contains(KiraWatermark.VISIBLE_TAG))
    }

    @Test
    fun `card-update build path auto-watermarks the card`() {
        val base = CharacterCard(name = "")
        val ops = listOf(
            CardOperation.SetField("name", "新角"),
            CardOperation.SetField("description", "描述")
        )
        val updated = CardOperationApplier.apply(base, ops)

        assertTrue(KiraWatermark.hasWatermark(updated))
        assertEquals("KiraCard", updated.creator)
        assertTrue(updated.tags.contains(KiraWatermark.VISIBLE_TAG))
    }
}