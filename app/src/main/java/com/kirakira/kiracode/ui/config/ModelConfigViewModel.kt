package com.kirakira.kiracode.ui.config

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kirakira.kiracode.domain.model.ModelConfig
import com.kirakira.kiracode.domain.repository.PreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import org.json.JSONObject

/**
 * 模型配置 UI 状态
 */
data class ModelConfigUiState(
    val configs: List<ModelConfig> = emptyList(),
    val activeId: String? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val fetchInProgress: String? = null // 正在拉取模型的 config id
)

/**
 * 模型配置 ViewModel
 *
 * 管理多个 Base URL/API Key 配置，拉取模型列表，选择默认模型，持久化。
 */
@HiltViewModel
class ModelConfigViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ModelConfigUiState())
    val uiState: StateFlow<ModelConfigUiState> = combine(
        preferencesRepository.modelConfigs,
        preferencesRepository.activeModelConfigId,
        _uiState
    ) { configs, activeId, localState ->
        localState.copy(configs = configs, activeId = activeId)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ModelConfigUiState())

    /**
     * 新增模型配置
     */
    fun addConfig(name: String, baseUrl: String, apiKey: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val newConfig = ModelConfig(
                    id = "config_${System.currentTimeMillis()}",
                    name = name.ifBlank { "默认配置" },
                    baseUrl = baseUrl.trim().trimEnd('/'),
                    apiKey = apiKey.trim()
                )
                val current = preferencesRepository.modelConfigs.first().toMutableList()
                if (current.isEmpty()) {
                    // 首个配置自动激活
                    current.add(newConfig.copy(isActive = true))
                    preferencesRepository.setActiveModelConfigId(newConfig.id)
                    preferencesRepository.setModelConfigs(current)
                    // 同步到旧的单配置字段（兼容）
                    preferencesRepository.setApiBaseUrl(newConfig.baseUrl)
                    preferencesRepository.setApiKey(newConfig.apiKey)
                } else {
                    current.add(newConfig)
                    preferencesRepository.setModelConfigs(current)
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "新增失败: ${e.message}") }
            }
        }
    }

    /**
     * 更新模型配置
     */
    fun updateConfig(config: ModelConfig) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val current = preferencesRepository.modelConfigs.first().toMutableList()
                val idx = current.indexOfFirst { it.id == config.id }
                if (idx >= 0) {
                    current[idx] = config
                    preferencesRepository.setModelConfigs(current)
                    if (config.isActive) {
                        preferencesRepository.setApiBaseUrl(config.baseUrl)
                        preferencesRepository.setApiKey(config.apiKey)
                        if (config.selectedModel != null) {
                            preferencesRepository.setSelectedModel(config.selectedModel)
                        }
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "更新失败: ${e.message}") }
            }
        }
    }

    /**
     * 删除模型配置
     */
    fun deleteConfig(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val current = preferencesRepository.modelConfigs.first().toMutableList()
                current.removeAll { it.id == id }
                preferencesRepository.setModelConfigs(current)
                if (_uiState.value.activeId == id) {
                    val newActive = current.firstOrNull()?.id
                    preferencesRepository.setActiveModelConfigId(newActive)
                    newActive?.let { aid ->
                        current.find { it.id == aid }?.let { cfg ->
                            preferencesRepository.setApiBaseUrl(cfg.baseUrl)
                            preferencesRepository.setApiKey(cfg.apiKey)
                        }
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "删除失败: ${e.message}") }
            }
        }
    }

    /**
     * 设置激活的模型配置
     */
    fun setActiveConfig(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val current = preferencesRepository.modelConfigs.first()
                val target = current.find { it.id == id } ?: return@launch
                val updated = current.map { it.copy(isActive = it.id == id) }
                preferencesRepository.setModelConfigs(updated)
                preferencesRepository.setActiveModelConfigId(id)
                preferencesRepository.setApiBaseUrl(target.baseUrl)
                preferencesRepository.setApiKey(target.apiKey)
                target.selectedModel?.let { preferencesRepository.setSelectedModel(it) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "切换失败: ${e.message}") }
            }
        }
    }

    /**
     * 为指定配置拉取模型列表（调用 OpenAI 兼容 /v1/models 接口）
     */
    fun fetchModels(configId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(fetchInProgress = configId, error = null) }
            try {
                val current = preferencesRepository.modelConfigs.first()
                val target = current.find { it.id == configId } ?: run {
                    _uiState.update { it.copy(fetchInProgress = null) }
                    return@launch
                }
                val models = fetchModelList(target.baseUrl, target.apiKey)
                val updated = current.map { cfg ->
                    if (cfg.id == configId) cfg.copy(models = models) else cfg
                }
                preferencesRepository.setModelConfigs(updated)
                _uiState.update { it.copy(fetchInProgress = null) }
            } catch (e: Exception) {
                _uiState.update { it.copy(fetchInProgress = null, error = "拉取失败: ${e.message}") }
            }
        }
    }

    /**
     * 为指定配置选择默认模型
     */
    fun selectModel(configId: String, model: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val current = preferencesRepository.modelConfigs.first().toMutableList()
                val idx = current.indexOfFirst { it.id == configId }
                if (idx >= 0) {
                    current[idx] = current[idx].copy(selectedModel = model)
                    preferencesRepository.setModelConfigs(current)
                    if (current[idx].isActive) {
                        preferencesRepository.setSelectedModel(model)
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "选择失败: ${e.message}") }
            }
        }
    }

    /**
     * 调用 /v1/models 接口拉取模型列表
     */
    private suspend fun fetchModelList(baseUrl: String, apiKey: String): List<String> = withContext(Dispatchers.IO) {
        // 规范化 Base URL：去尾斜杠；若已包含 /v1 则不再重复拼接
        val trimmed = baseUrl.trim().trimEnd('/')
        val url = if (trimmed.endsWith("/v1")) "$trimmed/models" else "$trimmed/v1/models"
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15000
            readTimeout = 15000
            if (apiKey.isNotBlank()) {
                setRequestProperty("Authorization", "Bearer $apiKey")
            }
            setRequestProperty("Content-Type", "application/json")
        }
        try {
            val responseCode = conn.responseCode
            if (responseCode != 200) {
                val errBody = runCatching {
                    conn.errorStream?.bufferedReader()?.use { it.readText() }?.take(200)
                }.getOrNull()
                throw Exception("HTTP $responseCode ${errBody ?: ""}".trim())
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            val data = json.optJSONArray("data") ?: return@withContext emptyList()
            val models = mutableListOf<String>()
            for (i in 0 until data.length()) {
                val item = data.optJSONObject(i)
                val id = item?.optString("id")
                if (!id.isNullOrBlank()) models.add(id)
            }
            models.sorted()
        } finally {
            conn.disconnect()
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    // ============ V2.1 高级选项（自动保存：改即生效，无保存按钮） ============

    val temperature: kotlinx.coroutines.flow.Flow<Float> = preferencesRepository.defaultTemperature
    val maxTokens: kotlinx.coroutines.flow.Flow<Int> = preferencesRepository.defaultMaxTokens

    fun setTemperature(value: Float) {
        viewModelScope.launch { preferencesRepository.setDefaultTemperature(value.coerceIn(0f, 2f)) }
    }

    fun setMaxTokens(value: Int) {
        viewModelScope.launch { preferencesRepository.setDefaultMaxTokens(value.coerceIn(512, 2000000)) }
    }

}

