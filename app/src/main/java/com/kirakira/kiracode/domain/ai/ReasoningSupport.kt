package com.kirakira.kiracode.domain.ai

/**
 * 思考强度支持能力判定（V1.0.0 返工重做）。
 *
 * 调研结论（2026-08）：
 * - OpenCode/Codex 等工具不再依赖精确模型 ID 关键词表：OpenCode 使用 models.dev 目录元数据
 *   + provider transform（variants 叠加 reasoningEffort/thinkingConfig）；Codex CLI 用
 *   模型族正则 + 不支持值自动钳制（clamp）。
 * - 参数形态已收敛为两大类：
 *   1) OpenAI 兼容 `reasoning_effort`：o 系 / GPT-5.x / GPT-6 / gpt-oss / DeepSeek V4 /
 *      SiliconFlow 等广泛采用；DeepSeek V4 需同时携带 `thinking:{type:"enabled"}`；
 *   2) 厂商私有开关（DashScope enable_thinking、MiniMax thinking.type 等），仅二值开关。
 *
 * 本实现 = 宽泛家族启发式（决定"是否显示选择器/用哪种参数形态"）
 *        + 运行时小请求探测（权威判定，结果持久化缓存）。
 * 启发式刻意使用通配模式（如 deepseek-v\d、gpt-[5-9]），
 * 新版本号（GPT 6、DeepSeek V5…）无需改代码即可被覆盖。
 */
object ReasoningSupport {

    enum class Family(val label: String, val needsThinkingToggle: Boolean) {
        /** OpenAI 推理系：reasoning_effort（low/medium/high[/xhigh/max]） */
        OPENAI("推理模型 · reasoning_effort", needsThinkingToggle = false),

        /** DeepSeek V4+：reasoning_effort + thinking:{type:enabled} 双参数 */
        DEEPSEEK("DeepSeek 思考模式", needsThinkingToggle = true),

        /** 名称暗示思考能力但厂商未知：乐观使用 reasoning_effort，由探测/实际请求纠偏 */
        HINTED("疑似思考模型", needsThinkingToggle = false)
    }

    /** 三档档位（另有"自动"=不传参） */
    val EFFORT_LEVELS = listOf("low", "medium", "high")

    /**
     * 家族启发式：宽泛通配，避免关键词表过时。
     * 返回 null 表示名称完全无线索（仍可能通过探测发现支持）。
     */
    fun heuristicFamily(modelId: String): Family? {
        if (modelId.isBlank()) return null
        val m = modelId.lowercase().trim()

        // DeepSeek 全系（V4/V5/…、r1、reasoner）：思考是请求参数而非独立模型
        if (Regex("""deepseek[-_]?(v\d|r\d|reasoner)""").containsMatchIn(m)) return Family.DEEPSEEK

        // OpenAI 推理系：o1/o3/o4/o5…、gpt-5/gpt-6/gpt-oss 及其变体、codex 系列
        if (Regex("""(^|/)(o[1-9])([-_.].*)?($|/)""").containsMatchIn(m)) return Family.OPENAI
        if (Regex("""gpt-[5-9]""").containsMatchIn(m)) return Family.OPENAI
        if (m.contains("gpt-oss") || m.contains("codex")) return Family.OPENAI

        // 其他已知思考型家族或命名暗示（QwQ、GLM-4.5+/thinking、Minimax-M 系列、通用 think/reason 词根）
        if (Regex("""\b(r\d)\b""").containsMatchIn(m) || m.startsWith("r1")) return Family.HINTED
        if (Regex("""glm-[45](\.\d+)?(-air|-flash)?""").containsMatchIn(m) && Regex("""glm-4\.[5-9]|glm-[5-9]""").containsMatchIn(m))
            return Family.HINTED
        if (m.contains("think") || m.contains("reason")) return Family.HINTED
        if (Regex("""qwq|minimax-m\d""").containsMatchIn(m)) return Family.HINTED

        return null
    }

    /**
     * 探测失败的错误文本是否指向"参数不被接受"。
     * 只有明确拒绝该参数才判定不支持；网络错误/鉴权错误等一律视为未知（保持乐观）。
     */
    fun errorIndicatesUnsupported(errorText: String): Boolean {
        val t = errorText.lowercase()
        val markers = listOf(
            "reasoning_effort", "reasoning effort", "effort",
            "unknown parameter", "unrecognized", "unexpected", "not supported",
            "not support", "invalid parameter", "unsupported", "extra_forbidden",
            "does not support"
        )
        return markers.any { t.contains(it) }
    }
}

/**
 * 探测结果持久化缓存（DataStore JSON：model → 是否支持）。
 * 新模型无需更新代码——首次使用自动探测并记忆。
 */
object ReasoningProbeCache {
    private val json = kotlinx.serialization.json.Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(map: Map<String, Boolean>): String = runCatching {
        json.encodeToString(
            kotlinx.serialization.builtins.MapSerializer(
                kotlinx.serialization.serializer<String>(),
                kotlinx.serialization.serializer<Boolean>()
            ), map
        )
    }.getOrDefault("{}")

    fun decode(raw: String?): Map<String, Boolean> {
        if (raw.isNullOrBlank()) return emptyMap()
        return runCatching {
            json.decodeFromString(
                kotlinx.serialization.builtins.MapSerializer(
                    kotlinx.serialization.serializer<String>(),
                    kotlinx.serialization.serializer<Boolean>()
                ), raw
            )
        }.getOrDefault(emptyMap())
    }
}
