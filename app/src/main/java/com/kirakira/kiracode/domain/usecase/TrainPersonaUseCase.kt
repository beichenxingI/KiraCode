package com.kirakira.kiracode.domain.usecase

import com.kirakira.kiracode.data.remote.dto.ChatMessage
import com.kirakira.kiracode.data.remote.real.ApiConfig
import com.kirakira.kiracode.data.remote.real.RealApiService
import com.kirakira.kiracode.domain.model.BrowsingDecision
import com.kirakira.kiracode.domain.model.CharacterCard
import com.kirakira.kiracode.domain.model.ContentSelection
import com.kirakira.kiracode.domain.model.ContentType
import com.kirakira.kiracode.domain.model.ContextOverflowException
import com.kirakira.kiracode.domain.model.TrainDimension
import com.kirakira.kiracode.domain.model.TrainPersonaConfig
import com.kirakira.kiracode.domain.model.TrainPhase
import com.kirakira.kiracode.domain.model.TrainingFailedException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import javax.inject.Inject

/**
 * 灵魂训练用例（全自动多轮浏览）。
 *
 * 流程：
 * 1. 逐维度推进：先生成"可浏览内容清单"（仅统计信息，不含正文）；
 * 2. 由 AI 根据清单决定下一轮读取哪些卡片/字段/条目（最多 3 轮）；
 * 3. 每轮读取完整内容并做上下文预算检查（超过 maxTokens 的 75% 即失败）；
 * 4. 每个维度生成一份分析报告；
 * 5. 汇总所有维度报告，合成最终「灵魂」提示词。
 *
 * 通过 [onProgress] 上报阶段与总进度（0.0~1.0）；协程取消即中止训练。
 */
