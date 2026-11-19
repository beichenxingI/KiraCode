package com.kirakira.kiracode.domain.usecase

import com.kirakira.kiracode.domain.exception.ParseException
import com.kirakira.kiracode.domain.exception.ValidationException
import com.kirakira.kiracode.domain.model.CharacterCard
import com.kirakira.kiracode.domain.model.st.StCardCodec
import com.kirakira.kiracode.domain.util.PngTextChunkCodec

/**
 * 导入角色卡用例（V2.0）
 *
 * 支持三种来源：
 * 1. JSON 文本 —— 自动识别 SillyTavern v1（平铺）/ v2 / v3（{spec, data} 包裹）；
 * 2. PNG 字节流 —— 读取 tEXt 块（ccv3 优先、chara 次之）内嵌的 Base64 JSON；
 * 3. 已解析的 JsonObject。
 *
 * 全部字段无损保真：未知扩展字段存入 [CharacterCard.rawData]，导出时原样回写。
 */
class ImportCharacterCardUseCase {

    /**
     * 从 JSON 文本导入
     */
    operator fun invoke(jsonText: String): Result<CharacterCard> {
        if (jsonText.isBlank()) {
            return Result.failure(ParseException("导入内容为空", rawInput = ""))
        }
        return StCardCodec.decode(jsonText.trim()).recoverCatching { e ->
            throw ParseException(
                "JSON 解析失败: ${e.message}",
                rawInput = jsonText.take(500),
                cause = e
            )
        }.mapCatching { card ->
            val errors = card.validateRequiredFields()
            if (errors.isNotEmpty()) {
                throw ValidationException(
                    "角色卡字段校验失败: ${errors.joinToString(", ")}",
                    fieldErrors = errors.associateWith { it }
                )
            }
            card
        }
    }

    /**
     * 从 PNG 文件字节导入（读取 tEXt 块中的角色卡 JSON）
     */
    fun importFromPng(pngBytes: ByteArray): Result<CharacterCard> = runCatching {
        val jsonText = PngTextChunkCodec.extractCardJson(pngBytes)
            ?: throw ParseException("PNG 中未找到角色卡数据（缺少 chara/ccv3 文本块）", rawInput = "")
        val card = StCardCodec.decode(jsonText).getOrElse { e ->
            throw ParseException(
                "PNG 内嵌 JSON 解析失败: ${e.message}",
                rawInput = jsonText.take(500),
                cause = e
            )
        }
        if (card.avatar.isNullOrBlank()) {
            card.copy(avatar = PngTextChunkCodec.toDataUri(pngBytes))
        } else {
            card
        }
    }

    /**
     * 智能导入：根据文件名/魔数自动判断 PNG 或 JSON
     */
    fun importAuto(fileName: String?, bytes: ByteArray): Result<CharacterCard> {
        val isPng = fileName?.endsWith(".png", ignoreCase = true) == true ||
                (bytes.size >= 8 && bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte())
        return if (isPng) importFromPng(bytes) else invoke(bytes.toString(Charsets.UTF_8))
    }
}
