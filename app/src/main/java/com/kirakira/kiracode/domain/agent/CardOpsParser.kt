package com.kirakira.kiracode.domain.agent

/**
 * Agent 操作解析器（V2.0）。
 *
 * 从 AI 回复中解析 ```card-update``` 结构化操作块，支持：
 * - `[field:xxx]...[/field]` 文本字段设置
 * - `[book.add]`/`[book.update uid=N]`/`[book.delete uid=N]`/`[book.clear]` 世界书操作
 * - `[regex.add]`/`[regex.delete id=xxx]` 正则脚本操作
 * - `[card.replace]` 整卡替换（JSON）
 *
 * 键值行格式：`key: value`，content 值可跨多行直到下一个键值行或块结束。
 */
object CardOpsParser {

    private val BLOCK_REGEX = Regex(
        pattern = """```(?:card-update|card_update)\s*([\s\S]*?)```""",
        options = setOf(RegexOption.IGNORE_CASE)
    )

    private val FIELD_REGEX = Regex(
        pattern = """\[field:([a-zA-Z_]+)]\s*([\s\S]*?)\[/field]""",
        options = setOf(RegexOption.IGNORE_CASE)
    )

    // 指令块：[op args] ... [/op]
    private val OP_REGEX = Regex(
        pattern = """\[([a-z_.]+)([^\]]*)]\s*([\s\S]*?)\[/\1]""",
        options = setOf(RegexOption.IGNORE_CASE)
    )

    // 自闭合指令：[op args]（无闭合标签，如 [book.delete uid=5] / [book.clear] / [regex.delete name=x]）
    private val SELF_CLOSING_REGEX = Regex(
        pattern = """\[([a-z_.]+)([^\]]*)]""",
        options = setOf(RegexOption.IGNORE_CASE)
    )

    /** 允许自闭合的指令集合 */
    private val SELF_CLOSING_OPS = setOf("book.delete", "book.clear", "regex.delete", "cover.clear", "cover.set")

    private val ARG_UID = Regex("""uid\s*=\s*(\d+)""", RegexOption.IGNORE_CASE)
    private val ARG_ID = Regex("""(?:id|name)\s*=\s*([^\\\]]+)""", RegexOption.IGNORE_CASE)

    /** 解析全部操作 */
    fun parse(aiReply: String): List<CardOperation> {
        val ops = mutableListOf<CardOperation>()
        for (block in BLOCK_REGEX.findAll(aiReply)) {
            val body = block.groupValues.getOrNull(1).orEmpty()
            ops += parseBlock(body)
        }
        return ops
    }

    fun hasUpdates(aiReply: String): Boolean =
        BLOCK_REGEX.containsMatchIn(aiReply)

    /** 移除所有操作块，仅保留自然语言部分 */
    fun stripUpdateBlocks(aiReply: String): String =
        BLOCK_REGEX.replace(aiReply, "").trim()

    private fun parseBlock(body: String): List<CardOperation> {
        val ops = mutableListOf<CardOperation>()
        var remaining = body

        // Pass 1：成对闭合指令（含内容体）
        for (m in OP_REGEX.findAll(body)) {
            val opName = m.groupValues[1].lowercase()
            val args = m.groupValues[2].trim()
            val content = m.groupValues[3].trim()
            handleOp(opName, args, content)?.let { ops += it }
            remaining = remaining.replace(m.value, "\u0000")
        }

        // Pass 2：自闭合指令（删除/清空类，无内容体）
        for (m in SELF_CLOSING_REGEX.findAll(remaining)) {
            val opName = m.groupValues[1].lowercase()
            if (opName !in SELF_CLOSING_OPS) continue
            handleOp(opName, m.groupValues[2].trim(), "")?.let { ops += it }
        }
        return ops
    }

    private fun handleOp(opName: String, rawArgs: String, content: String): CardOperation? {
        val args = rawArgs
        return when (opName) {
            "field" -> CardOperation.SetField(args.removePrefix(":").trim().lowercase(), content)
            "card.replace" -> CardOperation.ReplaceCard(content)
            "book.add" -> parseBookAdd(content)
            "book.update" -> {
                val uid = ARG_UID.find(args)?.groupValues?.get(1)?.toLongOrNull()
                if (uid != null) parseBookUpdate(uid, content) else null
            }
            "book.delete" -> {
                val uid = ARG_UID.find(args)?.groupValues?.get(1)?.toLongOrNull()
                if (uid != null) CardOperation.BookDelete(uid) else null
            }
            "book.clear" -> CardOperation.BookClear
            "regex.add" -> parseRegexAdd(content)
            "regex.delete" -> {
                ARG_ID.find(args)?.groupValues?.get(1)?.trim()?.let { CardOperation.RegexDelete(it) }
            }
            "cover.set" -> {
                // 内容体或 args 均可携带 URL / data URI
                val src = content.lineOrNull() ?: args.removePrefix("url=").trim()
                if (src.isNotBlank()) CardOperation.CoverSet(src) else null
            }
            "cover.clear" -> CardOperation.CoverClear
            else -> null // 未知指令忽略
        }
    }

