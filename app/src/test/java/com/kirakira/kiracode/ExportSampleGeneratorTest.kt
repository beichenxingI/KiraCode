package com.kirakira.kiracode

import com.kirakira.kiracode.domain.model.CharacterCard
import com.kirakira.kiracode.domain.model.st.StBookEntry
import com.kirakira.kiracode.domain.model.st.StCardCodec
import com.kirakira.kiracode.domain.model.st.StCharacterBook
import com.kirakira.kiracode.domain.model.st.StRegexScript
import com.kirakira.kiracode.domain.util.PngTextChunkCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import java.util.zip.CRC32

/**
 * M7 导出样本生成器 + V3 规范符合性验证：
 * 1. 构建完整示例卡（艾伦·魔法师）→ 导出 JSON 写入 testdata/（真机测试样本）；
 * 2. 最小合法 PNG + chara/ccv3 双块嵌入 → 导出 PNG 写入 testdata/；
 * 3. 往返校验：导出产物必须可无损解析回领域模型（酒馆导入不报错的基础）。
 */
class ExportSampleGeneratorTest {

    /** 完整示例卡：覆盖 V3 全字段（含世界书 2 条 + 正则 1 条） */
    private fun buildSampleCard(): CharacterCard = com.kirakira.kiracode.domain.model.CharacterCard(
        name = "艾伦",
        description = "艾伦是一位生活在蒸汽朋克世界的年轻魔法师。银白色短发，琥珀色眼眸，" +
            "常穿深棕色皮质长外套，腰间挂着一枚会发光的黄铜怀表。擅长火焰魔法，" +
            "但更喜欢用机械装置放大法术威力。表面玩世不恭，实则观察入微，" +
            "对蒸汽机械与古代魔法阵都有极深的研究。白天在工坊修理各式奇物，" +
            "夜晚则是城市传说中行侠仗义的「焰鸦」。",
        personality = "傲娇、玩世不恭、重情义、观察入微；被夸奖时会别过头去，嘴上说着「才不是为了你」",
        scenario = "{{char}}的工坊里，蒸汽管道在墙上蜿蜒，桌上摆着待修的黄铜鸟。窗外传来雾都特有的汽笛声。",
        firstMessage = "嘿，叫我艾伦就好。魔法？嗯…算是会一点吧。\n\n*他随手打了个响指，指尖跳出一小簇火苗，又立刻捏灭。*\n\n「找我修东西，还是找麻烦？」",
        exampleDialogue = "{{user}}: 你的魔法是跟谁学的？\n{{char}}: *擦着手上的机油，头也不抬*自学。书上都有，笨。\n\n{{user}}: 焰鸦是你吧。\n{{char}}: *怀表啪地弹开*……证据呢？没有证据的事，不要乱说。",
        creatorNotes = "KiraCard V2/V3 导出测试样本：用于 SillyTavern 导入试聊验证。",
        systemPrompt = "",
        postHistoryInstructions = "",
        creator = "KiraCard",
        version = "1.0",
        tags = listOf("魔法", "傲娇", "蒸汽朋克", "男性"),
        alternateGreetings = listOf("深夜的工坊只亮着一盏灯，艾伦正对着一堆齿轮皱眉。「来的正好，帮我按住这个。」"),
        avatar = null,
        spec = "chara_card_v3",
        specVersion = "3.0",
        characterBook = StCharacterBook(
            entries = listOf(
                StBookEntry(
                    id = 0,
                    keys = listOf("童年", "小时候", "家人"),
                    content = "艾伦生于没落贵族家庭，幼年家道中落。父亲是机械匠，母亲早逝；" +
                        "他在父亲的工作坊里长大，十岁那年第一次点燃魔法火苗，烧掉了半个书房。" +
                        "父亲的工具箱如今仍挂在他的腰间。",
                    comment = "童年经历",
                    constant = false,
                    selective = true,
                    insertionOrder = 100,
                    enabled = true,
                    position = "before_char"
                ),
                StBookEntry(
                    id = 1,
                    keys = listOf("脆弱", "哭", "难过"),
                    content = "艾伦在深夜独处时最容易卸下防备；被信任的人背叛时会沉默很久，" +
                        "然后用一句「我就知道」带过。他从不当面流泪，只会擦拭那枚黄铜怀表。",
                    comment = "脆弱触发条件",
                    constant = false,
                    selective = true,
                    insertionOrder = 100,
                    enabled = true,
                    position = "before_char"
                )
            )
        ),
        regexScripts = listOf(
            StRegexScript(
                id = UUID.randomUUID().toString(),
                scriptName = "去除思考标签",
                findRegex = "<think>[\\s\\S]*?",
                replaceString = "",
                placement = listOf(2)
            )
        )
    )

    // ==================== V3 规范符合性 ====================

