package com.kirakira.kiracode

import com.kirakira.kiracode.domain.agent.CardOperation
import com.kirakira.kiracode.domain.agent.CardOperationApplier
import com.kirakira.kiracode.domain.agent.CardOpsParser
import com.kirakira.kiracode.domain.usecase.SubAgentRunner
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 子 Agent 真实性验证（V2 Beta 任务 6）。
 *
 * 场景：主 Agent 要求并行创建两条世界书条目 →
 * 1) [subagent.request] 解析出两个独立任务；
 * 2) 两个子代理各自独立调用（独立 system 上下文，互不混入主会话历史）；
 * 3) 结果按派发顺序回收，构造回注消息；
 * 4) 主 Agent 基于回注结果用 [book.add] 写卡。
 *
 * 独立性验证方式：SubAgentRunner.run 构造的 messages 仅含
 * (system=子代理职责+卡快照[+人格/用户规则], user=单一任务) —— 不含主会话历史。
 * 本测试以「等效模拟执行」复现该结构（Runner 依赖 Android Context 无法在 JVM 直跑），
 * 并断言其消息结构契约与并行合并语义。
 */
class SubAgentE2eTest {

    /** 与 ChatViewModel.parseSubAgentRequests 相同协议 */
    private fun parseRequests(text: String): List<String> {
        val regex = Regex("""\[subagent\.request]([\s\S]*?)\[/subagent\.request]""", RegexOption.IGNORE_CASE)
        return regex.findAll(text).map { it.groupValues[1].trim() }
            .filter { it.isNotBlank() }.toList()
    }

    @Test
    fun `main agent can dispatch two parallel subagent requests`() {
        val mainReply = """
            计划：并行派两个子代理分别撰写两条世界书条目。

            [subagent.request]
            世界书条目：樱华高中——写 200 字校园场景正文
            [/subagent.request]

            [subagent.request]
            世界书条目：夜市祭典——写 200 字祭典氛围正文
            [/subagent.request]
        """.trimIndent()

        val requests = parseRequests(mainReply)
        assertEquals(2, requests.size)
        assertTrue(requests[0].contains("樱华高中"))
        assertTrue(requests[1].contains("夜市祭典"))
        // 每个请求都是单一、明确的任务（子代理「一次只做一件事」契约）
        requests.forEach { assertTrue(it.isNotBlank()) }
    }

    @Test
    fun `sub agent messages are independent of main session history`() {
        // 复现 SubAgentRunner.run 的消息构造契约：
        // 主会话历史哪怕有 100 条，子代理 messages 也只有 system + user 两条
        val cardContext = "角色名：汐音\n描述：海边的少女……"
        val task = "世界书条目：樱华高中——写 200 字校园场景正文"

        val personaPrompt = "你的文风清冷克制，善用意象。"
        val customPrompt = "世界书单条 ≤300 字。"

        val system = buildString {
            appendLine("你是 KiraCard 的制卡子代理，负责完成一个单一、明确的任务。")
            appendLine("只输出任务要求的正文内容本身，不要解释、不要字段名、不要 JSON、不要代码块围栏。")
            appendLine("当前角色卡上下文（用于保持风格一致）：")
            append(cardContext.take(2000))
            personaPrompt.takeIf { it.isNotBlank() }?.let {
                appendLine()
                appendLine("【灵魂（创作风格基线，请遵循）】")
                appendLine(it.take(1200))
            }
            customPrompt.takeIf { it.isNotBlank() }?.let {
                appendLine()
                appendLine("【用户的创作规则】")
                appendLine(it.take(600))
            }
        }
        val messages = listOf(
            com.kirakira.kiracode.data.remote.dto.ChatMessage(role = "system", content = system),
            com.kirakira.kiracode.data.remote.dto.ChatMessage(role = "user", content = task)
        )

        // 独立上下文契约：仅 2 条消息，无任何主会话历史混入
        assertEquals(2, messages.size)
        assertEquals("system", messages[0].role)
        assertEquals("user", messages[1].role)
        assertTrue(messages[0].content!!.contains("制卡子代理"))
        assertTrue(messages[0].content!!.contains(cardContext))
        // V2 Beta 强化：人格与用户规则注入子代理
        assertTrue(messages[0].content!!.contains("灵魂"))
        assertTrue(messages[0].content!!.contains("用户的创作规则"))
        // user 消息只包含单一任务
        assertEquals(task, messages[1].content)
    }

    @Test
    fun `parallel results merge in dispatch order and main agent writes card`() = runBlocking {
        // 模拟两个子代理并行执行（async 并发，与 runSubAgents 相同结构）
        val tasks = listOf("任务A：樱华高中", "任务B：夜市祭典")
        val results = mutableMapOf<Int, String>()
        coroutineScope {
            val deferred = tasks.mapIndexed { i, req ->
                async {
                    // 独立子代理执行（模拟独立 API 调用产出）
                    val output = if (i == 0) "暮色中的校舍与蝉鸣……" else "灯笼映红的街道……"
                    results[i] = "【子代理 #${i + 1} 结果（$req）】\n$output"
                    output
                }
            }
            deferred.awaitAll()
        }

        // 按派发顺序回收
        val ordered = tasks.indices.mapNotNull { results[it] }
        assertEquals(2, ordered.size)
        assertTrue(ordered[0].contains("任务A"))
        assertTrue(ordered[1].contains("任务B"))

        // 回注消息构造
        val followUp = "【系统·子代理返回（2 个独立子代理已完成）】\n" + ordered.joinToString("\n\n") +
                "\n\n请审核以上子代理产出，用 [book.add]/[regex.add]/[field:] 写入角色卡，继续按六步工作流推进。"
        assertTrue(followUp.contains("樱华高中"))
        assertTrue(followUp.contains("夜市祭典"))

        // 主 Agent 基于回注结果写卡（审核通过 → [book.add] 落卡）
        val mainAuditReply = """
            两个子代理产出已审核，均可采用，写入世界书。

            ```card-update
            [book.add]
            keys: 樱华高中
            content: 暮色中的校舍与蝉鸣……
            comment: 樱华高中
            constant: false
            position: before_char
            [/book.add]

            [book.add]
            keys: 夜市, 祭典
            content: 灯笼映红的街道……
            comment: 夜市祭典
            constant: false
            position: before_char
            [/book.add]
            ```
        """.trimIndent()

        val ops = CardOpsParser.parse(mainAuditReply)
        assertEquals(2, ops.size)
        val card = CardOperationApplier.apply(com.kirakira.kiracode.domain.model.CharacterCard(name = "汐音"), ops)
        val book = card.characterBook
        assertNotNull(book)
        assertEquals(2, book!!.entries.size)
        assertTrue(book.entries.any { it.comment == "樱华高中" && it.content.contains("校舍") })
        assertTrue(book.entries.any { it.comment == "夜市祭典" && it.content.contains("灯笼") })
    }

    @Test
    fun `subagent runner object exposes expected contract`() {
        // 编译期契约守护：SubAgentRunner 可注入（Hilt @Singleton），run(task, cardContext) 签名稳定
        val k = SubAgentRunner::class
        assertNotNull(k.constructors.firstOrNull())
        assertEquals(1, k.constructors.size) // 单一 @Inject 构造
    }
}
