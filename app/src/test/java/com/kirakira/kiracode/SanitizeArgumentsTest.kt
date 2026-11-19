package com.kirakira.kiracode

import com.kirakira.kiracode.data.remote.dto.ChatCompletionRequest
import com.kirakira.kiracode.data.remote.dto.ChatMessage
import com.kirakira.kiracode.data.remote.dto.FunctionCall
import com.kirakira.kiracode.data.remote.dto.ToolCall
import com.kirakira.kiracode.domain.agent.sanitizeArguments
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

class SanitizeArgumentsTest {

    @Test
    fun `valid arguments stay byte for byte unchanged`() {
        val raw = """{"findRegex":"/thought([\\s\\S]*?)<\\/thought>/g","replaceString":"<details>\n${'$'}1\n</details>"}"""

        assertEquals(raw, sanitizeArguments(raw))

        val pretty = "{\n  \"name\": \"心理活动-折叠\"\n}"
        assertEquals(pretty, sanitizeArguments(pretty))
    }

    @Test
    fun `raw newlines and tabs inside multiline html are escaped`() {
        val raw = "{\"replaceString\":\"<div>\r\n\t<p>内容</p>\n</div>\"}"

        val sanitized = sanitizeArguments(raw)
        val parsed = Json.parseToJsonElement(sanitized).jsonObject

        assertEquals("{\"replaceString\":\"<div>\\n\\t<p>内容</p>\\n</div>\"}", sanitized)
        assertEquals("<div>\n\t<p>内容</p>\n</div>", parsed.getValue("replaceString").jsonPrimitive.content)
    }

    @Test
    fun `outer json indentation is preserved while inner raw newlines are repaired`() {
        val raw = "{\n" +
            "  \"script_name\": \"前端\",\n" +
            "  \"replaceString\": \"<div>\n<p>内容</p>\n</div>\",\n" +
            "  \"slot\": \"${'$'}1\"\n" +
            "}"

        val sanitized = sanitizeArguments(raw)
        val parsed = Json.parseToJsonElement(sanitized).jsonObject

        assertTruePrefix("{\n  \"script_name\"", sanitized)
        assertEquals("<div>\n<p>内容</p>\n</div>", parsed.getValue("replaceString").jsonPrimitive.content)
        assertEquals("${'$'}1", parsed.getValue("slot").jsonPrimitive.content)
    }

    @Test
    fun `unrepairable arguments fall back to empty object`() {
        assertEquals("{}", sanitizeArguments("not-json"))
        assertEquals("{}", sanitizeArguments(""))
    }

    @Test
    fun `sanitized arguments remain valid json after request dto serialization`() {
        val raw = "{\"replaceString\":\"<div>\n<p>内容</p>\n</div>\"}"
        val request = ChatCompletionRequest(
            model = "test",
            messages = listOf(
                ChatMessage(
                    role = "assistant",
                    content = null,
                    tool_calls = listOf(
                        ToolCall(
                            id = "call_1",
                            function = FunctionCall(
                                name = "update_card_regex_add",
                                arguments = sanitizeArguments(raw)
                            )
                        )
                    )
                )
            )
        )

        val encoded = Json.encodeToString(ChatCompletionRequest.serializer(), request)
        val decoded = Json.decodeFromString(ChatCompletionRequest.serializer(), encoded)
        val roundTrippedArguments = decoded.messages.single().tool_calls!!.single().function.arguments

        Json.parseToJsonElement(roundTrippedArguments)
        assertEquals(sanitizeArguments(raw), roundTrippedArguments)
    }

    private fun assertTruePrefix(prefix: String, actual: String) {
        if (!actual.startsWith(prefix)) {
            throw AssertionError("Expected prefix <$prefix> but was <$actual>")
        }
    }
}
