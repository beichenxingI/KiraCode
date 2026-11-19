package com.kirakira.kiracode.domain.usecase

import com.kirakira.kiracode.data.remote.real.ApiConfig
import com.kirakira.kiracode.data.remote.real.RealApiService
import com.kirakira.kiracode.domain.exception.NetworkException
import com.kirakira.kiracode.domain.agent.PlanBook
import com.kirakira.kiracode.domain.model.CharacterCard
import com.kirakira.kiracode.domain.model.Message
import com.kirakira.kiracode.domain.model.RefSelection
import com.kirakira.kiracode.domain.model.RegexGenerationMode
import com.kirakira.kiracode.domain.model.Session
import com.kirakira.kiracode.domain.model.WorkMode
import com.kirakira.kiracode.domain.model.st.StCardCodec
import com.kirakira.kiracode.domain.prompt.BuiltinSystemPrompt
import com.kirakira.kiracode.domain.repository.MessageRepository
import com.kirakira.kiracode.domain.repository.PreferencesRepository
import com.kirakira.kiracode.domain.repository.SessionRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject


/**
 * V3.0 对话引擎（P0 清理后：仅保留消息组装与配置解析；流式循环与工具执行由 AgentController 承担）。
 *
 * 1. prepareChatMessages：分层组装最终 system/user 消息（协议→模式→工作流→质量→人格→用户规则→卡状态→计划→参考→附件→历史）；
 * 2. 当前工作卡与参考卡（支持整卡/风格/技术/单条目粒度）状态注入系统提示词；
 * 3. 配置自动路由：激活的多模型配置 > 旧版单配置。
 */
