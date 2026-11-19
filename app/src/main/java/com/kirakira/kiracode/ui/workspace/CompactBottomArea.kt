package com.kirakira.kiracode.ui.workspace

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kirakira.kiracode.domain.agent.PlanBook
import com.kirakira.kiracode.domain.agent.PlanPart
import com.kirakira.kiracode.ui.theme.AuroraColorTokens
import com.kirakira.kiracode.ui.theme.AuroraTypography
import kotlinx.coroutines.delay

/**
 * 手机底部三层：
 * 1. 计划进度（有计划时显示）
 * 2. Agent 状态（运行中显示，计时在本地计算）
 * 3. 输入区（复用现有 CommandDock，保留附件/命令/参考卡/重试）
 */
@Composable
fun CompactBottomArea(
    planBook: PlanBook?,
    agentStatus: AgentStatus,
    isSending: Boolean,
    contextUsagePercent: Int,
    inputDock: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    var thinkingSeconds by remember(isSending) { mutableStateOf(0) }
    LaunchedEffect(isSending) {
        if (isSending) {
            thinkingSeconds = 0
            while (true) {
                delay(1000)
                thinkingSeconds++
            }
        } else {
            thinkingSeconds = 0
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(AuroraColorTokens.Base0)
    ) {
        AnimatedVisibility(
            visible = planBook != null && planBook.parts.isNotEmpty(),
            enter = slideInVertically { -it } + fadeIn(tween(200)),
            exit = slideOutVertically { -it } + fadeOut(tween(150))
        ) {
            planBook?.let { plan ->
                val done = plan.parts.count { it.status == PlanPart.STATUS_DONE }
                val total = plan.parts.size
                val current = plan.parts.firstOrNull {
                    it.status == PlanPart.STATUS_RUNNING || it.status == PlanPart.STATUS_PENDING
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Icon(
                        Icons.Filled.List,
                        contentDescription = null,
                        tint = AuroraColorTokens.TextCaption,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "$done/$total",
                        style = AuroraTypography.Tech,
                        color = AuroraColorTokens.AuroraAccent
                    )
                    Text(
                        " · ${current?.title ?: "计划完成"}",
                        style = AuroraTypography.Tech,
                        color = AuroraColorTokens.TextCaption,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    LinearProgressIndicator(
                        progress = { if (total > 0) done.toFloat() / total else 0f },
                        modifier = Modifier
                            .width(60.dp)
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = AuroraColorTokens.AuroraAccent,
                        trackColor = AuroraColorTokens.Base2
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = isSending,
            enter = slideInVertically { -it } + fadeIn(tween(200)),
            exit = slideOutVertically { -it } + fadeOut(tween(150))
        ) {
            val infiniteTransition = rememberInfiniteTransition(label = "status_pulse")
            val pulseAlpha by infiniteTransition.animateFloat(
                initialValue = 0.6f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(900, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "pulse_alpha"
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                Icon(
                    imageVector = when (agentStatus) {
                        AgentStatus.STREAMING -> Icons.Filled.Psychology
                        AgentStatus.TOOL_RUNNING -> Icons.Filled.Build
                        AgentStatus.WAITING_USER -> Icons.Filled.Person
                        else -> Icons.Filled.HourglassEmpty
                    },
                    contentDescription = null,
                    tint = AuroraColorTokens.AuroraViolet.copy(alpha = pulseAlpha),
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = agentStatus.label(),
                    style = AuroraTypography.Tech,
                    color = AuroraColorTokens.TextCaption
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "$contextUsagePercent%",
                    style = AuroraTypography.Tech,
                    color = AuroraColorTokens.TextCaption
                )
                if (thinkingSeconds > 0) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "${thinkingSeconds}s",
                        style = AuroraTypography.Tech,
                        color = AuroraColorTokens.TextCaption
                    )
                }
            }
        }

        Box(
            Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .background(AuroraColorTokens.LinePrimary)
        )

        inputDock()
    }
}
