package com.kirakira.kiracode

import com.kirakira.kiracode.data.remote.dto.ChatCompletionChunk
import com.kirakira.kiracode.data.remote.dto.ChatCompletionRequest
import com.kirakira.kiracode.data.remote.dto.ChatMessage
import com.kirakira.kiracode.data.remote.dto.ChunkChoice
import com.kirakira.kiracode.data.remote.dto.FunctionDefinition
import com.kirakira.kiracode.data.remote.dto.Tool
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 流式请求协议审计（只读序列化测试，不修改生产逻辑）：
 *
 * 使用与 RealApiService 完全相同的 Json 配置（RealApiService.kt:90 与 createClient :801-803 的
 * ContentNegotiation 配置：ignoreUnknownKeys=true, isLenient=true；kotlinx.serialization 默认
 * encodeDefaults=false → 默认值字段被省略）捕获最终发出的请求 JSON。
 *
 * 样本 A：无工具、普通模型、流式（对应 buildChatRequest，RealApiService.kt:407-435）
 * 样本 B：带工具 + 思考参数 + 流式（同上，reasoningEffort/thinking 非空）
 * 样本 C：非流式 fallback（对应 chatCompletionWithTools 内联构造，RealApiService.kt:268-275）
 */
class StreamingRequestSerializationTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Test
    fun `样本A_无工具_普通模型_流式`() {
        val request = ChatCompletionRequest(
            model = "deepseek-flash",
            messages = listOf(
                ChatMessage(role = "system", content = "<SYSTEM_PROMPT>"),
                ChatMessage(role = "user", content = "<USER_INPUT>")
            ),
            temperature = 0.8f, // 与 DTO 默认值相同 → 预期被省略
            max_tokens = 32768,
            stream = true,
            stream_options = Json.parseToJsonElement("""{"include_usage":true}""")
        )
        val body = json.encodeToString(ChatCompletionRequest.serializer(), request)
        println("SAMPLE_A=$body")
        assertTrue("stream:true 必须序列化", body.contains("\"stream\":true"))
        assertTrue(
            "stream_options 键名为 snake_case 且结构正确",
            body.contains("\"stream_options\":{\"include_usage\":true}")
        )
        assertFalse(
            "默认温度 0.8 应被省略（encodeDefaults=false）",
            body.contains("\"temperature\":")
        )
        assertTrue("max_tokens 应序列化", body.contains("\"max_tokens\":32768"))
        assertFalse("默认 top_p=1.0 应被省略", body.contains("\"top_p\":"))
        assertFalse("默认 presence_penalty 应被省略", body.contains("\"presence_penalty\":"))
        assertFalse("默认 frequency_penalty 应被省略", body.contains("\"frequency_penalty\":"))
        assertFalse("未传 tools 应被省略", body.contains("\"tools\""))
        assertFalse("未传 reasoning_effort 应被省略", body.contains("\"reasoning_effort\":"))
        assertFalse("未传 thinking 应被省略", body.contains("\"thinking\":"))
    }

    @Test
    fun `样本B_带工具_思考参数_流式`() {
        val request = ChatCompletionRequest(
            model = "deepseek-flash",
            messages = listOf(ChatMessage(role = "user", content = "<USER_INPUT>")),
            temperature = 0.8f,
            top_p = 0.95f, // 非默认值 → 预期序列化
            max_tokens = 32768,
            reasoningEffort = "low",
            thinking = Json.parseToJsonElement("""{"type":"enabled"}"""),
            tools = listOf(
                Tool(
                    type = "function",
                    function = FunctionDefinition(
                        name = "update_card_field",
                        description = "<DESC>",
                        parameters = Json.parseToJsonElement("""{"type":"object"}""")
                    )
                )
            ),
            stream = true,
            stream_options = Json.parseToJsonElement("""{"include_usage":true}""")
        )
        val body = json.encodeToString(ChatCompletionRequest.serializer(), request)
        println("SAMPLE_B=$body")
        assertTrue(body.contains("\"stream\":true"))
        assertTrue(
            "reasoning_effort 键名正确（@SerialName 生效，非 camelCase）",
            body.contains("\"reasoning_effort\":\"low\"")
        )
        assertTrue("thinking 结构为 {\"type\":\"enabled\"}", body.contains("\"thinking\":{\"type\":\"enabled\"}"))
        assertTrue(body.contains("\"stream_options\":{\"include_usage\":true}"))
        assertTrue("tools 应序列化", body.contains("\"tools\":["))
        assertTrue("非默认 top_p=0.95 应序列化", body.contains("\"top_p\":0.95"))
    }

    @Test
    fun `样本C_非流式fallback_无流式专属字段`() {
        // 对应 chatCompletionWithTools 的内联构造（RealApiService.kt:268-275）：
        // 不经过 buildChatRequest → 无 stream_options / reasoning_effort / thinking / top_p
        val request = ChatCompletionRequest(
            model = "deepseek-flash",
            messages = listOf(ChatMessage(role = "user", content = "<USER_INPUT>")),
            temperature = 0.8f,
            max_tokens = 32768,
            tools = listOf(
                Tool(type = "function", function = FunctionDefinition(name = "update_card_field"))
            )
        )
        val body = json.encodeToString(ChatCompletionRequest.serializer(), request)
        println("SAMPLE_C=$body")
        assertFalse("非流式 fallback 不得含 stream 键（默认 false 被省略）", body.contains("\"stream\":"))
        assertFalse("不得含 stream_options", body.contains("\"stream_options\""))
        assertFalse("不得含 reasoning_effort", body.contains("\"reasoning_effort\""))
        assertFalse("不得含 thinking", body.contains("\"thinking\""))
        assertFalse("不得含 top_p", body.contains("\"top_p\":"))
    }

    // ============ NVIDIA NIM 400 修复回归（Tool.type / ToolCall.type 强制序列化） ============

    @Test
    fun `Tool_type默认值也必须写入JSON`() {
        val tool = Tool(function = FunctionDefinition(name = "test"))
        val body = json.encodeToString(Tool.serializer(), tool)
        println("TOOL_ENCODED=$body")
        assertTrue(
            "encodeDefaults=false 下 type==\"function\" 也必须序列化（NVIDIA NIM 要求），实际：$body",
            body.contains("\"type\":\"function\"")
        )
    }

    @Test
    fun `带工具的请求体tools数组每项都含type字段`() {
        val request = ChatCompletionRequest(
            model = "nvidia/nemotron",
            messages = listOf(ChatMessage(role = "user", content = "<USER_INPUT>")),
            tools = listOf(
                Tool(
                    type = "function",
                    function = FunctionDefinition(
                        name = "update_card_field",
                        parameters = Json.parseToJsonElement("""{"type":"object"}""")
                    )
                )
            ),
            stream = true
        )
        val body = json.encodeToString(ChatCompletionRequest.serializer(), request)
        println("NIM_REQ=$body")
        assertTrue("tools 元素必须含 \"type\":\"function\"", body.contains("\"tools\":[{\"type\":\"function\""))
    }

    // ============ Agnes chunk 解析修复回归（provider 省略字段时容错） ============

    @Test
    fun `ChunkChoice允许缺失finish_reason和index`() {
        // 模拟 Agnes 省略 finish_reason / index 的 delta 帧
        val raw = """{"delta":{"content":"hello"}}"""
        val choice = json.decodeFromString(ChunkChoice.serializer(), raw)
        assertEquals("hello", choice.delta.content)
        assertEquals(0, choice.index)
        assertEquals(null, choice.finish_reason)
    }

    @Test
    fun `ChatCompletionChunk允许缺失created和id`() {
        // 模拟 Agnes 的字段顺序 id,model,object（疑似省略 created）
        val raw = """{"id":"abc","model":"agnes-3.0-flash","object":"chat.completion.chunk","choices":[]}"""
        val chunk = json.decodeFromString(ChatCompletionChunk.serializer(), raw)
        assertEquals("abc", chunk.id)
        assertEquals(0L, chunk.created)
        assertTrue(chunk.choices.isEmpty())
    }

    @Test
    fun `ChatCompletionChunk允许完全省略choices`() {
        val raw = """{"id":"abc","model":"agnes-3.0-flash"}"""
        val chunk = json.decodeFromString(ChatCompletionChunk.serializer(), raw)
        assertTrue(chunk.choices.isEmpty())
    }
}
