package com.kirakira.kiracode.ui.workspace

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kirakira.kiracode.ui.components.DotStatus
import com.kirakira.kiracode.ui.components.StatusDot
import com.kirakira.kiracode.ui.theme.AuroraColorTokens
import com.kirakira.kiracode.ui.theme.AuroraShapes
import com.kirakira.kiracode.ui.theme.AuroraTypography
import com.kirakira.kiracode.ui.theme.IndustrialSpacing
import com.kirakira.kiracode.ui.theme.IndustrialTypography
import com.kirakira.kiracode.ui.theme.bouncyClickable
import kotlinx.coroutines.launch

// ============ 事件节点配色（AuroraColorTokens） ============
private val NodeText = AuroraColorTokens.TextTitle
private val NodeMuted = AuroraColorTokens.TextCaption
private val NodeDim = AuroraColorTokens.TextTech
private val NodeBg = AuroraColorTokens.Base2
private val NodeGreen = AuroraColorTokens.SignalSuccess
private val NodeRed = AuroraColorTokens.SignalError
private val NodeYellow = AuroraColorTokens.KiraGold
private val NodePurple = AuroraColorTokens.SignalThinking

/**
 * Agent 事件回调：追问/计划/Diff/脑补的交互入口（挂起语义由 ViewModel 端完成）。
 */
data class AgentCardCallbacks(
    val activeCardId: String? = null,
    val onAnswerOption: (String) -> Unit = {},
    val onSkipQuestion: () -> Unit = {},
    val onPlanConfirm: () -> Unit = {},
    val onPlanModify: () -> Unit = {},
    val onDiffAccept: () -> Unit = {},
    val onDiffReject: () -> Unit = {},
    val onDiffContinue: () -> Unit = {},
    val onContinueFromBudget: (Int) -> Unit = {},
    val onSkipAndFinish: () -> Unit = {},
    val onAbandonTask: () -> Unit = {},
    val onGuessClick: (String, String) -> Unit = { _, _ -> }
)

/**
 * Agent 事件节点分发：工具/结果/追问/计划/进度/Diff/信息（失败/降级）。
 * 节点是时间轴上的操作记录，不是聊天气泡。
 */
@Composable
fun AgentCardItem(
    card: AgentCardUi,
    callbacks: AgentCardCallbacks,
    isCompact: Boolean = false
) {
    Surface(
        color = AuroraColorTokens.Base2,
        shape = AuroraShapes.Medium,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier.padding(
                horizontal = IndustrialSpacing.Item,
                vertical = IndustrialSpacing.Compact
            )
        ) {
            when (card) {
                is AgentCardUi.ToolCall -> ToolNode(card, callbacks)
                is AgentCardUi.Observation -> ResultNode(card)
                is AgentCardUi.Question -> AskNode(card, callbacks)
                is AgentCardUi.Plan -> PlanNode(card, callbacks)
                is AgentCardUi.Progress -> ProgressNode(card)
                is AgentCardUi.BudgetDecision -> BudgetDecisionNode(card, callbacks)
                is AgentCardUi.Diff -> DiffNode(card, callbacks, isCompact)
                is AgentCardUi.Info -> InfoNode(card)
                is AgentCardUi.Search -> SearchNode(card, isCompact)
                is AgentCardUi.WebpageFetch -> WebpageFetchNode(card, isCompact)
                is AgentCardUi.SubAgent -> SubAgentNode(card, isCompact)
                is AgentCardUi.Audit -> AuditNode(card)
            }
        }
    }
}

