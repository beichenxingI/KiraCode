package com.kirakira.kiracode.domain.agent

import com.kirakira.kiracode.domain.model.CharacterCard
import com.kirakira.kiracode.domain.model.KiraWatermark
import com.kirakira.kiracode.domain.model.st.StBookEntry
import com.kirakira.kiracode.domain.model.st.StCharacterBook
import com.kirakira.kiracode.domain.model.st.StRegexScript
import java.util.UUID

/**
 * Agent 对角色卡的操作（结构化、可确认、可回放）。
 *
 * 由 [CardOpsParser] 从 AI 回复的 ```card-update``` 块解析而来，
 * 由 [CardOperationApplier] 以纯函数方式应用到 [CharacterCard]。
 */
sealed class CardOperation {

    /** 设置文本字段（snake_case 规范字段名） */
    abstract val describe: String

    data class SetField(
        val fieldName: String,
        val value: String,
        /** AI 脑补标注（V2/V3）：true=此值为 AI 未与用户确认自行生成，UI 显示可交互标注 */
        val isAiGuess: Boolean = false
    ) : CardOperation() {
        override val describe: String
            get() {
                val preview = if (fieldName == "tags" || fieldName == "alternate_greetings") {
                    value.split('\n', ',', '，').map { it.trim() }.filter { it.isNotEmpty() }
                        .joinToString("、")
                } else value.take(60)
                return "修改 $fieldName → $preview"
            }
    }

    /** 整卡替换（用于从零创建新卡；JSON 需符合 ST 规范） */
    data class ReplaceCard(
        val json: String,
        val regexGenerationMode: com.kirakira.kiracode.domain.model.RegexGenerationMode? = null
    ) : CardOperation() {
        override val describe: String get() = "替换整张角色卡"
    }

    /** 新增世界书条目 */
    data class BookAdd(
        val keys: List<String>,
        val content: String,
        val comment: String,
        val secondaryKeys: List<String> = emptyList(),
        val constant: Boolean = false,
        val selective: Boolean = true,
        val insertionOrder: Int = 100,
        val enabled: Boolean = true,
        val position: String? = null,
        /** 注入优先级（ST 扩展，数值越大越重要）——与 BookUpdate/工具 schema 对齐（P0） */
        val priority: Int? = null,
        /** AI 脑补标注（V2/V3） */
        val isAiGuess: Boolean = false,
        // ──────── V3 extensions 常用字段（增量2） ────────
        val caseSensitive: Boolean? = null,
        val matchWholeWords: Boolean? = null,
        val scanDepth: Int? = null,
        val automationId: String? = null,
        val probability: Int? = null,
        val useProbability: Boolean? = null,
        val selectiveLogic: Int? = null,
        val excludeRecursion: Boolean? = null,
        val preventRecursion: Boolean? = null,
        val group: String? = null,
        val groupOverride: Boolean? = null,
        val groupWeight: Int? = null
    ) : CardOperation() {
        override val describe: String
            get() = "新增世界书「${comment.ifBlank { keys.joinToString("/") }}」（${content.length} 字）"
    }

    /** 更新世界书条目（uid 定位；仅提供的字段生效） */
    data class BookUpdate(
        val uid: Long,
        val keys: List<String>? = null,
        val secondaryKeys: List<String>? = null,
        val content: String? = null,
        val comment: String? = null,
        val constant: Boolean? = null,
        val selective: Boolean? = null,
        val enabled: Boolean? = null,
        val insertionOrder: Int? = null,
        val position: String? = null,
        /** 注入优先级（ST 扩展，数值越大越重要） */
        val priority: Int? = null,
        // ──────── V3 extensions 常用字段（增量2，全部可选） ────────
        val caseSensitive: Boolean? = null,
        val matchWholeWords: Boolean? = null,
        val scanDepth: Int? = null,
        val automationId: String? = null,
        val probability: Int? = null,
        val useProbability: Boolean? = null,
        val selectiveLogic: Int? = null,
        val excludeRecursion: Boolean? = null,
        val preventRecursion: Boolean? = null,
        val group: String? = null,
        val groupOverride: Boolean? = null,
        val groupWeight: Int? = null
    ) : CardOperation() {
        override val describe: String
            get() {
                val target = comment?.let { "「$it」" } ?: "#$uid"
                val changes = buildList {
                    content?.let { add("内容(${it.length}字)") }
                    keys?.let { add("关键词") }
                    enabled?.let { add(if (it) "启用" else "停用") }
                    constant?.let { add(if (it) "常驻" else "非常驻") }
                    insertionOrder?.let { add("顺序$it") }
                    priority?.let { add("优先级$it") }
                }
                return "更新世界书 $target：${changes.joinToString("/").ifEmpty { "字段调整" }}"
            }
    }

