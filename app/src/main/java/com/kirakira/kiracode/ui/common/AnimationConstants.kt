package com.kirakira.kiracode.ui.common

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween

/**
 * V3.1 批次2/任务9：全 App 统一动效规范——200ms + FastOutSlowIn。
 */
object AnimationConstants {
    const val DURATION_MS = 200
    val EASING = FastOutSlowInEasing

    fun <T> tween(): androidx.compose.animation.core.FiniteAnimationSpec<T> =
        tween(durationMillis = DURATION_MS, easing = EASING)
}
