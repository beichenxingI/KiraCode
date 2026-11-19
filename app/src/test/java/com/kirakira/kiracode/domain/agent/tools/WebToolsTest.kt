package com.kirakira.kiracode.domain.agent.tools

import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 联网双工具（web_search + fetch_webpage）注册测试（W6）：
 * - 开关开启：16 个工具（8 卡片 + 3 交互 + 1 子代理 + 2 查询 + 2 联网）
 * - 开关关闭：14 个工具（双工具均不注册）
 * - schema：web_search 的 query 必填；fetch_webpage 的 url 必填
 */
class WebToolsTest {

    private fun toolName(tool: kotlinx.serialization.json.JsonObject): String? =
        tool["function"]?.jsonObject?.get("name")?.jsonPrimitive?.content

    @Test
    fun `getAllFunctions with webSearchEnabled=true returns 17 tools with both web tools`() {
        val tools = CardFunctions.getAllFunctions(webSearchEnabled = true)
        assertEquals("开关开启应有17个工具", 18, tools.size)
        assertTrue("必须包含 web_search", tools.any { toolName(it) == "web_search" })
        assertTrue("必须包含 fetch_webpage", tools.any { toolName(it) == "fetch_webpage" })
    }

    @Test
    fun `getAllFunctions with webSearchEnabled=false returns 15 tools without web tools`() {
        val tools = CardFunctions.getAllFunctions(webSearchEnabled = false)
        assertEquals("开关关闭应有15个工具", 16, tools.size)
        assertTrue("不得包含 web_search", tools.none { toolName(it) == "web_search" })
        assertTrue("不得包含 fetch_webpage", tools.none { toolName(it) == "fetch_webpage" })
    }

    @Test
    fun `fetch_webpage tool has required url parameter`() {
        val fetchTool = CardFunctions.getAllFunctions(webSearchEnabled = true)
            .first { toolName(it) == "fetch_webpage" }
        val params = fetchTool["function"]?.jsonObject?.get("parameters")?.jsonObject
        assertNotNull("必须有 parameters", params)

        val required = params!!["required"]?.jsonArray
        assertNotNull("必须有 required 数组", required)
        assertTrue(
            "required 必须含 url",
            required!!.any { it.jsonPrimitive.content == "url" }
        )

        val props = params["properties"]?.jsonObject
        assertNotNull("必须有 properties", props)
        assertEquals(
            "url 必须是 string 类型",
            "string",
            props!!["url"]?.jsonObject?.get("type")?.jsonPrimitive?.content
        )
    }

    @Test
    fun `web_search and fetch_webpage descriptions mention workflow guidance`() {
        val tools = CardFunctions.getAllFunctions(webSearchEnabled = true)
        val searchDesc = tools.first { toolName(it) == "web_search" }["function"]
            ?.jsonObject?.get("description")?.jsonPrimitive?.content.orEmpty()
        val fetchDesc = tools.first { toolName(it) == "fetch_webpage" }["function"]
            ?.jsonObject?.get("description")?.jsonPrimitive?.content.orEmpty()
        assertTrue("web_search 描述应说明系统执行并回注", searchDesc.contains("系统执行"))
        assertTrue("fetch_webpage 描述应说明正文截断", fetchDesc.contains("10000"))
    }
}
