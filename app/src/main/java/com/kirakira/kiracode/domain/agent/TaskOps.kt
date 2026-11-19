package com.kirakira.kiracode.domain.agent

import java.util.UUID
import kotlinx.serialization.Serializable

/**
 * Agent 子任务（数据面板任务板）。
 *
 * AI 可通过 [task.add] / [task.status] 指令创建与推进任务；
 * 任务板状态会注入下一轮系统提示词，实现「AI 可读取和修改」的闭环。
 * @Serializable：会话事件快照持久化（阶段 B，注解仅用于序列化，不改执行语义）。
 */
@Serializable
data class AgentTask(
    val id: String = UUID.randomUUID().toString().take(8),
    val title: String,
    val detail: String = "",
    /** PENDING / RUNNING / DONE / FAILED */
    val status: String = STATUS_PENDING
) {
    companion object {
        const val STATUS_PENDING = "PENDING"
        const val STATUS_RUNNING = "RUNNING"
        const val STATUS_DONE = "DONE"
        const val STATUS_FAILED = "FAILED"

        fun isValidStatus(s: String): Boolean =
            s.uppercase() in setOf(STATUS_PENDING, STATUS_RUNNING, STATUS_DONE, STATUS_FAILED)
    }
}

/** 任务操作指令（从 card-update 块中的 task.* 指令解析） */
sealed class TaskOp {
    abstract fun applyTo(tasks: List<AgentTask>): List<AgentTask>

    data class Add(val title: String, val detail: String = "", val status: String = AgentTask.STATUS_PENDING) : TaskOp() {
        override fun applyTo(tasks: List<AgentTask>): List<AgentTask> =
            tasks + AgentTask(title = title.take(60), detail = detail.take(300), status = status)
    }

    data class SetStatus(val taskIdOrTitle: String, val newStatus: String) : TaskOp() {
        override fun applyTo(tasks: List<AgentTask>): List<AgentTask> = tasks.map { t ->
            if (t.id == taskIdOrTitle || t.title == taskIdOrTitle) t.copy(status = newStatus) else t
        }
    }

    data class Delete(val taskIdOrTitle: String) : TaskOp() {
        override fun applyTo(tasks: List<AgentTask>): List<AgentTask> =
            tasks.filterNot { it.id == taskIdOrTitle || it.title == taskIdOrTitle }
    }
}

/**
 * 任务指令解析器：扫描 ```card-update 块内 [task.add] / [task.status id=x] / [task.delete id=x]。
 */
object TaskOpsParser {

    private val BLOCK_REGEX = Regex(
        pattern = """```(?:card-update|card_update)\s*([\s\S]*?)```""",
        options = setOf(RegexOption.IGNORE_CASE)
    )

    private val PAIRED_REGEX = Regex(
        pattern = """\[(task\.[a-z]+)([^\]]*)]\s*([\s\S]*?)\[/\1]""",
        options = setOf(RegexOption.IGNORE_CASE)
    )

    private val SELF_REGEX = Regex(
        pattern = """\[(task\.(?:status|delete|add))([^\]]*)](?:\s*\[/\1])?""",
        options = setOf(RegexOption.IGNORE_CASE)
    )

    private val KV_LINE = Regex("""^([a-zA-Z_]+)\s*:\s*(.*)$""")

    private val ID_OR_TITLE_ARG = Regex("""(?:id|title)\s*=\s*([^\]]+)""", RegexOption.IGNORE_CASE)

    fun parse(aiReply: String): List<TaskOp> {
        val ops = mutableListOf<TaskOp>()
        for (block in BLOCK_REGEX.findAll(aiReply)) {
            val body = block.groupValues[1]
            // 自闭合 task.status / task.delete / task.add title=...
            for (m in SELF_REGEX.findAll(body)) {
                parseSelf(m.groupValues[1].lowercase(), m.groupValues[2])?.let { ops += it }
            }
            // 成对 task.add（含键值体）
            for (m in PAIRED_REGEX.findAll(body)) {
                when (m.groupValues[1].lowercase()) {
                    "task.add" -> {
                        val kv = kvBlock(m.groupValues[3])
                        val title = (kv["title"] ?: kv["name"]).orEmpty().trim()
                        if (title.isNotBlank()) {
                            ops += TaskOp.Add(
                                title = title,
                                detail = kv["detail"].orEmpty().trim(),
                                status = kv["status"]?.trim()?.uppercase()
                                    ?.takeIf { AgentTask.isValidStatus(it) } ?: AgentTask.STATUS_PENDING
                            )
                        }
                    }
                    "task.status", "task.delete" -> {
                        if (m.groupValues[3].isNotBlank()) {
                            parseSelf(m.groupValues[1].lowercase(), m.groupValues[2])?.let { ops += it }
                        }
                    }
                }
            }
        }
        return ops
    }

    private fun parseSelf(opName: String, argsRaw: String): TaskOp? {
        val target = ID_OR_TITLE_ARG.find(argsRaw)?.groupValues?.get(1)?.trim() ?: return null
        return when (opName) {
            "task.add" -> TaskOp.Add(title = target.take(60), detail = "")
            "task.status" -> {
                // 目标与状态在同一参数串中：id=<target> status=<STATUS>
                val target = Regex(
                    pattern = """(?:id|title)\s*=\s*(.+?)\s*status\s*=\s*(\w+)""",
                    options = setOf(RegexOption.IGNORE_CASE)
                ).find(argsRaw)?.let { m ->
                    val st = m.groupValues[2].uppercase()
                    if (AgentTask.isValidStatus(st)) {
                        TaskOp.SetStatus(m.groupValues[1].trim(), st)
                    } else null
                } ?: return null
                target
            }
            "task.delete" -> TaskOp.Delete(target)
            else -> null
        }
    }

    private fun kvBlock(content: String): Map<String, String> {
        val result = LinkedHashMap<String, String>()
        var key: String? = null
        val buf = StringBuilder()
        // 兼容 AI 可能输出的 |key:value 内联分隔
        val normalized = content.replace(Regex("""\|(?=\s*[A-Za-z_][A-Za-z0-9_]*\s*[:=])"""), "\n")
        for (line in normalized.lines()) {
            val m = KV_LINE.find(line.trim())
            if (m != null) {
                key?.let { result[it] = buf.toString().trim() }
                key = m.groupValues[1].lowercase()
                buf.clear(); buf.appendLine(m.groupValues[2])
            } else if (key != null) {
                buf.appendLine(line)
            }
        }
        key?.let { result[it] = buf.toString().trim() }
        return result
    }
}
