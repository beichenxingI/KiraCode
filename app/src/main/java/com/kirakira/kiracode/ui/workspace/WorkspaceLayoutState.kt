package com.kirakira.kiracode.ui.workspace

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration

/**
 * 三档响应式宽度：
 * COMPACT < 600dp（手机竖屏）
 * MEDIUM 600-840dp（手机横屏/折叠屏/小平板）
 * EXPANDED > 840dp（平板）
 */
enum class WindowSizeClass { COMPACT, MEDIUM, EXPANDED }

@Composable
fun rememberWindowSizeClass(): WindowSizeClass {
    val configuration = LocalConfiguration.current
    return remember(configuration.screenWidthDp) {
        when {
            configuration.screenWidthDp < 600 -> WindowSizeClass.COMPACT
            configuration.screenWidthDp < 840 -> WindowSizeClass.MEDIUM
            else -> WindowSizeClass.EXPANDED
        }
    }
}

data class WorkspaceLayoutState(
    val windowSize: WindowSizeClass,
    val drawerOpen: Boolean = false,
    val monitorSheetOpen: Boolean = false,
    val monitorSheetFullScreen: Boolean = false
)
