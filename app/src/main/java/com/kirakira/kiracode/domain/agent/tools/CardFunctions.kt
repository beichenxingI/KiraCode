package com.kirakira.kiracode.domain.agent.tools

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * 8个function定义（OpenAI function calling格式）
 * 参考：ST V3格式分析报告（DiaoYan/ST_V3格式分析.md）
 */

// ══════════════════════════════════════════════════════════════
// Function 1: update_card_field - 修改角色卡顶层字段
// ══════════════════════════════════════════════════════════════
object CardFunctions {

val updateCardField: JsonObject = buildJsonObject {
    put("type", "function")
    putJsonObject("function") {
        put("name", "update_card_field")
        put("description", """
            更新角色卡的单个顶层文本字段。这是修改 name、description、personality、scenario、first_mes、mes_example、system_prompt 等字段的默认工具。

            参数：
            - field：字段名，仅限 schema enum 中的值
            - value：新内容；alternate_greetings 用换行符分隔多条
            - ai_guess：自动草稿或自行补全、未与用户确认时设为 true

            不要用于：
            - 整卡替换（用 replace_card_json）
            - 世界书条目（用 update_card_book_*）
            - 正则脚本（用 update_card_regex_*）
            - 内容与当前完全相同时
            - 用户没有要求修改时覆盖核心设定

            field 不合法时返回错误，不会创建新字段。
        """.trimIndent())
        putJsonObject("parameters") {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("field") {
                    put("type", "string")
                    put("description", "字段名（蛇形命名，如first_mes）")
                    putJsonArray("enum") {
                        // 14个必需字段 + nickname
                        add(JsonPrimitive("name")); add(JsonPrimitive("description")); add(JsonPrimitive("personality"))
                        add(JsonPrimitive("scenario")); add(JsonPrimitive("first_mes")); add(JsonPrimitive("mes_example"))
                        add(JsonPrimitive("creator_notes")); add(JsonPrimitive("system_prompt"))
                        add(JsonPrimitive("post_history_instructions"))
                        add(JsonPrimitive("alternate_greetings")); add(JsonPrimitive("tags"))
                        add(JsonPrimitive("creator")); add(JsonPrimitive("character_version")); add(JsonPrimitive("nickname"))
                    }
                }
                putJsonObject("value") {
                    put("type", "string")
                    put("description", "新值。列表字段分隔规则：tags 用逗号分隔；alternate_greetings 每条一行（换行分隔，不能用逗号）。其余字段为完整文本（多行可含换行）")
                }
                putJsonObject("ai_guess") {
                    put("type", "boolean")
                    put("description", "是否为 AI 脑补（自行生成、未与用户确认的内容）。自动草稿模式下必须为 true；正常按用户要求生成时省略")
                }
            }
            putJsonArray("required") { add(JsonPrimitive("field")); add(JsonPrimitive("value")) }
        }
    }
}

