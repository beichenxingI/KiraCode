package com.kirakira.kiracode.ui.theme

import android.content.Context
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Indication
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.kirakira.kiracode.domain.repository.PreferencesRepository
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * KiraCode 全局配色（V2.1 CodeX 极简暗色版）
 *
 * 对齐 CodeX 暗色极简美学：
 * - 背景   #0B0E14（蓝黑）
 * - 卡片   #1E1E1E / 输入面 #1E2430（蓝调深灰）
 * - 文字   #F8FAFC（浅灰白）
 * - 强调   紫 #9F86FF
 */
object KiraCodeColors {

    // ============ 规范色板 ============
    val AppBackground = Color(0xFF0B0E14)
    val CardSurface = Color(0xFF1E1E1E)
    val InputSurface = Color(0xFF1E2430)
    val ElevatedSurface = Color(0xFF252B3A)

    /**
     * 深色主题（唯一启用主题）。
     * surface=卡片色，background=页面背景，surfaceContainer*=分层表面。
     */
    val DarkColorScheme = darkColorScheme(
        primary = AuroraColorTokens.AuroraAccent,
        onPrimary = AuroraColorTokens.Base0,
        primaryContainer = AuroraColorTokens.Base2,
        onPrimaryContainer = AuroraColorTokens.TextTitle,
        secondary = AuroraColorTokens.AuroraViolet,
        onSecondary = AuroraColorTokens.Base0,
        secondaryContainer = AuroraColorTokens.Base2,
        onSecondaryContainer = AuroraColorTokens.TextTitle,
        tertiary = AuroraColorTokens.SignalSuccess,
        onTertiary = AuroraColorTokens.Base0,
        tertiaryContainer = AuroraColorTokens.Base2,
        onTertiaryContainer = AuroraColorTokens.TextTitle,
        error = AuroraColorTokens.SignalError,
        onError = AuroraColorTokens.Base0,
        errorContainer = AuroraColorTokens.Base2,
        onErrorContainer = AuroraColorTokens.TextTitle,
        // 表面层级：背景 < 卡片 < 浮层
        background = AuroraColorTokens.Base0,
        onBackground = AuroraColorTokens.TextBody,
        surface = AuroraColorTokens.Base1,
        onSurface = AuroraColorTokens.TextTitle,
        surfaceVariant = AuroraColorTokens.Base2,
        onSurfaceVariant = AuroraColorTokens.TextCaption,
        surfaceDim = AuroraColorTokens.Base0,
        surfaceBright = AuroraColorTokens.Base2,
        surfaceContainerLowest = AuroraColorTokens.Base0,
        surfaceContainerLow = AuroraColorTokens.Base1,
        surfaceContainer = AuroraColorTokens.Base1,
        surfaceContainerHigh = AuroraColorTokens.Base2,
        surfaceContainerHighest = AuroraColorTokens.LinePrimary,
        outline = AuroraColorTokens.LinePrimary,
        outlineVariant = AuroraColorTokens.LineSecondary,
        scrim = Color(0xCC000000),
        inverseSurface = AuroraColorTokens.TextTitle,
        inverseOnSurface = AuroraColorTokens.Base0,
        inversePrimary = AuroraColorTokens.AuroraAccentStrong
    )

    /** 兼容旧引用：浅色方案已移除，统一回落深色（禁止白底） */
    val LightColorScheme get() = DarkColorScheme
}

/**
 * 应用主题：全局强制深色（themeMode 仅保留数据键兼容，不再影响渲染）。
 * V2 Beta 手感优化：全局禁用白色 ripple/splash（LocalIndication = NoIndication），
 * 所有可点击元素使用 [bouncyClickable] 的 iOS 风格按压缩放反馈。
 */
@Composable
fun KiraCodeTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    // iOS 手感：无 ripple / 无白色 splash，点击反馈统一走按压缩放
    CompositionLocalProvider(LocalIndication provides KiraNoIndication) {
        IndustrialTheme {
            MaterialTheme(
                colorScheme = KiraCodeColors.DarkColorScheme,
                typography = KiraCodeTypography.Typography,
                shapes = KiraCodeShapes,
                content = content
            )
        }
    }
}

/**
 * 兼容旧调用点：忽略偏好中的主题模式，始终深色。
 */
@Composable
fun KiraCodeThemeWithPreferences(
    preferencesRepository: PreferencesRepository,
    content: @Composable () -> Unit
) {
    KiraCodeTheme(darkTheme = true, content = content)
}

/**
 * 系统栏颜色跟随深色主题
 */
@Composable
fun SystemBarsColorSync(
    darkTheme: Boolean,
    context: Context
) {
    SideEffect {
        val window = (context as? android.app.Activity)?.window
            ?: return@SideEffect

        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = false

        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
    }
}

/** 兼容旧枚举 */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

@Composable
fun rememberThemeMode(): androidx.compose.runtime.MutableState<ThemeMode> {
    return androidx.compose.runtime.mutableStateOf(ThemeMode.DARK)
}

/** 恒为 true（全局强制深色） */
@Composable
fun isDarkTheme(preferencesRepository: PreferencesRepository): Boolean = true

// ==================== V2 Beta 手感与动画（iOS 风格） ====================

/**
 * 空 Indication：不绘制任何 ripple/splash/高亮。
 * （foundation 1.6.x 的 NoIndication 是 private，这里等价自实现）
 */
private object KiraNoIndication : Indication {
    @Composable
    override fun rememberUpdatedInstance(
        interactionSource: androidx.compose.foundation.interaction.InteractionSource
    ): androidx.compose.foundation.IndicationInstance = KiraNoIndicationInstance
}

/** 什么都不绘制的 IndicationInstance（仅 drawContent，无任何 ripple/白底覆盖） */
private object KiraNoIndicationInstance : androidx.compose.foundation.IndicationInstance {
    override fun androidx.compose.ui.graphics.drawscope.ContentDrawScope.drawIndication() {
        drawContent()
    }
}

/** iOS 按压曲线：easeOutCubic 近似（快出慢收） */
val KiraEaseOutCubic = CubicBezierEasing(0.215f, 0.61f, 0.355f, 1f)

/**
 * iOS 风格点击：无 ripple，按下轻微缩小 + 轻微降透明度，松开弹回。
 * 时长 100-150ms，easeOutCubic 曲线。替代一切会出白底的 clickable。
 */
fun Modifier.bouncyClickable(
    enabled: Boolean = true,
    onPressScale: Float = 0.96f,
    onPressed: () -> Unit = {},
    onClick: () -> Unit
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (pressed && enabled) onPressScale else 1f,
        animationSpec = AuroraMotion.PressDown,
        label = "bouncyScale"
    )
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
            alpha = if (pressed && enabled) 0.92f else 1f
        }
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled
        ) {
            onPressed()
            onClick()
        }
}

/**
 * 列表 iOS 风格滚动（带回弹）。用于 Column verticalScroll（overscroll 效果由系统 Android 12+ 自带 stretch）。
 */
@Composable
fun rememberBouncyScrollState(): androidx.compose.foundation.ScrollState =
    androidx.compose.foundation.rememberScrollState()

/** 主按钮：iOS 按压缩放 + 主题色（无 ripple） */
@Composable
fun BouncyPrimaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit
) {
    androidx.compose.material3.Surface(
        color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (enabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
        modifier = modifier
            .fillMaxWidth()
            .bouncyClickable(enabled = enabled) { onClick() }
    ) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.padding(vertical = 14.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) { content() }
    }
}
