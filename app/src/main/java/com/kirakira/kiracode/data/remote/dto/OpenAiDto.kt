package com.kirakira.kiracode.data.remote.dto

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.JsonElement

/**
 * OpenAI 兼容 API 请求/响应 DTO
 * 用于与 OpenAI、DeepSeek、Kimi、智谱等兼容接口通信
 */

// ============ 请求模型 ============

@Serializable
data class ChatCompletionRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val temperature: Float = 0.8f,
    val top_p: Float = 1.0f,
    val max_tokens: Int? = null,
    @SerialName("reasoning_effort") val reasoningEffort: String? = null,
    /** DeepSeek V4+ 思考开关：{"type":"enabled"}（与 reasoning_effort 搭配） */
    val thinking: JsonElement? = null,
    /** function calling 工具定义（增量2：null 时不序列化，兼容不支持 tools 的提供商） */
    val tools: List<Tool>? = null,
    val stream: Boolean = false,
    /** V3.1 任务5：请求 usage 尾帧（OpenAI 兼容协议；不支持的中转通常忽略未知字段） */
    val stream_options: kotlinx.serialization.json.JsonElement? = null,
    val presence_penalty: Float = 0.0f,
    val frequency_penalty: Float = 0.0f,
    val stop: List<String>? = null,
)

@Serializable
data class ChatMessage(
    val role: String,      // "system" | "user" | "assistant" | "tool"
    val content: String?,  // null 表示 tool calls 情况
    val name: String? = null,
    val tool_calls: List<ToolCall>? = null,
    val tool_call_id: String? = null,
    /** 思维链（响应侧解析；encodeDefaults=false，请求序列化不受影响） */
    @SerialName("reasoning_content") val reasoningContent: String? = null,
    val reasoning: String? = null,
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class ToolCall(
    val id: String,
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)  // encodeDefaults=false 下 "function"==默认值会被省略，NVIDIA NIM 要求必发
    val type: String = "function",
    val function: FunctionCall,
)

@Serializable
data class FunctionCall(
    val name: String,
    val arguments: String, // JSON 字符串
)

/** function calling 工具定义（请求侧 tools[] 元素） */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class Tool(
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)  // encodeDefaults=false 下 "function"==默认值会被省略，NVIDIA NIM 要求必发
    val type: String = "function",
    val function: FunctionDefinition,
)

@Serializable
data class FunctionDefinition(
    val name: String,
    val description: String? = null,
    /** JSON Schema（参数定义） */
    val parameters: JsonElement? = null,
)

// ============ 响应模型 (非流式) ============

@Serializable
data class ChatCompletionResponse(
    val id: String,
    @SerialName("object") val `object`: String = "chat.completion",
    val created: Long,
    val model: String,
    val choices: List<ChatChoice>,
    val usage: Usage? = null,
    val system_fingerprint: String? = null,
)

@Serializable
data class ChatChoice(
    val index: Int,
    val message: ChatMessage,
    val finish_reason: String?, // "stop" | "length" | "tool_calls" | "content_filter"
    val logprobs: JsonElement? = null,
)

@Serializable
data class PromptTokensDetails(
    val cached_tokens: Int = 0,
)

@Serializable
data class Usage(
    val prompt_tokens: Int,
    val completion_tokens: Int,
    val total_tokens: Int,
    // DeepSeek / OpenAI 缓存命中字段（不支持的 provider 会返回 null，默认 0）
    val prompt_tokens_details: PromptTokensDetails? = null,
    // Anthropic 风格缓存字段（兼容多 provider）
    val cache_read_input_tokens: Int = 0,
) {
    /** 统一缓存命中 token：优先 OpenAI/DeepSeek 明细，其次 Anthropic 字段 */
    val cachedTokens: Int
        get() = prompt_tokens_details?.cached_tokens?.takeIf { it > 0 }
            ?: cache_read_input_tokens
}

