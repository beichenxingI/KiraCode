package com.kirakira.kiracode.domain.agent

import com.kirakira.kiracode.domain.agent.core.Action
import com.kirakira.kiracode.domain.agent.core.CardObservation
import com.kirakira.kiracode.domain.agent.core.FinishAction
import com.kirakira.kiracode.domain.agent.core.FinishObservation
import com.kirakira.kiracode.domain.agent.core.Observation
import com.kirakira.kiracode.domain.agent.core.ThinkAction
import com.kirakira.kiracode.domain.agent.core.ThinkObservation
import com.kirakira.kiracode.domain.agent.core.UpdateCardAction
import com.kirakira.kiracode.domain.agent.tools.CardFunctions
import com.kirakira.kiracode.domain.agent.tools.RegexScriptValidator
import com.kirakira.kiracode.domain.model.CharacterCard
import com.kirakira.kiracode.domain.model.RegexGenerationMode
import kotlinx.serialization.json.JsonObject

typealias ToolExecutor = suspend (Action) -> Observation

class ToolRegistry(
    private val cardOperationApplier: (CharacterCard, List<CardOperation>) -> Result<CharacterCard>
) {
    private var currentCard: CharacterCard? = null
    private var regexGenerationMode: RegexGenerationMode = RegexGenerationMode.DEFAULT

    fun setCurrentCard(card: CharacterCard?) {
        currentCard = card
    }

    fun setRegexGenerationMode(mode: RegexGenerationMode) {
        regexGenerationMode = mode
    }

    fun getCurrentCard(): CharacterCard? = currentCard

    suspend fun execute(
        action: Action,
        regexMode: RegexGenerationMode = regexGenerationMode
    ): Observation {
        return when (action) {
            is ThinkAction -> ThinkObservation(
                actionId = action.id,
                content = "Thought recorded: ${action.thought}"
            )
            is FinishAction -> FinishObservation(
                actionId = action.id,
                result = "Task completed"
            )
            is UpdateCardAction -> {
                val card = currentCard ?: return CardObservation(
                    actionId = action.id,
                    isError = true,
                    content = "❌ 当前无工作卡，请先创建角色卡"
                )

                val normalizedOperations = mutableListOf<CardOperation>()
                val validationErrors = mutableListOf<String>()
                action.operations.forEach { operation ->
                    val result = RegexScriptValidator.validateAndNormalize(operation, regexMode)
                    if (result.isValid) {
                        normalizedOperations += result.operation
                    } else {
                        val label = when (operation) {
                            is CardOperation.RegexAdd -> "update_card_regex_add"
                            is CardOperation.RegexUpdate -> "update_card_regex_update"
                            else -> "正则脚本"
                        }
                        val isFullFrontend = regexMode == RegexGenerationMode.FULL_FRONTEND &&
                            operation.let {
                                it is CardOperation.RegexAdd || it is CardOperation.RegexUpdate
                            }
                        validationErrors += buildString {
                            if (isFullFrontend) {
                                appendLine("完整前端正则未写入，原因：")
                            } else {
                                appendLine("$label 校验失败，未写入：")
                            }
                            result.errors.forEachIndexed { index, error ->
                                appendLine("${index + 1}. $error")
                            }
                            if (isFullFrontend) {
                                append("请修正 HTML 文档结构、代码围栏、真实换行或 \$1 注入槽位后重新调用工具。视觉设计不受限制。")
                            } else {
                                append("请修正正则、捕获组引用、JSON 参数或安全显示标志后重新调用工具。")
                            }
                        }
                    }
                }

                if (validationErrors.isNotEmpty()) {
                    return CardObservation(
                        actionId = action.id,
                        isError = true,
                        content = validationErrors.joinToString("\n\n")
                    )
                }

                // 应用操作
                val result = cardOperationApplier(card, normalizedOperations)

                result.fold(
                    onSuccess = { newCard ->
                        currentCard = newCard
                        CardObservation(
                            actionId = action.id,
                            isError = false,
                            content = "✅ 操作成功：${action.operations.size}个修改已应用"
                        )
                    },
                    onFailure = { e ->
                        CardObservation(
                            actionId = action.id,
                            isError = true,
                            content = "❌ 操作失败：${e.message}"
                        )
                    }
                )
            }
            else -> throw IllegalArgumentException("Unknown action: $action")
        }
    }

    /**
     * @param includeInteractiveTools 是否注册交互类工具（ask_user/submit_plan/complete_step）；
     *        YOLO 模式传 false（非交互：不挂起等用户）。
     */
    fun getTools(webSearchEnabled: Boolean = true, includeInteractiveTools: Boolean = true): List<JsonObject> {
        return CardFunctions.getAllFunctions(webSearchEnabled, includeInteractiveTools)
    }
}