// ══════════════════════════════════════════════════════════════
// Function 2: update_card_book_add - 添加世界书条目
// ══════════════════════════════════════════════════════════════
val updateCardBookAdd: JsonObject = buildJsonObject {
    put("type", "function")
    putJsonObject("function") {
        put("name", "update_card_book_add")
        put("description", """
            新增一条世界书条目。一个主题对应一条条目，条目正文应能脱离其他条目独立阅读。

            参数：
            - keys：触发关键词字符串数组；用户或 AI 输出包含这些词时条目被注入
            - content：条目正文；建议 ≤300 字，超长请拆分
            - insertion_order：注入顺序，数值越大越靠近上下文末尾、权重越高
            - constant：仅当每轮都需要时设为 true；过多 constant 条目会挤占上下文

            不要用于：
            - 修改已有条目（用 update_card_book_update）
            - 同一主题重复添加
            - 用户未要求时批量添加
            - content 超过 500 字时不拆分直接添加
        """.trimIndent())
        putJsonObject("parameters") {
            put("type", "object")
            putJsonObject("properties") {
                // ──── spec级必需字段 ────
                putJsonObject("comment") {
                    put("type", "string")
                    put("description", "条目简述/标题（中文，建议15字以内）")
                }
                putJsonObject("keys") {
                    put("type", "array")
                    putJsonObject("items") { put("type", "string") }
                    put("description", "主触发词列表（中文关键词，如['魔法','法术']）")
                }
                putJsonObject("content") {
                    put("type", "string")
                    put("description", "条目正文（必须自包含：触发词本身不会注入上下文，正文需独立可读；不少于50字，建议≤300字）")
                }
                // ──── spec级可选字段 ────
                putJsonObject("secondary_keys") {
                    put("type", "array")
                    putJsonObject("items") { put("type", "string") }
                    put("description", "次级触发词（需selective=true时生效）")
                }
                putJsonObject("constant") {
                    put("type", "boolean")
                    put("description", "是否总是注入（蓝灯，无视触发词），默认 false")
                }
                putJsonObject("selective") {
                    put("type", "boolean")
                    put("description", "是否选择性激活（需主触发词+次级触发词同时出现），默认 true")
                }
                putJsonObject("insertion_order") {
                    put("type", "integer")
                    put("description", "插入顺序（0-999）：数值越大越贴近上下文末尾、影响越强；默认 100")
                }
                putJsonObject("position") {
                    put("type", "string")
                    putJsonArray("enum") { add(JsonPrimitive("before_char")); add(JsonPrimitive("after_char")) }
                    put("description", "注入位置：before_char=角色卡前，after_char=角色卡后，默认 before_char")
                }
                putJsonObject("priority") {
                    put("type", "integer")
                    put("description", "注入优先级（数值越大越重要；预算紧张时优先丢弃低值条目），默认 10")
                }
                putJsonObject("ai_guess") {
                    put("type", "boolean")
                    put("description", "是否为 AI 脑补（自行生成、未与用户确认的内容）。自动草稿模式下必须为 true")
                }
                // ──── extensions常用字段（V3新增，12个） ────
                putJsonObject("case_sensitive") {
                    put("type", "boolean")
                    put("description", "是否区分大小写匹配触发词")
                }
                putJsonObject("match_whole_words") {
                    put("type", "boolean")
                    put("description", "是否整词匹配（避免部分匹配）")
                }
                putJsonObject("scan_depth") {
                    put("type", "integer")
                    put("description", "条目级扫描深度（覆盖书级设置）")
                }
                putJsonObject("automation_id") {
                    put("type", "string")
                    put("description", "自动化ID（用于快速动作等高级功能）")
                }
                putJsonObject("probability") {
                    put("type", "integer")
                    put("description", "触发概率（0-100，需use_probability=true）")
                }
                putJsonObject("use_probability") {
                    put("type", "boolean")
                    put("description", "是否启用概率触发")
                }
                putJsonObject("selective_logic") {
                    put("type", "integer")
                    put("description", "选择性逻辑，取值必须是 0/1/2/3 之一：0=AND_ANY（默认），1=NOT_ALL，2=NOT_ANY，3=AND_ALL")
                }
                putJsonObject("exclude_recursion") {
                    put("type", "boolean")
                    put("description", "排除递归扫描")
                }
                putJsonObject("prevent_recursion") {
                    put("type", "boolean")
                    put("description", "阻止递归扫描")
                }
                putJsonObject("group") {
                    put("type", "string")
                    put("description", "分组名（用于条目分组管理）")
                }
                putJsonObject("group_override") {
                    put("type", "boolean")
                    put("description", "覆盖分组默认设置")
                }
                putJsonObject("group_weight") {
                    put("type", "integer")
                    put("description", "分组权重")
                }
            }
            putJsonArray("required") { add(JsonPrimitive("comment")); add(JsonPrimitive("keys")); add(JsonPrimitive("content")) }
        }
    }
}

