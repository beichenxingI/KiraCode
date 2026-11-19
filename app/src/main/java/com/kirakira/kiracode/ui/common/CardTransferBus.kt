package com.kirakira.kiracode.ui.common

import com.kirakira.kiracode.domain.model.CharacterCard
import com.kirakira.kiracode.domain.model.ExportFormat
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * 页面间角色卡传递总线（进程级单例）。
 *
 * 取代 URL 编码整卡 JSON 的导航传参方式：
 * 大卡（含 Base64 头像/长世界书）编码进导航路由会触发
 * TransactionTooLargeException / URI 解析异常导致闪退，此处彻底规避。
 */
object CardTransferBus {

    /** 导入 → 生成页的参考卡 */
    @Volatile var referenceCard: CharacterCard? = null

    /** 生成/编辑 → 编辑页的工作卡 */
    @Volatile var editCard: CharacterCard? = null

    /** 编辑 → 导出页的最终卡 */
    @Volatile var exportCard: CharacterCard? = null

    /** 任意来源 → 聊天工作区（设为工作卡） */
    @Volatile var workingCard: CharacterCard? = null

    /** 导出格式（伴随 exportCard） */
    @Volatile var exportFormat: ExportFormat = ExportFormat.JSON

    fun clearAll() {
        referenceCard = null; editCard = null; exportCard = null; workingCard = null
    }
}
