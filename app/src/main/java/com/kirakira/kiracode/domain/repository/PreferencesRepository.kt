package com.kirakira.kiracode.domain.repository

import com.kirakira.kiracode.domain.model.ModelConfig
import com.kirakira.kiracode.domain.model.RegexGenerationMode
import kotlinx.coroutines.flow.Flow

/**
 * 用户偏好设置仓库接口 - Domain 层定义，Data 层用 DataStore 实现
 */
interface PreferencesRepository {
    // API Key
    val apiKey: Flow<String?>
    suspend fun setApiKey(key: String?)
    suspend fun clearApiKey()

    // API 提供方
    val apiProvider: Flow<String?>
    suspend fun setApiProvider(provider: String?)

    // API Base URL
    val apiBaseUrl: Flow<String?>
    suspend fun setApiBaseUrl(baseUrl: String?)

    // 模型
    val selectedModel: Flow<String?>
    suspend fun setSelectedModel(model: String?)

    // API 模式
    val apiMode: Flow<String>
    suspend fun setApiMode(mode: String)

    // 默认温度
    val defaultTemperature: Flow<Float>
    suspend fun setDefaultTemperature(temp: Float)

    // 默认最大 Token
    val defaultMaxTokens: Flow<Int>
    suspend fun setDefaultMaxTokens(tokens: Int)

    // Agent
    val agentEnabled: Flow<Boolean>
    suspend fun setAgentEnabled(enabled: Boolean)

    val agentCount: Flow<Int>
    suspend fun setAgentCount(count: Int)

    // 是否自动保存草稿
    val autoSaveDrafts: Flow<Boolean>
    suspend fun setAutoSaveDrafts(enabled: Boolean)

    // 最近使用的风格模板
    val lastUsedStyleTemplate: Flow<String?>
    suspend fun setLastUsedStyleTemplate(template: String?)

    // 主题模式：0=跟随系统，1=浅色，2=深色
    val themeMode: Flow<Int>
    suspend fun setThemeMode(mode: Int)

    // 语言
    val language: Flow<String?>
    suspend fun setLanguage(lang: String?)

    // 是否首次启动
    val isFirstLaunch: Flow<Boolean>
    suspend fun setFirstLaunchCompleted()

    // 免责声明是否已同意（首次进入需同意，默认 false）
    val disclaimerAccepted: Flow<Boolean>
    suspend fun setDisclaimerAccepted(accepted: Boolean)

    /** 首次引导完成 + 免责声明同意，同一事务写入，避免出现只写一半的状态 */
    suspend fun acceptFirstRunTerms()

    // 导出目录偏好
    val exportDirectory: Flow<String?>
    suspend fun setExportDirectory(path: String?)

    // 自定义提示词（用户可编辑的角色卡创作规则，作为附加系统提示词注入）
    // P0：三态语义——未配置/已停用均不注入；默认模板只能由用户在设置页主动启用
    val customPromptState: Flow<com.kirakira.kiracode.domain.model.CustomPromptState>
    suspend fun setCustomPromptText(text: String)
    suspend fun setCustomPromptEnabled(enabled: Boolean)

    // V1.0 多套自定义提示词（每套有名称与内容；当前启用的一套）
    val customPromptSetsJson: Flow<String>
    suspend fun setCustomPromptSetsJson(json: String)
    val activeCustomPromptSetId: Flow<String?>
    suspend fun setActiveCustomPromptSetId(id: String?)

    // 固定系统提示词开关（内置 SillyTavern 格式规范，默认开启）
    val builtinSystemPromptEnabled: Flow<Boolean>
    suspend fun setBuiltinSystemPromptEnabled(enabled: Boolean)

    // V3.0 联网搜索开关（AI 按需检索外部资料，默认关闭）
    val webSearchEnabled: Flow<Boolean>
    suspend fun setWebSearchEnabled(enabled: Boolean)

    // V1.0.0 流式输出开关（AI 回复逐字显示，默认开启；关闭后一次性返回完整回复）
    val streamingEnabled: Flow<Boolean>
    suspend fun setStreamingEnabled(enabled: Boolean)

    // V1.0 制卡师开关（关键决策询问用户，可与任意模式共存，默认开启）
    val cardMasterEnabled: Flow<Boolean>
    suspend fun setCardMasterEnabled(enabled: Boolean)

    // 正则生成模式（默认语义折叠）
    val regexGenerationMode: Flow<RegexGenerationMode>
    suspend fun setRegexGenerationMode(mode: RegexGenerationMode)

    // 解禁工具调用次数（大型角色卡重构用；默认关闭，解禁后硬上限 200 轮）
    val unlimitedToolCalls: Flow<Boolean>
    suspend fun setUnlimitedToolCalls(enabled: Boolean)

    // V1.0 灵魂列表（JSON）与当前启用灵魂 ID
    val personasJson: Flow<String>
    suspend fun setPersonasJson(json: String)
    val activePersonaId: Flow<String?>
    suspend fun setActivePersonaId(id: String?)

    // V1.0.0 思考强度档位（持久化）与探测缓存
    val reasoningEffortPref: Flow<String?>
    suspend fun setReasoningEffortPref(effort: String?)
    val reasoningProbeJson: Flow<String>
    suspend fun setReasoningProbeJson(json: String)

    // Token 估算模式："char"（按字符估算，默认）| "precise"（使用 API 返回的真实 token 数）
    val tokenEstimateMode: Flow<String>
    suspend fun setTokenEstimateMode(mode: String)

    // V1.0.0 上下文窗口管理
    val contextWindowTokens: Flow<Int>
    suspend fun setContextWindowTokens(tokens: Int)
    val contextSummarizeThreshold: Flow<Float>
    suspend fun setContextSummarizeThreshold(threshold: Float)

    // V2 Beta 会话恢复：上一次活跃会话 ID
    val lastSessionId: Flow<Long?>
    suspend fun setLastSessionId(id: Long?)

    // 清空所有偏好
    suspend fun clearAll()

    // 多模型配置列表（JSON 序列化存储）
    val modelConfigs: Flow<List<ModelConfig>>
    suspend fun setModelConfigs(configs: List<ModelConfig>)

    // 当前激活的模型配置 ID
    val activeModelConfigId: Flow<String?>
    suspend fun setActiveModelConfigId(id: String?)
}