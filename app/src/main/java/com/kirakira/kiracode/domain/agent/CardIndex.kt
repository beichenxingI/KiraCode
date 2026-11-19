package com.kirakira.kiracode.domain.agent

import com.kirakira.kiracode.domain.model.CharacterCard
import com.kirakira.kiracode.domain.model.st.StBookEntry
import com.kirakira.kiracode.domain.model.st.StCardCodec
import com.kirakira.kiracode.domain.model.st.StRegexScript
import com.kirakira.kiracode.domain.usecase.TokenEstimator
import java.util.UUID

/**
 * 角色卡按需读取（大卡上下文治理）。
 *
 * 问题：旧注入策略每轮把完整卡 JSON（含全部世界书/正则正文）塞进 system，
 * 巨型卡（10 万字级）直接撞上下文上限。
 *
 * 方案：按 token 估算分三档——
 * - SMALL：低于安全阈值 → 保持旧行为（完整 JSON 注入）；
 * - MEDIUM：注入摘要 + 索引；
 * - LARGE：只注入摘要 + 索引 + 按需查询工具（query_card_entries / get_card_entry）。
 *
 * 条目定位：世界书用稳定 uid（导入卡缺失/重复时先修复再建索引，禁止用列表位置当长期 ID）；
 * 正则用稳定字符串 id（缺失时补 UUID）。
 */
enum class CardScale {
    /** 小卡：完整注入（旧行为） */
    SMALL,
    /** 中卡：摘要 + 索引 */
    MEDIUM,
    /** 大卡：摘要 + 索引 + 按需查询 */
    LARGE
}

/** 世界书索引条目（供 AI 检索定位；content 全文不注入） */
data class WorldBookIndexEntry(
    val id: Long,
    val title: String,
    val keywords: List<String>,
    val contentLength: Int,
    val summary: String,
    val order: Int,
    val enabled: Boolean
)

/** 正则索引条目（find/replace 全文不注入） */
data class RegexIndexEntry(
    val id: String,
    val name: String,
    val patternSummary: String,
    val replacementSummary: String,
    val contentLength: Int,
    val enabled: Boolean
)

object CardIndex {

    /** SMALL 上限（token）：低于此值完整注入（≈4000 汉字的卡） */
    const val SMALL_THRESHOLD_TOKENS = 2500
    /** MEDIUM 上限（token）：超过此值为 LARGE */
    const val LARGE_THRESHOLD_TOKENS = 8000
    /** 索引条目摘要长度 */
    private const val INDEX_SUMMARY_CHARS = 80
    /** get_card_entry 返回内容上限（超长截断，保护主上下文） */
    const val ENTRY_CONTENT_MAX = 6000
    /** 查询结果条目数上限 */
    private const val QUERY_LIMIT_DEFAULT = 10
    private const val QUERY_LIMIT_MAX = 20

    // ──────────────── 稳定 ID ────────────────

    /**
     * 修复缺失/重复的世界书条目 uid 与正则脚本 id（不改动其它内容）。
     * 导入卡可能整批 uid=0 或重复——查询/修改工具无法定位，必须先修复。
     * 修复语义：id<=0 或与更早条目重复的条目，按顺序赋 maxId+1 起的递增 id。
     */
    fun ensureStableEntryIds(card: CharacterCard): CharacterCard {
        var changed = false

        val book = card.characterBook
        val repairedEntries: List<StBookEntry>? = book?.let { b ->
            var nextId = (b.entries.maxOfOrNull { it.id } ?: -1L) + 1
            val seen = mutableSetOf<Long>()
            b.entries.map { entry ->
                if (entry.id <= 0L || !seen.add(entry.id)) {
                    changed = true
                    val assigned = nextId++
                    entry.copy(id = assigned).also { seen.add(assigned) }
                } else entry
            }
        }

        val regexSeen = mutableSetOf<String>()
        val repairedRegex = card.regexScripts.map { s ->
            if (s.id.isBlank() || !regexSeen.add(s.id)) {
                changed = true
                val nid = UUID.randomUUID().toString()
                regexSeen.add(nid)
                s.copy(id = nid)
            } else s
        }

        if (!changed) return card
        return card.copy(
            characterBook = book?.copy(entries = repairedEntries ?: book.entries),
            regexScripts = repairedRegex
        )
    }

