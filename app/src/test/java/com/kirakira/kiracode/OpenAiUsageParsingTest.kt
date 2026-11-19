package com.kirakira.kiracode

import com.kirakira.kiracode.data.remote.dto.ChatCompletionChunk
import com.kirakira.kiracode.data.remote.dto.Usage
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

/** Usage 缓存字段解析回归：OpenAI/DeepSeek 明细与 Anthropic 兼容字段。 */
class OpenAiUsageParsingTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Test
    fun `parses openai prompt tokens details cached tokens`() {
        val chunk = json.decodeFromString<ChatCompletionChunk>(
            """{"choices":[],"usage":{"prompt_tokens":100,"completion_tokens":20,"total_tokens":120,"prompt_tokens_details":{"cached_tokens":64}}}"""
        )

        assertEquals(64, chunk.usage?.cachedTokens)
    }

    @Test
    fun `parses anthropic style cache read input tokens`() {
        val usage = json.decodeFromString<Usage>(
            """{"prompt_tokens":100,"completion_tokens":20,"total_tokens":120,"cache_read_input_tokens":80}"""
        )

        assertEquals(80, usage.cachedTokens)
    }

    @Test
    fun `usage without cache fields defaults to zero`() {
        val usage = json.decodeFromString<Usage>(
            """{"prompt_tokens":100,"completion_tokens":20,"total_tokens":120}"""
        )

        assertEquals(0, usage.cachedTokens)
    }

    @Test
    fun `prompt tokens details takes precedence over anthropic field`() {
        val usage = json.decodeFromString<Usage>(
            """{"prompt_tokens":100,"completion_tokens":20,"total_tokens":120,"prompt_tokens_details":{"cached_tokens":64},"cache_read_input_tokens":80}"""
        )

        assertEquals(64, usage.cachedTokens)
    }
}
