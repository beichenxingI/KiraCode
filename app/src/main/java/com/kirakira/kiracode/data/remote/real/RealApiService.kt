package com.kirakira.kiracode.data.remote.real

import android.os.SystemClock
import com.kirakira.kiracode.data.remote.dto.ApiError
import com.kirakira.kiracode.data.remote.dto.ApiErrorResponse
import com.kirakira.kiracode.data.remote.dto.ApiProvider
import com.kirakira.kiracode.data.remote.dto.ApiProviders
import com.kirakira.kiracode.data.remote.dto.ChatCompletionChunk
import com.kirakira.kiracode.data.remote.dto.ChatCompletionRequest
import com.kirakira.kiracode.data.remote.dto.ChatCompletionResponse
import com.kirakira.kiracode.data.remote.dto.ChatMessage
import com.kirakira.kiracode.data.remote.dto.FunctionCall
import com.kirakira.kiracode.data.remote.dto.FunctionDefinition
import com.kirakira.kiracode.data.remote.dto.ModelInfoDto
import com.kirakira.kiracode.data.remote.dto.ModelListResponse
import com.kirakira.kiracode.data.remote.dto.Tool
import com.kirakira.kiracode.util.AppLogger
import kotlinx.serialization.json.Json
import com.kirakira.kiracode.data.remote.dto.ToolCall
import com.kirakira.kiracode.domain.exception.ApiException
import com.kirakira.kiracode.domain.exception.NetworkException
import com.kirakira.kiracode.domain.model.CharacterCard
import com.kirakira.kiracode.domain.model.GenerationRequest
import com.kirakira.kiracode.domain.model.GenerationResult
import com.kirakira.kiracode.domain.model.ModelInfo
import com.kirakira.kiracode.domain.repository.ApiService
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.call.body
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.plugins.timeout
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readUTF8Line
import io.ktor.utils.io.core.readText
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.io.IOException
import java.net.URLEncoder
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * API 配置数据类
 * 包含调用真实 AI API 所需的所有配置
 */
data class ApiConfig(
    val apiKey: String,
    val baseUrl: String,
    val model: String,
    val providerId: String = "custom",
    val temperature: Float = 0.8f,
    val maxTokens: Int = 4096,
    val topP: Float = 1.0f,
    /** 思考强度（OpenAI o 系/gpt-5 等支持 reasoning_effort 的模型）：low|medium|high；null 不传参 */
    val reasoningEffort: String? = null
) {
    val provider: ApiProvider
        get() = ApiProviders.getById(providerId) ?: ApiProvider(
            id = providerId,
            name = "自定义",
            baseUrl = baseUrl,
            defaultModel = model,
            description = "用户自定义配置"
        )
}

/**
 * 真实 AI API 服务实现
 * 支持 OpenAI 兼容接口 (OpenAI、DeepSeek、Kimi、智谱、SiliconFlow 等)
 * 特性：
 * - 非流式与流式 (SSE) 调用
 * - 自动重试与错误处理
 * - 请求日志记录
 * - 取消传播
 */
