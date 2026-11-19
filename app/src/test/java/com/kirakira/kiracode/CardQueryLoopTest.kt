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
import com.kirakira.kiracode.domain.agent.core.BudgetExceededAction
import com.kirakira.kiracode.domain.agent.core.CardObservation
import com.kirakira.kiracode.domain.agent.core.FinishAction
import com.kirakira.kiracode.domain.agent.core.UserMessage
import com.kirakira.kiracode.domain.agent.tools.CardFunctions
import com.kirakira.kiracode.domain.model.CharacterCard
import com.kirakira.kiracode.domain.model.WorkMode
import com.kirakira.kiracode.domain.model.st.StBookEntry
import com.kirakira.kiracode.domain.model.st.StCharacterBook
import com.kirakira.kiracode.domain.usecase.ChatEngine
import com.kirakira.kiracode.domain.usecase.SubAgentRunner
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 大卡按需读取——主循环集成测试（阶段 2）：
 * 主 Agent 调用 query_card_entries / get_card_entry → 系统执行只读检索 →
 * 结果以匹配 tool_call_id 的 tool role 回注；只读操作不修改角色卡。
 */
class CardQueryLoopTest {

    private fun config() = ApiConfig(apiKey = "k", baseUrl = "http://test.local", model = "test-model")

    private fun card() = CharacterCard(
        name = "汐音",
        characterBook = StCharacterBook(entries = listOf(
            StBookEntry(id = 12, comment = "樱华高中", keys = listOf("校园"), content = "校舍与蝉鸣……")
        ))
    )

    @Test
    fun `query and get entries return matched tool messages without mutating card`() = runBlocking {
        val api = mockk<RealApiService>()
        val runner = mockk<SubAgentRunner>()
        val chatEngine = mockk<ChatEngine>(relaxed = true)
        val searchChain = mockk<SearchProviderChain>(relaxed = true)
        val fetcher = mockk<WebpageFetcher>(relaxed = true)
        val registry = ToolRegistry { card, ops -> Result.success(CardOperationApplier.apply(card, ops)) }

        val card = card()
        registry.setCurrentCard(card)

        val captured = mutableListOf<List<com.kirakira.kiracode.data.remote.dto.ChatMessage>>()
        val queryCall = DtoToolCall(
            id = "call_q1",
            function = DtoFunctionCall("query_card_entries", """{"kind":"worldbook","keywords":"校园"}""")
        )
        val getCall = DtoToolCall(
            id = "call_g1",
            function = DtoFunctionCall("get_card_entry", """{"kind":"worldbook","entry_id":"#12"}""")
        )
        every { api.chatCompletionStream(any(), any(), any()) } answers {
            captured.add(firstArg())
            when (captured.size) {
                1 -> flowOf(RealApiService.SseEvent.ToolCalls(listOf(queryCall)))
                2 -> flowOf(RealApiService.SseEvent.ToolCalls(listOf(getCall)))
                else -> flowOf(RealApiService.SseEvent.Content("已读取条目。请问还需要调整吗？"))
            }
        }

        val controller = AgentController(api, registry, chatEngine, searchChain, fetcher, runner)
        val events = controller.startWithMessages(
            messages = listOf(UserMessage("读取校园相关条目")),
            tools = CardFunctions.getAllFunctions(),
            streaming = true,
            config = config(),
            mode = WorkMode.YOLO
        ).toList(mutableListOf())

        // 查询与读取均有成功观测（用户可见"读取了哪条条目"）
        val observations = events.filterIsInstance<CardObservation>()
        assertTrue(observations.any { !it.isError && it.content.contains("条目检索完成") })
        assertTrue(observations.any { !it.isError && it.content.contains("樱华高中") && it.content.contains("校舍") })

        // 回注配对：第 2/3 轮请求历史分别包含与 call_q1/call_g1 配对的 tool 消息
        val qMsg = captured[1].single { it.role == "tool" && it.tool_call_id == "call_q1" }
        assertTrue(qMsg.content!!.contains("#12"))
        val gMsg = captured[2].single { it.role == "tool" && it.tool_call_id == "call_g1" }
        assertTrue(gMsg.content!!.contains("校舍"))

        // 只读：工作卡未被修改（内容与条目数不变）
        val after = registry.getCurrentCard()!!
        assertEquals(card, after)
        assertEquals(1, after.characterBook!!.entries.size)
    }

