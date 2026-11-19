package com.kirakira.kiracode.domain.agent.core

import kotlinx.serialization.Serializable

@Serializable
sealed class Message {
    abstract val content: String
}

@Serializable
data class SystemMessage(override val content: String) : Message()

@Serializable
data class UserMessage(override val content: String) : Message()

@Serializable
data class AssistantMessage(
    override val content: String,
    val toolCalls: List<ToolCall>? = null
) : Message()

@Serializable
data class ToolMessage(
    override val content: String,
    val toolCallId: String,
    val name: String
) : Message()

@Serializable
data class ToolCall(
    val id: String,
    val function: FunctionCall
)

@Serializable
data class FunctionCall(
    val name: String,
    val arguments: String
)
