package com.kirakira.kiracode.ui.edit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kirakira.kiracode.domain.exception.DomainException
import com.kirakira.kiracode.domain.model.CharacterCard
import com.kirakira.kiracode.domain.usecase.SaveDraftUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 编辑页面 ViewModel
 * 处理字段编辑、保存草稿、导出导航
 */
@HiltViewModel
class EditViewModel @Inject constructor(
    private val saveDraftUseCase: SaveDraftUseCase
) : ViewModel() {

    val uiState = EditUiState()

    /**
     * 设置初始卡片 (从导航参数接收)
     */
    fun setInitialCard(card: CharacterCard) {
        uiState.setInitialCard(card)
    }

    /**
     * 更新角色卡字段
     */
    fun updateField(fieldName: String, newValue: String) {
        val updatedCard = when (fieldName) {
            "name" -> uiState.card.copy(name = newValue)
            "description" -> uiState.card.copy(description = newValue)
            "personality" -> uiState.card.copy(personality = newValue)
            "firstMessage" -> uiState.card.copy(firstMessage = newValue)
            "creator" -> uiState.card.copy(creator = newValue)
            "exampleDialogue" -> uiState.card.copy(exampleDialogue = newValue)
            "scenario" -> uiState.card.copy(scenario = newValue)
            "creatorNotes" -> uiState.card.copy(creatorNotes = newValue)
            "systemPrompt" -> uiState.card.copy(systemPrompt = newValue)
            "postHistoryInstructions" -> uiState.card.copy(postHistoryInstructions = newValue)
            "tags" -> uiState.card.copy(tags = newValue.split(",").map { it.trim() }.filter { it.isNotEmpty() })
            "alternateGreetings" -> uiState.card.copy(alternateGreetings = newValue.lines().map { it.trim() }.filter { it.isNotEmpty() }.toList())
            "version" -> uiState.card.copy(version = newValue)
            "avatar" -> uiState.card.copy(avatar = newValue.takeIf { it.isNotBlank() })
            else -> return
        }
        uiState.updateCard(updatedCard.copyWithUpdatedTimestamp())
    }

    /**
     * 批量更新卡片
     */
    fun updateCard(newCard: CharacterCard) {
        uiState.updateCard(newCard.copyWithUpdatedTimestamp())
    }

    /**
     * 保存草稿
     */
    fun onSaveDraftClick() {
        if (!uiState.canSave) {
            uiState.setError("请填写所有必填字段")
            return
        }

        uiState.setSaving()

        viewModelScope.launch {
            val result = saveDraftUseCase(uiState.card)
            result.onSuccess { draftId ->
                uiState.setSaved(draftId)
            }.onFailure { exception ->
                val message = when (exception) {
                    is DomainException -> exception.message
                    else -> "保存失败: ${exception.message}"
                }
                uiState.setError(message ?: "保存失败", exception as? DomainException)
            }
        }
    }

    /**
     * 获取当前编辑的卡片
     */
    fun getCurrentCard(): CharacterCard {
        return uiState.card
    }

    /**
     * 重置为初始状态
     */
    fun resetToInitial(initialCard: CharacterCard) {
        uiState.setInitialCard(initialCard)
    }
}