    // ──────────────── 大小分级 ────────────────

    /** 全卡 JSON 的 token 估算（与注入体积同源，阈值统一由 TokenEstimator 计算） */
    fun estimateCardTokens(card: CharacterCard): Int =
        TokenEstimator.estimateTokens(StCardCodec.toJsonString(card, pretty = false))

    fun classify(card: CharacterCard): CardScale {
        val tokens = estimateCardTokens(card)
        return when {
            tokens < SMALL_THRESHOLD_TOKENS -> CardScale.SMALL
            tokens < LARGE_THRESHOLD_TOKENS -> CardScale.MEDIUM
            else -> CardScale.LARGE
        }
    }

    // ──────────────── 索引构建 ────────────────

    fun buildWorldBookIndex(card: CharacterCard): List<WorldBookIndexEntry> =
        card.characterBook?.entries?.mapIndexed { i, e ->
            WorldBookIndexEntry(
                id = e.id,
                title = e.comment.ifBlank { "(无标题)" },
                keywords = e.keys,
                contentLength = e.content.length,
                summary = e.content.take(INDEX_SUMMARY_CHARS),
                order = i + 1,
                enabled = e.enabled
            )
        }.orEmpty()

    fun buildRegexIndex(card: CharacterCard): List<RegexIndexEntry> =
        card.regexScripts.map { s ->
            RegexIndexEntry(
                id = s.id,
                name = s.scriptName.ifBlank { "(未命名)" },
                patternSummary = s.findRegex.take(INDEX_SUMMARY_CHARS),
                replacementSummary = s.replaceString.take(40),
                contentLength = s.findRegex.length + s.replaceString.length,
                enabled = !s.disabled
            )
        }

    /**
     * 按需读取段的完整 system 注入文本（MEDIUM/LARGE 用，替代完整 JSON）：
     * 关键字段摘要 + 世界书索引 + 正则索引 + 查询/修改工具说明。
     */
    fun buildOnDemandSection(card: CharacterCard): String = buildString {
        appendLine("当前卡过大，完整 JSON 不注入上下文。以下是摘要与索引——")
        appendLine("需要读取具体条目全文时，先 query_card_entries 检索，再 get_card_entry 读取；")
        appendLine("修改时用既有工具（update_card_book_update 的 uid 即索引中的 #ID）。")
        appendLine()
        appendLine("【基本信息摘要】")
        appendLine("角色名：${card.name}")
        if (card.description.isNotBlank()) appendLine("描述（前500字）：${card.description.take(500)}")
        if (card.personality.isNotBlank()) appendLine("性格（前300字）：${card.personality.take(300)}")
        if (card.scenario.isNotBlank()) appendLine("场景（前300字）：${card.scenario.take(300)}")
        if (card.firstMessage.isNotBlank()) appendLine("开场白（前200字）：${card.firstMessage.take(200)}")

        val wb = buildWorldBookIndex(card)
        if (wb.isEmpty()) {
            appendLine()
            appendLine("【世界书索引】（无条目）")
        } else {
            appendLine()
            appendLine("【世界书索引】共 ${wb.size} 条（正文不在此注入，用 get_card_entry(kind=worldbook, entry_id=#ID) 读取）：")
            wb.forEach { e ->
                appendLine("- #${e.id}「${e.title}」（触发词：${e.keywords.joinToString("|").ifBlank { "无" }}，" +
                        "${e.contentLength}字，${if (e.enabled) "启用" else "停用"}）：${e.summary}")
            }
        }

        val rx = buildRegexIndex(card)
        if (rx.isNotEmpty()) {
            appendLine()
            appendLine("【正则索引】共 ${rx.size} 条（pattern 全文不注入，用 get_card_entry(kind=regex, entry_id=<id>) 读取）：")
            rx.forEach { r ->
                appendLine("- [${r.id.take(8)}…] ${r.name}（${r.contentLength}字，${if (r.enabled) "启用" else "禁用"}）：${r.patternSummary}")
            }
        }
    }.trim()

    // ──────────────── 查询工具（纯函数，供 AgentController 调用） ────────────────

    /** 查询参数（function calling 解析产物） */
    data class QuerySpec(
        val kind: String,          // worldbook | regex | all
        val keywords: List<String>,
        val limit: Int
    )

