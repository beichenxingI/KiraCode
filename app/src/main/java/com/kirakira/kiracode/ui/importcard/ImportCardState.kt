package com.kirakira.kiracode.ui.importcard

import com.kirakira.kiracode.domain.model.CharacterCard

/**
 * 导入页面 UI 状态：单一 data class，随 MutableStateFlow 更新
 */
data class ImportCardUiState(
    val jsonText: String = "",
    val isLoading: Boolean = false,
    val parsedCard: CharacterCard? = null,
    val errorMessage: String? = null
)