// ══════════════════════════════════════════════════════════════
// Function 3: update_card_book_update - 修改已有世界书条目
// ══════════════════════════════════════════════════════════════
val updateCardBookUpdate: JsonObject = buildJsonObject {
    put("type", "function")
    putJsonObject("function") {
        put("name", "update_card_book_update")
        put("description", """
            修改一条已存在的世界书条目。uid 定位条目，content 是整段替换。

            调用前必须用 get_card_entry 读取完整原文，不要根据索引摘要重写。

            参数：
            - uid：条目的稳定 ID（来自卡片现状或 query_card_entries 的结果，不是条目标题）
            - content：新的完整正文（整段替换，不是追加）
            - 其他字段（keys、insertion_order 等）：只在需要修改时传入

            不要用于：
            - 未读取完整原文就修改 content
            - uid 不存在的条目（用 update_card_book_add）
            - 只想改触发词或顺序时重写 content
            - 把截断内容当完整原文覆盖

            uid 不存在时返回错误，不会创建新条目。
        """.trimIndent())
        putJsonObject("parameters") {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("uid") {
                    put("type", "integer")
                    put("description", "条目唯一ID（从已有条目中获取）")
                }
                // 其余字段同book.add（全部可选）
                putJsonObject("comment") { put("type", "string") }
                putJsonObject("keys") {
                    put("type", "array")
                    putJsonObject("items") { put("type", "string") }
                }
                putJsonObject("content") { put("type", "string") }
                putJsonObject("secondary_keys") {
                    put("type", "array")
                    putJsonObject("items") { put("type", "string") }
                }
                putJsonObject("constant") { put("type", "boolean") }
                putJsonObject("selective") { put("type", "boolean") }
                putJsonObject("insertion_order") { put("type", "integer") }
                putJsonObject("priority") { put("type", "integer") }
                putJsonObject("position") {
                    put("type", "string")
                    putJsonArray("enum") { add(JsonPrimitive("before_char")); add(JsonPrimitive("after_char")) }
                }
                // extensions字段（同book.add的12个）
                putJsonObject("case_sensitive") { put("type", "boolean") }
                putJsonObject("match_whole_words") { put("type", "boolean") }
                putJsonObject("scan_depth") { put("type", "integer") }
                putJsonObject("automation_id") { put("type", "string") }
                putJsonObject("probability") { put("type", "integer") }
                putJsonObject("use_probability") { put("type", "boolean") }
                putJsonObject("selective_logic") { put("type", "integer") }
                putJsonObject("exclude_recursion") { put("type", "boolean") }
                putJsonObject("prevent_recursion") { put("type", "boolean") }
                putJsonObject("group") { put("type", "string") }
                putJsonObject("group_override") { put("type", "boolean") }
                putJsonObject("group_weight") { put("type", "integer") }
            }
            putJsonArray("required") { add(JsonPrimitive("uid")) }
        }
    }
}

// ══════════════════════════════════════════════════════════════
// Function 4: update_card_book_delete - 删除世界书条目
// ══════════════════════════════════════════════════════════════
val updateCardBookDelete: JsonObject = buildJsonObject {
    put("type", "function")
    putJsonObject("function") {
        put("name", "update_card_book_delete")
        put("description", "删除角色卡中的一条世界书条目")
        putJsonObject("parameters") {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("uid") {
                    put("type", "integer")
                    put("description", "要删除的条目ID")
                }
            }
            putJsonArray("required") { add(JsonPrimitive("uid")) }
        }
    }
}

