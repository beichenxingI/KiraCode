package com.kirakira.kiracode.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

object IndustrialShapes {
    val None = RoundedCornerShape(0.dp)     // 面板/分隔条
    val Micro = RoundedCornerShape(2.dp)    // 输入框/按钮
    val Small = RoundedCornerShape(4.dp)    // 浮窗/卡片网格项
}

/** Aurora 圆角：Q 弹圆润，但保留分隔条/贴边容器的 0dp。 */
object AuroraShapes {
    val None = RoundedCornerShape(0.dp)
    val Micro = RoundedCornerShape(8.dp)       // chip、tag、小图标按钮
    val Small = RoundedCornerShape(12.dp)      // 按钮、输入框
    val Medium = RoundedCornerShape(18.dp)     // 事件卡、工具卡
    val Large = RoundedCornerShape(24.dp)      // 面板、浮窗
    val ExtraLarge = RoundedCornerShape(28.dp) // BottomSheet、Hero
}