    @Test
    fun `get entry with unknown id returns explicit error tool message`() = runBlocking {
        val api = mockk<RealApiService>()
        val runner = mockk<SubAgentRunner>()
        val chatEngine = mockk<ChatEngine>(relaxed = true)
        val searchChain = mockk<SearchProviderChain>(relaxed = true)
        val fetcher = mockk<WebpageFetcher>(relaxed = true)
        val registry = ToolRegistry { card, ops -> Result.success(CardOperationApplier.apply(card, ops)) }
        registry.setCurrentCard(card())

        val captured = mutableListOf<List<com.kirakira.kiracode.data.remote.dto.ChatMessage>>()
        val badCall = DtoToolCall(
            id = "call_bad",
            function = DtoFunctionCall("get_card_entry", """{"kind":"worldbook","entry_id":"99"}""")
        )
        every { api.chatCompletionStream(any(), any(), any()) } answers {
            captured.add(firstArg())
            if (captured.size == 1) flowOf(RealApiService.SseEvent.ToolCalls(listOf(badCall)))
            else flowOf(RealApiService.SseEvent.Content("条目不存在。请问要重新检索吗？"))
        }

        val controller = AgentController(api, registry, chatEngine, searchChain, fetcher, runner)
        val events = controller.startWithMessages(
            messages = listOf(UserMessage("读条目99")),
            tools = CardFunctions.getAllFunctions(),
            streaming = true,
            config = config(),
            mode = WorkMode.YOLO
        ).toList(mutableListOf())

        // 未命中 → 明确错误（观测 + 配对 ToolMessage），主循环继续
        assertTrue(events.filterIsInstance<CardObservation>().any { it.isError && it.content.contains("未找到") })
        val errMsg = captured[1].single { it.role == "tool" && it.tool_call_id == "call_bad" }
        assertTrue(errMsg.content!!.contains("未找到"))
    }

    @Test
    fun `non tool text reply stops loop without a second request`() = runBlocking {
        val api = mockk<RealApiService>()
        val runner = mockk<SubAgentRunner>()
        val chatEngine = mockk<ChatEngine>(relaxed = true)
        val searchChain = mockk<SearchProviderChain>(relaxed = true)
        val fetcher = mockk<WebpageFetcher>(relaxed = true)
        val registry = ToolRegistry { card, ops -> Result.success(CardOperationApplier.apply(card, ops)) }
        registry.setCurrentCard(card())

        var requestCount = 0
        every { api.chatCompletionStream(any(), any(), any()) } answers {
            requestCount++
            flowOf(RealApiService.SseEvent.Content("这张卡整体不错，但世界书触发词偏少。"))
        }

        val controller = AgentController(api, registry, chatEngine, searchChain, fetcher, runner)
        val events = controller.startWithMessages(
            messages = listOf(UserMessage("这张卡质量怎么样")),
            tools = CardFunctions.getAllFunctions(),
            streaming = true,
            config = config(),
            mode = WorkMode.YOLO
        ).toList(mutableListOf())

        assertEquals("纯文本回复不应触发第二轮请求", 1, requestCount)
        assertTrue(events.filterIsInstance<FinishAction>().any {
            it.outputs["result"]?.contains("整体不错") == true
        })
    }

    @Test
    fun `unlimited tool calls bypass budget decision past base iterations`() = runBlocking {
        val api = mockk<RealApiService>()
        val runner = mockk<SubAgentRunner>()
        val chatEngine = mockk<ChatEngine>(relaxed = true)
        val searchChain = mockk<SearchProviderChain>(relaxed = true)
        val fetcher = mockk<WebpageFetcher>(relaxed = true)
        val registry = ToolRegistry { card, ops -> Result.success(CardOperationApplier.apply(card, ops)) }
        registry.setCurrentCard(card())

        val queryCall = DtoToolCall(
            id = "call_q",
            function = DtoFunctionCall("query_card_entries", """{"kind":"worldbook","keywords":"校园"}""")
        )
        var calls = 0
        every { api.chatCompletionStream(any(), any(), any()) } answers {
            calls++
            if (calls <= 6) {
                flowOf(RealApiService.SseEvent.ToolCalls(listOf(queryCall)))
            } else {
                flowOf(RealApiService.SseEvent.Content("已完成。"))
            }
        }

        val controller = AgentController(api, registry, chatEngine, searchChain, fetcher, runner)
        val events = controller.startWithMessages(
            messages = listOf(UserMessage("连续检索六轮")),
            tools = CardFunctions.getAllFunctions(),
            streaming = true,
            config = config(),
            mode = WorkMode.YOLO,
            unlimitedToolCalls = true
        ).toList(mutableListOf())

        assertEquals("解禁后应越过 BASE_ITERATIONS=5", 7, calls)
        assertTrue(events.none { it is BudgetExceededAction })
        assertTrue(events.filterIsInstance<FinishAction>().isNotEmpty())
    }
}
