package com.kirakira.kiracode.domain.agent.tools

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CardFunctionsTest {

    private val json = Json { prettyPrint = true }

    @Test
    fun `8个function定义都存在且格式正确`() {
        val functions = CardFunctions.getAllFunctions()

        // 验证数量（V2/V3：9 卡片 + 3 交互 + 1 子代理 + 2 查询 + 1 审计 + web_search/fetch_webpage 联网双工具）
        assertEquals("应有18个function", 18, functions.size)

        // 验证每个function的基本结构
        functions.forEach { func ->
            assertTrue("必须有type=function", func["type"]?.jsonPrimitive?.content == "function")

            val funcDef = func["function"]?.jsonObject
            assertNotNull("必须有function对象", funcDef)

            val name = funcDef!!["name"]?.jsonPrimitive?.content
            assertNotNull("必须有name", name)

            val desc = funcDef["description"]?.jsonPrimitive?.content
            assertNotNull("必须有description", desc)

            val params = funcDef["parameters"]?.jsonObject
            assertNotNull("必须有parameters", params)

            val props = params!!["properties"]?.jsonObject
            assertNotNull("必须有properties", props)

            val required = params["required"]?.jsonArray
            assertNotNull("必须有required数组", required)

            println("✅ ${name}: ${desc?.take(50)}...")
        }
    }

    @Test
    fun `book_add包含所有V3字段`() {
        val bookAdd = CardFunctions.updateCardBookAdd
        val props = bookAdd["function"]?.jsonObject
            ?.get("parameters")?.jsonObject
            ?.get("properties")?.jsonObject

        assertNotNull(props)

        // spec级必需字段
        assertNotNull("必须有comment", props!!["comment"])
        assertNotNull("必须有keys", props["keys"])
        assertNotNull("必须有content", props["content"])

        // spec级可选字段
        assertNotNull("必须有secondary_keys", props["secondary_keys"])
        assertNotNull("必须有constant", props["constant"])
        assertNotNull("必须有selective", props["selective"])
        assertNotNull("必须有insertion_order", props["insertion_order"])
        assertNotNull("必须有position", props["position"])

        // extensions常用字段（12个）
        assertNotNull("必须有case_sensitive", props["case_sensitive"])
        assertNotNull("必须有match_whole_words", props["match_whole_words"])
        assertNotNull("必须有scan_depth", props["scan_depth"])
        assertNotNull("必须有automation_id", props["automation_id"])
        assertNotNull("必须有probability", props["probability"])
        assertNotNull("必须有use_probability", props["use_probability"])
        assertNotNull("必须有selective_logic", props["selective_logic"])
        assertNotNull("必须有exclude_recursion", props["exclude_recursion"])
        assertNotNull("必须有prevent_recursion", props["prevent_recursion"])
        assertNotNull("必须有group", props["group"])
        assertNotNull("必须有group_override", props["group_override"])
        assertNotNull("必须有group_weight", props["group_weight"])

        println("✅ book_add字段完整（spec 8个 + extensions 12个）")
    }

    @Test
    fun `regex_add包含所有协议字段`() {
        val regexAdd = CardFunctions.updateCardRegexAdd
        val props = regexAdd["function"]?.jsonObject
            ?.get("parameters")?.jsonObject
            ?.get("properties")?.jsonObject

        assertNotNull(props)

        // 必需字段
        assertNotNull("必须有script_name", props!!["script_name"])
        assertNotNull("必须有find_regex", props["find_regex"])
        assertNotNull("必须有replace_string", props["replace_string"])
        assertNotNull("必须有placement", props["placement"])

        // 扩展字段（8个）
        assertNotNull("必须有trim_strings", props["trim_strings"])
        assertNotNull("必须有disabled", props["disabled"])
        assertNotNull("必须有markdown_only", props["markdown_only"])
        assertNotNull("必须有prompt_only", props["prompt_only"])
        assertNotNull("必须有run_on_edit", props["run_on_edit"])
        assertNotNull("必须有substitute_regex", props["substitute_regex"])
        assertNotNull("必须有min_depth", props["min_depth"])
        assertNotNull("必须有max_depth", props["max_depth"])

        val replaceDesc = props["replace_string"]?.jsonObject
            ?.get("description")?.jsonPrimitive?.content.orEmpty()
        assertTrue(replaceDesc.contains("FULL_FRONTEND 模式必须是 ```html"))
        assertTrue(replaceDesc.contains("轻量内联美化"))

        // 验证substitute_regex是integer类型
        val subRegex = props["substitute_regex"]?.jsonObject
        assertEquals("substitute_regex必须是integer",
            "integer",
            subRegex?.get("type")?.jsonPrimitive?.content)

        println("✅ regex_add字段完整（4必需 + 8扩展）")
    }

    @Test
    fun `regex update exposes identifier and full replacement fields`() {
        val function = CardFunctions.updateCardRegexUpdate["function"]?.jsonObject!!
        val params = function["parameters"]?.jsonObject!!
        val props = params["properties"]?.jsonObject!!
        val required = params["required"]?.jsonArray!!.map { it.jsonPrimitive.content }

        assertEquals("update_card_regex_update", function["name"]?.jsonPrimitive?.content)
        listOf("name_or_id", "find_regex", "replace_string").forEach {
            assertTrue("update required must contain $it", it in required)
            assertNotNull("update must expose $it", props[it])
        }
        listOf("script_name", "placement", "markdown_only", "prompt_only", "run_on_edit").forEach {
            assertNotNull("update must expose $it", props[it])
        }
        val replaceDesc = props["replace_string"]?.jsonObject?.get("description")?.jsonPrimitive?.content.orEmpty()
        assertTrue(replaceDesc.contains("FULL_FRONTEND"))
        assertTrue(replaceDesc.contains("完整单文件 HTML"))
        assertTrue(replaceDesc.contains("无需围栏"))
        assertTrue(replaceDesc.contains("标准单次转义为 \\n"))
        assertTrue(replaceDesc.contains("不要双重转义成 \\\\n"))
    }

    @Test
    fun `field白名单包含所有V3顶层字段`() {
        val fieldUpdate = CardFunctions.updateCardField
        val fieldEnum = fieldUpdate["function"]?.jsonObject
            ?.get("parameters")?.jsonObject
            ?.get("properties")?.jsonObject
            ?.get("field")?.jsonObject
            ?.get("enum")?.jsonArray

        assertNotNull(fieldEnum)

        val requiredFields = listOf(
            "name", "description", "personality", "scenario",
            "first_mes", "mes_example", "creator_notes", "system_prompt",
            "post_history_instructions", "alternate_greetings", "tags",
            "creator", "character_version", "nickname"
        )

        requiredFields.forEach { field ->
            assertTrue("必须包含$field",
                fieldEnum!!.any { it.jsonPrimitive.content == field })
        }

        println("✅ field白名单完整（14个）")
    }

    @Test
    fun `列表字段分隔规则与执行器一致`() {
        // P0：alternate_greetings 执行器按换行切分（CardOperationApplier.applySetField），
        // 工具描述不得宣传逗号分隔
        val valueDesc = CardFunctions.updateCardField["function"]?.jsonObject
            ?.get("parameters")?.jsonObject
            ?.get("properties")?.jsonObject
            ?.get("value")?.jsonObject
            ?.get("description")?.jsonPrimitive?.content.orEmpty()
        assertTrue("value 描述必须说明 alternate_greetings 按换行分隔",
            valueDesc.contains("alternate_greetings 每条一行"))
    }

    @Test
    fun `book_add与book_update的priority链路存在且一致`() {
        // P0：priority 必须真实存在于 schema（不得只在 Prompt 虚构）
        val addProps = CardFunctions.updateCardBookAdd["function"]?.jsonObject
            ?.get("parameters")?.jsonObject?.get("properties")?.jsonObject
        val updateProps = CardFunctions.updateCardBookUpdate["function"]?.jsonObject
            ?.get("parameters")?.jsonObject?.get("properties")?.jsonObject
        assertNotNull("book_add 必须有 priority", addProps!!["priority"])
        assertNotNull("book_update 必须有 priority", updateProps!!["priority"])
    }

    @Test
    fun `book_update声明整段替换语义`() {
        val desc = CardFunctions.updateCardBookUpdate["function"]?.jsonObject
            ?.get("description")?.jsonPrimitive?.content.orEmpty()
        assertTrue("book_update 描述必须声明 content 为整段替换", desc.contains("整段替换"))
        assertTrue("book_update 描述必须要求改前读原文", desc.contains("get_card_entry"))
    }

    @Test
    fun `大卡读取工具声明使用范围与截断风险`() {
        assertTrue("query 描述必须限定大卡场景", queryCardEntries["function"]?.jsonObject
            ?.get("description")?.jsonPrimitive?.content.orEmpty().contains("完整 JSON 已注入"))
        assertTrue("get 描述必须限定大卡场景", getCardEntry["function"]?.jsonObject
            ?.get("description")?.jsonPrimitive?.content.orEmpty().contains("完整 JSON 已注入"))
        assertTrue("get 描述必须声明读取预算（1-3条）", getCardEntry["function"]?.jsonObject
            ?.get("description")?.jsonPrimitive?.content.orEmpty().contains("1-3 条"))
        assertTrue("get 描述必须声明截断风险", getCardEntry["function"]?.jsonObject
            ?.get("description")?.jsonPrimitive?.content.orEmpty().contains("截断"))
    }

    @Test
    fun `placement描述包含REASONING且无数字enum`() {
        val regexAdd = CardFunctions.updateCardRegexAdd
        val placement = regexAdd["function"]?.jsonObject
            ?.get("parameters")?.jsonObject
            ?.get("properties")?.jsonObject
            ?.get("placement")?.jsonObject

        assertNotNull(placement)
        // REASONING 信息在描述中（数字型 enum 已移除）
        val desc = placement!!["description"]?.jsonPrimitive?.content.orEmpty()
        assertTrue("描述必须包含REASONING(6=思考)", desc.contains("6=思考"))
        val itemsEnum = placement["items"]?.jsonObject?.get("enum")?.jsonArray
        assertNull("placement 不得使用数字型 enum（提供商校验器拒绝）", itemsEnum)

        println("✅ placement描述含REASONING且无数字enum")
    }

    @Test
    fun `所有function无数字enum且无default字段（提供商兼容）`() {
        CardFunctions.getAllFunctions().forEach { func ->
            val props = func["function"]?.jsonObject
                ?.get("parameters")?.jsonObject
                ?.get("properties")?.jsonObject
            assertNotNull(props)
            props!!.forEach { (name, prop) ->
                val obj = prop.jsonObject
                val enumArr = obj["enum"]?.jsonArray
                if (enumArr != null) {
                    enumArr.forEach { v ->
                        assertTrue(
                            "$name 的 enum 含非字符串值（提供商校验器拒绝数字型 enum）",
                            v.jsonPrimitive.isString
                        )
                    }
                }
                assertNull("$name 含 default 字段（Gemini 风格 Schema 不支持）", obj["default"])
            }
        }
        println("✅ 所有function：enum全字符串 + 无default字段")
    }
}
