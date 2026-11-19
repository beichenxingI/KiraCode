package com.kirakira.kiracode.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class PreferenceOption(
    val id: String,
    val dimensionId: String,
    val label: String,
    val rule: String,
    val isBuiltin: Boolean,
    val sortOrder: Int
)

@Serializable
data class PreferenceDimension(
    val id: String,
    val label: String,
    val isBuiltin: Boolean,
    val sortOrder: Int,
    val options: List<PreferenceOption> = emptyList()
)

@Serializable
data class SessionPreferences(
    val selectedOptionIds: List<String> = emptyList()
) {
    fun buildInjection(catalog: List<PreferenceDimension>): String {
        if (selectedOptionIds.isEmpty()) return ""
        val selected = catalog
            .flatMap { it.options }
            .filter { it.id in selectedOptionIds }
        if (selected.isEmpty()) return ""
        return buildString {
            appendLine("【本次创作偏好】")
            selected.forEach { appendLine("· ${it.rule}") }
        }.trimEnd()
    }

    companion object {
        val Empty = SessionPreferences()
    }
}
