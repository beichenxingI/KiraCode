package com.kirakira.kiracode.domain.agent.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface Observation : AgentEvent {
    val actionId: String
    val isError: Boolean
    override val source: EventSource
        get() = EventSource.ENVIRONMENT
}

@Serializable
@SerialName("think_observation")
data class ThinkObservation(
    override val id: String = generateEventId(),
    override val timestamp: Long = System.currentTimeMillis(),
    override val actionId: String,
    override val isError: Boolean = false,
    val content: String
) : Observation

@Serializable
@SerialName("finish_observation")
data class FinishObservation(
    override val id: String = generateEventId(),
    override val timestamp: Long = System.currentTimeMillis(),
    override val actionId: String,
    override val isError: Boolean = false,
    val result: String
) : Observation
