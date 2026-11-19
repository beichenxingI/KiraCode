package com.kirakira.kiracode.ui.config

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kirakira.kiracode.domain.model.CharacterCard
import com.kirakira.kiracode.domain.model.ExportFormat
import com.kirakira.kiracode.domain.repository.ReferenceCardRepository
import com.kirakira.kiracode.domain.usecase.ExportCharacterCardUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/**
 * 角色卡仓库 UI 状态
 */
data class CardRepositoryUiState(
    val cards: List<CharacterCard> = emptyList(),
    val selectedIds: Set<String> = emptySet(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val previewCard: CharacterCard? = null,
    val toast: String? = null
)

/**
 * 角色卡仓库 ViewModel。
 *
 * 保存后「列表不刷新/看似空」的历史根因是 Flow + stateIn(WhileSubscribed) 的订阅时序：
 * 从导入页返回仓库页时，若订阅未在返回瞬间重建，列表会停留在旧缓存。
 * 这里改为「显式拉取 + 可主动 refresh」，页面每次进入都会重拉最新数据，
 * 彻底绕开订阅时序问题。
 */
@HiltViewModel
class CardRepositoryViewModel @Inject constructor(
    private val referenceCardRepository: ReferenceCardRepository,
    private val exportCharacterCardUseCase: ExportCharacterCardUseCase,
    @ApplicationContext private val appContext: android.content.Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(CardRepositoryUiState(isLoading = true))
    val uiState: StateFlow<CardRepositoryUiState> = _uiState

    init {
        refresh()
    }

    /** 显式从数据库拉取最新卡片列表（页面每次进入都调用，保证保存后立即可见） */
    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val cards = referenceCardRepository.getAllReferenceCards(500, 0)
                    .getOrElse { e ->
                        _uiState.update { it.copy(isLoading = false, error = "加载失败：${e.message}") }
                        return@launch
                    }
                    .distinctBy { it.uuid }
                _uiState.update { it.copy(cards = cards, isLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = "加载异常：${e.message}") }
            }
        }
    }

    fun toggleSelection(uuid: String) {
        _uiState.update { state ->
            val newSelected = if (uuid in state.selectedIds) {
                state.selectedIds - uuid
            } else {
                state.selectedIds + uuid
            }
            state.copy(selectedIds = newSelected)
        }
    }

    fun selectAll() {
        _uiState.update { it.copy(selectedIds = it.cards.map { card -> card.uuid }.toSet()) }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedIds = emptySet()) }
    }

    fun deleteCard(uuid: String) {
        viewModelScope.launch {
            try {
                val result = referenceCardRepository.deleteReferenceCardByUuid(uuid)
                result.onFailure { e ->
                    _uiState.update { it.copy(error = "删除失败: ${e.message}") }
                }
                refresh()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "删除失败: ${e.message}") }
            }
        }
    }

    fun previewCard(card: CharacterCard) {
        _uiState.update { it.copy(previewCard = card) }
    }

    fun dismissPreview() {
        _uiState.update { it.copy(previewCard = null) }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun clearToast() {
        _uiState.update { it.copy(toast = null) }
    }

    /** 复制角色卡（新 uuid + 名称后缀「副本」） */
    fun duplicateCard(uuid: String) {
        viewModelScope.launch {
            val origin = _uiState.value.cards.firstOrNull { it.uuid == uuid }
                ?: run {
                    _uiState.update { it.copy(error = "未找到要复制的角色卡") }
                    return@launch
                }
            val copy = origin.copy(
                uuid = UUID.randomUUID().toString(),
                name = "${origin.name}·副本",
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            referenceCardRepository.saveReferenceCard(copy, source = "imported")
                .onSuccess {
                    _uiState.update { it.copy(toast = "已复制：${copy.name}") }
                    refresh()
                }
                .onFailure { e ->
                    _uiState.update { it.copy(error = "复制失败：${e.message}") }
                }
        }
    }

    /** 导出角色卡到 Download（PNG/JSON） */
    fun exportCard(card: CharacterCard, format: ExportFormat) {
        viewModelScope.launch {
            try {
                val fileName = com.kirakira.kiracode.data.local.export.DownloadExporter
                    .sanitizeFileName("${card.name.ifBlank { "card" }}_${System.currentTimeMillis()}")
                val bytes: ByteArray = when (format) {
                    ExportFormat.PNG -> exportCharacterCardUseCase.renderToPngBytes(card)
                    ExportFormat.JSON ->
                        com.kirakira.kiracode.domain.model.st.StCardCodec.toJsonString(card, pretty = true)
                            .toByteArray(Charsets.UTF_8)
                }
                val mime = if (format == ExportFormat.PNG) "image/png" else "application/json"
                val ext = if (format == ExportFormat.PNG) "png" else "json"
                com.kirakira.kiracode.data.local.export.DownloadExporter
                    .save(appContext, "$fileName.$ext", mime, bytes)
                    .onSuccess { path ->
                        _uiState.update { it.copy(toast = "已导出：$path") }
                    }
                    .onFailure { e ->
                        _uiState.update { it.copy(error = "导出失败：${e.message}") }
                    }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "导出失败：${e.message}") }
            }
        }
    }
}