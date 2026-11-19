package com.kirakira.kiracode.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * KiraCode 动画工具库
 * 提供统一的动画规范和预设
 */
object KiraCodeAnimations {

    // ============ 时长常量 ============
    const val DURATION_FAST = 150
    const val DURATION_NORMAL = 300
    const val DURATION_SLOW = 500
    const val DURATION_PAGE = 400

    // ============ 缓动曲线 ============
    // 使用 Compose 标准缓动函数避免自定义 CubicBezier
    val EASING_STANDARD: Easing = FastOutSlowInEasing
    val EASING_EMPHASIZED: Easing = FastOutSlowInEasing
    val EASING_DECELERATE: Easing = LinearOutSlowInEasing
    val EASING_ACCELERATE: Easing = FastOutLinearInEasing

    // ============ 预设进入/退出动画 ============

    /**
     * 消息气泡进入动画：从底部淡入 + 缩放
     */
    val messageEnter: EnterTransition = androidx.compose.animation.expandVertically(
        animationSpec = androidx.compose.animation.core.tween(
            durationMillis = DURATION_NORMAL,
            easing = EASING_EMPHASIZED
        )
    ) + fadeIn(
        animationSpec = androidx.compose.animation.core.tween(
            durationMillis = DURATION_FAST,
            easing = EASING_DECELERATE
        )
    )

    /**
     * 消息气泡退出动画：向上淡出 + 缩放
     */
    val messageExit: ExitTransition = androidx.compose.animation.shrinkVertically(
        animationSpec = androidx.compose.animation.core.tween(
            durationMillis = DURATION_FAST,
            easing = EASING_ACCELERATE
        )
    ) + fadeOut(
        animationSpec = androidx.compose.animation.core.tween(
            durationMillis = DURATION_FAST,
            easing = EASING_ACCELERATE
        )
    )

    /**
     * 页面级进入动画：从右侧滑入 + 淡入
     */
    val pageEnter: EnterTransition = androidx.compose.animation.slideInHorizontally(
        initialOffsetX = { it },
        animationSpec = androidx.compose.animation.core.tween(
            durationMillis = DURATION_PAGE,
            easing = EASING_STANDARD
        )
    ) + fadeIn(
        animationSpec = androidx.compose.animation.core.tween(
            durationMillis = DURATION_FAST,
            easing = EASING_DECELERATE
        )
    )

    /**
     * 页面级退出动画：向左滑出 + 淡出
     */
    val pageExit: ExitTransition = androidx.compose.animation.slideOutHorizontally(
        targetOffsetX = { -it },
        animationSpec = androidx.compose.animation.core.tween(
            durationMillis = DURATION_PAGE,
            easing = EASING_STANDARD
        )
    ) + fadeOut(
        animationSpec = androidx.compose.animation.core.tween(
            durationMillis = DURATION_FAST,
            easing = EASING_ACCELERATE
        )
    )

    /**
     * 底部弹出/收起动画
     */
    val bottomSheetEnter: EnterTransition = androidx.compose.animation.slideInVertically(
        initialOffsetY = { it },
        animationSpec = androidx.compose.animation.core.tween(
            durationMillis = DURATION_NORMAL,
            easing = EASING_EMPHASIZED
        )
    ) + fadeIn()

    val bottomSheetExit: ExitTransition = androidx.compose.animation.slideOutVertically(
        targetOffsetY = { it },
        animationSpec = androidx.compose.animation.core.tween(
            durationMillis = DURATION_NORMAL,
            easing = EASING_ACCELERATE
        )
    ) + fadeOut()

    /**
     * 侧边栏滑入/滑出
     */
    val sidebarEnter: EnterTransition = androidx.compose.animation.slideInHorizontally(
        initialOffsetX = { -it },
        animationSpec = androidx.compose.animation.core.tween(
            durationMillis = DURATION_NORMAL,
            easing = EASING_STANDARD
        )
    )

    val sidebarExit: ExitTransition = androidx.compose.animation.slideOutHorizontally(
        targetOffsetX = { -it },
        animationSpec = androidx.compose.animation.core.tween(
            durationMillis = DURATION_NORMAL,
            easing = EASING_STANDARD
        )
    )

    /**
     * 模态框/对话框进入退出
     */
    val modalEnter: EnterTransition = androidx.compose.animation.expandVertically(
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessLow
        )
    ) + fadeIn(
        animationSpec = androidx.compose.animation.core.tween(DURATION_FAST)
    )

    val modalExit: ExitTransition = androidx.compose.animation.shrinkVertically(
        animationSpec = androidx.compose.animation.core.tween(DURATION_FAST, easing = EASING_ACCELERATE)
    ) + fadeOut(
        animationSpec = androidx.compose.animation.core.tween(DURATION_FAST, easing = EASING_ACCELERATE)
    )

    /**
     * Toast/Snackbar 进入退出
     */
    val toastEnter: EnterTransition = androidx.compose.animation.slideInVertically(
        initialOffsetY = { -it },
        animationSpec = androidx.compose.animation.core.tween(DURATION_NORMAL, easing = EASING_EMPHASIZED)
    ) + fadeIn()

    val toastExit: ExitTransition = androidx.compose.animation.slideOutVertically(
        targetOffsetY = { -it },
        animationSpec = androidx.compose.animation.core.tween(DURATION_NORMAL, easing = EASING_ACCELERATE)
    ) + fadeOut()
}

