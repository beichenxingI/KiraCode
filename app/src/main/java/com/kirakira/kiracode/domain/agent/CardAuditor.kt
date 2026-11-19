package com.kirakira.kiracode.domain.agent

import com.kirakira.kiracode.domain.model.AuditIssue
import com.kirakira.kiracode.domain.model.AuditIssueCodes
import com.kirakira.kiracode.domain.model.AuditReport
import com.kirakira.kiracode.domain.model.AuditScope
import com.kirakira.kiracode.domain.model.AuditSeverity
import com.kirakira.kiracode.domain.model.CharacterCard
import com.kirakira.kiracode.domain.model.st.StBookEntry

object CardAuditor {

    /** 重复检测最多比较次数，避免超大世界书 O(n²) 卡住主线程 */
    private const val MAX_DUPLICATE_COMPARISONS = 20_000

    private val HOOK_VERBS = setOf(
        "偷", "买", "卖", "问", "潜入", "贿赂", "破坏", "交易", "逃亡", "寻找",
        "挑战", "委托", "调查", "追踪", "解锁", "激活", "对话", "招募", "谈判",
        "steal", "buy", "sell", "ask", "bribe", "escape", "investigate", "hire"
    )

    private val CLICHE_OPENING = listOf(
        Regex("^在.{0,20}的世界里"),
        Regex("^.{0,10}是一个.{0,20}(地方|世界|国度)"),
        Regex("^.{0,10}拥有(悠久的|漫长的|古老的)历史"),
        Regex("^欢迎来到"),
        Regex("^这是一个")
    )

    private val CLICHE_ENDING = listOf(
        Regex("总之[，,].{0,20}是一个"),
        Regex("综上所述"),
        Regex("这就是.{0,10}(的|介绍|全部)$"),
        Regex("总之，以上就是")
    )

    fun audit(card: CharacterCard, scope: AuditScope = AuditScope.ALL): AuditReport {
        val issues = mutableListOf<AuditIssue>()

        if (scope != AuditScope.WORLDBOOK) {
            auditCardFields(card, issues)
        }

        val entries = card.characterBook?.entries ?: emptyList()
        if (scope != AuditScope.FIELDS) {
            entries.forEach { entry -> auditEntry(entry, issues) }
            auditCrossEntries(entries, issues)

            if (entries.isEmpty()) {
                issues.add(
                    AuditIssue(
                        severity = AuditSeverity.INFO,
                        issueCode = AuditIssueCodes.NO_WORLDBOOK,
                        description = "该卡没有世界书条目",
                        suggestion = "如需丰富世界设定，可添加世界书条目"
                    )
                )
            }
        }

        val estimatedTokens = estimateTokens(card)
        if (estimatedTokens > 8000) {
            issues.add(
                AuditIssue(
                    severity = AuditSeverity.INFO,
                    issueCode = AuditIssueCodes.TOKEN_WARNING,
                    description = "卡片总 token 约 $estimatedTokens，属于大卡（LARGE 档）",
                    suggestion = "注意上下文预算，核查是否有冗余条目"
                )
            )
        }

        val errors = issues.count { it.severity == AuditSeverity.ERROR }
        val warnings = issues.count { it.severity == AuditSeverity.WARNING }
        val infos = issues.count { it.severity == AuditSeverity.INFO }

        return AuditReport(
            cardName = card.name,
            cardUuid = card.uuid,
            entryCount = entries.size,
            estimatedTokens = estimatedTokens,
            errorCount = errors,
            warningCount = warnings,
            infoCount = infos,
            issues = issues
        )
    }

    private fun auditCardFields(card: CharacterCard, issues: MutableList<AuditIssue>) {
        if (card.description.isBlank()) {
            issues.add(
                AuditIssue(
                    severity = AuditSeverity.ERROR,
                    fieldName = "description",
                    issueCode = AuditIssueCodes.CARD_NO_DESC,
                    description = "角色描述（description）为空",
                    suggestion = "补充角色外貌、性格、身份等核心描述"
                )
            )
        }
        if (card.firstMessage.isBlank()) {
            issues.add(
                AuditIssue(
                    severity = AuditSeverity.ERROR,
                    fieldName = "firstMessage",
                    issueCode = AuditIssueCodes.CARD_NO_GREETING,
                    description = "开场白（firstMessage）为空",
                    suggestion = "补充一段有代入感的开场白"
                )
            )
        }
    }

