package com.kirakira.kiracode.domain.agent.tools

import com.kirakira.kiracode.domain.agent.CardOperation
import com.kirakira.kiracode.domain.model.RegexGenerationMode

/**
 * 写入 SillyTavern 前的正则格式安检。
 * 只守运行兼容性与数据安全，不判断视觉风格是否“现代”。
 */
object RegexScriptValidator {

    data class ValidationResult(
        val operation: CardOperation,
        val errors: List<String> = emptyList()
    ) {
        val isValid: Boolean get() = errors.isEmpty()
    }

    fun validateAndNormalize(
        operation: CardOperation,
        mode: RegexGenerationMode
    ): ValidationResult = when (operation) {
        is CardOperation.RegexAdd -> {
            val normalized = operation.copy(
                placement = operation.placement.ifEmpty { listOf(2) },
                markdownOnly = operation.markdownOnly,
                promptOnly = operation.promptOnly
            ).withSafeDisplayDefaults()
            validate(normalized, mode)
        }
        is CardOperation.RegexUpdate -> {
            val normalized = operation.copy(
                placement = operation.placement.ifEmpty { listOf(2) }
            ).withSafeDisplayDefaults()
            validate(normalized, mode)
        }
        else -> ValidationResult(operation)
    }

    private fun CardOperation.RegexAdd.withSafeDisplayDefaults(): CardOperation.RegexAdd {
        if (!containsHtml(replaceString)) return this
        val safeMarkdown = markdownOnly ?: if (promptOnly == true) null else true
        val safePrompt = promptOnly ?: if (safeMarkdown == true) false else null
        return copy(markdownOnly = safeMarkdown, promptOnly = safePrompt)
    }

    private fun CardOperation.RegexUpdate.withSafeDisplayDefaults(): CardOperation.RegexUpdate {
        if (!containsHtml(replaceString)) return this
        val safeMarkdown = markdownOnly ?: if (promptOnly == true) null else true
        val safePrompt = promptOnly ?: if (safeMarkdown == true) false else null
        return copy(markdownOnly = safeMarkdown, promptOnly = safePrompt)
    }

    private fun validate(operation: CardOperation, mode: RegexGenerationMode): ValidationResult {
        val script = when (operation) {
            is CardOperation.RegexAdd -> ScriptFields(
                findRegex = operation.findRegex,
                replaceString = operation.replaceString,
                markdownOnly = operation.markdownOnly,
                promptOnly = operation.promptOnly
            )
            is CardOperation.RegexUpdate -> ScriptFields(
                findRegex = operation.findRegex,
                replaceString = operation.replaceString,
                markdownOnly = operation.markdownOnly,
                promptOnly = operation.promptOnly
            )
            else -> return ValidationResult(operation)
        }
        val errors = mutableListOf<String>()

        errors += validateFindRegex(script.findRegex)
        errors += validateReplacementReferences(script.findRegex, script.replaceString)
        if (script.replaceString.isBlank()) {
            errors += "replaceString 不能为空"
        }
        if (containsHtml(script.replaceString) &&
            script.markdownOnly == false && script.promptOnly == false
        ) {
            errors += "美化类 HTML 规则必须设置 markdownOnly=true、promptOnly=false，避免永久污染聊天记录"
        }

        if (mode == RegexGenerationMode.FULL_FRONTEND) {
            errors += validateFullFrontend(script.replaceString)
        }
        return ValidationResult(operation, errors.distinct())
    }

    private fun validateFindRegex(findRegex: String): List<String> {
        val raw = findRegex.trim()
        if (raw.isEmpty()) return listOf("findRegex 不能为空")

        val pattern: String
        if (raw.startsWith("/")) {
            val closing = raw.lastIndexOf('/')
            if (closing <= 0) return listOf("findRegex 必须写成 /正则/flags 形式，且不能缺少结束斜杠")
            pattern = raw.substring(1, closing)
            val flags = raw.substring(closing + 1)
            val invalidFlags = flags.filterNot { it in "dgimsuvy" }
            if (invalidFlags.isNotEmpty()) {
                return listOf("findRegex 包含不支持的 flags：$invalidFlags")
            }
        } else {
            pattern = raw
        }

        return try {
            Regex(pattern)
            emptyList()
        } catch (e: Exception) {
            listOf("findRegex 无法编译：${e.message?.take(160) ?: "正则语法错误"}")
        }
    }

    private fun validateReplacementReferences(findRegex: String, replaceString: String): List<String> {
        val pattern = extractPattern(findRegex) ?: return emptyList()
        val groupCount = runCatching {
            Regex(pattern).toPattern().matcher("").groupCount()
        }.getOrDefault(0)
        val errors = mutableListOf<String>()

        Regex("""\$(\d+)""").findAll(replaceString).forEach { match ->
            val index = match.groupValues[1].toIntOrNull() ?: return@forEach
            if (index > groupCount) {
                errors += "replaceString 引用了 \$$index，但 findRegex 只有 $groupCount 个捕获组"
            }
        }
        Regex("""\$<([^>]+)>""").findAll(replaceString).forEach { match ->
            val name = match.groupValues[1]
            val declared = pattern.contains("(?<$name>") || pattern.contains("(?'$name'")
            if (!declared) {
                errors += "replaceString 引用了命名捕获组 \$${ "<$name>" }，但 findRegex 未声明该组"
            }
        }
        return errors
    }

    private fun extractPattern(findRegex: String): String? {
        val raw = findRegex.trim()
        if (!raw.startsWith("/")) return raw.ifBlank { null }
        val closing = raw.lastIndexOf('/')
        if (closing <= 0) return null
        return raw.substring(1, closing)
    }

    private fun validateFullFrontend(replaceString: String): List<String> {
        val trimmed = replaceString.trim()
        val errors = mutableListOf<String>()

        val lines = trimmed.lines()
        if (lines.firstOrNull()?.trim() != "```html") {
            errors += "replaceString 首行必须是 ```html 代码围栏"
        }
        if (lines.lastOrNull()?.trim() != "```") {
            errors += "replaceString 末行必须是 ``` 代码围栏"
        }
        if (!trimmed.contains("<!DOCTYPE html>", ignoreCase = true)) {
            errors += "完整前端必须包含 <!DOCTYPE html>"
        }
        listOf("<html", "<head", "</head>", "<body", "</body>", "</html>").forEach { tag ->
            if (!trimmed.contains(tag, ignoreCase = true)) {
                errors += "完整前端缺少必要标签：$tag"
            }
        }
        if (!replaceString.contains("$1") && !replaceString.contains("{{match}}")) {
            errors += "完整前端必须包含 \$1 或 {{match}} 注入槽位"
        }

        val newlineCount = trimmed.count { it == '\n' }
        if (newlineCount < 12) {
            errors += "完整前端必须使用真实多行格式（至少 12 个真实换行），不能压成单行"
        }
        val htmlCssRegion = trimmed.substringBefore("<script", trimmed)
        val literalNewlineCount = Regex("""\\n""").findAll(htmlCssRegion).count()
        if (literalNewlineCount > 3) {
            errors += "HTML/CSS 结构不能使用字面量 \\n 冒充真实换行"
        }
        return errors
    }

    private fun containsHtml(value: String): Boolean =
        listOf("<div", "<span", "<details", "<style", "<script", "<!DOCTYPE", "<html", "```html")
            .any { value.contains(it, ignoreCase = true) }

    private data class ScriptFields(
        val findRegex: String,
        val replaceString: String,
        val markdownOnly: Boolean?,
        val promptOnly: Boolean?
    )
}
