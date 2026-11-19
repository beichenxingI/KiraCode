package com.kirakira.kiracode.domain.usecase

import com.kirakira.kiracode.data.remote.real.RealApiService
import com.kirakira.kiracode.domain.exception.NetworkException
import com.kirakira.kiracode.domain.model.CharacterCard
import com.kirakira.kiracode.domain.model.GenerationRequest
import com.kirakira.kiracode.domain.model.GenerationResult
import com.kirakira.kiracode.domain.repository.ApiService
import com.kirakira.kiracode.domain.repository.PreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * 生成角色卡用例
 * 负责构建请求、调用 API、处理响应
 * 支持 Mock 和 Real 两种 API 模式
 *
 * @param apiService API 服务接口
 * @param realApiService 真实 API 服务 (用于流式调用和配置)
 * @param preferencesRepository 偏好设置仓库 (用于获取 API 配置)
 */
class GenerateCharacterCardUseCase @Inject constructor(
    private val apiService: ApiService,
    private val realApiService: RealApiService,
    private val preferencesRepository: PreferencesRepository,
    private val chatEngine: ChatEngine
) {

    /**
     * 执行生成操作 (非流式)
     * @param referenceCard 参考角色卡
     * @param newCharacterDescription 新角色描述
     * @param styleTemplate 可选风格模板
     * @return 生成结果
     */
    suspend operator fun invoke(
        referenceCard: CharacterCard,
        newCharacterDescription: String,
        styleTemplate: String? = null
    ): Result<CharacterCard> = withContext(Dispatchers.IO) {
        // 1. 获取 API 配置
        val apiMode = preferencesRepository.apiMode.first()
        val apiKey = preferencesRepository.apiKey.first()
        val apiProvider = preferencesRepository.apiProvider.first()
        val apiBaseUrl = preferencesRepository.apiBaseUrl.first()
        val selectedModel = preferencesRepository.selectedModel.first()
        val defaultTemperature = preferencesRepository.defaultTemperature.first()
        val defaultMaxTokens = preferencesRepository.defaultMaxTokens.first()

        // 2. 构建请求
        val request = GenerationRequest(
            referenceCard = referenceCard,
            newCharacterDescription = newCharacterDescription,
            styleTemplate = styleTemplate,
            model = selectedModel ?: "gpt-4o-mini",
            temperature = defaultTemperature,
            maxTokens = defaultMaxTokens,
            regexGenerationMode = preferencesRepository.regexGenerationMode.first()
        )

        // 3. 根据模式调用对应的 API
        return@withContext when (apiMode) {
            "real" -> {
                // 与聊天引擎统一：优先新多模型配置组，回退旧单键（避免独立生成页失效）
                val config = chatEngine.resolveActiveConfig()
                if (config == null) {
                    Result.failure(
                        NetworkException(
                            "请在「模型配置」中添加 Base URL 与 API Key 后重试",
                            errorCode = "MISSING_API_KEY"
                        )
                    )
                } else {
                    realApiService.generateCharacterCardWithConfig(request, config)
                        .fold(
                            onSuccess = { generationResult ->
                                when (generationResult) {
is GenerationResult.Success -> Result.success(
                                com.kirakira.kiracode.domain.model.KiraWatermark.apply(
                                    com.kirakira.kiracode.domain.prompt.RegexGenerationPrompts.applyDescriptionProtocol(
                                        generationResult.card,
                                        request.regexGenerationMode
                                    )
                                )
                            )
                                    is GenerationResult.Failure -> Result.failure(
                                        NetworkException(
                                            "生成失败: ${generationResult.error}",
                                            errorCode = "GENERATION_FAILED",
                                            rawResponse = generationResult.rawResponse
                                        )
                                    )
                                }
                            },
                            onFailure = { exception ->
                                Result.failure(
                                    NetworkException(
                                        "网络请求异常: ${exception.message}",
                                        errorCode = "NETWORK_ERROR",
                                        cause = exception
                                    )
                                )
                            }
                        )
                }
            }
            else -> { // "mock" 模式
                val result: Result<GenerationResult> = apiService.generateCharacterCard(request)
                result.fold(
                    onSuccess = { generationResult ->
                        when (generationResult) {
                            is GenerationResult.Success -> Result.success(
                                com.kirakira.kiracode.domain.prompt.RegexGenerationPrompts.applyDescriptionProtocol(
                                    generationResult.card,
                                    request.regexGenerationMode
                                )
                            )
                            is GenerationResult.Failure -> Result.failure(
                                NetworkException(
                                    "生成失败: ${generationResult.error}",
                                    errorCode = "GENERATION_FAILED",
                                    rawResponse = generationResult.rawResponse
                                )
                            )
                        }
                    },
                    onFailure = { exception ->
                        Result.failure(
                            NetworkException(
                                "网络请求异常: ${exception.message}",
                                errorCode = "NETWORK_ERROR",
                                cause = exception
                            )
                        )
                    }
                )
            }
        }
    }

    /**
     * 流式生成角色卡 (用于聊天界面逐字显示)
     * @param referenceCard 参考角色卡
     * @param newCharacterDescription 新角色描述
     * @param styleTemplate 可选风格模板
     * @return Flow 逐块发送内容片段
     */
    suspend fun generateStream(
        referenceCard: CharacterCard,
        newCharacterDescription: String,
        styleTemplate: String? = null
    ): kotlinx.coroutines.flow.Flow<Result<String>> {
        // 获取 API 配置
        val apiMode = preferencesRepository.apiMode.first()
        val apiKey = preferencesRepository.apiKey.first()
        val apiProvider = preferencesRepository.apiProvider.first()
        val apiBaseUrl = preferencesRepository.apiBaseUrl.first()
        val selectedModel = preferencesRepository.selectedModel.first()
        val defaultTemperature = preferencesRepository.defaultTemperature.first()
        val defaultMaxTokens = preferencesRepository.defaultMaxTokens.first()

        val request = GenerationRequest(
            referenceCard = referenceCard,
            newCharacterDescription = newCharacterDescription,
            styleTemplate = styleTemplate,
            model = selectedModel ?: "gpt-4o-mini",
            temperature = defaultTemperature,
            maxTokens = defaultMaxTokens,
            regexGenerationMode = preferencesRepository.regexGenerationMode.first()
        )

        return when (apiMode) {
            "real" -> {
                if (apiKey.isNullOrBlank()) {
                    kotlinx.coroutines.flow.flow {
                        emit(Result.failure(
                            NetworkException(
                                "真实 API 模式需要配置 API Key，请在设置中填写",
                                errorCode = "MISSING_API_KEY"
                            )
                        ))
                    }
                } else {
                    val baseUrl = apiBaseUrl ?: getBaseUrlForProvider(apiProvider ?: "openai")
                    val config = com.kirakira.kiracode.data.remote.real.ApiConfig(
                        apiKey = apiKey!!,
                        baseUrl = baseUrl,
                        model = selectedModel ?: "gpt-4o-mini",
                        providerId = apiProvider ?: "openai",
                        temperature = defaultTemperature,
                        maxTokens = defaultMaxTokens
                    )
                    realApiService.generateCharacterCardStreamWithConfig(request, config)
                }
            }
            else -> {
                apiService.generateCharacterCardStream(request)
            }
        }
    }

    /**
     * 获取可用模型列表
     */
    suspend fun listModels(): Result<List<com.kirakira.kiracode.domain.model.ModelInfo>> {
        val apiMode = preferencesRepository.apiMode.first()
        val apiKey = preferencesRepository.apiKey.first()
        val apiBaseUrl = preferencesRepository.apiBaseUrl.first()

        return when (apiMode) {
            "real" -> {
                if (apiKey.isNullOrBlank()) {
                    Result.failure(
                        NetworkException("真实 API 模式需要配置 API Key", errorCode = "MISSING_API_KEY")
                    )
                } else {
                    val baseUrl = apiBaseUrl ?: getBaseUrlForProvider(preferencesRepository.apiProvider.first() ?: "openai")
                    realApiService.listModels(apiKey!!, baseUrl)
                }
            }
            else -> apiService.listModels()
        }
    }

    /**
     * 校验 API Key
     */
    suspend fun validateApiKey(apiKey: String): Result<Boolean> {
        val apiMode = preferencesRepository.apiMode.first()
        val apiBaseUrl = preferencesRepository.apiBaseUrl.first()

        return when (apiMode) {
            "real" -> {
                val baseUrl = apiBaseUrl ?: getBaseUrlForProvider(preferencesRepository.apiProvider.first() ?: "openai")
                realApiService.validateApiKey(apiKey, baseUrl)
            }
            else -> apiService.validateApiKey(apiKey)
        }
    }

    /**
     * 根据提供商 ID 获取默认 Base URL
     */
    private fun getBaseUrlForProvider(providerId: String): String {
        return when (providerId) {
            "openai" -> "https://api.openai.com"
            "deepseek" -> "https://api.deepseek.com"
            "kimi" -> "https://api.moonshot.cn"
            "zhipu" -> "https://open.bigmodel.cn/api/paas/v4"
            "siliconflow" -> "https://api.siliconflow.cn"
            else -> "https://api.openai.com"
        }
    }
}