@Singleton
class RealApiService @javax.inject.Inject constructor(
    private val apiClient: HttpClient
) : ApiService {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    /** SSE 专用客户端：readTimeout=0，避免长思考流被读超时掐断 */
    private val sseClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    // ============ 生成角色卡 (非流式) ============

    override suspend fun generateCharacterCard(request: GenerationRequest): Result<GenerationResult> {
        return runCatching {
            val config = extractConfigFromRequest(request)
            val provider = config.provider

            // 构建提示词
            val systemPrompt = buildSystemPrompt(request)
            val userPrompt = buildUserPrompt(request)

            val messages = listOf(
                ChatMessage(role = "system", content = systemPrompt),
                ChatMessage(role = "user", content = userPrompt)
            )

            // 调用 API (非流式，生成完整角色卡)
            val response = apiClient.post {
                url(ApiUrlResolver.chatCompletions(config.baseUrl))
                header(HttpHeaders.Authorization, "Bearer ${config.apiKey}")
                contentType(ContentType.Application.Json)
                setBody(ChatCompletionRequest(
                    model = config.model,
                    messages = messages,
                    temperature = config.temperature,
                    max_tokens = config.maxTokens,
                    stream = false
                ))
            }
            val parsed = response.body<ChatCompletionResponse>()

            val content = parsed.choices.firstOrNull()?.message?.content
                ?: throw NetworkException("API 返回空内容", errorCode = "EMPTY_RESPONSE")

            // 解析返回的 JSON 为 CharacterCard
            val card = parseCharacterCardFromJson(content, request)
            GenerationResult.Success(card)
        }.recoverCatching { e ->
            throw when (e) {
                is NetworkException, is ApiException -> e
                else -> NetworkException("生成角色卡失败: ${e.message}", cause = e)
            }
        }
    }

    override fun generateCharacterCardStream(request: GenerationRequest): Flow<Result<String>> = flow {
        val config = extractConfigFromRequest(request)
        val provider = config.provider

        val systemPrompt = buildSystemPrompt(request)
        val userPrompt = buildUserPrompt(request)

        val messages = listOf(
            ChatMessage(role = "system", content = systemPrompt),
            ChatMessage(role = "user", content = userPrompt)
        )

        val requestBody = ChatCompletionRequest(
            model = config.model,
            messages = messages,
            temperature = config.temperature,
            max_tokens = config.maxTokens,
            stream = true
        )

        val response: HttpResponse = apiClient.post {
            url(ApiUrlResolver.chatCompletions(config.baseUrl))
            header(HttpHeaders.Authorization, "Bearer ${config.apiKey}")
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Accept, "text/event-stream")
            setBody(requestBody)
        }

        if (!response.status.isSuccess()) {
            val errorBody = response.bodyAsText()
            val error = parseApiError(errorBody, response.status.value)
            emit(Result.failure(ApiException(
                error.message,
                errorCode = error.code ?: "API_ERROR",
                rawResponse = errorBody
            )))
            return@flow
        }

        // 处理 SSE 流：逐行读取（readUTF8Line 阻塞至读到一行或流结束）
        val channel: ByteReadChannel = response.bodyAsChannel()
        while (!channel.isClosedForRead) {
            val line = channel.readUTF8Line() ?: break
            val trimmed = line.trim()
            if (!trimmed.startsWith("data: ")) continue
            val data = trimmed.substring(6)
            if (data == "[DONE]") return@flow
            try {
                val chunk = json.decodeFromString<ChatCompletionChunk>(data)
                val content = chunk.choices.firstOrNull()?.delta?.content
                if (!content.isNullOrBlank()) {
                    emit(Result.success(content))
                }
                if (chunk.choices.firstOrNull()?.finish_reason != null) {
                    return@flow
                }
            } catch (e: Exception) {
                // 忽略解析失败的 chunk
            }
        }
    }

    // ============ 对话补全 (V2.0: 全量历史 + SSE) ============

    /**
     * 非流式对话补全：发送完整消息列表，返回助手回复文本。
     */
    suspend fun chatCompletion(
        messages: List<ChatMessage>,
        config: ApiConfig
    ): Result<String> = runCatching {
        val provider = config.provider
        val response = apiClient.post {
            url(ApiUrlResolver.chatCompletions(config.baseUrl))
            header(HttpHeaders.Authorization, "Bearer ${config.apiKey}")
            contentType(ContentType.Application.Json)
            setBody(ChatCompletionRequest(
                model = config.model,
                messages = messages,
                temperature = config.temperature,
                max_tokens = config.maxTokens,
                stream = false
            ))
        }
        if (!response.status.isSuccess()) {
            val errorBody = response.bodyAsText()
            throw ApiException(
                parseApiError(errorBody, response.status.value).message,
                errorCode = "HTTP_${response.status.value}",
                rawResponse = errorBody
            )
        }
        val parsed = response.body<ChatCompletionResponse>()
        parsed.choices.firstOrNull()?.message?.content
            ?: throw NetworkException("API 返回空内容", errorCode = "EMPTY_RESPONSE")
    }.recoverCatching { e ->
        throw when (e) {
            is NetworkException, is ApiException -> e
            else -> NetworkException("对话请求失败: ${e.message}", cause = e)
        }
    }

    /** 非流式对话补全结果（content + tool_calls + reasoning + token 用量） */
    data class ChatCompletionResult(
        val content: String,
        val toolCalls: List<ToolCall>?,
        val promptTokens: Int? = null,
        val completionTokens: Int? = null,
        val cachedTokens: Int = 0,
        val reasoning: String? = null
    )

    /**
     * 非流式对话补全（支持 function calling，增量2）。
     * tools 为 null 时不传该字段（兼容不支持 function calling 的提供商）。
     * 返回 content 与 tool_calls；调用方负责将 tool_calls 结果以 tool role 回注。
     */
    suspend fun chatCompletionWithTools(
        messages: List<ChatMessage>,
        config: ApiConfig,
        tools: List<JsonObject>? = null
    ): Result<ChatCompletionResult> = runCatching {
        android.util.Log.d(
            "KiraCard/Tools",
            "chatCompletionWithTools → tools=${tools?.size ?: 0} 个：${tools?.map { it["function"]?.jsonObject?.get("name")?.jsonPrimitive?.content }.orEmpty()}"
        )
        val response = apiClient.post {
            url(ApiUrlResolver.chatCompletions(config.baseUrl))
            header(HttpHeaders.Authorization, "Bearer ${config.apiKey}")
            contentType(ContentType.Application.Json)
            // 长任务：单请求超时放宽到 10 分钟（与流式一致，避免长生成被 60s 全局超时掐断）
            timeout { requestTimeoutMillis = 600_000 }
            setBody(ChatCompletionRequest(
                model = config.model,
                messages = messages,
                temperature = config.temperature,
                max_tokens = config.maxTokens,
                tools = tools?.toToolDefinitions()
            ))
        }
        if (!response.status.isSuccess()) {
            val errorBody = response.bodyAsText()
            com.kirakira.kiracode.util.AppLogger.e(
                "RealApiService",
                "HTTP ${response.status.value} chat_completions(非流式) model=${config.model} messages=${messages.size} body=${errorBody.take(300)}"
            )
            throw ApiException(
                parseApiError(errorBody, response.status.value).message,
                errorCode = "HTTP_${response.status.value}",
                rawResponse = errorBody
            )
        }
        val parsed = response.body<ChatCompletionResponse>()
        val message = parsed.choices.firstOrNull()?.message
        ChatCompletionResult(
            content = message?.content ?: "",
            toolCalls = message?.tool_calls,
            promptTokens = parsed.usage?.prompt_tokens,
            completionTokens = parsed.usage?.completion_tokens,
            cachedTokens = parsed.usage?.cachedTokens ?: 0,
            reasoning = message?.reasoningContent ?: message?.reasoning
        )
    }.recoverCatching { e ->
        throw when (e) {
            is NetworkException, is ApiException -> e
            else -> NetworkException("对话请求失败: ${e.message}", cause = e)
        }
    }
    /** 流式对话补全 (SSE)：发送完整消息列表，产出内容/思考/错误三类事件。 */
    sealed interface SseEvent {
        data class Content(val text: String) : SseEvent
        data class Reasoning(val text: String) : SseEvent
        data class Error(val e: Throwable) : SseEvent
        /** 流式 function calling：流结束时回传累积的完整 tool_calls */
        data class ToolCalls(val calls: List<ToolCall>) : SseEvent
        /** V3.1 任务5：usage 尾帧（精确 token 统计 + 缓存命中） */
        data class Usage(
            val promptTokens: Int,
            val completionTokens: Int,
            val totalTokens: Int,
            val cachedTokens: Int = 0
        ) : SseEvent
    }

    fun chatCompletionStream(
        messages: List<ChatMessage>,
        config: ApiConfig,
        tools: List<JsonObject>? = null
    ): Flow<SseEvent> = callbackFlow {
        val requestBody = buildChatRequest(messages, config, stream = true, tools = tools?.toToolDefinitions())
        val httpRequest = Request.Builder()
            .url(ApiUrlResolver.chatCompletions(config.baseUrl))
            .header("Authorization", "Bearer ${config.apiKey}")
            .header("Content-Type", "application/json")
            .header("Accept", "text/event-stream")
            .post(
                json.encodeToString(ChatCompletionRequest.serializer(), requestBody)
                    .toRequestBody(jsonMediaType)
            )
            .build()

        var reasoningDeltas = 0
        var contentDeltas = 0
        val toolCallAcc = sortedMapOf<Int, ToolCallSlot>()
        var ended = false

        fun emitToolCallsOnce() {
            if (ended) return
            ended = true
            if (toolCallAcc.isNotEmpty()) {
                trySend(SseEvent.ToolCalls(toolCallAcc.map { (idx, slot) ->
                    ToolCall(
                        id = slot.id.ifBlank { "call_$idx" },
                        function = FunctionCall(name = slot.name, arguments = slot.args.toString())
                    )
                }))
            }
        }

        val listener = object : EventSourceListener() {
            override fun onEvent(
                eventSource: EventSource,
                id: String?,
                type: String?,
                data: String
            ) {
                if (data == "[DONE]") {
                    emitToolCallsOnce()
                    close()
                    return
                }
                try {
                    val chunk = json.decodeFromString<ChatCompletionChunk>(data)
                    chunk.usage?.let { u ->
                        trySend(SseEvent.Usage(u.prompt_tokens, u.completion_tokens, u.total_tokens, u.cachedTokens))
                    }
                    val choice = chunk.choices.firstOrNull() ?: return
                    choice.delta.reasoningContent?.takeIf { it.isNotEmpty() }?.let {
                        reasoningDeltas++
                        trySend(SseEvent.Reasoning(it))
                    }
                    choice.delta.reasoning?.takeIf { it.isNotEmpty() }?.let {
                        reasoningDeltas++
                        trySend(SseEvent.Reasoning(it))
                    }
                    choice.delta.content?.takeIf { it.isNotEmpty() }?.let {
                        contentDeltas++
                        trySend(SseEvent.Content(it))
                    }
                    choice.delta.tool_calls?.forEach { tc ->
                        val slot = toolCallAcc.getOrPut(tc.index) { ToolCallSlot() }
                        if (!tc.id.isNullOrBlank() && slot.id.isEmpty()) slot.id = tc.id
                        tc.function?.name?.takeIf { name -> name.isNotEmpty() && slot.name.isEmpty() }?.let {
                            slot.name = it
                        }
                        tc.function?.arguments?.let { slot.args.append(it) }
                    }
                } catch (e: Exception) {
                    AppLogger.w(
                        "SSE",
                        "chunk 解析失败 [${e::class.simpleName}]: ${e.message} | data前100字=${data.take(100)}"
                    )
                }
            }

            override fun onFailure(
                eventSource: EventSource,
                t: Throwable?,
                response: okhttp3.Response?
            ) {
                val error = t ?: IOException(
                    "SSE failed: HTTP ${response?.code} ${response?.message}"
                )
                trySend(SseEvent.Error(error))
                close()
            }

            override fun onClosed(eventSource: EventSource) {
                AppLogger.d(
                    "SSE",
                    "OkHttp stream closed reasoning=$reasoningDeltas content=$contentDeltas toolCalls=${toolCallAcc.size}"
                )
                emitToolCallsOnce()
                close()
            }
        }

        AppLogger.d(
            "SSE",
            "OkHttp stream start model=${config.model} tools=${tools?.size ?: 0} msgs=${messages.size}"
        )
        val eventSource = EventSources.createFactory(sseClient).newEventSource(httpRequest, listener)
        awaitClose { eventSource.cancel() }
    }

    /** 旧 Ktor CIO 流式实现，保留作回退参考；当前入口使用 Aurora 同款 OkHttp EventSource。 */
    private fun chatCompletionStreamKtor(
        messages: List<ChatMessage>,
        config: ApiConfig,
        tools: List<JsonObject>? = null
    ): Flow<SseEvent> = flow {
        val msg = "chatCompletionStream → tools=${tools?.size ?: 0} 个"
        android.util.Log.d("KiraCard/Tools", msg)
        com.kirakira.kiracode.util.AppLogger.d("RealApiService", msg)
        
        // 诊断：SSE 到达节奏统计（仅 DEBUG，聚合不逐条）
        val diagEnabled = true
        val requestId = java.util.UUID.randomUUID().toString().take(8)
        val nanosStart = if (diagEnabled) SystemClock.elapsedRealtimeNanos() else 0L
        var nanosHeaders: Long = 0
        var nanosFirstByte: Long = 0
        var nanosFirstReasoning: Long = 0
        var nanosFirstContent: Long = 0
        var nanosLastDelta: Long = 0
        var nanosFirstLine: Long = 0
        // 实验 B：首个原始字节到达时刻（决定性指标：区分“网络晚到”与“行读取器等待”）
        var nanosFirstRawByte: Long = 0
        var headersLog = ""
        val contentArrivalBuckets = if (diagEnabled) mutableMapOf<String, Int>() else null
        val contentArrivalNanos = if (diagEnabled) mutableListOf<Long>() else null
        
        val requestBody = buildChatRequest(messages, config, stream = true, tools = tools?.toToolDefinitions())
        // 诊断：实际发出的请求字段摘要（不含 messages 内容 / API Key，避免泄漏 prompt）
        if (diagEnabled) {
            com.kirakira.kiracode.util.AppLogger.d(
                "SSE_TRACE",
                "REQ_SUMMARY model=${config.model} stream=${requestBody.stream} " +
                    "stream_options=${requestBody.stream_options ?: "absent"} " +
                    "reasoning_effort=${requestBody.reasoningEffort ?: "absent"} " +
                    "thinking=${requestBody.thinking ?: "absent"} " +
                    "tools=${requestBody.tools?.size ?: 0} msgs=${requestBody.messages.size} " +
                    "temp=${requestBody.temperature} top_p=${requestBody.top_p} max_tokens=${requestBody.max_tokens} " +
                    "accept=text/event-stream"
            )
        }
        val response: HttpResponse = apiClient.post {
            url(ApiUrlResolver.chatCompletions(config.baseUrl))
            header(HttpHeaders.Authorization, "Bearer ${config.apiKey}")
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Accept, "text/event-stream")
            timeout { requestTimeoutMillis = 600_000 }
            setBody(requestBody)
        }
        if (diagEnabled) {
            nanosHeaders = SystemClock.elapsedRealtimeNanos()
            val ct = response.headers["Content-Type"]?.firstOrNull() ?: "absent"
            val te = response.headers["Transfer-Encoding"]?.firstOrNull() ?: "absent"
            val ce = response.headers["Content-Encoding"]?.firstOrNull() ?: "absent"
            val cc = response.headers["Cache-Control"]?.firstOrNull() ?: "absent"
            val xab = response.headers["X-Accel-Buffering"]?.firstOrNull() ?: "absent"
            // 全量响应头（脱敏：仅名称/值，值截断；不含 Authorization 等请求头）
            val allHeaders = response.headers.entries().joinToString(" | ") { (k, v) ->
                "$k=${v.joinToString(",").take(60)}"
            }.take(600)
            headersLog = "proto=${response.version} ct=$ct te=$te ce=$ce cc=$cc xab=$xab all=[$allHeaders]"
        }
        if (!response.status.isSuccess()) {
            val errorBody = response.bodyAsText()
            com.kirakira.kiracode.util.AppLogger.e(
                "RealApiService",
                "HTTP 错误 ${response.status.value}: ${errorBody.take(200)}"
            )
            emit(SseEvent.Error(ApiException(
                parseApiError(errorBody, response.status.value).message,
                errorCode = "HTTP_${response.status.value}",
                rawResponse = errorBody
            )))
            return@flow
        }
        val toolCallAcc = sortedMapOf<Int, ToolCallSlot>()
        var reasoningDeltaCount = 0
        var contentDeltaCount = 0
        var reasoningChars = 0
        var contentChars = 0
        val channel: ByteReadChannel = response.bodyAsChannel()
        // === 实验 B：字节级读取（对齐 KiraKira：utf8 流 + 按行切分）===
        // 回退方式：把本段换成 `while (!channel.isClosedForRead) { val line = channel.readUTF8Line() ?: break; ... }`
        val lineReader = SseByteLineReader()
        sseLoop@ while (true) {
            val n = channel.readAvailable(lineReader.readBuf, 0, lineReader.readBuf.size)
            if (n < 0) {
                // EOF：把无换行结尾的残留行刷出
                if (lineReader.hasPending()) lineReader.appendNewline()
            } else if (n > 0) {
                if (diagEnabled && nanosFirstRawByte == 0L) {
                    nanosFirstRawByte = SystemClock.elapsedRealtimeNanos()
                }
                lineReader.append(lineReader.readBuf, n)
            }
            var line = lineReader.nextLine()
            while (line != null) {
            val currentLine = line
            line = lineReader.nextLine() // 先推进，确保 continue 不会重复处理同一行
            if (diagEnabled && nanosFirstLine == 0L) {
                nanosFirstLine = SystemClock.elapsedRealtimeNanos()
            }
            val trimmed = currentLine.trim()
            if (!trimmed.startsWith("data:")) continue
            val data = trimmed.removePrefix("data:").trim()
            if (data == "[DONE]") break@sseLoop
            try {
                val chunk = json.decodeFromString<ChatCompletionChunk>(data)
                chunk.usage?.let { u ->
                    emit(SseEvent.Usage(u.prompt_tokens, u.completion_tokens, u.total_tokens, u.cachedTokens))
                }
                val choice = chunk.choices.firstOrNull() ?: continue
                val now = if (diagEnabled) SystemClock.elapsedRealtimeNanos() else 0L
                choice.delta.reasoningContent?.takeIf { it.isNotEmpty() }?.let {
                    if (reasoningDeltaCount == 0) {
                        com.kirakira.kiracode.util.AppLogger.d("SSE", "首个 reasoning delta")
                        if (diagEnabled) nanosFirstReasoning = now
                    }
                    reasoningDeltaCount++
                    reasoningChars += it.length
                    emit(SseEvent.Reasoning(it))
                }
                choice.delta.reasoning?.takeIf { it.isNotEmpty() }?.let {
                    if (reasoningDeltaCount == 0) {
                        com.kirakira.kiracode.util.AppLogger.d("SSE", "首个 reasoning delta")
                        if (diagEnabled) nanosFirstReasoning = now
                    }
                    reasoningDeltaCount++
                    reasoningChars += it.length
                    emit(SseEvent.Reasoning(it))
                }
                choice.delta.content?.takeIf { it.isNotEmpty() }?.let {
                    if (contentDeltaCount == 0) {
                        com.kirakira.kiracode.util.AppLogger.d("SSE", "首个 content delta")
                        if (diagEnabled) nanosFirstContent = now
                    }
                    contentDeltaCount++
                    contentChars += it.length
                    if (diagEnabled) {
                        nanosLastDelta = now
                        val bucket = when {
                            now - nanosFirstContent < 1_000_000 -> "<1ms"
                            now - nanosFirstContent < 5_000_000 -> "1-5ms"
                            now - nanosFirstContent < 20_000_000 -> "5-20ms"
                            now - nanosFirstContent < 100_000_000 -> "20-100ms"
                            else -> ">100ms"
                        }
                        contentArrivalBuckets!![bucket] = contentArrivalBuckets.getOrDefault(bucket, 0) + 1
                        contentArrivalNanos!!.add(now - nanosFirstContent)
                    }
                    emit(SseEvent.Content(it))
                }
                choice.delta.tool_calls?.forEach { tc ->
                    val slot = toolCallAcc.getOrPut(tc.index) { ToolCallSlot() }
                    if (!tc.id.isNullOrBlank() && slot.id.isEmpty()) slot.id = tc.id
                    tc.function?.name?.takeIf { it.isNotEmpty() && slot.name.isEmpty() }?.let { slot.name = it }
                    tc.function?.arguments?.let { slot.args.append(it) }
                }
            } catch (e: Exception) {
                com.kirakira.kiracode.util.AppLogger.w(
                    "SSE",
                    "chunk 解析失败 [${e::class.simpleName}]: ${e.message} | data前100字=${data.take(100)}"
                )
            }
            }
            if (n < 0) break@sseLoop
        }
        if (diagEnabled) {
            val totalNanos = SystemClock.elapsedRealtimeNanos() - nanosStart
            val hist = contentArrivalBuckets!!.entries.joinToString(", ") { "${it.key}=${it.value}" }
            com.kirakira.kiracode.util.AppLogger.d(
                "SSE_TRACE",
                "requestId=$requestId " +
                "t_req_start=0 " +
                "t_headers_ms=${(nanosHeaders - nanosStart) / 1_000_000} " +
                "t_first_raw_byte_ms=${if (nanosFirstRawByte > 0) (nanosFirstRawByte - nanosStart) / 1_000_000 else -1} " +
                "t_headers_to_first_raw_byte_ms=${if (nanosFirstRawByte > 0 && nanosHeaders > 0) (nanosFirstRawByte - nanosHeaders) / 1_000_000 else -1} " +
                "t_first_line_ms=${if (nanosFirstLine > 0) (nanosFirstLine - nanosStart) / 1_000_000 else -1} " +
                "t_headers_to_first_line_ms=${if (nanosFirstLine > 0 && nanosHeaders > 0) (nanosFirstLine - nanosHeaders) / 1_000_000 else -1} " +
                "t_first_reasoning_ms=${(nanosFirstReasoning - nanosStart) / 1_000_000} " +
                "t_first_content_ms=${(nanosFirstContent - nanosStart) / 1_000_000} " +
                "t_last_delta_ms=${(nanosLastDelta - nanosStart) / 1_000_000} " +
                "total_ms=${totalNanos / 1_000_000} " +
                "reasoning_delta=$reasoningDeltaCount(${reasoningChars}字) " +
                "content_delta=$contentDeltaCount(${contentChars}字) " +
                "content_hist=[$hist] " +
                "headers=$headersLog"
            )
        } else {
            com.kirakira.kiracode.util.AppLogger.d(
                "SSE",
                "流结束摘要 reasoning delta=$reasoningDeltaCount(${reasoningChars}字) " +
                    "content delta=$contentDeltaCount(${contentChars}字) toolCalls=${toolCallAcc.size}"
            )
        }
        // 流结束：回传累积的完整 tool_calls（流式 function calling）
        if (toolCallAcc.isNotEmpty()) {
            emit(SseEvent.ToolCalls(
                toolCallAcc.map { (idx, slot) ->
                    ToolCall(
                        id = slot.id.ifBlank { "call_$idx" },
                        function = FunctionCall(name = slot.name, arguments = slot.args.toString())
                    )
                }
            ))
        }
    }

    /** CardFunctions 的 JSON 对象 → Tool 定义（流式/非流式补全入口共用） */
    private fun List<JsonObject>.toToolDefinitions(): List<Tool> = map { toolJson ->
        Tool(
            type = toolJson["type"]?.jsonPrimitive?.content ?: "function",
            function = toolJson["function"]?.jsonObject?.let { funcObj ->
                FunctionDefinition(
                    name = funcObj["name"]?.jsonPrimitive?.content ?: "",
                    description = funcObj["description"]?.jsonPrimitive?.content,
                    parameters = funcObj["parameters"]?.jsonObject
                )
            } ?: FunctionDefinition(name = "")
        )
    }

    /** 流式 tool_call 增量累积槽 */
    private class ToolCallSlot {
        var id: String = ""
        var name: String = ""
        val args: StringBuilder = StringBuilder()
    }

    /**
     * 实验 B：字节级 SSE 行读取器（对齐 KiraKira 的 utf8 流 + 按行切分）。
     * 只按 0x0A 切分——UTF-8 续字节均 ≥0x80，不会与换行字节冲突，故按字节切行再解码是安全的。
     */
    private class SseByteLineReader {
        val readBuf = ByteArray(8192)
        private var pending = ByteArray(4096)
        private var pendingLen = 0

        fun hasPending(): Boolean = pendingLen > 0

        fun appendNewline() = append(NEWLINE_BYTES, 1)

        fun append(src: ByteArray, n: Int) {
            if (pendingLen + n > pending.size) {
                pending = pending.copyOf(maxOf(pending.size * 2, pendingLen + n))
            }
            System.arraycopy(src, 0, pending, pendingLen, n)
            pendingLen += n
        }

        /** 取出下一整行（不含换行符）；当前无完整行时返回 null */
        fun nextLine(): String? {
            var i = 0
            while (i < pendingLen) {
                if (pending[i] == NEWLINE) {
                    val line = String(pending, 0, i, Charsets.UTF_8)
                    val remain = pendingLen - (i + 1)
                    if (remain > 0) System.arraycopy(pending, i + 1, pending, 0, remain)
                    pendingLen = remain
                    return line
                }
                i++
            }
            return null
        }

        private companion object {
            val NEWLINE: Byte = '\n'.code.toByte()
            val NEWLINE_BYTES = byteArrayOf(NEWLINE)
        }
    }

    /** 按模型家族构建请求体（思考强度参数映射） */
    private fun buildChatRequest(
        messages: List<ChatMessage>,
        config: ApiConfig,
        stream: Boolean,
        tools: List<Tool>? = null
    ): ChatCompletionRequest {
        val family = com.kirakira.kiracode.domain.ai.ReasoningSupport.heuristicFamily(config.model)
        return ChatCompletionRequest(
            model = config.model,
            messages = messages,
            temperature = config.temperature,
            top_p = config.topP,
            max_tokens = config.maxTokens,
            reasoningEffort = config.reasoningEffort,
            thinking = if (config.reasoningEffort != null && family == com.kirakira.kiracode.domain.ai.ReasoningSupport.Family.DEEPSEEK) {
                kotlinx.serialization.json.JsonPrimitive("enabled").let { type ->
                    kotlinx.serialization.json.buildJsonObject {
                        put("type", type)
                    }
                }
            } else null,
            tools = tools,
            stream = stream,
            // V3.1 任务5：流式请求 usage 尾帧（末帧 choices 为空 + usage 字段）
            stream_options = if (stream) {
                kotlinx.serialization.json.Json.parseToJsonElement("""{"include_usage":true}""")
            } else null
        )
    }

    /**
     * 思考强度探测（权威判定）：发送携带 reasoning_effort=low 的极小请求。
     * - 成功 → 支持
     * - 失败且错误文本指向该参数不被接受 → 不支持
     * - 其他失败（网络/鉴权等）→ 视为未知，回退启发式判断
     */
    suspend fun probeReasoningEffort(config: ApiConfig): Boolean? {
        return try {
            val probeConfig = config.copy(reasoningEffort = "low")
            val request = buildChatRequest(
                listOf(ChatMessage(role = "user", content = "hi")),
                probeConfig,
                stream = false
            )
            val response = apiClient.post {
                url(ApiUrlResolver.chatCompletions(probeConfig.baseUrl))
                header(HttpHeaders.Authorization, "Bearer ${probeConfig.apiKey}")
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            if (response.status.isSuccess()) true
            else {
                val body = runCatching { response.bodyAsText() }.getOrDefault("")
                if (com.kirakira.kiracode.domain.ai.ReasoningSupport.errorIndicatesUnsupported(body)) false
                else null // 网络/配额/鉴权等无关错误 → 未知
            }
        } catch (e: Exception) {
            null
        }
    }

    // ============ 模型列表 ============

    override suspend fun listModels(apiKey: String?, baseUrl: String?): Result<List<ModelInfo>> {
        return runCatching {
            val config = ApiConfig(
                apiKey = apiKey ?: "",
                baseUrl = baseUrl ?: "https://api.openai.com",
                model = "gpt-4o-mini",
                providerId = "openai"
            )
            val provider = config.provider

            val response = apiClient.get {
                url(ApiUrlResolver.models(config.baseUrl))
                header(HttpHeaders.Authorization, "Bearer ${config.apiKey}")
            }
            val parsed = response.body<ModelListResponse>()

            parsed.data.map { dto ->
                ModelInfo(
                    id = dto.id,
                    name = dto.id,
                    provider = provider.name,
                    maxTokens = getModelMaxTokens(dto.id),
                    capabilities = listOf("chat", "character_generation")
                )
            }
        }.recoverCatching { e ->
            throw when (e) {
                is NetworkException, is ApiException -> e
                else -> NetworkException("获取模型列表失败: ${e.message}", cause = e)
            }
        }
    }

    // ============ API Key 校验 ============

    override suspend fun validateApiKey(apiKey: String, baseUrl: String?): Result<Boolean> {
        return runCatching {
            val config = ApiConfig(
                apiKey = apiKey,
                baseUrl = baseUrl ?: "https://api.openai.com",
                model = "gpt-4o-mini",
                providerId = "openai"
            )
            val provider = config.provider

            val response = apiClient.get {
                url(ApiUrlResolver.models(config.baseUrl))
                header(HttpHeaders.Authorization, "Bearer ${config.apiKey}")
            }
            // 仅校验状态码，不解析响应体
            response.status.isSuccess()
        }.recoverCatching { e ->
            throw when (e) {
                is NetworkException, is ApiException -> e
                else -> NetworkException("API Key 校验失败: ${e.message}", cause = e)
            }
        }
    }

    // ============ 私有辅助方法 ============

    /**
     * 从 GenerationRequest 提取 ApiConfig
     * 优先使用 request 中的字段，否则使用默认值
     * 注意：apiKey 和 baseUrl 需要从 PreferencesRepository 获取，这里返回默认值
     * 实际使用时由 ViewModel 调用 withConfig 传入完整配置
     */
    private fun extractConfigFromRequest(request: GenerationRequest): ApiConfig {
        return ApiConfig(
            apiKey = "", // 需要从外部注入
            baseUrl = "https://api.openai.com", // 默认值
            model = request.model.ifBlank { "gpt-4o-mini" },
            providerId = "openai",
            temperature = request.temperature,
            maxTokens = request.maxTokens
        )
    }

    /**
     * 带完整配置的生成角色卡 (由 ViewModel 调用)
     */
    suspend fun generateCharacterCardWithConfig(request: GenerationRequest, config: ApiConfig): Result<GenerationResult> {
        return runCatching {
            val provider = config.provider

            val systemPrompt = buildSystemPrompt(request)
            val userPrompt = buildUserPrompt(request)

            val messages = listOf(
                ChatMessage(role = "system", content = systemPrompt),
                ChatMessage(role = "user", content = userPrompt)
            )

            val response = apiClient.post {
                url(ApiUrlResolver.chatCompletions(config.baseUrl))
                header(HttpHeaders.Authorization, "Bearer ${config.apiKey}")
                contentType(ContentType.Application.Json)
                setBody(ChatCompletionRequest(
                    model = config.model,
                    messages = messages,
                    temperature = config.temperature,
                    max_tokens = config.maxTokens,
                    stream = false
                ))
            }
            val parsed = response.body<ChatCompletionResponse>()

            val content = parsed.choices.firstOrNull()?.message?.content
                ?: throw NetworkException("API 返回空内容", errorCode = "EMPTY_RESPONSE")

            val card = parseCharacterCardFromJson(content, request)
            GenerationResult.Success(card)
        }.recoverCatching { e ->
            throw when (e) {
                is NetworkException, is ApiException -> e
                else -> NetworkException("生成角色卡失败: ${e.message}", cause = e)
            }
        }
    }

    /**
     * 带完整配置的流式生成
     */
    fun generateCharacterCardStreamWithConfig(request: GenerationRequest, config: ApiConfig): Flow<Result<String>> = flow {
        val provider = config.provider

        val systemPrompt = buildSystemPrompt(request)
        val userPrompt = buildUserPrompt(request)

        val messages = listOf(
            ChatMessage(role = "system", content = systemPrompt),
            ChatMessage(role = "user", content = userPrompt)
        )

        val requestBody = ChatCompletionRequest(
            model = config.model,
            messages = messages,
            temperature = config.temperature,
            max_tokens = config.maxTokens,
            stream = true
        )

        val response: HttpResponse = apiClient.post {
            url(ApiUrlResolver.chatCompletions(config.baseUrl))
            header(HttpHeaders.Authorization, "Bearer ${config.apiKey}")
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Accept, "text/event-stream")
            setBody(requestBody)
        }

        if (!response.status.isSuccess()) {
            val errorBody = response.bodyAsText()
            val error = parseApiError(errorBody, response.status.value)
            emit(Result.failure(ApiException(
                error.message,
                errorCode = error.code ?: "API_ERROR",
                rawResponse = errorBody
            )))
            return@flow
        }

        // 处理 SSE 流：逐行读取（readUTF8Line 阻塞至读到一行或流结束）
        val channel: ByteReadChannel = response.bodyAsChannel()
        while (!channel.isClosedForRead) {
            val line = channel.readUTF8Line() ?: break
            val trimmed = line.trim()
            if (!trimmed.startsWith("data: ")) continue
            val data = trimmed.substring(6)
            if (data == "[DONE]") return@flow
            try {
                val chunk = json.decodeFromString<ChatCompletionChunk>(data)
                val content = chunk.choices.firstOrNull()?.delta?.content
                if (!content.isNullOrBlank()) {
                    emit(Result.success(content))
                }
                if (chunk.choices.firstOrNull()?.finish_reason != null) {
                    return@flow
                }
            } catch (e: Exception) {
                // 忽略解析失败的 chunk
            }
        }
    }

    /**
     * 构建系统提示词：角色卡生成专用
     */
    private fun buildSystemPrompt(request: GenerationRequest): String {
        val refCard = request.referenceCard
        val styleTemplate = request.styleTemplate?.let { template ->
            when (template) {
                "tsundere" -> "你擅长创作傲娇类角色：口嫌体正直、反差萌、语气强硬实则在意、语尾习惯加语气词。"
                "gentle" -> "你擅长创作温柔治愈类角色：语气柔和、共情能力强、动作细腻、环境描写丰富。"
                "cold" -> "你擅长创作高冷禁欲类角色：语气简短、情感内敛、理性至上、隐藏深情。"
                "chaotic" -> "你擅长创作疯批/混乱类角色：语气跳跃、行为不可预测、打破第四面墙、元叙事。"
                "lore_heavy" -> "你擅长创作设定党角色：世界观极深、专有名词多、时间线完整、组织关系复杂。"
                else -> "你擅长创作平衡型角色：细节与留白并重、通用性强。"
            }
        } ?: "你擅长创作平衡型角色：细节与留白并重、通用性强。"

        val styleFeatures = extractStyleFeatures(refCard)

        val basePrompt = """
            你是 KiraCard 的角色卡生成引擎，专门基于参考卡风格创作新的 SillyTavern 格式角色卡。
            
            $styleTemplate
            
            参考卡风格特征：
            $styleFeatures
            
            输出要求：
            1. 必须输出有效的 JSON，符合 SillyTavern v2/v3 规范
            2. 包含所有必填字段：name、description、personality、first_message、creator
            3. 可选字段：example_dialogue、scenario、creator_notes、system_prompt、post_history_instructions、alternate_greetings、tags、avatar、uuid、created_at、updated_at、version、spec、spec_version、extensions
            4. 语言风格、格式、语气需模仿参考卡
            5. 不要输出任何解释性文字，只输出 JSON
        """.trimIndent()

        return basePrompt + "\n\n" +
            com.kirakira.kiracode.domain.prompt.RegexGenerationPrompts.promptFor(request.regexGenerationMode) +
            "\n\n请将上述脚本写入角色卡 regexScripts（或 extensions.regex_scripts），" +
            "description 末尾的输出协议由应用自动、幂等追加，仍需保持其余 JSON 结构合法。"
    }

    /**
     * 构建用户提示词
     */
    private fun buildUserPrompt(request: GenerationRequest): String {
        return """
            请基于以下描述创作一个新的角色卡：
            
            新角色描述：${request.newCharacterDescription}
            
            请生成完整的角色卡 JSON，风格参考上述系统提示词中的参考卡特征。
        """.trimIndent()
    }

    /**
     * 从参考卡提取风格特征
     */
    private fun extractStyleFeatures(card: CharacterCard): String {
        val features = mutableListOf<String>()
        
        if (card.firstMessage.isNotBlank()) {
            features.add("首条消息风格：${card.firstMessage.take(100)}...")
        }
        if (card.exampleDialogue.isNotBlank()) {
            features.add("示例对话格式：${card.exampleDialogue.lines().firstOrNull()?.take(80)}...")
        }
        if (card.systemPrompt.isNotBlank()) {
            features.add("系统提示词风格：${card.systemPrompt.take(150)}...")
        }
        if (card.personality.isNotBlank()) {
            features.add("性格描述：${card.personality}")
        }
        
        return if (features.isNotEmpty()) features.joinToString("\n") else "无明显风格特征，自由发挥"
    }

    /**
     * 解析 AI 返回的 JSON 为 CharacterCard
     */
    private fun parseCharacterCardFromJson(jsonString: String, request: GenerationRequest): CharacterCard {
        try {
            val card = json.decodeFromString<CharacterCard>(jsonString)
            
            return card.copy(
                uuid = UUID.randomUUID().toString(),
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                creator = "KiraCard"
            )
        } catch (e: Exception) {
            val extractedJson = extractJsonFromMarkdown(jsonString)
            if (extractedJson != null) {
                return try {
                    val card = json.decodeFromString<CharacterCard>(extractedJson)
                    card.copy(
                        uuid = UUID.randomUUID().toString(),
                        createdAt = System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis(),
                        creator = "KiraCard"
                    )
                } catch (e2: Exception) {
                    throw NetworkException("解析生成结果失败: ${e2.message}", errorCode = "PARSE_FAILED", rawResponse = jsonString, cause = e2)
                }
            }
            throw NetworkException("解析生成结果失败: ${e.message}", errorCode = "PARSE_FAILED", rawResponse = jsonString, cause = e)
        }
    }

    /**
     * 从 markdown 代码块提取 JSON
     */
    private fun extractJsonFromMarkdown(text: String): String? {
        val pattern = """```(?:json)?\s*(\{[\s\S]*?\})\s*```""".toRegex()
        val match = pattern.find(text)
        return match?.groupValues?.get(1)
    }

    /**
     * 解析 API 错误响应
     */
    private fun parseApiError(body: String, statusCode: Int): ApiError {
        return try {
            json.decodeFromString<ApiErrorResponse>(body).error
        } catch (e: Exception) {
            ApiError(
                message = "HTTP $statusCode: $body",
                code = statusCode.toString()
            )
        }
    }

    /**
     * 根据模型 ID 估算最大 Token
     */
    private fun getModelMaxTokens(modelId: String): Int {
        return when {
            modelId.contains("gpt-4", ignoreCase = true) -> 8192
            modelId.contains("gpt-3.5", ignoreCase = true) -> 4096
            modelId.contains("deepseek", ignoreCase = true) -> 8192
            modelId.contains("moonshot", ignoreCase = true) -> 32768
            modelId.contains("glm-4", ignoreCase = true) -> 8192
            modelId.contains("claude", ignoreCase = true) -> 4096
            else -> 4096
        }
    }

    companion object {
        /**
         * 创建配置好的 Ktor HttpClient
         * 支持：JSON 序列化、日志、重试、超时、SSE
         */
        @Suppress("UNUSED_PARAMETER")
        fun createClient(): HttpClient = HttpClient(CIO) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; isLenient = true })
            }
            // === 实验：排除 Logging 插件缓冲（流式首字节诊断）===
            // 若去掉后 t_first_line_ms 显著降低、t_last_delta_ms 显著拉长，
            // 说明 Logging 插件在包装响应体并触发缓冲。
            // install(Logging) {
            //     logger = object : Logger {
            //         override fun log(message: String) {
            //             android.util.Log.d("KiraCard/Api", message)
            //         }
            //     }
            //     level = LogLevel.HEADERS
            // }
            install(io.ktor.client.plugins.HttpTimeout) {
                requestTimeoutMillis = 60_000
                connectTimeoutMillis = 15_000
                socketTimeoutMillis = 120_000
            }
        }
    }
}