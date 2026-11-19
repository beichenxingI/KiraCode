package com.kirakira.kiracode.ui.workspace

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.os.SystemClock
import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kirakira.kiracode.domain.agent.CardOperation
import com.kirakira.kiracode.domain.agent.CardOperationApplier
import com.kirakira.kiracode.domain.agent.CardOpsParser
import com.kirakira.kiracode.domain.agent.aiGuessFlag
import com.kirakira.kiracode.domain.model.AuditReport
import com.kirakira.kiracode.domain.model.CharacterCard
import com.kirakira.kiracode.domain.model.ChatMessage
import com.kirakira.kiracode.domain.model.ChatSession
import com.kirakira.kiracode.domain.model.ContextInfo
import com.kirakira.kiracode.domain.model.Message
import com.kirakira.kiracode.domain.model.PreferenceDimension
import com.kirakira.kiracode.domain.model.Session
import com.kirakira.kiracode.domain.model.SessionPreferences
import com.kirakira.kiracode.domain.model.WorkMode
import com.kirakira.kiracode.domain.model.ReasoningUiState
import com.kirakira.kiracode.domain.model.RegexGenerationMode
import com.kirakira.kiracode.domain.model.st.StCardCodec
import com.kirakira.kiracode.domain.repository.MessageRepository
import com.kirakira.kiracode.domain.repository.PreferenceRepository
import com.kirakira.kiracode.domain.repository.ReferenceCardRepository
import com.kirakira.kiracode.domain.repository.SessionRepository
import com.kirakira.kiracode.domain.usecase.ChatEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import java.io.ByteArrayOutputStream
import javax.inject.Inject

/** 工作卡封面压缩后上限（Base64 前字节数） */
private const val MAX_COVER_BYTES = 400 * 1024
/** 相册原图上限，避免一次性读入超大文件 */
private const val MAX_RAW_COVER_BYTES = 15 * 1024 * 1024

/**
 * 工作区状态：当前编辑卡、模式、模型信息。
 */
data class WorkspaceState(
    val workingCard: CharacterCard? = null,
    val workMode: WorkMode = WorkMode.YOLO,
    val selectedReferenceIds: List<String> = emptyList(),
    // V3.0 细粒度参考选择（uuid → 整卡/风格/技术/指定条目）
    val refSelections: List<com.kirakira.kiracode.domain.model.RefSelection> = emptyList(),
    // V3.0 计划书（全自动模式）
    val planBook: com.kirakira.kiracode.domain.agent.PlanBook = com.kirakira.kiracode.domain.agent.PlanBook(),
    val activeModelName: String = "",
    val activeModelMaxTokens: Int = 32768,
    val contextInfo: ContextInfo = ContextInfo(),
    val hasConfiguredApi: Boolean = false,
    val toast: String? = null,
    // V2.1 数据面板：Agent 任务板（AI 可读写，实时更新）
    val tasks: List<com.kirakira.kiracode.domain.agent.AgentTask> = emptyList(),
    // 控制面板
    val temperature: Float = 0.8f,
    val topP: Float = 0.95f,
    val autoSummarize: Boolean = true,
    val customPrompt: String = "",
    val webSearchEnabled: Boolean = false,
    // V1.0.0 上下文窗口管理
    val contextWindowTokens: Int = 128000,
    val summarizeThreshold: Float = 0.8f,
    // V1.0 制卡师开关与思考强度
    val cardMasterEnabled: Boolean = true,
    val regexGenerationMode: RegexGenerationMode = RegexGenerationMode.DEFAULT,
    val reasoningEffort: String? = null,
    // 解禁工具调用次数（大型重构；硬上限 200 轮）
    val unlimitedToolCalls: Boolean = false,
    // V2/V3：Agent 结构化卡片（工具调用/observation/追问/计划/进度/Diff，渲染在消息流中）
    val agentCards: List<AgentCardUi> = emptyList(),
    // V2/V3：当前挂起的交互（ask_user 追问 / submit_plan 计划 / diff 审查）
    val pendingInteraction: PendingInteraction? = null,
    // V2.1：导出进行中（ExportDialog 进度条）
    val exporting: Boolean = false
)

sealed interface ChatUiState {
    data class Loading(val sessionId: Long? = null) : ChatUiState
    data class Ready(
        val sessionId: Long,
        val messages: List<ChatMessage> = emptyList(),
        val isSending: Boolean = false,
        val error: String? = null
    ) : ChatUiState
    data class Error(val message: String, val sessionId: Long? = null) : ChatUiState
    data class Empty(val sessionId: Long? = null) : ChatUiState
}

