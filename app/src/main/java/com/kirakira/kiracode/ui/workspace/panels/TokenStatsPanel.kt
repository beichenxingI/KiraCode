package com.kirakira.kiracode.ui.workspace.panels

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kirakira.kiracode.domain.model.ContextInfo
import com.kirakira.kiracode.ui.theme.AuroraColorTokens
import com.kirakira.kiracode.ui.theme.AuroraShapes
import com.kirakira.kiracode.ui.theme.AuroraTypography
import java.util.Locale

private val AccentPurple = AuroraColorTokens.AuroraViolet

/**
 * Token 统计：2x2 紧凑仪表盘 + 底部运行信息卡。
 * 数据源 WorkspaceState.contextInfo（流式 usage 尾帧接入后为精确值）。
 */
@Composable
fun TokenStatsPanel(
    contextInfo: ContextInfo,
    modelName: String? = null,
    workModeName: String? = null,
    reasoningEffort: String? = null,
    modifier: Modifier = Modifier
) {
    val total = contextInfo.promptTokensTotal + contextInfo.completionTokensTotal
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.BarChart,
                contentDescription = "Token 统计",
                tint = AccentPurple,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "Token 统计",
                style = AuroraTypography.PanelTitle,
                color = AuroraColorTokens.TextTitle
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard(
                icon = Icons.Default.TrendingUp,
                label = "上下文占用",
                value = "${contextInfo.totalTokens}",
                unit = "tokens",
                color = AuroraColorTokens.SignalThinking,
                modifier = Modifier.weight(1f)
            )
            StatCard(
                icon = Icons.Default.ShowChart,
                label = "累计消耗",
                value = "$total",
                unit = "tokens",
                color = AccentPurple,
                modifier = Modifier.weight(1f)
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard(
                icon = Icons.Default.Sync,
                label = "生成轮次",
                value = "${contextInfo.requestCount}",
                unit = "轮",
                color = AuroraColorTokens.SignalSuccess,
                modifier = Modifier.weight(1f)
            )
            StatCard(
                icon = Icons.Default.Speed,
                label = "缓存命中率",
                value = "${(contextInfo.cacheHitRate * 100).toInt()}",
                unit = "%",
                color = AuroraColorTokens.KiraGold,
                modifier = Modifier.weight(1f)
            )
        }

        Surface(
            color = AuroraColorTokens.Base2,
            shape = AuroraShapes.Small,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AttachMoney,
                    contentDescription = null,
                    tint = AuroraColorTokens.SignalSuccess,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "预估成本 ¥${estimateCost(total)}",
                        style = AuroraTypography.Caption,
                        color = AuroraColorTokens.TextTitle
                    )
                    Text(
                        "MODEL ${modelName ?: "-"} · MODE ${workModeName ?: "-"} · EFFORT ${reasoningEffort ?: "自动"}",
                        style = AuroraTypography.Tech,
                        color = AuroraColorTokens.TextCaption,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun StatCard(
    icon: ImageVector,
    label: String,
    value: String,
    unit: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = AuroraShapes.Small,
        color = AuroraColorTokens.Base2
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    label,
                    style = AuroraTypography.Caption,
                    color = AuroraColorTokens.TextCaption,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    value,
                    style = AuroraTypography.PanelTitle,
                    color = AuroraColorTokens.TextTitle,
                    maxLines = 1
                )
                Spacer(Modifier.width(2.dp))
                Text(
                    unit,
                    style = AuroraTypography.Caption,
                    color = AuroraColorTokens.TextCaption,
                    maxLines = 1
                )
            }
        }
    }
}

/** DeepSeek 价格：1M tokens ≈ ¥1 */
private fun estimateCost(tokens: Long): String {
    val cost = tokens / 1_000_000.0
    return String.format(Locale.US, "%.4f", cost)
}
