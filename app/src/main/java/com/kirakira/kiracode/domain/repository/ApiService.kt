package com.kirakira.kiracode.domain.repository

import com.kirakira.kiracode.domain.model.GenerationRequest
import com.kirakira.kiracode.domain.model.GenerationResult
import com.kirakira.kiracode.domain.model.ModelInfo
import kotlinx.coroutines.flow.Flow

/**
 * AI API 服务接口 - Domain 层定义，Data 层实现
 * 支持 Mock 和 Real 两种实现
 */
interface ApiService {
    /**
     * 生成角色卡 (非流式)
     * @param request 生成请求参数
     * @return 生成结果
     */
    suspend fun generateCharacterCard(request: GenerationRequest): Result<GenerationResult>

    /**
     * 流式生成角色卡 (用于聊天界面逐字显示)
     * @param request 生成请求参数
     * @return Flow 逐块发送内容片段
     *
     * 注意：返回 Flow 的函数本身不需要 suspend 修饰 —— Flow 是冷流，
     * 真正的异步在 collect 时发生。沿用 Kotlin 协程惯例。
     */
    fun generateCharacterCardStream(request: GenerationRequest): Flow<Result<String>>

    /**
     * 获取可用模型列表
     * @param apiKey API Key (可选，某些提供商不需要)
     * @param baseUrl Base URL (可选)
     * @return 模型列表
     */
    suspend fun listModels(apiKey: String? = null, baseUrl: String? = null): Result<List<ModelInfo>>

    /**
     * 校验 API Key 是否有效
     * @param apiKey 用户提供的 API Key
     * @param baseUrl Base URL (可选)
     * @return 校验通过返回 true；否则 false
     */
    suspend fun validateApiKey(apiKey: String, baseUrl: String? = null): Result<Boolean>
}