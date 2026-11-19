package com.kirakira.kiracode

import android.os.SystemClock
import android.util.Log
import com.kirakira.kiracode.data.remote.dto.ChatMessage as DtoChatMessage
import com.kirakira.kiracode.data.remote.real.ApiConfig
import com.kirakira.kiracode.domain.agent.AgentController
import com.kirakira.kiracode.domain.agent.ToolRegistry
import com.kirakira.kiracode.domain.agent.core.AgentEvent
import com.kirakira.kiracode.domain.agent.core.ReasoningCompleteEvent
import com.kirakira.kiracode.domain.agent.core.ReasoningDeltaEvent
import com.kirakira.kiracode.domain.agent.core.StreamDeltaEvent
import com.kirakira.kiracode.domain.agent.core.ThinkAction
import com.kirakira.kiracode.domain.model.ChatMessage
import com.kirakira.kiracode.domain.model.Session
import com.kirakira.kiracode.domain.repository.CharacterCardRepository
import com.kirakira.kiracode.domain.repository.MessageRepository
import com.kirakira.kiracode.domain.repository.PreferenceRepository
import com.kirakira.kiracode.domain.repository.PreferencesRepository
import com.kirakira.kiracode.domain.repository.ReferenceCardRepository
import com.kirakira.kiracode.domain.repository.SessionRepository
import com.kirakira.kiracode.domain.usecase.ChatEngine
import com.kirakira.kiracode.ui.workspace.ChatUiState
import com.kirakira.kiracode.ui.workspace.WorkspaceViewModel
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicLong

/**
 * 流式显示回归（端到端延迟修复）：
 *
 * 1. 多个 ReasoningDeltaEvent 后 liveReasoning 累积增长，定稿写入占位 thinking；
 * 2. 多个 StreamDeltaEvent 后正文逐步提交（32ms 帧批 + 收尾无条件刷出，最终 delta 不丢）；
 * 3. reasoning-only 轮（contentLength=0）不产生伪正文，thinking 保留；
 * 4. 关键回归：轮间（上一轮 ThinkAction 收束后）新一轮 reasoning delta 恢复 isSending=true——
 *    修复前 ReasoningDelta 分支不恢复 isSending，导致纯思考轮期间 UI 完全静默；
 * 5. 完成后状态收束：isSending=false、无 isStreaming 残留、liveReasoning 清空。
 */
class WorkspaceStreamingDisplayTest {

    private val config = ApiConfig(
        apiKey = "test-key",
        baseUrl = "https://api.test.local",
        model = "test-model"
    )

    private val sessionRepository: SessionRepository = mockk(relaxed = true)
    private val messageRepository: MessageRepository = mockk(relaxed = true)
    private val chatEngine: ChatEngine = mockk(relaxed = true)
    private val preferencesRepository: PreferencesRepository = mockk(relaxed = true)
    private val preferenceRepository: PreferenceRepository = mockk(relaxed = true)
    private val referenceCardRepository: ReferenceCardRepository = mockk(relaxed = true)
    private val characterCardRepository: CharacterCardRepository = mockk(relaxed = true)
    private val agentController: AgentController = mockk(relaxed = true)
    private val toolRegistry: ToolRegistry = mockk(relaxed = true)

    /** 可控单调时钟：每次 elapsedRealtime() 调用推进 clockStep（默认 50ms，让 32ms 帧批门必开）；
     *  爆发流测试将 clockStep 置 0 冻结时钟，模拟同毫秒批量 delta */
    private val fakeClock = AtomicLong(0L)
    private val clockStep = AtomicLong(50L)

    @After
    fun tearDown() {
        unmockkAll()
        Dispatchers.resetMain()
    }

    private fun stubPreferenceFlows() {
        every { preferencesRepository.modelConfigs } returns MutableStateFlow(emptyList())
        every { preferencesRepository.activeModelConfigId } returns MutableStateFlow(null)
        every { preferencesRepository.selectedModel } returns MutableStateFlow(null)
        every { preferencesRepository.webSearchEnabled } returns MutableStateFlow(false)
        every { preferencesRepository.cardMasterEnabled } returns MutableStateFlow(true)
        every { preferencesRepository.regexGenerationMode } returns MutableStateFlow(
            com.kirakira.kiracode.domain.model.RegexGenerationMode.FOLDABLE_TAGS
        )
        every { preferencesRepository.unlimitedToolCalls } returns MutableStateFlow(false)
        every { preferencesRepository.reasoningEffortPref } returns MutableStateFlow(null)
        every { preferencesRepository.contextWindowTokens } returns MutableStateFlow(128_000)
        every { preferencesRepository.contextSummarizeThreshold } returns MutableStateFlow(0.8f)
        every { preferencesRepository.reasoningProbeJson } returns MutableStateFlow("{}")
        every { preferencesRepository.personasJson } returns MutableStateFlow("[]")
        every { preferencesRepository.activePersonaId } returns MutableStateFlow(null)
        every { preferencesRepository.customPromptSetsJson } returns MutableStateFlow("[]")
        every { preferencesRepository.activeCustomPromptSetId } returns MutableStateFlow(null)
        every { preferencesRepository.isFirstLaunch } returns MutableStateFlow(false)
        coEvery { preferenceRepository.ensureDefaults() } returns Unit
        every { preferenceRepository.observeDimensions() } returns MutableStateFlow(emptyList())
    }