// ============ 流式响应模型 (SSE) ============

@Serializable
data class ChatCompletionChunk(
    val id: String = "",
    @SerialName("object") val `object`: String = "chat.completion.chunk",
    val created: Long = 0L,
    val model: String = "",
    val choices: List<ChunkChoice> = emptyList(),
    val system_fingerprint: String? = null,
    /** V3.1 任务5：流式 usage 尾帧（stream_options.include_usage 时出现在 choices 为空的末帧） */
    val usage: Usage? = null,
)

@Serializable
data class ChunkChoice(
    val index: Int = 0,
    val delta: DeltaMessage = DeltaMessage(),
    val finish_reason: String? = null,
    val logprobs: JsonElement? = null,
)

@Serializable
data class DeltaMessage(
    val role: String? = null,
    val content: String? = null,
    @SerialName("reasoning_content") val reasoningContent: String? = null,
    val reasoning: String? = null,
    val tool_calls: List<ToolCallDelta>? = null,
)

@Serializable
data class ToolCallDelta(
    val index: Int,
    val id: String? = null,
    val type: String? = null,
    val function: FunctionCallDelta? = null,
)

@Serializable
data class FunctionCallDelta(
    val name: String? = null,
    val arguments: String? = null,
)

// ============ 模型列表响应 ============

@Serializable
data class ModelListResponse(
    @SerialName("object") val `object`: String = "list",
    val data: List<ModelInfoDto>,
)

@Serializable
data class ModelInfoDto(
    val id: String,
    @SerialName("object") val `object`: String = "model",
    val created: Long,
    val owned_by: String,
    val permission: List<JsonElement>? = null,
    val root: String? = null,
    val parent: String? = null,
)

// ============ 错误响应 ============

@Serializable
data class ApiErrorResponse(
    val error: ApiError,
)

@Serializable
data class ApiError(
    val message: String,
    val type: String? = null,
    val param: String? = null,
    val code: String? = null,
)

// ============ 预置提供商配置 ============

data class ApiProvider(
    val id: String,
    val name: String,
    val baseUrl: String,
    val defaultModel: String,
    val modelsUrl: String = "/v1/models",
    val chatUrl: String = "/v1/chat/completions",
    val requiresApiKey: Boolean = true,
    val description: String = "",
)

object ApiProviders {
    val PRESET_PROVIDERS = listOf(
        ApiProvider(
            id = "openai",
            name = "OpenAI",
            baseUrl = "https://api.openai.com",
            defaultModel = "gpt-4o-mini",
            description = "官方 OpenAI API，需科学上网"
        ),
        ApiProvider(
            id = "deepseek",
            name = "DeepSeek",
            baseUrl = "https://api.deepseek.com",
            defaultModel = "deepseek-chat",
            description = "国产强模型，国内直连"
        ),
        ApiProvider(
            id = "kimi",
            name = "Kimi (Moonshot)",
            baseUrl = "https://api.moonshot.cn",
            defaultModel = "moonshot-v1-8k",
            description = "长上下文擅长，国内直连"
        ),
        ApiProvider(
            id = "zhipu",
            name = "智谱 AI (GLM)",
            baseUrl = "https://open.bigmodel.cn/api/paas/v4",
            defaultModel = "glm-4-flash",
            description = "国产对话模型，国内直连"
        ),
        ApiProvider(
            id = "siliconflow",
            name = "SiliconFlow",
            baseUrl = "https://api.siliconflow.cn",
            defaultModel = "deepseek-ai/DeepSeek-V3",
            description = "聚合平台，多模型可选"
        ),
        ApiProvider(
            id = "custom",
            name = "自定义 (OpenAI 兼容)",
            baseUrl = "",
            defaultModel = "",
            description = "任意 OpenAI 兼容接口，需手动填写 Base URL"
        ),
    )

    fun getById(id: String): ApiProvider? = PRESET_PROVIDERS.firstOrNull { it.id == id }
}