    @Test
    fun `导出JSON符合V3规范-字段齐全可往返`() {
        val card = buildSampleCard()
        val json = StCardCodec.toJsonString(card, pretty = true)

        assertTrue("spec 必须为 chara_card_v3", json.contains("\"spec\": \"chara_card_v3\""))
        assertTrue("spec_version 必须为 3.0", json.contains("\"spec_version\": \"3.0\""))

        val decoded = StCardCodec.decode(json)
        assertTrue("导出的 JSON 必须可无损解析", decoded.isSuccess)
        val back = decoded.getOrThrow()
        assertEquals("name 往返一致", "艾伦", back.name)
        assertEquals("description 往返一致", card.description, back.description)
        assertEquals("personality 往返一致", card.personality, back.personality)
        assertEquals("first_mes 往返一致", card.firstMessage, back.firstMessage)
        assertEquals("mes_example 往返一致", card.exampleDialogue, back.exampleDialogue)
        assertEquals("scenario 往返一致", card.scenario, back.scenario)
        assertEquals("tags 往返一致", card.tags, back.tags)
        assertEquals("alternate_greetings 往返一致", card.alternateGreetings, back.alternateGreetings)
        assertEquals("世界书条目数一致", 2, back.characterBook?.entries?.size)
        assertEquals("正则条数一致", 1, back.regexScripts.size)
        assertEquals("正则内容一致", card.regexScripts.first().findRegex, back.regexScripts.first().findRegex)

        // 触发式世界书：keys/constant 字段正确（酒馆触发验证的基础）
        val childhood = back.characterBook!!.entries.first { it.comment == "童年经历" }
        assertTrue("触发词包含'童年'", childhood.keys.contains("童年"))
        assertTrue("触发式条目 constant=false", !childhood.constant)
        assertTrue("条目内容非空（注入验证基础）", childhood.content.isNotBlank())

        // 写入 testdata/ 作为真机测试样本
        writeSample("export_eilen_v3.json", json)
    }

    @Test
    fun `导出PNG嵌入chara与ccv3双块-可解码往返`() {
        val card = buildSampleCard()
        val json = StCardCodec.toJsonString(card, pretty = false)
        val base64 = java.util.Base64.getEncoder().encodeToString(json.toByteArray(Charsets.UTF_8))

        // 最小合法 PNG + 双块（真机渲染路径由 Canvas 铺满；此处验证元数据块）
        var png = buildMinimalPng()
        png = PngTextChunkCodec.embedTextChunk(png, PngTextChunkCodec.KEY_V2, base64)
        png = PngTextChunkCodec.embedTextChunk(png, PngTextChunkCodec.KEY_V3, base64)

        // PNG 结构合法（签名）
        assertEquals("PNG 签名正确", 0x89, png[0].toInt() and 0xFF)
        assertEquals('P'.code.toByte(), png[1])

        // 解码往返：ccv3 优先读取 → JSON 完整还原
        val chunks = PngTextChunkCodec.readTextChunks(png)
        assertTrue("必须包含 chara 块", chunks.any { it.keyword == "chara" })
        assertTrue("必须包含 ccv3 块", chunks.any { it.keyword == "ccv3" })

        val decodedB64 = chunks.first { it.keyword == "ccv3" }.text
        val decodedJson = String(java.util.Base64.getDecoder().decode(decodedB64), Charsets.UTF_8)
        val back = StCardCodec.decode(decodedJson)
        assertTrue("PNG 内嵌 JSON 必须可无损解析", back.isSuccess)
        assertEquals("PNG 内嵌卡 name 一致", "艾伦", back.getOrThrow().name)
        assertEquals("PNG 内嵌卡世界书一致", 2, back.getOrThrow().characterBook?.entries?.size)

        // 写入 testdata/ 作为真机测试样本
        writeSample("export_eilen_v3.png", png)
    }

    // ==================== 工具 ====================

    /** 最小合法 PNG（签名 + IHDR + IEND），1x1 RGBA */
    private fun buildMinimalPng(): ByteArray {
        val ihdr = byteArrayOf(0, 0, 0, 1, 0, 0, 0, 1, 8, 6, 0, 0, 0)
        val signature = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        val out = ByteArrayOutputStream()
        out.write(signature)
        out.write(chunk("IHDR", ihdr))
        out.write(chunk("IEND", ByteArray(0)))
        return out.toByteArray()
    }

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

    /** 写样本文件（testdata/，相对仓库根） */
    private fun writeSample(name: String, content: String) {
        val dir = File("testdata")
        if (!dir.exists()) dir.mkdirs()
        File(dir, name).writeText(content, Charsets.UTF_8)
        println("✅ 样本已写入: ${File(dir, name).absolutePath}")
    }

    private fun writeSample(name: String, bytes: ByteArray) {
        val dir = File("testdata")
        if (!dir.exists()) dir.mkdirs()
        File(dir, name).writeBytes(bytes)
        println("✅ 样本已写入: ${File(dir, name).absolutePath}")
    }
}
