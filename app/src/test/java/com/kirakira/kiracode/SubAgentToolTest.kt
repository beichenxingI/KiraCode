package com.kirakira.kiracode

import com.kirakira.kiracode.data.remote.WebpageFetcher
import com.kirakira.kiracode.data.remote.dto.FunctionCall as DtoFunctionCall
import com.kirakira.kiracode.data.remote.dto.ToolCall as DtoToolCall
import com.kirakira.kiracode.data.remote.real.ApiConfig
import com.kirakira.kiracode.data.remote.real.RealApiService
import com.kirakira.kiracode.data.remote.search.SearchProviderChain
import com.kirakira.kiracode.domain.agent.AgentController
import com.kirakira.kiracode.domain.agent.CardOperationApplier
import com.kirakira.kiracode.domain.agent.ToolRegistry
import com.kirakira.kiracode.domain.agent.core.AgentEvent as CoreAgentEvent
import com.kirakira.kiracode.domain.agent.core.SubAgentCompletedEvent
import com.kirakira.kiracode.domain.agent.core.SubAgentFailedEvent
import com.kirakira.kiracode.domain.agent.core.SubAgentStartedEvent
import com.kirakira.kiracode.domain.agent.core.ThinkAction
import com.kirakira.kiracode.domain.agent.core.ToolMessage
import com.kirakira.kiracode.domain.agent.core.UserMessage
import com.kirakira.kiracode.domain.agent.tools.CardFunctions
import com.kirakira.kiracode.domain.model.CharacterCard
import com.kirakira.kiracode.domain.model.WorkMode
import com.kirakira.kiracode.domain.usecase.ChatEngine
import com.kirakira.kiracode.domain.usecase.SubAgentRunner
import com.kirakira.kiracode.ui.workspace.AgentCardUi
import com.kirakira.kiracode.ui.workspace.failRunningSubAgents
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tool-as-Agent 子代理（spawn_subagent）验收测试。
 *
 * 覆盖：
 * 1. 工具注册（AGENT/YOLO 注册；联网开关不影响）；
 * 2. 工具描述契约（审核权/不直接写卡/预算约束）；
 * 3. 主循环派发 → 子代理独立执行 → 匹配 tool_call_id 回注（成功/失败/缺参）；
 * 4. 子代理消息独立上下文 + 预算（SubAgentRunner 契约）；
 * 5. 取消收敛（运行中卡片 → 失败，无僵尸状态）；
 * 6. 事件卡序列化稳定。
 */
class SubAgentToolTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private fun toolNames(webSearchEnabled: Boolean = true): List<String> =
        CardFunctions.getAllFunctions(webSearchEnabled)
            .map { it["function"]!!.jsonObject["name"]!!.jsonPrimitive.content }

    // ────────────────────────── 1/2. 工具注册与契约 ──────────────────────────

    @Test
    fun `spawn_subagent is registered for tool modes regardless of web toggle`() {
        assertTrue("spawn_subagent" in toolNames(webSearchEnabled = true))
        // 联网搜索关闭时子代理工具仍然可用（两者独立开关）
        assertTrue("spawn_subagent" in toolNames(webSearchEnabled = false))
    }

    @Test
    fun `spawn_subagent description enforces review contract`() {
        val desc = CardFunctions.getAllFunctions()
            .first { it["function"]!!.jsonObject["name"]!!.jsonPrimitive.content == "spawn_subagent" }
            .let { it["function"]!!.jsonObject["description"]!!.jsonPrimitive.content }
        // 主 Agent 拥有审核与写入权；子代理不直接修改角色卡；不是无限预算
        assertTrue(desc.contains("审核"))
        assertTrue(desc.contains("不会直接修改角色卡"))
        assertTrue(desc.contains("不是无限预算"))
        // 参数契约：task 必填（required 数组含 task）
        val requiredArray = CardFunctions.getAllFunctions()
            .first { it["function"]!!.jsonObject["name"]!!.jsonPrimitive.content == "spawn_subagent" }
            .let { it["function"]!!.jsonObject["parameters"]!!.jsonObject["required"]!! }
        assertTrue(requiredArray.toString().contains("task"))
    }

    // ────────────────────────── 主循环集成（成功/失败/缺参/取消） ──────────────────────────

    /** 构造带 mock 依赖的控制器 + 捕获每轮流式请求的 messages */
    private fun config() = ApiConfig(apiKey = "k", baseUrl = "http://test.local", model = "test-model")

    @Test
    fun `spawn_subagent success feeds result back with matched tool call id`() = runBlocking {
        val api = mockk<RealApiService>()
        val runner = mockk<SubAgentRunner>()
        val chatEngine = mockk<ChatEngine>(relaxed = true)
        val searchChain = mockk<SearchProviderChain>(relaxed = true)
        val fetcher = mockk<WebpageFetcher>(relaxed = true)
        val registry = ToolRegistry { card, ops -> Result.success(CardOperationApplier.apply(card, ops)) }

        val captured = mutableListOf<List<com.kirakira.kiracode.data.remote.dto.ChatMessage>>()
        val toolCall = DtoToolCall(
            id = "call_sub_1",
            function = DtoFunctionCall("spawn_subagent", """{"task":"世界书条目：樱华高中——写 200 字正文"}""")
        )
        every { api.chatCompletionStream(any(), any(), any()) } answers {
            captured.add(firstArg())
            if (captured.size == 1) flowOf(RealApiService.SseEvent.ToolCalls(listOf(toolCall)))
            else flowOf(RealApiService.SseEvent.Content("子代理结果已审核完成。请问还需要调整吗？"))
        }
        coEvery { runner.run(any(), any(), any()) } returns Result.success("暮色中的校舍与蝉鸣……")

        registry.setCurrentCard(CharacterCard(name = "汐音"))
        val controller = AgentController(api, registry, chatEngine, searchChain, fetcher, runner)

        val events = controller.startWithMessages(
            messages = listOf(UserMessage("派个子代理写樱华高中的世界书条目")),
            tools = CardFunctions.getAllFunctions(),
            streaming = true,
            config = config(),
            mode = WorkMode.YOLO
        ).toList(mutableListOf())

        // 生命周期事件齐全且任务一致
        val started = events.filterIsInstance<SubAgentStartedEvent>().single()
        assertEquals("世界书条目：樱华高中——写 200 字正文", started.task)
        val completed = events.filterIsInstance<SubAgentCompletedEvent>().single()
        assertTrue(completed.output.contains("校舍"))
        assertTrue(completed.outputPreview.isNotBlank())
        assertTrue(completed.finishedAt >= completed.startedAt)

        // 子代理以独立上下文执行：workingCard 传入、任务一致
        val taskSlot = slot<String>()
        val cardSlot = slot<CharacterCard>()
        coVerify(exactly = 1) { runner.run(capture(taskSlot), capture(cardSlot), isNull()) }
        assertEquals("世界书条目：樱华高中——写 200 字正文", taskSlot.captured)
        assertEquals("汐音", cardSlot.captured?.name)

        // 回注校验：第二次 LLM 请求的历史包含与 tool_call.id 配对的 tool 消息，且携带产出
        assertEquals(2, captured.size)
        val toolMsg = captured[1].single { it.role == "tool" }
        assertEquals("call_sub_1", toolMsg.tool_call_id)
        assertEquals("spawn_subagent", toolMsg.name)
        assertTrue(toolMsg.content!!.contains("校舍"))
        assertTrue(toolMsg.content!!.contains("尚未写入角色卡"))

        // 主 Agent 仍能继续执行（第二轮 LLM 调用已发生并正常收束）
        assertTrue(events.any { it is ThinkAction })
    }

    @Test
    fun `spawn_subagent failure feeds matched error tool message and loop continues`() = runBlocking {
        val api = mockk<RealApiService>()
        val runner = mockk<SubAgentRunner>()
        val chatEngine = mockk<ChatEngine>(relaxed = true)
        val searchChain = mockk<SearchProviderChain>(relaxed = true)
        val fetcher = mockk<WebpageFetcher>(relaxed = true)
        val registry = ToolRegistry { card, ops -> Result.success(CardOperationApplier.apply(card, ops)) }

        val captured = mutableListOf<List<com.kirakira.kiracode.data.remote.dto.ChatMessage>>()
        val toolCall = DtoToolCall(
            id = "call_sub_2",
            function = DtoFunctionCall("spawn_subagent", """{"task":"分析设定一致性"}""")
        )
        every { api.chatCompletionStream(any(), any(), any()) } answers {
            captured.add(firstArg())
            if (captured.size == 1) flowOf(RealApiService.SseEvent.ToolCalls(listOf(toolCall)))
            else flowOf(RealApiService.SseEvent.Content("子代理失败了，我自己来。请问还需要别的帮助吗？"))
        }
        coEvery { runner.run(any(), any(), any()) } returns Result.failure(RuntimeException("API 限额"))

        val controller = AgentController(api, registry, chatEngine, searchChain, fetcher, runner)

        val events = controller.startWithMessages(
            messages = listOf(UserMessage("分析这张卡")),
            tools = CardFunctions.getAllFunctions(),
            streaming = true,
            config = config(),
            mode = WorkMode.YOLO
        ).toList(mutableListOf())

        val failed = events.filterIsInstance<SubAgentFailedEvent>().single()
        assertEquals("API 限额", failed.errorMessage)

        assertEquals(2, captured.size)
        val toolMsg = captured[1].single { it.role == "tool" }
        assertEquals("call_sub_2", toolMsg.tool_call_id)
        assertTrue(toolMsg.content!!.contains("子代理执行失败"))
        assertTrue(toolMsg.content!!.contains("API 限额"))
        // 主循环未崩溃且继续执行
        assertTrue(events.any { it is ThinkAction })
    }

    @Test
    fun `blank task is rejected without dispatching runner`() = runBlocking {
        val api = mockk<RealApiService>()
        val runner = mockk<SubAgentRunner>()
        val chatEngine = mockk<ChatEngine>(relaxed = true)
        val searchChain = mockk<SearchProviderChain>(relaxed = true)
        val fetcher = mockk<WebpageFetcher>(relaxed = true)
        val registry = ToolRegistry { card, ops -> Result.success(CardOperationApplier.apply(card, ops)) }

        val captured = mutableListOf<List<com.kirakira.kiracode.data.remote.dto.ChatMessage>>()
        val toolCall = DtoToolCall(id = "call_sub_3", function = DtoFunctionCall("spawn_subagent", "{}"))
        every { api.chatCompletionStream(any(), any(), any()) } answers {
            captured.add(firstArg())
            if (captured.size == 1) flowOf(RealApiService.SseEvent.ToolCalls(listOf(toolCall)))
            else flowOf(RealApiService.SseEvent.Content("好的。请问还有其他需要吗？"))
        }

        val controller = AgentController(api, registry, chatEngine, searchChain, fetcher, runner)

        val events = controller.startWithMessages(
            messages = listOf(UserMessage("派子代理")),
            tools = CardFunctions.getAllFunctions(),
            streaming = true,
            config = config(),
            mode = WorkMode.YOLO
        ).toList(mutableListOf())

        // 未派发子代理、未发 Started 事件
        coVerify(exactly = 0) { runner.run(any(), any(), any()) }
        assertTrue(events.none { it is SubAgentStartedEvent })

        // 错误以匹配的 ToolMessage 回注，主循环继续
        val toolMsg = captured[1].single { it.role == "tool" }
        assertEquals("call_sub_3", toolMsg.tool_call_id)
        assertTrue(toolMsg.content!!.contains("缺少任务描述"))
    }

    @Test
    fun `cancellation during subagent run leaves no terminal or zombie events`() = runBlocking {
        val api = mockk<RealApiService>()
        val runner = mockk<SubAgentRunner>()
        val chatEngine = mockk<ChatEngine>(relaxed = true)
        val searchChain = mockk<SearchProviderChain>(relaxed = true)
        val fetcher = mockk<WebpageFetcher>(relaxed = true)
        val registry = ToolRegistry { card, ops -> Result.success(CardOperationApplier.apply(card, ops)) }

        val captured = mutableListOf<List<com.kirakira.kiracode.data.remote.dto.ChatMessage>>()
        val toolCall = DtoToolCall(
            id = "call_sub_4",
            function = DtoFunctionCall("spawn_subagent", """{"task":"长期任务"}""")
        )
        every { api.chatCompletionStream(any(), any(), any()) } answers {
            captured.add(firstArg())
            flowOf(RealApiService.SseEvent.ToolCalls(listOf(toolCall)))
        }
        // 子代理长时间执行不返回
        coEvery { runner.run(any(), any(), any()) } coAnswers { awaitCancellation() }

        val controller = AgentController(api, registry, chatEngine, searchChain, fetcher, runner)

        val events = mutableListOf<CoreAgentEvent>()
        val job = launch {
            controller.startWithMessages(
                messages = listOf(UserMessage("派子代理")),
                tools = CardFunctions.getAllFunctions(),
                streaming = true,
                config = config(),
                mode = WorkMode.YOLO
            ).collect { events.add(it) }
        }
        withTimeout(10_000) {
            while (events.none { it is SubAgentStartedEvent }) delay(10)
        }
        job.cancelAndJoin()

        // 取消传播：无终态事件、无第二次 LLM 调用（无僵尸回注）
        assertTrue(events.any { it is SubAgentStartedEvent })
        assertTrue(events.none { it is SubAgentCompletedEvent })
        assertTrue(events.none { it is SubAgentFailedEvent })
        assertEquals(1, captured.size)
    }

    // ────────────────────────── 5. 取消收敛纯函数 ──────────────────────────

    @Test
    fun `cancelling marks running subagent cards as failed and keeps others`() {
        val now = System.currentTimeMillis()
        val running = AgentCardUi.SubAgent(
            id = "c1", subagentId = "sa1", task = "任务A",
            status = AgentCardUi.SubAgent.STATUS_RUNNING, startedAt = now
        )
        val done = AgentCardUi.SubAgent(
            id = "c2", subagentId = "sa2", task = "任务B",
            status = AgentCardUi.SubAgent.STATUS_DONE, startedAt = now, finishedAt = now,
            output = "产出"
        )
        val observation = AgentCardUi.Observation(id = "c3", content = "✅ 正常", isError = false)

        val result = listOf(running, done, observation).failRunningSubAgents()

        val first = result[0] as AgentCardUi.SubAgent
        assertEquals(AgentCardUi.SubAgent.STATUS_FAILED, first.status)
        assertEquals("任务已取消", first.errorMessage)
        assertNotNull(first.finishedAt)
        // 已完成/失败卡不受影响
        assertEquals(AgentCardUi.SubAgent.STATUS_DONE, (result[1] as AgentCardUi.SubAgent).status)
        assertEquals("c3", result[2].id)
    }

    // ────────────────────────── 6. 事件卡序列化 ──────────────────────────

    @Test
    fun `subagent card serializes with stable type tag`() {
        val card = AgentCardUi.SubAgent(
            id = "card-1", subagentId = "sa-1", task = "写条目",
            status = AgentCardUi.SubAgent.STATUS_DONE, startedAt = 1L, finishedAt = 2L,
            output = "产出内容", outputPreview = "产出内容",
            parentAgentId = "42"
        )
        val encoded = json.encodeToString(AgentCardUi.serializer(), card)
        assertTrue(encoded.contains("\"subagent\""))
        assertTrue(encoded.contains("\"parentAgentId\":\"42\""))
        val decoded = json.decodeFromString(AgentCardUi.serializer(), encoded)
        val back = decoded as AgentCardUi.SubAgent
        assertEquals("sa-1", back.subagentId)
        assertEquals(AgentCardUi.SubAgent.STATUS_DONE, back.status)
        assertEquals("42", back.parentAgentId)
        assertEquals("写条目", back.task)
    }

    // ────────────────────────── 4. 独立上下文与预算 ──────────────────────────

    @Test
    fun `sub agent messages are independent bounded and style aware`() {
        val runner = SubAgentRunner(
            realApiService = mockk(),
            chatEngine = mockk(),
            preferencesRepository = mockk()
        )
        val longContext = (1..3000).joinToString("") { "段$it；" } // 超过 4000 字符预算，尾段唯一可判
        val longPersona = (1..1000).joinToString("") { "格$it；" } // 超过 1200 字符预算，尾段唯一可判
        val messages = runner.buildSubAgentMessages(
            task = "写一条世界书",
            cardContext = longContext,
            personaPrompt = longPersona,
            customPrompt = "世界书单条 ≤300 字。"
        )

        // 独立上下文契约：仅 system + user 两条，user 只有任务本身
        assertEquals(2, messages.size)
        assertEquals("system", messages[0].role)
        assertEquals("user", messages[1].role)
        assertEquals("写一条世界书", messages[1].content)

        val system = messages[0].content!!
        assertTrue(system.contains("制卡子代理"))
        // 预算：卡上下文截断到 4000（尾段被裁掉）
        assertTrue(system.contains("段1；"))
        assertFalse(system.contains(longContext.takeLast(100)))
        // 人格注入且截断到 1200
        assertTrue(system.contains("灵魂"))
        assertFalse(system.contains(longPersona.takeLast(100)))
        // 用户规则注入
        assertTrue(system.contains("用户的创作规则"))
        assertTrue(system.contains("世界书单条 ≤300 字。"))
    }
}
