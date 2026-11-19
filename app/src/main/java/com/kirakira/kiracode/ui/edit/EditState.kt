package com.kirakira.kiracode.ui.edit

import com.kirakira.kiracode.domain.exception.DomainException
import com.kirakira.kiracode.domain.model.CharacterCard
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 编辑页面状态密封类
 */
sealed interface EditState {
    data class Editing(
        val card: CharacterCard,
        val hasChanges: Boolean = false,
        val validationErrors: Map<String, String> = emptyMap()
    ) : EditState

    data class Saving(
        val card: CharacterCard
    ) : EditState

    data class Saved(
        val card: CharacterCard,
        val draftId: Long
    ) : EditState

    data class Error(
        val message: String,
        val card: CharacterCard,
        val exception: DomainException? = null
    ) : EditState
}

/**
 * 编辑页面 UI 状态持有类
 */
class EditUiState {
    private val _state = MutableStateFlow<EditState>(
        EditState.Editing(card = CharacterCard(
            name = "", description = "", personality = "", firstMessage = ""
        ))
    )
    val state = _state.asStateFlow()

    var card: CharacterCard = CharacterCard(
        name = "", description = "", personality = "", firstMessage = ""
    )
        set(value) {
            field = value
            _state.update { current ->
                when (current) {
                    is EditState.Editing -> current.copy(card = value)
                    is EditState.Saving -> current.copy(card = value)
                    is EditState.Saved -> current.copy(card = value)
                    is EditState.Error -> current.copy(card = value)
                }
            }
        }

    fun setInitialCard(initialCard: CharacterCard) {
        card = initialCard
        _state.value = EditState.Editing(card = initialCard)
    }

    fun updateCard(updatedCard: CharacterCard) {
        card = updatedCard
        val errors = updatedCard.validateRequiredFields()
        val errorMap = errors.associateWith { it }
        _state.value = EditState.Editing(
            card = updatedCard,
            hasChanges = true,
            validationErrors = errorMap
        )
    }

    fun setSaving() {
        _state.value = EditState.Saving(card)
    }

    fun setSaved(draftId: Long) {
        _state.value = EditState.Saved(card, draftId)
    }

    fun setError(message: String, exception: DomainException? = null) {
        _state.value = EditState.Error(message, card, exception)
    }

    fun setEditing() {
        _state.value = EditState.Editing(card, hasChanges = true)
    }

    val canSave: Boolean
        get() = card.validateRequiredFields().isEmpty()

    val canExport: Boolean
        get() = card.validateRequiredFields().isEmpty()
}