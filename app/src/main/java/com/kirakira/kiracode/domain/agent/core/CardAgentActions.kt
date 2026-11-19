package com.kirakira.kiracode.domain.agent.core

import com.kirakira.kiracode.domain.agent.CardOperation
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 卡编辑相关 Action/Observation（function calling 落地层）。
 *
 * sealed 继承者必须与基类同包（core），故定义于此；
 * CardOperation 尚未标注 @Serializable，故 operations 用 @Transient
 * （内存事件流阶段不入序列化，后续事件持久化时可将 CardOperation
 * 层级标注 @Serializable 后移除 Transient）。
 */

/** 更新角色卡动作：携带结构化操作序列 */
@Serializable
@SerialName("update_card")
data class UpdateCardAction(
    override val id: String = generateEventId(),
    override val timestamp: Long = System.currentTimeMillis(),
    @kotlinx.serialization.Transient
    val operations: List<CardOperation> = emptyList(),
    /** OpenAI tool_call.id（追溯是哪个 tool_call 触发） */
    val toolCallId: String = "",
    /** function 名称（如 update_card_book_add） */
    val functionName: String = ""
) : Action

/** 角色卡操作结果观测 */
@Serializable
@SerialName("card_observation")
data class CardObservation(
    override val id: String = generateEventId(),
    override val timestamp: Long = System.currentTimeMillis(),
    override val actionId: String,
    override val isError: Boolean = false,
    val content: String,
    /** 附加诊断/展示数据（如 web_search 的查询词与结构化结果，供 UI 预览浮窗） */
    val metadata: Map<String, String> = emptyMap()
) : Observation