    /** 键值块解析：`key: value`（可多行），兼容 AI 可能输出的 `|key: value` 内联分隔 */
    private fun parseKeyValueBlock(content: String): Map<String, String> {
        val result = LinkedHashMap<String, String>()
        var currentKey: String? = null
        val buffer = StringBuilder()
        // 把「| 后紧跟 key:」视为键值对分隔符（拆成换行），正文中的普通 | 不受影响
        val normalized = content.replace(Regex("""\|(?=\s*[A-Za-z_][A-Za-z0-9_]*\s*[:=])"""), "\n")
        for (line in normalized.lines()) {
            val kv = Regex("""^([a-zA-Z_][a-zA-Z0-9_]*)\s*[:：]\s*(.*)$""").find(line.trim())
            if (kv != null) {
                currentKey?.let { result[it] = buffer.toString().trim() }
                currentKey = kv.groupValues[1].lowercase()
                buffer.clear()
                buffer.appendLine(kv.groupValues[2])
            } else if (currentKey != null) {
                buffer.appendLine(line)
            }
        }
        currentKey?.let { result[it] = buffer.toString().trim() }
        return result
    }

    private fun splitKeys(value: String?): List<String> =
        value?.split(',', '，', ';', '；', '\n', '|')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()

    private fun String.lineOrNull(): String? =
        lines().firstOrNull { it.isNotBlank() }?.trim()

    private fun toBool(value: String?, default: Boolean): Boolean =
        when (value?.trim()?.lowercase()) {
            "true", "1", "yes", "是" -> true
            "false", "0", "no", "否" -> false
            null -> default
            else -> default
        }

    private fun parseBookAdd(content: String): CardOperation.BookAdd {
        val kv = parseKeyValueBlock(content)
        return CardOperation.BookAdd(
            keys = splitKeys(kv["keys"] ?: kv["key"]),
            secondaryKeys = splitKeys(kv["secondary_keys"] ?: kv["keysecondary"]),
            content = kv["content"].orEmpty(),
            comment = kv["comment"].orEmpty(),
            constant = toBool(kv["constant"], false),
            selective = toBool(kv["selective"], true),
            insertionOrder = kv["insertion_order"]?.trim()?.toIntOrNull() ?: 100,
            enabled = toBool(kv["enabled"], true),
            position = kv["position"]?.trim()?.takeIf { it.isNotBlank() },
            priority = kv["priority"]?.trim()?.toIntOrNull()
        )
    }

    private fun parseBookUpdate(uid: Long, content: String): CardOperation.BookUpdate {
        val kv = parseKeyValueBlock(content)
        return CardOperation.BookUpdate(
            uid = uid,
            keys = if (kv.containsKey("keys") || kv.containsKey("key")) splitKeys(kv["keys"] ?: kv["key"]) else null,
            secondaryKeys = if (kv.containsKey("secondary_keys") || kv.containsKey("keysecondary"))
                splitKeys(kv["secondary_keys"] ?: kv["keysecondary"]) else null,
            content = kv["content"],
            comment = kv["comment"],
            constant = kv["constant"]?.let { toBool(it, false) },
            enabled = kv["enabled"]?.let { toBool(it, true) },
            insertionOrder = kv["insertion_order"]?.trim()?.toIntOrNull(),
            position = kv["position"]?.trim()?.takeIf { it.isNotBlank() },
            priority = kv["priority"]?.trim()?.toIntOrNull()
        )
    }

    private fun parseRegexAdd(content: String): CardOperation.RegexAdd? {
        val kv = parseKeyValueBlock(content)
        val findRegex = kv["find_regex"] ?: kv["findregex"] ?: return null
        val placementRaw = kv["placement"]?.trim().orEmpty()
        val placement = placementRaw.split(',', '，')
            .mapNotNull { it.trim().toIntOrNull() }
            .ifEmpty { listOf(2) }
        return CardOperation.RegexAdd(
            scriptName = kv["script_name"] ?: kv["scriptname"] ?: "",
            findRegex = findRegex,
            replaceString = kv["replace_string"] ?: kv["replacestring"] ?: "",
            placement = placement,
            disabled = toBool(kv["disabled"], false),
            trimStrings = splitKeys(kv["trim_strings"]),
            markdownOnly = kv["markdown_only"]?.let { toBool(it, false) },
            promptOnly = kv["prompt_only"]?.let { toBool(it, false) },
            runOnEdit = toBool(kv["run_on_edit"], false),
            substituteRegex = kv["substitute_regex"]?.trim()?.toIntOrNull() ?: 0,
            minDepth = kv["min_depth"]?.trim()?.toIntOrNull(),
            maxDepth = kv["max_depth"]?.trim()?.toIntOrNull()
        )
    }
}