class ChatEngine @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val messageRepository: MessageRepository,
    private val realApiService: RealApiService,
    private val preferencesRepository: PreferencesRepository
) {

    companion object {
        /** 送入上下文的最大历史消息数 */
        private const val MAX_HISTORY_MESSAGES = 40
        /** 参考卡摘要截断长度 */
        private const val REF_DIGEST_LIMIT = 400

        /**
         * P0：用户自定义规则注入段（纯函数，可测试）。
         * 仅 [com.kirakira.kiracode.domain.model.CustomPromptState.Enabled] 生成【我的创作规则】段；
         * 未配置/停用 → null（不注入）。
         */
        fun customPromptSection(state: com.kirakira.kiracode.domain.model.CustomPromptState): String? =
            when (state) {
                is com.kirakira.kiracode.domain.model.CustomPromptState.Enabled ->
                    "【我的创作规则】\n${state.text}"
                else -> null
            }
    }


    /**
     * 组装消息列表与生效配置（Agent 内核接入入口，不调用 LLM）。
     * 负责用户消息持久化与统计；返回 (apiMessages, effectiveConfig)。
     */
    suspend fun prepareChatMessages(
        sessionId: Long,
        userContent: String,
        workingCard: CharacterCard?,
        referenceCards: List<CharacterCard>,
        mode: WorkMode,
        refSpecs: List<RefSelection> = emptyList(),
        agentTasks: List<com.kirakira.kiracode.domain.agent.AgentTask> = emptyList(),
        planBook: PlanBook? = null,
        temperatureOverride: Float? = null,
        topPOverride: Float? = null,
        cardMasterEnabled: Boolean = false,
        webSearchEnabled: Boolean = false,
        personaPrompt: String? = null,
        reasoningEffort: String? = null,
        /** V3.1 任务6：+号附件（文件名 → 文本内容），注入 system 参考资料段 */
        referenceFiles: List<Pair<String, String>> = emptyList(),
        /** 会话级创作偏好注入文本（系统协议之后、其它内容之前） */
        preferenceInjection: String = "",
        regexGenerationMode: RegexGenerationMode = RegexGenerationMode.DEFAULT
    ): Pair<List<com.kirakira.kiracode.data.remote.dto.ChatMessage>, ApiConfig> {
        val config = resolveActiveConfig()
            ?: throw NetworkException(
                "尚未配置 AI 接口：请在「模型配置」中添加 Base URL 与 API Key",
                errorCode = "MISSING_CONFIG"
            )
        val session = sessionRepository.getSession(sessionId).getOrNull()
            ?: throw NetworkException("会话不存在", errorCode = "SESSION_NOT_FOUND")
        val userMessage = Message.createUserMessage(sessionId, userContent)
        messageRepository.insertMessage(userMessage)
            .onFailure { e ->
                android.util.Log.e("ChatEngine", "insertMessage failed for session $sessionId", e)
            }
        bumpStats(sessionId, userContent)
        val apiMessages = buildMessages(session, sessionId, workingCard, referenceCards, mode, refSpecs, agentTasks, planBook, cardMasterEnabled, webSearchEnabled, personaPrompt, referenceFiles, preferenceInjection, regexGenerationMode)
        // 诊断：最终请求摘要（只记录数量/名称/长度/marker，不记录正文）
        run {
            val sys = apiMessages.firstOrNull { it.role == "system" }?.content.orEmpty()
            val refSection = sys.substringAfter("【参考角色卡】", "")
            val preparedLog = "PREPARED_MESSAGES count=${apiMessages.size} " +
                "roles=[${apiMessages.joinToString(",") { it.role }}] " +
                "REFERENCE_CARDS count=${referenceCards.size} names=[${referenceCards.joinToString(",") { it.name }}] " +
                "REFERENCE_FILES count=${referenceFiles.size} names=[${referenceFiles.joinToString(",") { it.first }}] " +
                "WORKING_CARD present=${workingCard != null} name=${workingCard?.name.orEmpty()} " +
                "SYSTEM_MESSAGE length=${sys.length} " +
                "SYSTEM_HAS_REFERENCE_MARKER=${sys.contains("【参考角色卡】")} SYSTEM_REF_SECTION_LENGTH=${refSection.length} " +
                "SYSTEM_HAS_FILE_MARKER=${sys.contains("【参考资料")}"
            android.util.Log.d("ChatEngine", preparedLog)
            com.kirakira.kiracode.util.AppLogger.d("ChatEngine", preparedLog)
        }
        val effectiveConfig = config.copy(
            temperature = temperatureOverride ?: session.temperature,
            maxTokens = if (mode == WorkMode.YOLO) (session.maxTokens).coerceAtLeast(4096) else session.maxTokens,
            topP = topPOverride ?: config.topP,
            reasoningEffort = reasoningEffort ?: config.reasoningEffort
        )
        return apiMessages to effectiveConfig
    }

    /**
     * 构建发送给 API 的消息列表：
     * system(协议+规则+卡状态+计划书+参考画像) + 历史。
     */
    private suspend fun buildMessages(
        session: Session,
        sessionId: Long,
        workingCard: CharacterCard?,
        referenceCards: List<CharacterCard>,
        mode: WorkMode,
        refSpecs: List<RefSelection>,
        agentTasks: List<com.kirakira.kiracode.domain.agent.AgentTask>,
        planBook: PlanBook?,
        cardMasterEnabled: Boolean,
        webSearchEnabled: Boolean = false,
        personaPrompt: String?,
        referenceFiles: List<Pair<String, String>> = emptyList(),
        preferenceInjection: String = "",
        regexGenerationMode: RegexGenerationMode = RegexGenerationMode.DEFAULT
    ): List<com.kirakira.kiracode.data.remote.dto.ChatMessage> {
        val messages = mutableListOf<com.kirakira.kiracode.data.remote.dto.ChatMessage>()

        val systemParts = mutableListOf<String>()
        // 1. 固定协议（默认开启；制卡师开关叠加决策询问准则；创作/改卡工作流按工作卡有无分离）
        // 联网调研段仅当开关开启时注入（关闭时 prompt 不宣传联网工具，避免模型幻觉调用）
        // P0：参考材料边界按挂载情况注入；大卡（索引注入）使用独立改卡工作流
        if (preferencesRepository.builtinSystemPromptEnabled.first()) {
            systemParts += BuiltinSystemPrompt.build(
                mode, cardMasterEnabled,
                isCardEditing = workingCard != null,
                webSearchEnabled = webSearchEnabled,
                hasReferenceMaterial = referenceCards.isNotEmpty() || referenceFiles.isNotEmpty(),
                usesCardIndex = workingCard != null &&
                        com.kirakira.kiracode.domain.agent.CardIndex.classify(workingCard) !=
                        com.kirakira.kiracode.domain.agent.CardScale.SMALL,
                regexGenerationMode = regexGenerationMode
            )
        }
        // 1.5 会话级创作偏好（系统协议之后、其它内容之前）
        if (preferenceInjection.isNotBlank()) {
            systemParts += preferenceInjection
        }
        // 2. 灵魂（创作风格基线）
        personaPrompt?.takeIf { it.isNotBlank() }?.let {
            systemParts += "【灵魂（创作风格基线，请遵循）】\n$it"
        }
        // 3. 用户自定义创作规则（P0 三态：仅用户主动启用且内容非空才注入；与人格层相互独立不重复）
        customPromptSection(preferencesRepository.customPromptState.first())?.let {
            systemParts += it
        }
        // 3. 会话级系统提示词
        session.systemPrompt.takeIf { it.isNotBlank() }?.let { systemParts += it }
        // 4. 历史摘要
        session.summary.takeIf { it.isNotBlank() }?.let {
            systemParts += "【更早对话的摘要】\n$it"
        }
        // 5. 当前工作卡状态（动态感知会话状态）
        if (workingCard != null && mode != WorkMode.CHAT) {
            val bookCount = workingCard.characterBook?.entries?.size ?: 0
            val regexCount = workingCard.regexScripts.size
            systemParts += buildString {
                appendLine("【当前角色卡状态】名称=${workingCard.name}")
                appendLine("字段概况：description=${if (workingCard.description.isBlank()) "未填" else "已填(${workingCard.description.length}字)"}，" +
                        "personality=${if (workingCard.personality.isBlank()) "未填" else "已填"}，" +
                        "scenario=${if (workingCard.scenario.isBlank()) "未填" else "已填"}，" +
                        "first_mes=${if (workingCard.firstMessage.isBlank()) "未填" else "已填"}，" +
                        "tags=${workingCard.tags.size}个")
                appendLine("世界书条目=$bookCount 条，正则=$regexCount 条")
                // 大小分级注入：SMALL 完整 JSON（旧行为兼容）；MEDIUM/LARGE 摘要+索引+按需查询（大卡不撞上下文上限）
                if (com.kirakira.kiracode.domain.agent.CardIndex.classify(workingCard) ==
                    com.kirakira.kiracode.domain.agent.CardScale.SMALL
                ) {
                    appendLine("当前卡较小，完整 JSON 如下（仅供你读取现状，不要照抄输出；无需调用 query_card_entries/get_card_entry）：")
                    append(StCardCodec.toJsonString(workingCard, pretty = false))
                } else {
                    append(com.kirakira.kiracode.domain.agent.CardIndex.buildOnDemandSection(workingCard))
                }
            }
        } else if (workingCard != null) {
            systemParts += "【讨论中的角色卡】名称:${workingCard.name}；描述:${workingCard.description.take(600)}"
        } else if (mode != WorkMode.CHAT) {
            // 无卡：明确告知 AI 需先用工具建卡（协议细节由系统提示词的工具协议层承担）
            systemParts += "【当前角色卡状态】会话中尚无角色卡。" +
                    "请用工具创建：update_card_field 逐字段设置（必须先设 name），或 replace_card_json 整卡创建；" +
                    "禁止在聊天正文中直接输出 JSON，禁止输出任何文本协议指令块。"
        }
        // 6. 计划书状态（由 submit_plan/complete_step 工具维护；此处仅注入现状）
        if (planBook != null && (planBook.goal.isNotBlank() || planBook.parts.isNotEmpty())) {
            systemParts += "【当前计划书】\n${planBook.digest()}"
        }
        // 7. 任务板状态（信息展示；当前无任务写工具，AI 不维护任务板）
        if (agentTasks.isNotEmpty()) {
            val digest = agentTasks.take(12).joinToString("\n") { t ->
                "[${t.status}] #${t.id} ${t.title}"
            }
            systemParts += "【当前任务板（仅供参考）】\n$digest"
        }
        // 8. 参考卡画像（按粒度）
        if (referenceCards.isNotEmpty()) {
            val digests = referenceCards.mapIndexed { i, card ->
                val spec = refSpecs.firstOrNull { it.uuid == card.uuid }
                referenceDigest(i, card, spec)
            }
            systemParts += digests.joinToString("\n\n", prefix = "【参考角色卡】\n")
        }

        // V3.1 任务6：参考文件（+号附件）——文本内容注入 system，AI 可直接阅读
        if (referenceFiles.isNotEmpty()) {
            systemParts += buildString {
                appendLine("【参考资料（用户提供，创作时按需引用）】")
                referenceFiles.forEachIndexed { i, (name, content) ->
                    appendLine("── 文件 ${i + 1}：$name ──")
                    appendLine(content.take(20000))
                }
            }
        }

        // V3.1 批次4任务4：注入校验日志（临时调试）
        val systemContent = systemParts.joinToString("\n\n")
        android.util.Log.d(
            "ChatEngine",
            "SystemMessage len=${systemContent.length} 参考资料=${if (systemContent.contains("【参考资料")) "已注入" else "无"} " +
                "referenceFiles=${referenceFiles.size}"
        )
        if (referenceFiles.isNotEmpty() && !systemContent.contains("【参考资料")) {
            android.util.Log.e("ChatEngine", "referenceFiles 传了但没注入！count=${referenceFiles.size}")
        }

        messages += com.kirakira.kiracode.data.remote.dto.ChatMessage(
            role = "system",
            content = systemContent
        )

        // V2 Beta 注入验证日志：确认各提示词段真实进入 system 消息（logcat: KiraCard/Prompt）
        run {
            val built = messages.first().content.orEmpty()
            val hasBuiltin = built.contains("KiraCard 制卡助手") || built.contains("KiraCard 创作顾问")
            val hasPersona = built.contains("【灵魂")
            val hasCustom = built.contains("【我的创作规则】")
            val hasSummary = built.contains("【更早对话的摘要】")
            val hasCard = built.contains("【当前角色卡状态】") || built.contains("【讨论中的角色卡】")
            android.util.Log.i(
                "KiraCard/Prompt",
                "注入结构 system=${built.length}字 → " +
                        "固定协议=$hasBuiltin 人格=$hasPersona 自定义规则=$hasCustom " +
                        "会话摘要=$hasSummary 卡状态=$hasCard 历史消息=${messages.size - 1}条 " +
                        "personaPrompt参数=${personaPrompt?.length ?: 0}字"
            )
        }

        // 9. 历史（最近 N 条，排除空内容）
        val recent = messageRepository.getRecentMessages(sessionId, MAX_HISTORY_MESSAGES)
            .getOrNull().orEmpty()
        for (msg in recent) {
            if (msg.content.isBlank()) continue
            if (msg.role !in setOf("user", "assistant")) continue
            messages += com.kirakira.kiracode.data.remote.dto.ChatMessage(
                role = msg.role,
                content = msg.content
            )
        }
        return messages
    }

    /** 按粒度生成参考卡摘要 */
    private fun referenceDigest(index: Int, card: CharacterCard, spec: RefSelection?): String = buildString {
        appendLine("── 参考卡 ${index + 1}: ${card.name} ── 参考范围: ${spec?.describe() ?: RefSelection.whole(card.uuid).describe()} ── 只学风格与结构，禁止照抄本卡设定/剧情/原文 ──")
        val gran = spec?.granularity ?: RefSelection.GRAN_WHOLE
        val wantWhole = gran == RefSelection.GRAN_WHOLE || spec == null

        // 风格面：语气与行文样本
        if (wantWhole || gran == RefSelection.GRAN_STYLE) {
            if (card.description.isNotBlank()) appendLine("描述: ${card.description.take(REF_DIGEST_LIMIT)}")
            if (card.personality.isNotBlank()) appendLine("性格: ${card.personality.take(200)}")
            if (card.scenario.isNotBlank()) appendLine("场景: ${card.scenario.take(200)}")
            if (card.firstMessage.isNotBlank()) appendLine("开场白样本(学其口吻): ${card.firstMessage.take(REF_DIGEST_LIMIT)}")
            if (card.exampleDialogue.isNotBlank()) appendLine("对话样本(学其格式): ${card.exampleDialogue.take(REF_DIGEST_LIMIT)}")
            if (card.tags.isNotEmpty()) appendLine("标签: ${card.tags.joinToString("/")}")
        }

        // 技术面：世界书与正则的写法结构
        if (wantWhole || gran == RefSelection.GRAN_TECH) {
            card.characterBook?.entries?.takeIf { it.isNotEmpty() }?.let { entries ->
                appendLine("世界书条目(${entries.size} 条，学习其 keys/constant/insertion_order 写法):")
                entries.take(8).forEach { e ->
                    appendLine("  · #${e.id} 「${e.comment}」keys=${e.keys.joinToString("|")} constant=${constantLabel(e.constant)} order=${e.insertionOrder}")
                    appendLine("    内容节选: ${e.content.take(160)}")
                }
            }
            card.regexScripts.takeIf { it.isNotEmpty() }?.let { scripts ->
                appendLine("正则脚本(${scripts.size} 条，学习其 find_regex/placement 写法):")
                scripts.take(6).forEach { s ->
                    appendLine("  · 「${s.scriptName}」find=${s.findRegex.take(80)} → replace=${s.replaceString.take(40)} placement=${s.placement}")
                }
            }
        }

        // 指定条目：仅注入所选内容
        spec?.entries?.takeIf { it.isNotEmpty() }?.forEach { label ->
            val entry = card.characterBook?.entries?.firstOrNull {
                it.comment == label || it.id.toString() == label
            }
            if (entry != null) {
                appendLine("◆ 指定世界书条目「${entry.comment}」全文:")
                appendLine(entry.content.take(800))
            }
            val regex = card.regexScripts.firstOrNull { it.scriptName == label }
            if (regex != null) {
                appendLine("◆ 指定正则脚本「${regex.scriptName}」:")
                appendLine("find=${regex.findRegex} replace=${regex.replaceString} placement=${regex.placement}")
            }
        }
    }.trim()

    private fun constantLabel(v: Boolean?) = if (v == true) "常驻" else "触发"

    /**
     * 解析激活的 API 配置：激活模型配置优先，否则回退旧版单配置。
     */
    suspend fun resolveActiveConfig(): ApiConfig? {
        val configs = preferencesRepository.modelConfigs.first()
        val activeId = preferencesRepository.activeModelConfigId.first()
        val active = configs.firstOrNull { it.id == activeId && it.baseUrl.isNotBlank() && it.apiKey.isNotBlank() }
            ?: configs.firstOrNull { it.baseUrl.isNotBlank() && it.apiKey.isNotBlank() }
        if (active != null) {
            return ApiConfig(
                apiKey = active.apiKey,
                baseUrl = normalizeBaseUrl(active.baseUrl),
                model = active.selectedModel ?: active.models.firstOrNull() ?: "gpt-4o-mini",
                providerId = "custom"
            )
        }
        // 回退旧版键
        val key = preferencesRepository.apiKey.first()
        val baseUrl = preferencesRepository.apiBaseUrl.first()
        if (!key.isNullOrBlank()) {
            return ApiConfig(
                apiKey = key,
                baseUrl = normalizeBaseUrl(baseUrl ?: "https://api.openai.com"),
                model = preferencesRepository.selectedModel.first() ?: "gpt-4o-mini",
                providerId = preferencesRepository.apiProvider.first() ?: "openai"
            )
        }
        return null
    }

    /**
     * 按模型配置组 id 解析 API 配置；id 为空或找不到时回退激活配置。
     * 供灵魂训练等需要显式指定模型的流程使用。
     */
    suspend fun resolveConfigById(modelConfigId: String?): ApiConfig? {
        if (!modelConfigId.isNullOrBlank()) {
            val configs = preferencesRepository.modelConfigs.first()
            val target = configs.firstOrNull {
                it.id == modelConfigId && it.baseUrl.isNotBlank() && it.apiKey.isNotBlank()
            }
            if (target != null) {
                return ApiConfig(
                    apiKey = target.apiKey,
                    baseUrl = normalizeBaseUrl(target.baseUrl),
                    model = target.selectedModel ?: target.models.firstOrNull() ?: "gpt-4o-mini",
                    providerId = "custom"
                )
            }
        }
        return resolveActiveConfig()
    }

    /** 规范化 Base URL：去尾部斜杠 */
    private fun normalizeBaseUrl(url: String): String =
        url.trim().trimEnd('/')

    private suspend fun bumpStats(sessionId: Long, content: String) {
        val deltaTokens = TokenEstimator.estimateTokens(content)
        sessionRepository.incrementStats(sessionId, deltaTokens)
            .onFailure { e ->
                android.util.Log.w("ChatEngine", "bumpStats failed for session $sessionId", e)
            }
    }
}
