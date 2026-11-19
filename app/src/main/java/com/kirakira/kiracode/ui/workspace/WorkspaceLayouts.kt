package com.kirakira.kiracode.ui.workspace

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kirakira.kiracode.ui.theme.AuroraColorTokens

/**
 * 三档布局骨架。内容/顶栏/底栏/监控/抽屉都以 slot 注入，
 * 避免把 40+ 个业务参数穿透到布局层。
 */
@Composable
fun CompactWorkspaceLayout(
    drawerOpen: Boolean,
    onDrawerClose: () -> Unit,
    topBar: @Composable () -> Unit,
    content: @Composable (Modifier) -> Unit,
    bottom: @Composable () -> Unit,
    drawerContent: @Composable () -> Unit,
    monitorSheetOpen: Boolean,
    onMonitorDismiss: () -> Unit,
    monitorContent: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            topBar()
            content(Modifier.weight(1f))
            bottom()
        }

        AnimatedVisibility(
            visible = drawerOpen,
            enter = fadeIn() + slideInHorizontally { -it / 3 },
            exit = fadeOut() + slideOutHorizontally { -it / 3 }
        ) {
            Box(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f))
                        .clickable(onClick = onDrawerClose)
                )
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(0.86f)
                        .widthIn(max = 320.dp)
                        .background(AuroraColorTokens.Base1)
                        .align(Alignment.CenterStart)
                ) {
                    drawerContent()
                }
            }
        }

        if (monitorSheetOpen) {
            MonitorBottomSheet(onDismiss = onMonitorDismiss) {
                monitorContent()
            }
        }
    }
}

@Composable
fun MediumWorkspaceLayout(
    railContent: @Composable () -> Unit,
    topBar: @Composable () -> Unit,
    content: @Composable (Modifier) -> Unit,
    bottom: @Composable () -> Unit,
    drawerOpen: Boolean,
    onDrawerClose: () -> Unit,
    drawerContent: @Composable () -> Unit,
    monitorOpen: Boolean,
    onMonitorClose: () -> Unit,
    monitorContent: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxSize()) {
            Box(Modifier.width(64.dp).fillMaxHeight()) { railContent() }
            Column(Modifier.weight(1f).fillMaxHeight()) {
                topBar()
                content(Modifier.weight(1f))
                bottom()
            }
        }

        AnimatedVisibility(
            visible = drawerOpen,
            enter = fadeIn() + slideInHorizontally { -it / 3 },
            exit = fadeOut() + slideOutHorizontally { -it / 3 }
        ) {
            Box(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f))
                        .clickable(onClick = onDrawerClose)
                )
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(0.5f)
                        .widthIn(max = 320.dp)
                        .background(AuroraColorTokens.Base1)
                        .align(Alignment.CenterStart)
                ) {
                    drawerContent()
                }
            }
        }

        AnimatedVisibility(
            visible = monitorOpen,
            enter = slideInHorizontally { it } + fadeIn(),
            exit = slideOutHorizontally { it } + fadeOut()
        ) {
            Box(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f))
                        .clickable(onClick = onMonitorClose)
                )
                Box(
                    Modifier
                        .fillMaxHeight()
                        .width(340.dp)
                        .background(AuroraColorTokens.Base1)
                        .align(Alignment.CenterEnd)
                ) {
                    monitorContent()
                }
            }
        }
    }
}

@Composable
fun ExpandedWorkspaceLayout(
    topBar: @Composable () -> Unit,
    sidebarCollapsed: Boolean,
    sidebarContent: @Composable () -> Unit,
    content: @Composable (Modifier) -> Unit,
    bottom: @Composable () -> Unit,
    monitorVisible: Boolean,
    monitorContent: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    val sidebarWidth by animateDpAsState(
        targetValue = if (sidebarCollapsed) 64.dp else 220.dp,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = 320f),
        label = "sidebar_width"
    )

    Column(modifier.fillMaxSize()) {
        topBar()
        Row(Modifier.weight(1f)) {
            Box(Modifier.width(sidebarWidth).fillMaxHeight()) { sidebarContent() }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f).fillMaxHeight()) {
                content(Modifier.weight(1f))
                bottom()
            }
            AnimatedVisibility(
                visible = monitorVisible,
                enter = slideInHorizontally { it } + fadeIn(),
                exit = slideOutHorizontally { it } + fadeOut()
            ) {
                Row(Modifier.fillMaxHeight()) {
                    Spacer(Modifier.width(16.dp))
                    Box(Modifier.width(360.dp).fillMaxHeight()) { monitorContent() }
                }
            }
        }
    }
}