    data class BookDelete(val uid: Long) : CardOperation() {
        override val describe: String get() = "删除世界书条目 #$uid"
    }

    object BookClear : CardOperation() {
        override val describe: String get() = "清空世界书"
    }

    /** 新增正则脚本 */
    data class RegexAdd(
        val scriptName: String,
        val findRegex: String,
        val replaceString: String,
        val placement: List<Int> = listOf(2),
        val disabled: Boolean = false,
        val trimStrings: List<String> = emptyList(),
        val markdownOnly: Boolean? = null,
        val promptOnly: Boolean? = null,
        val runOnEdit: Boolean = false,
        /** 替换模式（ST substitute_find_regex）：0=NONE 1=RAW 2=ESCAPED */
        val substituteRegex: Int = 0,
        val minDepth: Int? = null,
        val maxDepth: Int? = null
    ) : CardOperation() {
        override val describe: String
            get() = "新增正则「${scriptName.ifBlank { findRegex.take(24) }}」"
    }

    /** 按名称或 ID 更新一条正则脚本（整条替换，保留原 id） */
    data class RegexUpdate(
        val idOrName: String,
        val scriptName: String,
        val findRegex: String,
        val replaceString: String,
        val placement: List<Int> = listOf(2),
        val disabled: Boolean = false,
        val trimStrings: List<String> = emptyList(),
        val markdownOnly: Boolean? = null,
        val promptOnly: Boolean? = null,
        val runOnEdit: Boolean = false,
        val substituteRegex: Int = 0,
        val minDepth: Int? = null,
        val maxDepth: Int? = null
    ) : CardOperation() {
        override val describe: String
            get() = "更新正则「${scriptName.ifBlank { idOrName }}」"
    }

    /** 删除正则脚本（按 id 或名称） */
    data class RegexDelete(val idOrName: String) : CardOperation() {
        override val describe: String get() = "删除正则「$idOrName」"
    }

    /** 设置封面（value 为 URL 或 data URI；URL 由上层先下载转为 data URI 再应用） */
    data class CoverSet(val source: String) : CardOperation() {
        override val describe: String
            get() = if (source.startsWith("data:image")) "更换封面（内嵌图片）" else "设置封面图片"
    }

    /** 清除封面 */
    object CoverClear : CardOperation() {
        override val describe: String get() = "清除封面"
    }
}

/** 脑补标注读取（UI 渲染用）：SetField/BookAdd 携带 isAiGuess */
val CardOperation.aiGuessFlag: Boolean
    get() = when (this) {
        is CardOperation.SetField -> isAiGuess
        is CardOperation.BookAdd -> isAiGuess
        else -> false
    }

/**
 * 操作应用器：将操作序列以纯函数方式应用到角色卡。
 * 返回新卡实例（不可变数据模型）；非法操作静默跳过。
 */
object CardOperationApplier {

    private val SNAKE_TO_DOMAIN = mapOf(
        "name" to "name",
        "description" to "description",
        "personality" to "personality",
        "scenario" to "scenario",
        "first_mes" to "firstMessage",
        "mes_example" to "exampleDialogue",
        "creator_notes" to "creatorNotes",
        "system_prompt" to "systemPrompt",
        "post_history_instructions" to "postHistoryInstructions",
        "creator" to "creator",
        "character_version" to "version",
        "nickname" to "nickname"
    )

    fun apply(card: CharacterCard, operations: List<CardOperation>): CharacterCard {
        var current = card
        for (op in operations) {
            current = when (op) {
                is CardOperation.SetField -> applySetField(current, op)
                is CardOperation.ReplaceCard -> applyReplace(current, op)
                is CardOperation.BookAdd -> applyBookAdd(current, op)
                is CardOperation.BookUpdate -> applyBookUpdate(current, op)
                is CardOperation.BookDelete -> applyBookDelete(current, op.uid)
                is CardOperation.BookClear -> applyBookClear(current)
                is CardOperation.RegexAdd -> applyRegexAdd(current, op)
                is CardOperation.RegexUpdate -> applyRegexUpdate(current, op)
                is CardOperation.RegexDelete -> applyRegexDelete(current, op.idOrName)
                is CardOperation.CoverSet -> current.copy(avatar = op.source)
                CardOperation.CoverClear -> current.copy(avatar = null)
            }
        }
        return KiraWatermark.apply(current.copyWithUpdatedTimestamp())
    }

