package com.kirakira.kiracode.ui.workspace

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kirakira.kiracode.ui.theme.AuroraColorTokens
import com.kirakira.kiracode.ui.theme.AuroraTypography

/**
 * 手机/横屏使用的紧凑顶栏：圆形按钮 + 标题胶囊。
 * 设置入口保留在左侧抽屉的 RecordRail 中，避免顶栏拥挤。
 */
@Composable
fun CompactTopBar(
    sessionTitle: String?,
    agentStatus: AgentStatus,
    modelName: String?,
    onMenuClick: () -> Unit,
    onMonitorClick: () -> Unit,
    onNewSessionClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(AuroraColorTokens.Base0)
            .padding(horizontal = 12.dp)
    ) {
        Surface(
            onClick = onMenuClick,
            shape = CircleShape,
            color = AuroraColorTokens.Base2,
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                Icons.Filled.Menu,
                contentDescription = "会话历史",
                tint = AuroraColorTokens.TextBody,
                modifier = Modifier
                    .padding(10.dp)
                    .fillMaxSize()
            )
        }

        Surface(
            shape = RoundedCornerShape(20.dp),
            color = AuroraColorTokens.Base2,
            modifier = Modifier.weight(1f)
        ) {
            androidx.compose.foundation.layout.Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Text(
                    text = sessionTitle ?: "新会话",
                    style = AuroraTypography.Body.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = AuroraColorTokens.TextTitle
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    com.kirakira.kiracode.ui.components.StatusDot(
                        status = agentStatus.toDotStatus(),
                        size = 6.dp
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "KiraCard · ${agentStatus.label()} · ${modelName ?: ""}",
                        style = AuroraTypography.Caption.copy(
                            color = AuroraColorTokens.TextCaption
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Surface(
            onClick = onMonitorClick,
            shape = CircleShape,
            color = AuroraColorTokens.Base2,
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                Icons.Filled.Visibility,
                contentDescription = "监控",
                tint = AuroraColorTokens.TextBody,
                modifier = Modifier
                    .padding(10.dp)
                    .fillMaxSize()
            )
        }

        Surface(
            onClick = onNewSessionClick,
            shape = CircleShape,
            color = AuroraColorTokens.Base2,
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                Icons.Filled.Add,
                contentDescription = "新建会话",
                tint = AuroraColorTokens.TextBody,
                modifier = Modifier
                    .padding(10.dp)
                    .fillMaxSize()
            )
        }
    }
}
