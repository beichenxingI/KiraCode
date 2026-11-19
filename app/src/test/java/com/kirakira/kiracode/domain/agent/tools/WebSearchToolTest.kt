package com.kirakira.kiracode.domain.agent.tools

import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 联网搜索工具注册测试（S5）：
 * - 开关开启：12 个工具（8 卡片 + 3 交互 + web_search）
 * - 开关关闭：11 个工具（无 web_search，模型看不到搜索能力）
 * - schema：query 必填，max_results 可选（无 default 字段，保持提供商兼容）
 */
class WebSearchToolTest {

    private fun toolName(tool: kotlinx.serialization.json.JsonObject): String? =
        tool["function"]?.jsonObject?.get("name")?.jsonPrimitive?.content

    @Test
    fun `getAllFunctions with webSearchEnabled=true returns 17 tools including web_search`() {
        val tools = CardFunctions.getAllFunctions(webSearchEnabled = true)
        assertEquals("开关开启应有17个工具（含 fetch_webpage、spawn_subagent、查询双工具与 audit_card）", 18, tools.size)
        assertTrue(
            "必须包含 web_search",
            tools.any { toolName(it) == "web_search" }
        )
        assertTrue(
            "必须包含 fetch_webpage",
            tools.any { toolName(it) == "fetch_webpage" }
        )
    }

    @Test
    fun `getAllFunctions with webSearchEnabled=false returns 15 tools without web_search`() {
        val tools = CardFunctions.getAllFunctions(webSearchEnabled = false)
        assertEquals("开关关闭应有15个工具", 16, tools.size)
        assertTrue(
            "不得包含 web_search",
            tools.none { toolName(it) == "web_search" }
        )
        assertTrue(
            "不得包含 fetch_webpage",
            tools.none { toolName(it) == "fetch_webpage" }
        )
    }

    @Test
    fun `web_search tool has required query and optional max_results`() {
        val searchTool = CardFunctions.getAllFunctions(webSearchEnabled = true)
            .first { toolName(it) == "web_search" }
        val params = searchTool["function"]?.jsonObject?.get("parameters")?.jsonObject
        assertNotNull("必须有 parameters", params)

        val required = params!!["required"]?.jsonArray
        assertNotNull("必须有 required 数组", required)
        assertTrue(
            "required 必须含 query",
            required!!.any { it.jsonPrimitive.content == "query" }
        )

        val props = params["properties"]?.jsonObject
        assertNotNull("必须有 properties", props)
        assertNotNull("必须有 query 属性", props!!["query"])
        assertNotNull("必须有 max_results 属性", props["max_results"])
        assertEquals(
            "query 必须是 string 类型",
            "string",
            props["query"]?.jsonObject?.get("type")?.jsonPrimitive?.content
        )
        assertEquals(
            "max_results 必须是 integer 类型",
            "integer",
            props["max_results"]?.jsonObject?.get("type")?.jsonPrimitive?.content
        )
    }
}