    private fun stubRepositories() {
        coEvery { sessionRepository.createSession(any()) } returns Result.success(1L)
        coEvery { sessionRepository.getSession(any()) } returns Result.success(
            Session(title = "测试会话", workMode = "YOLO")
        )
        coEvery { messageRepository.getMessagesBySessionId(any(), any(), any()) } returns Result.success(emptyList())
        coEvery { messageRepository.insertMessage(any()) } returns Result.success(1L)
        coEvery { referenceCardRepository.getAllReferenceCards(any(), any()) } returns Result.success(emptyList())
        every { referenceCardRepository.observeReferenceCards() } returns MutableStateFlow(emptyList())
    }

    private fun stubEngineAndAgent() {
        coEvery { chatEngine.resolveActiveConfig() } returns config
        coEvery {
            chatEngine.prepareChatMessages(
                any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
            )
        } returns Pair(listOf(DtoChatMessage(role = "system", content = "sys")), config)
        every { toolRegistry.getTools() } returns emptyList()
    }

    private fun stubAgentEventFlow(block: suspend FlowCollector<AgentEvent>.() -> Unit) {
        coEvery {
            agentController.startWithMessages(any(), any(), any(), any(), any(), any(), any())
        } returns flow(block)
    }

    private fun stubAndroidAndClock() {
        mockkStatic(Log::class)
        every { Log.v(any<String>(), any<String>()) } returns 0
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<Throwable>()) } returns 0
        every { Log.w(any<String>(), any<String>(), any<Throwable>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any<Throwable>()) } returns 0
        // JVM 测试中 SystemClock.elapsedRealtime() 默认返回 0（returnDefaultValues），
        // 32ms 帧批门永远不开——用可控时钟驱动节流路径
        mockkStatic(SystemClock::class)
        every { SystemClock.elapsedRealtime() } answers { fakeClock.addAndGet(clockStep.get()) }
    }

    private fun createViewModel(): WorkspaceViewModel {
        stubAndroidAndClock()
        stubPreferenceFlows()
        stubRepositories()
        stubEngineAndAgent()
        return WorkspaceViewModel(
            sessionRepository = sessionRepository,
            messageRepository = messageRepository,
            chatEngine = chatEngine,
            summarizeContextUseCase = mockk(relaxed = true),
            preferencesRepository = preferencesRepository,
            preferenceRepository = preferenceRepository,
            referenceCardRepository = referenceCardRepository,
            characterCardRepository = characterCardRepository,
            webSearchService = mockk(relaxed = true),
            trainPersonaUseCase = mockk(relaxed = true),
            realApiService = mockk(relaxed = true),
            agentController = agentController,
            toolRegistry = toolRegistry,
            exportCharacterCardUseCase = mockk(relaxed = true),
            appContext = mockk(relaxed = true)
        )
    }

    private fun readyState(vm: WorkspaceViewModel): ChatUiState.Ready =
        vm.uiState.value as ChatUiState.Ready

    // ==================== 用例 1：reasoning 累积与定稿 ====================

    @Test
    fun `reasoning增量累积liveReasoning_定稿写入占位thinking_不丢`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = createViewModel()
        val midStream = CompletableDeferred<Unit>()
        stubAgentEventFlow {
            emit(ReasoningDeltaEvent(text = "思考A"))
            midStream.await()
            emit(ReasoningCompleteEvent(text = "思考A全文"))
            emit(ThinkAction(thought = "回答"))
        }

        vm.sendMessage("测试消息")
        runCurrent()

        // 首个 reasoning delta 即提交（lastReasoningFlushAt=0，帧批门必开）
        assertEquals("思考A", vm.liveReasoning.value)
        assertTrue(readyState(vm).isSending)

        midStream.complete(Unit)
        advanceUntilIdle()

        val state = readyState(vm)
        // 定稿：thinking 写入占位、liveReasoning 清空、正文为 ThinkAction 内容
        assertEquals("回答", state.messages.last().content)
        assertEquals("思考A全文", state.messages.last().thinking)
        assertNull(vm.liveReasoning.value)
        assertFalse(state.isSending)
        assertTrue(state.messages.none { it.isStreaming })
    }

    // ==================== 用例 2：reasoning-only 轮与轮间 isSending 恢复 ====================

    @Test
    fun `reasoning-only轮不产生伪正文_下一轮reasoning恢复isSending`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = createViewModel()
        val round1Done = CompletableDeferred<Unit>()
        val round2Reasoning = CompletableDeferred<Unit>()
        stubAgentEventFlow {
            // round1：纯思考轮（contentLength=0，对应 ROUND_DONE round=1 contentLength=0 toolCalls=0）
            emit(ReasoningDeltaEvent(text = "R1"))
            emit(ReasoningCompleteEvent(text = "R1思维"))
            emit(ThinkAction(thought = ""))
            round1Done.await()
            // round2：先 reasoning 后正文
            emit(ReasoningDeltaEvent(text = "R2"))
            round2Reasoning.await()
            emit(ReasoningCompleteEvent(text = "R2思维"))
            emit(StreamDeltaEvent(text = "正文"))
            emit(ThinkAction(thought = "正文"))
        }

        vm.sendMessage("测试消息")
        runCurrent()

        // round1 收束后：无伪正文（修复前此处为"（本轮无数据操作）"）、thinking 保留、isSending=false
        var state = readyState(vm)
        assertEquals("", state.messages.last().content)
        assertEquals("R1思维", state.messages.last().thinking)
        assertTrue(state.messages.none { it.isStreaming })
        assertFalse(state.isSending)

        round1Done.complete(Unit)
        runCurrent()

        // 关键回归：round2 的 reasoning delta 必须恢复 isSending（修复前为 false → UI 整轮静默）
        state = readyState(vm)
        assertTrue("轮间 reasoning delta 应恢复 isSending", state.isSending)

        round2Reasoning.complete(Unit)
        advanceUntilIdle()

        // round2 定稿：正文不丢、本轮 thinking 覆盖、状态收束
        state = readyState(vm)
        assertEquals("正文", state.messages.last().content)
        assertEquals("R2思维", state.messages.last().thinking)
        assertFalse(state.isSending)
        assertNull(vm.liveReasoning.value)
        assertTrue(state.messages.none { it.isStreaming })
    }

    // ==================== 用例 3：正文逐步提交与最终 delta 不丢 ====================

    @Test
    fun `stream增量逐步提交占位_收尾不丢最终delta`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = createViewModel()
        val afterFirstDelta = CompletableDeferred<Unit>()
        stubAgentEventFlow {
            emit(StreamDeltaEvent(text = "你"))
            afterFirstDelta.await()
            emit(StreamDeltaEvent(text = "好"))
            emit(ThinkAction(thought = "你好"))
        }

        vm.sendMessage("测试消息")
        runCurrent()

        // 首个 delta 即入占位（帧批门：lastStreamFlushAt=0）
        val state = readyState(vm)
        assertEquals("你", state.messages.last().content)
        assertTrue(state.messages.last().isStreaming)
        // 无 reasoning 时 liveReasoning 保持 null（不产生空的"正在思考"块）
        assertNull(vm.liveReasoning.value)

        afterFirstDelta.complete(Unit)
        advanceUntilIdle()

        // 收尾无条件刷出：最终 delta 不丢
        val final = readyState(vm)
        assertEquals("你好", final.messages.last().content)
        assertFalse(final.messages.last().isStreaming)
        assertFalse(final.isSending)
    }

    // ==================== 用例 4：reasoning 爆发尾部随 content 刷新刷出 ====================

    @Test
    fun `reasoning爆发尾部不再有reasoning delta时_随首个content刷新一并提交`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = createViewModel()
        // 冻结时钟：模拟同毫秒爆发（除首个 delta 外，其余 delta 全部撞上 32ms 帧批门）
        fakeClock.set(1_000L)
        clockStep.set(0L)
        val contentStarted = CompletableDeferred<Unit>()
        stubAgentEventFlow {
            // reasoning 爆发：首个 delta 刷出（1000-0≥32），R2/R3 被帧批门拦下滞留在 thinkingRaw
            emit(ReasoningDeltaEvent(text = "R1"))
            emit(ReasoningDeltaEvent(text = "R2"))
            emit(ReasoningDeltaEvent(text = "R3"))
            emit(StreamDeltaEvent(text = "正文"))
            contentStarted.await()
            emit(ThinkAction(thought = "正文"))
        }

        vm.sendMessage("测试消息")
        runCurrent()

        // 修复点：首个 content delta 刷新时，滞留的 reasoning 尾批（R2/R3）一并提交
        assertEquals("R1R2R3", vm.liveReasoning.value)
        val state = readyState(vm)
        assertEquals("正文", state.messages.last().content)
        assertTrue(state.messages.last().isStreaming)

        contentStarted.complete(Unit)
        advanceUntilIdle()

        // 收束：thinking 定稿为完整 reasoning、正文不丢、状态归位
        val final = readyState(vm)
        assertEquals("正文", final.messages.last().content)
        assertEquals("R1R2R3", final.messages.last().thinking)
        assertFalse(final.isSending)
        assertTrue(final.messages.none { it.isStreaming })
    }
}
