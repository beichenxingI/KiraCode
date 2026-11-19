package com.kirakira.kiracode.ui.finished

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kirakira.kiracode.data.local.export.DownloadExporter
import com.kirakira.kiracode.domain.model.CharacterCard
import com.kirakira.kiracode.domain.model.st.StCardCodec
import com.kirakira.kiracode.domain.repository.CharacterCardRepository
import com.kirakira.kiracode.domain.usecase.ExportCharacterCardUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 成品区状态
 */
data class FinishedUiState(
    val cards: List<CharacterCard> = emptyList(),
    val drafts: List<CharacterCard> = emptyList(),   // V3.1 批次1：草稿标签页
    val selectedIds: Set<String> = emptySet(),   // 多选（uuid）
    val isLoading: Boolean = false,
    val toast: String? = null,
    val exporting: Boolean = false
)

/**
 * 成品区 ViewModel：
 * - 展示已完成的角色卡（character_cards 表）
 * - 导出 PNG/JSON 到公共 Download 目录（MediaStore）
 * - 批量导出、删除
 */
@HiltViewModel
class FinishedViewModel @Inject constructor(
    private val characterCardRepository: CharacterCardRepository,
    private val exportUseCase: ExportCharacterCardUseCase,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(FinishedUiState())
    val uiState: StateFlow<FinishedUiState> = _uiState

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val cards = characterCardRepository.getHistory(500, 0).getOrDefault(emptyList())
                .distinctBy { it.uuid }
            // V3.1 批次1：草稿标签页数据（character card 草稿表）
            val drafts = runCatching { characterCardRepository.getAllDrafts().getOrDefault(emptyList()) }
                .getOrDefault(emptyList())
            _uiState.update { it.copy(cards = cards, drafts = drafts, isLoading = false, selectedIds = emptySet()) }
        }
    }

    fun clearToast() {
        _uiState.update { it.copy(toast = null) }
    }

    fun toggleSelect(uuid: String) {
        _uiState.update { ws ->
            val next = if (uuid in ws.selectedIds) ws.selectedIds - uuid else ws.selectedIds + uuid
            ws.copy(selectedIds = next)
        }
    }

    fun selectAll() {
        _uiState.update { ws -> ws.copy(selectedIds = if (ws.selectedIds.size == ws.cards.size) emptySet() else ws.cards.map { it.uuid }.toSet()) }
    }

    /** 单卡导出到 Download */
    fun exportCard(card: CharacterCard, asPng: Boolean) {
        viewModelScope.launch {
            _uiState.update { it.copy(exporting = true) }
            try {
                val bytes: ByteArray
                val mime: String
                if (asPng) {
                    bytes = buildPngBytes(card)
                    mime = "image/png"
                } else {
                    bytes = StCardCodec.toJsonString(card, pretty = true).toByteArray(Charsets.UTF_8)
                    mime = "application/json"
                }
                val fileName = DownloadExporter.sanitizeFileName(card.name.ifBlank { "card" }) +
                        (if (asPng) ".png" else ".json")
                DownloadExporter.save(context, fileName, mime, bytes)
                    .onSuccess { path ->
                        _uiState.update { it.copy(toast = "已导出：$path", exporting = false) }
                    }
                    .onFailure { e ->
                        _uiState.update { it.copy(toast = "导出失败：${e.message}", exporting = false) }
                    }
            } catch (e: Exception) {
                _uiState.update { it.copy(toast = "导出失败：${e.message}", exporting = false) }
            }
        }
    }

    /** 批量导出选中卡 */
    fun exportSelected(asPng: Boolean) {
        val selected = _uiState.value.cards.filter { it.uuid in _uiState.value.selectedIds }
        if (selected.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(exporting = true) }
            var ok = 0
            var lastPath = ""
            for (card in selected) {
                runCatching {
                    val bytes: ByteArray
                    val mime: String
                    if (asPng) {
                        bytes = buildPngBytes(card); mime = "image/png"
                    } else {
                        bytes = StCardCodec.toJsonString(card, pretty = true).toByteArray(Charsets.UTF_8)
                        mime = "application/json"
                    }
                    val fileName = DownloadExporter.sanitizeFileName(card.name.ifBlank { "card" }) +
                            (if (asPng) ".png" else ".json")
                    DownloadExporter.save(context, fileName, mime, bytes).getOrThrow()
                    ok++; 
                }
            }
            _uiState.update { it.copy(exporting = false, toast = "批量导出完成 $ok/${selected.size} → $lastPath") }
        }
    }

    /** 删除单张成品卡 */
    fun deleteCard(uuid: String) {
        viewModelScope.launch {
            val id = characterCardRepository.getHistoryIdByUuid(uuid).getOrNull() ?: return@launch
            characterCardRepository.deleteFinishedCard(id)
            _uiState.update { it.copy(toast = "已删除") }
            load()
        }
    }

    /** 删除草稿（按 uuid） */
    fun deleteDraft(uuid: String) {
        viewModelScope.launch {
            characterCardRepository.deleteDraftByUuid(uuid)
                .onFailure { e -> _uiState.update { it.copy(toast = "删除失败：${e.message}") } }
            _uiState.update { it.copy(toast = "已删除草稿") }
            load()
        }
    }

    /**
     * V1.0.0 返工：为成品卡设置/更换封面（本地图片 URI → data URI 内嵌保存）。
     */
    fun updateCover(uuid: String, imageUri: android.net.Uri) {
        viewModelScope.launch {
            runCatching {
                val bytes = context.contentResolver.openInputStream(imageUri)?.use { it.readBytes() }
                    ?: error("无法读取所选图片")
                if (bytes.size > 8 * 1024 * 1024) error("图片超过 8MB 上限")
                val b64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                val dataUri = "data:image/png;base64,$b64"
                val card = _uiState.value.cards.firstOrNull { it.uuid == uuid } ?: error("卡片不存在")
                characterCardRepository.saveHistory(card.copy(avatar = dataUri)).getOrThrow()
            }.onSuccess {
                _uiState.update { it.copy(toast = "封面已更新") }
                load()
            }.onFailure { e ->
                _uiState.update { it.copy(toast = "封面设置失败：${e.message}") }
            }
        }
    }

    /** 删除选中的多张卡 */
    fun deleteSelected() {
        val ids = _uiState.value.selectedIds.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            for (uuid in ids) {
                characterCardRepository.getHistoryIdByUuid(uuid).getOrNull()?.let {
                    characterCardRepository.deleteFinishedCard(it)
                }
            }
            _uiState.update { it.copy(toast = "已删除 ${ids.size} 张") }
            load()
        }
    }

    /** 生成带嵌入 JSON 的 PNG 字节（复用导出用例的渲染与 tEXt 嵌入） */
    private suspend fun buildPngBytes(card: CharacterCard): ByteArray =
        exportUseCase.renderToPngBytes(card)
}