    private fun applySetField(card: CharacterCard, op: CardOperation.SetField): CharacterCard {
        // 列表型字段
        if (op.fieldName == "tags") {
            return card.copy(tags = splitList(op.value))
        }
        if (op.fieldName == "alternate_greetings") {
            return card.copy(alternateGreetings = op.value.split("\n").map { it.trim() }.filter { it.isNotEmpty() })
        }
        val domainField = SNAKE_TO_DOMAIN[op.fieldName.lowercase()] ?: return card
        return try {
            when (domainField) {
                "name" -> card.copy(name = op.value)
                "description" -> card.copy(description = op.value)
                "personality" -> card.copy(personality = op.value)
                "scenario" -> card.copy(scenario = op.value)
                "firstMessage" -> card.copy(firstMessage = op.value)
                "exampleDialogue" -> card.copy(exampleDialogue = op.value)
                "creatorNotes" -> card.copy(creatorNotes = op.value)
                "systemPrompt" -> card.copy(systemPrompt = op.value)
                "postHistoryInstructions" -> card.copy(postHistoryInstructions = op.value)
                "creator" -> card.copy(creator = op.value)
                "version" -> card.copy(version = op.value)
                "nickname" -> card.copy(nickname = op.value)
                else -> card
            }
        } catch (e: Exception) {
            card
        }
    }

    private fun applyReplace(card: CharacterCard, op: CardOperation.ReplaceCard): CharacterCard {
        val decoded = com.kirakira.kiracode.domain.model.st.StCardCodec.decode(op.json).getOrElse { return card }
        return op.regexGenerationMode?.let { mode ->
            com.kirakira.kiracode.domain.prompt.RegexGenerationPrompts.applyDescriptionProtocol(decoded, mode)
        } ?: decoded
    }

    private fun applyBookAdd(card: CharacterCard, op: CardOperation.BookAdd): CharacterCard {
        val book = card.characterBook ?: StCharacterBook(entries = emptyList())
        val nextId = (book.entries.maxOfOrNull { it.id } ?: -1L) + 1
        val entry = StBookEntry(
            id = nextId,
            keys = op.keys,
            secondaryKeys = op.secondaryKeys,
            comment = op.comment,
            content = op.content,
            constant = op.constant,
            selective = op.selective,
            insertionOrder = op.insertionOrder,
            enabled = op.enabled,
            position = op.position ?: "before_char",
            priority = op.priority,
            caseSensitive = op.caseSensitive,
            matchWholeWords = op.matchWholeWords,
            scanDepth = op.scanDepth,
            automationId = op.automationId,
            probability = op.probability,
            useProbability = op.useProbability,
            selectiveLogic = op.selectiveLogic,
            excludeRecursion = op.excludeRecursion,
            preventRecursion = op.preventRecursion,
            group = op.group,
            groupOverride = op.groupOverride,
            groupWeight = op.groupWeight
        )
        return card.copy(characterBook = book.copy(entries = book.entries + entry, raw = book.raw))
    }

    private fun applyBookUpdate(card: CharacterCard, op: CardOperation.BookUpdate): CharacterCard {
        val book = card.characterBook ?: return card
        val entries = book.entries.map { entry ->
            if (entry.id != op.uid) entry else entry.copy(
                keys = op.keys ?: entry.keys,
                secondaryKeys = op.secondaryKeys ?: entry.secondaryKeys,
                content = op.content ?: entry.content,
                comment = op.comment ?: entry.comment,
                constant = op.constant ?: entry.constant,
                selective = op.selective ?: entry.selective,
                enabled = op.enabled ?: entry.enabled,
                insertionOrder = op.insertionOrder ?: entry.insertionOrder,
                position = op.position ?: entry.position,
                priority = op.priority ?: entry.priority,
                caseSensitive = op.caseSensitive ?: entry.caseSensitive,
                matchWholeWords = op.matchWholeWords ?: entry.matchWholeWords,
                scanDepth = op.scanDepth ?: entry.scanDepth,
                automationId = op.automationId ?: entry.automationId,
                probability = op.probability ?: entry.probability,
                useProbability = op.useProbability ?: entry.useProbability,
                selectiveLogic = op.selectiveLogic ?: entry.selectiveLogic,
                excludeRecursion = op.excludeRecursion ?: entry.excludeRecursion,
                preventRecursion = op.preventRecursion ?: entry.preventRecursion,
                group = op.group ?: entry.group,
                groupOverride = op.groupOverride ?: entry.groupOverride,
                groupWeight = op.groupWeight ?: entry.groupWeight
            )
        }
        return card.copy(characterBook = book.copy(entries = entries))
    }

