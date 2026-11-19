package com.kirakira.kiracode.domain.agent

import kotlinx.serialization.Serializable

/**
 * 计划书（全自动模式）：AI 动手前必须先输出任务计划，
 * 展示在数据控制面板左侧并实时更新完成状态。
 * @Serializable：会话事件快照持久化（阶段 B，注解仅用于序列化，不改执行语义）。
 */
@Serializable
data class PlanPart(
    val id: String,
    val title: String,
    val detail: String = "",
    val status: String = STATUS_PENDING
) {
    companion object {
        const val STATUS_PENDING = "PENDING"
        const val STATUS_RUNNING = "RUNNING"
        const val STATUS_DONE = "DONE"
        const val STATUS_FAILED = "FAILED"
    }
}

@Serializable
data class PlanBook(
    val goal: String = "",
    val parts: List<PlanPart> = emptyList()
) {
    val doneCount: Int get() = parts.count { it.status == PlanPart.STATUS_DONE }
    val progressText: String get() = "${doneCount}/${parts.size}"
    val isComplete: Boolean get() = parts.isNotEmpty() && parts.all { it.status == PlanPart.STATUS_DONE }

    /** 注入系统提示词的摘要（含 id 供 AI 更新状态） */
    fun digest(): String = buildString {
        if (goal.isNotBlank()) appendLine("目标：$goal")
        parts.forEachIndexed { i, p ->
            appendLine("${i + 1}. [${p.status}] #${p.id} ${p.title}")
            if (p.detail.isNotBlank()) appendLine("   └ ${p.detail.take(80)}")
        }
    }.trim()
}

/** 计划书操作 */
sealed class PlanOp {
    data class SetGoal(val goal: String) : PlanOp()
    data class Add(
        val title: String,
        val detail: String = "",
        val status: String = PlanPart.STATUS_PENDING
    ) : PlanOp()
    data class SetStatus(val target: String, val newStatus: String) : PlanOp()
    object Clear : PlanOp()

    fun applyTo(book: PlanBook): PlanBook {
        when (this) {
            is SetGoal -> return book.copy(goal = goal)
            is Add -> return book.copy(parts = book.parts + PlanPart(
                id = nextId(book),
                title = title.trim(),
                detail = detail,
                status = status
            ))
            is SetStatus -> {
                val st = newStatus.uppercase().let { s ->
                    listOf(
                        PlanPart.STATUS_PENDING, PlanPart.STATUS_RUNNING,
                        PlanPart.STATUS_DONE, PlanPart.STATUS_FAILED
                    ).firstOrNull { it == s }
                } ?: return book
                val t = target.trim()
                return book.copy(parts = book.parts.map { part ->
                    if (part.id.equals(t, ignoreCase = true) || part.title == t || part.title.startsWith(t)) {
                        part.copy(status = st)
                    } else part
                })
            }
            Clear -> return PlanBook()
        }
    }

    private fun nextId(book: PlanBook): String {
        var n = book.parts.size + 1
        var candidate = "P$n"
        while (book.parts.any { it.id == candidate }) {
            n++; candidate = "P$n"
        }
        return candidate
    }
}

/**
 * 计划书指令解析器。
 *
 * 配对块：
 * [plan.goal]打造一张赛博朋克都市传说卡[/plan.goal]
 * [plan.add]
 * title: 第一部分：世界观框架
 * detail: 城市分区/势力/时间线
 * [/plan.add]
 *
 * 自闭合单行：
 * [plan.status id=P1 status=DONE]
 * [plan.clear]
 */
object PlanOpsParser {

    private val PAIRED_REGEX = Regex(
        pattern = """\[plan\.(goal|add)\]([\s\S]*?)\[/plan\.\1]""",
        options = setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE)
    )
    private val SELF_REGEX = Regex(
        pattern = """\[plan\.(status|clear)(?:[ \t]+([^\]]*))?]""",
        options = setOf(RegexOption.IGNORE_CASE)
    )
    private val KV_REGEX = Regex(
        pattern = """^([A-Za-z_][A-Za-z0-9_]*)\s*[:：]\s*(.*)$""",
        options = setOf(RegexOption.MULTILINE)
    )

    fun parse(reply: String): List<PlanOp> {
        val ops = mutableListOf<PlanOp>()
        for (m in PAIRED_REGEX.findAll(reply)) {
            val tag = m.groupValues[1].lowercase()
            val body = m.groupValues[2].trim()
            when (tag) {
                "goal" -> if (body.isNotEmpty()) ops += PlanOp.SetGoal(body)
                "add" -> {
                    val normalized = body.replace(Regex("""\|(?=\s*[A-Za-z_][A-Za-z0-9_]*\s*[:=])"""), "\n")
                    val kv = KV_REGEX.findAll(normalized).associate {
                        it.groupValues[1].lowercase() to it.groupValues[2].trim()
                    }
                    // 支持纯文本标题（无 kv 结构时整段作为标题）
                    val title = kv["title"] ?: body.lines().firstOrNull()?.take(60) ?: ""
                    if (title.isNotBlank()) ops += PlanOp.Add(
                        title = title,
                        detail = kv["detail"].orEmpty(),
                        status = normalizeStatus(kv["status"])
                    )
                }
            }
        }
        for (m in SELF_REGEX.findAll(reply)) {
            val tag = m.groupValues[1].lowercase()
            val argsRaw = m.groupValues[2]
            when (tag) {
                "status" -> {
                    val target = Regex("""(?:id|part)\s*=\s*([^\]\s]+)""", RegexOption.IGNORE_CASE)
                        .find(argsRaw)?.groupValues?.get(1)
                    val status = Regex("""status\s*=\s*(\w+)""", RegexOption.IGNORE_CASE)
                        .find(argsRaw)?.groupValues?.get(1)?.uppercase()
                    if (!target.isNullOrBlank() && !status.isNullOrBlank()) {
                        ops += PlanOp.SetStatus(target, status)
                    }
                }
                "clear" -> ops += PlanOp.Clear
            }
        }
        return ops
    }

    private fun normalizeStatus(raw: String?): String = when (raw?.trim()?.uppercase()) {
        PlanPart.STATUS_RUNNING -> PlanPart.STATUS_RUNNING
        PlanPart.STATUS_DONE -> PlanPart.STATUS_DONE
        PlanPart.STATUS_FAILED -> PlanPart.STATUS_FAILED
        else -> PlanPart.STATUS_PENDING
    }
}