/** 工具调用节点：工具名 + 参数摘要 + 已发送状态 + 脑补字段入口 */
@Composable
private fun ToolNode(card: AgentCardUi.ToolCall, callbacks: AgentCardCallbacks) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = IndustrialSpacing.Tight),
        verticalArrangement = Arrangement.spacedBy(IndustrialSpacing.Tight)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusDot(status = DotStatus.IDLE)
            Spacer(Modifier.width(IndustrialSpacing.Tight))
            Icon(
                imageVector = Icons.Filled.Build,
                contentDescription = "工具调用",
                tint = NodeDim,
                modifier = Modifier.size(IndustrialSpacing.IconSmall)
            )
            Spacer(Modifier.width(IndustrialSpacing.Tight))
            Text(
                text = card.functionName,
                style = IndustrialTypography.Tech,
                color = NodeDim
            )
            if (card.desc.isNotBlank()) {
                Spacer(Modifier.width(IndustrialSpacing.Tight))
                Text(
                    text = card.desc,
                    style = IndustrialTypography.Caption,
                    color = NodeMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.width(IndustrialSpacing.Compact))
            Text(
                text = "已发送",
                style = IndustrialTypography.Marker,
                color = NodeDim
            )
        }
        if (card.isAiGuess) {
            Surface(
                color = NodePurple.copy(alpha = 0.12f),
                shape = AuroraShapes.Small,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        callbacks.onGuessClick(card.fieldKey, card.fieldLabel.ifBlank { card.desc })
                    }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(
                        horizontal = IndustrialSpacing.Item,
                        vertical = IndustrialSpacing.Tight
                    )
                ) {
                    Icon(
                        imageVector = Icons.Filled.Lightbulb,
                        contentDescription = "脑补标注",
                        tint = NodePurple,
                        modifier = Modifier.size(IndustrialSpacing.IconSmall)
                    )
                    Spacer(Modifier.width(IndustrialSpacing.Tight))
                    Text(
                        text = "此处是 AI 脑补 · 点按重写或给选项",
                        style = IndustrialTypography.Caption,
                        color = NodePurple
                    )
                }
            }
        }
    }
}

/** 工具结果节点：成功/失败状态 + 结果摘要（无耗时数据则不显示耗时） */
@Composable
private fun ResultNode(card: AgentCardUi.Observation) {
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = IndustrialSpacing.Item, top = IndustrialSpacing.Tight, bottom = IndustrialSpacing.Tight)
    ) {
        StatusDot(status = if (card.isError) DotStatus.ERROR else DotStatus.SUCCESS)
        Spacer(Modifier.width(IndustrialSpacing.Tight))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (card.isError) "失败" else "完成",
                style = IndustrialTypography.Marker,
                color = if (card.isError) NodeRed else NodeGreen
            )
            if (card.content.isNotBlank()) {
                Text(
                    text = card.content,
                    style = IndustrialTypography.Tech,
                    color = NodeMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = IndustrialSpacing.Tight)
                )
            }
        }
    }
}

/** 审计节点：摘要 + 错误/警告/提示计数 + 可展开的问题列表 */
@Composable
private fun AuditNode(card: AgentCardUi.Audit) {
    var expanded by rememberSaveable(card.id) { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = IndustrialSpacing.Tight)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Info,
                contentDescription = "审计",
                tint = AuroraColorTokens.AuroraAccent,
                modifier = Modifier.size(IndustrialSpacing.IconSmall)
            )
            Spacer(Modifier.width(IndustrialSpacing.Tight))
            Text(
                text = "审计：「${card.cardName}」",
                style = AuroraTypography.PanelTitle,
                color = AuroraColorTokens.TextTitle
            )
            Spacer(Modifier.weight(1f))
            if (card.errorCount > 0) CountChip(card.errorCount, AuroraColorTokens.SignalError)
            if (card.warningCount > 0) CountChip(card.warningCount, AuroraColorTokens.SignalWarning)
            if (card.infoCount > 0) CountChip(card.infoCount, AuroraColorTokens.TextCaption)
        }

        Spacer(Modifier.height(IndustrialSpacing.Tight))
        Text(
            text = "${card.entryCount} 条世界书 · 约 ${card.estimatedTokens} tokens",
            style = AuroraTypography.Caption,
            color = AuroraColorTokens.TextCaption
        )

        if (card.issues.isNotEmpty()) {
            Spacer(Modifier.height(IndustrialSpacing.Tight))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (expanded) "收起详情" else "展开详情 (${card.issues.size} 项)",
                    style = AuroraTypography.Caption,
                    color = AuroraColorTokens.AuroraAccent
                )
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
                    tint = AuroraColorTokens.AuroraAccent,
                    modifier = Modifier.size(IndustrialSpacing.IconSmall)
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(IndustrialSpacing.Tight)) {
                    val errors = card.issues.filter { it.severity == "ERROR" }
                    if (errors.isNotEmpty()) {
                        AuditIssueGroup("错误", AuroraColorTokens.SignalError, errors)
                    }
                    val warnings = card.issues.filter { it.severity == "WARNING" }
                    if (warnings.isNotEmpty()) {
                        AuditIssueGroup("警告", AuroraColorTokens.SignalWarning, warnings)
                    }
                    val infos = card.issues.filter { it.severity == "INFO" }
                    if (infos.isNotEmpty()) {
                        AuditIssueGroup("提示", AuroraColorTokens.TextCaption, infos)
                    }
                }
            }
        }
    }
}

