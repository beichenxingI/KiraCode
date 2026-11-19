package com.kirakira.kiracode.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * KiraCode 统一形状规范
 *
 * 全局 Material3 Shape scheme，统一所有 Material3 组件（Button/Card/
 * Dialog/TextField/Surface 等）的圆角，避免逐个硬编码。
 *
 * 圆角策略（更圆润、专业）：
 * - extraSmall: 8dp  — 小型内嵌元素
 * - small:      12dp — 按钮、芯片、输入框（任务书：按钮 12dp）
 * - medium:     16dp — 卡片、菜单、弹出层（任务书：卡片 16dp）
 * - large:      24dp — 底部弹出层、大卡片
 * - extraLarge: 28dp — 全屏弹窗、底部表单
 */
val KiraCodeShapes: Shapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(2.dp),
    medium = RoundedCornerShape(2.dp),
    large = RoundedCornerShape(4.dp),
    extraLarge = RoundedCornerShape(4.dp)
)
