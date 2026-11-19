package com.kirakira.kiracode.data.local.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import com.kirakira.kiracode.domain.repository.PreferencesRepository
import com.kirakira.kiracode.domain.model.ModelConfig
import com.kirakira.kiracode.domain.model.RegexGenerationMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 顶层 DataStore 委托——必须声明在文件顶层（包级），
 * 生命周期跟随 Application。其他代码通过 PreferencesRepositoryImpl 访问。
 */
private val Context.kiraCodeDataStore: DataStore<Preferences> by preferencesDataStore("kiracode_preferences")

/**
 * Preferences 数据键定义
 */
object PreferencesKeys {
    // API 配置
    val API_KEY = stringPreferencesKey("api_key")
    val API_PROVIDER = stringPreferencesKey("api_provider")
    val API_BASE_URL = stringPreferencesKey("api_base_url")
    val SELECTED_MODEL = stringPreferencesKey("selected_model")
    val API_MODE = stringPreferencesKey("api_mode") // "mock" | "real"
    
    // 默认生成参数
    val DEFAULT_TEMPERATURE = floatPreferencesKey("default_temperature")
    val DEFAULT_MAX_TOKENS = intPreferencesKey("default_max_tokens")
    
    // Agent 配置
    val AGENT_ENABLED = booleanPreferencesKey("agent_enabled")
    val AGENT_COUNT = intPreferencesKey("agent_count")
    
    // 界面偏好
    val AUTO_SAVE_DRAFTS = booleanPreferencesKey("auto_save_drafts")
    val LAST_USED_STYLE_TEMPLATE = stringPreferencesKey("last_used_style_template")
    val THEME_MODE = intPreferencesKey("theme_mode") // 0=system, 1=light, 2=dark
    val LANGUAGE = stringPreferencesKey("language")
    val IS_FIRST_LAUNCH = booleanPreferencesKey("is_first_launch")
    val DISCLAIMER_ACCEPTED = booleanPreferencesKey("disclaimer_accepted")
    val LAST_EXPORT_DIR = stringPreferencesKey("last_export_dir")

    // 提示词与 Token 配置
val CUSTOM_PROMPT = stringPreferencesKey("custom_prompt")
val CUSTOM_PROMPT_TEXT = stringPreferencesKey("custom_prompt_text")
val CUSTOM_PROMPT_ENABLED = booleanPreferencesKey("custom_prompt_enabled")
val BUILTIN_SYSTEM_PROMPT_ENABLED = booleanPreferencesKey("builtin_system_prompt_enabled")
val WEB_SEARCH_ENABLED = booleanPreferencesKey("web_search_enabled")
val STREAMING_ENABLED = booleanPreferencesKey("streaming_enabled")
val CARD_MASTER_ENABLED = booleanPreferencesKey("card_master_enabled")
val REGEX_GENERATION_MODE = stringPreferencesKey("regex_generation_mode")
val UNLIMITED_TOOL_CALLS = booleanPreferencesKey("unlimited_tool_calls")
val PERSONAS_JSON = stringPreferencesKey("personas_json")
val ACTIVE_PERSONA_ID = stringPreferencesKey("active_persona_id")
val REASONING_EFFORT_PREF = stringPreferencesKey("reasoning_effort_pref")
val REASONING_PROBE_JSON = stringPreferencesKey("reasoning_probe_json")
    val TOKEN_ESTIMATE_MODE = stringPreferencesKey("token_estimate_mode") // "char" | "precise"

    // V1.0.0 上下文窗口管理
    val CONTEXT_WINDOW_TOKENS = intPreferencesKey("context_window_tokens") // 默认 128000，最大 2000000
    val CONTEXT_SUMMARIZE_THRESHOLD = floatPreferencesKey("context_summarize_threshold") // 默认 0.8f

    // 多模型配置
    val MODEL_CONFIGS = stringPreferencesKey("model_configs") // JSON 序列化的 List<ModelConfig>
    val ACTIVE_MODEL_CONFIG_ID = stringPreferencesKey("active_model_config_id")

    // V2 Beta 多套自定义提示词
    val CUSTOM_PROMPT_SETS_JSON = stringPreferencesKey("custom_prompt_sets_json")
    val ACTIVE_CUSTOM_PROMPT_SET_ID = stringPreferencesKey("active_custom_prompt_set_id")

    // V2 Beta 会话恢复
    val LAST_SESSION_ID = stringPreferencesKey("last_session_id")
}

