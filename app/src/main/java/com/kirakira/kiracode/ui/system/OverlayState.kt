package com.kirakira.kiracode.ui.system

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * L1 一级浮窗（同时最多打开一个；已打开时再次打开=替换）。
 */
sealed interface Overlay {
    /** 工作记录浮窗 */
    data object WorkRecords : Overlay

    /** 资料库浮窗（参考卡/成品/草稿；initialTab: 0=参考卡 1=成品 2=草稿） */
    data class Library(val initialTab: Int = 0) : Overlay

    /** 设置浮窗（LLM 方案/运行参数/人格/提示词/诊断/关于） */
    data class Settings(val initialTab: Int = 0) : Overlay

    /** 导入浮窗（文件/粘贴/AI 生成） */
    data object Import : Overlay

    /** 编辑浮窗（全字段表单） */
    data class Edit(val card: com.kirakira.kiracode.domain.model.CharacterCard) : Overlay
}

/**
 * L2 二级参数浮窗（同时最多打开一个；随 L1 关闭而关闭）。
 */
sealed interface SecondaryOverlay {
    /** 导出（direct=true 走通用导出路径（文件名自动生成），false 走工作卡导出（文件名可自定义）） */
    data class Export(
        val card: com.kirakira.kiracode.domain.model.CharacterCard,
        val direct: Boolean = false
    ) : SecondaryOverlay
}

/**
 * 浮窗宿主状态：L0 工作台之上的统一浮窗层级管理。
 *
 * 层级规则：
 * - L1 同时最多一个，重复打开=替换；
 * - L2 同时最多一个，依附于当前 L1；
 * - 返回键优先关 L2，再关 L1；
 * - 关闭语义（直接关/确认/异步禁关）由各浮窗内容自行实现。
 */
class OverlayHostState {
    var l1 by mutableStateOf<Overlay?>(null)
        private set
    var l2 by mutableStateOf<SecondaryOverlay?>(null)
        private set

    val hasOverlay: Boolean
        get() = l1 != null || l2 != null

    /** 打开 L1（互斥，替换当前） */
    fun open(overlay: Overlay) {
        l1 = overlay
    }

    /** 打开 L2（单发） */
    fun openSecondary(overlay: SecondaryOverlay) {
        if (l2 == null) l2 = overlay
    }

    fun closeL1() {
        l1 = null
        l2 = null
    }

    fun closeL2() {
        l2 = null
    }
}
