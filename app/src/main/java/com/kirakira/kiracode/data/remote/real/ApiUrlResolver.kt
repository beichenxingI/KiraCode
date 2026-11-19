package com.kirakira.kiracode.data.remote.real

/**
 * OpenAI 兼容端点解析器（根治 404）。
 *
 * 用户输入的 Base URL 形态五花八门：
 * - https://api.deepseek.com            → 拼 /v1/chat/completions
 * - https://api.deepseek.com/v1         → 拼 /chat/completions
 * - https://api.deepseek.com/v1/        → 同上（去尾斜杠）
 * - https://x.com/v1/chat/completions   → 直接使用（用户贴了完整端点）
 * - https://open.bigmodel.cn/api/paas/v4 → 拼 /chat/completions（版本段已含）
 */
object ApiUrlResolver {

    private val VERSION_TAIL = Regex("""/v\d+$""")
    private val CHAT_TAIL = Regex("""/chat/completions/?$""")
    private val MODELS_TAIL = Regex("""/models/?$""")

    /** 规范化 Base URL：去空白与尾部斜杠 */
    fun normalize(baseUrl: String): String =
        baseUrl.trim().trimEnd('/')

    /**
     * 解析 chat/completions 完整端点。
     */
    fun chatCompletions(baseUrl: String): String {
        val b = normalize(baseUrl)
        return when {
            // 用户直接粘贴了完整端点
            CHAT_TAIL.containsMatchIn(b) -> b
            // 已含版本段（如 /v1、/api/paas/v4）→ 只补方法路径
            endsWithVersion(b) -> "$b/chat/completions"
            else -> "$b/v1/chat/completions"
        }
    }

    /**
     * 解析 models 列表端点。
     */
    fun models(baseUrl: String): String {
        val b = normalize(baseUrl)
        return when {
            MODELS_TAIL.containsMatchIn(b) -> b
            endsWithVersion(b) -> "$b/models"
            else -> "$b/v1/models"
        }
    }

    private fun endsWithVersion(b: String): Boolean =
        VERSION_TAIL.containsMatchIn(b)
}