    private fun applyBookDelete(card: CharacterCard, uid: Long): CharacterCard {
        val book = card.characterBook ?: return card
        return card.copy(characterBook = book.copy(entries = book.entries.filterNot { it.id == uid }))
    }

    private fun applyBookClear(card: CharacterCard): CharacterCard {
        val book = card.characterBook ?: return card
        return card.copy(characterBook = book.copy(entries = emptyList()))
    }

    private fun applyRegexAdd(card: CharacterCard, op: CardOperation.RegexAdd): CharacterCard {
        val script = StRegexScript(
            id = UUID.randomUUID().toString(),
            scriptName = op.scriptName,
            findRegex = op.findRegex,
            replaceString = op.replaceString,
            placement = op.placement,
            disabled = op.disabled,
            trimStrings = op.trimStrings,
            markdownOnly = op.markdownOnly ?: false,
            promptOnly = op.promptOnly ?: false,
            runOnEdit = op.runOnEdit,
            substituteRegex = op.substituteRegex,
            minDepth = op.minDepth,
            maxDepth = op.maxDepth
        )
        return card.copy(regexScripts = card.regexScripts + script)
    }

    private fun applyRegexUpdate(card: CharacterCard, op: CardOperation.RegexUpdate): CharacterCard {
        val target = card.regexScripts.firstOrNull {
            it.id == op.idOrName || it.scriptName == op.idOrName
        } ?: return card
        val updated = target.copy(
            scriptName = op.scriptName.ifBlank { target.scriptName },
            findRegex = op.findRegex,
            replaceString = op.replaceString,
            placement = op.placement,
            disabled = op.disabled,
            trimStrings = op.trimStrings,
            markdownOnly = op.markdownOnly ?: false,
            promptOnly = op.promptOnly ?: false,
            runOnEdit = op.runOnEdit,
            substituteRegex = op.substituteRegex,
            minDepth = op.minDepth,
            maxDepth = op.maxDepth
        )
        return card.copy(regexScripts = card.regexScripts.map { if (it.id == target.id) updated else it })
    }

    private fun applyRegexDelete(card: CharacterCard, idOrName: String): CharacterCard {
        return card.copy(
            regexScripts = card.regexScripts.filterNot {
                it.id == idOrName || it.scriptName == idOrName
            }
        )
    }

    private fun splitList(value: String): List<String> =
        value.split('\n', ',', '，', ';', '；')
            .map { it.trim().removePrefix("#") }
            .filter { it.isNotEmpty() }

    /** snake_case 字段 → 中文名（Diff 对比与脑补标注展示用） */
    val FIELD_LABELS = mapOf(
        "name" to "名字",
        "description" to "人设描述",
        "personality" to "性格",
        "scenario" to "场景",
        "first_mes" to "开场白",
        "mes_example" to "示例对话",
        "creator_notes" to "创作备注",
        "system_prompt" to "系统提示词",
        "post_history_instructions" to "后置提示",
        "alternate_greetings" to "备选开场白",
        "tags" to "标签",
        "creator" to "创作者",
        "character_version" to "版本",
        "nickname" to "昵称"
    )

    fun fieldLabel(fieldName: String): String =
        FIELD_LABELS[fieldName.lowercase()] ?: fieldName

    /** 读取字段当前值（Diff 旧版取值；与 SNAKE_TO_DOMAIN 同一映射） */
    fun readField(card: CharacterCard, fieldName: String): String {
        if (fieldName.lowercase() == "tags") return card.tags.joinToString("、")
        if (fieldName.lowercase() == "alternate_greetings") return card.alternateGreetings.joinToString("\n")
        return when (SNAKE_TO_DOMAIN[fieldName.lowercase()]) {
            "name" -> card.name
            "description" -> card.description
            "personality" -> card.personality
            "scenario" -> card.scenario
            "firstMessage" -> card.firstMessage
            "exampleDialogue" -> card.exampleDialogue
            "creatorNotes" -> card.creatorNotes
            "systemPrompt" -> card.systemPrompt
            "postHistoryInstructions" -> card.postHistoryInstructions
            "creator" -> card.creator
            "version" -> card.version
            "nickname" -> card.nickname ?: ""
            else -> ""
        }
    }
}
