package com.kirakira.kiracode.domain.usecase

import com.kirakira.kiracode.data.remote.dto.ChatMessage
import com.kirakira.kiracode.data.remote.real.RealApiService
import com.kirakira.kiracode.domain.model.CharacterCard
import com.kirakira.kiracode.domain.repository.PreferencesRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 真实子代理执行器（MAX 模式）。
 *
 * 与 OpenCode 子代理机制等价的核心点：
 * - **独立上下文**：子代理用独立的 messages（system=子代理职责 + 卡快照，user=单一任务），
 *   不混入主会话历史，互不污染；
 * - **单一任务目标**：每次只完成一件事（如「撰写世界书条目：樱华高中」）；
 * - **返回结果**：子代理返回文本正文，由主 Agent 审核后写入角色卡（子代理不直接落库，
 *   主 Agent 拥有审核与写入权）。
 *
 * V2 Beta 强化：
 * - 注入灵魂与用户自定义规则（若启用），保证子代理产出与主 Agent 风格一致；
 * - 独立低温采样（0.7）保证正文生成稳定。
 *
 * 增量3 上下文加厚（修复文风漂移/世界书矛盾两大质量问题的根因）：
 * - 基本信息不再截断（原：角色名/描述前300字/性格前200字）；
 * - 注入已有世界书条目列表（comment+keys+内容预览）——子代理能看到主 Agent 已写了什么，
 *   避免条目重复/矛盾（此前 cardCtx 快照不含任何已有条目）；
 * - 注入参考卡风格样本（开场白/对话示例）——补上 buildFewShotPrompt 死代码
 *   从未接线导致的 few-shot 缺失；
 * - 上下文上限从 2000 放宽到 4000 字符（加厚后的上下文需要更大预算）。
 */
@Singleton
class SubAgentRunner @Inject constructor(
    private val realApiService: RealApiService,
    private val chatEngine: ChatEngine,
    private val preferencesRepository: PreferencesRepository
) {

    /**
     * 运行一个子代理，生成指定任务的正文。
     *
     * @param task 单一任务描述（如「世界书条目：樱华高中——写 200-300 字正文」）
     * @param workingCard 当前工作卡（用于构建上下文；null 表示尚无角色卡）
     * @param referenceCard 参考卡（提供风格样本；null 表示无参考卡。
     *   调用方（聊天 UI 重写后）需传入以启用风格样本）
     * @return 子代理产出的正文文本
     */
    suspend fun run(
        task: String,
        workingCard: CharacterCard?,
        referenceCard: CharacterCard? = null
    ): Result<String> {
        val config = chatEngine.resolveActiveConfig()
            ?: return Result.failure(IllegalStateException("尚未配置 AI 接口"))

        // V2 Beta：子代理风格基线（人格 + 用户规则），与主 Agent 同源
        val personaPrompt = runCatching {
            val personas = com.kirakira.kiracode.domain.model.PersonaCodec
                .decodeList(preferencesRepository.personasJson.first())
            personas.firstOrNull { it.id == preferencesRepository.activePersonaId.first() }?.prompt
        }.getOrNull()
        // P0 三态：仅用户主动启用且内容非空才同源注入子代理
        val customPrompt = runCatching {
            (preferencesRepository.customPromptState.first()
                as? com.kirakira.kiracode.domain.model.CustomPromptState.Enabled)?.text
        }.getOrNull()

        // 增量3：加厚版上下文（完整信息 + 已有条目 + 参考卡风格）
        val cardContext = buildSubAgentContext(workingCard, referenceCard)
        val messages = buildSubAgentMessages(task, cardContext, personaPrompt, customPrompt)
        // 子代理独立采样参数：温度 0.7（正文生成稳定且不死板）
        return realApiService.chatCompletion(messages, config.copy(temperature = 0.7f))
    }

    /**
     * 组装子代理独立 messages（纯函数，便于契约测试）。
     *
     * 契约：仅 system + user 两条 —— 主会话历史（哪怕 100 条）不进入；
     * 卡上下文 ≤4000 字符、人格 ≤1200、用户规则 ≤600（明确的上下文预算）。
     */
    internal fun buildSubAgentMessages(
        task: String,
        cardContext: String,
        personaPrompt: String?,
        customPrompt: String?
    ): List<ChatMessage> {
        val system = buildString {
            appendLine("你是 KiraCard 的制卡子代理，负责完成一个单一、明确的任务。")
            appendLine("只输出任务要求的正文内容本身，不要解释、不要字段名、不要 JSON、不要代码块围栏。")
            appendLine("当前角色卡上下文（用于保持风格一致、避免与已有条目矛盾）：")
            append(cardContext.take(4000))
            personaPrompt?.takeIf { it.isNotBlank() }?.let {
                appendLine()
                appendLine("【灵魂（创作风格基线，请遵循）】")
                appendLine(it.take(1200))
            }
            customPrompt?.takeIf { it.isNotBlank() }?.let {
                appendLine()
                appendLine("【用户的创作规则】")
                appendLine(it.take(600))
            }
        }
        return listOf(
            ChatMessage(role = "system", content = system),
            ChatMessage(role = "user", content = task)
        )
    }

    /**
     * 构建子Agent上下文（加厚版，增量3）
     * 包含：完整描述+性格+场景+已有世界书条目列表+参考卡风格样本
     */
    internal fun buildSubAgentContext(
        workingCard: CharacterCard?,
        referenceCard: CharacterCard?
    ): String {
        // 1. 基本信息（完整，不截断）
        val basicInfo = workingCard?.let { card ->
            buildString {
                appendLine("角色名：${card.name}")
                if (card.description.isNotBlank()) appendLine("描述：${card.description}")
                if (card.personality.isNotBlank()) appendLine("性格：${card.personality}")
                if (card.scenario.isNotBlank()) appendLine("场景：${card.scenario}")
            }.trim()
        } ?: "（尚无角色卡）"

        // 2. 已完成的世界书条目（让子Agent看到主Agent已写了什么，避免重复/矛盾）
        val existingEntries = workingCard?.characterBook?.let { book ->
            if (book.entries.isEmpty()) {
                "已完成的世界书条目：（尚无，你写的是第一条）"
            } else {
                buildString {
                    appendLine("已完成的世界书条目（${book.entries.size}条，避免与以下条目重复或矛盾）：")
                    book.entries.forEach { entry ->
                        val keysStr = entry.keys.joinToString("|")
                        val contentPreview = entry.content.take(100).let {
                            if (entry.content.length > 100) "$it..." else it
                        }
                        appendLine("- ${entry.comment}（触发词：$keysStr）: $contentPreview")
                    }
                }.trim()
            }
        } ?: "已完成的世界书条目：（尚无世界书）"

        // 3. 参考卡风格样本（如果有）——修复文风漂移：子代理此前看不到任何风格样本
        val referenceStyle = referenceCard?.let { ref ->
            buildString {
                appendLine()
                appendLine("参考卡风格样本（用于保持文风一致）：")
                if (ref.firstMessage.isNotBlank()) {
                    appendLine("开场白示例：")
                    appendLine(ref.firstMessage.take(400).let { if (ref.firstMessage.length > 400) "$it..." else it })
                }
                if (ref.exampleDialogue.isNotBlank()) {
                    appendLine("对话示例：")
                    appendLine(ref.exampleDialogue.take(400).let { if (ref.exampleDialogue.length > 400) "$it..." else it })
                }
            }.trim()
        } ?: ""

        return listOf(basicInfo, existingEntries, referenceStyle)
            .filter { it.isNotBlank() }
            .joinToString("\n\n")
    }
}
