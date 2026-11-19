package com.kirakira.kiracode.ui.workspace.panels

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kirakira.kiracode.domain.agent.PlanBook
import com.kirakira.kiracode.domain.agent.PlanPart
import com.kirakira.kiracode.ui.theme.AuroraColorTokens
import com.kirakira.kiracode.ui.theme.AuroraTypography

private val AccentPurple = AuroraColorTokens.AuroraViolet
private val StatusGreen = AuroraColorTokens.SignalSuccess
private val StatusOrange = AuroraColorTokens.KiraGold
private val StatusRed = AuroraColorTokens.SignalError
private val StatusGray = AuroraColorTokens.SignalNeutral

/**
 * 任务进度面板（V3.1 批次2/任务3，右栏第一个 Tab）：
 * 计划书步骤清单 + 进度条。数据源 WorkspaceState.planBook。
 */
@Composable
fun TaskProgressPanel(
    planBook: PlanBook?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Assignment,
                contentDescription = "任务进度",
                tint = AccentPurple
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "任务进度",
                style = AuroraTypography.PanelTitle,
                fontWeight = FontWeight.Bold
            )
        }

        if (planBook == null || planBook.parts.isEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.HourglassEmpty,
                    contentDescription = null,
                    tint = AuroraColorTokens.TextCaption,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "暂无任务",
                    style = AuroraTypography.Caption,
                    color = AuroraColorTokens.TextCaption
                )
            }
        } else {
            val progress = planBook.doneCount.toFloat() / planBook.parts.size
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(8.dp)),
                color = AccentPurple
            )

            Text(
                "已完成 ${planBook.doneCount} / ${planBook.parts.size} 步",
                style = AuroraTypography.Caption,
                color = AuroraColorTokens.TextCaption
            )

            if (planBook.goal.isNotBlank()) {
                Text(
                    "目标：${planBook.goal}",
                    fontSize = 12.sp,
                    color = AccentPurple,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            HorizontalDivider()

            planBook.parts.forEachIndexed { index, part ->
                val stepNumber = index + 1
                val (icon, tint) = when (part.status) {
                    PlanPart.STATUS_DONE -> Icons.Default.CheckCircle to StatusGreen
                    PlanPart.STATUS_RUNNING -> Icons.Default.HourglassEmpty to StatusOrange
                    PlanPart.STATUS_FAILED -> Icons.Default.Cancel to StatusRed
                    else -> Icons.Default.Circle to StatusGray
                }
                Row(
                    verticalAlignment = Alignment.Top,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            "$stepNumber. ${part.title}",
                            style = AuroraTypography.Body,
                            fontWeight = if (part.status == PlanPart.STATUS_RUNNING) FontWeight.Bold else FontWeight.Normal,
                            color = if (part.status == PlanPart.STATUS_DONE) AuroraColorTokens.TextCaption else AuroraColorTokens.TextBody
                        )
                        if (part.detail.isNotBlank()) {
                            Text(
                                part.detail.take(80),
                                fontSize = 11.sp,
                                color = AuroraColorTokens.TextCaption,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}