@Composable
private fun CountChip(count: Int, color: Color) {
    Surface(
        shape = RoundedCornerShape(50),
        color = color.copy(alpha = 0.12f),
        modifier = Modifier.padding(start = 4.dp)
    ) {
        Text(
            text = "$count",
            style = AuroraTypography.Caption,
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun AuditIssueGroup(
    label: String,
    color: Color,
    issues: List<SerializableAuditIssue>
) {
    Column {
        Text(
            text = label,
            style = AuroraTypography.Caption,
            color = color,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
        )
        issues.forEach { issue ->
            Surface(
                shape = AuroraShapes.Small,
                color = color.copy(alpha = 0.06f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp)
            ) {
                Column(modifier = Modifier.padding(IndustrialSpacing.Tight)) {
                    val location = when {
                        issue.entryUid != null -> {
                            val pair = if (issue.pairUid != null) " ↔ uid=${issue.pairUid}" else ""
                            "uid=${issue.entryUid}$pair"
                        }
                        !issue.fieldName.isNullOrBlank() -> issue.fieldName.orEmpty()
                        else -> "卡级"
                    }
                    Text(
                        text = "[$location] ${issue.issueCode}",
                        style = AuroraTypography.Caption,
                        color = color,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = issue.description,
                        style = AuroraTypography.Caption,
                        color = AuroraColorTokens.TextBody
                    )
                    Text(
                        text = "→ ${issue.suggestion}",
                        style = AuroraTypography.Caption,
                        color = AuroraColorTokens.TextCaption
                    )
                }
            }
        }
    }
}

/** 追问面板：高优先级（黄色侧线）+ 选项按钮 + 跳过 */
@Composable
private fun AskNode(card: AgentCardUi.Question, callbacks: AgentCardCallbacks) {
    val active = card.id == callbacks.activeCardId
    Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Box(
            Modifier
                .width(IndustrialSpacing.SelectionBarWidth)
                .fillMaxHeight()
                .background(if (active) NodeYellow else NodeYellow.copy(alpha = 0.35f))
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = IndustrialSpacing.Item, end = IndustrialSpacing.Tight, top = IndustrialSpacing.Tight, bottom = IndustrialSpacing.Tight),
            verticalArrangement = Arrangement.spacedBy(IndustrialSpacing.Tight)
        ) {
            Text(
                text = "需要你的决定",
                style = IndustrialTypography.SectionLabel,
                color = NodeYellow
            )
            Text(
                text = card.question,
                style = IndustrialTypography.Body,
                color = NodeText
            )
            when {
                card.answered != null -> Text(
                    text = "已回答：${card.answered}",
                    style = IndustrialTypography.Marker,
                    color = NodeGreen
                )
                card.skipped -> Text(
                    text = "已跳过",
                    style = IndustrialTypography.Marker,
                    color = NodeDim
                )
                active -> {
                    card.options.forEach { option ->
                        Surface(
                            color = NodeBg,
                            shape = AuroraShapes.Small,
                            modifier = Modifier
                                .fillMaxWidth()
                                .bouncyClickable { callbacks.onAnswerOption(option) }
                        ) {
                            Text(
                                text = option,
                                style = IndustrialTypography.Caption,
                                color = NodeText,
                                modifier = Modifier.padding(
                                    horizontal = IndustrialSpacing.Item,
                                    vertical = IndustrialSpacing.Tight
                                )
                            )
                        }
                    }
                    Row {
                        Text(
                            text = if (card.about.isBlank()) "跳过" else "跳过（${card.about}）",
                            style = IndustrialTypography.Tech,
                            color = NodeDim,
                            modifier = Modifier
                                .bouncyClickable(onClick = callbacks.onSkipQuestion)
                                .padding(vertical = IndustrialSpacing.Tight)
                        )
                    }
                }
            }
        }
    }
}

/** 计划节点：步骤编号 + 状态 + 确认/修改 */
@Composable
private fun PlanNode(card: AgentCardUi.Plan, callbacks: AgentCardCallbacks) {
    val active = card.id == callbacks.activeCardId && card.status == AgentCardUi.Plan.STATUS_PENDING
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = IndustrialSpacing.Tight),
        verticalArrangement = Arrangement.spacedBy(IndustrialSpacing.Tight)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "当前计划 · 共 ${card.steps.size} 步",
                style = IndustrialTypography.SectionLabel,
                color = NodeYellow
            )
            if (card.status == AgentCardUi.Plan.STATUS_CONFIRMED) {
                Spacer(Modifier.width(IndustrialSpacing.Item))
                Text(
                    text = "已确认",
                    style = IndustrialTypography.Marker,
                    color = NodeGreen
                )
            }
        }
        card.steps.forEachIndexed { index, step ->
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    text = "%02d".format(index + 1),
                    style = IndustrialTypography.Tech,
                    color = NodeDim
                )
                Spacer(Modifier.width(IndustrialSpacing.Tight))
                Column {
                    Text(
                        text = step.title,
                        style = IndustrialTypography.Caption,
                        color = NodeText
                    )
                    if (step.detail.isNotBlank()) {
                        Text(
                            text = step.detail,
                            style = IndustrialTypography.Tech,
                            color = NodeDim
                        )
                    }
                }
            }
        }
        if (active) {
            Row(horizontalArrangement = Arrangement.spacedBy(IndustrialSpacing.Item)) {
                IndustrialActionButton("确认计划", filled = true, onClick = callbacks.onPlanConfirm)
                IndustrialActionButton("修改计划", filled = false, onClick = callbacks.onPlanModify)
            }
        }
    }
}