// ══════════════════════════════════════════════════════════════
// Function 5: update_card_regex_add - 添加正则脚本
// ══════════════════════════════════════════════════════════════
val updateCardRegexAdd: JsonObject = buildJsonObject {
    put("type", "function")
    putJsonObject("function") {
        put("name", "update_card_regex_add")
        put("description", """
            新增一条正则脚本。正则运行在 SillyTavern 渲染层，处理 SillyTavern 里的 AI 已经输出的文字。

            必须遵循系统提示词当前选定的正则生成模式：状态前端 / 语义折叠 / 折叠思维链 / 轻量着色。
            每次调用只添加当前模式要求的一条独立脚本；需要多条时分次调用，不把脚本数组拼进一次调用。

            配套要求：
            1. 输出格式协议由应用自动、幂等追加到 description 末尾，不依赖 system_prompt 或世界书；不要重复手写该协议。
            2. 在 mes_example 里至少一段示例对话演示当前模式的正确输出格式。
            3. 显示美化使用 placement=[2]、markdown_only=true、prompt_only=false、disabled=false。

            参数：
            - script_name：脚本名称，用于识别和管理
            - find_regex：JS 正则语法；用 () 定义捕获组，替换时用 $1 $2 引用
            - replace_string：替换模板；$1 引用第一个捕获组，$0 引用整个匹配；可以包含 HTML
            - placement：作用位置数组，常用 [2]；1=用户输入，2=AI输出，3=命令，5=世界信息，6=思考
            - trim_strings：需要截断的字符串数组（可选）

            不要用于：
            - 在 SillyTavern 里的 AI 不可能输出的格式上写正则
            - 用正则承担世界书的内容职责（内容归世界书，格式渲染归正则）
            - 用工具校验限制视觉风格；题材和用户要求可自由决定设计方向
            """.trimIndent())
        putJsonObject("parameters") {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("script_name") {
                    put("type", "string")
                    put("description", "脚本名称")
                }
                putJsonObject("find_regex") {
                    put("type", "string")
                    put("description", "查找正则表达式（JavaScript正则语法）")
                }
                putJsonObject("replace_string") {
                    put("type", "string")
                    put("description", "替换字符串（可用${'$'}1、${'$'}2等捕获组）。FULL_FRONTEND 模式必须是 ```html 围栏包裹的完整单文件 HTML，并以同格式围栏结束；解析后的字符串必须保留真实换行。JSON 参数中换行按标准单次转义为 \\n，不要双重转义成 \\\\n。轻量内联美化（<details>/<span>）直接写标签，无需围栏。")
                }
                putJsonObject("placement") {
                    put("type", "array")
                    putJsonObject("items") {
                        put("type", "integer")
                        put("description", "取值必须是 1/2/3/5/6 之一")
                    }
                    put("description", "作用位置数组，每项取值必须是 1/2/3/5/6 之一：1=用户输入，2=AI输出，3=命令，5=世界信息，6=思考（推理输出）；默认 [2]（AI输出）")
                }
                // ──── 模型已支持但协议不教的8个字段 ────
                putJsonObject("trim_strings") {
                    put("type", "array")
                    putJsonObject("items") { put("type", "string") }
                    put("description", "截断字符串列表")
                }
                putJsonObject("disabled") {
                    put("type", "boolean")
                    put("description", "是否禁用此脚本，默认 false")
                }
                putJsonObject("markdown_only") {
                    put("type", "boolean")
                    put("description", "仅在Markdown模式生效；美化类规则必须为 true（HTML 美化未显式传值时自动安全默认为 true）")
                }
                putJsonObject("prompt_only") {
                    put("type", "boolean")
                    put("description", "仅在Prompt生效；美化类规则必须为 false（HTML 美化未显式传值时自动安全默认为 false）")
                }
                putJsonObject("run_on_edit") {
                    put("type", "boolean")
                    put("description", "编辑消息时运行，默认 false")
                }
                putJsonObject("substitute_regex") {
                    put("type", "integer")
                    put("description", "替换模式，取值必须是 0/1/2 之一：0=NONE（默认），1=RAW，2=ESCAPED")
                }
                putJsonObject("min_depth") {
                    put("type", "integer")
                    put("description", "最小作用深度")
                }
                putJsonObject("max_depth") {
                    put("type", "integer")
                    put("description", "最大作用深度")
                }
            }
            putJsonArray("required") { add(JsonPrimitive("script_name")); add(JsonPrimitive("find_regex")); add(JsonPrimitive("replace_string")) }
        }
    }
}

