package com.kirakira.kiracode.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kirakira.kiracode.ui.theme.AuroraColorTokens

enum class DotStatus {
    RUNNING,   // 黄
    WAITING,   // 紫
    SUCCESS,   // 青
    ERROR,     // 红
    IDLE       // 灰
}

@Composable
fun StatusDot(
    status: DotStatus,
    modifier: Modifier = Modifier,
    size: Dp = 8.dp
) {
    val color = when (status) {
        DotStatus.RUNNING -> AuroraColorTokens.AuroraAccent
        DotStatus.WAITING -> AuroraColorTokens.SignalThinking
        DotStatus.SUCCESS -> AuroraColorTokens.SignalSuccess
        DotStatus.ERROR -> AuroraColorTokens.SignalError
        DotStatus.IDLE -> AuroraColorTokens.SignalNeutral
    }

    Box(
        modifier = modifier
            .size(size)
            .background(color, CircleShape)
    )
}