/** 预算决策节点：已完成/未完成摘要 + 继续/保存/放弃 */
@Composable
private fun BudgetDecisionNode(
    card: AgentCardUi.BudgetDecision,
    callbacks: AgentCardCallbacks
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = IndustrialSpacing.Tight),
        verticalArrangement = Arrangement.spacedBy(IndustrialSpacing.Tight)
    ) {
        Text(
            text = "执行需要你决定（已用 ${card.iterationsUsed} 轮）",
            style = IndustrialTypography.SectionLabel,
            color = NodeYellow
        )
        Text(
            text = "已完成：${card.completedSummary}",
            style = IndustrialTypography.Caption,
            color = NodeText
        )
        if (card.pendingSteps.isNotEmpty()) {
            Text(
                text = "未完成：${card.pendingSteps.joinToString("、")}",
                style = IndustrialTypography.Caption,
                color = NodeMuted,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(IndustrialSpacing.Item)) {
            IndustrialActionButton(
                label = "继续 +5 轮",
                filled = true,
                onClick = { callbacks.onContinueFromBudget(5) }
            )
            IndustrialActionButton(
                label = "保存当前",
                filled = false,
                onClick = callbacks.onSkipAndFinish
            )
            IndustrialActionButton(
                label = "放弃",
                filled = false,
                danger = true,
                onClick = callbacks.onAbandonTask
            )
        }
    }
}

