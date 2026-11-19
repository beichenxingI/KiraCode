package com.kirakira.kiracode.domain.agent

import com.kirakira.kiracode.domain.agent.tools.CardFunctions
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P0 YOLO 非交互测试：全局模式下不注册 ask_user/submit_plan/complete_step，
 * 修改类工具照常注册（自动执行由内核 Diff 审查的 YOLO 分支保证）。
 */
class YoloNonInteractiveTest {

    private fun names(functions: List<JsonObject>) =
        functions.map { it["function"]!!.jsonObject["name"]!!.jsonPrimitive.content }

    @Test
    fun `yolo registers no interactive tools`() {
        val names = names(CardFunctions.getAllFunctions(webSearchEnabled = false, includeInteractiveTools = false))
        assertFalse(names.contains("ask_user"))
        assertFalse(names.contains("submit_plan"))
        assertFalse(names.contains("complete_step"))
    }

    @Test
    fun `yolo still registers modification and analysis tools`() {
        val names = names(CardFunctions.getAllFunctions(webSearchEnabled = false, includeInteractiveTools = false))
        assertTrue(names.containsAll(listOf(
            "update_card_field", "update_card_book_add", "update_card_book_update",
            "update_card_book_delete", "update_card_regex_add", "update_card_regex_delete",
            "update_card_cover", "replace_card_json", "spawn_subagent",
            "query_card_entries", "get_card_entry", "audit_card"
        )))
        assertEquals(13, names.size)
    }

    @Test
    fun `agent keeps interactive tools`() {
        val names = names(CardFunctions.getAllFunctions(webSearchEnabled = false, includeInteractiveTools = true))
        assertTrue(names.containsAll(listOf("ask_user", "submit_plan", "complete_step")))
        assertEquals(16, names.size)
    }

    @Test
    fun `web tools follow switch regardless of interactivity`() {
        assertTrue(names(CardFunctions.getAllFunctions(webSearchEnabled = true, includeInteractiveTools = false))
            .containsAll(listOf("web_search", "fetch_webpage")))
        assertTrue(names(CardFunctions.getAllFunctions(webSearchEnabled = false, includeInteractiveTools = true))
            .none { it == "web_search" || it == "fetch_webpage" })
    }
}
