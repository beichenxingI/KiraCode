package com.kirakira.kiracode.ui.importcard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kirakira.kiracode.domain.model.CharacterCard
import com.kirakira.kiracode.domain.repository.ReferenceCardRepository
import com.kirakira.kiracode.domain.usecase.ImportCharacterCardUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * 导入角色卡 ViewModel（V2.0）
 * 支持：粘贴 JSON / 文件导入（JSON、PNG）/ 解析预览 / 存入参考卡仓库
 */
@HiltViewModel
class ImportCardViewModel @Inject constructor(
    private val importUseCase: ImportCharacterCardUseCase,
    private val referenceCardRepository: ReferenceCardRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ImportCardUiState())
    val uiState: StateFlow<ImportCardUiState> = _uiState.asStateFlow()

    fun onJsonTextChanged(text: String) {
        _uiState.update { it.copy(jsonText = text, errorMessage = null) }
    }

    /**
     * 触发解析（粘贴文本路径）
     */
    fun parse() {
        val current = _uiState.value
        if (current.jsonText.isBlank()) {
            _uiState.update { it.copy(errorMessage = "请粘贴角色卡 JSON") }
            return
        }
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            importUseCase(current.jsonText.trim()).fold(
                onSuccess = { card -> _uiState.update { it.copy(isLoading = false, parsedCard = card) } },
                onFailure = { e ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = e.message ?: "解析失败") }
                }
            )
        }
    }

    /**
     * 文件导入（自动识别 JSON/PNG），fileName 用于判断扩展名。
     */
    fun importFromBytes(fileName: String?, bytes: ByteArray) {
        if (bytes.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "文件为空或读取失败") }
            return
        }
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                importUseCase.importAuto(fileName, bytes)
            }
            result.fold(
                onSuccess = { card ->
                    val text = if (fileName?.endsWith(".png", true) == true) "" else bytes.toString(Charsets.UTF_8)
                    _uiState.update { it.copy(isLoading = false, parsedCard = card, jsonText = text.takeIf { t -> t.isNotBlank() && t.length < 200_000 } ?: it.jsonText) }
                },
                onFailure = { e ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = e.message ?: "导入失败") }
                }
            )
        }
    }

    /**
     * 将当前解析成功的卡存入参考卡仓库，并**读回校验**确认真实落库。
     * 保存后按 uuid 读回：读到即成功；读不到返回失败（不再有「显示成功但未落库」）。
     */
    suspend fun saveToRepository(card: CharacterCard): Result<Long> {
        val saved = referenceCardRepository.saveReferenceCard(card, source = "import")
        if (saved.isFailure) return saved
        // 读回校验
        val back = referenceCardRepository.getReferenceCardByUuid(card.uuid)
        return when {
            back.isFailure -> Result.failure(back.exceptionOrNull() ?: IllegalStateException("读回校验失败"))
            back.getOrNull() == null -> Result.failure(IllegalStateException("保存后未能在仓库中读回该卡"))
            else -> saved
        }
    }

    /** 文件读取失败提示 */
    fun notifyReadFailure() {
        _uiState.update { it.copy(errorMessage = "无法读取所选文件，请重试或改用粘贴 JSON") }
    }

    /**
     * 异步存入参考卡仓库，带回真实成功/失败反馈。
     * 注意：saveToRepository 返回 Result<Long>（内部已捕获），不能再用 runCatching 包裹，
     * 否则内层失败会被外层 runCatching 误判为成功而静默吞掉（历史 bug 根因）。
     */
    fun saveAsyncWithFeedback(card: CharacterCard, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            val result = saveToRepository(card)
            result.fold(
                onSuccess = { onResult(true, null) },
                onFailure = { e -> onResult(false, e.message ?: "保存失败") }
            )
        }
    }

    fun reset() {
        _uiState.value = ImportCardUiState()
    }
}