// ══════════════════════════════════════════════════════════════
// Function 6: update_card_regex_update - 更新正则脚本
// ══════════════════════════════════════════════════════════════
val updateCardRegexUpdate: JsonObject = buildJsonObject {
    put("type", "function")
    putJsonObject("function") {
        put("name", "update_card_regex_update")
        put("description", """
            按脚本 ID 或名称更新一条已有正则脚本（整条替换并保留原 id）。
            正则语法、输出协议与 FULL_FRONTEND 完整 HTML 单文件要求同 update_card_regex_add。
            只守格式、字段和运行兼容性；视觉风格可依据角色卡题材与用户要求自由设计。
        """.trimIndent())
        putJsonObject("parameters") {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("name_or_id") {
                    put("type", "string")
                    put("description", "要更新的脚本 ID 或 script_name")
                }
                putJsonObject("script_name") {
                    put("type", "string")
                    put("description", "新脚本名称；留空则保留原名")
                }
                putJsonObject("find_regex") {
                    put("type", "string")
                    put("description", "完整 JavaScript 正则字符串，包含斜杠和 flags")
                }
                putJsonObject("replace_string") {
                    put("type", "string")
                    put("description", "替换字符串（可用${'$'}1、${'$'}2等捕获组）。FULL_FRONTEND 模式必须是 ```html 围栏包裹的完整单文件 HTML，并以同格式围栏结束；解析后的字符串必须保留真实换行。JSON 参数中换行按标准单次转义为 \\n，不要双重转义成 \\\\n。轻量内联美化（<details>/<span>）直接写标签，无需围栏。")
                }
                putJsonObject("placement") {
                    put("type", "array")
                    putJsonObject("items") { put("type", "integer") }
                    put("description", "作用位置数组；留空默认 [2]（AI输出）。1=用户输入，2=AI输出，3=命令，5=世界信息，6=思考")
                }
                putJsonObject("trim_strings") {
                    put("type", "array")
                    putJsonObject("items") { put("type", "string") }
                    put("description", "截断字符串列表")
                }
                putJsonObject("disabled") {
                    put("type", "boolean")
                    put("description", "是否禁用此脚本，默认 false")
                }
                putJsonObject("markdown_only") {
                    put("type", "boolean")
                    put("description", "仅在Markdown模式生效；美化类规则必须为 true（HTML 美化未显式传值时自动安全默认为 true）")
                }
                putJsonObject("prompt_only") {
                    put("type", "boolean")
                    put("description", "仅在Prompt生效；美化类规则必须为 false（HTML 美化未显式传值时自动安全默认为 false）")
                }
                putJsonObject("run_on_edit") {
                    put("type", "boolean")
                    put("description", "编辑消息时运行，默认 false")
                }
                putJsonObject("substitute_regex") {
                    put("type", "integer")
                    put("description", "替换模式：0=NONE，1=RAW，2=ESCAPED")
                }
                putJsonObject("min_depth") {
                    put("type", "integer")
                    put("description", "最小作用深度")
                }
                putJsonObject("max_depth") {
                    put("type", "integer")
                    put("description", "最大作用深度")
                }
            }
            putJsonArray("required") {
                add(JsonPrimitive("name_or_id"))
                add(JsonPrimitive("find_regex"))
                add(JsonPrimitive("replace_string"))
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════
// Function 7: update_card_regex_delete - 删除正则脚本
// ══════════════════════════════════════════════════════════════
val updateCardRegexDelete: JsonObject = buildJsonObject {
    put("type", "function")
    putJsonObject("function") {
        put("name", "update_card_regex_delete")
        put("description", "删除角色卡中的一条正则脚本")
        putJsonObject("parameters") {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("name_or_id") {
                    put("type", "string")
                    put("description", "要删除的脚本ID（UUID）或脚本名称")
                }
            }
            putJsonArray("required") { add(JsonPrimitive("name_or_id")) }
        }
    }
}

// ══════════════════════════════════════════════════════════════
// Function 7: update_card_cover - 设置封面
// ══════════════════════════════════════════════════════════════
val updateCardCover: JsonObject = buildJsonObject {
    put("type", "function")
    putJsonObject("function") {
        put("name", "update_card_cover")
        put("description", "设置角色卡封面图片（支持URL或data URI）")
        putJsonObject("parameters") {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("url") {
                    put("type", "string")
                    put("description", "图片URL（http://或https://）或data URI（data:image/...）")
                }
            }
            putJsonArray("required") { add(JsonPrimitive("url")) }
        }
    }
}

// ══════════════════════════════════════════════════════════════
// Function 8: replace_card_json - 整卡替换（兜底）
// ══════════════════════════════════════════════════════════════
val replaceCardJson: JsonObject = buildJsonObject {
    put("type", "function")
    putJsonObject("function") {
        put("name", "replace_card_json")
        put("description", """
            用完整的JSON替换整个角色卡（兜底工具，仅在其它function无法满足需求时使用）。
            JSON必须符合SillyTavern V2/V3格式。
        """.trimIndent())
        putJsonObject("parameters") {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("json") {
                    put("type", "string")
                    put("description", "完整的ST V2/V3格式JSON字符串")
                }
            }
            putJsonArray("required") { add(JsonPrimitive("json")) }
        }
    }
}

    /**
     * 获取所有function的列表（联网搜索工具受开关控制）
     * @param webSearchEnabled 是否注册 web_search 搜索工具
     * @param includeInteractiveTools 是否注册交互类工具（ask_user/submit_plan/complete_step）。
     *   YOLO 模式传 false：不挂起等待用户，缺失信息由模型用合理默认值补全并标注 ai_guess。
     */
    fun getAllFunctions(
        webSearchEnabled: Boolean = true,
        includeInteractiveTools: Boolean = true
    ): List<JsonObject> {
        val functions = mutableListOf(
            updateCardField,
            updateCardBookAdd,
            updateCardBookUpdate,
            updateCardBookDelete,
            updateCardRegexAdd,
            updateCardRegexUpdate,
            updateCardRegexDelete,
            updateCardCover,
            replaceCardJson,
            spawnSubagent,
            queryCardEntries,
            getCardEntry,
            auditCard
        )
        if (includeInteractiveTools) {
            functions += askUser
            functions += submitPlan
            functions += completeStep
        }
        if (webSearchEnabled) {
            functions += webSearch
            functions += fetchWebpage
        }
        return functions
    }
}