data class SessionListUiState(
    val sessions: List<ChatSession> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

/**
 * 聊天页面 ViewModel（V2.0）
 *
 * 职责：
 * - 通过 [ChatEngine] 驱动真实多轮对话；
 * - 维护工作区（工作卡/模式/参考卡选择）并持久化到会话；
 * - 三种模式的操作执行策略：会话=忽略，代理人=入队待确认，全局=自动应用。
 */
@HiltViewModel
class WorkspaceViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val messageRepository: MessageRepository,
    private val chatEngine: ChatEngine,
    private val summarizeContextUseCase: com.kirakira.kiracode.domain.usecase.SummarizeContextUseCase,
    private val preferencesRepository: com.kirakira.kiracode.domain.repository.PreferencesRepository,
    private val preferenceRepository: PreferenceRepository,
    private val referenceCardRepository: ReferenceCardRepository,
    private val characterCardRepository: com.kirakira.kiracode.domain.repository.CharacterCardRepository,
    private val webSearchService: com.kirakira.kiracode.data.remote.WebSearchService,
    private val trainPersonaUseCase: com.kirakira.kiracode.domain.usecase.TrainPersonaUseCase,
    private val realApiService: com.kirakira.kiracode.data.remote.real.RealApiService,
    private val agentController: com.kirakira.kiracode.domain.agent.AgentController,
    private val toolRegistry: com.kirakira.kiracode.domain.agent.ToolRegistry,
    private val exportCharacterCardUseCase: com.kirakira.kiracode.domain.usecase.ExportCharacterCardUseCase,
    @dagger.hilt.android.qualifiers.ApplicationContext private val appContext: android.content.Context
) : ViewModel() {

    private val _uiState = MutableStateFlow<ChatUiState>(ChatUiState.Empty())
    val uiState: StateFlow<ChatUiState> = _uiState

    private val _sessionListState = MutableStateFlow(SessionListUiState())
    val sessionListState: StateFlow<SessionListUiState> = _sessionListState

    private val _workspace = MutableStateFlow(WorkspaceState())
    val workspace: StateFlow<WorkspaceState> = _workspace

    /** 参考卡仓库全量列表（供参考卡选择器） */
    private val _referenceCards = MutableStateFlow<List<CharacterCard>>(emptyList())
    val referenceCards: StateFlow<List<CharacterCard>> = _referenceCards

    /** 全局创作偏好目录（维度 + 选项） */
    val preferenceCatalog: StateFlow<List<PreferenceDimension>> =
        preferenceRepository.observeDimensions()
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** 当前会话的偏好选择（会话级；空态下作为待创建会话的草稿） */
    private val _sessionPreferences = MutableStateFlow(SessionPreferences.Empty)
    val sessionPreferences: StateFlow<SessionPreferences> = _sessionPreferences

    /** 当前激活配置的可用模型列表（聊天页快捷切换） */
    private val _availableModels = MutableStateFlow<List<String>>(emptyList())
    val availableModels: StateFlow<List<String>> = _availableModels

    /** V1.0 灵魂列表（设置浮窗灵魂 Tab 使用） */
    private val _personas = MutableStateFlow<List<com.kirakira.kiracode.domain.model.Persona>>(emptyList())
    val personas: StateFlow<List<com.kirakira.kiracode.domain.model.Persona>> = _personas

    private val _activePersonaId = MutableStateFlow<String?>(null)
    val activePersonaId: StateFlow<String?> = _activePersonaId

    /** 首次启动引导（AGPLv3 声明） */
    private val _isFirstLaunch = MutableStateFlow(false)
    val isFirstLaunch: StateFlow<Boolean> = _isFirstLaunch

    // ============ 思考强度探测 ============

    /** 探测结果缓存：model → true支持/false不支持；无记录=未探测 */
    private val _reasoningProbeCache = MutableStateFlow<Map<String, Boolean>>(emptyMap())

    private val _reasoningProbingModels = MutableStateFlow<Set<String>>(emptySet())
    val reasoningProbingModels: StateFlow<Set<String>> = _reasoningProbingModels

    private val probeJobs = mutableMapOf<String, kotlinx.coroutines.Job>()

    /**
     * 当前模型思考强度 UI 状态：启发式或探测任一通过即可用；
     * 探测明确不支持则隐藏；探测中显示进行态。
     */
    fun reasoningStateFor(model: String): ReasoningUiState {
        val family = com.kirakira.kiracode.domain.ai.ReasoningSupport.heuristicFamily(model)
        val cached = _reasoningProbeCache.value[model.lowercase()]
        val probing = model.lowercase() in _reasoningProbingModels.value
        val supported = when (cached) {
            true -> true
            false -> false
            null -> family != null
        }
        return ReasoningUiState(
            supported = supported,
            probing = probing,
            familyLabel = family?.label ?: "通用"
        )
    }

    /** 面板打开/模型切换时触发探测（有缓存则跳过） */
    fun ensureReasoningProbe() {
        val model = _workspace.value.activeModelName
        if (model.isBlank()) return
        val key = model.lowercase()
        if (_reasoningProbeCache.value.containsKey(key) || probeJobs.containsKey(key)) return
        viewModelScope.launch {
            val config = runCatching { chatEngine.resolveActiveConfig() }.getOrNull() ?: return@launch
            if (!config.model.equals(model, ignoreCase = true)) return@launch
            _reasoningProbingModels.value = _reasoningProbingModels.value + key
            try {
                val result = realApiService.probeReasoningEffort(config)
                if (result != null) {
                    _reasoningProbeCache.value = _reasoningProbeCache.value + (key to result)
                    runCatching {
                        preferencesRepository.setReasoningProbeJson(
                            com.kirakira.kiracode.domain.ai.ReasoningProbeCache.encode(_reasoningProbeCache.value)
                        )
                    }
                }
            } finally {
                _reasoningProbingModels.value = _reasoningProbingModels.value - key
                probeJobs.remove(key)
            }
        }.also { probeJobs[key] = it }
    }


    var currentSessionId: Long? = null
        private set

    /** V2/V3：当前流式占位消息 id（分轮收束；交互回答后空占位移除重建） */
    private var currentPlaceholderId: String = ""

    /** V2.1：流式实时思维链（ReasoningDelta 累积，轮次收束后清空；定稿挂到消息 thinking） */
    private val _liveReasoning = MutableStateFlow<String?>(null)
    val liveReasoning: StateFlow<String?> = _liveReasoning

    /** V3.1 批次4任务3：执行中（驱动命令栏 loading；挂起等待用户输入时为 false） */
    private val _isExecuting = MutableStateFlow(false)
    val isExecuting: StateFlow<Boolean> = _isExecuting

    /** 上一次请求的提示词 token（估算缓存命中用） */
    private var lastPromptTokens: Int = 0

    init {
        viewModelScope.launch {
            runCatching { preferenceRepository.ensureDefaults() }
            refreshModelInfo()
            referenceCardRepository.getAllReferenceCards(200, 0).getOrNull().orEmpty().let { _referenceCards.value = it }
            _workspace.update {
                it.copy(
                    webSearchEnabled = preferencesRepository.webSearchEnabled.first(),
                    cardMasterEnabled = preferencesRepository.cardMasterEnabled.first(),
                    regexGenerationMode = preferencesRepository.regexGenerationMode.first(),
                    unlimitedToolCalls = preferencesRepository.unlimitedToolCalls.first(),
                    reasoningEffort = preferencesRepository.reasoningEffortPref.first(),
                    contextWindowTokens = preferencesRepository.contextWindowTokens.first(),
                    summarizeThreshold = preferencesRepository.contextSummarizeThreshold.first()
                )
            }
            _reasoningProbeCache.value = com.kirakira.kiracode.domain.ai.ReasoningProbeCache
                .decode(preferencesRepository.reasoningProbeJson.first())
            _personas.value = com.kirakira.kiracode.domain.model.PersonaCodec
                .decodeList(preferencesRepository.personasJson.first())
            _activePersonaId.value = preferencesRepository.activePersonaId.first()
            loadCustomPromptSets()
        }
        viewModelScope.launch {
            // 配置组变化（拉取模型/选择模型/切换配置）→ 工作台状态实时生效
            preferencesRepository.modelConfigs.collect {
                refreshModelInfo()
            }
        }
        viewModelScope.launch {
            // 观察参考卡库变化
            runCatching {
                referenceCardRepository.observeReferenceCards().collect { cards ->
                    _referenceCards.value = cards
                }
            }
        }
        viewModelScope.launch {
            // 角色卡仓库页的多选结果 → 工作区参考卡选择
            com.kirakira.kiracode.ui.common.ReferenceSelectionBus.selectedUuids.collect { uuids ->
                if (uuids != _workspace.value.selectedReferenceIds) {
                    _workspace.update { it.copy(selectedReferenceIds = uuids) }
                    persistWorkspace()
                }
            }
        }
        viewModelScope.launch {
            // V3.1 批次4任务3：进入挂起交互（追问/计划/Diff 确认）→ 执行态置 false（输入框可答）
            _workspace.collect { ws ->
                if (ws.pendingInteraction != null) _isExecuting.value = false
            }
        }
        viewModelScope.launch {
            // V2 Beta：实时观察人格变化（训练独立页保存后，聊天页立即同步，注入不失效）
            runCatching {
                preferencesRepository.personasJson.collect { json ->
                    val decoded = com.kirakira.kiracode.domain.model.PersonaCodec.decodeList(json)
                    if (decoded != _personas.value) _personas.value = decoded
                }
            }
        }
        viewModelScope.launch {
            runCatching {
                preferencesRepository.activePersonaId.collect { id ->
                    if (id != _activePersonaId.value) _activePersonaId.value = id
                }
            }
        }
        viewModelScope.launch {
            // 历史列表由 Room Flow 驱动：创建会话 / 首条消息更新 messageCount 后自动刷新
            sessionRepository.observeActiveSessions()
                .catch { e ->
                    android.util.Log.e("WorkspaceViewModel", "sessions flow error", e)
                    _sessionListState.update {
                        it.copy(isLoading = false, error = e.message)
                    }
                }
                .collect { sessions ->
                    _sessionListState.update { current ->
                        current.copy(
                            sessions = mapSessionsToUiItems(sessions),
                            isLoading = false,
                            error = null
                        )
                    }
                }
        }
    }

    // ============ V3.0 工作台：卡片项目 + 工作区切换 + 面板状态 ============

    /** 全部角色卡项目（左侧栏「我的卡」，character card 库） */
    private val _allCards = MutableStateFlow<List<CharacterCard>>(emptyList())
    val allCards: StateFlow<List<CharacterCard>> = _allCards

    /** 刷新卡片项目列表（进入工作台/成品区变动后） */
    fun refreshCards() {
        viewModelScope.launch {
            runCatching {
                characterCardRepository.getHistory(200, 0).getOrNull().orEmpty().let { _allCards.value = it }
            }
        }
    }

    /** 右侧预览面板收起状态 */
    private val _previewCollapsed = MutableStateFlow(false)
    val previewCollapsed: StateFlow<Boolean> = _previewCollapsed

    fun togglePreview() {
        _previewCollapsed.value = !_previewCollapsed.value
    }

    /** 左侧栏收起状态（宽屏收起到图标条；窄屏为抽屉） */
    private val _sidebarCollapsed = MutableStateFlow(false)
    val sidebarCollapsed: StateFlow<Boolean> = _sidebarCollapsed

    fun toggleSidebar() {
        _sidebarCollapsed.value = !_sidebarCollapsed.value
    }

    /**
     * 左侧栏点卡：切换工作卡 + 打开/新建该卡的会话（事件流与预览随之更新）。
     * 已有以该卡为工作卡的会话 → 打开最近的；否则新建（标题=卡名，保留该卡）。
     */
    fun switchCard(card: CharacterCard) {
        agentJob?.cancel()
        setWorkingCard(card)
        viewModelScope.launch {
            val sessions = sessionRepository.getActiveSessions(limit = 50, offset = 0).getOrDefault(emptyList())
            val existing = sessions.firstOrNull { s ->
                decodeCard(s.workingCardJson)?.uuid == card.uuid
            }
            if (existing != null) {
                openSessionInternal(existing.id)
            } else {
                createNewSession(title = "${card.name.ifBlank { "未命名角色" }} · 创作", keepWorkingCard = true)
            }
            refreshCards()
        }
    }

    /** V3.1 批次1：参考文件（+ 按钮文件选择器加载的小说/文本/角色卡 JSON） */
    private val _referenceFiles = MutableStateFlow<List<ReferenceFile>>(emptyList())
    val referenceFiles: StateFlow<List<ReferenceFile>> = _referenceFiles

    /**
     * 文件选择器回调：读取所选文件（.json/.png/.txt 等文本）内容入参考文件列表。
     * 文本/json 读取内容（200KB 截断保护）；图片仅记录名称；
     * UI 展示后续批次接入，本批次以 Toast 反馈。
     */
    fun addReferenceFiles(uris: List<android.net.Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            var loaded = 0
            val detectedCards = mutableListOf<com.kirakira.kiracode.domain.model.CharacterCard>()
            for (uri in uris) {
                runCatching {
                    val resolver = appContext.contentResolver
                    val name = resolver.query(
                        uri,
                        arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
                        null, null, null
                    )?.use { c ->
                        if (c.moveToFirst()) c.getString(0) else null
                    } ?: "未命名文件"
                    val mime = resolver.getType(uri).orEmpty()
                    val isPng = mime == "image/png" || name.endsWith(".png", ignoreCase = true)

                    if (mime.startsWith("image/")) {
                        // PNG 角色卡检测：tEXt 内嵌 ccv3/chara 元数据 → 解码 → 自动导入并挂载
                        if (isPng) {
                            val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
                            val cardJson = bytes?.let {
                                com.kirakira.kiracode.domain.util.PngTextChunkCodec.extractCardJson(it)
                            }
                            if (cardJson != null) {
                                val decoded = com.kirakira.kiracode.domain.model.st.StCardCodec
                                    .decode(cardJson).getOrNull()
                                val card = decoded?.let { c ->
                                    val pngBytes = bytes
                                    if (c.avatar.isNullOrBlank() && pngBytes != null) {
                                        c.copy(
                                            avatar = com.kirakira.kiracode.domain.util.PngTextChunkCodec
                                                .toDataUri(pngBytes)
                                        )
                                    } else {
                                        c
                                    }
                                }
                                if (card != null) {
                                    com.kirakira.kiracode.util.AppLogger.d("WorkspaceVM", "检测到 PNG 角色卡: ${card.name}")
                                    detectedCards += card
                                    loaded++
                                    return@runCatching
                                }
                            }
                            com.kirakira.kiracode.util.AppLogger.d("WorkspaceVM", "PNG 不是角色卡: $name")
                        }
                        // 普通图片（或非角色卡 PNG）：仅记录引用，无文本内容不注入上下文
                        _referenceFiles.value += ReferenceFile(uri = uri, name = name, content = "", sizeBytes = 0)
                    } else {
                        val text = resolver.openInputStream(uri)?.use {
                            it.readBytes().toString(Charsets.UTF_8)
                        }.orEmpty()
                        _referenceFiles.value += ReferenceFile(
                            uri = uri,
                            name = name,
                            content = text.take(200_000),
                            sizeBytes = text.toByteArray(Charsets.UTF_8).size.toLong()
                        )
                    }
                }.onSuccess { loaded++ }
            }
            // PNG 角色卡：自动导入参考卡仓库并挂载到当前会话
            if (detectedCards.isNotEmpty()) {
                importAndMountCards(detectedCards)
            }
            _workspace.update {
                it.copy(
                    toast = "已加载 $loaded/${uris.size} 个参考文件" +
                        if (detectedCards.isNotEmpty()) "（${detectedCards.size} 张 PNG 角色卡已自动导入并挂载）" else ""
                )
            }
        }
    }

    /** PNG 角色卡自动导入参考卡仓库并挂载到当前会话 */
    private suspend fun importAndMountCards(cards: List<com.kirakira.kiracode.domain.model.CharacterCard>) {
        runCatching {
            val importedIds = mutableListOf<String>()
            cards.forEach { card ->
                referenceCardRepository.saveReferenceCard(card, source = "imported")
                    .onSuccess {
                        com.kirakira.kiracode.util.AppLogger.d(
                            "WorkspaceVM",
                            "PNG 角色卡已导入: ${card.name}, uuid=${card.uuid}, rowId=$it"
                        )
                    }
                importedIds += card.uuid
            }
            // 刷新参考卡缓存（restore 收口后 uuid 与保存一致）
            referenceCardRepository.getAllReferenceCards(500, 0).getOrNull()?.let {
                _referenceCards.value = it
            }
            // 追加挂载（保留已有挂载）
            setSelectedReferenceIds((_workspace.value.selectedReferenceIds + importedIds).distinct())
            com.kirakira.kiracode.util.AppLogger.d(
                "WorkspaceVM",
                "已自动挂载 ${importedIds.size} 张 PNG 角色卡: ${cards.map { it.name }}"
            )
        }.onFailure { e ->
            com.kirakira.kiracode.util.AppLogger.e("WorkspaceVM", "导入 PNG 角色卡失败: ${e.message}")
        }
    }

    /** V3.1 任务6：移除已选参考文件 */
    fun removeReferenceFile(file: ReferenceFile) {
        _referenceFiles.value = _referenceFiles.value.filterNot { it.uri == file.uri }
    }

    // ============ 模型信息 ============

    fun refreshModelInfo() {
        viewModelScope.launch {
            val config = runCatching { chatEngine.resolveActiveConfig() }.getOrNull()
            _workspace.update { ws ->
                ws.copy(
                    activeModelName = config?.model ?: "",
                    activeModelMaxTokens = config?.model?.let { maxTokensFor(it) } ?: 32768,
                    hasConfiguredApi = config != null
                )
            }
            // 可选模型列表：激活配置组已拉取的 models 优先，回退当前单个模型
            val configs = preferencesRepository.modelConfigs.first()
            val activeId = preferencesRepository.activeModelConfigId.first()
            val active = configs.firstOrNull { it.id == activeId } ?: configs.firstOrNull()
            val models = buildList {
                active?.models?.forEach { add(it) }
                config?.model?.takeIf { it.isNotBlank() }?.let { add(it) }
                preferencesRepository.selectedModel.first()?.takeIf { it.isNotBlank() }?.let { add(it) }
            }.distinct().sorted()
            _availableModels.value = models
        }
    }

    /**
     * 聊天页快捷切换模型（实时生效，无需重启）。
     * 写入激活配置组 selectedModel + 旧版偏好键；ChatEngine 每次发送都会重新解析配置。
     */
    fun setChatModel(model: String) {
        if (model.isBlank()) return
        viewModelScope.launch {
            runCatching {
                val configs = preferencesRepository.modelConfigs.first()
                val activeId = preferencesRepository.activeModelConfigId.first()
                val idx = configs.indexOfFirst { it.id == activeId }
                if (idx >= 0) {
                    val list = configs.toMutableList()
                    list[idx] = list[idx].copy(selectedModel = model)
                    preferencesRepository.setModelConfigs(list)
                }
                preferencesRepository.setSelectedModel(model)
            }
            _workspace.update { ws ->
                ws.copy(
                    activeModelName = model,
                    activeModelMaxTokens = maxTokensFor(model),
                    toast = "模型已切换：$model"
                )
            }
        }
    }

    private fun maxTokensFor(model: String): Int = when {
        model.contains("gpt-4o", true) || model.contains("gpt-4.1", true) -> 128000
        model.contains("gpt-4", true) -> 8192
        model.contains("o1", true) || model.contains("o3", true) || model.contains("o4", true) -> 128000
        model.contains("deepseek", true) -> 64000
        model.contains("moonshot", true) || model.contains("kimi", true) -> 128000
        model.contains("glm-4", true) -> 128000
        model.contains("claude", true) -> 200000
        model.contains("qwen", true) -> 32000
        else -> 32768
    }

    // ============ 会话管理 ============

    fun loadSessions() {
        viewModelScope.launch {
            _sessionListState.update { it.copy(isLoading = true) }
            val result = runCatching {
                sessionRepository.getActiveSessions(limit = 100, offset = 0).getOrDefault(emptyList())
            }
            val sessions = result.getOrElse {
                _sessionListState.update { s -> s.copy(isLoading = false, error = it.message) }
                return@launch
            }
            _sessionListState.value = SessionListUiState(
                sessions = mapSessionsToUiItems(sessions),
                isLoading = false
            )
        }
    }

    /** Session 列表 → 左侧/工作记录 UI 项（空会话隐藏，置顶优先，最近优先，最多 100 条） */
    private fun mapSessionsToUiItems(sessions: List<Session>): List<ChatSession> {
        return sessions
            .asSequence()
            .filter { it.messageCount > 0 }
            .sortedWith(
                compareByDescending<Session> { it.isPinned }.thenByDescending { it.updatedAt }
            )
            .take(100)
            .map { s ->
                ChatSession(
                    id = s.id,
                    uuid = s.uuid,
                    title = s.getDisplayTitle(),
                    lastMessageTime = s.updatedAt,
                    messageCount = s.messageCount,
                    isArchived = s.isArchived,
                    isPinned = s.isPinned
                )
            }
            .toList()
    }

    suspend fun createNewSession(title: String = "新会话", keepWorkingCard: Boolean = false): Result<Long> {
        val mode = _workspace.value.workMode
        val result = sessionRepository.createSession(
            Session(
                title = title,
                workMode = mode.name,
                selectedPreferenceIdsJson = encodeIds(_sessionPreferences.value.selectedOptionIds)
            )
        )
        result.onSuccess { id ->
            _workspace.update {
                it.copy(
                    // keepWorkingCard=true（改卡模式）：保留用户选定的目标卡，不清空
                    workingCard = if (keepWorkingCard) it.workingCard else null,
                    selectedReferenceIds = emptyList(),
                    refSelections = emptyList(),
                    tasks = emptyList(),
                    planBook = com.kirakira.kiracode.domain.agent.PlanBook(),
                    agentCards = emptyList(),
                    pendingInteraction = null
                )
            }
            openSessionInternal(id)
        }
        return result
    }

    fun openSession(sessionId: Long) {
        viewModelScope.launch { openSessionInternal(sessionId) }
    }

    /**
     * V2 Beta：冷启动恢复上一次会话。
     * @return true=已恢复（上层无需再建新会话）
     */
    suspend fun restoreLastSession(): Boolean {
        if (currentSessionId != null) return true
        val lastId = runCatching { preferencesRepository.lastSessionId.first() }.getOrNull()
            ?: return false
        val session = sessionRepository.getSession(lastId).getOrNull() ?: run {
            runCatching { preferencesRepository.setLastSessionId(null) }
            return false
        }
        openSessionInternal(lastId)
        // 恢复会话同时还原其工作模式（会话级持久化；V3.1 旧 AUTO/FULL_AUTO/MAX 迁移为 YOLO）
        session.workMode.takeIf { it.isNotBlank() }?.let { n ->
            WorkMode.fromLegacyName(n)?.let { m ->
                _workspace.update { it.copy(workMode = m) }
            }
        }
        return true
    }

    private suspend fun openSessionInternal(sessionId: Long) {
        // 仅在切换到不同会话时取消旧 Agent job 并保存切出快照；
        // 同会话重入/新会话创建链（createNewSession → openSessionInternal 在 agentJob 内运行）不取消，
        // 否则会自取消当前发送协程，打断整条发送路径。
        if (currentSessionId != null && currentSessionId != sessionId) {
            agentJob?.cancel()
            currentSessionId?.takeIf { it > 0 }?.let { saveEventSnapshot(it) }
            lastPromptTokens = 0  // 防止缓存命中率跨会话污染
        }
        _uiState.value = ChatUiState.Loading(sessionId)
        currentSessionId = sessionId
        // V2 Beta：记录最近活跃会话，冷启动自动恢复
        runCatching { preferencesRepository.setLastSessionId(sessionId) }
        try {
            val session = sessionRepository.getSession(sessionId).getOrNull() ?: run {
                _uiState.value = ChatUiState.Error("会话不存在", sessionId)
                return
            }
            val messages = messageRepository.getMessagesBySessionId(sessionId, limit = 500, offset = 0)
                .getOrDefault(emptyList())
                .filter { it.role == "user" || it.role == "assistant" }
                .map { ChatMessage.fromMessage(it).let { m -> m.copy(content = CardOpsParser.stripUpdateBlocks(m.content)) } }

            val card = decodeCard(session.workingCardJson)
            // 恢复挂载记录并校验：uuid 已失配的旧条目清理（幽灵 chip / 注入不生效）
            val restoredSelections = com.kirakira.kiracode.domain.model.RefSelection.decodeList(session.selectedReferenceIdsJson)
            val validSelections = restoredSelections.filter { sel ->
                _referenceCards.value.any { it.uuid == sel.uuid } ||
                    runCatching { referenceCardRepository.getReferenceCardByUuid(sel.uuid).getOrNull() }.getOrNull() != null
            }
            val refIds = validSelections.map { it.uuid }
            // 恢复事件快照（定稿事件卡/计划书/任务板；无快照则空）
            val snapshot = WorkspaceEventSnapshot.decode(session.eventSnapshotJson)
            // 恢复会话级创作偏好选择
            _sessionPreferences.value = SessionPreferences(decodeIds(session.selectedPreferenceIdsJson))

            _workspace.update { ws ->
                ws.copy(
                    workingCard = card,
                    workMode = parseMode(session.workMode, fallback = ws.workMode),
                    selectedReferenceIds = refIds,
                    refSelections = validSelections,
                    tasks = snapshot.tasks,
                    planBook = snapshot.planBook,
                    agentCards = snapshot.cards,
                    pendingInteraction = null,
                    // V3.1 批次6任务：恢复 Token 统计（snapshot 有历史数据则用历史，否则保持默认 0）
                    contextInfo = ws.contextInfo.copy(
                        promptTokensTotal = snapshot.promptTokensTotal,
                        completionTokensTotal = snapshot.completionTokensTotal,
                        requestCount = snapshot.requestCount,
                        cacheHitRate = snapshot.cacheHitRate,
                        processedTokens = snapshot.processedTokens
                    )
                )
            }
            if (validSelections.size < restoredSelections.size) {
                // 有失配项被清理：把清理后的状态写回会话
                persistWorkspace()
            }
            _uiState.value = ChatUiState.Ready(sessionId = sessionId, messages = messages)
            recomputeContext(messages.map { it.content })
        } catch (e: Exception) {
            _uiState.value = ChatUiState.Error(e.message ?: "加载会话失败", sessionId)
        }
    }

    /** 保存会话事件快照（定稿事件/计划/任务/Token 统计；失败静默——快照是增强能力） */
    private fun saveEventSnapshot(sessionId: Long) {
        if (sessionId <= 0L) return
        val ws = _workspace.value
        val info = ws.contextInfo
        val snapshot = WorkspaceEventSnapshot.of(ws.agentCards, ws.planBook, ws.tasks).copy(
            // V3.1 批次6任务：Token 统计随快照持久化（会话切换/重启后恢复累计值）
            promptTokensTotal = info.promptTokensTotal,
            completionTokensTotal = info.completionTokensTotal,
            requestCount = info.requestCount,
            cacheHitRate = info.cacheHitRate,
            processedTokens = info.processedTokens
        )
        viewModelScope.launch {
            runCatching {
                sessionRepository.updateEventSnapshot(sessionId, WorkspaceEventSnapshot.encode(snapshot))
            }
        }
    }

    suspend fun deleteSession(sessionId: Long) {
        sessionRepository.deleteSession(sessionId)
        if (currentSessionId == sessionId) {
            currentSessionId = null
            _sessionPreferences.value = SessionPreferences.Empty
            _uiState.value = ChatUiState.Empty()
        }
        loadSessions()
    }

    suspend fun renameSession(sessionId: Long, newTitle: String) {
        sessionRepository.updateTitle(sessionId, newTitle)
        loadSessions()
    }

    /** V3.0 会话置顶/取消置顶 */
    fun pinSession(sessionId: Long, pinned: Boolean) {
        viewModelScope.launch {
            sessionRepository.setPinned(sessionId, pinned)
            loadSessions()
            _workspace.update { it.copy(toast = if (pinned) "已置顶" else "已取消置顶") }
        }
    }

    // ============ 工作区 ============

    fun setWorkMode(mode: WorkMode) {
        _workspace.update { it.copy(workMode = mode, toast = "已切换：${mode.displayName}") }
        if (mode == WorkMode.CHAT) {
                    }
        persistWorkspace()
    }

    fun clearToast() {
        _workspace.update { it.copy(toast = null) }
    }

    fun setWorkingCard(card: CharacterCard?) {
        _workspace.update { it.copy(workingCard = card) }
        persistWorkspace()
    }

    /** 相册选图 → 缩放/压缩 → data URI → 更新工作卡并持久化（防撑爆 workingCardJson） */
    fun updateWorkingCardAvatar(uri: Uri) {
        if (_workspace.value.workingCard == null) {
            _workspace.update { it.copy(toast = "没有工作卡，无法设置封面") }
            return
        }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { compressCoverToDataUri(uri) }
            }
            result.onSuccess { dataUri ->
                val current = _workspace.value.workingCard ?: return@onSuccess
                setWorkingCard(current.copy(avatar = dataUri))
                _workspace.update { it.copy(toast = "封面已更新") }
            }.onFailure { e ->
                android.util.Log.e("WorkspaceViewModel", "上传封面失败 uri=$uri", e)
                _workspace.update {
                    it.copy(toast = "封面设置失败：${e.message ?: "无法读取图片"}")
                }
            }
        }
    }

    fun clearWorkingCardAvatar() {
        val current = _workspace.value.workingCard
        if (current == null) {
            _workspace.update { it.copy(toast = "没有工作卡，无法清除封面") }
            return
        }
        setWorkingCard(current.copy(avatar = null))
        _workspace.update { it.copy(toast = "封面已清除") }
    }

    private fun compressCoverToDataUri(uri: Uri): String {
        val scheme = uri.scheme
        if (scheme != "content" && scheme != "file") {
            error("不支持的图片来源格式")
        }

        // 只开一次流：部分 ContentProvider 不支持重复打开同一 URI
        val rawBytes = appContext.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("无法读取所选图片（图片可能已被删除或无访问权限）")
        if (rawBytes.isEmpty()) error("图片文件为空")
        if (rawBytes.size > MAX_RAW_COVER_BYTES) {
            error("图片过大（超过 15MB），请选择较小的图片")
        }

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, bounds)
        val width = bounds.outWidth
        val height = bounds.outHeight
        if (width <= 0 || height <= 0) error("图片格式无效或已损坏")

        val maxEdge = 960
        var sample = 1
        while (maxOf(width / sample, height / sample) > maxEdge * 2) sample *= 2

        val decoded = BitmapFactory.decodeByteArray(
            rawBytes,
            0,
            rawBytes.size,
            BitmapFactory.Options().apply { inSampleSize = sample }
        ) ?: error("解码图片失败")

        val scaled = if (maxOf(decoded.width, decoded.height) > maxEdge) {
            val scale = maxEdge.toFloat() / maxOf(decoded.width, decoded.height)
            val w = (decoded.width * scale).toInt().coerceAtLeast(1)
            val h = (decoded.height * scale).toInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(decoded, w, h, true).also {
                if (it !== decoded) decoded.recycle()
            }
        } else {
            decoded
        }

        val flat = if (scaled.hasAlpha()) {
            Bitmap.createBitmap(scaled.width, scaled.height, Bitmap.Config.ARGB_8888).also { b ->
                Canvas(b).apply {
                    drawColor(Color.WHITE)
                    drawBitmap(scaled, 0f, 0f, null)
                }
            }.also { scaled.recycle() }
        } else {
            scaled
        }

        try {
            var quality = 85
            var bytes = ByteArray(0)
            while (true) {
                ByteArrayOutputStream().use { out ->
                    val success = flat.compress(Bitmap.CompressFormat.JPEG, quality, out)
                    if (!success) error("压缩图片失败")
                    bytes = out.toByteArray()
                }
                if (bytes.size <= MAX_COVER_BYTES || quality <= 45) break
                quality -= 10
            }
            if (bytes.size > MAX_COVER_BYTES) {
                val smaller = Bitmap.createScaledBitmap(
                    flat,
                    (flat.width * 0.75f).toInt().coerceAtLeast(1),
                    (flat.height * 0.75f).toInt().coerceAtLeast(1),
                    true
                )
                ByteArrayOutputStream().use { out ->
                    if (!smaller.compress(Bitmap.CompressFormat.JPEG, 70, out)) {
                        smaller.recycle()
                        error("压缩图片失败")
                    }
                    bytes = out.toByteArray()
                }
                smaller.recycle()
            }
            require(bytes.size <= MAX_COVER_BYTES) { "图片压缩后仍超过 400KB" }
            return "data:image/jpeg;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
        } finally {
            flat.recycle()
        }
    }

    fun setSelectedReferenceIds(ids: List<String>) {
        // 分级设定粒度：新给的 id 默认整卡参考
        _workspace.update { ws ->
            val kept = ws.refSelections.filter { it.uuid in ids }
            val newOnes = ids.filter { id -> kept.none { it.uuid == id } }
                .map { com.kirakira.kiracode.domain.model.RefSelection.whole(it) }
            ws.copy(
                selectedReferenceIds = ids,
                refSelections = (kept + newOnes).sortedBy { s -> ids.indexOf(s.uuid) }
            )
        }
        persistWorkspace()
        // 诊断：挂载即记录 uuid→名称匹配（判定挂载链是否断裂）
        val mountedNames = ids.mapNotNull { id ->
            _referenceCards.value.find { it.uuid == id }?.name
        }
        val mountLog = "参考卡挂载: ids=${ids.size} 个，内存匹配到名称=$mountedNames"
        android.util.Log.d("WorkspaceVM", mountLog)
        com.kirakira.kiracode.util.AppLogger.d("WorkspaceVM", mountLog)
    }

    /** V3.0 更新单个参考卡的粒度/条目选择 */
    fun updateRefSelection(selection: com.kirakira.kiracode.domain.model.RefSelection) {
        _workspace.update { ws ->
            ws.copy(refSelections = ws.refSelections.map {
                if (it.uuid == selection.uuid) selection else it
            })
        }
        persistWorkspace()
    }

    /** V3.0 存入成品区（按 uuid upsert） */
    fun saveWorkingCardToFinished() {
        val card = _workspace.value.workingCard ?: run {
            _workspace.update { it.copy(toast = "工作区没有角色卡") }
            return
        }
        viewModelScope.launch {
            characterCardRepository.saveHistory(card)
                .onSuccess {
                    _workspace.update { it.copy(toast = "已存入成品区：${card.name}") }
                }
                .onFailure { e ->
                    _workspace.update { it.copy(toast = "存入失败：${e.message}") }
                }
        }
    }

    /**
     * V2 Beta：工作区快捷导出（不经成品区，直落 Download 目录）。
     * V2.1：fileName 可选（ExportDialog 输入）；exporting 标志驱动对话框进度条。
     * V3.0：targetCard 可选（左侧栏任意卡直接导出；缺省导出当前工作卡）。
     */
    fun exportWorkingCard(
        format: com.kirakira.kiracode.domain.model.ExportFormat,
        fileName: String? = null,
        targetCard: CharacterCard? = null
    ) {
        val card = targetCard ?: _workspace.value.workingCard ?: run {
            _workspace.update { it.copy(toast = "工作区没有角色卡，无法导出") }
            return
        }
        if (_workspace.value.exporting) return
        _workspace.update { it.copy(exporting = true) }
        viewModelScope.launch {
            try {
                val baseName = fileName?.takeIf { it.isNotBlank() }
                    ?: com.kirakira.kiracode.data.local.export.DownloadExporter
                        .sanitizeFileName("${card.name.ifBlank { "card" }}_${System.currentTimeMillis()}")
                val finalName = com.kirakira.kiracode.data.local.export.DownloadExporter
                    .sanitizeFileName(baseName)
                val bytes: ByteArray = when (format) {
                    com.kirakira.kiracode.domain.model.ExportFormat.PNG ->
                        exportCharacterCardUseCase.renderToPngBytes(card)
                    com.kirakira.kiracode.domain.model.ExportFormat.JSON ->
                        com.kirakira.kiracode.domain.model.st.StCardCodec.toJsonString(card, pretty = true)
                            .toByteArray(Charsets.UTF_8)
                }
                val mime = if (format == com.kirakira.kiracode.domain.model.ExportFormat.PNG) "image/png" else "application/json"
                val saved = com.kirakira.kiracode.data.local.export.DownloadExporter
                    .save(appContext, "$finalName.${if (format == com.kirakira.kiracode.domain.model.ExportFormat.PNG) "png" else "json"}", mime, bytes)
                saved.onSuccess { path ->
                    _workspace.update { it.copy(toast = "已导出到 Download：$path") }
                }.onFailure { e ->
                    _workspace.update { it.copy(toast = "导出失败：${e.message}") }
                }
            } catch (e: Exception) {
                _workspace.update { it.copy(toast = "导出失败：${e.message}") }
            } finally {
                _workspace.update { it.copy(exporting = false) }
            }
        }
    }

    /** V3.0 删除卡片项目（左侧栏长按菜单；按 uuid 定位 history 记录） */
    fun deleteCard(card: CharacterCard) {
        viewModelScope.launch {
            val id = runCatching { characterCardRepository.getHistoryIdByUuid(card.uuid).getOrNull() }.getOrNull()
            if (id != null) {
                characterCardRepository.deleteFinishedCard(id)
                _workspace.update { it.copy(toast = "已删除：${card.name.ifBlank { "未命名角色" }}") }
            } else {
                _workspace.update { it.copy(toast = "卡片不存在或已删除") }
            }
            refreshCards()
        }
    }

    private fun persistWorkspace() {
        val sessionId = currentSessionId ?: return
        val ws = _workspace.value
        viewModelScope.launch {
            runCatching {
                sessionRepository.updateWorkspace(
                    id = sessionId,
                    workingCardJson = ws.workingCard?.let { StCardCodec.toJsonString(it, pretty = false) } ?: "",
                    workMode = ws.workMode.name,
                    referenceIdsJson = com.kirakira.kiracode.domain.model.RefSelection.encodeList(ws.refSelections)
                )
            }
        }
    }

    private fun decodeCard(json: String?): CharacterCard? {
        if (json.isNullOrBlank()) return null
        return runCatching { StCardCodec.decode(json).getOrNull() }.getOrNull()
    }

    private fun decodeIds(json: String?): List<String> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            kotlinx.serialization.json.Json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(kotlinx.serialization.serializer<String>()), json)
        }.getOrDefault(emptyList())
    }

    private fun encodeIds(ids: List<String>): String = runCatching {
        kotlinx.serialization.json.Json.encodeToString(kotlinx.serialization.builtins.ListSerializer(kotlinx.serialization.serializer<String>()), ids)
    }.getOrDefault("[]")

    private fun parseMode(name: String?, fallback: WorkMode): WorkMode =
        WorkMode.fromLegacyName(name) ?: fallback

    // ============ 发送与 Agent 执行 ============

    /** 当前 Agent 任务协程（新任务开始前取消旧任务：放弃未完成的执行与挂起的交互等待） */
    private var agentJob: kotlinx.coroutines.Job? = null

    fun sendMessage(content: String) {
        if (content.isBlank()) return
        agentJob?.cancel()
        viewModelScope.launch {
            val sessionId = ensureSessionReady() ?: return@launch
            dispatchSend(sessionId, content)
        }.also { agentJob = it }
    }

    /** 执行中停止：取消当前 Agent 任务协程（占位清理与状态复位由 dispatchSend 取消分支完成） */
    fun stopGeneration() {
        agentJob?.cancel()
    }

    /** V3.1 批次5任务8：顶栏 + 新建会话（清空工作卡与事件流，全新开始） */
    fun startNewSession() {
        agentJob?.cancel()
        _sessionPreferences.value = SessionPreferences.Empty
        viewModelScope.launch {
            val r = createNewSession(title = "新会话", keepWorkingCard = false)
            r.onSuccess { _workspace.update { it.copy(toast = "已新建会话") } }
            r.onFailure { e -> _workspace.update { it.copy(toast = "新建会话失败：${e.message}") } }
        }
    }

    /** 确保有可用会话；无则自动创建。返回 sessionId 或 null（失败） */
    private suspend fun ensureSessionReady(): Long? {
        currentSessionId?.takeIf { it > 0 }?.let { return it }
        val created = createNewSession(title = defaultTitle())
        return created.getOrNull()
    }

    private fun defaultTitle(): String {
        val ws = _workspace.value
        return ws.workingCard?.name?.takeIf { it.isNotBlank() }?.let { "$it · 创作" } ?: "新会话"
    }

    private suspend fun dispatchSend(sessionId: Long, content: String) {
        val ws = _workspace.value

        // 本地追加用户消息 + 流式占位
        val userMsg = ChatMessage.createUserMessage(sessionId, content)
        val firstPlaceholder = ChatMessage.createAssistantMessage(sessionId, "", isStreaming = true)
        _uiState.update { state ->
            if (state is ChatUiState.Ready) state.copy(messages = state.messages + userMsg + firstPlaceholder, isSending = true, error = null)
            else state
        }
        // V2/V3：分轮收束——工具轮定稿后开新占位，实现"AI文本→工具→成功→AI文本→…"交错流
        // 写入成员变量（与 ensurePlaceholder/appendUserBubble 同一来源；勿用局部遮蔽）
        currentPlaceholderId = firstPlaceholder.id
        // V2.1：每轮任务开始清空实时思维链
        _liveReasoning.value = null

        val referenceCards = ws.refSelections.mapNotNull { sel ->
            _referenceCards.value.firstOrNull { it.uuid == sel.uuid }
        }.also { found ->
            val lookupLog = "参考卡查找: selections=${ws.refSelections.size}，从内存找到=${found.size}"
            android.util.Log.d("WorkspaceVM", lookupLog)
            com.kirakira.kiracode.util.AppLogger.d("WorkspaceVM", lookupLog)
        }.ifEmpty {
            ws.selectedReferenceIds.mapNotNull { uid ->
                _referenceCards.value.firstOrNull { it.uuid == uid }
                    ?: runCatching { referenceCardRepository.getReferenceCardByUuid(uid).getOrNull() }.getOrNull()
            }.also { dbFound ->
                val dbLog = "参考卡查找: 内存失配，从 DB 找到=${dbFound.size}"
                android.util.Log.d("WorkspaceVM", dbLog)
                com.kirakira.kiracode.util.AppLogger.d("WorkspaceVM", dbLog)
            }
        }.also { final ->
            val finalLog = "最终参考卡: ${final.size} 张，names=${final.map { it.name }}"
            android.util.Log.d("WorkspaceVM", finalLog)
            com.kirakira.kiracode.util.AppLogger.d("WorkspaceVM", finalLog)
        }
        val personaPrompt = _personas.value.firstOrNull { it.id == _activePersonaId.value }?.prompt

    var fullRaw = ""
    var thinkingRaw = ""
    var failedMsg: String? = null
    // 对齐 Aurora：每个 delta 立即提交 UI，不做 32ms 帧批
    // 收尾文本由 AssistantText/ThinkAction 分支无条件刷出
    // V3.1 任务5：流式 usage 尾帧累计（精确 token；多轮工具调用求和）
    var exactPromptTokens = 0
    var exactCompletionTokens = 0
    var exactCachedTokens = 0
    // 诊断：VM 提交节奏统计（仅 DEBUG 聚合）
    val diagEnabled = true
    val vmTrace = mutableMapOf<String, Any>()
    var vmDeltaReceived = 0
    var vmCommitCount = 0
    var vmFirstDeltaNanos = 0L
    var vmFirstCommitNanos = 0L
    var vmLastCommitNanos = 0L
    var vmMaxCommitGapNanos = 0L
    var vmLastCommitAtNanos = 0L
    var vmSkippedDueToBatch = 0
    var vmTraceStartNanos = 0L
    // V3.1 批次4任务3：执行开始
    _isExecuting.value = true
    try {
            // 稳定条目 ID 修复（大卡按需读取前置）：导入卡可能整批 uid=0/重复，
            // 修复后的卡同时作为注入源与工具操作目标，保证索引 ID 与工具 ID 一致
            val workingCardForRun = ws.workingCard?.let { base ->
                val repaired = com.kirakira.kiracode.domain.agent.CardIndex.ensureStableEntryIds(base)
                if (repaired != base) {
                    _workspace.update { it.copy(workingCard = repaired) }
                    persistWorkspace()
                    com.kirakira.kiracode.util.AppLogger.d("VM", "已修复角色卡条目稳定 ID（uid/正则 id）")
                }
                repaired
            }
            // 提示词组装 + 用户消息持久化 + 生效配置（ChatEngine 准备层）
            val (apiMessages, effectiveConfig) = chatEngine.prepareChatMessages(
                sessionId = sessionId,
                userContent = content,
                workingCard = workingCardForRun,
                referenceCards = referenceCards,
                mode = ws.workMode,
                refSpecs = ws.refSelections,
                agentTasks = _workspace.value.tasks,
                planBook = _workspace.value.planBook,
                temperatureOverride = _workspace.value.temperature,
                topPOverride = _workspace.value.topP,
                cardMasterEnabled = ws.cardMasterEnabled,
                webSearchEnabled = ws.webSearchEnabled,
                personaPrompt = personaPrompt,
                reasoningEffort = _workspace.value.reasoningEffort,
                // V3.1 任务6：+号附件文本注入 system（图片无文本内容，注入前被过滤并留痕）
                referenceFiles = run {
                    val all = _referenceFiles.value
                    val injected = all.filter { it.content.isNotBlank() }.map { it.name to it.content }
                    com.kirakira.kiracode.util.AppLogger.d(
                        "VM",
                        "ATTACHMENTS total=${all.size} injectedText=${injected.size} " +
                            "skippedNonText=${all.count { it.content.isBlank() }} names=[${all.joinToString(",") { f -> if (f.content.isBlank()) "${f.name}(未注入)" else f.name }}]"
                    )
                    injected
                },
                preferenceInjection = getPreferenceInjection(),
                regexGenerationMode = ws.regexGenerationMode
            )
            val coreMessages = apiMessages.map { it.toCoreMessage() }
            // CHAT 模式无工具（纯聊天）；其余模式带 function calling 工具
            // 联网搜索开关：关闭时 web_search 不注册（模型看不到该工具）
            // YOLO 模式不注册交互类工具（ask_user/submit_plan/complete_step）：不挂起等待用户，
            // 缺失信息由模型按合理默认值补全并标注 ai_guess（与系统提示词 YOLO 层语义一致）
            val useTools = ws.workMode != WorkMode.CHAT
            val tools = if (useTools) {
                toolRegistry.getTools(
                    webSearchEnabled = ws.webSearchEnabled,
                    includeInteractiveTools = ws.workMode != WorkMode.YOLO
                )
            } else null
            if (useTools) {
                // 同步工作卡给内核（工具应用的目标；无卡则用占位卡；与注入同源——已修复稳定 ID）
                toolRegistry.setCurrentCard(workingCardForRun ?: CharacterCard(name = "未命名角色"))
                // P0 接线：参考卡传入内核，供 spawn_subagent 同源注入风格样本
                agentController.setReferenceCard(referenceCards.firstOrNull())
            }
            val estimatedPromptTokens = apiMessages.sumOf {
                com.kirakira.kiracode.domain.usecase.TokenEstimator.estimateTokens(it.content.orEmpty())
            }

            // Agent 内核循环：tool_calls → 执行 → 回注 → 继续；无 tool_calls → 本轮即完成
            agentController.startWithMessages(
                messages = coreMessages,
                tools = tools,
                // V3.1 强制流式（开关已移除；非流式仅作为内核流式失败的兜底）
                streaming = true,
                config = effectiveConfig,
                mode = ws.workMode,
                unlimitedToolCalls = ws.unlimitedToolCalls,
                regexGenerationMode = ws.regexGenerationMode
            ).also { if (diagEnabled) vmTraceStartNanos = SystemClock.elapsedRealtimeNanos() }
            .collect { event ->
                // V3.1 批次4任务3：收到内核事件 = 仍在执行（等待用户的挂起由 pendingInteraction 收集器置 false）
                _isExecuting.value = true
                when (event) {
                    is com.kirakira.kiracode.domain.agent.core.StreamDeltaEvent -> {
                        fullRaw += event.text
                        // 只记录前几次（避免刃爆 500 条日志缓冲）
                        if (fullRaw.length <= 50) {
                            com.kirakira.kiracode.util.AppLogger.d("VM", "StreamDelta: ${event.text.take(20)}, fullRaw长度=${fullRaw.length}")
                        }
                        // 诊断：记录 delta 接收
                        if (diagEnabled) {
                            vmDeltaReceived++
                            if (vmFirstDeltaNanos == 0L) vmFirstDeltaNanos = SystemClock.elapsedRealtimeNanos()
                        }
                        ensurePlaceholder(sessionId)
                        updatePlaceholder(sessionId, currentPlaceholderId) { it.copyWithContent(fullRaw, isStreaming = true) }
                        // reasoning 尾批顺带刷出：reasoning 爆发后不再有 reasoning delta 时，
                        // 滞留在 thinkingRaw 的尾部随首个 content 刷新一并提交
                        // （StateFlow 同值去重，reasoning 稳定期重复写零开销；无 reasoning 时不写，保持 null 语义）
                        if (thinkingRaw.isNotEmpty()) _liveReasoning.value = thinkingRaw
                        // 流式期间禁发（防止并发二次任务）；交互等待期 isSending=false 恢复可发
                        _uiState.update { s -> if (s is ChatUiState.Ready && !s.isSending) s.copy(isSending = true) else s }
                        // 诊断：记录提交
                        if (diagEnabled) {
                            vmCommitCount++
                            val nowNanos = SystemClock.elapsedRealtimeNanos()
                            if (vmFirstCommitNanos == 0L) vmFirstCommitNanos = nowNanos
                            if (vmLastCommitAtNanos != 0L) {
                                val gap = nowNanos - vmLastCommitAtNanos
                                if (gap > vmMaxCommitGapNanos) vmMaxCommitGapNanos = gap
                            }
                            vmLastCommitAtNanos = nowNanos
                        }
                    }
                    is com.kirakira.kiracode.domain.agent.core.ReasoningDeltaEvent -> {
                        thinkingRaw += event.text
                        if (thinkingRaw.length <= 100) {
                            com.kirakira.kiracode.util.AppLogger.d("VM", "ReasoningDelta: ${event.text.take(20)}, thinkingRaw长度=${thinkingRaw.length}")
                        }
                        // 诊断：记录 reasoning delta 接收
                        if (diagEnabled) {
                            vmDeltaReceived++
                            if (vmFirstDeltaNanos == 0L) vmFirstDeltaNanos = SystemClock.elapsedRealtimeNanos()
                        }
                        // 纯思考轮（无正文 delta）也必须恢复 isSending，否则下一轮 reasoning 流式期间
                        // UI 因 isSending=false 完全静默（liveReasoning/caret 均被门控隐藏）
                        _uiState.update { s -> if (s is ChatUiState.Ready && !s.isSending) s.copy(isSending = true) else s }
                        _liveReasoning.value = thinkingRaw
                    }
                    is com.kirakira.kiracode.domain.agent.core.ReasoningCompleteEvent -> {
                        // 本轮思维链定稿（内核发送本轮完整思维文本）
                        thinkingRaw = event.text
                        _liveReasoning.value = event.text
                    }
                    is com.kirakira.kiracode.domain.agent.core.UsageEvent -> {
                        // V3.1 任务5：精确 token 累计（替代 ThinkAction 的估算值）
                        exactPromptTokens += event.promptTokens
                        exactCompletionTokens += event.completionTokens
                        exactCachedTokens += event.cachedTokens
                    }
                    is com.kirakira.kiracode.domain.agent.core.AssistantTextEvent -> {
                        // 工具轮正文定稿：收束占位（携带本轮思维）+ 持久化 + 开新占位
                        sealPlaceholder(sessionId, currentPlaceholderId, event.text, thinkingRaw)
                        thinkingRaw = ""
                        _liveReasoning.value = null
                        fullRaw = ""
                        val np = ChatMessage.createAssistantMessage(sessionId, "", isStreaming = true)
                        _uiState.update { s -> if (s is ChatUiState.Ready) s.copy(messages = s.messages + np) else s }
                        currentPlaceholderId = np.id
                    }
                    is com.kirakira.kiracode.domain.agent.core.ThinkAction -> {
                        // 本轮响应完成：占位收束 + 协议解析（finishAssistant）+ 模式分派
                        ensurePlaceholder(sessionId)
                        updatePlaceholder(sessionId, currentPlaceholderId) {
                            it.copyWithContent(fullRaw, isStreaming = false, thinking = thinkingRaw)
                        }
                        _liveReasoning.value = null
                        _uiState.update { s -> if (s is ChatUiState.Ready) s.copy(isSending = false) else s }
                        finishAssistant(
                            sessionId,
                            currentPlaceholderId,
                            event.thought,
                            event.promptTokens ?: exactPromptTokens.takeIf { it > 0 } ?: estimatedPromptTokens,
                            event.completionTokens ?: exactCompletionTokens.takeIf { it > 0 } ?: 0,
                            cachedTokens = event.cachedTokens ?: exactCachedTokens,
                            hasProtocol = useTools
                        )
                        // 本轮统计已落账，重置局部累计，防止下一个 ThinkAction 重复累加
                        exactPromptTokens = 0
                        exactCompletionTokens = 0
                        exactCachedTokens = 0
                        // 本轮已定稿：清空累积缓冲，避免下一轮新建占位时带入上一轮正文
                        fullRaw = ""
                        thinkingRaw = ""
                    }
                    is com.kirakira.kiracode.domain.agent.core.UpdateCardAction -> {
                        // 工具调用卡片（工具；脑补字段带可点击标注）+ 活动流
                        val cards = event.operations.map { op ->
                            AgentCardUi.ToolCall(
                                id = java.util.UUID.randomUUID().toString(),
                                desc = op.describe,
                                functionName = event.functionName,
                                isAiGuess = op.aiGuessFlag,
                                fieldKey = (op as? CardOperation.SetField)?.fieldName.orEmpty(),
                                fieldLabel = when (op) {
                                    is CardOperation.SetField -> CardOperationApplier.fieldLabel(op.fieldName)
                                    is CardOperation.BookAdd -> "世界书「${op.comment.ifBlank { op.keys.joinToString("/") }}」"
                                    else -> ""
                                }
                            )
                        }
                        if (cards.isNotEmpty()) {
                            _workspace.update { it.copy(agentCards = it.agentCards + cards) }
                        }
                    }
                    is com.kirakira.kiracode.domain.agent.core.CardObservation -> {
                        // 审计报告优先渲染为专用 Audit 卡片；其它工具沿用 observation 卡片
                        val auditCard = event.metadata["audit_report"]?.let { decodeAuditCard(event, it) }
                        _workspace.update { ws2 ->
                            ws2.copy(
                                agentCards = if (auditCard != null) {
                                    ws2.agentCards + auditCard
                                } else {
                                    ws2.agentCards + AgentCardUi.Observation(
                                        id = event.id, content = event.content, isError = event.isError
                                    )
                                },
                                toast = if (auditCard != null) {
                                    "审计完成：${auditCard.cardName}"
                                } else {
                                    event.content.take(60)
                                }
                            )
                        }
                        // web_search 成功：追加可点击的搜索结果卡（浮窗预览标题/URL/摘要）
                        event.metadata["search_results"]?.let { resultsJson ->
                            val searchCard = decodeSearchCard(event, resultsJson)
                            if (searchCard != null) {
                                _workspace.update { ws2 ->
                                    ws2.copy(agentCards = ws2.agentCards + searchCard)
                                }
                            }
                        }
                        // fetch_webpage 成功：追加抓取卡片（URL + 字符数 + 正文预览）
                        event.metadata["fetched_url"]?.let { fetchedUrl ->
                            val fullContent = event.content.substringAfter("\n\n")
                            _workspace.update { ws2 ->
                                ws2.copy(
                                    agentCards = ws2.agentCards + AgentCardUi.WebpageFetch(
                                        id = java.util.UUID.randomUUID().toString(),
                                        url = fetchedUrl,
                                        contentLength = event.metadata["content_length"]?.toIntOrNull()
                                            ?: fullContent.length,
                                        preview = fullContent.take(200)
                                    )
                                )
                            }
                        }
                        if (!event.isError) {
                            // 工具结果回填工作区（内核已应用，读最新卡）+ 持久化
                            toolRegistry.getCurrentCard()?.let { updated ->
                                _workspace.update { it.copy(workingCard = updated) }
                                persistWorkspace()
                            }
                        }
                    }
                    is com.kirakira.kiracode.domain.agent.core.AskUserAction -> {
                        // 追问卡片：挂起等待用户回答（输入栏路由到 respondToAgent）
                        _workspace.update { ws2 ->
                            ws2.copy(
                                agentCards = ws2.agentCards + AgentCardUi.Question(
                                    id = event.id, question = event.question, options = event.options, about = event.about
                                ),
                                pendingInteraction = PendingInteraction.Question(
                                    cardId = event.id, question = event.question, options = event.options
                                )
                            )
                        }
                        _uiState.update { s -> if (s is ChatUiState.Ready) s.copy(isSending = false) else s }
                    }
                    is com.kirakira.kiracode.domain.agent.core.PlanAction -> {
                        // 计划卡片：替换旧计划卡（重复提交），挂起等待确认/修改
                        // V3.1 批次4任务2：同步写入 planBook（右侧任务进度面板数据源）
                        _workspace.update { ws2 ->
                            val parts = event.steps.mapIndexed { i, s ->
                                com.kirakira.kiracode.domain.agent.PlanPart(
                                    id = "step_${i + 1}",
                                    title = s.title,
                                    detail = s.detail,
                                    status = if (i < ws2.planBook.doneCount) com.kirakira.kiracode.domain.agent.PlanPart.STATUS_DONE
                                    else com.kirakira.kiracode.domain.agent.PlanPart.STATUS_PENDING
                                )
                            }
                            ws2.copy(
                                agentCards = ws2.agentCards.filterNot { it is AgentCardUi.Plan } + AgentCardUi.Plan(
                                    id = event.id, steps = event.steps.map { PlanStepUi(it.title, it.detail) }
                                ),
                                pendingInteraction = PendingInteraction.Plan(cardId = event.id),
                                planBook = ws2.planBook.copy(
                                    goal = ws2.planBook.goal.ifBlank { "按制卡计划执行" },
                                    parts = parts
                                )
                            )
                        }
                        _uiState.update { s -> if (s is ChatUiState.Ready) s.copy(isSending = false) else s }
                    }
                    is com.kirakira.kiracode.domain.agent.core.StepProgressEvent -> {
                        // 进度卡片：只保留最新一条（"已完成 N/M 步"）
                        // V3.1 批次4任务2：同步 planBook 状态（右侧任务进度实时跟随）
                        _workspace.update { ws2 ->
                            val parts = ws2.planBook.parts
                            val synced = parts.mapIndexed { i, p ->
                                val stepNo = i + 1
                                when {
                                    p.status == com.kirakira.kiracode.domain.agent.PlanPart.STATUS_DONE -> p
                                    event.stepIndex > 0 && stepNo == event.stepIndex ->
                                        p.copy(status = com.kirakira.kiracode.domain.agent.PlanPart.STATUS_DONE)
                                    event.stepIndex > 0 && stepNo == event.stepIndex + 1 ->
                                        p.copy(status = com.kirakira.kiracode.domain.agent.PlanPart.STATUS_RUNNING)
                                    event.stepIndex <= 0 && i < event.done ->
                                        p.copy(status = com.kirakira.kiracode.domain.agent.PlanPart.STATUS_DONE)
                                    event.stepIndex <= 0 && i == event.done ->
                                        p.copy(status = com.kirakira.kiracode.domain.agent.PlanPart.STATUS_RUNNING)
                                    else -> p
                                }
                            }
                            ws2.copy(
                                agentCards = ws2.agentCards.filterNot { it is AgentCardUi.Progress } + AgentCardUi.Progress(
                                    id = event.id, done = event.done, total = event.total,
                                    title = event.title, summary = event.summary
                                ),
                                planBook = if (parts.isEmpty()) ws2.planBook else ws2.planBook.copy(parts = synced),
                                toast = if (event.total > 0 && event.done >= event.total)
                                    "任务完成（${event.done}/${event.total} 步）" else ws2.toast
                            )
                        }
                    }
                    is com.kirakira.kiracode.domain.agent.core.DiffProposalAction -> {
                        // Diff 卡片：删除线旧版 + 绿色新版。
                        // V3.1 任务7：YOLO 模式内核不挂起 → 卡片直接标为已接受（对照仍展示）；
                        // AGENT 模式挂起等待接受/拒绝/继续改
                        val autoAccept = _workspace.value.workMode == WorkMode.YOLO
                        _workspace.update { ws2 ->
                            ws2.copy(
                                agentCards = ws2.agentCards + AgentCardUi.Diff(
                                    id = event.id, fieldKey = event.fieldKey, fieldLabel = event.fieldLabel,
                                    oldValue = event.oldValue, newValue = event.newValue,
                                    decision = if (autoAccept) AgentCardUi.Diff.ACCEPTED else null
                                ),
                                pendingInteraction = if (autoAccept) null else PendingInteraction.Diff(
                                    cardId = event.id, fieldKey = event.fieldKey, fieldLabel = event.fieldLabel
                                )
                            )
                        }
                        if (!autoAccept) {
                            _uiState.update { s -> if (s is ChatUiState.Ready) s.copy(isSending = false) else s }
                        }
                    }
                    is com.kirakira.kiracode.domain.agent.core.AutoDraftEvent -> {
                        _workspace.update { ws2 ->
                            ws2.copy(agentCards = ws2.agentCards + AgentCardUi.Info(id = event.id, text = event.message))
                        }
                    }
                    is com.kirakira.kiracode.domain.agent.core.SystemNoticeEvent -> {
                        // V3.1 强制流式：流式失败降级非流式时，持久事件卡 + 用户提示
                        _workspace.update { ws2 ->
                            ws2.copy(
                                agentCards = ws2.agentCards + AgentCardUi.Info(
                                    id = event.id,
                                    text = event.message,
                                    kind = AgentCardUi.Info.Kind.DEGRADED
                                ),
                                toast = event.message
                            )
                        }
                        com.kirakira.kiracode.util.AppLogger.d("VM", "收到 SystemNoticeEvent: ${event.message}")
                    }
                    is com.kirakira.kiracode.domain.agent.core.BudgetWarningEvent -> {
                        _workspace.update {
                            it.copy(toast = "剩余 ${event.remainingIterations} 轮，AI 将尽快完成当前任务")
                        }
                    }
                    is com.kirakira.kiracode.domain.agent.core.BudgetExceededAction -> {
                        // 迭代预算耗尽：插入用户决策卡，内核挂起等待选择
                        _workspace.update { ws2 ->
                            ws2.copy(
                                agentCards = ws2.agentCards.filterNot {
                                    it is AgentCardUi.BudgetDecision
                                } + AgentCardUi.BudgetDecision(
                                    id = event.id,
                                    completedSummary = event.completedSummary,
                                    pendingSteps = event.pendingSteps,
                                    iterationsUsed = event.iterationsUsed
                                ),
                                pendingInteraction = PendingInteraction.Budget(cardId = event.id)
                            )
                        }
                        _isExecuting.value = false
                        _uiState.update { s ->
                            if (s is ChatUiState.Ready) s.copy(isSending = false) else s
                        }
                    }
                    is com.kirakira.kiracode.domain.agent.core.FinishAction -> {
                        // 内核循环结束：错误时上抛显示
                        val errText = event.outputs["error"]
                        if (errText != null) {
                            failedMsg = errText
                            _workspace.update { ws2 ->
                                ws2.copy(
                                    agentCards = ws2.agentCards + AgentCardUi.Info(
                                        id = java.util.UUID.randomUUID().toString(),
                                        text = errText,
                                        kind = AgentCardUi.Info.Kind.FAIL
                                    )
                                )
                            }
                            // 错误不覆盖已有正文（第一轮内容保留，FAIL 卡完整呈现错误）；
                            // 仅当占位为空（首轮即失败）时才用错误文本收束，避免空流式占位残留
                            updatePlaceholder(sessionId, currentPlaceholderId) { msg ->
                                if (msg.content.isBlank()) msg.copyWithContent(errText, isStreaming = false)
                                else msg.copyWithContent(msg.content, isStreaming = false)
                            }
                            _uiState.update { s -> if (s is ChatUiState.Ready) s.copy(isSending = false, error = errText) else s }
                        } else if (event.outputs["status"] == "budget_exceeded") {
                            // 兼容旧内核：预算耗尽提示（新内核走 BudgetExceededAction 决策卡）
                            _workspace.update { it.copy(toast = "已达最大迭代次数，任务未完全完成") }
                        } else if (event.outputs["status"] == "saved") {
                            _workspace.update { it.copy(toast = "已保存当前进度") }
                        } else if (event.outputs["status"] == "abandoned") {
                            _workspace.update { it.copy(toast = "已放弃任务") }
                        } else {
                            // V3.1 批次4任务1：计划完成/显式 FINISH → 明示任务完成
                            _workspace.update { it.copy(toast = "任务完成") }
                        }
                    }
                    is com.kirakira.kiracode.domain.agent.core.SubAgentStartedEvent -> {
                        // 子代理开始：追加"运行中"状态卡（Completed/Failed 原位更新）
                        _workspace.update { ws2 ->
                            ws2.copy(
                                agentCards = ws2.agentCards + AgentCardUi.SubAgent(
                                    id = event.id,
                                    subagentId = event.subagentId,
                                    task = event.task,
                                    status = AgentCardUi.SubAgent.STATUS_RUNNING,
                                    startedAt = event.startedAt,
                                    parentAgentId = currentSessionId?.toString() ?: event.parentAgentId
                                ),
                                toast = "子代理已派发：${event.task.take(40)}"
                            )
                        }
                    }
                    is com.kirakira.kiracode.domain.agent.core.SubAgentCompletedEvent -> {
                        // 子代理完成：原位更新为"已完成"（无匹配卡则追加，防事件乱序丢卡）
                        _workspace.update { ws2 ->
                            val matched = ws2.agentCards.any {
                                it is AgentCardUi.SubAgent && it.subagentId == event.subagentId
                            }
                            val cards = if (matched) {
                                ws2.agentCards.map { card ->
                                    if (card is AgentCardUi.SubAgent && card.subagentId == event.subagentId) {
                                        card.copy(
                                            status = AgentCardUi.SubAgent.STATUS_DONE,
                                            finishedAt = event.finishedAt,
                                            output = event.output,
                                            outputPreview = event.outputPreview
                                        )
                                    } else card
                                }
                            } else {
                                ws2.agentCards + AgentCardUi.SubAgent(
                                    id = event.id,
                                    subagentId = event.subagentId,
                                    task = event.task,
                                    status = AgentCardUi.SubAgent.STATUS_DONE,
                                    startedAt = event.startedAt,
                                    finishedAt = event.finishedAt,
                                    output = event.output,
                                    outputPreview = event.outputPreview,
                                    parentAgentId = currentSessionId?.toString() ?: event.parentAgentId
                                )
                            }
                            ws2.copy(agentCards = cards, toast = "子代理已完成（结果待主 Agent 审核）")
                        }
                    }
                    is com.kirakira.kiracode.domain.agent.core.SubAgentFailedEvent -> {
                        // 子代理失败：原位更新为"失败"（错误原因可见；主 Agent 收到回注可自行重试/换策略）
                        _workspace.update { ws2 ->
                            val matched = ws2.agentCards.any {
                                it is AgentCardUi.SubAgent && it.subagentId == event.subagentId
                            }
                            val cards = if (matched) {
                                ws2.agentCards.map { card ->
                                    if (card is AgentCardUi.SubAgent && card.subagentId == event.subagentId) {
                                        card.copy(
                                            status = AgentCardUi.SubAgent.STATUS_FAILED,
                                            finishedAt = event.finishedAt,
                                            errorMessage = event.errorMessage
                                        )
                                    } else card
                                }
                            } else {
                                ws2.agentCards + AgentCardUi.SubAgent(
                                    id = event.id,
                                    subagentId = event.subagentId,
                                    task = event.task,
                                    status = AgentCardUi.SubAgent.STATUS_FAILED,
                                    startedAt = event.startedAt,
                                    finishedAt = event.finishedAt,
                                    errorMessage = event.errorMessage,
                                    parentAgentId = currentSessionId?.toString() ?: event.parentAgentId
                                )
                            }
                            ws2.copy(agentCards = cards, toast = "子代理失败：${event.errorMessage.take(40)}")
                        }
                    }
                    else -> {}
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            // V3.1 批次4任务5：取消同样清理流式僵尸占位
            // 子代理取消收敛：运行中的子代理卡 → 失败（不留僵尸"运行中"状态）
            _workspace.update { ws2 -> ws2.copy(agentCards = ws2.agentCards.failRunningSubAgents()) }
            _uiState.update { s ->
                if (s is ChatUiState.Ready) {
                    s.copy(
                        messages = s.messages.mapNotNull { m ->
                            if (m.isStreaming) {
                                if (m.content.isBlank()) null else m.copy(isStreaming = false)
                            } else m
                        },
                        isSending = false
                    )
                } else s
            }
            _liveReasoning.value = null
            _isExecuting.value = false
            throw e
        } catch (e: Exception) {
            android.util.Log.e("WorkspaceViewModel", "dispatchSend 失败", e)
            failedMsg = "执行失败：${e.message ?: "未知错误"}"
            // V3.1 批次4任务5：清理流式占位（空占位移除/非空收束），杜绝"正在生成中…"僵尸
            _uiState.update { s ->
                if (s is ChatUiState.Ready) {
                    s.copy(
                        messages = s.messages.mapNotNull { m ->
                            if (m.isStreaming) {
                                if (m.content.isBlank()) null else m.copy(isStreaming = false)
                            } else m
                        },
                        isSending = false,
                        error = failedMsg
                    )
                } else s
            }
            _liveReasoning.value = null
            _isExecuting.value = false
            // 失败持久事件卡（错误原因保留在时间轴，不只依赖 Snackbar）
            val failText = failedMsg
            if (failText != null) {
                _workspace.update { ws2 ->
                    ws2.copy(
                        agentCards = ws2.agentCards + AgentCardUi.Info(
                            id = java.util.UUID.randomUUID().toString(),
                            text = failText,
                            kind = AgentCardUi.Info.Kind.FAIL
                        )
                    )
                }
            }
        } finally {
            // 诊断：VM_TRACE 输出
            if (diagEnabled) {
                val maxGapMs = if (vmMaxCommitGapNanos > 0) vmMaxCommitGapNanos / 1_000_000 else 0L
                val firstDeltaMs = if (vmFirstDeltaNanos > 0) (vmFirstDeltaNanos - vmTraceStartNanos) / 1_000_000 else 0L
                val firstCommitMs = if (vmFirstCommitNanos > 0) (vmFirstCommitNanos - vmTraceStartNanos) / 1_000_000 else 0L
                com.kirakira.kiracode.util.AppLogger.d(
                    "VM_TRACE",
                    "delta_received=$vmDeltaReceived " +
                    "ui_commit=$vmCommitCount " +
                    "first_delta_ms=$firstDeltaMs " +
                    "first_commit_ms=$firstCommitMs " +
                    "max_commit_gap_ms=$maxGapMs " +
                    "skipped_by_batch=$vmSkippedDueToBatch"
                )
            }
            // 兜底清交互挂起（取消/结束时状态不残留，输入栏恢复普通发送）
            _isExecuting.value = false
            _workspace.update { it.copy(pendingInteraction = null) }
            // 重置占位 id（正常/异常/取消三态统一；正常路径占位已收束，异常/取消已清理，
            // 置空防止残留 id 被下一轮或 appendUserBubble 误用）
            currentPlaceholderId = ""
            // 轮次结束/取消/异常：保存事件快照（定稿事件/计划/任务）
            saveEventSnapshot(sessionId)
        }
    }

    /** dto ChatMessage → 核心 Message（内核输入转换） */
    private fun com.kirakira.kiracode.data.remote.dto.ChatMessage.toCoreMessage(): com.kirakira.kiracode.domain.agent.core.Message {
        return when (role) {
            "system" -> com.kirakira.kiracode.domain.agent.core.SystemMessage(content ?: "")
            "assistant" -> com.kirakira.kiracode.domain.agent.core.AssistantMessage(content ?: "")
            "tool" -> com.kirakira.kiracode.domain.agent.core.ToolMessage(content ?: "", tool_call_id ?: "", name ?: "")
            else -> com.kirakira.kiracode.domain.agent.core.UserMessage(content ?: "")
        }
    }

    /** 确保当前流式占位存在；已 finalized 的占位不能复用，否则下一轮会覆盖上一轮正文 */
    private suspend fun ensurePlaceholder(sessionId: Long) {
        val existsAndStreaming = (_uiState.value as? ChatUiState.Ready)
            ?.messages
            ?.any { it.id == currentPlaceholderId && it.isStreaming } == true
        if (existsAndStreaming) return
        // placeholder 不存在，或已 finalized（isStreaming=false）→ 新建
        val np = ChatMessage.createAssistantMessage(sessionId, "", isStreaming = true)
        _uiState.update { s -> if (s is ChatUiState.Ready) s.copy(messages = s.messages + np) else s }
        currentPlaceholderId = np.id
    }

    /** 收束占位（定稿正文+本轮思维）并持久化助手消息（修复会话恢复丢回复） */
    private suspend fun sealPlaceholder(sessionId: Long, localId: String, content: String, thinking: String) {
        if (content.isBlank() && thinking.isBlank()) return
        updatePlaceholder(sessionId, localId) {
            it.copyWithContent(content, isStreaming = false, thinking = thinking)
        }
        if (content.isNotBlank() || thinking.isNotBlank()) {
            runCatching {
                messageRepository.insertMessage(
                    com.kirakira.kiracode.domain.model.Message.createAssistantMessage(
                        sessionId,
                        content,
                        thinking = thinking
                    )
                )
            }
        }
    }

    // ============ V2/V3 交互回复（追问/计划/Diff/脑补） ============

    /**
     * 输入路由：有挂起交互时回答交互（追问=回答/计划=修改反馈/Diff=继续改反馈），
     * 否则普通发送。交互等待期内核挂起，不会产生并发任务。
     */
    fun onUserSend(text: String) {
        if (_workspace.value.pendingInteraction != null) {
            answerPending(text)
            return
        }
        sendMessage(text)
    }

    /** 回答当前挂起交互（自由文本） */
    fun answerPending(raw: String) {
        val pending = _workspace.value.pendingInteraction ?: return
        val text = raw.trim()
        if (text.isBlank()) return
        viewModelScope.launch {
            when (pending) {
                is PendingInteraction.Question -> {
                    val skipped = text == "跳过" || text.equals("skip", ignoreCase = true)
                    updateAgentCard(pending.cardId) {
                        (it as? AgentCardUi.Question)?.copy(
                            answered = if (skipped) null else text,
                            skipped = skipped
                        ) ?: it
                    }
                    appendUserBubble(sessionId = currentSessionId, text = if (skipped) "（跳过这个问题）" else text)
                    agentController.respondToAgent(
                        if (skipped) com.kirakira.kiracode.domain.agent.AgentController.MARK_SKIP else text
                    )
                }
                is PendingInteraction.Plan -> {
                    appendUserBubble(sessionId = currentSessionId, text = "（修改计划）$text")
                    agentController.respondToAgent(
                        com.kirakira.kiracode.domain.agent.AgentController.MARK_MODIFY + text
                    )
                }
                is PendingInteraction.Diff -> {
                    updateAgentCard(pending.cardId) {
                        (it as? AgentCardUi.Diff)?.copy(decision = AgentCardUi.Diff.CONTINUED) ?: it
                    }
                    appendUserBubble(sessionId = currentSessionId, text = "（继续改）$text")
                    agentController.respondToAgent(
                        com.kirakira.kiracode.domain.agent.AgentController.MARK_CONTINUE + text
                    )
                }
                is PendingInteraction.Budget -> {
                    _workspace.update { it.copy(toast = "请点击「继续 / 保存当前 / 放弃」按钮") }
                    return@launch
                }
            }
            _workspace.update { it.copy(pendingInteraction = null) }
        }
    }

    /** 跳过当前追问（→ AI 给出 3 个候选选项） */
    fun skipQuestion() {
        val pending = _workspace.value.pendingInteraction as? PendingInteraction.Question ?: return
        updateAgentCard(pending.cardId) {
            (it as? AgentCardUi.Question)?.copy(skipped = true) ?: it
        }
        appendUserBubble(sessionId = currentSessionId, text = "（跳过这个问题）")
        _workspace.update { it.copy(pendingInteraction = null) }
        agentController.respondToAgent(com.kirakira.kiracode.domain.agent.AgentController.MARK_SKIP)
    }

    /** 确认制卡计划（→ AI 按计划逐步执行，complete_step 标记进度） */
    fun confirmPlan() {
        val pending = _workspace.value.pendingInteraction as? PendingInteraction.Plan ?: return
        updateAgentCard(pending.cardId) {
            (it as? AgentCardUi.Plan)?.copy(status = AgentCardUi.Plan.STATUS_CONFIRMED) ?: it
        }
        appendUserBubble(sessionId = currentSessionId, text = "（确认计划，开始执行）")
        _workspace.update { it.copy(pendingInteraction = null) }
        agentController.respondToAgent(com.kirakira.kiracode.domain.agent.AgentController.MARK_CONFIRM)
    }

    /** Diff 决定：接受（应用修改）/ 拒绝（回滚保持原值） */
    fun decideDiff(accepted: Boolean) {
        val pending = _workspace.value.pendingInteraction as? PendingInteraction.Diff ?: return
        updateAgentCard(pending.cardId) {
            (it as? AgentCardUi.Diff)?.copy(
                decision = if (accepted) AgentCardUi.Diff.ACCEPTED else AgentCardUi.Diff.REJECTED
            ) ?: it
        }
        appendUserBubble(
            sessionId = currentSessionId,
            text = if (accepted) "（接受修改）" else "（拒绝修改，保持原值）"
        )
        _workspace.update { it.copy(pendingInteraction = null) }
        agentController.respondToAgent(
            if (accepted) com.kirakira.kiracode.domain.agent.AgentController.MARK_ACCEPT
            else com.kirakira.kiracode.domain.agent.AgentController.MARK_REJECT
        )
    }

    /** Diff「继续改」：提交修改反馈（AI 基于反馈给出新版本，再次弹 Diff） */
    fun submitDiffFeedback(feedback: String) {
        val pending = _workspace.value.pendingInteraction as? PendingInteraction.Diff ?: return
        if (feedback.isBlank()) return
        updateAgentCard(pending.cardId) {
            (it as? AgentCardUi.Diff)?.copy(decision = AgentCardUi.Diff.CONTINUED) ?: it
        }
        appendUserBubble(sessionId = currentSessionId, text = "（继续改）$feedback")
        _workspace.update { it.copy(pendingInteraction = null) }
        agentController.respondToAgent(
            com.kirakira.kiracode.domain.agent.AgentController.MARK_CONTINUE + feedback.trim()
        )
    }

    /** 计划「修改」：提交修改反馈（AI 调整后重新提交计划） */
    fun submitPlanFeedback(feedback: String) {
        val pending = _workspace.value.pendingInteraction as? PendingInteraction.Plan ?: return
        if (feedback.isBlank()) return
        appendUserBubble(sessionId = currentSessionId, text = "（修改计划）$feedback")
        _workspace.update { it.copy(pendingInteraction = null) }
        agentController.respondToAgent(
            com.kirakira.kiracode.domain.agent.AgentController.MARK_MODIFY + feedback.trim()
        )
    }

    /** 预算耗尽：继续追加轮次（内核保持挂起，不丢工具历史） */
    fun continueFromBudget(extraIterations: Int = 5) {
        if (_workspace.value.pendingInteraction !is PendingInteraction.Budget) return
        val extra = extraIterations.coerceIn(1, 50)
        _workspace.update { ws ->
            ws.copy(
                agentCards = ws.agentCards.filterNot { it is AgentCardUi.BudgetDecision },
                pendingInteraction = null
            )
        }
        appendUserBubble(sessionId = currentSessionId, text = "（继续 $extra 轮）")
        agentController.respondToAgent(
            com.kirakira.kiracode.domain.agent.AgentController.MARK_BUDGET_CONTINUE + extra
        )
    }

    /** 预算耗尽：保存当前进度并结束 */
    fun skipAndFinish() {
        if (_workspace.value.pendingInteraction !is PendingInteraction.Budget) return
        _workspace.update { ws ->
            ws.copy(
                agentCards = ws.agentCards.filterNot { it is AgentCardUi.BudgetDecision },
                pendingInteraction = null,
                toast = "已保存当前进度"
            )
        }
        appendUserBubble(sessionId = currentSessionId, text = "（保存当前进度并结束）")
        agentController.respondToAgent(
            com.kirakira.kiracode.domain.agent.AgentController.MARK_BUDGET_SAVE
        )
    }

    /** 预算耗尽：放弃当前任务 */
    fun abandonTask() {
        if (_workspace.value.pendingInteraction !is PendingInteraction.Budget) return
        _workspace.update { ws ->
            ws.copy(
                agentCards = ws.agentCards.filterNot { it is AgentCardUi.BudgetDecision },
                pendingInteraction = null,
                toast = "已放弃任务"
            )
        }
        appendUserBubble(sessionId = currentSessionId, text = "（放弃任务）")
        agentController.respondToAgent(
            com.kirakira.kiracode.domain.agent.AgentController.MARK_BUDGET_ABANDON
        )
    }

    /** 脑补标注「重写」：用户描述要求 → 发起新一轮请求重写该字段 */
    fun requestRewrite(fieldLabel: String, instruction: String) {
        val busy = (_uiState.value as? ChatUiState.Ready)?.isSending == true ||
            _workspace.value.pendingInteraction != null
        if (busy) {
            _workspace.update { it.copy(toast = "请先回答当前问题或等待任务完成") }
            return
        }
        sendMessage("请重写角色卡的「${fieldLabel}」：${instruction.trim()}")
    }

    /** 脑补标注「给选项」：AI 给出 3 个候选（ask_user）让用户点选 */
    fun requestOptions(fieldLabel: String) {
        val busy = (_uiState.value as? ChatUiState.Ready)?.isSending == true ||
            _workspace.value.pendingInteraction != null
        if (busy) {
            _workspace.update { it.copy(toast = "请先回答当前问题或等待任务完成") }
            return
        }
        sendMessage("请为角色卡的「${fieldLabel}」给出 3 个不同的候选方案（A/B/C），用 ask_user 工具的 options 参数让我点选")
    }

    /** 按 id 更新交互卡片状态 */
    /** web_search observation → 搜索结果卡（metadata.search_results JSON 解码失败返回 null，不影响主流程） */
    private fun decodeSearchCard(
        event: com.kirakira.kiracode.domain.agent.core.CardObservation,
        resultsJson: String
    ): AgentCardUi.Search? {
        return try {
            val hits = Json { ignoreUnknownKeys = true }.decodeFromString(
                ListSerializer(com.kirakira.kiracode.data.remote.search.SearchHit.serializer()),
                resultsJson
            )
            AgentCardUi.Search(
                id = java.util.UUID.randomUUID().toString(),
                query = event.metadata["search_query"].orEmpty(),
                resultCount = event.metadata["result_count"]?.toIntOrNull() ?: hits.size,
                provider = event.metadata["provider"].orEmpty(),
                results = hits.map { SearchResultItemUi(it.title, it.url, it.snippet) },
                digest = event.content.substringAfter("\n\n")
            )
        } catch (e: Exception) {
            com.kirakira.kiracode.util.AppLogger.w("VM", "搜索结果卡解码失败: ${e.message}")
            null
        }
    }

    /** audit_card observation → 审计卡（metadata.audit_report JSON 解码失败返回 null，不影响主流程） */
    private fun decodeAuditCard(
        event: com.kirakira.kiracode.domain.agent.core.CardObservation,
        reportJson: String
    ): AgentCardUi.Audit? {
        return runCatching {
            val report = Json { ignoreUnknownKeys = true }
                .decodeFromString(AuditReport.serializer(), reportJson)
            AgentCardUi.Audit(
                id = event.id,
                cardName = report.cardName,
                entryCount = report.entryCount,
                estimatedTokens = report.estimatedTokens,
                errorCount = report.errorCount,
                warningCount = report.warningCount,
                infoCount = report.infoCount,
                issues = report.issues.map { issue ->
                    SerializableAuditIssue(
                        severity = issue.severity.name,
                        entryUid = issue.entryUid,
                        fieldName = issue.fieldName,
                        entryComment = issue.entryComment,
                        pairUid = issue.pairUid,
                        issueCode = issue.issueCode,
                        description = issue.description,
                        suggestion = issue.suggestion
                    )
                }
            )
        }.onFailure { e ->
            com.kirakira.kiracode.util.AppLogger.w("VM", "审计报告卡解码失败: ${e.message}")
        }.getOrNull()
    }

    private fun updateAgentCard(cardId: String, transform: (AgentCardUi) -> AgentCardUi) {
        _workspace.update { ws ->
            ws.copy(agentCards = ws.agentCards.map { if (it.id == cardId) transform(it) else it })
        }
    }

    /** 追加用户交互气泡（显示+持久化：跨 send 的 LLM 上下文连续性） */
    private fun appendUserBubble(sessionId: Long?, text: String) {
        val sid = sessionId ?: return
        val state = _uiState.value
        if (state !is ChatUiState.Ready) return
        // 移除空的流式占位（让 AI 续写从气泡下方开始，顺序正确）
        val ph = state.messages.firstOrNull { it.id == currentPlaceholderId }
        val dropEmpty = ph != null && ph.isStreaming && ph.content.isBlank()
        if (dropEmpty) currentPlaceholderId = ""
        val msgs = if (dropEmpty && ph != null) state.messages.filterNot { it.id == ph.id } else state.messages
        _uiState.update { s ->
            if (s is ChatUiState.Ready) s.copy(messages = msgs + ChatMessage.createUserMessage(sid, text)) else s
        }
        viewModelScope.launch {
            runCatching {
                messageRepository.insertMessage(com.kirakira.kiracode.domain.model.Message.createUserMessage(sid, text))
            }
        }
    }

    private fun refCardsFallbackNeeded(refs: List<CharacterCard>): Boolean =
        refs.size != _workspace.value.selectedReferenceIds.size

    private suspend fun finishAssistant(
        sessionId: Long,
        placeholderLocalId: String,
        fullText: String,
        promptTokens: Int,
        completionTokens: Int,
        cachedTokens: Int = 0,
        hasProtocol: Boolean
    ) {
        // 协议解析（ViewModel 端：内核负责工具调用，plan.*/task.*/card-update 文本协议在此二次解析）
        val ops = if (hasProtocol) CardOpsParser.parse(fullText) else emptyList()
        val taskOps = if (hasProtocol) com.kirakira.kiracode.domain.agent.TaskOpsParser.parse(fullText) else emptyList()
        val planOps = com.kirakira.kiracode.domain.agent.PlanOpsParser.parse(fullText)
        // 正文占位：优先显示自然语言；若正文只剩操作块，则显示实际动作摘要（不再误导性空话）；
        // 纯思考轮（正文为空且无操作）不伪造占位回复——正文保持空白，本轮产出由 thinking 思维链承载
        val stripped = CardOpsParser.stripUpdateBlocks(fullText)
        val displayText = when {
            fullText.isBlank() && ops.isEmpty() && taskOps.isEmpty() && planOps.isEmpty() -> ""
            stripped.isNotBlank() -> stripped
            ops.isNotEmpty() -> "已完成 ${ops.size} 项修改：" +
                    ops.take(3).joinToString("、") { it.describe.take(24) }
            taskOps.isNotEmpty() -> "任务板已更新（${taskOps.size} 条）"
            planOps.isNotEmpty() -> "计划书已更新，继续执行下一阶段…"
            else -> ""
        }

        updatePlaceholder(sessionId, placeholderLocalId) {
            it.copyWithContent(displayText, isStreaming = false)
        }
        // 助手消息持久化（新内核路径修复：此前不落库，会话恢复丢回复）
        if (displayText.isNotBlank()) {
            runCatching {
                messageRepository.insertMessage(
                    com.kirakira.kiracode.domain.model.Message.createAssistantMessage(sessionId, displayText)
                )
            }
        }
        _uiState.update { s -> if (s is ChatUiState.Ready) s.copy(isSending = false) else s }

        // 优先用 API 真实缓存数据；API 不返回时退回启发式估算
        val hit = when {
            promptTokens > 0 && cachedTokens > 0 ->
                (cachedTokens.toFloat() / promptTokens.toFloat()).coerceIn(0f, 1f)
            promptTokens > 0 && lastPromptTokens > 0 ->
                (lastPromptTokens.toFloat() / promptTokens.toFloat()).coerceIn(0f, 0.95f)
            else -> 0f
        }
        lastPromptTokens = promptTokens

        recomputeContextFromEvent(promptTokens, completionTokens, hit)

        // P0：卡片修改的唯一执行方是内核（AgentController）——工具路径走 toolRegistry，
        // 文本协议降级路径同样在内核内执行（AGENT 模式经 runDiffReview 审查）。
        // 此处不再直接应用文本协议操作，避免绕过 Diff 确认与双路径落卡。

        // 任务板操作：所有模式即时生效（信息性数据，AI 读写闭环）
        if (taskOps.isNotEmpty()) {
            var tasks = _workspace.value.tasks
            for (op in taskOps) tasks = op.applyTo(tasks)
            _workspace.update {
                it.copy(tasks = tasks, toast = "任务板已更新（${taskOps.size} 条指令）")
            }
        }

        // 计划书操作：所有模式即时生效（全自动模式核心）
        if (planOps.isNotEmpty()) {
            var book = _workspace.value.planBook
            for (op in planOps) book = op.applyTo(book)
            val msg = if (book.parts.isNotEmpty() && book.isComplete) "计划书已全部完成"
                else "计划书已更新（${book.progressText}）"
            _workspace.update { it.copy(planBook = book, toast = msg) }
        }

        // 自动总结：开启且上下文占用达到用户阈值时触发
        val wsNow = _workspace.value
        val window = wsNow.contextWindowTokens
        val threshold = wsNow.summarizeThreshold
        if (wsNow.autoSummarize &&
            window > 0 &&
            promptTokens > (window * threshold).toInt()
        ) {
            compressContext()
        }
    }

    private fun updatePlaceholder(
        sessionId: Long,
        localId: String,
        transform: (ChatMessage) -> ChatMessage
    ) {
        _uiState.update { state ->
            if (state is ChatUiState.Ready) {
                state.copy(messages = state.messages.map { if (it.id == localId) transform(it) else it })
            } else state
        }
    }

    // ============ 操作确认（代理人模式） ============
    // （pendingOperations 死队列已删除：代理人模式确认走 pendingInteraction 挂起语义）
    // P0：文本协议显示级对照与直接落卡逻辑已移除——卡片修改统一由内核执行并经 CardObservation 回填。

    // ============ 上下文管理 ============

    private fun recomputeContext(contents: List<String>) {
        val tokens = contents.sumOf { TokenEstimatorLite.estimate(it) }
        _workspace.update { ws ->
            ws.copy(contextInfo = ws.contextInfo.copy(totalTokens = tokens, messageCount = contents.size))
        }
    }

    private fun recomputeContextFromEvent(promptTokens: Int, completionTokens: Int, hitRate: Float) {
        _workspace.update { ws ->
            ws.copy(contextInfo = ws.contextInfo.copy(
                // 上下文占用 = 当前发送给模型的 prompt tokens（衡量上下文窗口用量）
                // completion 不占上下文窗口配额，不应计入"占用"
                totalTokens = promptTokens,
                cacheHitRate = hitRate,
                processedTokens = ws.contextInfo.processedTokens + completionTokens,
                promptTokensTotal = ws.contextInfo.promptTokensTotal + promptTokens,
                completionTokensTotal = ws.contextInfo.completionTokensTotal + completionTokens,
                requestCount = ws.contextInfo.requestCount + 1
            ))
        }
    }

    fun compressContext() {
        val sessionId = currentSessionId ?: return
        viewModelScope.launch {
            try {
                val result = summarizeContextUseCase.summarizeManually(sessionId, _workspace.value.contextWindowTokens)
                // 总结成功：旧消息已从数据库删除，重新加载消息列表让 UI 反映真实上下文收缩
                if (result.isSuccess) {
                    openSessionInternal(sessionId)
                }
                _workspace.update { it.copy(toast = "上下文已压缩，旧消息已移除") }
            } catch (e: Exception) {
                _workspace.update { it.copy(toast = "压缩失败：${e.message}") }
            }
        }
    }

    /** 上下文窗口大小（token，最大 200 万） */
    fun setContextWindowTokens(tokens: Int) {
        val v = tokens.coerceIn(1024, 2_000_000)
        _workspace.update { it.copy(contextWindowTokens = v) }
        viewModelScope.launch { runCatching { preferencesRepository.setContextWindowTokens(v) } }
    }

    /** 自动总结阈值（0.3~0.95） */
    fun setSummarizeThreshold(th: Float) {
        val v = th.coerceIn(0.3f, 0.95f)
        _workspace.update { it.copy(summarizeThreshold = v) }
        viewModelScope.launch { runCatching { preferencesRepository.setContextSummarizeThreshold(v) } }
    }

    fun releaseContext() {
        val sessionId = currentSessionId ?: return
        viewModelScope.launch {
            runCatching { messageRepository.deleteMessagesBySessionId(sessionId) }
            _uiState.update { s ->
                if (s is ChatUiState.Ready) s.copy(messages = emptyList()) else s
            }
            lastPromptTokens = 0
            recomputeContext(emptyList())
            _workspace.update { it.copy(toast = "上下文已释放") }
        }
    }

    fun triggerSummarize() = compressContext()

    // ============ 控制面板与任务板 ============

    fun setTemperature(value: Float) {
        _workspace.update { it.copy(temperature = value.coerceIn(0f, 2f)) }
        persistWorkspace()
    }

    /** V3.0 top_p 采样参数 */
    fun setTopP(value: Float) {
        _workspace.update { it.copy(topP = value.coerceIn(0.1f, 1f)) }
    }

    /** V1.0 制卡师开关（关键决策询问用户；与任意模式共存） */
    fun setCardMasterEnabled(enabled: Boolean) {
        _workspace.update { it.copy(cardMasterEnabled = enabled) }
        viewModelScope.launch { runCatching { preferencesRepository.setCardMasterEnabled(enabled) } }
    }

    /** 正则生成模式（持久化；默认语义折叠） */
    fun setRegexGenerationMode(mode: RegexGenerationMode) {
        _workspace.update { it.copy(regexGenerationMode = mode, toast = "正则生成：${mode.label}") }
        viewModelScope.launch { runCatching { preferencesRepository.setRegexGenerationMode(mode) } }
    }

    /** 解禁工具调用次数（大型重构；解禁后硬上限 200 轮） */
    fun setUnlimitedToolCalls(enabled: Boolean) {
        _workspace.update {
            it.copy(
                unlimitedToolCalls = enabled,
                toast = if (enabled) "已解禁工具调用次数（硬上限 200 轮）" else "已恢复工具调用预算"
            )
        }
        viewModelScope.launch { runCatching { preferencesRepository.setUnlimitedToolCalls(enabled) } }
    }

    /** V1.0 思考强度档位（持久化；null=自动不传参） */
    fun setReasoningEffort(effort: String?) {
        _workspace.update {
            it.copy(
                reasoningEffort = effort,
                toast = when (effort) {
                    null -> "思考强度：自动（由模型决定）"
                    else -> "思考强度：$effort"
                }
            )
        }
        viewModelScope.launch { runCatching { preferencesRepository.setReasoningEffortPref(effort) } }
    }

    /** V3.0 联网搜索开关（持久化） */
    fun setWebSearchEnabled(enabled: Boolean) {
        _workspace.update { it.copy(webSearchEnabled = enabled, toast = if (enabled) "联网搜索已开启" else "联网搜索已关闭") }
        viewModelScope.launch { runCatching { preferencesRepository.setWebSearchEnabled(enabled) } }
    }


    fun setAutoSummarize(enabled: Boolean) {
        _workspace.update { it.copy(autoSummarize = enabled, toast = if (enabled) "自动总结已开启" else "自动总结已关闭") }
    }

    // ============ 创作偏好（目录全局，选择会话级） ============

    fun togglePreferenceOption(optionId: String) {
        val current = _sessionPreferences.value
        val next = if (optionId in current.selectedOptionIds) {
            current.copy(selectedOptionIds = current.selectedOptionIds - optionId)
        } else {
            current.copy(selectedOptionIds = current.selectedOptionIds + optionId)
        }
        _sessionPreferences.value = next
        val sessionId = currentSessionId ?: return
        viewModelScope.launch {
            runCatching {
                sessionRepository.updateSelectedPreferences(sessionId, encodeIds(next.selectedOptionIds))
            }
        }
    }

    fun getPreferenceInjection(): String =
        _sessionPreferences.value.buildInjection(preferenceCatalog.value)

    fun addPreferenceDimension(label: String) {
        if (label.isBlank()) return
        viewModelScope.launch { runCatching { preferenceRepository.addDimension(label) } }
    }

    fun updatePreferenceDimension(id: String, label: String) {
        if (label.isBlank()) return
        viewModelScope.launch { runCatching { preferenceRepository.updateDimension(id, label) } }
    }

    fun deletePreferenceDimension(id: String) {
        val removedIds = preferenceCatalog.value
            .firstOrNull { it.id == id }
            ?.options
            ?.map { it.id }
            ?.toSet()
            .orEmpty()
        removePreferenceIds(removedIds)
        viewModelScope.launch { runCatching { preferenceRepository.deleteDimension(id) } }
    }

    fun addPreferenceOption(dimensionId: String, label: String, rule: String) {
        if (label.isBlank() || rule.isBlank()) return
        viewModelScope.launch { runCatching { preferenceRepository.addOption(dimensionId, label, rule) } }
    }

    fun updatePreferenceOption(id: String, label: String, rule: String) {
        if (label.isBlank() || rule.isBlank()) return
        viewModelScope.launch { runCatching { preferenceRepository.updateOption(id, label, rule) } }
    }

    fun deletePreferenceOption(id: String) {
        removePreferenceIds(setOf(id))
        viewModelScope.launch { runCatching { preferenceRepository.deleteOption(id) } }
    }

    private fun removePreferenceIds(ids: Set<String>) {
        if (ids.isEmpty()) return
        val current = _sessionPreferences.value
        val nextIds = current.selectedOptionIds.filterNot { it in ids }
        if (nextIds.size == current.selectedOptionIds.size) return
        _sessionPreferences.value = current.copy(selectedOptionIds = nextIds)
        val sessionId = currentSessionId ?: return
        viewModelScope.launch {
            runCatching {
                sessionRepository.updateSelectedPreferences(sessionId, encodeIds(nextIds))
            }
        }
    }

    fun setCustomPrompt(prompt: String) {
        viewModelScope.launch {
            // P0 三态：非空内容=保存并启用；清空=不注入（未配置态）
            runCatching {
                preferencesRepository.setCustomPromptText(prompt)
                if (prompt.isNotBlank()) preferencesRepository.setCustomPromptEnabled(true)
            }
            _workspace.update { it.copy(customPrompt = prompt) }
        }
    }

    // ============ V2 Beta 多套自定义提示词 ============

    /** 多套自定义提示词列表 */
    private val _customPromptSets = MutableStateFlow<List<com.kirakira.kiracode.domain.model.CustomPromptSet>>(emptyList())
    val customPromptSets: StateFlow<List<com.kirakira.kiracode.domain.model.CustomPromptSet>> = _customPromptSets

    /** 当前启用的提示词套 id（null=使用单套 customPrompt 旧键） */
    private val _activeCustomPromptSetId = MutableStateFlow<String?>(null)
    val activeCustomPromptSetId: StateFlow<String?> = _activeCustomPromptSetId

    /** P0：自定义提示词三态（设置页状态显示与默认模板按钮判断） */
    val customPromptState: StateFlow<com.kirakira.kiracode.domain.model.CustomPromptState> =
        preferencesRepository.customPromptState.stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            com.kirakira.kiracode.domain.model.CustomPromptState.NotConfigured
        )

    /** 加载多套提示词（init 时调用） */
    private fun loadCustomPromptSets() {
        viewModelScope.launch {
            _customPromptSets.value = com.kirakira.kiracode.domain.model.CustomPromptSetCodec
                .decodeList(preferencesRepository.customPromptSetsJson.first())
            _activeCustomPromptSetId.value = preferencesRepository.activeCustomPromptSetId.first()
        }
    }

    /** 新建/保存一套提示词；replaceId 非空则覆盖该套 */
    fun saveCustomPromptSet(name: String, content: String, replaceId: String? = null) {
        if (name.isBlank() || content.isBlank()) {
            _workspace.update { it.copy(toast = "名称与内容不能为空") }
            return
        }
        val set = com.kirakira.kiracode.domain.model.CustomPromptSet(
            id = replaceId ?: java.util.UUID.randomUUID().toString(),
            name = name.trim(),
            content = content.trim()
        )
        val next = if (replaceId != null) {
            _customPromptSets.value.map { if (it.id == replaceId) set else it }
        } else {
            _customPromptSets.value + set
        }
        _customPromptSets.value = next.filter { it.content.isNotBlank() }.distinctBy { it.id }
        viewModelScope.launch {
            runCatching {
                preferencesRepository.setCustomPromptSetsJson(
                    com.kirakira.kiracode.domain.model.CustomPromptSetCodec.encodeList(_customPromptSets.value)
                )
            }
        }
        // 新建或覆盖后自动启用该套，并同步旧键（兼容 ChatEngine 注入）
        activateCustomPromptSet(set.id)
    }

    /** 删除一套 */
    fun deleteCustomPromptSet(id: String) {
        val next = _customPromptSets.value.filterNot { it.id == id }
        _customPromptSets.value = next
        if (_activeCustomPromptSetId.value == id) {
            _activeCustomPromptSetId.value = null
        }
        viewModelScope.launch {
            runCatching {
                preferencesRepository.setCustomPromptSetsJson(
                    com.kirakira.kiracode.domain.model.CustomPromptSetCodec.encodeList(next)
                )
                if (_activeCustomPromptSetId.value == null) preferencesRepository.setActiveCustomPromptSetId(null)
            }
        }
    }

    /** 切换启用的提示词套（该套内容成为当前 customPrompt，注入每轮请求） */
    fun activateCustomPromptSet(id: String?) {
        val newId = id
        _activeCustomPromptSetId.value = newId
        val active = _customPromptSets.value.firstOrNull { it.id == newId }
        val content = active?.content ?: ""
        _workspace.update { ws ->
            ws.copy(customPrompt = content, toast = if (active != null) "已启用提示词「${active.name}」" else "已停用自定义提示词")
        }
        viewModelScope.launch {
            runCatching { preferencesRepository.setActiveCustomPromptSetId(newId) }
            // P0 三态：启用套=保存文本并开启；停用=仅关闭开关（保留文本，可重新启用恢复）
            if (active != null) {
                runCatching {
                    preferencesRepository.setCustomPromptText(active.content)
                    preferencesRepository.setCustomPromptEnabled(true)
                }
            } else {
                runCatching { preferencesRepository.setCustomPromptEnabled(false) }
            }
        }
    }

    fun loadControlPanelState() {
        viewModelScope.launch {
            val temp = preferencesRepository.defaultTemperature.first()
            val prompt = when (val st = preferencesRepository.customPromptState.first()) {
                is com.kirakira.kiracode.domain.model.CustomPromptState.Enabled -> st.text
                is com.kirakira.kiracode.domain.model.CustomPromptState.Disabled -> st.text
                else -> ""
            }
            val web = preferencesRepository.webSearchEnabled.first()
            _workspace.update { it.copy(temperature = temp, customPrompt = prompt, webSearchEnabled = web) }
        }
    }

    /** 手动添加任务（数据面板） */
    fun addTask(title: String, detail: String = "") {
        if (title.isBlank()) return
        _workspace.update {
            it.copy(tasks = it.tasks + com.kirakira.kiracode.domain.agent.AgentTask(title = title.trim(), detail = detail))
        }
    }

    fun cycleTaskStatus(taskId: String) {
        _workspace.update { ws ->
            ws.copy(tasks = ws.tasks.map { t ->
                if (t.id != taskId) t else t.copy(
                    status = when (t.status) {
                        com.kirakira.kiracode.domain.agent.AgentTask.STATUS_PENDING ->
                            com.kirakira.kiracode.domain.agent.AgentTask.STATUS_RUNNING
                        com.kirakira.kiracode.domain.agent.AgentTask.STATUS_RUNNING ->
                            com.kirakira.kiracode.domain.agent.AgentTask.STATUS_DONE
                        else -> com.kirakira.kiracode.domain.agent.AgentTask.STATUS_PENDING
                    }
                )
            })
        }
    }

    fun deleteTask(taskId: String) {
        _workspace.update { ws -> ws.copy(tasks = ws.tasks.filterNot { it.id == taskId }) }
    }

    fun clearFinishedTasks() {
        _workspace.update { ws ->
            ws.copy(tasks = ws.tasks.filterNot { it.status == com.kirakira.kiracode.domain.agent.AgentTask.STATUS_DONE })
        }
    }

    /** 手动清空计划书（数据面板） */
    fun clearPlanBook() {
        _workspace.update {
            it.copy(planBook = com.kirakira.kiracode.domain.agent.PlanBook(), toast = "计划书已清空")
        }
    }

    // ============ V1.0 灵魂 ============

    /** 启用/停用人格（传 null 或再次点击当前人格即停用） */
    fun activatePersona(id: String?) {
        val next = if (id == _activePersonaId.value) null else id
        _activePersonaId.value = next
        viewModelScope.launch { runCatching { preferencesRepository.setActivePersonaId(next) } }
        _workspace.update { it.copy(toast = if (next != null) "已启用人格" else "已停用人格") }
    }

    /** 删除人格 */
    fun deletePersona(id: String) {
        val list = _personas.value.filterNot { it.id == id }
        _personas.value = list
        if (_activePersonaId.value == id) {
            _activePersonaId.value = null
            viewModelScope.launch { runCatching { preferencesRepository.setActivePersonaId(null) } }
        }
        viewModelScope.launch { runCatching { preferencesRepository.setPersonasJson(com.kirakira.kiracode.domain.model.PersonaCodec.encodeList(list)) } }
    }

    /** 保存人格（训练结果确认或手动新建/编辑） */
    fun savePersona(name: String, description: String, prompt: String, editId: String? = null) {
        if (name.isBlank() || prompt.isBlank()) {
            _workspace.update { it.copy(toast = "名称与提示词不能为空") }
            return
        }
        val persona = com.kirakira.kiracode.domain.model.Persona(
            id = editId ?: java.util.UUID.randomUUID().toString(),
            name = name.trim(),
            description = description.trim(),
            prompt = prompt.trim()
        )
        val list = if (editId != null) _personas.value.map { if (it.id == editId) persona else it } + listOf()
            else _personas.value + persona
        val finalList = list.filter { it.prompt.isNotBlank() }.distinctBy { it.id }
        _personas.value = finalList
        viewModelScope.launch {
            runCatching { preferencesRepository.setPersonasJson(com.kirakira.kiracode.domain.model.PersonaCodec.encodeList(finalList)) }
            // 新建的人格自动启用
            if (editId == null) activatePersona(persona.id)
        }
        _workspace.update { it.copy(toast = "人格已保存并启用") }
    }

    fun dismissError() {
        _uiState.update { s -> if (s is ChatUiState.Ready) s.copy(error = null) else s }
    }

    // ============ V1.0 首启引导 ============

    init {
        viewModelScope.launch {
            _isFirstLaunch.value = preferencesRepository.isFirstLaunch.first()
        }
    }

    /** 用户同意首启声明 */
    fun completeFirstLaunch() {
        _isFirstLaunch.value = false
        viewModelScope.launch { runCatching { preferencesRepository.setFirstLaunchCompleted() } }
    }
}

/** 轻量 token 估算（中文≈1字1token，英文≈4字符1token） */
private object TokenEstimatorLite {
    fun estimate(text: String): Int {
        if (text.isEmpty()) return 0
        var cjk = 0
        var other = 0
        for (ch in text) {
            if (ch.code > 0x2E80) cjk++ else other++
        }
        return cjk + (other + 3) / 4
    }
}

/**
 * 参考文件（V3.1 批次1）：文件选择器加载的小说/文本/角色卡 JSON。
 * content 为文本内容（200KB 截断）；图片类仅记录 uri+name（sizeBytes=0）。
 */
data class ReferenceFile(
    val uri: android.net.Uri,
    val name: String,
    val content: String,
    val sizeBytes: Long = 0
)
