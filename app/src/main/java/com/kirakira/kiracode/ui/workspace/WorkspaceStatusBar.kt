package com.kirakira.kiracode.ui.workspace

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kirakira.kiracode.ui.components.DotStatus
import com.kirakira.kiracode.ui.components.StatusDot
import com.kirakira.kiracode.ui.theme.AuroraColorTokens
import com.kirakira.kiracode.ui.theme.IndustrialSpacing
import com.kirakira.kiracode.ui.theme.IndustrialTypography

/** 顶栏状态条：菜单/品牌 | 当前工作记录 | Agent状态 | 模型 | 监控 | 设置 | 新建 */
@Composable
fun WorkspaceStatusBar(
    sessionTitle: String?,
    agentStatus: AgentStatus,
    modelName: String?,
    onMenuClick: () -> Unit,
    onMonitorClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onNewSessionClick: () -> Unit
) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(IndustrialSpacing.StatusBarHeight)
                .background(AuroraColorTokens.Base1)
                .padding(horizontal = IndustrialSpacing.Compact)
        ) {
            IconButton(onClick = onMenuClick) {
                Icon(
                    Icons.Filled.Menu,
                    contentDescription = "工作记录栏",
                    tint = AuroraColorTokens.TextCaption,
                    modifier = Modifier.size(IndustrialSpacing.IconMedium)
                )
            }
            Text(
                "KiraCard",
                style = IndustrialTypography.PanelTitle,
                color = AuroraColorTokens.TextTitle
            )
            Box(
                Modifier
                    .padding(horizontal = IndustrialSpacing.Item)
                    .width(IndustrialSpacing.Hairline)
                    .height(IndustrialSpacing.IconMedium)
                    .background(AuroraColorTokens.LinePrimary)
            )
            Text(
                sessionTitle ?: "新会话",
                style = IndustrialTypography.Caption,
                color = AuroraColorTokens.TextCaption,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            Spacer(Modifier.weight(1f))
            StatusDot(status = agentStatus.toDotStatus())
            Spacer(Modifier.width(IndustrialSpacing.Tight))
            Text(
                text = agentStatus.label(),
                style = IndustrialTypography.Tech,
                color = AuroraColorTokens.TextTech
            )
            Spacer(Modifier.width(IndustrialSpacing.Item))
            Text(
                modelName ?: "",
                style = IndustrialTypography.Tech,
                color = AuroraColorTokens.TextTech,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = IndustrialSpacing.StatusModelMaxWidth)
            )
            IconButton(onClick = onMonitorClick) {
                Icon(
                    Icons.Filled.Visibility,
                    contentDescription = "监控面板",
                    tint = AuroraColorTokens.TextCaption,
                    modifier = Modifier.size(IndustrialSpacing.IconMedium)
                )
            }
            IconButton(onClick = onSettingsClick) {
                Icon(
                    Icons.Filled.Settings,
                    contentDescription = "设置",
                    tint = AuroraColorTokens.TextCaption,
                    modifier = Modifier.size(IndustrialSpacing.IconMedium)
                )
            }
            IconButton(onClick = onNewSessionClick) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = "新建会话",
                    tint = AuroraColorTokens.TextCaption,
                    modifier = Modifier.size(IndustrialSpacing.IconMedium)
                )
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(IndustrialSpacing.Hairline)
                .background(AuroraColorTokens.LinePrimary)
        )
    }
}

fun AgentStatus.toDotStatus(): DotStatus = when (this) {
    AgentStatus.IDLE -> DotStatus.IDLE
    AgentStatus.STREAMING -> DotStatus.RUNNING
    AgentStatus.TOOL_RUNNING -> DotStatus.RUNNING
    AgentStatus.WAITING_USER -> DotStatus.WAITING
    AgentStatus.WAITING_PLAN -> DotStatus.WAITING
    AgentStatus.WAITING_DIFF -> DotStatus.WAITING
    AgentStatus.WAITING_BUDGET -> DotStatus.WAITING
    AgentStatus.ERROR -> DotStatus.ERROR
}

fun AgentStatus.label(): String = when (this) {
    AgentStatus.IDLE -> "空闲"
    AgentStatus.STREAMING -> "生成中"
    AgentStatus.TOOL_RUNNING -> "执行工具"
    AgentStatus.WAITING_USER -> "等待用户"
    AgentStatus.WAITING_PLAN -> "等待计划确认"
    AgentStatus.WAITING_DIFF -> "等待 Diff 决定"
    AgentStatus.WAITING_BUDGET -> "等待预算决策"
    AgentStatus.ERROR -> "失败"
}
