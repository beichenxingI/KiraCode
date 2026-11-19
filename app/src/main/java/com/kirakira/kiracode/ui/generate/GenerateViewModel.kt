package com.kirakira.kiracode.ui.generate

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kirakira.kiracode.domain.model.CharacterCard
import com.kirakira.kiracode.domain.model.STYLE_TEMPLATES
import com.kirakira.kiracode.domain.model.StyleTemplate
import com.kirakira.kiracode.domain.usecase.GenerateCharacterCardUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 生成角色卡 ViewModel
 * 管理参考卡设置 → 用户输入描述 → 生成 → 成功/失败 的 UI 状态
 */
@HiltViewModel
class GenerateViewModel @Inject constructor(
    private val generateUseCase: GenerateCharacterCardUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<GenerateState>(GenerateState.Idle())
    val uiState: StateFlow<GenerateState> = _uiState.asStateFlow()

    /**
     * 由导航层在进入页面时调用，写入参考卡
     */
    fun setReferenceCard(card: CharacterCard) {
        _uiState.update { current ->
            when (current) {
                is GenerateState.Idle -> current.copy(referenceCard = card)
                is GenerateState.Error -> GenerateState.Idle(referenceCard = card)
                else -> current
            }
        }
    }

    /**
     * 文本变化回调
     */
    fun onDescriptionChanged(text: String) {
        _uiState.update { current ->
            when (current) {
                is GenerateState.Idle -> current.copy(description = text)
                is GenerateState.Error -> current.copy(description = text)
                else -> current
            }
        }
    }

    /**
     * 风格模板选择
     */
    fun onStyleTemplateSelected(template: StyleTemplate?) {
        _uiState.update { current ->
            when (current) {
                is GenerateState.Idle -> current.copy(styleTemplate = template)
                is GenerateState.Error -> current.copy(styleTemplate = template)
                else -> current
            }
        }
    }

    /**
     * 温度调整
     */
    fun onTemperatureChanged(value: Float) {
        _uiState.update { current ->
            (current as? GenerateState.Idle)?.copy(temperature = value) ?: current
        }
    }

    /**
     * 最大 Token 调整
     */
    fun onMaxTokensChanged(value: Int) {
        _uiState.update { current ->
            (current as? GenerateState.Idle)?.copy(maxTokens = value) ?: current
        }
    }

    /**
     * 触发生成
     */
    fun generate() {
        val current = _uiState.value
        val referenceCard = (current as? GenerateState.Idle)?.referenceCard
            ?: (current as? GenerateState.Error)?.referenceCard
            ?: run {
                _uiState.value = GenerateState.Error("未设置参考卡", description = (current as? GenerateState.Idle)?.description ?: "")
                return
            }
        val description = (current as? GenerateState.Idle)?.description?.trim()
            ?: (current as? GenerateState.Error)?.description?.trim()
            ?: ""
        if (description.isBlank()) {
            _uiState.update {
                (it as? GenerateState.Idle)?.copy(description = "") ?: it
            }
            return
        }
        val styleTemplate = (current as? GenerateState.Idle)?.styleTemplate
            ?: (current as? GenerateState.Error)?.styleTemplate
        val temperature = (current as? GenerateState.Idle)?.temperature ?: 0.8f
        val maxTokens = (current as? GenerateState.Idle)?.maxTokens ?: 4096

        _uiState.value = GenerateState.Loading(
            referenceCard = referenceCard,
            description = description,
            styleTemplate = styleTemplate,
            progressMessage = "正在生成角色卡..."
        )

        viewModelScope.launch {
            val result = generateUseCase(
                referenceCard = referenceCard,
                newCharacterDescription = description,
                styleTemplate = styleTemplate?.id
            )
            result.fold(
                onSuccess = { card ->
                    _uiState.value = GenerateState.Success(
                        generatedCard = card,
                        referenceCard = referenceCard,
                        description = description
                    )
                },
                onFailure = { e ->
                    _uiState.value = GenerateState.Error(
                        message = e.message ?: "生成失败",
                        referenceCard = referenceCard,
                        description = description,
                        styleTemplate = styleTemplate
                    )
                }
            )
        }
    }

    /**
     * 从错误回到 Idle 态（保留用户输入）
     */
    fun backToIdle() {
        val current = _uiState.value
        if (current is GenerateState.Error) {
            _uiState.value = GenerateState.Idle(
                referenceCard = current.referenceCard,
                description = current.description,
                styleTemplate = current.styleTemplate
            )
        }
    }

    /**
     * 完全重置
     */
    fun reset() {
        _uiState.value = GenerateState.Idle()
    }

    /**
     * 获取风格模板列表（供 Screen 展示）
     */
    fun getStyleTemplates(): List<StyleTemplate> = STYLE_TEMPLATES
}