/** 进度节点：已完成 N/M 步 + 黄色进度段 */
@Composable
private fun ProgressNode(card: AgentCardUi.Progress) {
    val fraction = if (card.total > 0) (card.done.toFloat() / card.total).coerceIn(0f, 1f) else 0f
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = IndustrialSpacing.Tight),
        verticalArrangement = Arrangement.spacedBy(IndustrialSpacing.Tight)
    ) {
        Text(
            text = "已完成 ${card.done}/${card.total} 步",
            style = IndustrialTypography.Tech,
            color = NodeMuted
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(IndustrialSpacing.SelectionBarWidth)
                    .background(AuroraColorTokens.LineSecondary)
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fraction)
                    .height(IndustrialSpacing.SelectionBarWidth)
                    .background(NodeYellow)
            )
        }
        if (card.title.isNotBlank() || card.summary.isNotBlank()) {
            Text(
                text = listOf(card.title, card.summary).filter { it.isNotBlank() }.joinToString(" · "),
                style = IndustrialTypography.Caption,
                color = NodeMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Diff 节点：手机上下折叠；平板左右并排 + 接受/拒绝/继续修改 */
@Composable
private fun DiffNode(
    card: AgentCardUi.Diff,
    callbacks: AgentCardCallbacks,
    isCompact: Boolean
) {
    var showOldValue by rememberSaveable(card.id, isCompact) { mutableStateOf(!isCompact) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = IndustrialSpacing.Tight),
        verticalArrangement = Arrangement.spacedBy(IndustrialSpacing.Tight)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (isCompact) Modifier.clickable { showOldValue = !showOldValue }
                    else Modifier
                )
                .padding(vertical = IndustrialSpacing.Tight),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "字段：${card.fieldLabel}",
                style = IndustrialTypography.SectionLabel,
                color = NodeMuted,
                modifier = Modifier.weight(1f)
            )
            if (isCompact) {
                Icon(
                    imageVector = if (showOldValue) Icons.Filled.ExpandLess
                    else Icons.Filled.ExpandMore,
                    contentDescription = if (showOldValue) "收起改前内容" else "展开改前内容",
                    tint = NodeDim,
                    modifier = Modifier.size(IndustrialSpacing.IconSmall)
                )
            }
        }

        if (isCompact) {
            AnimatedVisibility(visible = showOldValue) {
                if (card.oldValue.isNotBlank()) {
                    DiffValueBlock(
                        label = "改前",
                        value = card.oldValue,
                        color = NodeRed,
                        strikethrough = true
                    )
                } else {
                    Text(
                        text = "原状态：不存在",
                        style = IndustrialTypography.Tech,
                        color = NodeDim
                    )
                }
            }
            DiffValueBlock(
                label = "改后",
                value = card.newValue,
                color = NodeGreen,
                strikethrough = false
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(IndustrialSpacing.Item)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    if (card.oldValue.isNotBlank()) {
                        DiffValueBlock(
                            label = "改前",
                            value = card.oldValue,
                            color = NodeRed,
                            strikethrough = true
                        )
                    } else {
                        Text(
                            text = "原状态：不存在",
                            style = IndustrialTypography.Tech,
                            color = NodeDim
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    DiffValueBlock(
                        label = "改后",
                        value = card.newValue,
                        color = NodeGreen,
                        strikethrough = false
                    )
                }
            }
        }

        when (card.decision) {
            null -> if (card.id == callbacks.activeCardId) {
                Row(horizontalArrangement = Arrangement.spacedBy(IndustrialSpacing.Item)) {
                    IndustrialActionButton("接受", filled = true, onClick = callbacks.onDiffAccept)
                    IndustrialActionButton("拒绝", filled = false, danger = true, onClick = callbacks.onDiffReject)
                    IndustrialActionButton("继续修改", filled = false, onClick = callbacks.onDiffContinue)
                }
            }
            AgentCardUi.Diff.ACCEPTED -> Text(
                text = "已接受，修改已应用",
                style = IndustrialTypography.Marker,
                color = NodeGreen
            )
            AgentCardUi.Diff.REJECTED -> Text(
                text = "已拒绝",
                style = IndustrialTypography.Marker,
                color = NodeRed
            )
            AgentCardUi.Diff.CONTINUED -> Text(
                text = "继续修改中",
                style = IndustrialTypography.Marker,
                color = NodeYellow
            )
        }
    }
}

