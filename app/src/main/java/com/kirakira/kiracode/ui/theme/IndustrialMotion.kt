package com.kirakira.kiracode.ui.theme

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

object IndustrialMotion {
    // 反馈动画：状态切换/颜色变化/新事件进场
    fun <T> feedback() = tween<T>(
        durationMillis = 160,
        easing = FastOutSlowInEasing
    )

    // 展开/收起：思维链/步骤/摘要
    fun <T> expand() = tween<T>(
        durationMillis = 200,
        easing = FastOutSlowInEasing
    )

    // 面板滑入/滑出：浮窗/侧栏停靠
    fun <T> panel() = tween<T>(
        durationMillis = 240,
        easing = androidx.compose.animation.core.EaseOutCubic
    )

    // 页面路由转场
    fun <T> transition() = tween<T>(
        durationMillis = 240,
        easing = androidx.compose.animation.core.EaseOutCubic
    )

    // 唯一循环动画：流式 shimmer
    const val LOOP_DURATION = 700
}

/** Aurora 动效：Q 弹按压、卡片/面板/Sheet 的 spring 参数。 */
object AuroraMotion {
    val PressDown = spring<Float>(
        dampingRatio = 0.55f,
        stiffness = 900f
    )
    val PressRelease = spring<Float>(
        dampingRatio = 0.75f,
        stiffness = 600f
    )
    val CardExpand = spring<Float>(
        dampingRatio = 0.72f,
        stiffness = 380f
    )
    val PanelSlide = spring<Float>(
        dampingRatio = 0.85f,
        stiffness = 320f
    )
    val SheetSettle = spring<Float>(
        dampingRatio = 0.80f,
        stiffness = 300f
    )
    val PageTransition = tween<Float>(
        durationMillis = 260,
        easing = FastOutSlowInEasing
    )
    val CardEnter = spring<Float>(
        dampingRatio = 0.62f,
        stiffness = 520f
    )
}
