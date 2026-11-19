package com.kirakira.kiracode.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class AuditIssue(
    val severity: AuditSeverity,
    val entryUid: Long? = null,
    val fieldName: String? = null,
    val entryComment: String? = null,
    val pairUid: Long? = null,
    val issueCode: String,
    val description: String,
    val suggestion: String
)

@Serializable
enum class AuditSeverity { ERROR, WARNING, INFO }

@Serializable
enum class AuditScope { ALL, WORLDBOOK, FIELDS }

@Serializable
data class AuditReport(
    val cardName: String,
    val cardUuid: String,
    val entryCount: Int,
    val estimatedTokens: Int,
    val errorCount: Int,
    val warningCount: Int,
    val infoCount: Int,
    val issues: List<AuditIssue>,
    val auditedAt: Long = System.currentTimeMillis()
) {
    fun toShortSummary(): String = buildString {
        appendLine("审计完成：「$cardName」")
        appendLine("世界书 $entryCount 条，约 $estimatedTokens tokens")
        appendLine("$errorCount 个错误 · $warningCount 个警告 · $infoCount 个提示")
        appendLine()

        val errors = issues.filter { it.severity == AuditSeverity.ERROR }
        val warnings = issues.filter { it.severity == AuditSeverity.WARNING }

        if (errors.isNotEmpty()) {
            appendLine("【错误 · 需修复】")
            errors.take(8).forEach { issue ->
                appendLine("[${issue.location()}] ${issue.issueCode}：${issue.description}")
            }
            if (errors.size > 8) appendLine("…另有 ${errors.size - 8} 条错误，见完整报告")
        }
        if (warnings.isNotEmpty()) {
            appendLine("【警告】")
            warnings.take(5).forEach { issue ->
                appendLine("[${issue.location()}] ${issue.issueCode}：${issue.description}")
            }
            if (warnings.size > 5) appendLine("…另有 ${warnings.size - 5} 条警告，见完整报告")
        }
        appendLine()
        append("完整报告已展示在审计卡片中，可逐条查看并修复。")
    }
}

private fun AuditIssue.location(): String = when {
    entryUid != null && pairUid != null -> "uid=$entryUid ↔ uid=$pairUid"
    entryUid != null -> "uid=$entryUid"
    !fieldName.isNullOrBlank() -> fieldName
    else -> "卡级"
}

object AuditIssueCodes {
    const val EMPTY_CONTENT = "EMPTY_CONTENT"
    const val EMPTY_KEYS = "EMPTY_KEYS"
    const val CARD_NO_DESC = "CARD_NO_DESC"
    const val CARD_NO_GREETING = "CARD_NO_GREETING"

    const val TOO_LONG = "TOO_LONG"
    const val LOW_NUMBERS = "LOW_NUMBERS"
    const val CLICHE_OPENING = "CLICHE_OPENING"
    const val CLICHE_ENDING = "CLICHE_ENDING"
    const val DUPLICATE = "DUPLICATE"
    const val NO_HOOK = "NO_HOOK"
    const val DISABLED_ENTRY = "DISABLED_ENTRY"

    const val LONG_ENTRY = "LONG_ENTRY"
    const val NO_WORLDBOOK = "NO_WORLDBOOK"
    const val TOKEN_WARNING = "TOKEN_WARNING"
}