// ══════════════════════════════════════════════════════════════
// Function 9: ask_user - 追问用户（交互挂起：用户回答/跳过后继续）
// ══════════════════════════════════════════════════════════════
val askUser: JsonObject = buildJsonObject {
    put("type", "function")
    putJsonObject("function") {
        put("name", "ask_user")
        put("description", """
            当且仅当一个决定会改变你下一步怎么做、且无法从用户请求、已有卡片或合理默认中确定时，才向用户提一个问题。

            问题必须是二选一或简短明确的；如果可以，附上你的倾向。

            不要用于：
            - 确认你能推断的细节
            - 一次问多个问题
            - 询问"是否继续"或"计划是否 OK"
            - 用户已进入 YOLO 模式或自动草稿流程

            每次调用只提一个问题。
        """.trimIndent())
        putJsonObject("parameters") {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("question") {
                    put("type", "string")
                    put("description", "问题（中文，具体、小白能听懂，避免术语）")
                }
                putJsonObject("options") {
                    put("type", "array")
                    putJsonObject("items") { put("type", "string") }
                    put("description", "可选的候选选项列表（如['A. 深夜独处','B. 被信任的人背叛','C. 回忆童年']），用户可直接点选")
                }
                putJsonObject("about") {
                    put("type", "string")
                    put("description", "问题主题短语（如'性格成因'），用于界面提示")
                }
            }
            putJsonArray("required") { add(JsonPrimitive("question")) }
        }
    }
}

// ══════════════════════════════════════════════════════════════
// Function 10: submit_plan - 提交制卡计划（挂起：用户确认/修改后执行）
// ══════════════════════════════════════════════════════════════
val submitPlan: JsonObject = buildJsonObject {
    put("type", "function")
    putJsonObject("function") {
        put("name", "submit_plan")
        put("description", """
            提交制卡计划（步骤清单）供用户确认。用户确认后你按计划逐步执行；
            用户要求修改时，根据反馈调整后重新提交。

            触发条件（满足任一即提交，不必先 ask_user）：
            - 预计需要 3 个以上工具轮次
            - 步骤超过 5 个
            - 需要填写 ≥3 个字段、添加 ≥2 条世界书，或调用 ≥2 种工具
            - 用户要求“完整/全部/整张”角色卡

            简单修改或用户明确说“随便做/简单改一下”时可直接执行。
            计划应覆盖：角色名、人设描述、世界书条目、开场白、示例对话、正则、封面等。
        """.trimIndent())
        putJsonObject("parameters") {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("steps") {
                    put("type", "array")
                    putJsonObject("items") {
                        put("type", "object")
                        putJsonObject("properties") {
                            putJsonObject("title") {
                                put("type", "string")
                                put("description", "步骤标题（如'设置角色名：艾伦'）")
                            }
                            putJsonObject("detail") {
                                put("type", "string")
                                put("description", "步骤说明（可空）")
                            }
                        }
                        putJsonArray("required") { add(JsonPrimitive("title")) }
                    }
                    put("description", "步骤列表（按执行顺序，6-10 步）")
                }
            }
            putJsonArray("required") { add(JsonPrimitive("steps")) }
        }
    }
}

// ══════════════════════════════════════════════════════════════
// Function 11: complete_step - 标记步骤完成（实时进度"已完成 N/M 步"）
// ══════════════════════════════════════════════════════════════
val completeStep: JsonObject = buildJsonObject {
    put("type", "function")
    putJsonObject("function") {
        put("name", "complete_step")
        put("description", """
            计划确认后，每完成一个步骤调用一次本工具标记进度（步骤序号从 1 开始）。
            必须按计划中的真实序号标记，不要用“已完成数量”代替序号；
            重复调用同一 step 是幂等的，不会重复计数。
        """.trimIndent())
        putJsonObject("parameters") {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("step") {
                    put("type", "integer")
                    put("description", "完成的步骤序号（1-based，对应计划中的第几步）")
                }
                putJsonObject("summary") {
                    put("type", "string")
                    put("description", "完成摘要（一句话，如'已设置名字为艾伦'）")
                }
            }
            putJsonArray("required") { add(JsonPrimitive("step")) }
        }
    }
}