/**
 * DataStore Preferences 实现类
 */
@Singleton
class PreferencesRepositoryImpl @Inject constructor(
    private val context: Context
) : PreferencesRepository {

    private val dataStore: DataStore<Preferences> get() = context.kiraCodeDataStore

    // ============ API Key ==========
    override val apiKey: Flow<String?> = dataStore.data
        .map { it[PreferencesKeys.API_KEY] }
        .catch { emit(null) }

    override suspend fun setApiKey(key: String?) {
        dataStore.edit {
            if (key.isNullOrBlank()) it.remove(PreferencesKeys.API_KEY)
            else it[PreferencesKeys.API_KEY] = key
        }
    }

    override suspend fun clearApiKey() { dataStore.edit { it.remove(PreferencesKeys.API_KEY) } }

    // ============ API Provider ==========
    override val apiProvider: Flow<String?> = dataStore.data
        .map { it[PreferencesKeys.API_PROVIDER] }
        .catch { emit(null) }

    override suspend fun setApiProvider(provider: String?) {
        dataStore.edit {
            if (provider.isNullOrBlank()) it.remove(PreferencesKeys.API_PROVIDER)
            else it[PreferencesKeys.API_PROVIDER] = provider
        }
    }

    // ============ API Base URL ==========
    override val apiBaseUrl: Flow<String?> = dataStore.data
        .map { it[PreferencesKeys.API_BASE_URL] }
        .catch { emit(null) }

    override suspend fun setApiBaseUrl(baseUrl: String?) {
        dataStore.edit {
            if (baseUrl.isNullOrBlank()) it.remove(PreferencesKeys.API_BASE_URL)
            else it[PreferencesKeys.API_BASE_URL] = baseUrl
        }
    }

    // ============ Selected Model ==========
    override val selectedModel: Flow<String?> = dataStore.data
        .map { it[PreferencesKeys.SELECTED_MODEL] }
        .catch { emit(null) }

    override suspend fun setSelectedModel(model: String?) {
        dataStore.edit {
            if (model.isNullOrBlank()) it.remove(PreferencesKeys.SELECTED_MODEL)
            else it[PreferencesKeys.SELECTED_MODEL] = model
        }
    }

    // ============ API Mode (mock/real) ==========
    override val apiMode: Flow<String> = dataStore.data
        .map { it[PreferencesKeys.API_MODE] ?: "real" }
        .catch { emit("real") }

    override suspend fun setApiMode(mode: String) {
        dataStore.edit {
            it[PreferencesKeys.API_MODE] = mode
        }
    }

    // ============ Temperature ==========
    override val defaultTemperature: Flow<Float> = dataStore.data
        .map { it[PreferencesKeys.DEFAULT_TEMPERATURE] ?: 0.8f }
        .catch { emit(0.8f) }

    override suspend fun setDefaultTemperature(temp: Float) {
        dataStore.edit {
            it[PreferencesKeys.DEFAULT_TEMPERATURE] = temp
        }
    }

    // ============ Max Tokens ==========
    override val defaultMaxTokens: Flow<Int> = dataStore.data
        .map { it[PreferencesKeys.DEFAULT_MAX_TOKENS] ?: 4096 }
        .catch { emit(4096) }

    override suspend fun setDefaultMaxTokens(tokens: Int) {
        dataStore.edit {
            it[PreferencesKeys.DEFAULT_MAX_TOKENS] = tokens
        }
    }

    // ============ Agent ==========
    override val agentEnabled: Flow<Boolean> = dataStore.data
        .map { it[PreferencesKeys.AGENT_ENABLED] ?: false }
        .catch { emit(false) }

    override suspend fun setAgentEnabled(enabled: Boolean) {
        dataStore.edit {
            it[PreferencesKeys.AGENT_ENABLED] = enabled
        }
    }

    override val agentCount: Flow<Int> = dataStore.data
        .map { it[PreferencesKeys.AGENT_COUNT] ?: 3 }
        .catch { emit(3) }

    override suspend fun setAgentCount(count: Int) {
        dataStore.edit {
            it[PreferencesKeys.AGENT_COUNT] = count.coerceIn(2, 5)
        }
    }

    // ============ Auto Save Drafts ==========
    override val autoSaveDrafts: Flow<Boolean> = dataStore.data
        .map { it[PreferencesKeys.AUTO_SAVE_DRAFTS] ?: true }
        .catch { emit(true) }

    override suspend fun setAutoSaveDrafts(enabled: Boolean) {
        dataStore.edit {
            it[PreferencesKeys.AUTO_SAVE_DRAFTS] = enabled
        }
    }

    // ============ Last Used Style Template ==========
    override val lastUsedStyleTemplate: Flow<String?> = dataStore.data
        .map { it[PreferencesKeys.LAST_USED_STYLE_TEMPLATE] }
        .catch { emit(null) }

    override suspend fun setLastUsedStyleTemplate(template: String?) {
        dataStore.edit {
            if (template.isNullOrBlank()) it.remove(PreferencesKeys.LAST_USED_STYLE_TEMPLATE)
            else it[PreferencesKeys.LAST_USED_STYLE_TEMPLATE] = template
        }
    }

    // ============ Theme Mode ==========
    override val themeMode: Flow<Int> = dataStore.data
        .map { it[PreferencesKeys.THEME_MODE] ?: 0 }
        .catch { emit(0) }

    override suspend fun setThemeMode(mode: Int) {
        dataStore.edit {
            it[PreferencesKeys.THEME_MODE] = mode
        }
    }

    // ============ Language ==========
    override val language: Flow<String?> = dataStore.data
        .map { it[PreferencesKeys.LANGUAGE] }
        .catch { emit(null) }

    override suspend fun setLanguage(lang: String?) {
        dataStore.edit {
            if (lang.isNullOrBlank()) it.remove(PreferencesKeys.LANGUAGE)
            else it[PreferencesKeys.LANGUAGE] = lang
        }
    }

    // ============ First Launch ==========
    override val isFirstLaunch: Flow<Boolean> = dataStore.data
        .map { it[PreferencesKeys.IS_FIRST_LAUNCH] ?: true }
        .catch { emit(true) }

    override suspend fun setFirstLaunchCompleted() {
        dataStore.edit {
            it[PreferencesKeys.IS_FIRST_LAUNCH] = false
        }
    }

    // ============ Disclaimer Accepted ==========
    override val disclaimerAccepted: Flow<Boolean> = dataStore.data
        .map { it[PreferencesKeys.DISCLAIMER_ACCEPTED] ?: false }
        .catch { emit(false) }

    override suspend fun setDisclaimerAccepted(accepted: Boolean) {
        dataStore.edit {
            it[PreferencesKeys.DISCLAIMER_ACCEPTED] = accepted
        }
    }

    override suspend fun acceptFirstRunTerms() {
        dataStore.edit {
            it[PreferencesKeys.IS_FIRST_LAUNCH] = false
            it[PreferencesKeys.DISCLAIMER_ACCEPTED] = true
        }
    }

    // ============ Export Directory ==========
    override val exportDirectory: Flow<String?> = dataStore.data
        .map { it[PreferencesKeys.LAST_EXPORT_DIR] }
        .catch { emit(null) }

    override suspend fun setExportDirectory(path: String?) {
        dataStore.edit {
            if (path.isNullOrBlank()) it.remove(PreferencesKeys.LAST_EXPORT_DIR)
            else it[PreferencesKeys.LAST_EXPORT_DIR] = path
        }
    }

    // ============ Custom Prompt（P0 三态：未配置/停用/启用；默认模板不再自动注入） ==========
    override val customPromptState: Flow<com.kirakira.kiracode.domain.model.CustomPromptState> = dataStore.data
        .map { prefs ->
            com.kirakira.kiracode.domain.model.CustomPromptState.from(
                enabled = prefs[PreferencesKeys.CUSTOM_PROMPT_ENABLED],
                newText = prefs[PreferencesKeys.CUSTOM_PROMPT_TEXT],
                legacyText = prefs[PreferencesKeys.CUSTOM_PROMPT]
            )
        }
        .catch { emit(com.kirakira.kiracode.domain.model.CustomPromptState.NotConfigured) }

    override suspend fun setCustomPromptText(text: String) {
        dataStore.edit {
            if (text.isBlank()) it.remove(PreferencesKeys.CUSTOM_PROMPT_TEXT)
            else it[PreferencesKeys.CUSTOM_PROMPT_TEXT] = text
        }
    }

    override suspend fun setCustomPromptEnabled(enabled: Boolean) {
        dataStore.edit {
            it[PreferencesKeys.CUSTOM_PROMPT_ENABLED] = enabled
        }
    }

    // ============ 多套自定义提示词 (V2 Beta) ============
    private val customPromptSetJsonParser = Json { ignoreUnknownKeys = true }

    override val customPromptSetsJson: Flow<String> = dataStore.data
        .map { it[PreferencesKeys.CUSTOM_PROMPT_SETS_JSON] ?: "[]" }
        .catch { emit("[]") }

    override suspend fun setCustomPromptSetsJson(json: String) {
        dataStore.edit { it[PreferencesKeys.CUSTOM_PROMPT_SETS_JSON] = json }
    }

    override val activeCustomPromptSetId: Flow<String?> = dataStore.data
        .map { it[PreferencesKeys.ACTIVE_CUSTOM_PROMPT_SET_ID] }
        .catch { emit(null) }

    override suspend fun setActiveCustomPromptSetId(id: String?) {
        dataStore.edit {
            if (id == null) it.remove(PreferencesKeys.ACTIVE_CUSTOM_PROMPT_SET_ID)
            else it[PreferencesKeys.ACTIVE_CUSTOM_PROMPT_SET_ID] = id
        }
    }

    // ============ 会话恢复 (V2 Beta) ============
    override val lastSessionId: Flow<Long?> = dataStore.data
        .map { it[PreferencesKeys.LAST_SESSION_ID]?.toLongOrNull() }
        .catch { emit(null) }

    override suspend fun setLastSessionId(id: Long?) {
        dataStore.edit {
            if (id == null) it.remove(PreferencesKeys.LAST_SESSION_ID)
            else it[PreferencesKeys.LAST_SESSION_ID] = id.toString()
        }
    }

    // ============ Builtin System Prompt ==========
    override val builtinSystemPromptEnabled: Flow<Boolean> = dataStore.data
        .map { it[PreferencesKeys.BUILTIN_SYSTEM_PROMPT_ENABLED] ?: true }
        .catch { emit(true) }

    override suspend fun setBuiltinSystemPromptEnabled(enabled: Boolean) {
        dataStore.edit {
            it[PreferencesKeys.BUILTIN_SYSTEM_PROMPT_ENABLED] = enabled
        }
    }

    // ============ Web Search (V3.0) ==========
    override val webSearchEnabled: Flow<Boolean> = dataStore.data
        .map { it[PreferencesKeys.WEB_SEARCH_ENABLED] ?: false }
        .catch { emit(false) }

    override suspend fun setWebSearchEnabled(enabled: Boolean) {
        dataStore.edit {
            it[PreferencesKeys.WEB_SEARCH_ENABLED] = enabled
        }
    }

    // ============ Streaming Output (V1.0.0) ==========
    override val streamingEnabled: Flow<Boolean> = dataStore.data
        .map { it[PreferencesKeys.STREAMING_ENABLED] ?: true }
        .catch { emit(true) }

    override suspend fun setStreamingEnabled(enabled: Boolean) {
        dataStore.edit {
            it[PreferencesKeys.STREAMING_ENABLED] = enabled
        }
    }

    // ============ Card Master Toggle & Personas (V1.0) ==========
    override val cardMasterEnabled: Flow<Boolean> = dataStore.data
        .map { it[PreferencesKeys.CARD_MASTER_ENABLED] ?: true }
        .catch { emit(true) }

    override suspend fun setCardMasterEnabled(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.CARD_MASTER_ENABLED] = enabled }
    }

    override val regexGenerationMode: Flow<RegexGenerationMode> = dataStore.data
        .map { RegexGenerationMode.fromName(it[PreferencesKeys.REGEX_GENERATION_MODE]) }
        .catch { emit(RegexGenerationMode.DEFAULT) }

    override suspend fun setRegexGenerationMode(mode: RegexGenerationMode) {
        dataStore.edit { it[PreferencesKeys.REGEX_GENERATION_MODE] = mode.name }
    }

    // ============ Unlimited Tool Calls ==========
    override val unlimitedToolCalls: Flow<Boolean> = dataStore.data
        .map { it[PreferencesKeys.UNLIMITED_TOOL_CALLS] ?: false }
        .catch { emit(false) }

    override suspend fun setUnlimitedToolCalls(enabled: Boolean) {
        dataStore.edit { it[PreferencesKeys.UNLIMITED_TOOL_CALLS] = enabled }
    }

    override val personasJson: Flow<String> = dataStore.data
        .map { it[PreferencesKeys.PERSONAS_JSON] ?: "[]" }
        .catch { emit("[]") }

    override suspend fun setPersonasJson(json: String) {
        dataStore.edit { it[PreferencesKeys.PERSONAS_JSON] = json }
    }

    override val activePersonaId: Flow<String?> = dataStore.data
        .map { it[PreferencesKeys.ACTIVE_PERSONA_ID] }
        .catch { emit(null) }

    override suspend fun setActivePersonaId(id: String?) {
        dataStore.edit {
            if (id == null) it.remove(PreferencesKeys.ACTIVE_PERSONA_ID)
            else it[PreferencesKeys.ACTIVE_PERSONA_ID] = id
        }
    }

        override val reasoningEffortPref: Flow<String?> = dataStore.data
        .map { it[PreferencesKeys.REASONING_EFFORT_PREF] }
        .catch { emit(null) }

    override suspend fun setReasoningEffortPref(effort: String?) {
        dataStore.edit {
            if (effort == null) it.remove(PreferencesKeys.REASONING_EFFORT_PREF)
            else it[PreferencesKeys.REASONING_EFFORT_PREF] = effort
        }
    }

    override val reasoningProbeJson: Flow<String> = dataStore.data
        .map { it[PreferencesKeys.REASONING_PROBE_JSON] ?: "{}" }
        .catch { emit("{}") }

    override suspend fun setReasoningProbeJson(json: String) {
        dataStore.edit { it[PreferencesKeys.REASONING_PROBE_JSON] = json }
    }