// ============ 专用动画组件 ============

/**
 * 消息气泡容器 - 带进入/退出动画
 */
@Composable
fun AnimatedMessageBubble(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = KiraCodeAnimations.messageEnter,
        exit = KiraCodeAnimations.messageExit,
        modifier = modifier
    ) {
        content()
    }
}

/**
 * 页面内容容器 - 带页面切换动画
 */
@Composable
fun AnimatedPageContent(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = KiraCodeAnimations.pageEnter,
        exit = KiraCodeAnimations.pageExit,
        modifier = modifier.fillMaxSize()
    ) {
        content()
    }
}

/**
 * 底部弹出面板容器
 */
@Composable
fun AnimatedBottomSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = KiraCodeAnimations.bottomSheetEnter,
        exit = KiraCodeAnimations.bottomSheetExit,
        modifier = modifier
    ) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)),
            contentAlignment = Alignment.BottomCenter
        ) {
            content()
        }
    }
}

/**
 * 列表项错位进入动画
 */
@Composable
fun StaggeredItemAnimation(
    index: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val delay = (index * 50).coerceAtMost(300)
    LaunchedEffect(Unit) {
        // 触发重组以启动动画
    }
    AnimatedVisibility(
        visible = true,
        enter = fadeIn(
            animationSpec = androidx.compose.animation.core.tween(
                durationMillis = 300,
                delayMillis = delay,
                easing = KiraCodeAnimations.EASING_EMPHASIZED
            )
        ) + androidx.compose.animation.slideInVertically(
            initialOffsetY = { it / 6 },
            animationSpec = androidx.compose.animation.core.tween(
                durationMillis = 400,
                delayMillis = delay,
                easing = KiraCodeAnimations.EASING_EMPHASIZED
            )
        ),
        modifier = modifier
    ) {
        content()
    }
}

/**
 * 脉冲/呼吸动画（用于加载状态、录音指示等）
 */
@Composable
fun PulsingAnimation(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    size: androidx.compose.ui.unit.Dp = 8.dp
) {
    val infiniteTransition = rememberInfiniteTransition()
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(1000, easing = KiraCodeAnimations.EASING_EMPHASIZED),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        )
    )
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(1000, easing = KiraCodeAnimations.EASING_EMPHASIZED),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        )
    )

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
            }
            .background(color, RoundedCornerShape(size / 2))
    )
}

/**
 * 打字机指示器（三个跳动的点）
 */
@Composable
fun TypingIndicator(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
    dotSize: androidx.compose.ui.unit.Dp = 6.dp,
    spacing: androidx.compose.ui.unit.Dp = 4.dp
) {
    val infiniteTransition = rememberInfiniteTransition()
    
    val alphas = (0..2).map { index ->
        infiniteTransition.animateFloat(
            initialValue = 0.3f,
            targetValue = 1f,
            animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                animation = androidx.compose.animation.core.tween(
                    durationMillis = 600,
                    delayMillis = index * 150,
                    easing = KiraCodeAnimations.EASING_EMPHASIZED
                ),
                repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
            ),
            label = "typing_dot_$index"
        )
    }

    Row(
        modifier = modifier.padding(top = 4.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(spacing)
    ) {
        alphas.forEachIndexed { index, alphaState ->
            Box(
                modifier = Modifier
                    .size(dotSize)
                    .graphicsLayer { this.alpha = alphaState.value }
                    .background(color, RoundedCornerShape(dotSize / 2))
            )
        }
    }
}

/**
 * 加载骨架屏闪烁动画
 */
@Composable
fun SkeletonShimmer(
    modifier: Modifier = Modifier,
    baseColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    highlightColor: Color = MaterialTheme.colorScheme.surface
) {
    val infiniteTransition = rememberInfiniteTransition()
    val translationX by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(1500, easing = androidx.compose.animation.core.LinearEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Restart
        )
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(baseColor)
            .graphicsLayer {
                // 用 alpha 脉冲近似 shimmer 效果，避免依赖 clipPath / 测量尺寸
                this.alpha = 1f - (translationX * translationX)
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(highlightColor)
        )
    }
}

/**
 * 页面共享元素转场容器
 * 用于列表项点击后展开详情页的共享元素转场
 */
@Composable
fun SharedElementContainer(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    // 共享元素转场需要配合 Navigation Compose 的 shared element API
    // 这里提供基础容器，具体实现需要在 Navigation 中配置
    Box(modifier = modifier.fillMaxSize()) {
        content()
    }
}