/** Diff 值块：侧线 + 8% 色底（红/绿只用于变化本身） */
@Composable
private fun DiffValueBlock(
    label: String,
    value: String,
    color: androidx.compose.ui.graphics.Color,
    strikethrough: Boolean
) {
    Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Box(
            Modifier
                .width(IndustrialSpacing.SelectionBarWidth)
                .fillMaxHeight()
                .background(color.copy(alpha = 0.6f))
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .background(color.copy(alpha = 0.08f))
                .padding(
                    horizontal = IndustrialSpacing.Item,
                    vertical = IndustrialSpacing.Tight
                )
        ) {
            Text(
                text = label,
                style = IndustrialTypography.Marker,
                color = color
            )
            Text(
                text = value,
                style = IndustrialTypography.Tech,
                color = NodeMuted,
                textDecoration = if (strikethrough) TextDecoration.LineThrough else null,
                modifier = Modifier.padding(top = IndustrialSpacing.Tight)
            )
        }
    }
}

/** 信息节点：系统提示 / 流式降级 / 执行失败（持久事件卡） */
@Composable
private fun InfoNode(card: AgentCardUi.Info) {
    when (card.kind) {
        AgentCardUi.Info.Kind.INFO -> Row(
            verticalAlignment = Alignment.Top,
            modifier = Modifier.padding(vertical = IndustrialSpacing.Tight)
        ) {
            Icon(
                imageVector = Icons.Filled.Info,
                contentDescription = "信息",
                tint = NodeDim,
                modifier = Modifier.size(IndustrialSpacing.IconSmall)
            )
            Spacer(Modifier.width(IndustrialSpacing.Tight))
            Text(
                text = card.text,
                style = IndustrialTypography.Tech.copy(fontStyle = FontStyle.Italic),
                color = NodeDim
            )
        }
        AgentCardUi.Info.Kind.DEGRADED -> Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            Box(
                Modifier
                    .width(IndustrialSpacing.SelectionBarWidth)
                    .fillMaxHeight()
                    .background(NodeYellow.copy(alpha = 0.7f))
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = IndustrialSpacing.Item, top = IndustrialSpacing.Tight, bottom = IndustrialSpacing.Tight),
                verticalArrangement = Arrangement.spacedBy(IndustrialSpacing.Tight)
            ) {
                Text(
                    text = "已从流式切换为整段返回",
                    style = IndustrialTypography.SectionLabel,
                    color = NodeYellow
                )
                Text(
                    text = card.text,
                    style = IndustrialTypography.Caption,
                    color = NodeMuted
                )
            }
        }
        AgentCardUi.Info.Kind.FAIL -> {
            val clipboard = LocalClipboardManager.current
            Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                Box(
                    Modifier
                        .width(IndustrialSpacing.SelectionBarWidth)
                        .fillMaxHeight()
                        .background(NodeRed)
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = IndustrialSpacing.Item, top = IndustrialSpacing.Tight, bottom = IndustrialSpacing.Tight),
                    verticalArrangement = Arrangement.spacedBy(IndustrialSpacing.Tight)
                ) {
                    Text(
                        text = "执行失败",
                        style = IndustrialTypography.SectionLabel,
                        color = NodeRed
                    )
                    Text(
                        text = card.text,
                        style = IndustrialTypography.Caption,
                        color = NodeMuted
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val copyScope = rememberCoroutineScope()
                        Text(
                            text = "复制诊断",
                            style = IndustrialTypography.Tech,
                            color = NodeDim,
                            modifier = Modifier
                                .clickable {
                                    // 后台构建全文，避免主线程拼大字符串卡顿
                                    copyScope.launch(kotlinx.coroutines.Dispatchers.Default) {
                                        val text = com.kirakira.kiracode.util.AppLogger.getLogsAsText()
                                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                            clipboard.setText(AnnotatedString(text))
                                        }
                                    }
                                }
                                .padding(vertical = IndustrialSpacing.Tight)
                        )
                    }
                }
            }
        }
    }
}