// ============ Token Estimate Mode ==========
    override val tokenEstimateMode: Flow<String> = dataStore.data
        .map { it[PreferencesKeys.TOKEN_ESTIMATE_MODE] ?: "char" }
        .catch { emit("char") }

    override suspend fun setTokenEstimateMode(mode: String) {
        dataStore.edit {
            it[PreferencesKeys.TOKEN_ESTIMATE_MODE] = mode
        }
    }

    // ============ 上下文窗口管理 (V1.0.0) ==========
    override val contextWindowTokens: Flow<Int> = dataStore.data
        .map { it[PreferencesKeys.CONTEXT_WINDOW_TOKENS] ?: 128000 }
        .catch { emit(128000) }

    override suspend fun setContextWindowTokens(tokens: Int) {
        dataStore.edit {
            it[PreferencesKeys.CONTEXT_WINDOW_TOKENS] = tokens.coerceIn(1024, 2_000_000)
        }
    }

    override val contextSummarizeThreshold: Flow<Float> = dataStore.data
        .map { it[PreferencesKeys.CONTEXT_SUMMARIZE_THRESHOLD] ?: 0.8f }
        .catch { emit(0.8f) }

    override suspend fun setContextSummarizeThreshold(threshold: Float) {
        dataStore.edit {
            it[PreferencesKeys.CONTEXT_SUMMARIZE_THRESHOLD] = threshold.coerceIn(0.3f, 0.95f)
        }
    }

    // ============ Clear All ==========
    override suspend fun clearAll() { dataStore.edit { it.clear() } }

    // ============ Model Configs (多 Base/API) ==========
    private val modelConfigJson = Json { ignoreUnknownKeys = true }

    override val modelConfigs: Flow<List<ModelConfig>> = dataStore.data
        .map {
            val jsonStr = it[PreferencesKeys.MODEL_CONFIGS]
            if (jsonStr.isNullOrBlank()) emptyList()
            else try {
                modelConfigJson.decodeFromString(
                    kotlinx.serialization.builtins.ListSerializer(ModelConfig.serializer()),
                    jsonStr
                )
            } catch (e: Exception) { emptyList() }
        }
        .catch { emit(emptyList()) }

    override suspend fun setModelConfigs(configs: List<ModelConfig>) {
        dataStore.edit {
            it[PreferencesKeys.MODEL_CONFIGS] = modelConfigJson.encodeToString(
                kotlinx.serialization.builtins.ListSerializer(ModelConfig.serializer()),
                configs
            )
        }
    }

    override val activeModelConfigId: Flow<String?> = dataStore.data
        .map { it[PreferencesKeys.ACTIVE_MODEL_CONFIG_ID] }
        .catch { emit(null) }

    override suspend fun setActiveModelConfigId(id: String?) {
        dataStore.edit {
            if (id != null) it[PreferencesKeys.ACTIVE_MODEL_CONFIG_ID] = id
            else it.remove(PreferencesKeys.ACTIVE_MODEL_CONFIG_ID)
        }
    }
}