// ══════════════════════════════════════════════════════════════
// Function 13: fetch_webpage - 网页抓取（配合 web_search 深度调研）
// ══════════════════════════════════════════════════════════════
val fetchWebpage: JsonObject = buildJsonObject {
    put("type", "function")
    putJsonObject("function") {
        put("name", "fetch_webpage")
        put("description", """
            网页抓取工具。抓取指定 URL 的完整内容并提取正文。
            适用场景：
            - 搜索结果中有感兴趣的链接，想查看完整内容
            - 需要阅读技术文档、GitHub README、博客文章
            - 需要从多个页面交叉验证信息

            注意：
            - 只抓取公开可访问的页面（无需登录）
            - 会自动移除脚本/样式等标签，只返回正文文本
            - 内容会截断到约 10000 字符（避免超出上下文）
            - 抓取失败会返回明确错误（404/403/超时等）
            - 不要抓取需要 JavaScript 渲染的页面（SPA 首屏可能为空）
        """.trimIndent())
        putJsonObject("parameters") {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("url") {
                    put("type", "string")
                    put("description", "要抓取的完整 URL（含 https:// 前缀）。" +
                            "示例：'https://github.com/anthropics/anthropic-cookbook'、" +
                            "'https://docs.python.org/3/library/asyncio.html'")
                }
            }
            putJsonArray("required") { add(JsonPrimitive("url")) }
        }
    }
}

// ══════════════════════════════════════════════════════════════
// Function 14: spawn_subagent - 子代理（Tool-as-Agent：独立上下文执行单一任务，结果回注主 Agent 审核）
// ══════════════════════════════════════════════════════════════
val spawnSubagent: JsonObject = buildJsonObject {
    put("type", "function")
    putJsonObject("function") {
        put("name", "spawn_subagent")
        put("description", """
            仅当用户明确要求子代理，或任务确实需要独立上下文的长文本/专项分析时，派发一个单一、明确的任务。

            子代理不会直接修改角色卡；它只返回文本结果，由你审核后决定是否写入。子代理不是无限预算——用户没有要求且你能直接完成的任务不要派发。

            参数：
            - task：清晰的单一任务描述
            - focus：分析重点（可选）
            - output_format：期望的输出格式（可选）

            不要用于：
            - 用户没有要求且你能直接完成的任务
            - 简单字段修改或格式整理
            - 一次派发多个
            - 结果未审核就直接写入卡片

            每次只派发一个子代理。子代理失败时换策略自己做，不要重复派发。
        """.trimIndent())
        putJsonObject("parameters") {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("task") {
                    put("type", "string")
                    put("description", "子任务描述（单一、明确、可直接执行）。" +
                            "示例：'世界书条目：樱华高中——写 200-300 字校园场景正文'、" +
                            "'分析当前角色卡设定是否存在自相矛盾，输出条目级别的修改建议'")
                }
                putJsonObject("focus") {
                    put("type", "string")
                    put("description", "（可选）重点关注点或约束，如'文风清冷克制'、'避免与已有条目「银针与医术」重复'")
                }
                putJsonObject("output_format") {
                    put("type", "string")
                    put("description", "（可选）期望输出形态，如'纯正文'、'分点分析报告'、'A/B 两个候选'")
                }
            }
            putJsonArray("required") { add(JsonPrimitive("task")) }
        }
    }
}

// ══════════════════════════════════════════════════════════════
// Function 15: query_card_entries - 检索角色卡条目（大卡按需读取；只读不修改）
// ══════════════════════════════════════════════════════════════
val queryCardEntries: JsonObject = buildJsonObject {
    put("type", "function")
    putJsonObject("function") {
        put("name", "query_card_entries")
        put("description", """
            检索当前角色卡的世界书/正则条目（只读，不修改任何内容）。
            使用范围：仅当系统消息提供的是索引而非完整 JSON（大卡模式）时使用；
            当前卡完整 JSON 已注入（小卡）时无需调用。
            大卡场景下系统只注入条目索引（不含正文），你应先用本工具按关键词检索，
            拿到稳定条目 ID 后再用 get_card_entry 读取全文；修改用 update_card_book_update（uid=检索到的 ID）。
            一次只检索与当前任务直接相关的关键词，不要无目的地批量检索。
            关键词留空时返回完整索引列表。
        """.trimIndent())
        putJsonObject("parameters") {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("kind") {
                    put("type", "string")
                    put("description", "检索范围：worldbook（世界书）| regex（正则）| all（两者）")
                }
                putJsonObject("keywords") {
                    put("type", "string")
                    put("description", "空格分隔的检索关键词（匹配标题/触发词/内容摘要），留空=列出索引")
                }
                putJsonObject("limit") {
                    put("type", "integer")
                    put("description", "最多返回条数，默认 10，最大 20")
                }
            }
            putJsonArray("required") { add(JsonPrimitive("kind")) }
        }
    }
}

