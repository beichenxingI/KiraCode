package com.kirakira.kiracode.ui.trainpersona

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kirakira.kiracode.domain.model.CharacterCard
import com.kirakira.kiracode.domain.model.ContextOverflowException
import com.kirakira.kiracode.domain.model.ModelConfig
import com.kirakira.kiracode.domain.model.TrainDimension
import com.kirakira.kiracode.domain.model.TrainPersonaConfig
import com.kirakira.kiracode.domain.model.TrainPhase
import com.kirakira.kiracode.domain.model.TrainingFailedException
import com.kirakira.kiracode.domain.repository.PreferencesRepository
import com.kirakira.kiracode.domain.repository.ReferenceCardRepository
import com.kirakira.kiracode.domain.usecase.TrainPersonaUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 灵魂训练页 UI 状态。
 */
sealed class TrainUiState {
    data object Idle : TrainUiState()
    data class Running(val phase: TrainPhase, val progress: Float) : TrainUiState()
    data class Result(val prompt: String) : TrainUiState()
    data class Error(
        val message: String,
        val partialResults: Map<TrainDimension, String>
    ) : TrainUiState()
}

/**
 * 灵魂训练页 ViewModel。
 *
 * 职责：
 * - 加载参考卡列表（支持搜索、预览、多选）；
 * - 训练配置（模型 / 温度 / Top-P / 维度 / 自定义偏好）；
 * - 全自动多轮浏览训练（逐维度分析 → 合成）；
 * - 生成的提示词预览、命名并保存（多人格持久化，内部仍称 Persona）。
 */
