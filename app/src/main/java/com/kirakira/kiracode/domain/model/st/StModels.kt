package com.kirakira.kiracode.domain.model.st

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * SillyTavern 角色世界书（Character Book）条目。
 *
 * 对应 V2/V3 规范中 `character_book.entries[]` 的字段；
 * [raw] 保存该条目的完整原始 JSON，导出时合并，保证未知扩展字段不丢失。
 */
@Serializable
data class StBookEntry(
    /** 全局递增 ID（SillyTavern 世界书条目 uid） */
    val id: Long = 0,
    /** 主触发关键词 */
    val keys: List<String> = emptyList(),
    /** 次级触发关键词（v2 规范字段 secondary_keys） */
    val secondaryKeys: List<String> = emptyList(),
    /** 条目备注/标题 */
    val comment: String = "",
    /** 条目正文（注入到上下文的内容） */
    val content: String = "",
    /** 常驻条目（蓝灯，无视关键词始终注入） */
    val constant: Boolean = false,
    /** 选择性条目（需主+次关键词同时命中） */
    val selective: Boolean = true,
    /** 插入顺序（越小越靠前） */
    val insertionOrder: Int = 100,
    /** 是否启用 */
    val enabled: Boolean = true,
    /**
     * v2 规范位置："before_char" | "after_char"
     * （ST 扩展的数字位置存放在 [extensions] 中：0=before_char 1=after_char 2=AN 3=AT_DEPTH 等）
     */
    val position: String? = null,
    /** 是否使用正则匹配关键词（ST 扩展） */
    val caseSensitive: Boolean? = null,
    val priority: Int? = null,
    val depth: Int? = null,
    // ──────── V3 extensions 常用字段（新增，条目级建模） ────────
    /** 条目级扫描深度（覆盖书级设置） */
    val scanDepth: Int? = null,
    /** 整词匹配 */
    val matchWholeWords: Boolean? = null,
    /** 自动化 ID */
    val automationId: String? = null,
    /** 触发概率 0-100（ST 实际使用 0-100 刻度） */
    val probability: Int? = null,
    /** 启用概率触发 */
    val useProbability: Boolean? = null,
    /** 选择性逻辑（ST world_info_logic）：AND_ANY=0 / NOT_ALL=1 / NOT_ANY=2 / AND_ALL=3 */
    val selectiveLogic: Int? = null,
    /** 排除递归 */
    val excludeRecursion: Boolean? = null,
    /** 阻止递归 */
    val preventRecursion: Boolean? = null,
    /** 分组 */
    val group: String? = null,
    /** 覆盖分组默认设置 */
    val groupOverride: Boolean? = null,
    /** 分组权重 */
    val groupWeight: Int? = null,
    /** 使用分组评分 */
    val useGroupScoring: Boolean? = null,
    /** 条目级扩展字段（ST: probability/useProbability 等），导出时与 raw 合并 */
    val extensions: JsonObject = JsonObject(emptyMap()),
    /** 该条目的完整原始 JSON（无损保真用，仅内存） */
    @kotlinx.serialization.Transient
    val raw: JsonObject? = null
)

/**
 * SillyTavern 角色世界书（character_book）。
 */
@Serializable
data class StCharacterBook(
    val name: String = "",
    val description: String = "",
    val scanDepth: Int? = null,
    val tokenBudget: Int? = null,
    val recursiveScanning: Boolean? = null,
    val entries: List<StBookEntry> = emptyList(),
    /** 书级未知字段原始 JSON（无损保真用） */
    @kotlinx.serialization.Transient
    val raw: JsonObject? = null
) {
    val entryCount: Int get() = entries.size
}

/**
 * SillyTavern 正则脚本（extensions.regex_scripts[]）。
 * 用于对 AI 回复/用户输入做正则替换以格式化输出。
 */
@Serializable
data class StRegexScript(
    val id: String = java.util.UUID.randomUUID().toString(),
    val scriptName: String = "",
    val findRegex: String = "",
    val replaceString: String = "",
    val trimStrings: List<String> = emptyList(),
    /**
     * 作用位置：1=User Input 2=AI Output 3=Slash Command 5=World Info
     */
    val placement: List<Int> = listOf(2),
    val disabled: Boolean = false,
    val markdownOnly: Boolean = false,
    val promptOnly: Boolean = false,
    val runOnEdit: Boolean = true,
    /** 替换模式（ST substitute_find_regex）：0=NONE 1=RAW 2=ESCAPED */
    val substituteRegex: Int = 0,
    val minDepth: Int? = null,
    val maxDepth: Int? = null,
    /** 未知扩展字段原始 JSON（无损保真用） */
    @kotlinx.serialization.Transient
    val raw: JsonObject? = null
)

/** 正则脚本作用位置常量 */
object RegexPlacement {
    const val USER_INPUT = 1
    const val AI_OUTPUT = 2
    const val SLASH_COMMAND = 3
    const val WORLD_INFO = 5
    /** V3 时代新增：思考/推理输出 */
    const val REASONING = 6
}

/**
 * 角色卡规格版本。
 */
enum class StSpec(val wireName: String, val specVersion: String) {
    V1("chara_card_v1", "1.0"),
    V2("chara_card_v2", "2.0"),
    V3("chara_card_v3", "3.0");

    companion object {
        fun fromWire(name: String?): StSpec = when (name) {
            "chara_card_v3" -> V3
            "chara_card_v1" -> V1
            else -> V2
        }
    }
}