class TrainPersonaUseCase @Inject constructor(
    private val realApiService: RealApiService,
    private val chatEngine: ChatEngine
) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend operator fun invoke(
        cards: List<CharacterCard>,
        config: TrainPersonaConfig,
        onProgress: (TrainPhase, Float) -> Unit
    ): Result<String> {
        if (cards.isEmpty()) {
            return Result.failure(IllegalArgumentException("未选择任何角色卡"))
        }
        if (config.selectedDimensions.isEmpty()) {
            return Result.failure(IllegalArgumentException("请至少选择一个分析维度"))
        }

        val apiConfig = chatEngine.resolveConfigById(config.modelId)
            ?: return Result.failure(
                IllegalStateException("找不到可用的模型配置：${config.modelId.ifBlank { "默认" }}")
            )

        val effectiveConfig = apiConfig.copy(
            temperature = config.temperature,
            topP = config.topP,
            maxTokens = config.maxTokens.coerceAtLeast(2048)
        )

        val dimensionResults = mutableMapOf<TrainDimension, String>()
        val dimensions = config.selectedDimensions.sortedBy { it.order }
        val totalDimensions = dimensions.size

        try {
            dimensions.forEachIndexed { idx, dimension ->
                val baseProgress = idx.toFloat() / totalDimensions

                // ===== 轮 1：生成内容清单（本地统计，不调用 API）=====
                onProgress(TrainPhase.AnalyzingDimension(dimension, 1, "生成清单"), baseProgress)
                val checklist = generateChecklist(cards, dimension, config.contextBudget)

                // ===== 轮 2~4：AI 决策浏览（最多 3 轮）=====
                val browsedContent = mutableListOf<String>()   // 完整原文，仅用于最终报告
                val readLog = mutableListOf<String>()          // 轻量浏览记录，仅用于 AI 决策
                for (round in 2..4) {
                    onProgress(
                        TrainPhase.AnalyzingDimension(dimension, round, "浏览内容 ($round/5)"),
                        baseProgress + (round - 1) * 0.2f / totalDimensions
                    )

                    // ① 决策上下文只含清单 + 轻量记录，不含历史原文
                    val alreadyReadSummary = if (readLog.isEmpty()) ""
                    else "【已浏览记录】\n" + readLog.joinToString("\n")

                    val decision = askAIWhatToRead(
                        checklist = checklist,
                        dimension = dimension,
                        alreadyRead = alreadyReadSummary,
                        config = effectiveConfig
                    ).getOrElse { e ->
                        return Result.failure(
                            TrainingFailedException(
                                TrainPhase.AnalyzingDimension(dimension, round, "浏览决策失败"),
                                dimensionResults.toMap(),
                                e
                            )
                        )
                    }

                    if (decision.selections.isEmpty()) break

                    // ② 单轮内容上限：输入上下文预算的 40%
                    val content = fetchContent(cards, decision.selections)
                    val singleRoundTokens = TokenEstimator.estimateTokens(content)
                    if (singleRoundTokens > (config.contextBudget * 0.4).toInt()) {
                        return Result.failure(
                            ContextOverflowException(
                                dimension,
                                dimensionResults.toMap(),
                                "单轮内容过大（${singleRoundTokens} tokens），" +
                                        "请选择更小的内容范围或换用更大上下文的模型"
                            )
                        )
                    }

                    browsedContent += content

                    // ③ 记录轻量浏览日志（类型/卡片/字数）
                    val selectionDesc = decision.selections.joinToString("、") { sel ->
                        "${sel.contentType.name}(卡${sel.cardIndex})"
                    }
                    readLog += "第${round - 1}轮：$selectionDesc（${content.length}字）"
                }

                // ===== 轮 5：生成维度报告 =====
                onProgress(
                    TrainPhase.AnalyzingDimension(dimension, 5, "生成分析报告"),
                    baseProgress + 0.7f / totalDimensions
                )
                val allContent = browsedContent.joinToString("\n\n")
                val totalTokens = TokenEstimator.estimateTokens(allContent)
                val totalBudget = (config.contextBudget * 0.6).toInt()
                val reportContent = if (totalTokens > totalBudget) {
                    // 内容过多：截断最旧内容，保留最近浏览的部分；再按估算器收紧一次
                    var charBudget = (config.contextBudget * 0.6 * 3).toInt()
                    var truncated = allContent.takeLast(charBudget)
                    while (charBudget > 1000 &&
                        TokenEstimator.estimateTokens(truncated) > totalBudget
                    ) {
                        charBudget = (charBudget * 0.8).toInt()
                        truncated = allContent.takeLast(charBudget)
                    }
                    truncated
                } else {
                    allContent
                }

                val report = generateDimensionReport(
                    dimension = dimension,
                    content = reportContent,
                    customPrompt = config.customPrompt,
                    config = effectiveConfig
                ).getOrElse { e ->
                    return Result.failure(
                        TrainingFailedException(
                            TrainPhase.AnalyzingDimension(dimension, 5, "生成报告失败"),
                            dimensionResults.toMap(),
                            e
                        )
                    )
                }
                dimensionResults[dimension] = report

                onProgress(
                    TrainPhase.AnalyzingDimension(dimension, 5, "完成"),
                    (idx + 1).toFloat() / totalDimensions
                )
            }

            // ===== 合成阶段 =====
            onProgress(TrainPhase.Synthesizing, 0.95f)
            val finalPersona = synthesizePersona(dimensionResults, config, effectiveConfig)
                .getOrElse { e ->
                    return Result.failure(
                        TrainingFailedException(TrainPhase.Synthesizing, dimensionResults.toMap(), e)
                    )
                }

            onProgress(TrainPhase.Complete, 1.0f)
            return Result.success(finalPersona)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: ContextOverflowException) {
            return Result.failure(e)
        } catch (e: TrainingFailedException) {
            return Result.failure(e)
        } catch (e: Exception) {
            return Result.failure(
                TrainingFailedException(null, dimensionResults.toMap(), e)
            )
        }
    }

    // ===== 私有辅助函数 =====

    /**
     * 生成可浏览内容清单（不含完整正文，只有统计信息与索引）。
     */
    private fun generateChecklist(
        cards: List<CharacterCard>,
        dimension: TrainDimension,
        contextBudget: Int
    ): String = buildString {
        appendLine("【可浏览内容清单 · 当前维度：${dimension.label}】")
        cards.forEachIndexed { idx, card ->
            appendLine()
            appendLine("卡片 $idx：${card.name.ifBlank { "未命名" }}")
            appendLine("- 描述：${card.description.length} 字")
            appendLine("- 性格：${card.personality.length} 字")
            appendLine("- 开场白：${card.firstMessage.length} 字")
            appendLine("- 示例对话：${card.exampleDialogue.length} 字")
            appendLine("- 场景：${card.scenario.length} 字")

            card.characterBook?.entries?.takeIf { it.isNotEmpty() }?.let { entries ->
                val limit = if (contextBudget >= 500_000) entries.size else minOf(entries.size, 50)
                appendLine(
                    "- 世界书：${entries.size} 条" +
                            if (limit < entries.size) "（清单截取前 $limit 条）" else ""
                )
                entries.take(limit).forEachIndexed { entryIdx, e ->
                    appendLine(
                        "  [$entryIdx] keys=${e.keys.joinToString("|")} " +
                                "constant=${e.constant} order=${e.insertionOrder} 长度=${e.content.length}字"
                    )
                }
            }

            card.regexScripts.takeIf { it.isNotEmpty() }?.let { scripts ->
                val limit = if (contextBudget >= 500_000) scripts.size else minOf(scripts.size, 50)
                appendLine(
                    "- 正则脚本：${scripts.size} 条" +
                            if (limit < scripts.size) "（清单截取前 $limit 条）" else ""
                )
                scripts.take(limit).forEachIndexed { scriptIdx, s ->
                    appendLine(
                        "  [$scriptIdx] 「${s.scriptName}」placement=${s.placement} " +
                                "find=${s.findRegex.take(40)}"
                    )
                }
            }

            if (card.tags.isNotEmpty()) {
                appendLine("- 标签：${card.tags.joinToString("/")}")
            }
        }
    }.trim()

    /**
     * 询问 AI 下一轮要看什么；返回空 selections 表示已足够。
     */
    private suspend fun askAIWhatToRead(
        checklist: String,
        dimension: TrainDimension,
        alreadyRead: String,
        config: ApiConfig
    ): Result<BrowsingDecision> = runCatching {
        val prompt = buildString {
            appendLine("你是角色卡分析专家。当前分析维度：${dimension.label}")
            appendLine()
            appendLine(checklist)
            appendLine()
            if (alreadyRead.isNotBlank()) {
                appendLine("【已浏览内容摘要（最多 500 字）】")
                appendLine(alreadyRead.take(500))
                appendLine()
            }
            appendLine("请决定下一轮浏览哪些内容（如果已够用，返回空 selections）。")
            appendLine("只输出 JSON，不要解释、不要 markdown 代码块。格式：")
            appendLine(
                """{"selections":[{"cardIndex":0,"contentType":"WORLD_BOOK","rangeStart":0,"rangeEnd":7}],"reasoning":"需要看世界书结构"}"""
            )
            appendLine("contentType 可选：${ContentType.entries.joinToString("/")}")
            appendLine("rangeStart/rangeEnd 为 0-based 闭区间；省略两者表示读取全部。")
        }

        val messages = listOf(
            ChatMessage(role = "system", content = "你是角色卡分析专家，只输出合法 JSON。"),
            ChatMessage(role = "user", content = prompt)
        )

        val response = realApiService.chatCompletion(messages, config).getOrThrow()
        parseBrowsingDecision(response)
    }

    /**
     * 宽容解析 AI 输出：优先取 ```json 代码块，其次截取首个 { 到末个 }。
     */
    private fun parseBrowsingDecision(raw: String): BrowsingDecision {
        val fenced = raw.substringAfter("```json", "").substringBefore("```").trim()
        val candidate = fenced.ifBlank { raw }
        val start = candidate.indexOf('{')
        val end = candidate.lastIndexOf('}')
        val jsonText = if (start >= 0 && end > start) {
            candidate.substring(start, end + 1)
        } else {
            candidate.trim()
        }
        return json.decodeFromString<BrowsingDecision>(jsonText)
    }

    /**
     * 根据 AI 的选择提取完整内容（不做截断）。
     */
    private fun fetchContent(
        cards: List<CharacterCard>,
        selections: List<ContentSelection>
    ): String = buildString {
        selections.forEach { sel ->
            val card = cards.getOrNull(sel.cardIndex) ?: return@forEach
            appendLine()
            appendLine("【卡片 ${sel.cardIndex}：${card.name.ifBlank { "未命名" }} - ${sel.contentType.name}】")

            when (sel.contentType) {
                ContentType.DESCRIPTION -> appendLine(card.description)
                ContentType.PERSONALITY -> appendLine(card.personality)
                ContentType.FIRST_MESSAGE -> appendLine(card.firstMessage)
                ContentType.EXAMPLE_DIALOGUE -> appendLine(card.exampleDialogue)
                ContentType.SCENARIO -> appendLine(card.scenario)
                ContentType.TAGS -> appendLine(card.tags.joinToString(" / "))

                ContentType.WORLD_BOOK -> {
                    val entries = card.characterBook?.entries ?: emptyList()
                    val range = resolveRange(sel.rangeStart, sel.rangeEnd, entries.size)
                    entries.slice(range).forEach { e ->
                        appendLine(
                            "【世界书条目】keys=${e.keys.joinToString("|")} " +
                                    "constant=${e.constant} order=${e.insertionOrder}"
                        )
                        appendLine(e.content)
                        appendLine()
                    }
                }

                ContentType.REGEX_SCRIPTS -> {
                    val scripts = card.regexScripts
                    val range = resolveRange(sel.rangeStart, sel.rangeEnd, scripts.size)
                    scripts.slice(range).forEach { s ->
                        appendLine("【正则】「${s.scriptName}」placement=${s.placement}")
                        appendLine("find: ${s.findRegex}")
                        appendLine("replace: ${s.replaceString}")
                        appendLine()
                    }
                }
            }
        }
    }.trim()

    private fun resolveRange(start: Int?, end: Int?, size: Int): IntRange {
        if (size <= 0) return IntRange.EMPTY
        val s = (start ?: 0).coerceIn(0, size - 1)
        val e = (end ?: (size - 1)).coerceIn(s, size - 1)
        return s..e
    }

    /**
     * 生成单个维度的分析报告。
     */
    private suspend fun generateDimensionReport(
        dimension: TrainDimension,
        content: String,
        customPrompt: String,
        config: ApiConfig
    ): Result<String> = runCatching {
        val prompt = buildString {
            appendLine("你是资深 SillyTavern 制卡技术分析师。")
            appendLine(DIMENSION_PROMPTS[dimension].orEmpty())
            if (customPrompt.isNotBlank()) {
                appendLine()
                appendLine("【用户偏好】$customPrompt")
            }
            appendLine()
            appendLine(ANALYSIS_RULES)
            appendLine()
            appendLine("【待分析内容】")
            appendLine(content)
        }

        val messages = listOf(
            ChatMessage(role = "system", content = "你是角色卡技术分析师，输出紧凑要点清单。"),
            ChatMessage(role = "user", content = prompt)
        )

        realApiService.chatCompletion(messages, config).getOrThrow()
    }

    /**
     * 合成最终灵魂提示词。
     */
    private suspend fun synthesizePersona(
        dimensionResults: Map<TrainDimension, String>,
        userConfig: TrainPersonaConfig,
        apiConfig: ApiConfig
    ): Result<String> = runCatching {
        val allReports = dimensionResults.entries
            .sortedBy { it.key.order }
            .joinToString("\n\n────\n\n") { "${it.key.label}：\n${it.value}" }

        val userPrompt = buildString {
            appendLine("以下是逐维度分析报告：")
            appendLine(allReports)
            if (userConfig.customPrompt.isNotBlank()) {
                appendLine()
                appendLine("【用户创作偏好（优先遵守）】")
                appendLine(userConfig.customPrompt)
            }
        }

        val messages = listOf(
            ChatMessage(role = "system", content = SYNTHESIZER_SYSTEM),
            ChatMessage(role = "user", content = userPrompt)
        )

        realApiService.chatCompletion(messages, apiConfig).getOrThrow()
    }

    companion object {
        internal val ANALYSIS_RULES = """
            只输出抽象规律与技巧，不要复述原文；
            禁止出现原角色名、原剧情、原文句子或原卡专有设定。总长不超过 500 字，中文输出。
        """.trimIndent()

        internal val DIMENSION_PROMPTS: Map<TrainDimension, String> = mapOf(
            TrainDimension.TECH_STACK to
                    "分析技术栈：世界书条目结构、keys 命名、constant/insertion_order 使用习惯、" +
                    "description 结构、system_prompt/post_history_instructions 写法、正则命名与 replace 技巧。",
            TrainDimension.WRITING_STYLE to
                    "分析文风：用词习惯（书面/口语/古风/现代）、句式长短、标点特色、语气温度、" +
                    "动作/心理/环境描写比例、对话与叙述比例。",
            TrainDimension.HABITS to
                    "分析写作习惯：开场白设计套路与结尾是否留行动空间、示例对话的场景覆盖方式、" +
                    "{{user}}/{{char}} 使用模式、世界书条目与正文的分工逻辑。",
            TrainDimension.FRONTEND to
                    "分析前端工艺：HTML/CSS 使用习惯、信息面板结构、主题色倾向、是否服务可读性" +
                    "（无 HTML 则标注「无前端工艺」）。",
            TrainDimension.REGEX_COLLAB to
                    "分析正则与渲染协作：正则是否承担样式注入/清理/称呼统一等职责，" +
                    "与世界书、系统提示词的边界。",
            TrainDimension.HIGHLIGHTS to
                    "分析可迁移亮点：该作者最值得模仿的 3 个具体技巧（要具体到可直接复述，均为抽象规律）。"
        )

        internal val SYNTHESIZER_SYSTEM = """
            你是提示词工程师。基于多份对同一作者/团队角色卡的逐维度分析报告，合成一段「灵魂」系统提示词。

            要求：
            1. 直接以第二人称对 AI 写作指令的形式书写（如"你的文风…"、"创作世界书时你会…"）；
            2. 覆盖已分析的所有维度；
            3. 具体可执行，避免空泛形容词；总长控制在 400-700 字；
            4. 只沉淀抽象规律与风格指令：禁止写入原角色名、原剧情、原世界观专有设定、原文句子或大段原文——灵魂描述的是"怎么写"，不是"写了什么"；
            5. 输出仅包含提示词正文本身，不要标题、不要解释、不要 markdown 代码块包裹。
        """.trimIndent()
    }
}