package com.kirakira.kiracode.ui.common

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 参考卡选择总线：跨页面共享「当前会话选定的参考卡 UUID 列表」。
 *
 * 角色卡仓库页多选确认后写入；聊天 ViewModel 订阅并同步到会话工作区。
 * （进程级单例，无需 DI）
 */
object ReferenceSelectionBus {
    private val _selectedUuids = MutableStateFlow<List<String>>(emptyList())
    val selectedUuids: StateFlow<List<String>> = _selectedUuids

    fun publish(uuids: List<String>) {
        _selectedUuids.value = uuids
    }
}