/** 搜索结果节点：紫侧线 + 查询词 + 结果数；手机默认折叠预览 */
@Composable
private fun SearchNode(card: AgentCardUi.Search, isCompact: Boolean) {
    var showDialog by remember { mutableStateOf(false) }
    var expanded by rememberSaveable(card.id, isCompact) { mutableStateOf(!isCompact) }
    Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Box(
            Modifier
                .width(IndustrialSpacing.SelectionBarWidth)
                .fillMaxHeight()
                .background(NodePurple)
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = IndustrialSpacing.Item, top = IndustrialSpacing.Tight, bottom = IndustrialSpacing.Tight),
            verticalArrangement = Arrangement.spacedBy(IndustrialSpacing.Tight)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
            ) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = null,
                    tint = NodePurple,
                    modifier = Modifier.size(IndustrialSpacing.IconSmall)
                )
                Spacer(Modifier.width(IndustrialSpacing.Tight))
                Text(
                    text = buildString {
                        append("联网搜索")
                        if (card.provider.isNotBlank()) append(" · ").append(card.provider)
                        append(" · ").append(card.resultCount).append(" 条")
                    },
                    style = IndustrialTypography.SectionLabel,
                    color = NodePurple,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "查看详情 →",
                    style = IndustrialTypography.Tech,
                    color = NodeDim,
                    modifier = Modifier.bouncyClickable { showDialog = true }
                )
                Spacer(Modifier.width(IndustrialSpacing.Tight))
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "收起搜索结果" else "展开搜索结果",
                    tint = NodeDim,
                    modifier = Modifier.size(IndustrialSpacing.IconSmall)
                )
            }
            if (card.query.isNotBlank()) {
                Text(
                    text = "查询：${card.query}",
                    style = IndustrialTypography.Caption,
                    color = NodeText
                )
            }
            AnimatedVisibility(visible = expanded) {
                val preview = card.results.take(2).mapNotNull { r ->
                    r.title.takeIf { it.isNotBlank() }
                }
                if (preview.isNotEmpty()) {
                    Text(
                        text = preview.joinToString("\n") { "· $it" },
                        style = IndustrialTypography.Tech,
                        color = NodeMuted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
    if (showDialog) {
        com.kirakira.kiracode.ui.overlays.SearchResultDialog(
            query = card.query,
            results = card.results.map {
                com.kirakira.kiracode.ui.overlays.SearchResultItem(it.title, it.url, it.snippet)
            },
            onDismiss = { showDialog = false }
        )
    }
}

/** 子代理节点：紫侧线 + 任务 + 状态 + 结果（点击展开/收起；不展示思维链） */
@Composable
private fun SubAgentNode(card: AgentCardUi.SubAgent, isCompact: Boolean) {
    var expanded by rememberSaveable(card.id, isCompact) { mutableStateOf(!isCompact) }
    val accent = when (card.status) {
        AgentCardUi.SubAgent.STATUS_DONE -> NodeGreen
        AgentCardUi.SubAgent.STATUS_FAILED -> NodeRed
        else -> NodePurple
    }
    val statusLabel = when (card.status) {
        AgentCardUi.SubAgent.STATUS_DONE -> "已完成"
        AgentCardUi.SubAgent.STATUS_FAILED -> "失败"
        else -> "运行中"
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = IndustrialSpacing.Tight),
        verticalArrangement = Arrangement.spacedBy(IndustrialSpacing.Tight)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusDot(
                status = when (card.status) {
                    AgentCardUi.SubAgent.STATUS_DONE -> DotStatus.SUCCESS
                    AgentCardUi.SubAgent.STATUS_FAILED -> DotStatus.ERROR
                    else -> DotStatus.RUNNING
                }
            )
            Spacer(Modifier.width(IndustrialSpacing.Tight))
            Text(
                text = "子代理 · $statusLabel",
                style = IndustrialTypography.SectionLabel,
                color = accent,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.Filled.Psychology,
                contentDescription = "子代理",
                tint = accent,
                modifier = Modifier.size(IndustrialSpacing.IconSmall)
            )
        }
        Text(
            text = "任务：${card.task}",
            style = IndustrialTypography.Tech,
            color = NodeText,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        when (card.status) {
            AgentCardUi.SubAgent.STATUS_DONE -> {
                if (card.outputPreview.isNotBlank()) {
                    Text(
                        text = card.outputPreview,
                        style = IndustrialTypography.Caption,
                        color = NodeMuted,
                        maxLines = if (expanded) Int.MAX_VALUE else 2,
                        overflow = if (expanded) TextOverflow.Clip else TextOverflow.Ellipsis
                    )
                }
                if (card.output.length > card.outputPreview.length) {
                    Text(
                        text = if (expanded) "收起结果" else "展开完整结果",
                        style = IndustrialTypography.Tech,
                        color = NodePurple,
                        modifier = Modifier
                            .bouncyClickable { expanded = !expanded }
                            .padding(vertical = IndustrialSpacing.Tight)
                    )
                }
                if (expanded && card.output.isNotBlank()) {
                    Text(
                        text = card.output,
                        style = IndustrialTypography.Caption,
                        color = NodeText
                    )
                }
            }
            AgentCardUi.SubAgent.STATUS_FAILED -> Text(
                text = "失败原因：${card.errorMessage.ifBlank { "未知错误" }}",
                style = IndustrialTypography.Caption,
                color = NodeRed,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            else -> Text(
                text = "子代理正在独立上下文中执行任务…",
                style = IndustrialTypography.Caption,
                color = NodeMuted
            )
        }
    }
}

/** 网页抓取节点：绿侧线 + 域名/大小；手机默认折叠正文预览 */
@Composable
private fun WebpageFetchNode(card: AgentCardUi.WebpageFetch, isCompact: Boolean) {
    var expanded by rememberSaveable(card.id, isCompact) { mutableStateOf(!isCompact) }
    val domain = remember(card.url) {
        runCatching { Uri.parse(card.url).host }.getOrNull() ?: card.url
    }
    val sizeText = if (card.contentLength >= 1000) {
        "${card.contentLength / 1000}K 字"
    } else {
        "${card.contentLength} 字"
    }
    Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Box(
            Modifier
                .width(IndustrialSpacing.SelectionBarWidth)
                .fillMaxHeight()
                .background(NodeGreen)
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = IndustrialSpacing.Item, top = IndustrialSpacing.Tight, bottom = IndustrialSpacing.Tight),
            verticalArrangement = Arrangement.spacedBy(IndustrialSpacing.Tight)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
            ) {
                Icon(
                    imageVector = Icons.Filled.Language,
                    contentDescription = null,
                    tint = NodeGreen,
                    modifier = Modifier.size(IndustrialSpacing.IconSmall)
                )
                Spacer(Modifier.width(IndustrialSpacing.Tight))
                Text(
                    text = "$domain · $sizeText",
                    style = IndustrialTypography.SectionLabel,
                    color = NodeGreen,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "收起网页内容" else "展开网页内容",
                    tint = NodeDim,
                    modifier = Modifier.size(IndustrialSpacing.IconSmall)
                )
            }
            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(IndustrialSpacing.Tight)) {
                    if (card.url.isNotBlank()) {
                        Text(
                            text = card.url,
                            style = IndustrialTypography.Tech,
                            color = NodeMuted,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (card.preview.isNotBlank()) {
                        Text(
                            text = card.preview,
                            style = IndustrialTypography.Caption,
                            color = NodeText,
                            maxLines = 8,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

/** 工业操作按钮：直角 2dp；filled=黄底黑字（主操作），否则描边 */
@Composable
private fun IndustrialActionButton(
    label: String,
    filled: Boolean,
    onClick: () -> Unit,
    danger: Boolean = false
) {
    Surface(
        color = if (filled) AuroraColorTokens.AuroraAccent else AuroraColorTokens.Base1,
        shape = AuroraShapes.Small,
        modifier = Modifier.bouncyClickable(onClick = onClick)
    ) {
        Text(
            text = label,
            style = IndustrialTypography.Caption,
            color = when {
                filled -> AuroraColorTokens.Base0
                danger -> NodeRed
                else -> NodeMuted
            },
            modifier = Modifier.padding(
                horizontal = IndustrialSpacing.Item,
                vertical = IndustrialSpacing.Tight
            )
        )
    }
}

