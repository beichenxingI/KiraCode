package com.kirakira.kiracode

import android.util.Log
import com.kirakira.kiracode.data.remote.dto.ChatMessage as DtoChatMessage
import com.kirakira.kiracode.data.remote.real.ApiConfig
import com.kirakira.kiracode.domain.agent.AgentController
import com.kirakira.kiracode.domain.agent.ToolRegistry
import com.kirakira.kiracode.domain.agent.core.AgentEvent
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 幽灵占位 BUG 修复验证（P1：currentPlaceholderId 成员/局部变量分裂）：
 *
 * 1. 创建首个占位；
 * 2. 收到 StreamDeltaEvent 后合入占位（32ms 帧批）；
 * 3. 不产生第二个空占位（ensurePlaceholder 与 dispatchSend 使用同一成员变量）；
 * 4. 正常完成后没有 isStreaming=true 且内容为空的消息；
 * 5. 异常与取消后也没有空流式占位残留。
 */
class WorkspacePlaceholderTest {

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

    /** 流式 delta 挂起点：取消用例中让 agent 流停在半途且不消耗虚拟时间 */
    private val neverCompleted = CompletableDeferred<Unit>()

    @After
    fun tearDown() {
        unmockkAll()
        Dispatchers.resetMain()
    }

    /** PreferencesRepository 的 Flow 属性全部显式 stub（relaxed Flow 的 first() 会永久挂起） */
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

    /** stub AgentController.startWithMessages 返回自定义事件流 */
    private fun stubAgentEventFlow(block: suspend FlowCollector<AgentEvent>.() -> Unit) {
        coEvery {
            agentController.startWithMessages(any(), any(), any(), any(), any(), any(), any())
        } returns flow(block)
    }

    /** JVM 单元测试中 android.util.Log 未实现 → 静态 mock（mockk matcher 亦匹配 null 实参） */
    private fun stubAndroidLog() {
        mockkStatic(Log::class)
        every { Log.v(any<String>(), any<String>()) } returns 0
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<Throwable>()) } returns 0
        every { Log.w(any<String>(), any<String>(), any<Throwable>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any<Throwable>()) } returns 0
        // SystemClock.elapsedRealtime：JVM 默认返回 0 会让 VM 32ms 帧批门不开，返回非零常量
        mockkStatic(android.os.SystemClock::class)
        every { android.os.SystemClock.elapsedRealtime() } returns 1_000L
    }

    /** 构建被测 VM（必须在 runTest 内、setMain 之后创建，让 init 协程共享测试调度器） */
    private fun createViewModel(): WorkspaceViewModel {
        stubAndroidLog()
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

    /** 反射读取私有 agentJob（switchCard/startNewSession 的 agentJob?.cancel() 等价路径） */
    private fun currentAgentJob(vm: WorkspaceViewModel): Job? {
        val f = WorkspaceViewModel::class.java.getDeclaredField("agentJob")
        f.isAccessible = true
        return f.get(vm) as Job?
    }

    // ==================== 用例 1：正常流 ====================

    @Test
    fun `首个占位创建_StreamDelta合入_不产生第二个空占位_完成后无空流式残留`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = createViewModel()
        stubAgentEventFlow {
            emit(StreamDeltaEvent(text = "你"))
            emit(StreamDeltaEvent(text = "好"))
            emit(ThinkAction(thought = "完成回复"))
        }

        vm.sendMessage("测试消息")
        advanceUntilIdle()

        val state = vm.uiState.value as ChatUiState.Ready
        // 1. 首个占位创建 + 用户消息，共 2 条（修复前 ensurePlaceholder 会额外追加幽灵空占位 → 3 条）
        assertEquals(2, state.messages.size)
        assertEquals("user", state.messages[0].role)
        assertEquals("测试消息", state.messages[0].content)
        // 2. StreamDelta 已合入占位（ThinkAction 收束时以 fullRaw="你好" 刷出，finishAssistant 以 displayText="完成回复" 定稿）
        assertEquals("assistant", state.messages[1].role)
        assertEquals("完成回复", state.messages[1].content)
        // 3+4. 完成后没有任何 isStreaming=true 的消息（更没有 isStreaming 且内容为空）
        assertTrue(state.messages.none { it.isStreaming })
        assertTrue(state.messages.none { it.isStreaming && it.content.isBlank() })
        assertFalse(state.isSending)
    }

    // ==================== 用例 1b：第二轮不复用已定稿占位 ====================

    @Test
    fun `第二轮流式不复用已定稿占位_第一轮正文保留`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = createViewModel()
        stubAgentEventFlow {
            emit(StreamDeltaEvent(text = "第一轮"))
            emit(ThinkAction(thought = "第一轮"))
            emit(StreamDeltaEvent(text = "第二轮"))
            emit(ThinkAction(thought = "第二轮"))
        }

        vm.sendMessage("测试消息")
        advanceUntilIdle()

        val state = vm.uiState.value as ChatUiState.Ready
        val assistant = state.messages.filter { it.role == "assistant" }
        assertEquals(2, assistant.size)
        assertEquals("第一轮", assistant[0].content)
        assertEquals("第二轮", assistant[1].content)
        assertTrue(state.messages.none { it.isStreaming })
    }

    // ==================== 用例 2：异常后无空流式占位残留 ====================

    @Test
    fun `异常后无空流式占位残留`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = createViewModel()
        stubAgentEventFlow {
            emit(StreamDeltaEvent(text = "部分"))
            throw RuntimeException("内核崩溃")
        }

        vm.sendMessage("测试消息")
        advanceUntilIdle()

        val state = vm.uiState.value as ChatUiState.Ready
        // 异常清理：空占位移除、非空占位收束（isStreaming=false 保留）
        assertTrue(state.messages.none { it.isStreaming })
        assertTrue(state.messages.none { it.isStreaming && it.content.isBlank() })
        // 已流出的部分文本被收束保留
        assertTrue(state.messages.any { it.role == "assistant" && it.content == "部分" && !it.isStreaming })
        assertEquals(false, state.isSending)
    }

    // ==================== 用例 3：取消后无空流式占位残留 ====================

    @Test
    fun `取消后无空流式占位残留`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = createViewModel()
        stubAgentEventFlow {
            emit(StreamDeltaEvent(text = "部分文本"))
            neverCompleted.await() // 挂起，不消耗虚拟时间
        }

        vm.sendMessage("测试消息")
        advanceUntilIdle()

        var state = vm.uiState.value as ChatUiState.Ready
        assertTrue("占位应处于流式态", state.messages.any { it.isStreaming })

        // 取消 agentJob（switchCard/startNewSession 的 agentJob?.cancel() 等价路径）
        currentAgentJob(vm)?.cancel()
        runCurrent()

        state = vm.uiState.value as ChatUiState.Ready
        // 取消清理：空占位移除、非空占位收束
        assertTrue("取消后不得残留空流式占位", state.messages.none { it.isStreaming && it.content.isBlank() })
        assertTrue("取消后不得残留任何流式占位", state.messages.none { it.isStreaming })
        assertFalse("取消后 isSending 复位", state.isSending)
    }
}
