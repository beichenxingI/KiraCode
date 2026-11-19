package com.kirakira.kiracode.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Aurora 色板（深色，当前唯一启用）。
 * 极光青为主 accent，工业黄仅保留为 KiraGold 点缀。
 */
object AuroraColorTokens {
    // 背景三层
    val Base0 = Color(0xFF070A12)
    val Base1 = Color(0xFF0D1320)
    val Base2 = Color(0xFF151D2B)

    // 线条
    val LinePrimary = Color(0xFF26344A)
    val LineSecondary = Color(0xFF1B2536)
    val LineDisabled = Color(0xFF141B28)

    // 文字
    val TextTitle = Color(0xFFF2F7FF)
    val TextBody = Color(0xFFC6D3E6)
    val TextCaption = Color(0xFF8B9AB3)
    val TextTech = Color(0xFF6F7F99)

    // Aurora 主色
    val AuroraAccent = Color(0xFF6EF3E0)
    val AuroraAccentStrong = Color(0xFF22D3C5)
    val AuroraViolet = Color(0xFFA78BFA)

    // Kira 点缀（保留品牌延续）
    val KiraGold = Color(0xFFFFD86B)

    // 语义色
    val SignalSuccess = Color(0xFF4ADE80)
    val SignalError = Color(0xFFFF7A8A)
    val SignalWarning = Color(0xFFFFC857)
    val SignalThinking = Color(0xFFA78BFA)
    val SignalNeutral = Color(0xFF5B6B82)

    val IconDefault = TextCaption
    val IconActive = AuroraAccent
}

/**
 * 工业工作台色板 - 保留旧名称作为 Aurora 别名，避免已有调用点一次性迁移。
 */
object IndustrialColorTokens {
    val Base0 = AuroraColorTokens.Base0
    val Base1 = AuroraColorTokens.Base1
    val Base2 = AuroraColorTokens.Base2

    val LinePrimary = AuroraColorTokens.LinePrimary
    val LineSecondary = AuroraColorTokens.LineSecondary
    val LineDisabled = AuroraColorTokens.LineDisabled

    val TextTitle = AuroraColorTokens.TextTitle
    val TextBody = AuroraColorTokens.TextBody
    val TextCaption = AuroraColorTokens.TextCaption
    val TextTech = AuroraColorTokens.TextTech

    val SignalWarning = AuroraColorTokens.SignalWarning
    val SignalSuccess = AuroraColorTokens.SignalSuccess
    val SignalError = AuroraColorTokens.SignalError
    val SignalThinking = AuroraColorTokens.SignalThinking
    val SignalNeutral = AuroraColorTokens.SignalNeutral

    val IconDefault = AuroraColorTokens.IconDefault
    val IconActive = AuroraColorTokens.IconActive
}

/**
 * 浅色主题（Paper 制图纸，阶段 D2 启用）
 */
object IndustrialColorTokensPaper {
    val Base0 = Color(0xFFFAFAF8)
    val Base1 = Color(0xFFF1F1EC)
    val Base2 = Color(0xFFE7E7E0)

    val LinePrimary = Color(0xFF141414)
    val LineSecondary = Color(0xFFC9C9C0)
    val LineDisabled = Color(0xFFE2E2DB)

    val TextTitle = Color(0xFF141412)
    val TextBody = Color(0xFF33332F)
    val TextCaption = Color(0xFF6E6E66)
    val TextTech = Color(0xFF8A8A80)

    val SignalWarning = Color(0xFFFFE01B)  // 浅色黄必配黑字
    val SignalSuccess = Color(0xFF00875F)
    val SignalError = Color(0xFFD0342C)
    val SignalThinking = Color(0xFF6750A4)
    val SignalNeutral = Color(0xFF9A9A92)

    val IconDefault = TextCaption
    val IconActive = SignalWarning
}
