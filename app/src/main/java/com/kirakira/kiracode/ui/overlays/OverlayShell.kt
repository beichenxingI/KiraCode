package com.kirakira.kiracode.ui.overlays

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kirakira.kiracode.ui.theme.AuroraColorTokens

/**
 * 共用浮窗壳：手机底部贴边大圆角，平板居中限宽。
 * 只负责容器，不承载业务状态。
 */
@Composable
fun OverlayShell(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        val isCompact = LocalConfiguration.current.screenWidthDp < 600
        Box(modifier = Modifier.fillMaxSize()) {
            Surface(
                shape = if (isCompact) {
                    RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
                } else {
                    RoundedCornerShape(20.dp)
                },
                color = AuroraColorTokens.Base1,
                border = BorderStroke(0.5.dp, AuroraColorTokens.LinePrimary),
                modifier = if (isCompact) {
                    modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.94f)
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .imePadding()
                } else {
                    modifier
                        .padding(horizontal = 24.dp)
                        .widthIn(max = 840.dp)
                        .fillMaxWidth()
                        .fillMaxHeight(0.88f)
                        .align(Alignment.Center)
                }
            ) {
                content()
            }
        }
    }
}
