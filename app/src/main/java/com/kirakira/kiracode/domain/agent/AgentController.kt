package com.kirakira.kiracode.domain.agent

import android.os.SystemClock
import android.util.Log
import com.kirakira.kiracode.data.remote.WebpageFetcher
import com.kirakira.kiracode.data.remote.search.SearchChainResult
import com.kirakira.kiracode.data.remote.search.SearchProviderChain
import com.kirakira.kiracode.data.remote.dto.ToolCall as DtoToolCall
import com.kirakira.kiracode.data.remote.real.ApiConfig
import com.kirakira.kiracode.data.remote.real.RealApiService
import com.kirakira.kiracode.domain.agent.core.AgentEvent
import com.kirakira.kiracode.domain.agent.core.AskUserAction
import com.kirakira.kiracode.domain.agent.core.AssistantMessage
import com.kirakira.kiracode.domain.agent.core.AssistantTextEvent
import com.kirakira.kiracode.domain.agent.core.AutoDraftEvent
import com.kirakira.kiracode.domain.agent.core.BudgetExceededAction
import com.kirakira.kiracode.domain.agent.core.BudgetWarningEvent
import com.kirakira.kiracode.domain.agent.core.CardObservation
import com.kirakira.kiracode.domain.agent.core.DiffProposalAction
import com.kirakira.kiracode.domain.agent.core.FinishAction
import com.kirakira.kiracode.domain.agent.core.FunctionCall
import com.kirakira.kiracode.domain.agent.core.generateEventId
import com.kirakira.kiracode.util.AppLogger
import com.kirakira.kiracode.domain.agent.core.Message
import com.kirakira.kiracode.domain.exception.ApiException
import com.kirakira.kiracode.domain.agent.core.PlanAction
import com.kirakira.kiracode.domain.agent.core.PlanStepData
import com.kirakira.kiracode.domain.agent.core.ReasoningCompleteEvent
import com.kirakira.kiracode.domain.agent.core.ReasoningDeltaEvent
import com.kirakira.kiracode.domain.agent.core.StepProgressEvent
import com.kirakira.kiracode.domain.agent.core.StreamDeltaEvent
import com.kirakira.kiracode.domain.agent.core.SubAgentCompletedEvent
import com.kirakira.kiracode.domain.agent.core.SubAgentFailedEvent
import com.kirakira.kiracode.domain.agent.core.SubAgentStartedEvent
import com.kirakira.kiracode.domain.agent.core.SystemMessage
import com.kirakira.kiracode.domain.agent.core.ThinkAction
import com.kirakira.kiracode.domain.agent.core.ToolCall
import com.kirakira.kiracode.domain.agent.core.ToolMessage
import com.kirakira.kiracode.domain.agent.core.UpdateCardAction
import com.kirakira.kiracode.domain.agent.core.UserMessage
import com.kirakira.kiracode.domain.model.AuditReport
import com.kirakira.kiracode.domain.model.AuditScope
import com.kirakira.kiracode.domain.model.WorkMode
import com.kirakira.kiracode.domain.usecase.ChatEngine
import com.kirakira.kiracode.domain.usecase.SubAgentRunner
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class AgentController(
    private val apiService: RealApiService,
    private val toolRegistry: ToolRegistry,
    private val chatEngine: ChatEngine,
    private val searchChain: SearchProviderChain,
    private val webpageFetcher: WebpageFetcher,
    private val subAgentRunner: SubAgentRunner
) {
    companion object {
        private const val TAG = "AgentController"
        /** 基础迭代预算（无计划时）；有计划时按步骤数动态计算 */
        private const val BASE_ITERATIONS = 5
        /** 解禁工具调用时的硬性轮次上限，防止真正死循环 */
        private const val HARD_ITERATION_LIMIT = 200
        /** 每个计划步骤分配的轮次 */
        private const val ITERATIONS_PER_STEP = 2
        /** 单步超限后的自动追加轮次 */
        private const val STEP_OVERFLOW_BONUS = 5
        /** 单步自动追加最多几次 */
        private const val MAX_STEP_BONUS_COUNT = 1
    /** 连续无工具调用多少轮后注入 nudge 提示（再多一轮仍不调用则终止） */
    private const val NUDGE_AFTER_ROUNDS = 2
        /** V3.1 批次4任务1：计划未完成时的最大续跑提示次数 */
        private const val PLAN_NUDGE_MAX = 4
        /** 非流式兜底的频控退避与重试上限（对齐旧 chatEngine.send 路径的重试策略） */
        private const val MAX_FALLBACK_RETRIES = 2
        private const val FALLBACK_BASE_DELAY_MS = 1500L
        private const val FALLBACK_JITTER_MS = 500
        /** HTTP 500 专属：上游 bad_response 常为瞬态，更长退避 + 更多重试 */
        private const val MAX_500_FALLBACK_RETRIES = 3
        private const val RETRY_500_BASE_DELAY_MS = 3000L
        /** 轮间延迟（实测 round2 间隔 <0.05s 会稳定触发上游 500） */
        private const val ROUND_DELAY_BASE_MS = 800L
        private const val ROUND_DELAY_JITTER_MS = 400

        /** 子代理产出回注主 Agent 的最大长度（超长截断，保护主上下文预算） */
        private const val SUBAGENT_TOOL_OUTPUT_MAX = 12000
        /** 子代理事件卡携带的完整产出上限（UI 展开用；超出部分仅主 Agent 可见） */
        private const val SUBAGENT_CARD_OUTPUT_MAX = 4000
        /** 子代理事件卡一行预览长度 */
        private const val SUBAGENT_PREVIEW_MAX = 120
        /** 子代理工具名（与 CardFunctions.spawnSubagent 定义一致） */
        const val TOOL_SPAWN_SUBAGENT = "spawn_subagent"
        /** 只读查询工具名（大卡按需读取） */
        const val TOOL_QUERY_CARD_ENTRIES = "query_card_entries"
        const val TOOL_GET_CARD_ENTRY = "get_card_entry"
        const val TOOL_AUDIT_CARD = "audit_card"

        // ── V2/V3 交互协议标记（ViewModel 按用户点击生成，内核据此解释） ──
        const val MARK_SKIP = "（跳过）"
        const val MARK_CONFIRM = "（确认）"
        const val MARK_MODIFY = "（修改）"
        const val MARK_ACCEPT = "（接受）"
        const val MARK_REJECT = "（拒绝）"
        const val MARK_CONTINUE = "（继续改）"
        /** 预算决策：继续追加轮次 / 保存当前进度 / 放弃任务 */
        const val MARK_BUDGET_CONTINUE = "（预算继续）"
        const val MARK_BUDGET_SAVE = "（预算保存）"
        const val MARK_BUDGET_ABANDON = "（预算放弃）"

        /** 强制推进阈值：连续 N 次无有效输入（跳过/空白）→ 进入自动草稿模式 */
        const val FORCED_PROGRESS_AFTER = 3
    }

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    // ── V2/V3 交互挂起状态（每次 startWithMessages 重置） ──

    /** 当前挂起交互的回复通道：ask_user 追问 / submit_plan 计划确认 / diff 审查共用 */
    private var pendingResponse: CompletableDeferred<String>? = null

    /** 连续无有效输入计数（跳过/空白回答），达到阈值触发自动草稿 */
    private var noInputCount = 0

    /** 自动草稿模式：AI 直接补全剩余内容并标注 ai_guess */
    private var autoDraft = false
    /** V3.1 任务7：当前工作模式（AGENT=Diff 逐条挂起确认；YOLO=直接执行仅展示对照） */
    private var sessionMode: WorkMode = WorkMode.AGENT
    private var regexGenerationMode: com.kirakira.kiracode.domain.model.RegexGenerationMode =
        com.kirakira.kiracode.domain.model.RegexGenerationMode.DEFAULT

    /** P0：文本协议降级路径——provider 拒绝 tools 后置位；置位后不再携带 tools，文本协议兜底执行仅在降级路径生效 */
    private var textProtocolFallback = false
    /** P0：降级协议说明是否已注入本会话历史（每次任务最多注入一次） */
    private var fallbackNoticeInjected = false

    /**
     * P0：本轮任务的参考卡（供 spawn_subagent 同源注入风格样本，不直接写卡）。
     * 由 WorkspaceViewModel 在任务启动时设置（dispatchSend 与 toolRegistry.setCurrentCard 同步）。
     */
    private var currentReferenceCard: com.kirakira.kiracode.domain.model.CharacterCard? = null

    fun setReferenceCard(card: com.kirakira.kiracode.domain.model.CharacterCard?) {
        currentReferenceCard = card
    }

    /** 已确认的计划步骤（1-based 序号 → 标题） */
    private var planSteps: List<PlanStepData> = emptyList()
    private val completedSteps = mutableSetOf<Int>()
    /** V3.1 批次4任务1：计划续跑提示计数 */
    private var planNudgeCount = 0

    // ── 动态预算状态（每次 startWithMessages 重置） ──
    private var dynamicMaxIterations = BASE_ITERATIONS
    private var stepBonusUsed = 0
    private var currentStepStartIteration = 0
    private var currentIterations = 0
    private var budgetWarned = false

    /**
     * 用户/上层对当前挂起交互的回复（线程安全，供 ViewModel 在用户点击时调用）。
     * 无挂起交互时静默忽略。
     */
    fun respondToAgent(response: String) {
        pendingResponse?.complete(response)
    }

    /** 挂起等待用户回复（ViewModel 端 respondToAgent 完成后继续） */
    private suspend fun awaitUserResponse(): String {
        val deferred = CompletableDeferred<String>()
        pendingResponse = deferred
        return try {
            deferred.await()
        } finally {
            pendingResponse = null
        }
    }

    /** 一轮流式响应的结果（全文/思考/累积的 tool_calls） */
    private class RoundResult(
        val fullText: String,
        val thinkingText: String,
        val toolCalls: List<DtoToolCall>?
    )

    /** 收集一轮流式响应：增量上抛 StreamDeltaEvent + tool_call 增量累积 */
    private suspend fun kotlinx.coroutines.flow.FlowCollector<AgentEvent>.runStreamRound(
        history: MutableList<Message>,
        activeConfig: ApiConfig,
        tools: List<JsonObject>?
    ): RoundResult {
        var fullRaw = ""
        var thinkingRaw = ""
        var toolCalls: List<DtoToolCall>? = null
        // 诊断：首个 delta 到达时间
        val firstDeltaNanos = LongArray(1) { 0L }
        apiService.chatCompletionStream(
            messages = history.map { it.toOpenAiMessage() },
            config = activeConfig,
            tools = tools
        ).collect { sse ->
            when (sse) {
                is RealApiService.SseEvent.Content -> {
                    if (firstDeltaNanos!![0] == 0L) {
                        firstDeltaNanos[0] = SystemClock.elapsedRealtimeNanos()
                        com.kirakira.kiracode.util.AppLogger.d("ROUND_TRACE", "first_content_delta_nanos=${firstDeltaNanos[0]}")
                    }
                    fullRaw += sse.text
                    emit(StreamDeltaEvent(text = sse.text))
                }
                is RealApiService.SseEvent.Reasoning -> {
                    if (firstDeltaNanos!![0] == 0L) {
                        firstDeltaNanos[0] = SystemClock.elapsedRealtimeNanos()
                        com.kirakira.kiracode.util.AppLogger.d("ROUND_TRACE", "first_reasoning_delta_nanos=${firstDeltaNanos[0]}")
                    }
                    thinkingRaw += sse.text
                    emit(ReasoningDeltaEvent(text = sse.text))
                }
                is RealApiService.SseEvent.ToolCalls -> toolCalls = sse.calls
                is RealApiService.SseEvent.Usage -> emit(
                    com.kirakira.kiracode.domain.agent.core.UsageEvent(
                        promptTokens = sse.promptTokens,
                        completionTokens = sse.completionTokens,
                        totalTokens = sse.totalTokens,
                        cachedTokens = sse.cachedTokens
                    )
                )
                is RealApiService.SseEvent.Error -> throw sse.e
            }
        }
        return RoundResult(fullRaw, thinkingRaw, toolCalls)
    }

    /** 运行一轮流式响应；tools 相关报错（schema 校验/不支持 function calling）时降级为无工具重试一次 */
    private suspend fun kotlinx.coroutines.flow.FlowCollector<AgentEvent>.runStreamRoundWithFallback(
        history: MutableList<Message>,
        activeConfig: ApiConfig,
        tools: List<JsonObject>?
    ): RoundResult {
        return try {
            runStreamRound(history, activeConfig, tools)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            if (tools == null || !looksLikeToolsRejection(e.message.orEmpty())) throw e
            Log.w(TAG, "tools 被提供商拒绝，降级为无工具模式重试：${e.message}")
            com.kirakira.kiracode.util.AppLogger.w("AgentController", "Tools 被拒绝，去掉 tools 重试流式")
            // P0：provider 不支持 function calling → 切换文本协议降级路径（仅注入一次降级协议说明）
            enterTextProtocolFallback(history)
            runStreamRound(history, activeConfig, null)
        }
    }

    /**
     * 进入文本协议降级路径：
     * 1. 置位 [textProtocolFallback]，本轮后续与剩余轮次不再携带 tools；
     * 2. 向历史注入一次降级协议说明（BuiltinSystemPrompt.fallbackNotice，与存活解析器严格一致）；
     * 3. 正常工具路径中的文本协议兜底执行随之关闭，避免双协议并存。
     */
    private fun enterTextProtocolFallback(history: MutableList<Message>) {
        if (textProtocolFallback) return
        textProtocolFallback = true
        if (!fallbackNoticeInjected) {
            fallbackNoticeInjected = true
            history.add(UserMessage(
                "（系统切换）当前模型或服务商不支持 function calling。从现在起改用以下文本协议完成全部修改：\n\n" +
                    com.kirakira.kiracode.domain.prompt.BuiltinSystemPrompt.fallbackNotice(sessionMode, regexGenerationMode)
            ))
        }
    }

    /** 报错是否指向 tools 被提供商拒绝（schema 校验/不支持 function calling） */
    private fun looksLikeToolsRejection(message: String?): Boolean {
        if (message.isNullOrBlank()) return false
        return Regex("tool|function|enum|schema", RegexOption.IGNORE_CASE).containsMatchIn(message)
    }

    /** 报错是否为频控/过载类（退避重试只针对这类错误） */
    private fun looksLikeRateLimit(message: String?): Boolean {
        if (message.isNullOrBlank()) return false
        val m = message.lowercase()
        return m.contains("try again") || m.contains("429") ||
            m.contains("rate limit") || m.contains("too many requests") ||
            m.contains("overloaded")
    }

    /** 是否 HTTP 500 类上游错误（优先看 ApiException.errorCode 的 HTTP_500） */
    private fun looksLike500(e: Throwable): Boolean {
        if ((e as? ApiException)?.errorCode?.contains("500") == true) return true
        val m = e.message ?: return false
        return m.contains("500") || m.contains("bad_response", ignoreCase = true)
    }

    /** 轮间延迟：避免连续请求触发上游 500（实测 round2 间隔 <0.05s 稳定 500） */
    private suspend fun interRoundDelay() {
        val roundDelay = ROUND_DELAY_BASE_MS + (0..ROUND_DELAY_JITTER_MS).random()
        com.kirakira.kiracode.util.AppLogger.d("AgentController", "轮间延迟 ${roundDelay}ms，避免上游 500")
        delay(roundDelay)
    }

    /**
     * 以预组装消息启动内核循环（聊天接入入口）。
     *
     * 循环语义：LLM 返回 tool_calls → 执行工具并把结果以 tool role 回注 → 继续；
     * 无 tool_calls → 本轮响应即完成（ThinkAction 携带全文与 token 用量），循环终止。
     *
     * @param tools 工具定义（CardFunctions JSON，来自 ToolRegistry.getTools()）；null = 无工具（纯聊天）
     * @param streaming true = SSE 流式（增量事件实时上抛）；false = 非流式（一次性，携带 token 用量）
     * @param config 显式传入生效配置；null 时用 ChatEngine.resolveActiveConfig() 解析
     */
    suspend fun startWithMessages(
        messages: List<Message>,
        tools: List<JsonObject>? = null,
        streaming: Boolean = true,
        config: ApiConfig? = null,
        mode: WorkMode = WorkMode.AGENT,
        unlimitedToolCalls: Boolean = false,
        regexGenerationMode: com.kirakira.kiracode.domain.model.RegexGenerationMode =
            com.kirakira.kiracode.domain.model.RegexGenerationMode.DEFAULT
    ): Flow<AgentEvent> = flow {
        Log.d(TAG, "Starting agent loop (streaming=$streaming, tools=${tools?.size ?: 0} 个, mode=${mode.name}, unlimited=$unlimitedToolCalls)")
        // 交互状态重置（每轮任务独立：追问计数/自动草稿/计划进度/挂起通道/工作模式/降级状态）
        noInputCount = 0
        autoDraft = false
        sessionMode = mode
        this@AgentController.regexGenerationMode = regexGenerationMode
        toolRegistry.setRegexGenerationMode(regexGenerationMode)
        planSteps = emptyList()
        planNudgeCount = 0
        completedSteps.clear()
        pendingResponse = null
        textProtocolFallback = false
        fallbackNoticeInjected = false
        val activeConfig = config ?: chatEngine.resolveActiveConfig()
        if (activeConfig == null) {
            Log.e(TAG, "No active API config")
            emit(FinishAction(outputs = mapOf("error" to "尚未配置 AI 接口，请先在模型配置中设置")))
            return@flow
        }

        val history = messages.toMutableList()
        var iterations = 0
        var noToolCallsCount = 0
        var forceBudgetDecision = false
        // 动态预算状态（成员字段供 handleSubmitPlan/handleCompleteStep 同步）
        dynamicMaxIterations = BASE_ITERATIONS
        stepBonusUsed = 0
        currentStepStartIteration = 0
        currentIterations = 0
        budgetWarned = false
        // 防重复搜索：本轮 Agent 会话内已使用的查询词（循环结束即废弃）
        val recentSearchQueries = mutableSetOf<String>()
        // 诊断：轮次计时（仅 DEBUG）
        val diagEnabled = true
        val roundTraces = mutableListOf<String>()

        while (true) {
            // ── 解禁模式：仅保留硬性轮次上限，不进入预算决策 ──
            if (unlimitedToolCalls && iterations >= HARD_ITERATION_LIMIT) {
                Log.w(TAG, "Hard iteration limit reached (unlimitedToolCalls=true, iterations=$iterations)")
                emit(FinishAction(outputs = mapOf(
                    "error" to "已达最大轮次上限（$HARD_ITERATION_LIMIT 轮），已停止"
                )))
                return@flow
            }

            // ── 预算决策：达到上限，或单步自动追加已用尽（解禁模式跳过） ──
            if (!unlimitedToolCalls && (forceBudgetDecision || iterations >= dynamicMaxIterations)) {
                forceBudgetDecision = false
                Log.w(TAG, "Budget decision required (iterations=$iterations max=$dynamicMaxIterations)")
                val completedSummary = buildCompletedFieldsSummary()
                val pendingSteps = planSteps
                    .filterIndexed { index, _ -> (index + 1) !in completedSteps }
                    .take(5)
                    .map { it.title }
                emit(BudgetExceededAction(
                    completedSummary = completedSummary,
                    pendingSteps = pendingSteps,
                    iterationsUsed = iterations,
                    toolCallId = generateEventId()
                ))
                val response = awaitUserResponse()
                when {
                    response.startsWith(MARK_BUDGET_CONTINUE) -> {
                        val extra = response.removePrefix(MARK_BUDGET_CONTINUE).trim()
                            .toIntOrNull()?.coerceIn(1, 50) ?: 5
                        dynamicMaxIterations = maxOf(
                            dynamicMaxIterations + extra,
                            iterations + 1
                        )
                        if (planSteps.isNotEmpty()) {
                            dynamicMaxIterations = maxOf(
                                dynamicMaxIterations,
                                planSteps.size * ITERATIONS_PER_STEP + 2
                            )
                        }
                        stepBonusUsed = 0
                        currentStepStartIteration = iterations
                        budgetWarned = false
                        Log.w(TAG, "用户选择继续：追加 $extra 轮，新上限=$dynamicMaxIterations")
                        history.add(UserMessage("（系统提示）用户选择继续，追加 $extra 轮预算。"))
                    }
                    response.startsWith(MARK_BUDGET_SAVE) -> {
                        emit(FinishAction(outputs = mapOf(
                            "status" to "saved",
                            "partial_result" to completedSummary,
                            "completed_iterations" to iterations.toString()
                        )))
                        return@flow
                    }
                    else -> {
                        emit(FinishAction(outputs = mapOf(
                            "status" to "abandoned",
                            "partial_result" to completedSummary,
                            "completed_iterations" to iterations.toString()
                        )))
                        return@flow
                    }
                }
            }

            // ── 单步预算：超出预计轮次时自动追加一次；再次超出转用户决策（解禁模式跳过） ──
            if (!unlimitedToolCalls && planSteps.isNotEmpty() && completedSteps.size < planSteps.size) {
                val currentStepIterations = iterations - currentStepStartIteration
                val allowedStepIterations = ITERATIONS_PER_STEP +
                        if (stepBonusUsed > 0) STEP_OVERFLOW_BONUS else 0
                if (currentStepIterations >= allowedStepIterations) {
                    if (stepBonusUsed < MAX_STEP_BONUS_COUNT) {
                        stepBonusUsed++
                        dynamicMaxIterations += STEP_OVERFLOW_BONUS
                        val currentStep = completedSteps.size + 1
                        val stepTitle = planSteps.getOrNull(currentStep - 1)?.title ?: "当前步骤"
                        Log.w(
                            TAG,
                            "步骤「$stepTitle」超出预计轮次，自动追加 $STEP_OVERFLOW_BONUS 轮"
                        )
                        history.add(UserMessage(
                            "（系统提示）步骤「$stepTitle」已超出预期轮次，" +
                                    "自动追加 $STEP_OVERFLOW_BONUS 轮继续执行。"
                        ))
                    } else {
                        forceBudgetDecision = true
                        continue
                    }
                }
            }

            iterations++
            currentIterations = iterations
            val roundStartNanos = if (diagEnabled) SystemClock.elapsedRealtimeNanos() else 0L
            var firstDeltaNanos: Long = 0
            var nudgeInjected = false
            var fallbackTriggered = false
            var retryCount = 0
            // P0：降级路径置位后不再携带 tools（文本协议模式）
            val roundTools = if (textProtocolFallback) null else tools
            Log.d(TAG, "Iteration $iterations")
            com.kirakira.kiracode.util.AppLogger.d(
                "AgentController",
                "ROUND_START round=$iterations mode=${sessionMode.name} tools=${roundTools?.size ?: 0} " +
                    "HISTORY count=${history.size} roles=[${history.joinToString(",") { it.javaClass.simpleName.removeSuffix("Message").lowercase() }}] " +
                    "TAIL=${history.lastOrNull()?.debugLabel()}"
            )

            // 软着陆：剩余轮次不足时提醒模型加速收尾（仅一次；纯聊天无预算）
            if (!unlimitedToolCalls && !budgetWarned && tools != null && iterations >= (dynamicMaxIterations * 4 / 5)) {
                budgetWarned = true
                val remaining = dynamicMaxIterations - iterations
                emit(BudgetWarningEvent(
                    remainingIterations = remaining,
                    message = "迭代预算即将用尽，剩余${remaining}轮；请尽快完成任务，或总结已完成的部分"
                ))
                history.add(UserMessage(
                    "（系统提醒）迭代预算即将用尽（剩余${remaining}轮）。请在接下来的轮次中完成任务并调用finish工具；" +
                    "如果任务无法完成，请总结已完成的部分并说明原因。"
                ))
            }

            try {
                var fullRaw = ""
                var toolCalls: List<DtoToolCall>? = null
                var promptTokens: Int? = null
                var completionTokens: Int? = null
                var cachedTokens: Int? = null

                // V3.1 强制流式：流式优先；流式失败（provider 不支持/网络中断等）降级非流式兜底
                try {
                    val r = runStreamRoundWithFallback(history, activeConfig, roundTools)
                    fullRaw = r.fullText
                    toolCalls = r.toolCalls
                    // 本轮思维链定稿上抛（渲染为可折叠卡片）
                    if (r.thinkingText.isNotEmpty()) {
                        emit(ReasoningCompleteEvent(text = r.thinkingText))
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (streamError: Exception) {
                    // 流式失败 → 兜底降级非流式（整段返回）
                    val errorMsg = "流式调用失败，降级为非流式: ${streamError.message}"
                    Log.w(TAG, errorMsg)
                    com.kirakira.kiracode.util.AppLogger.w("AgentController", errorMsg)

                    // HTTP 500 专属：更长退避 + 更多重试（上游 bad_response 常为瞬态）
                    val is500 = looksLike500(streamError)
                    val maxRetries = if (is500) MAX_500_FALLBACK_RETRIES else MAX_FALLBACK_RETRIES
                    val baseDelay = if (is500) RETRY_500_BASE_DELAY_MS else FALLBACK_BASE_DELAY_MS

                    // 频控类错误：降级前先退避，避免紧随的重试同样被拒
                    if (looksLikeRateLimit(streamError.message)) {
                        val backoffMs = baseDelay + (0..FALLBACK_JITTER_MS).random()
                        com.kirakira.kiracode.util.AppLogger.d("AgentController", "降级前退避 ${backoffMs}ms")
                        delay(backoffMs)
                    }

                    // 非流式兜底 + 有限次退避重试（HTTP 500 类与频控类重试，其它错误快速失败）
                    val callResult: kotlin.Result<RealApiService.ChatCompletionResult> = run {
                        var attempt = 0
                        var last: kotlin.Result<RealApiService.ChatCompletionResult> = kotlin.Result.failure(streamError)
                        while (true) {
                            val r = apiService.chatCompletionWithTools(
                                messages = history.map { it.toOpenAiMessage() },
                                config = activeConfig,
                                tools = roundTools
                            )
                            if (r.isSuccess) {
                                if (attempt > 0) {
                                    com.kirakira.kiracode.util.AppLogger.d("AgentController", "降级重试成功（第 $attempt 次退避后）")
                                }
                                last = r
                                break
                            }
                            val fallbackError = r.exceptionOrNull() ?: streamError
                            attempt++
                            val retryable = looksLike500(fallbackError) || looksLikeRateLimit(fallbackError.message)
                            if (attempt > maxRetries || !retryable) {
                                com.kirakira.kiracode.util.AppLogger.e(
                                    "AgentController",
                                    "ROUND_FAIL round=$iterations transport=非流式 http=${(fallbackError as? ApiException)?.errorCode} " +
                                        "message=${fallbackError.message}"
                                )
                                last = r
                                break
                            }
                            val backoffMs = baseDelay * attempt + (0..FALLBACK_JITTER_MS).random()
                            com.kirakira.kiracode.util.AppLogger.d(
                                "AgentController",
                                "降级重试 $attempt/$maxRetries（${if (is500) "HTTP 500" else "频控"}），退避 ${backoffMs}ms"
                            )
                            delay(backoffMs)
                        }
                        last
                    }
                    val result = callResult.getOrNull()
                    if (result == null) {
                        throw (callResult.exceptionOrNull() ?: IllegalStateException("LLM 调用失败（流式+非流式均失败）"))
                    }
                    fullRaw = result.content
                    toolCalls = result.toolCalls
                    promptTokens = result.promptTokens
                    completionTokens = result.completionTokens
                    cachedTokens = result.cachedTokens
                    if (result.reasoning?.isNotEmpty() == true) {
                        emit(ReasoningCompleteEvent(text = result.reasoning))
                    }
                    // 降级提示（VM 显示 toast）
                    com.kirakira.kiracode.util.AppLogger.d("AgentController", "非流式 fallback 成功，emit SystemNoticeEvent")
                    emit(com.kirakira.kiracode.domain.agent.core.SystemNoticeEvent(
                        message = "流式输出失败，已降级为整段返回"
                    ))
                }

                Log.d(TAG, "Round done: ${fullRaw.take(80)}... (toolCalls=${toolCalls?.size ?: 0})")
                com.kirakira.kiracode.util.AppLogger.d(
                    "AgentController",
                    "ROUND_DONE round=$iterations contentLength=${fullRaw.length} toolCalls=${toolCalls?.size ?: 0}"
                )
                // 诊断：ROUND_TRACE 输出
                if (roundTraces != null) {
                    val roundEndNanos = SystemClock.elapsedRealtimeNanos()
                    val totalMs = (roundEndNanos - roundStartNanos) / 1_000_000
                    val firstDeltaMs = if (firstDeltaNanos > 0) (firstDeltaNanos - roundStartNanos) / 1_000_000 else 0L
                    val trace = "ROUND_TRACE round=$iterations " +
                        "t_round_start=0 " +
                        "t_first_delta_ms=$firstDeltaMs " +
                        "t_round_done_ms=${(roundEndNanos - roundStartNanos) / 1_000_000} " +
                        "inter_round_delay_ms=${if (iterations > 1) 0 else 0} " +
                        "nudge=$nudgeInjected " +
                        "fallback=$fallbackTriggered " +
                        "retry=$retryCount " +
                        "total_ms=$totalMs"
                    roundTraces.add(trace)
                    com.kirakira.kiracode.util.AppLogger.d("ROUND_TRACE", trace)
                }

                // ── 工具调用轮：执行并把结果回注 → 继续 ──
                val calls = toolCalls
                if (!calls.isNullOrEmpty()) {
                    Log.d(TAG, "LLM返回${calls.size}个tool_calls")
                    noToolCallsCount = 0

                    // 助手消息（含 tool_calls）必须先入历史，随后才是 tool 消息（协议要求）
                    history.add(AssistantMessage(content = fullRaw, toolCalls = calls.map { it.toCore() }))

                    // 本轮正文定稿上抛（流式卡片收束后正文以独立卡片保留）
                    if (fullRaw.isNotBlank()) {
                        emit(AssistantTextEvent(text = fullRaw))
                    }

                    for (toolCall in calls) {
                        // ── 搜索工具：web_search（系统执行搜索，结果以 tool role 回注） ──
                        if (toolCall.function.name == "web_search") {
                            handleWebSearch(history, toolCall, recentSearchQueries)
                            continue
                        }
                        // ── 抓取工具：fetch_webpage（系统抓取正文，内容以 tool role 回注） ──
                        if (toolCall.function.name == "fetch_webpage") {
                            handleFetchWebpage(history, toolCall)
                            continue
                        }
                        // ── 子代理工具：spawn_subagent（独立上下文执行单一任务，结果回注主 Agent 审核） ──
                        if (toolCall.function.name == TOOL_SPAWN_SUBAGENT) {
                            handleSpawnSubagent(history, toolCall)
                            continue
                        }
                        // ── 只读查询工具：query_card_entries / get_card_entry（大卡按需读取） ──
                        if (toolCall.function.name == TOOL_QUERY_CARD_ENTRIES) {
                            handleQueryCardEntries(history, toolCall)
                            continue
                        }
                        if (toolCall.function.name == TOOL_GET_CARD_ENTRY) {
                            handleGetCardEntry(history, toolCall)
                            continue
                        }
                        // ── 审计工具：audit_card（只读，完整报告走 UI metadata，不进 ToolMessage） ──
                        if (toolCall.function.name == TOOL_AUDIT_CARD) {
                            handleAuditCard(history, toolCall)
                            continue
                        }
                        // ── 交互工具：ask_user（追问，挂起等待回答） ──
                        if (toolCall.function.name == "ask_user") {
                            handleAskUser(history, toolCall)
                            continue
                        }
                        // ── 交互工具：submit_plan（制卡计划，挂起等待确认/修改） ──
                        if (toolCall.function.name == "submit_plan") {
                            handleSubmitPlan(history, toolCall)
                            continue
                        }
                        // ── 交互工具：complete_step（步骤进度，不挂起） ──
                        if (toolCall.function.name == "complete_step") {
                            handleCompleteStep(history, toolCall)
                            continue
                        }

                        // ── 卡片工具：解析 → Diff 审查 → 执行 → 回注 ──
                        val operations = try {
                            parseToolCallToOperations(toolCall)
                        } catch (e: Exception) {
                            Log.e(TAG, "解析tool_call失败: ${toolCall.function.name}", e)
                            emptyList()
                        }

                        if (operations.isEmpty()) {
                            val action = UpdateCardAction(
                                operations = emptyList(),
                                toolCallId = toolCall.id,
                                functionName = toolCall.function.name
                            )
                            emit(action)
                            val observation = CardObservation(
                                actionId = action.id,
                                isError = true,
                                content = "❌ 工具 ${toolCall.function.name} 参数解析失败，未产生任何操作"
                            )
                            emit(observation)
                            history.add(ToolMessage(
                                content = observation.textContent(),
                                toolCallId = toolCall.id,
                                name = toolCall.function.name
                            ))
                            continue
                        }

                        // Diff 审查（V2/V3）：覆盖已有非空字段 → 挂起展示旧版/新版，等待用户决定
                        val (finalOps, diffNotes) = runDiffReview(operations, toolCall.id)

                        if (finalOps.isNotEmpty()) {
                            val action = UpdateCardAction(
                                operations = finalOps,
                                toolCallId = toolCall.id,
                                functionName = toolCall.function.name
                            )
                            emit(action)

                            val observation = toolRegistry.execute(action)
                            emit(observation)

                            // Diff 决定 + 执行结果合并为单条 tool 消息（协议要求一个 tool_call 一条 tool 消息）
                            val combined = (if (diffNotes.isNotBlank()) "$diffNotes\n" else "") + observation.textContent()
                            history.add(ToolMessage(
                                content = combined.trim(),
                                toolCallId = toolCall.id,
                                name = toolCall.function.name
                            ))
                        } else {
                            // 全部被拒绝/继续改：不发 UpdateCardAction，以 diff 决定回注
                            history.add(ToolMessage(
                                content = diffNotes,
                                toolCallId = toolCall.id,
                                name = toolCall.function.name
                            ))
                        }
                    }

                    interRoundDelay()
                    continue
                }

                // ── 无工具调用轮 ──
                emit(ThinkAction(
                    thought = fullRaw,
                    promptTokens = promptTokens,
                    completionTokens = completionTokens,
                    cachedTokens = cachedTokens
                ))
                history.add(AssistantMessage(content = fullRaw))

                // P0：文本协议仅在降级路径执行（provider 拒绝 tools 后）；
                // 正常工具路径不解析不执行文本协议——由运行规则的 nudge 引导回工具调用，
                // 避免"教两套协议"与绕过 Diff 确认的问题。
                if (textProtocolFallback) {
                    val textOps = try {
                        extractTextProtocolOps(fullRaw)
                    } catch (e: Exception) {
                        Log.e(TAG, "响应文本协议解析失败", e)
                        emptyList()
                    }
                    if (textOps.isNotEmpty()) {
                        Log.i(TAG, "降级路径：文本协议解析到${textOps.size}个操作")
                        // AGENT 模式照常走 Diff 审查（YOLO 在审查内部自动接受）
                        val (finalOps, diffNotes) = runDiffReview(textOps, toolCallId = "text_protocol_fallback")
                        if (finalOps.isNotEmpty()) {
                            val action = UpdateCardAction(
                                operations = finalOps,
                                toolCallId = "",
                                functionName = "text_protocol"
                            )
                            emit(action)
                            val observation = toolRegistry.execute(action)
                            emit(observation)
                            val note = (if (diffNotes.isNotBlank()) "$diffNotes\n" else "") +
                                "（系统 observation）文本协议已解析执行：${observation.textContent()}"
                            history.add(UserMessage(note.trim()))
                        } else {
                            history.add(UserMessage("（系统 observation）${diffNotes.ifBlank { "操作未应用" }}"))
                        }
                        interRoundDelay()
                        continue
                    }
                }

                // 正常完成：响应文本标记 FINISH
                if (fullRaw.contains("FINISH", ignoreCase = true)) {
                    val action = FinishAction(outputs = mapOf("result" to fullRaw))
                    emit(action)
                    emit(toolRegistry.execute(action))
                    return@flow
                }

                // 无工具（纯聊天）：本轮响应即完成
                if (tools == null) return@flow

                // 等待用户检测：模型用自然语言向用户提问/等待选择 → 视为本轮交互完成，
                // 停止循环等待用户（避免对"等待输入"的回复强行续跑触发上游 500）
                val isWaitingUser = fullRaw.contains("您可以") || fullRaw.contains("你可以") ||
                    fullRaw.contains("请问") || fullRaw.contains("或者") || fullRaw.contains("想法") ||
                    fullRaw.contains("选择") || fullRaw.contains("?") || fullRaw.contains("？")
                if (isWaitingUser) {
                    com.kirakira.kiracode.util.AppLogger.d(
                        "AgentController",
                        "检测到等待用户状态（迭代 $iterations），停止 Agent 循环"
                    )
                    emit(FinishAction(outputs = mapOf("result" to fullRaw)))
                    return@flow
                }

                // V3.1 批次4任务1：计划执行自动推进——计划未完成时注入续跑提示，
                // 不进入 nudge/终止计数（complete_step 后循环本就 continue，此分支解决
                // 模型中途只输出文本总结导致的"执行 1 步就停"）
                if (planSteps.isNotEmpty() && completedSteps.size < planSteps.size && planNudgeCount < PLAN_NUDGE_MAX) {
                    planNudgeCount++
                    val remaining = planSteps.size - completedSteps.size
                    Log.w(TAG, "计划未完成（${completedSteps.size}/${planSteps.size}），注入续跑提示 #$planNudgeCount")
                    history.add(UserMessage(
                        "（系统提醒）计划尚未完成：已 ${completedSteps.size}/${planSteps.size} 步，还剩 $remaining 步。" +
                            "请立即用工具继续执行下一步（update_card_field/update_card_book_add 等），" +
                            "每完成一步调用 complete_step(step=步骤序号, summary=摘要)。" +
                            "禁止只输出文本总结；全部步骤完成并保存卡片后再回复 FINISH。"
                    ))
                    interRoundDelay()
                    continue
                }

                // nudge 重试：连续无工具调用 → 注入提示；仍不调用 → 终止
                noToolCallsCount++
                if (!unlimitedToolCalls && noToolCallsCount == NUDGE_AFTER_ROUNDS) {
                    Log.w(TAG, "连续${noToolCallsCount}轮无工具调用，注入nudge提示")
                    history.add(UserMessage(
                        if (textProtocolFallback) {
                            "请使用系统说明中的 card-update 文本协议继续操作，每条修改必须带对应指令块。"
                        } else {
                            "请使用提供的工具（update_card_field/update_card_book_add等）继续操作。" +
                            "禁止只输出文本，必须调用工具。"
                        }
                    ))
                    interRoundDelay()
                    continue
                }
                if (!unlimitedToolCalls && noToolCallsCount > NUDGE_AFTER_ROUNDS) {
                    Log.e(TAG, "nudge后仍不调用工具（连续${noToolCallsCount}轮），终止循环")
                    emit(FinishAction(outputs = mapOf(
                        "error" to (if (textProtocolFallback) "模型未产出有效操作，任务终止" else "模型未使用工具，任务终止"),
                        "hint" to "请检查模型配置或切换其它模型（GPT-4o/Claude等）"
                    )))
                    return@flow
                }
                // 有效文本回复 + 无计划步骤 + 首次无工具轮 = 正常对话结束，不重试
                // 注意：noToolCallsCount 已在上面自增，首次无工具轮此处为 1；
                // nudge 路径（== NUDGE_AFTER_ROUNDS）和终止路径（> NUDGE_AFTER_ROUNDS）均在此前处理。
                if (fullRaw.isNotBlank() && planSteps.isEmpty() && noToolCallsCount == 1) {
                    val action = FinishAction(outputs = mapOf("result" to fullRaw))
                    emit(action)
                    emit(toolRegistry.execute(action))
                    return@flow
                }
                // 首轮无工具调用且正文为空：给模型一次机会自我修正，静默重试
                interRoundDelay()
                continue
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                com.kirakira.kiracode.util.AppLogger.e(
                    "AgentController",
                    "ROUND_FAIL round=$iterations transport=stream http=${(e as? ApiException)?.errorCode} " +
                        "HISTORY count=${history.size} roles=[${history.joinToString(",") { it.javaClass.simpleName.removeSuffix("Message").lowercase() }}] " +
                        "TAIL=${history.lastOrNull()?.debugLabel()} message=${e.message}"
                )
                Log.e(TAG, "Error in agent loop: msgCount=${history.size} types=[${history.joinToString(",") { it.javaClass.simpleName }}]", e)
                emit(FinishAction(outputs = mapOf("error" to "Error: ${e.message}")))
                return@flow
            }
        }
    }

    /**
     * 解析OpenAI tool_call的arguments JSON → List<CardOperation>
     *
     * 双协议兜底：优先结构化 JSON 解析（arguments 解析失败/产出为空时，
     * 降级为从 arguments 原文中提取文本协议片段再解析）。
     */
    private fun parseToolCallToOperations(toolCall: DtoToolCall): List<CardOperation> {
        val structured = try {
            parseStructuredOperations(toolCall.function.name, toolCall.function.arguments)
        } catch (e: Exception) {
            Log.e(TAG, "结构化解析失败: ${toolCall.function.name}", e)
            emptyList()
        }
        if (structured.isNotEmpty()) return structured.map(::withRegexGenerationProtocol)

        // 工具调用上下文里允许裸字段标记（模型明显在尝试调用工具）
        val textOps = try {
            extractTextProtocolOps(toolCall.function.arguments, allowBareFields = true)
        } catch (e: Exception) {
            Log.e(TAG, "文本协议解析也失败: ${toolCall.function.name}", e)
            emptyList()
        }
        if (textOps.isNotEmpty()) {
            Log.i(TAG, "tool_call 参数降级到文本协议解析: ${toolCall.function.name} → ${textOps.size}个操作")
        }
        return textOps.map(::withRegexGenerationProtocol)
    }

    /** description 协议在工具操作进入 Diff/应用前确定，避免依赖模型逐字复制且支持幂等切换。 */
    private fun withRegexGenerationProtocol(operation: CardOperation): CardOperation = when {
        operation is CardOperation.SetField && operation.fieldName.equals("description", ignoreCase = true) ->
            operation.copy(
                value = com.kirakira.kiracode.domain.prompt.RegexGenerationPrompts.appendDescriptionProtocol(
                    operation.value,
                    regexGenerationMode
                )
            )
        operation is CardOperation.ReplaceCard ->
            operation.copy(regexGenerationMode = regexGenerationMode)
        else -> operation
    }

    /** 结构化路径：arguments JSON → 按函数名映射 CardOperation（原 parseToolCallToOperations 主体） */
    private fun parseStructuredOperations(functionName: String, argsJson: String): List<CardOperation> {
        val args = try {
            Json.parseToJsonElement(sanitizeArguments(argsJson)).jsonObject
        } catch (e: Exception) {
            Log.e(TAG, "解析arguments JSON失败: $argsJson", e)
            return emptyList()
        }

        return when (functionName) {
            "update_card_field" -> {
                val field = args["field"]?.jsonPrimitive?.contentOrNull ?: return emptyList()
                val value = args["value"]?.jsonPrimitive?.contentOrNull ?: ""
                val aiGuess = args["ai_guess"]?.jsonPrimitive?.booleanOrNull ?: false
                listOf(CardOperation.SetField(field, value, isAiGuess = aiGuess))
            }

            "update_card_book_add" -> {
                listOf(CardOperation.BookAdd(
                    comment = args["comment"]?.jsonPrimitive?.contentOrNull ?: "",
                    keys = args["keys"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList(),
                    content = args["content"]?.jsonPrimitive?.contentOrNull ?: "",
                    secondaryKeys = args["secondary_keys"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList(),
                    constant = args["constant"]?.jsonPrimitive?.booleanOrNull ?: false,
                    selective = args["selective"]?.jsonPrimitive?.booleanOrNull ?: true,
                    insertionOrder = args["insertion_order"]?.jsonPrimitive?.intOrNull ?: 100,
                    position = args["position"]?.jsonPrimitive?.contentOrNull ?: "before_char",
                    priority = args["priority"]?.jsonPrimitive?.intOrNull,
                    isAiGuess = args["ai_guess"]?.jsonPrimitive?.booleanOrNull ?: false,
                    caseSensitive = args["case_sensitive"]?.jsonPrimitive?.booleanOrNull,
                    matchWholeWords = args["match_whole_words"]?.jsonPrimitive?.booleanOrNull,
                    scanDepth = args["scan_depth"]?.jsonPrimitive?.intOrNull,
                    automationId = args["automation_id"]?.jsonPrimitive?.contentOrNull,
                    probability = args["probability"]?.jsonPrimitive?.intOrNull,
                    useProbability = args["use_probability"]?.jsonPrimitive?.booleanOrNull,
                    selectiveLogic = args["selective_logic"]?.jsonPrimitive?.intOrNull,
                    excludeRecursion = args["exclude_recursion"]?.jsonPrimitive?.booleanOrNull,
                    preventRecursion = args["prevent_recursion"]?.jsonPrimitive?.booleanOrNull,
                    group = args["group"]?.jsonPrimitive?.contentOrNull,
                    groupOverride = args["group_override"]?.jsonPrimitive?.booleanOrNull,
                    groupWeight = args["group_weight"]?.jsonPrimitive?.intOrNull
                ))
            }

            "update_card_book_update" -> {
                val uid = args["uid"]?.jsonPrimitive?.intOrNull?.toLong() ?: return emptyList()
                listOf(CardOperation.BookUpdate(
                    uid = uid,
                    keys = args["keys"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull },
                    secondaryKeys = args["secondary_keys"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull },
                    content = args["content"]?.jsonPrimitive?.contentOrNull,
                    comment = args["comment"]?.jsonPrimitive?.contentOrNull,
                    constant = args["constant"]?.jsonPrimitive?.booleanOrNull,
                    selective = args["selective"]?.jsonPrimitive?.booleanOrNull,
                    insertionOrder = args["insertion_order"]?.jsonPrimitive?.intOrNull,
                    position = args["position"]?.jsonPrimitive?.contentOrNull,
                    priority = args["priority"]?.jsonPrimitive?.intOrNull,
                    caseSensitive = args["case_sensitive"]?.jsonPrimitive?.booleanOrNull,
                    matchWholeWords = args["match_whole_words"]?.jsonPrimitive?.booleanOrNull,
                    scanDepth = args["scan_depth"]?.jsonPrimitive?.intOrNull,
                    automationId = args["automation_id"]?.jsonPrimitive?.contentOrNull,
                    probability = args["probability"]?.jsonPrimitive?.intOrNull,
                    useProbability = args["use_probability"]?.jsonPrimitive?.booleanOrNull,
                    selectiveLogic = args["selective_logic"]?.jsonPrimitive?.intOrNull,
                    excludeRecursion = args["exclude_recursion"]?.jsonPrimitive?.booleanOrNull,
                    preventRecursion = args["prevent_recursion"]?.jsonPrimitive?.booleanOrNull,
                    group = args["group"]?.jsonPrimitive?.contentOrNull,
                    groupOverride = args["group_override"]?.jsonPrimitive?.booleanOrNull,
                    groupWeight = args["group_weight"]?.jsonPrimitive?.intOrNull
                ))
            }

            "update_card_book_delete" -> {
                val uid = args["uid"]?.jsonPrimitive?.intOrNull?.toLong() ?: return emptyList()
                listOf(CardOperation.BookDelete(uid))
            }

            "update_card_regex_add" -> {
                listOf(CardOperation.RegexAdd(
                    scriptName = args["script_name"]?.jsonPrimitive?.contentOrNull ?: "",
                    findRegex = args["find_regex"]?.jsonPrimitive?.contentOrNull ?: "",
                    replaceString = args["replace_string"]?.jsonPrimitive?.contentOrNull ?: "",
                    placement = args["placement"]?.jsonArray?.mapNotNull { it.jsonPrimitive.intOrNull } ?: listOf(2),
                    trimStrings = args["trim_strings"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList(),
                    disabled = args["disabled"]?.jsonPrimitive?.booleanOrNull ?: false,
                    markdownOnly = args["markdown_only"]?.jsonPrimitive?.booleanOrNull,
                    promptOnly = args["prompt_only"]?.jsonPrimitive?.booleanOrNull,
                    runOnEdit = args["run_on_edit"]?.jsonPrimitive?.booleanOrNull ?: false,
                    substituteRegex = args["substitute_regex"]?.jsonPrimitive?.intOrNull ?: 0,
                    minDepth = args["min_depth"]?.jsonPrimitive?.intOrNull,
                    maxDepth = args["max_depth"]?.jsonPrimitive?.intOrNull
                ))
            }

            "update_card_regex_update" -> {
                listOf(CardOperation.RegexUpdate(
                    idOrName = args["name_or_id"]?.jsonPrimitive?.contentOrNull
                        ?: args["id"]?.jsonPrimitive?.contentOrNull
                        ?: return emptyList(),
                    scriptName = args["script_name"]?.jsonPrimitive?.contentOrNull ?: "",
                    findRegex = args["find_regex"]?.jsonPrimitive?.contentOrNull ?: "",
                    replaceString = args["replace_string"]?.jsonPrimitive?.contentOrNull ?: "",
                    placement = args["placement"]?.jsonArray?.mapNotNull { it.jsonPrimitive.intOrNull } ?: listOf(2),
                    trimStrings = args["trim_strings"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList(),
                    disabled = args["disabled"]?.jsonPrimitive?.booleanOrNull ?: false,
                    markdownOnly = args["markdown_only"]?.jsonPrimitive?.booleanOrNull,
                    promptOnly = args["prompt_only"]?.jsonPrimitive?.booleanOrNull,
                    runOnEdit = args["run_on_edit"]?.jsonPrimitive?.booleanOrNull ?: false,
                    substituteRegex = args["substitute_regex"]?.jsonPrimitive?.intOrNull ?: 0,
                    minDepth = args["min_depth"]?.jsonPrimitive?.intOrNull,
                    maxDepth = args["max_depth"]?.jsonPrimitive?.intOrNull
                ))
            }

            "update_card_regex_delete" -> {
                val nameOrId = args["name_or_id"]?.jsonPrimitive?.contentOrNull
                    ?: args["uid"]?.jsonPrimitive?.contentOrNull
                    ?: return emptyList()
                listOf(CardOperation.RegexDelete(nameOrId))
            }

            "update_card_cover" -> {
                val url = args["url"]?.jsonPrimitive?.contentOrNull ?: return emptyList()
                listOf(CardOperation.CoverSet(url))
            }

            "replace_card_json" -> {
                val cardJson = args["json"]?.jsonPrimitive?.contentOrNull ?: return emptyList()
                listOf(CardOperation.ReplaceCard(cardJson))
            }

            else -> {
                Log.w(TAG, "未知的function: $functionName")
                emptyList()
            }
        }
    }

    /**
     * 双协议兜底：从文本协议提取卡片操作。
     * 优先 ```card-update``` 代码块（CardOpsParser，无歧义）；
     * allowBareFields 时再试裸 [field:xxx]...[/field] 字段标记。
     * 注意：响应文本场景必须 allowBareFields=false——系统提示词的"禁止示例"本身含字段标记，避免误解析。
     */
    private fun extractTextProtocolOps(text: String, allowBareFields: Boolean = false): List<CardOperation> {
        if (text.isBlank()) return emptyList()
        CardOpsParser.parse(text).takeIf { it.isNotEmpty() }?.let { return it }
        if (!allowBareFields) return emptyList()
        val fieldRegex = Regex("""\[field:([a-zA-Z_]+)]\s*([\s\S]*?)\[/field]""", RegexOption.IGNORE_CASE)
        return fieldRegex.findAll(text)
            .map { m -> CardOperation.SetField(m.groupValues[1], m.groupValues[2].trim()) }
            .toList()
    }

    private fun DtoToolCall.toCore(): ToolCall = ToolCall(
        id = id,
        function = FunctionCall(name = function.name, arguments = sanitizeArguments(function.arguments))
    )

    // ══════════════════════════════════════════════════════════════
    // 搜索工具处理（系统执行 DuckDuckGo 检索，结果/失败均以 tool role 回注）
    // ══════════════════════════════════════════════════════════════

    /**
     * web_search 处理：解析 query/max_results → 执行 WebSearchService → 回注。
     *
     * - 成功 → observation「✅ 搜索完成」+ digest（标题/URL/摘要）+ metadata 结构化结果
     * - 失败（HTTP/空结果/网络异常）→ observation「❌ 搜索失败」+ 分类原因
     * - 任何路径都保证生成与 toolCall.id 配对的 ToolMessage，不让模型收到空消息
     */
    private suspend fun kotlinx.coroutines.flow.FlowCollector<AgentEvent>.handleWebSearch(
        history: MutableList<Message>,
        toolCall: DtoToolCall,
        recentSearchQueries: MutableSet<String>
    ) {
        val args = try {
            Json.parseToJsonElement(toolCall.function.arguments).jsonObject
        } catch (e: Exception) {
            Log.e(TAG, "web_search 参数解析失败: ${toolCall.function.arguments}", e)
            JsonObject(emptyMap())
        }
        val query = args["query"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        val maxResults = (args["max_results"]?.jsonPrimitive?.intOrNull ?: 5).coerceIn(1, 10)

        // 缺少 query：合法 ToolMessage 明确报错（不让模型收到空/格式错误消息）
        if (query.isBlank()) {
            val obsText = "❌ 搜索失败：缺少查询词（query 参数为空或无效）。" +
                "请重新调用 web_search 并提供简洁具体的 query。"
            com.kirakira.kiracode.util.AppLogger.e(
                "AgentController",
                "web_search 缺少 query tool_call_id=${toolCall.id} args=${toolCall.function.arguments.take(120)}"
            )
            emit(CardObservation(actionId = generateEventId(), isError = true, content = obsText))
            history.add(ToolMessage(content = obsText, toolCallId = toolCall.id, name = "web_search"))
            return
        }

        com.kirakira.kiracode.util.AppLogger.d(
            "AgentController",
            "web_search 开始 queryLength=${query.length} maxResults=$maxResults tool_call_id=${toolCall.id}"
        )

        // 防重复查询：同一轮 Agent 会话内相同 query 不再无条件请求搜索链
        if (!recentSearchQueries.add(query)) {
            val obsText = "❌ 搜索提示：查询词「${query.take(60)}」本轮已搜索过且没有新增结果。" +
                "请更换更具体的关键词，或基于已有搜索结果继续回答。"
            com.kirakira.kiracode.util.AppLogger.w(
                "AgentController",
                "web_search 重复查询已拦截 queryLength=${query.length} tool_call_id=${toolCall.id}"
            )
            emit(CardObservation(actionId = generateEventId(), isError = true, content = obsText))
            history.add(ToolMessage(content = obsText, toolCallId = toolCall.id, name = "web_search"))
            return
        }

        when (val chainResult = searchChain.search(query, maxResults)) {
            is SearchChainResult.SearchChainSuccess -> {
                val hits = chainResult.results
                val digest = formatSearchDigest(hits)
                val obsText = "✅ 搜索完成：共 ${hits.size} 条结果（query=$query，来源：${chainResult.provider}）\n\n$digest"
                val metadata = mapOf(
                    "search_query" to query,
                    "result_count" to hits.size.toString(),
                    "provider" to chainResult.provider,
                    "search_results" to json.encodeToString(
                        kotlinx.serialization.builtins.ListSerializer(
                            com.kirakira.kiracode.data.remote.search.SearchHit.serializer()
                        ),
                        hits
                    )
                )
                com.kirakira.kiracode.util.AppLogger.d(
                    "AgentController",
                    "web_search 成功 provider=${chainResult.provider} hits=${hits.size} queryLength=${query.length} tool_call_id=${toolCall.id}"
                )
                emit(CardObservation(actionId = generateEventId(), isError = false, content = obsText, metadata = metadata))
                history.add(ToolMessage(content = obsText, toolCallId = toolCall.id, name = "web_search"))
            }
            is SearchChainResult.SearchChainFailure -> {
                // 用户可见 userMessage（红卡/toast）；模型可见 modelMessage（ToolMessage）
                com.kirakira.kiracode.util.AppLogger.e(
                    "AgentController",
                    "web_search 失败 queryLength=${query.length} tool_call_id=${toolCall.id} attempts=${chainResult.attempts.size}"
                )
                emit(CardObservation(actionId = generateEventId(), isError = true, content = chainResult.userMessage))
                history.add(ToolMessage(content = chainResult.modelMessage, toolCallId = toolCall.id, name = "web_search"))
            }
        }
    }

    /** 搜索结果摘要（含 provider 标注；不输出完整结果正文到日志） */
    private fun formatSearchDigest(hits: List<com.kirakira.kiracode.data.remote.search.SearchHit>): String = buildString {
        appendLine("共 ${hits.size} 条结果：")
        hits.forEachIndexed { i, h ->
            appendLine("${i + 1}. ${h.title}")
            if (h.snippet.isNotBlank()) appendLine("   ${h.snippet.take(220)}")
            appendLine("   来源[${h.provider}]: ${h.url}")
        }
    }.trim()

    /**
     * fetch_webpage 处理：解析 url → WebpageFetcher 抓取正文 → 回注。
     *
     * - 成功 → observation「✅ 页面抓取成功」+ 正文（≤10K 字符）+ metadata（url/长度）
     * - 失败（404/403/超时/解析失败）→ observation「❌ 页面抓取失败」+ 分类原因
     * - 任何路径都保证生成与 toolCall.id 配对的 ToolMessage
     */
    private suspend fun kotlinx.coroutines.flow.FlowCollector<AgentEvent>.handleFetchWebpage(
        history: MutableList<Message>,
        toolCall: DtoToolCall
    ) {
        val args = try {
            Json.parseToJsonElement(toolCall.function.arguments).jsonObject
        } catch (e: Exception) {
            Log.e(TAG, "fetch_webpage 参数解析失败: ${toolCall.function.arguments}", e)
            JsonObject(emptyMap())
        }
        val url = args["url"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()

        if (url.isBlank()) {
            val obsText = "❌ 页面抓取失败：缺少 URL 参数（url 为空或无效）。" +
                "请重新调用 fetch_webpage 并提供完整的 http(s):// 链接。"
            com.kirakira.kiracode.util.AppLogger.e(
                "AgentController",
                "fetch_webpage 缺少 url tool_call_id=${toolCall.id} args=${toolCall.function.arguments.take(120)}"
            )
            emit(CardObservation(actionId = generateEventId(), isError = true, content = obsText))
            history.add(ToolMessage(content = obsText, toolCallId = toolCall.id, name = "fetch_webpage"))
            return
        }
        if (!com.kirakira.kiracode.data.remote.UrlSafety.isAllowed(url)) {
            val obsText = "❌ 页面抓取失败：URL 不允许抓取（仅支持公网 http/https 地址，" +
                "拒绝 file/content/javascript/data 与内网/本地地址）。收到：${url.take(80)}。"
            com.kirakira.kiracode.util.AppLogger.e(
                "AgentController",
                "fetch_webpage 非法 url（安全策略拦截）tool_call_id=${toolCall.id}"
            )
            emit(CardObservation(actionId = generateEventId(), isError = true, content = obsText))
            history.add(ToolMessage(content = obsText, toolCallId = toolCall.id, name = "fetch_webpage"))
            return
        }

        com.kirakira.kiracode.util.AppLogger.d(
            "AgentController",
            "fetch_webpage 开始 url='$url' tool_call_id=${toolCall.id}"
        )
        try {
            val content = webpageFetcher.fetch(url)
            val obsText = "✅ 页面抓取成功\n\n$content"
            val metadata = mapOf(
                "fetched_url" to url,
                "content_length" to content.length.toString()
            )
            com.kirakira.kiracode.util.AppLogger.d(
                "AgentController",
                "fetch_webpage 成功 chars=${content.length} url='$url' tool_call_id=${toolCall.id}"
            )
            emit(CardObservation(actionId = generateEventId(), isError = false, content = obsText, metadata = metadata))
            history.add(ToolMessage(content = obsText, toolCallId = toolCall.id, name = "fetch_webpage"))
        } catch (e: Exception) {
            val obsText = "❌ 页面抓取失败：${e.message ?: "未知错误"}"
            com.kirakira.kiracode.util.AppLogger.e(
                "AgentController",
                "fetch_webpage 失败 url='$url' tool_call_id=${toolCall.id} error=${e.message}",
                e
            )
            emit(CardObservation(actionId = generateEventId(), isError = true, content = obsText))
            history.add(ToolMessage(content = obsText, toolCallId = toolCall.id, name = "fetch_webpage"))
        }
    }

    // ══════════════════════════════════════════════════════════════
    // 子代理工具（Tool-as-Agent）：独立上下文执行单一任务，结果回注主 Agent 审核
    // ══════════════════════════════════════════════════════════════

    /**
     * spawn_subagent 处理：解析 task → SubAgentRunner（独立 messages + 独立 LLM 请求）→ 回注。
     *
     * - 子代理只返回候选文本/分析结果，**不直接修改角色卡**（写卡仍由主 Agent 的卡片工具完成）；
     * - 成功/失败都保证生成与 toolCall.id 配对的 ToolMessage，不让模型收到空消息；
     * - 取消（CancellationException）原样上抛，不留僵尸状态（UI 收敛由 ViewModel 取消分支完成）；
     * - 产出超长时截断回注，保护主 Agent 上下文预算。
     */
    private suspend fun kotlinx.coroutines.flow.FlowCollector<AgentEvent>.handleSpawnSubagent(
        history: MutableList<Message>,
        toolCall: DtoToolCall
    ) {
        val args = try {
            Json.parseToJsonElement(toolCall.function.arguments).jsonObject
        } catch (e: Exception) {
            Log.e(TAG, "spawn_subagent 参数解析失败: ${toolCall.function.arguments}", e)
            JsonObject(emptyMap())
        }
        val task = args["task"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()

        // 缺少 task：合法 ToolMessage 明确报错（不让模型收到空/格式错误消息）
        if (task.isBlank()) {
            val obsText = "❌ 子代理派发失败：缺少任务描述（task 参数为空或无效）。" +
                "请重新调用 spawn_subagent 并给出单一、明确的任务。"
            com.kirakira.kiracode.util.AppLogger.e(
                TAG,
                "spawn_subagent 缺少 task tool_call_id=${toolCall.id} args=${toolCall.function.arguments.take(120)}"
            )
            emit(CardObservation(actionId = generateEventId(), isError = true, content = obsText))
            history.add(ToolMessage(content = obsText, toolCallId = toolCall.id, name = TOOL_SPAWN_SUBAGENT))
            return
        }

        val subagentId = generateEventId()
        val startedAt = System.currentTimeMillis()
        com.kirakira.kiracode.util.AppLogger.d(
            TAG,
            "spawn_subagent 开始 taskLength=${task.length} tool_call_id=${toolCall.id} subagentId=$subagentId"
        )
        emit(SubAgentStartedEvent(subagentId = subagentId, task = task, toolCallId = toolCall.id))
        try {
            // 子代理上下文由 SubAgentRunner 安全组装（卡摘要+已有条目预览+人格/用户规则，禁止主会话历史）
            // P0 接线：参考卡风格样本同源传入（此前调用未传 referenceCard，风格段从未生效）
            val result = subAgentRunner.run(task, toolRegistry.getCurrentCard(), currentReferenceCard)
            val finishedAt = System.currentTimeMillis()
            result.fold(
                onSuccess = { output ->
                    val truncated = output.length > SUBAGENT_TOOL_OUTPUT_MAX
                    val toolOutput = if (truncated) {
                        output.take(SUBAGENT_TOOL_OUTPUT_MAX) +
                            "\n\n（子代理产出过长，已截断到 $SUBAGENT_TOOL_OUTPUT_MAX 字符）"
                    } else output
                    val obsText = "✅ 子代理已完成任务，结果如下（仅供审核，尚未写入角色卡；" +
                        "认可后请用卡片工具写入，或直接给出修改意见）：\n\n$toolOutput"
                    com.kirakira.kiracode.util.AppLogger.d(
                        TAG,
                        "spawn_subagent 成功 outputChars=${output.length} subagentId=$subagentId"
                    )
                    emit(SubAgentCompletedEvent(
                        subagentId = subagentId,
                        task = task,
                        output = toolOutput.take(SUBAGENT_CARD_OUTPUT_MAX),
                        outputPreview = toolOutput.lineSequence().firstOrNull { it.isNotBlank() }
                            ?.take(SUBAGENT_PREVIEW_MAX).orEmpty(),
                        toolCallId = toolCall.id,
                        startedAt = startedAt,
                        finishedAt = finishedAt
                    ))
                    history.add(ToolMessage(content = obsText, toolCallId = toolCall.id, name = TOOL_SPAWN_SUBAGENT))
                },
                onFailure = { e ->
                    val obsText = "❌ 子代理执行失败：${e.message ?: "未知错误"}。" +
                        "可重试一次、把任务拆小，或由你直接完成该部分。"
                    com.kirakira.kiracode.util.AppLogger.e(
                        TAG,
                        "spawn_subagent 失败 subagentId=$subagentId error=${e.message}"
                    )
                    emit(SubAgentFailedEvent(
                        subagentId = subagentId,
                        task = task,
                        errorMessage = e.message ?: "未知错误",
                        toolCallId = toolCall.id,
                        startedAt = startedAt,
                        finishedAt = finishedAt
                    ))
                    history.add(ToolMessage(content = obsText, toolCallId = toolCall.id, name = TOOL_SPAWN_SUBAGENT))
                }
            )
        } catch (e: kotlinx.coroutines.CancellationException) {
            // 取消传播：不留僵尸状态（ViewModel 取消分支将运行中的子代理卡收敛为失败）
            throw e
        } catch (e: Exception) {
            val finishedAt = System.currentTimeMillis()
            val obsText = "❌ 子代理执行异常：${e.message ?: "未知错误"}。可重试或改由你直接完成。"
            com.kirakira.kiracode.util.AppLogger.e(TAG, "spawn_subagent 异常 subagentId=$subagentId", e)
            emit(SubAgentFailedEvent(
                subagentId = subagentId,
                task = task,
                errorMessage = e.message ?: "未知错误",
                toolCallId = toolCall.id,
                startedAt = startedAt,
                finishedAt = finishedAt
            ))
            history.add(ToolMessage(content = obsText, toolCallId = toolCall.id, name = TOOL_SPAWN_SUBAGENT))
        }
    }

    /**
     * query_card_entries 处理：解析 kind/keywords/limit → CardIndex 纯函数检索 → 回注。
     * 只读工具：不产生任何卡片修改；观测文本带稳定条目 ID（供 get_card_entry / book_update 定位）。
     */
    private suspend fun kotlinx.coroutines.flow.FlowCollector<AgentEvent>.handleQueryCardEntries(
        history: MutableList<Message>,
        toolCall: DtoToolCall
    ) {
        val card = toolRegistry.getCurrentCard()
        if (card == null) {
            val obsText = "❌ 查询失败：当前会话没有角色卡。请先创建或导入角色卡。"
            emit(CardObservation(actionId = generateEventId(), isError = true, content = obsText))
            history.add(ToolMessage(content = obsText, toolCallId = toolCall.id, name = TOOL_QUERY_CARD_ENTRIES))
            return
        }
        val args = try {
            Json.parseToJsonElement(toolCall.function.arguments).jsonObject
        } catch (e: Exception) {
            Log.e(TAG, "query_card_entries 参数解析失败: ${toolCall.function.arguments}", e)
            JsonObject(emptyMap())
        }
        val kind = args["kind"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty().ifBlank { "all" }
        val keywords = args["keywords"]?.jsonPrimitive?.contentOrNull
            ?.split(' ', ',', '，', ';', '；')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            .orEmpty()
        val limit = args["limit"]?.jsonPrimitive?.intOrNull ?: 10

        if (kind !in setOf("worldbook", "regex", "all")) {
            val obsText = "❌ 查询失败：kind 只支持 worldbook | regex | all（收到：$kind）。"
            emit(CardObservation(actionId = generateEventId(), isError = true, content = obsText))
            history.add(ToolMessage(content = obsText, toolCallId = toolCall.id, name = TOOL_QUERY_CARD_ENTRIES))
            return
        }

        val result = CardIndex.queryEntries(card, CardIndex.QuerySpec(kind, keywords, limit))
        com.kirakira.kiracode.util.AppLogger.d(
            TAG,
            "query_card_entries kind=$kind keywords=${keywords.size} limit=$limit tool_call_id=${toolCall.id}"
        )
        val obsText = "✅ 条目检索完成（只读）：\n$result"
        emit(CardObservation(actionId = generateEventId(), isError = false, content = obsText))
        history.add(ToolMessage(content = result, toolCallId = toolCall.id, name = TOOL_QUERY_CARD_ENTRIES))
    }

    /**
     * get_card_entry 处理：解析 kind/entry_id → CardIndex 读取单条全文 → 回注。
     * 未找到返回明确错误（配对 ToolMessage）；内容超长截断保护上下文。
     */
    private suspend fun kotlinx.coroutines.flow.FlowCollector<AgentEvent>.handleGetCardEntry(
        history: MutableList<Message>,
        toolCall: DtoToolCall
    ) {
        val card = toolRegistry.getCurrentCard()
        if (card == null) {
            val obsText = "❌ 读取失败：当前会话没有角色卡。请先创建或导入角色卡。"
            emit(CardObservation(actionId = generateEventId(), isError = true, content = obsText))
            history.add(ToolMessage(content = obsText, toolCallId = toolCall.id, name = TOOL_GET_CARD_ENTRY))
            return
        }
        val args = try {
            Json.parseToJsonElement(toolCall.function.arguments).jsonObject
        } catch (e: Exception) {
            Log.e(TAG, "get_card_entry 参数解析失败: ${toolCall.function.arguments}", e)
            JsonObject(emptyMap())
        }
        val kind = args["kind"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        val entryId = args["entry_id"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()

        if (kind.isBlank() || entryId.isBlank()) {
            val obsText = "❌ 读取失败：kind 与 entry_id 均为必填。" +
                "请重新调用 get_card_entry（kind=worldbook|regex，entry_id=query_card_entries 返回的 ID）。"
            emit(CardObservation(actionId = generateEventId(), isError = true, content = obsText))
            history.add(ToolMessage(content = obsText, toolCallId = toolCall.id, name = TOOL_GET_CARD_ENTRY))
            return
        }

        CardIndex.getEntry(card, kind, entryId).fold(
            onSuccess = { text ->
                com.kirakira.kiracode.util.AppLogger.d(
                    TAG,
                    "get_card_entry 成功 kind=$kind entryId=$entryId chars=${text.length} tool_call_id=${toolCall.id}"
                )
                val obsText = "✅ 条目读取完成（只读）：\n$text"
                emit(CardObservation(actionId = generateEventId(), isError = false, content = obsText))
                history.add(ToolMessage(content = text, toolCallId = toolCall.id, name = TOOL_GET_CARD_ENTRY))
            },
            onFailure = { e ->
                com.kirakira.kiracode.util.AppLogger.e(
                    TAG,
                    "get_card_entry 未命中 kind=$kind entryId=$entryId error=${e.message}"
                )
                val obsText = "❌ 条目读取失败：${e.message}"
                emit(CardObservation(actionId = generateEventId(), isError = true, content = obsText))
                history.add(ToolMessage(content = obsText, toolCallId = toolCall.id, name = TOOL_GET_CARD_ENTRY))
            }
        )
    }

    /**
     * audit_card 处理：读取当前工作卡 → 稳定 uid → 纯 Kotlin 审计 → 短摘要回注 + 完整报告 metadata。
     * 只读工具，不产生任何卡片修改。
     */
    private suspend fun kotlinx.coroutines.flow.FlowCollector<AgentEvent>.handleAuditCard(
        history: MutableList<Message>,
        toolCall: DtoToolCall
    ) {
        val card = toolRegistry.getCurrentCard()
        if (card == null) {
            val obsText = "❌ 审计失败：当前会话没有角色卡。请先创建或导入角色卡。"
            emit(CardObservation(actionId = generateEventId(), isError = true, content = obsText))
            history.add(ToolMessage(content = obsText, toolCallId = toolCall.id, name = TOOL_AUDIT_CARD))
            return
        }

        val scope = parseAuditScope(toolCall.function.arguments)
        val stableCard = CardIndex.ensureStableEntryIds(card)
        if (stableCard !== card) {
            toolRegistry.setCurrentCard(stableCard)
        }

        val report = CardAuditor.audit(stableCard, scope)
        val summary = report.toShortSummary()
        val metadataJson = Json.encodeToString(AuditReport.serializer(), report)

        emit(CardObservation(
            actionId = generateEventId(),
            isError = false,
            content = summary,
            metadata = mapOf("audit_report" to metadataJson)
        ))
        history.add(ToolMessage(content = summary, toolCallId = toolCall.id, name = TOOL_AUDIT_CARD))

        com.kirakira.kiracode.util.AppLogger.d(
            TAG,
            "audit_card done scope=${scope.name} entries=${report.entryCount} " +
                "errors=${report.errorCount} warnings=${report.warningCount} infos=${report.infoCount} " +
                "tool_call_id=${toolCall.id}"
        )
    }

    private fun parseAuditScope(arguments: String): AuditScope {
        val raw = runCatching {
            Json.parseToJsonElement(arguments).jsonObject["scope"]?.jsonPrimitive?.contentOrNull
        }.getOrNull()?.trim()?.lowercase()

        return when (raw) {
            "worldbook" -> AuditScope.WORLDBOOK
            "fields" -> AuditScope.FIELDS
            else -> AuditScope.ALL
        }
    }

    // ══════════════════════════════════════════════════════════════
    // V2/V3 交互工具处理（挂起等待用户回复，回复以 tool role 回注）
    // ══════════════════════════════════════════════════════════════

    /**
     * 追问处理：发出 AskUserAction → 挂起等待用户回答/跳过 → 回注 observation。
     *
     * - 用户直接回答 → observation「用户回答：...」
     * - 用户跳过 → observation 引导 AI 给出 3 个候选（A/B/C）再次询问
     * - 连续 [FORCED_PROGRESS_AFTER] 次无有效输入 → 强制推进：注入自动草稿模式
     *   （AI 直接补全剩余内容，脑补字段必须标注 ai_guess=true）
     */
    private suspend fun kotlinx.coroutines.flow.FlowCollector<AgentEvent>.handleAskUser(
        history: MutableList<Message>,
        toolCall: DtoToolCall
    ) {
        val (question, options, about) = parseAskUserArgs(toolCall.function.arguments)

        // P0 防御分支：YOLO 模式不应出现 ask_user（工具层已不注册；旧状态/模型幻觉仍可能发来）
        // 行为：不挂起等待用户，直接回注"自行决策"observation，让模型用合理默认值继续
        if (sessionMode == WorkMode.YOLO) {
            Log.w(TAG, "YOLO 模式收到 ask_user，自动决策不挂起")
            val obsText = "（YOLO 模式）ask_user 不可用：不要等待用户，直接采用合理默认值继续执行；" +
                "对自行生成的内容在后续工具调用中设置 ai_guess=true，并在完成汇报中列出。"
            emit(CardObservation(actionId = generateEventId(), isError = false, content = obsText))
            history.add(ToolMessage(content = obsText, toolCallId = toolCall.id, name = "ask_user"))
            return
        }

        val action = AskUserAction(
            question = question,
            options = options,
            about = about,
            toolCallId = toolCall.id
        )
        emit(action)

        val response = awaitUserResponse()
        val skipped = response.startsWith(MARK_SKIP) || response.isBlank()
        if (skipped) noInputCount++ else noInputCount = 0

        var obsText = if (skipped) {
            "（用户跳过了此问题）请基于常见设定给出 3 个具体候选（A/B/C），" +
            "用 ask_user 工具的 options 参数让用户点选。"
        } else {
            "用户回答：$response"
        }

        // 强制推进：连续无有效输入 → 自动草稿模式
        if (skipped && noInputCount >= FORCED_PROGRESS_AFTER) {
            noInputCount = 0
            autoDraft = true
            obsText = "（用户已连续${FORCED_PROGRESS_AFTER}次无有效输入）现在进入自动草稿模式：" +
                "停止追问，直接生成所有剩余内容并完成角色卡。" +
                "对你自行脑补（未与用户确认）的字段，调用 update_card_field / update_card_book_add 时必须设置 ai_guess=true。"
            emit(AutoDraftEvent(
                message = "已进入自动草稿模式：AI 将直接补全剩余内容，脑补部分会标注出来，可点击重写或换选项"
            ))
        }

        emit(CardObservation(actionId = action.id, isError = false, content = obsText))
        history.add(ToolMessage(content = obsText, toolCallId = toolCall.id, name = "ask_user"))
    }

    /**
     * 制卡计划处理：发出 PlanAction → 挂起等待用户确认/修改 → 回注 observation。
     *
     * - 用户确认 → observation 指示 AI 按计划逐步执行（complete_step 标记进度）
     * - 用户要求修改 → observation 携带反馈，AI 调整后重新 submit_plan（新 PlanAction 替换旧卡）
     */
    private suspend fun kotlinx.coroutines.flow.FlowCollector<AgentEvent>.handleSubmitPlan(
        history: MutableList<Message>,
        toolCall: DtoToolCall
    ) {
        val steps = parsePlanSteps(toolCall.function.arguments)
        if (steps.isEmpty()) {
            val obsText = "❌ 计划为空：请重新调用 submit_plan 并提供 steps（6-10 步）"
            emit(CardObservation(actionId = generateEventId(), isError = true, content = obsText))
            history.add(ToolMessage(content = obsText, toolCallId = toolCall.id, name = "submit_plan"))
            return
        }

        // P0 防御分支：YOLO 模式不应出现 submit_plan（工具层已不注册；旧状态/模型幻觉仍可能发来）
        // 行为：不挂起等待确认，计划照记（供任务板/进度展示），直接回注"自动执行"observation
        if (sessionMode == WorkMode.YOLO) {
            Log.w(TAG, "YOLO 模式收到 submit_plan，自动执行不挂起")
            planSteps = steps
            completedSteps.clear()
            currentStepStartIteration = currentIterations
            stepBonusUsed = 0
            recalculateMaxIterations()
            emit(PlanAction(steps = steps, toolCallId = toolCall.id))
            val obsText = "（YOLO 模式）计划已记录但无需用户确认：立即按计划自主执行，不要等待用户。" +
                "缺失信息用合理默认值补全并对脑补内容标注 ai_guess=true。"
            emit(CardObservation(actionId = generateEventId(), isError = false, content = obsText))
            history.add(ToolMessage(content = obsText, toolCallId = toolCall.id, name = "submit_plan"))
            return
        }

        planSteps = steps
        completedSteps.clear()
        currentStepStartIteration = currentIterations
        stepBonusUsed = 0
        recalculateMaxIterations()
        val action = PlanAction(steps = steps, toolCallId = toolCall.id)
        emit(action)

        val response = awaitUserResponse()
        val obsText = if (response.startsWith(MARK_CONFIRM)) {
            "用户确认了计划（共${steps.size}步），请开始逐步执行。" +
            "每完成一个步骤调用 complete_step(step=步骤序号, summary=完成摘要) 标记进度。" +
            "注意：修改已有内容的字段时，系统会先弹出 Diff 让用户确认后再应用。"
        } else {
            "用户要求修改计划：${response.removePrefix(MARK_MODIFY).trim()}" +
            "请根据反馈调整步骤，重新调用 submit_plan。"
        }
        emit(CardObservation(actionId = action.id, isError = false, content = obsText))
        history.add(ToolMessage(content = obsText, toolCallId = toolCall.id, name = "submit_plan"))
    }

    /** 按计划步骤数重算动态上限；不缩小到当前轮次以下 */
    private fun recalculateMaxIterations() {
        dynamicMaxIterations = if (planSteps.isEmpty()) {
            BASE_ITERATIONS
        } else {
            maxOf(currentIterations + 1, planSteps.size * ITERATIONS_PER_STEP + 2)
        }
        budgetWarned = false
        Log.d(
            TAG,
            "动态预算更新：steps=${planSteps.size} current=$currentIterations max=$dynamicMaxIterations"
        )
    }

    /** 已完成字段摘要（预算决策卡展示） */
    private fun buildCompletedFieldsSummary(): String {
        val card = toolRegistry.getCurrentCard()
        val filled = mutableListOf<String>()
        val name = card?.name?.takeIf { it.isNotBlank() && it != "未命名角色" }
        if (name != null) filled += "名字"
        if (!card?.description.isNullOrBlank()) filled += "描述"
        if (!card?.personality.isNullOrBlank()) filled += "性格"
        if (!card?.firstMessage.isNullOrBlank()) filled += "开场白"
        if (!card?.exampleDialogue.isNullOrBlank()) filled += "示例对话"
        if (card?.characterBook?.entries?.isNotEmpty() == true) {
            filled += "世界书(${card.characterBook.entries.size}条)"
        }
        if (card?.regexScripts?.isNotEmpty() == true) {
            filled += "正则(${card.regexScripts.size}条)"
        }
        return if (filled.isEmpty()) "（尚未创建角色卡）" else filled.joinToString("、")
    }

    /** 步骤进度处理：更新计划完成状态（不挂起，即时回注"已完成 N/M 步"） */
    private suspend fun kotlinx.coroutines.flow.FlowCollector<AgentEvent>.handleCompleteStep(
        history: MutableList<Message>,
        toolCall: DtoToolCall
    ) {
        val args = try {
            Json.parseToJsonElement(toolCall.function.arguments).jsonObject
        } catch (e: Exception) {
            Log.e(TAG, "complete_step 参数解析失败: ${toolCall.function.arguments}", e)
            JsonObject(emptyMap())
        }
        val stepIdx = args["step"]?.jsonPrimitive?.intOrNull
        val summary = args["summary"]?.jsonPrimitive?.contentOrNull ?: ""
        if (stepIdx == null || stepIdx !in 1..planSteps.size) {
            val obsText = "❌ 无效步骤序号：请在 1..${planSteps.size} 范围内按计划序号标记"
            emit(CardObservation(actionId = generateEventId(), isError = true, content = obsText))
            history.add(ToolMessage(content = obsText, toolCallId = toolCall.id, name = "complete_step"))
            return
        }

        // Set 去重：重复调用同一 step 幂等，不重复计数
        val newlyCompleted = completedSteps.add(stepIdx)
        if (newlyCompleted) {
            currentStepStartIteration = currentIterations
            stepBonusUsed = 0
        }
        val done = completedSteps.size
        val total = planSteps.size.coerceAtLeast(done)
        val title = planSteps.getOrNull(stepIdx - 1)?.title.orEmpty()

        emit(StepProgressEvent(
            done = done,
            total = total,
            title = title,
            summary = summary,
            stepIndex = stepIdx
        ))
        val obsText = "✅ 进度：已完成 $done/$total 步" + if (summary.isNotBlank()) "（$summary）" else ""
        emit(CardObservation(actionId = generateEventId(), isError = false, content = obsText))
        history.add(ToolMessage(content = obsText, toolCallId = toolCall.id, name = "complete_step"))
    }

    // ══════════════════════════════════════════════════════════════
    // Diff 审查（V2/V3 核心交互）
    // ══════════════════════════════════════════════════════════════

    /** Diff 提案数据（字段 key / 中文标签 / 旧值 / 新值） */
    private data class DiffData(
        val fieldKey: String,
        val fieldLabel: String,
        val oldValue: String,
        val newValue: String
    )

    /**
     * Diff 审查：对"覆盖已有非空字段"的操作，挂起展示 Diff（删除线旧版 + 绿色新版）等待用户决定。
     *
     * - （接受）→ 操作进入待应用列表
     * - （拒绝）→ 丢弃操作，observation 记录"保留原值"
     * - （继续改）→ 丢弃操作，observation 携带用户反馈，AI 重新调用同一工具给出新版本
     *
     * 返回 (用户接受的操作序列, 决定说明)。自动草稿模式跳过审查（脑补内容由 UI 标注交互处理）。
     * 空字段覆盖（首次创建）无需审查，直接应用。
     */
    private suspend fun kotlinx.coroutines.flow.FlowCollector<AgentEvent>.runDiffReview(
        operations: List<CardOperation>,
        toolCallId: String
    ): Pair<List<CardOperation>, String> {
        if (autoDraft) return operations to ""

        val accepted = mutableListOf<CardOperation>()
        val notes = mutableListOf<String>()
        val card = toolRegistry.getCurrentCard()

        for (op in operations) {
            // V3.1 任务8：diffFor 未覆盖的操作（新增字段/世界书追加/正则新增等）合成对照
            val diff = diffFor(card, op) ?: synthesizedDiff(card, op)
            if (diff == null) {
                // 无法展示对照的操作（如纯元数据更新）：直接应用
                accepted += op
                continue
            }
            val action = DiffProposalAction(
                fieldKey = diff.fieldKey,
                fieldLabel = diff.fieldLabel,
                oldValue = diff.oldValue,
                newValue = diff.newValue,
                toolCallId = toolCallId
            )
            emit(action)

            // V3.1 任务7：YOLO（全局）模式不挂起——对照卡已在事件流展示（VM 端标为已接受）
            if (sessionMode == WorkMode.YOLO) {
                accepted += op
                notes += "（全局模式自动接受「${diff.fieldLabel}」的修改）"
                continue
            }

            val response = awaitUserResponse()
            when {
                response.startsWith(MARK_ACCEPT) -> {
                    accepted += op
                    notes += "用户接受了「${diff.fieldLabel}」的修改。"
                }
                response.startsWith(MARK_REJECT) -> {
                    notes += "用户拒绝了「${diff.fieldLabel}」的修改，保留原值。"
                }
                else -> {
                    val feedback = response.removePrefix(MARK_CONTINUE).trim()
                    notes += "用户希望继续修改「${diff.fieldLabel}」，反馈：$feedback。" +
                            "请基于反馈给出新版本（重新调用同一工具）。"
                }
            }
        }
        return accepted to notes.joinToString("\n")
    }

    /**
     * V3.1 任务8：diffFor 覆盖不到的操作合成「（无）→新值」对照（新增字段/列表追加）。
     * 脑补字段（aiGuess）保持原行为（由 脑补标注交互处理，不进 Diff 审查）；
     * 空值且与旧值相同/纯元数据操作返回 null（直接应用）。
     */
    private fun synthesizedDiff(card: com.kirakira.kiracode.domain.model.CharacterCard?, op: CardOperation): DiffData? {
        when (op) {
            is CardOperation.SetField -> {
                if (op.isAiGuess) return null
                val old = card?.let { CardOperationApplier.readField(it, op.fieldName) }.orEmpty()
                if (old == op.value) return null
                return DiffData(
                    fieldKey = op.fieldName,
                    fieldLabel = CardOperationApplier.fieldLabel(op.fieldName),
                    oldValue = old.ifBlank { "（无）" },
                    newValue = op.value
                )
            }
            is CardOperation.BookAdd -> return DiffData(
                fieldKey = "book_add",
                fieldLabel = "世界书「${op.comment.ifBlank { op.keys.joinToString("/") }}」新增",
                oldValue = "（无）",
                newValue = op.content
            )
            is CardOperation.RegexAdd -> return DiffData(
                fieldKey = "regex_add",
                fieldLabel = "新增正则脚本",
                oldValue = "（无）",
                newValue = op.describe
            )
            is CardOperation.RegexUpdate -> return DiffData(
                fieldKey = "regex_update",
                fieldLabel = "更新正则脚本",
                oldValue = op.idOrName,
                newValue = op.describe
            )
            is CardOperation.BookDelete -> {
                val entry = card?.characterBook?.entries?.firstOrNull { it.id == op.uid }
                return DiffData(
                    fieldKey = "book_${op.uid}",
                    fieldLabel = "世界书「${entry?.comment?.ifBlank { "#${op.uid}" } ?: "#${op.uid}"}」删除",
                    oldValue = entry?.content ?: "（无此条目）",
                    newValue = "（删除）"
                )
            }
            is CardOperation.CoverSet -> return DiffData(
                fieldKey = "cover",
                fieldLabel = "封面",
                oldValue = "（无）",
                newValue = op.source.take(120)
            )
            else -> return null
        }
    }

    /** 判断操作是否需要 Diff 审查；返回提案数据或 null（无需审查） */
    private fun diffFor(card: com.kirakira.kiracode.domain.model.CharacterCard?, op: CardOperation): DiffData? {
        if (card == null) return null
        return when (op) {
            is CardOperation.SetField -> {
                if (op.isAiGuess) return null // 脑补内容由标注交互处理，不弹 Diff
                val old = CardOperationApplier.readField(card, op.fieldName)
                if (old.isBlank() || old == op.value) return null
                DiffData(
                    fieldKey = op.fieldName,
                    fieldLabel = CardOperationApplier.fieldLabel(op.fieldName),
                    oldValue = old,
                    newValue = op.value
                )
            }
            is CardOperation.BookUpdate -> {
                val newContent = op.content ?: return null
                val entry = card.characterBook?.entries?.firstOrNull { it.id == op.uid } ?: return null
                if (entry.content.isBlank() || entry.content == newContent) return null
                DiffData(
                    fieldKey = "book_${op.uid}",
                    fieldLabel = "世界书「${entry.comment.ifBlank { "#${op.uid}" }}」内容",
                    oldValue = entry.content,
                    newValue = newContent
                )
            }
            else -> null
        }
    }

    /** ask_user 参数解析（question 必需，options/about 可选） */
    private fun parseAskUserArgs(argsJson: String): Triple<String, List<String>, String> {
        val args = try {
            Json.parseToJsonElement(argsJson).jsonObject
        } catch (e: Exception) {
            Log.e(TAG, "ask_user 参数解析失败: $argsJson", e)
            return Triple("（AI 忘了写问题内容，请直接告诉它你想补充什么）", emptyList(), "")
        }
        val question = args["question"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
            ?: "（AI 忘了写问题内容，请直接告诉它你想补充什么）"
        val options = args["options"]?.jsonArray
            ?.mapNotNull { it.jsonPrimitive.contentOrNull }
            ?.filter { it.isNotBlank() }
            .orEmpty()
        val about = args["about"]?.jsonPrimitive?.contentOrNull ?: ""
        return Triple(question, options, about)
    }

    /** submit_plan 参数解析（steps 数组 → PlanStepData 列表） */
    private fun parsePlanSteps(argsJson: String): List<PlanStepData> {
        val args = try {
            Json.parseToJsonElement(argsJson).jsonObject
        } catch (e: Exception) {
            Log.e(TAG, "submit_plan 参数解析失败: $argsJson", e)
            return emptyList()
        }
        val stepsArray = args["steps"]?.jsonArray ?: return emptyList()
        return stepsArray.mapNotNull { el ->
            val obj = try { el.jsonObject } catch (e: Exception) { return@mapNotNull null }
            val title = obj["title"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            if (title.isBlank()) return@mapNotNull null
            PlanStepData(
                title = title,
                detail = obj["detail"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            )
        }
    }

    /** 各类 Observation 的统一文本内容（用于回注 tool 消息） */
    private fun com.kirakira.kiracode.domain.agent.core.Observation.textContent(): String = when (this) {
        is com.kirakira.kiracode.domain.agent.core.ThinkObservation -> content
        is com.kirakira.kiracode.domain.agent.core.FinishObservation -> result
        is CardObservation -> content
        else -> toString()
    }

    /** 诊断用消息摘要（只含 role/长度/工具元数据，不含正文） */
    private fun Message.debugLabel(): String = when (this) {
        is SystemMessage -> "system(len=${content.length})"
        is UserMessage -> "user(len=${content.length})"
        is AssistantMessage -> "assistant(len=${content.length},toolCalls=${toolCalls?.size ?: 0}" +
            (toolCalls?.joinToString(prefix=",ids=[", postfix="]") { "${it.id.take(12)}:${it.function.name}" } ?: "") + ")"
        is ToolMessage -> "tool(toolCallId=${toolCallId.take(12)},name=$name,len=${content.length})"
    }

    private fun Message.toOpenAiMessage(): com.kirakira.kiracode.data.remote.dto.ChatMessage {
        return when (this) {
            is SystemMessage -> com.kirakira.kiracode.data.remote.dto.ChatMessage(
                role = "system",
                content = content
            )
            is UserMessage -> com.kirakira.kiracode.data.remote.dto.ChatMessage(
                role = "user",
                content = content
            )
            is AssistantMessage -> com.kirakira.kiracode.data.remote.dto.ChatMessage(
                role = "assistant",
                content = content,
                tool_calls = toolCalls?.map {
                    com.kirakira.kiracode.data.remote.dto.ToolCall(
                        id = it.id,
                        function = com.kirakira.kiracode.data.remote.dto.FunctionCall(
                            name = it.function.name,
                            arguments = sanitizeArguments(it.function.arguments)
                        )
                    )
                }
            )
            is ToolMessage -> com.kirakira.kiracode.data.remote.dto.ChatMessage(
                role = "tool",
                content = content,
                tool_call_id = toolCallId,
                name = name
            )
        }
    }
}

/**
 * 确保 tool_calls.arguments 始终是合法 JSON 字符串。
 * 提供商可能把 HTML/代码中的换行作为裸控制字符放进 arguments；先尝试转义修复，失败则用空对象，
 * 防止非法参数写入 history 后让下一轮 HTTP 请求被服务商以 400 拒绝。
 */
internal fun sanitizeArguments(raw: String): String {
    if (isValidToolArguments(raw)) return raw

    val escaped = escapeControlCharactersInJsonStrings(raw)
    if (isValidToolArguments(escaped)) return escaped

    Log.e("AgentController", "tool arguments invalid JSON, fallback to {}: ${raw.take(300)}")
    return "{}"
}

/** 工具参数必须是 JSON 对象；默认 Json 解析器会宽容接受裸控制字符，因此额外扫描字符串内部。 */
private fun isValidToolArguments(raw: String): Boolean = try {
    Json.parseToJsonElement(raw).jsonObject
    !hasRawControlCharactersInJsonStrings(raw)
} catch (e: Exception) {
    false
}

private fun hasRawControlCharactersInJsonStrings(raw: String): Boolean {
    var inString = false
    var escaped = false
    for (char in raw) {
        if (inString && char.code < 0x20) return true
        when {
            inString && escaped -> escaped = false
            inString && char == '\\' -> escaped = true
            char == '"' -> inString = !inString
        }
    }
    return false
}

/** 只修复字符串值中的裸控制字符，保留 JSON 对象本身合法的换行与缩进。 */
private fun escapeControlCharactersInJsonStrings(raw: String): String {
    val result = StringBuilder(raw.length)
    var inString = false
    var escaped = false
    var index = 0
    while (index < raw.length) {
        val char = raw[index]
        if (inString && char.code < 0x20) {
            when (char) {
                '\r' -> {
                    result.append("\\n")
                    if (index + 1 < raw.length && raw[index + 1] == '\n') index++
                }
                '\n' -> result.append("\\n")
                '\t' -> result.append("\\t")
                '\b' -> result.append("\\b")
                '\u000C' -> result.append("\\f")
                else -> result.append("\\u").append(char.code.toString(16).padStart(4, '0'))
            }
            escaped = false
        } else {
            result.append(char)
            when {
                inString && escaped -> escaped = false
                inString && char == '\\' -> escaped = true
                char == '"' -> inString = !inString
            }
        }
        index++
    }
    return result.toString()
}
