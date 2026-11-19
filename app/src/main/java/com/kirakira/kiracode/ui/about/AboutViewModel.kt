package com.kirakira.kiracode.ui.about

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kirakira.kiracode.data.remote.AnnouncementClient
import com.kirakira.kiracode.ui.announce.AnnounceUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * 「关于」页 ViewModel：检查更新（拉取公告）+ AGPLv3 协议全文（assets）。
 */
@HiltViewModel
class AboutViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _announceState = MutableStateFlow<AnnounceUiState?>(null)
    val announceState: StateFlow<AnnounceUiState?> = _announceState

    /** 协议全文（assets/LICENSE_agplv3.txt） */
    private val _licenseText = MutableStateFlow<String?>(null)
    val licenseText: StateFlow<String?> = _licenseText

    init {
        viewModelScope.launch {
            _licenseText.value = withContext(Dispatchers.IO) {
                runCatching {
                    context.assets.open("LICENSE_agplv3.txt").use { input ->
                        input.bufferedReader(Charsets.UTF_8).readText()
                    }
                }.getOrNull()
            }
        }
    }

    fun checkUpdate() {
        if (_announceState.value is AnnounceUiState.Loading) return
        _announceState.value = AnnounceUiState.Loading
        viewModelScope.launch {
            AnnouncementClient.fetch()
                .onSuccess { _announceState.value = AnnounceUiState.Ready(it) }
                .onFailure { e ->
                    _announceState.value = AnnounceUiState.Failed(e.message ?: "网络异常，请稍后重试")
                }
        }
    }

    fun dismissAnnouncement() {
        _announceState.value = null
    }
}
