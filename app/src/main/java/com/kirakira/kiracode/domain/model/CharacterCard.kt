package com.kirakira.kiracode.domain.model

import com.kirakira.kiracode.domain.model.st.StCharacterBook
import com.kirakira.kiracode.domain.model.st.StRegexScript
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import java.util.UUID

/**
 * SillyTavern 角色卡核心数据模型
 * 对应 SillyTavern v2/v3 格式规范
 *
 * 无损保真：导入时 [rawData] 保存线格式 data 对象原文，
 * 导出时由 StCardCodec 将规范字段覆盖回 rawData，未知扩展字段原样保留。
 *
 * @see <a href="https://github.com/SillyTavern/SillyTavern/blob/release/docs/CHARACTER_CARD_SPEC.md">Character Card Spec</a>
 */
@Serializable
data class CharacterCard(
    // 基础信息
    val name: String,
    val description: String = "",      // 角色设定/人设
    val personality: String = "",      // 性格
    val firstMessage: String = "",     // 首条消息 (first_mes)
    val creator: String = "KiraCard",
    val tags: List<String> = emptyList(),

    // 示例对话
    val exampleDialogue: String = "",  // mes_example，{{user}}/{{char}} 格式，<START> 分隔

    // 世界观/背景
    val scenario: String = "",
    val depth: Int = 0,                // 对话深度（本地辅助字段）

    // 扩展字段 (SillyTavern v2/v3 兼容)
    val creatorNotes: String = "",     // creator_notes
    val systemPrompt: String = "",     // system_prompt
    val postHistoryInstructions: String = "", // post_history_instructions
    val alternateGreetings: List<String> = emptyList(), // alternate_greetings
    val nickname: String? = null,      // v3 可选昵称

    // 角色世界书 (character_book)
    val characterBook: StCharacterBook? = null,

    // 角色正则脚本 (extensions.regex_scripts)
    val regexScripts: List<StRegexScript> = emptyList(),

    // 元数据
    val uuid: String = UUID.randomUUID().toString(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val version: String = "main",      // character_version

    // 头像 (Base64 Data URI 或 URL)
    val avatar: String? = null,

    // 角色卡规格版本: chara_card_v2 / chara_card_v3
    val spec: String = "chara_card_v2",
    val specVersion: String = "2.0",

    // 扩展数据（保留未知扩展字段；编解码时与 rawData.extensions 合并）
    val extensions: JsonElement? = null,

    /**
     * 线格式 data 对象原文（仅内存传递，不参与 kotlinx 序列化；
     * 持久化由 Room 的 rawJson 列承担）
     */
    @kotlinx.serialization.Transient
    val rawData: kotlinx.serialization.json.JsonObject? = null
) {
    /** 创建一个新的 CharacterCard 实例，更新 updatedAt 时间戳 */
    fun copyWithUpdatedTimestamp(): CharacterCard {
        return this.copy(updatedAt = System.currentTimeMillis())
    }

    /** 验证必填字段是否为空（SillyTavern 规范：name 必填；description 可为空，缺省用 first_mes/scenario 兜底） */
    fun validateRequiredFields(): List<String> {
        val errors = mutableListOf<String>()
        if (name.isBlank()) errors.add("角色名称不能为空")
        return errors
    }

    /** 判断必填字段是否全部有效 */
    fun isValid(): Boolean = validateRequiredFields().isEmpty()

    /** 获取用于显示的简短描述 */
    fun getShortDescription(maxLength: Int = 100): String {
        return if (description.length <= maxLength) description
        else "${description.substring(0, maxLength)}..."
    }
}