// ══════════════════════════════════════════════════════════════
// Function 16: get_card_entry - 读取单条条目全文（大卡按需读取；只读不修改）
// ══════════════════════════════════════════════════════════════
val getCardEntry: JsonObject = buildJsonObject {
    put("type", "function")
    putJsonObject("function") {
        put("name", "get_card_entry")
        put("description", """
            读取当前角色卡单条条目的完整内容（只读，不修改）。
            使用范围：仅当系统消息提供的是索引而非完整 JSON（大卡模式）时使用；
            当前卡完整 JSON 已注入（小卡）时无需调用。
            - worldbook：entry_id 用 query_card_entries 返回的 #数字 ID；
            - regex：entry_id 用正则脚本 id（可用 8 位短 id，唯一时）。
            一次只读取与当前任务直接相关的 1-3 条；超长条目可能被截断返回（约6000字符），
            不得把截断内容当作完整原文进行整条替换。
            未找到时返回明确错误，请重新检索；不要凭猜测的 ID 修改条目。
        """.trimIndent())
        putJsonObject("parameters") {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("kind") {
                    put("type", "string")
                    put("description", "条目类型：worldbook | regex")
                }
                putJsonObject("entry_id") {
                    put("type", "string")
                    put("description", "条目 ID（worldbook 为 #数字 如 #12；regex 为脚本 id 或其前 8 位）")
                }
            }
            putJsonArray("required") {
                add(JsonPrimitive("kind"))
                add(JsonPrimitive("entry_id"))
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════
// Function 17: audit_card - 审计当前工作卡（只读，不修改）
// ══════════════════════════════════════════════════════════════
val auditCard: JsonObject = buildJsonObject {
    put("type", "function")
    putJsonObject("function") {
        put("name", "audit_card")
        put("description", """
            审计当前工作卡的质量问题（只读，不修改任何内容）。

            触发时机：
            - 用户说“看看这张卡”/“检查一下”/“有什么问题”/“审计”/“质量怎么样”
            - 用户上传参考卡准备二创之前
            - 大量修改后想做整体质检

            工具行为：
            - 自动读取当前工作卡的所有字段和世界书条目
            - 检测：空触发词、超长条目、低数字密度、开场套话、钩子缺失、重复条目等
            - 返回结构化问题列表，按 uid 定位每个问题
            - 完整报告展示在 UI 卡片中，不占用后续对话上下文

            审计完成后：
            - 向用户展示问题摘要
            - 询问用户想优先修复哪些问题
            - 用户确认后，用 get_card_entry(uid) 读取原文，再调用对应修改工具
        """.trimIndent())
        putJsonObject("parameters") {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("scope") {
                    put("type", "string")
                    put("description", "审计范围：all（全卡）| worldbook（只看世界书）| fields（只看角色字段）；默认 all")
                }
            }
            putJsonArray("required") { }
        }
    }
}

// ══════════════════════════════════════════════════════════════
// Function 12: web_search - 联网搜索（系统执行，结果回注）
// ══════════════════════════════════════════════════════════════
val webSearch: JsonObject = buildJsonObject {
    put("type", "function")
    putJsonObject("function") {
        put("name", "web_search")
        put("description", """
            联网搜索工具。用于查询实时信息、事实核查、获取背景资料。
            适用场景：
            - 用户要求查询最新信息、新闻、时事
            - 需要验证事实、数据、引用
            - 角色卡需要真实背景资料（历史事件、地理、文化）
            - 专业领域知识（科技、医学、法律等）

            注意：
            - 搜索由系统执行并回注，你只需提供查询词
            - 查询词应简洁、具体，避免过长或含糊
            - 搜索失败时会收到明确错误，可换关键词重试
            - 中文查询时系统会优先返回中文区结果
        """.trimIndent())
        putJsonObject("parameters") {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("query") {
                    put("type", "string")
                    put("description", "搜索查询词。简洁、具体的关键词或短语，1-50字。" +
                            "示例：'赛博朋克 义体 设定'、'中世纪 骑士 装备'、'日本战国 武士'")
                }
                putJsonObject("max_results") {
                    put("type", "integer")
                    put("description", "返回结果数量，默认 5 条，范围 1-10")
                }
            }
            putJsonArray("required") { add(JsonPrimitive("query")) }
        }
    }
}