    /**
     * 检索条目：按关键词匹配（keys/comment/scriptName/content 子串，忽略大小写）；
     * 关键词为空 = 列出索引。返回面向模型的观测文本（带稳定 ID）。
     */
    fun queryEntries(card: CharacterCard, spec: QuerySpec): String {
        val limit = spec.limit.coerceIn(1, QUERY_LIMIT_MAX)
        val kind = spec.kind.lowercase()

        fun match(text: String): Boolean =
            spec.keywords.isEmpty() || spec.keywords.any { k ->
                text.contains(k, ignoreCase = true)
            }

        return buildString {
            if (kind == "worldbook" || kind == "all") {
                val wb = buildWorldBookIndex(card)
                    .filter { match(it.title) || match(it.keywords.joinToString(" ")) || match(it.summary) }
                appendLine("世界书匹配 ${wb.size} 条（显示前 $limit）：")
                wb.take(limit).forEach { e ->
                    appendLine("- #${e.id}「${e.title}」（触发词：${e.keywords.joinToString("|").ifBlank { "无" }}，" +
                            "${e.contentLength}字，${if (e.enabled) "启用" else "停用"}）：${e.summary}")
                }
            }
            if (kind == "regex" || kind == "all") {
                val rx = buildRegexIndex(card)
                    .filter { match(it.name) || match(it.patternSummary) }
                appendLine("正则匹配 ${rx.size} 条（显示前 $limit）：")
                rx.take(limit).forEach { r ->
                    appendLine("- [${r.id.take(8)}…] ${r.name}（${r.contentLength}字，${if (r.enabled) "启用" else "禁用"}）：${r.patternSummary}")
                }
            }
            appendLine("读取完整内容用 get_card_entry(kind=worldbook|regex, entry_id=<上面的 ID>)。")
        }.trim()
    }

    /**
     * 读取单条完整内容：worldbook 按 #uid，regex 按 id（允许前 8 位短 id 匹配，唯一时）。
     * 未找到返回明确错误（Result.failure），不返回无关条目。
     */
    fun getEntry(card: CharacterCard, kind: String, entryId: String): Result<String> {
        val id = entryId.trim().removePrefix("#")
        if (id.isBlank()) return Result.failure(IllegalArgumentException("entry_id 为空"))
        return when (kind.lowercase()) {
            "worldbook" -> {
                val uid = id.toLongOrNull()
                    ?: return Result.failure(IllegalArgumentException("世界书条目 ID 必须是数字（收到：$id）"))
                val entry = card.characterBook?.entries?.firstOrNull { it.id == uid }
                    ?: return Result.failure(NoSuchElementException(
                        "未找到世界书条目 #$uid（可用 query_card_entries 重新检索有效 ID）"))
                val content = if (entry.content.length > ENTRY_CONTENT_MAX) {
                    entry.content.take(ENTRY_CONTENT_MAX) + "\n\n（内容过长，已截断到 $ENTRY_CONTENT_MAX 字符）"
                } else entry.content
                Result.success(
                    "世界书条目 #$uid「${entry.comment.ifBlank { "(无标题)" } }」" +
                            "（触发词：${entry.keys.joinToString("|").ifBlank { "无" }}，" +
                            "${if (entry.enabled) "启用" else "停用"}，全文 ${entry.content.length} 字）：\n$content"
                )
            }
            "regex" -> {
                val script = card.regexScripts.firstOrNull { it.id == id }
                    ?: card.regexScripts.filter { it.id.startsWith(id) && id.length >= 8 }.singleOrNull()
                    ?: return Result.failure(NoSuchElementException(
                        "未找到正则脚本 id=$id（可用 query_card_entries 检索有效 ID）"))
                Result.success(
                    "正则脚本「${script.scriptName.ifBlank { "(未命名)" }}」" +
                            "（id=${script.id}，${if (!script.disabled) "启用" else "禁用"}）：\n" +
                            "find: ${script.findRegex.take(ENTRY_CONTENT_MAX)}\n" +
                            "replace: ${script.replaceString.take(ENTRY_CONTENT_MAX)}"
                )
            }
            else -> Result.failure(IllegalArgumentException("kind 只支持 worldbook | regex（收到：$kind）"))
        }
    }
}
