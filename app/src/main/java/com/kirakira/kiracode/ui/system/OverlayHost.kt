package com.kirakira.kiracode.ui.system

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable

/**
 * 统一浮窗宿主：工作台内容常驻，浮窗按层级叠加。
 *
 * - 返回键优先关闭 L2，再关闭 L1，最后交还系统；
 * - 浮窗渲染委托给调用方（各浮窗内容自持状态与关闭语义）；
 * - 背景工作台不因浮窗打开而销毁或重排。
 */
@Composable
fun OverlayHost(
    state: OverlayHostState,
    content: @Composable () -> Unit,
    l1Content: @Composable (Overlay) -> Unit,
    l2Content: @Composable (SecondaryOverlay) -> Unit
) {
    BackHandler(enabled = state.l2 != null) { state.closeL2() }
    BackHandler(enabled = state.l2 == null && state.l1 != null) { state.closeL1() }

    content()
    state.l1?.let { l1Content(it) }
    state.l2?.let { l2Content(it) }
}