@HiltViewModel
class TrainPersonaViewModel @Inject constructor(
    private val referenceCardRepository: ReferenceCardRepository,
    private val trainPersonaUseCase: TrainPersonaUseCase,
    private val preferencesRepository: PreferencesRepository
) : ViewModel() {

    /** 可训练参考卡全量列表 */
    private val _cards = MutableStateFlow<List<CharacterCard>>(emptyList())
    val cards: StateFlow<List<CharacterCard>> = _cards

    /** 已选卡片 uuid 集合 */
    private val _selected = MutableStateFlow<Set<String>>(emptySet())
    val selected: StateFlow<Set<String>> = _selected

    /** 搜索关键字（名称/描述/tags 过滤） */
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    /** 训练配置 */
    private val _config = MutableStateFlow(
        TrainPersonaConfig(modelId = "", contextBudget = 1_000_000)
    )
    val config: StateFlow<TrainPersonaConfig> = _config.asStateFlow()

    /** 可选的模型配置组 */
    private val _modelConfigs = MutableStateFlow<List<ModelConfig>>(emptyList())
    val modelConfigs: StateFlow<List<ModelConfig>> = _modelConfigs.asStateFlow()

    /** 训练状态 */
    private val _trainState = MutableStateFlow<TrainUiState>(TrainUiState.Idle)
    val trainState: StateFlow<TrainUiState> = _trainState

    /** 训练完成的待保存提示词 */
    private val _resultPrompt = MutableStateFlow<String?>(null)
    val resultPrompt: StateFlow<String?> = _resultPrompt

    private var trainJob: kotlinx.coroutines.Job? = null

    init {
        loadCards()
        viewModelScope.launch {
            runCatching {
                val configs = preferencesRepository.modelConfigs.first()
                val activeId = preferencesRepository.activeModelConfigId.first()
                _modelConfigs.value = configs
                val initial = configs.firstOrNull {
                    it.id == activeId && it.baseUrl.isNotBlank() && it.apiKey.isNotBlank()
                } ?: configs.firstOrNull()
                if (initial != null && _config.value.modelId.isBlank()) {
                    _config.update { it.copy(modelId = initial.id) }
                }
            }
        }
        viewModelScope.launch {
            runCatching {
                preferencesRepository.modelConfigs.collect { list ->
                    _modelConfigs.value = list
                    if (_config.value.modelId.isBlank()) {
                        val initial = list.firstOrNull {
                            it.baseUrl.isNotBlank() && it.apiKey.isNotBlank()
                        }
                        if (initial != null) {
                            _config.update { it.copy(modelId = initial.id) }
                        }
                    }
                }
            }
        }
    }

    fun loadCards() {
        viewModelScope.launch {
            _cards.value = referenceCardRepository.getAllReferenceCards(300, 0).getOrNull().orEmpty()
        }
    }

    /** 单选/取消（多选）；点击同一项再次点击即取消，不残留旧选择 */
    fun toggleSelect(uuid: String) {
        _selected.update { sel ->
            if (uuid in sel) sel - uuid else sel + uuid
        }
    }

    /** 清空全部选择（用于“取消选择”操作） */
    fun clearSelection() {
        _selected.value = emptySet()
    }

    fun setQuery(q: String) {
        _query.value = q
    }

    /** 更新训练配置 */
    fun updateConfig(update: TrainPersonaConfig.() -> TrainPersonaConfig) {
        _config.update { it.update() }
    }

    /** 当前过滤后的卡片列表（按名称/描述/标签匹配） */
    fun filteredCards(): List<CharacterCard> {
        val q = _query.value.trim().lowercase()
        if (q.isEmpty()) return _cards.value
        return _cards.value.filter { c ->
            c.name.lowercase().contains(q) ||
                c.description.lowercase().contains(q) ||
                c.tags.any { it.lowercase().contains(q) }
        }
    }

    fun startTraining() {
        if (_trainState.value is TrainUiState.Running) return

        val currentConfig = _config.value
        if (currentConfig.modelId.isBlank()) {
            _trainState.value = TrainUiState.Error("请先选择训练模型", emptyMap())
            return
        }
        if (currentConfig.selectedDimensions.isEmpty()) {
            _trainState.value = TrainUiState.Error("请至少选择一个分析维度", emptyMap())
            return
        }

        val uuids = _selected.value.toList()
        val cards = uuids.mapNotNull { uid -> _cards.value.firstOrNull { it.uuid == uid } }
        if (cards.isEmpty()) return

        trainJob?.cancel()
        trainJob = viewModelScope.launch {
            _resultPrompt.value = null
            _trainState.value = TrainUiState.Running(
                TrainPhase.AnalyzingDimension(TrainDimension.entries.first(), 1, "准备"),
                0f
            )
            trainPersonaUseCase(
                cards = cards,
                config = currentConfig,
                onProgress = { phase, progress ->
                    _trainState.value = TrainUiState.Running(phase, progress)
                }
            ).onSuccess { prompt ->
                _resultPrompt.value = prompt
                _trainState.value = TrainUiState.Result(prompt)
            }.onFailure { e ->
                val message = when (e) {
                    is ContextOverflowException ->
                        e.message ?: "内容超过上下文限制（维度：${e.dimension.label}）"
                    is TrainingFailedException ->
                        e.message ?: "训练失败"
                    else -> "训练失败：${e.message}"
                }
                val partial = when (e) {
                    is ContextOverflowException -> e.partialResults
                    is TrainingFailedException -> e.partialResults
                    else -> emptyMap()
                }
                _trainState.value = TrainUiState.Error(message, partial)
            }
        }
    }

    fun cancelTraining() {
        trainJob?.cancel()
        trainJob = null
        _trainState.value = TrainUiState.Idle
    }

    /** 重置为初始态（回到选卡界面） */
    fun reset() {
        trainJob?.cancel()
        trainJob = null
        _trainState.value = TrainUiState.Idle
        _resultPrompt.value = null
    }

    /**
     * 保存灵魂（写入 DataStore 多人格列表），返回新人格 id；保存后自动启用。
     */
    suspend fun savePersona(name: String, description: String, prompt: String): String? {
        if (name.isBlank() || prompt.isBlank()) return null
        val persona = com.kirakira.kiracode.domain.model.Persona(
            id = java.util.UUID.randomUUID().toString(),
            name = name.trim(),
            description = description.trim(),
            prompt = prompt.trim()
        )
        val current = com.kirakira.kiracode.domain.model.PersonaCodec
            .decodeList(preferencesRepository.personasJson.first())
        val next = (current + persona).filter { it.prompt.isNotBlank() }.distinctBy { it.id }
        runCatching { preferencesRepository.setPersonasJson(com.kirakira.kiracode.domain.model.PersonaCodec.encodeList(next)) }
        runCatching { preferencesRepository.setActivePersonaId(persona.id) }
        return persona.id
    }
}