    private fun auditEntry(entry: StBookEntry, issues: MutableList<AuditIssue>) {
        val uid = entry.id
        val comment = entry.comment.ifBlank { "uid=$uid" }
        val content = entry.content

        if (content.isBlank()) {
            issues.add(
                AuditIssue(
                    severity = AuditSeverity.ERROR,
                    entryUid = uid,
                    entryComment = comment,
                    issueCode = AuditIssueCodes.EMPTY_CONTENT,
                    description = "条目「$comment」内容为空",
                    suggestion = "填写条目正文，或删除该条目"
                )
            )
            return
        }

        if (!entry.constant && entry.keys.all { it.isBlank() }) {
            issues.add(
                AuditIssue(
                    severity = AuditSeverity.ERROR,
                    entryUid = uid,
                    entryComment = comment,
                    issueCode = AuditIssueCodes.EMPTY_KEYS,
                    description = "条目「$comment」触发词为空，该条目永不触发",
                    suggestion = "添加 3~6 个触发词，或设为常驻条目（constant=true）"
                )
            )
        }

        if (!entry.enabled) {
            issues.add(
                AuditIssue(
                    severity = AuditSeverity.WARNING,
                    entryUid = uid,
                    entryComment = comment,
                    issueCode = AuditIssueCodes.DISABLED_ENTRY,
                    description = "条目「$comment」已禁用",
                    suggestion = "确认是否需要启用该条目"
                )
            )
        }

        val charCount = content.length
        if (charCount > 800) {
            issues.add(
                AuditIssue(
                    severity = AuditSeverity.WARNING,
                    entryUid = uid,
                    entryComment = comment,
                    issueCode = AuditIssueCodes.TOO_LONG,
                    description = "条目「$comment」内容 $charCount 字，超过 800 字上限",
                    suggestion = "按“历史/现状/权力”或“外观/机制/人物”拆分为多条"
                )
            )
        } else if (charCount > 500) {
            issues.add(
                AuditIssue(
                    severity = AuditSeverity.INFO,
                    entryUid = uid,
                    entryComment = comment,
                    issueCode = AuditIssueCodes.LONG_ENTRY,
                    description = "条目「$comment」内容 $charCount 字，接近上限",
                    suggestion = "注意内容密度，确认无冗余"
                )
            )
        }

        val numberCount = Regex("\\d+").findAll(content).count()
        if (numberCount < 3) {
            issues.add(
                AuditIssue(
                    severity = AuditSeverity.WARNING,
                    entryUid = uid,
                    entryComment = comment,
                    issueCode = AuditIssueCodes.LOW_NUMBERS,
                    description = "条目「$comment」只含 $numberCount 个数字，描述过于模糊",
                    suggestion = "补充具体数字（距离/人口/价格/时间/等级等）"
                )
            )
        }

        val firstLine = content.take(50)
        if (CLICHE_OPENING.any { it.containsMatchIn(firstLine) }) {
            issues.add(
                AuditIssue(
                    severity = AuditSeverity.WARNING,
                    entryUid = uid,
                    entryComment = comment,
                    issueCode = AuditIssueCodes.CLICHE_OPENING,
                    description = "条目「$comment」首句是 AI 惯用开场套话",
                    suggestion = "直接从核心信息切入，删掉首句铺垫"
                )
            )
        }

        val lastLine = content.takeLast(40)
        if (CLICHE_ENDING.any { it.containsMatchIn(lastLine) }) {
            issues.add(
                AuditIssue(
                    severity = AuditSeverity.WARNING,
                    entryUid = uid,
                    entryComment = comment,
                    issueCode = AuditIssueCodes.CLICHE_ENDING,
                    description = "条目「$comment」末句是 AI 惯用总结套话",
                    suggestion = "删掉最后一句总结，以关键信息或钩子收尾"
                )
            )
        }

        if (charCount > 150) {
            val lastThird = content.takeLast(charCount / 3)
            val hasHook = HOOK_VERBS.any { verb -> lastThird.contains(verb, ignoreCase = true) }
            if (!hasHook) {
                issues.add(
                    AuditIssue(
                        severity = AuditSeverity.WARNING,
                        entryUid = uid,
                        entryComment = comment,
                        issueCode = AuditIssueCodes.NO_HOOK,
                        description = "条目「$comment」末段没有动作动词，缺少行动钩子",
                        suggestion = "末段加入玩家可执行的动作（如：可委托/可购买/可挑战）"
                    )
                )
            }
        }
    }

    private fun auditCrossEntries(entries: List<StBookEntry>, issues: MutableList<AuditIssue>) {
        val candidates = entries.filter { it.content.length > 50 }
        if (candidates.size < 2) return

        val bigramSets = candidates.map { it.content.chunked(2).toSet() }
        var comparisons = 0
        for (i in candidates.indices) {
            for (j in i + 1 until candidates.size) {
                if (++comparisons > MAX_DUPLICATE_COMPARISONS) return
                val a = candidates[i]
                val b = candidates[j]
                val shorter = minOf(a.content.length, b.content.length).toFloat()
                val longer = maxOf(a.content.length, b.content.length).toFloat()
                if (longer <= 0f || shorter / longer < 0.5f) continue

                val similarity = jaccardSimilarity(bigramSets[i], bigramSets[j])
                if (similarity > 0.6f) {
                    issues.add(
                        AuditIssue(
                            severity = AuditSeverity.WARNING,
                            entryUid = a.id,
                            entryComment = a.comment.ifBlank { "uid=${a.id}" },
                            pairUid = b.id,
                            issueCode = AuditIssueCodes.DUPLICATE,
                            description = "uid=${a.id}「${a.comment}」与 uid=${b.id}「${b.comment}」内容相似度 ${(similarity * 100).toInt()}%，疑似重复",
                            suggestion = "合并重复内容，或明确区分两条的侧重点"
                        )
                    )
                }
            }
        }
    }

    private fun jaccardSimilarity(a: Set<String>, b: Set<String>): Float {
        if (a.isEmpty() || b.isEmpty()) return 0f
        val intersection = (a intersect b).size
        val union = (a union b).size
        return intersection.toFloat() / union
    }

    private fun estimateTokens(card: CharacterCard): Int {
        val textLength = listOf(
            card.description,
            card.personality,
            card.scenario,
            card.firstMessage,
            card.exampleDialogue,
            card.systemPrompt,
            card.postHistoryInstructions
        ).sumOf { it.length } +
            (card.characterBook?.entries?.sumOf { it.content.length } ?: 0)

        return (textLength * 1.0f).toInt()
    }
}
