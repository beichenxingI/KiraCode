package com.kirakira.kiracode.ui.workspace

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.kirakira.kiracode.domain.agent.AgentTask
import com.kirakira.kiracode.domain.agent.PlanBook
import com.kirakira.kiracode.domain.model.CharacterCard
import com.kirakira.kiracode.domain.model.ContextInfo
import com.kirakira.kiracode.domain.model.WorkMode
import com.kirakira.kiracode.ui.common.resolveCoverCoilModel
import com.kirakira.kiracode.ui.components.DotStatus
import com.kirakira.kiracode.ui.components.StatusDot
import com.kirakira.kiracode.ui.workspace.panels.TaskProgressPanel
import com.kirakira.kiracode.ui.workspace.panels.TokenStatsPanel
import com.kirakira.kiracode.ui.theme.AuroraColorTokens
import com.kirakira.kiracode.ui.theme.AuroraShapes
import com.kirakira.kiracode.ui.theme.AuroraTypography
import com.kirakira.kiracode.ui.theme.bouncyClickable

/**
 * 右侧「角色卡 / 监控」面板：
 * - 顶部双胶囊分段器 + 独立关闭按钮；
 * - 角色卡视图：Hero 封面色卡 + 4 个子 Tab；
 * - 监控视图：紧凑 2x2 仪表盘 + 任务/状态/卡摘要。
 */
@Composable
fun MonitorPanel(
    planBook: PlanBook?,
    tasks: List<AgentTask>,
    contextInfo: ContextInfo,
    agentStatus: AgentStatus,
    modelName: String?,
    workMode: WorkMode,
    reasoningEffort: String?,
    card: CharacterCard?,
    onCardChange: (CharacterCard) -> Unit,
    onPickCover: ((android.net.Uri) -> Unit)? = null,
    onClearCover: (() -> Unit)? = null,
    onClose: () -> Unit,
    onSaveToFinished: (() -> Unit)? = null,
    showCloseButton: Boolean = true,
    modifier: Modifier = Modifier
) {
    var showPreview by rememberSaveable(card != null) { mutableStateOf(card != null) }

    Surface(color = AuroraColorTokens.Base0, modifier = modifier.fillMaxHeight()) {
        Row(modifier = Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(AuroraColorTokens.LinePrimary)
            )
            Column(modifier = Modifier.weight(1f)) {
                MonitorHeader(
                    showPreview = showPreview,
                    onShowPreview = { showPreview = it },
                    bookCount = card?.characterBook?.entries?.size ?: 0,
                    agentStatus = agentStatus,
                    showCloseButton = showCloseButton,
                    onClose = onClose
                )

                if (showPreview) {
                    CardPreviewSection(
                        card = card,
                        onCardChange = onCardChange,
                        onSaveToFinished = onSaveToFinished,
                        onPickCover = onPickCover,
                        onClearCover = onClearCover
                    )
                } else {
                    MonitorContent(
                        planBook = planBook,
                        tasks = tasks,
                        contextInfo = contextInfo,
                        agentStatus = agentStatus,
                        modelName = modelName,
                        workMode = workMode,
                        reasoningEffort = reasoningEffort,
                        card = card,
                        onOpenPreview = { showPreview = true }
                    )
                }
            }
        }
    }
}

@Composable
private fun MonitorHeader(
    showPreview: Boolean,
    onShowPreview: (Boolean) -> Unit,
    bookCount: Int,
    agentStatus: AgentStatus,
    showCloseButton: Boolean,
    onClose: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .height(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(AuroraColorTokens.Base0)
                .border(BorderStroke(1.dp, AuroraColorTokens.LinePrimary), RoundedCornerShape(12.dp))
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            SegmentTab(
                label = "角色卡",
                selected = showPreview,
                badge = bookCount.takeIf { it > 0 }?.toString(),
                running = false,
                modifier = Modifier.weight(1f),
                onClick = { onShowPreview(true) }
            )
            SegmentTab(
                label = "监控",
                selected = !showPreview,
                badge = null,
                running = agentStatus.isBusy,
                modifier = Modifier.weight(1f),
                onClick = { onShowPreview(false) }
            )
        }

        if (showCloseButton) {
            Spacer(Modifier.width(12.dp))
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(AuroraColorTokens.Base2)
                    .border(BorderStroke(1.dp, AuroraColorTokens.LinePrimary), RoundedCornerShape(10.dp))
                    .bouncyClickable(onClick = onClose),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "收起面板",
                    tint = AuroraColorTokens.TextCaption,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun SegmentTab(
    label: String,
    selected: Boolean,
    badge: String?,
    running: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val accent = AuroraColorTokens.AuroraAccent
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) accent.copy(alpha = 0.14f) else Color.Transparent)
            .border(
                BorderStroke(
                    1.dp,
                    if (selected) accent.copy(alpha = 0.5f) else Color.Transparent
                ),
                RoundedCornerShape(10.dp)
            )
            .bouncyClickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                style = AuroraTypography.Caption,
                color = if (selected) accent else AuroraColorTokens.TextCaption,
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                maxLines = 1
            )
            if (badge != null) {
                Spacer(Modifier.width(4.dp))
                Text(
                    badge,
                    style = AuroraTypography.Tech,
                    color = if (selected) accent else AuroraColorTokens.TextCaption,
                    maxLines = 1
                )
            }
            if (running) {
                Spacer(Modifier.width(6.dp))
                PulsingDot()
            }
        }
    }
}

@Composable
private fun PulsingDot() {
    val transition = rememberInfiniteTransition(label = "monitor_pulse")
    val alpha by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "monitor_pulse_alpha"
    )
    Box(
        Modifier
            .size(6.dp)
            .clip(CircleShape)
            .background(AuroraColorTokens.AuroraAccent.copy(alpha = alpha))
    )
}

@Composable
private fun CardPreviewSection(
    card: CharacterCard?,
    onCardChange: (CharacterCard) -> Unit,
    onSaveToFinished: (() -> Unit)?,
    onPickCover: ((Uri) -> Unit)?,
    onClearCover: (() -> Unit)?
) {
    if (card == null) {
        EmptyHint("未选择角色卡：先创建或导入一张卡")
        return
    }

    var tab by rememberSaveable(card.uuid) { mutableIntStateOf(0) }
    Column(modifier = Modifier.fillMaxSize()) {
        CardHero(
            card = card,
            onSaveToFinished = onSaveToFinished,
            onPickCover = onPickCover,
            onClearCover = onClearCover
        )
        CardSubTabs(selected = tab, onSelect = { tab = it })
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            when (tab) {
                0 -> BasicInfoTab(card, onCardChange)
                1 -> WorldBookTab(card, onCardChange)
                2 -> RegexTab(card, onCardChange)
                3 -> CardPreviewTab(card)
            }
        }
    }
}

@Composable
private fun CardHero(
    card: CharacterCard,
    onSaveToFinished: (() -> Unit)?,
    onPickCover: ((Uri) -> Unit)?,
    onClearCover: (() -> Unit)?
) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { onPickCover?.invoke(it) }
    }
    val spec = card.spec.replace("chara_card_", "V").ifBlank { "V2" }
    val bookCount = card.characterBook?.entries?.size ?: 0
    val regexCount = card.regexScripts.size

    Surface(
        color = AuroraColorTokens.Base2,
        shape = AuroraShapes.Medium,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(width = 68.dp, height = 90.dp)
                    .clip(AuroraShapes.Small)
                    .background(AuroraColorTokens.Base1)
                    .border(
                        BorderStroke(
                            1.dp,
                            if (card.avatar.isNullOrBlank()) AuroraColorTokens.LinePrimary
                            else Color.Transparent
                        ),
                        AuroraShapes.Small
                    )
                    .clickable(enabled = onPickCover != null) {
                        picker.launch("image/*")
                    },
                contentAlignment = Alignment.Center
            ) {
                if (!card.avatar.isNullOrBlank()) {
                    AsyncImage(
                        model = resolveCoverCoilModel(card.avatar),
                        contentDescription = "角色卡封面",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(4.dp)
                            .size(20.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.Black.copy(alpha = 0.55f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.PhotoCamera,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Filled.PhotoCamera,
                            contentDescription = null,
                            tint = AuroraColorTokens.TextCaption,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "上传封面",
                            style = AuroraTypography.Caption,
                            color = AuroraColorTokens.TextCaption
                        )
                    }
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    card.name.ifBlank { "未命名角色" },
                    style = AuroraTypography.PanelTitle,
                    color = AuroraColorTokens.TextTitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "$spec · 世界书 $bookCount 条 · 正则 $regexCount 条",
                    style = AuroraTypography.Tech,
                    color = AuroraColorTokens.TextCaption,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (onSaveToFinished != null) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = AuroraColorTokens.AuroraAccent.copy(alpha = 0.10f),
                            border = BorderStroke(1.dp, AuroraColorTokens.AuroraAccent.copy(alpha = 0.35f)),
                            modifier = Modifier.clickable(onClick = onSaveToFinished)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    Icons.Filled.SaveAlt,
                                    contentDescription = null,
                                    tint = AuroraColorTokens.AuroraAccent,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    "存入成品区",
                                    style = AuroraTypography.Caption,
                                    color = AuroraColorTokens.AuroraAccent
                                )
                            }
                        }
                    }
                    if (!card.avatar.isNullOrBlank() && onClearCover != null) {
                        Text(
                            "清除封面",
                            style = AuroraTypography.Caption,
                            color = AuroraColorTokens.SignalError,
                            modifier = Modifier
                                .clickable(onClick = onClearCover)
                                .padding(horizontal = 4.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CardSubTabs(
    selected: Int,
    onSelect: (Int) -> Unit
) {
    val tabs = listOf("设定", "世界书", "正则", "总览")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        tabs.forEachIndexed { index, label ->
            val active = selected == index
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(32.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (active) AuroraColorTokens.AuroraAccent.copy(alpha = 0.12f)
                        else AuroraColorTokens.Base2
                    )
                    .border(
                        BorderStroke(
                            1.dp,
                            if (active) AuroraColorTokens.AuroraAccent.copy(alpha = 0.45f)
                            else AuroraColorTokens.LinePrimary
                        ),
                        RoundedCornerShape(10.dp)
                    )
                    .clickable { onSelect(index) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    style = AuroraTypography.Caption,
                    color = if (active) AuroraColorTokens.AuroraAccent
                    else AuroraColorTokens.TextCaption,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun MonitorContent(
    planBook: PlanBook?,
    tasks: List<AgentTask>,
    contextInfo: ContextInfo,
    agentStatus: AgentStatus,
    modelName: String?,
    workMode: WorkMode,
    reasoningEffort: String?,
    card: CharacterCard?,
    onOpenPreview: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        val hasPlan = planBook != null && planBook.parts.isNotEmpty()
        if (hasPlan) {
            TaskProgressPanel(planBook = planBook, modifier = Modifier.fillMaxWidth())
            PanelDivider()
        }
        if (tasks.isNotEmpty()) {
            TaskBoardSection(tasks = tasks)
            PanelDivider()
        }
        if (!hasPlan && tasks.isEmpty()) {
            IdleStatusRow(agentStatus = agentStatus)
            PanelDivider()
        }

        TokenStatsPanel(
            contextInfo = contextInfo,
            modelName = modelName,
            workModeName = workMode.displayName,
            reasoningEffort = reasoningEffort,
            modifier = Modifier.fillMaxWidth()
        )
        PanelDivider()
        CardSummarySection(card = card, onOpenPreview = onOpenPreview)
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun IdleStatusRow(agentStatus: AgentStatus) {
    Surface(
        color = AuroraColorTokens.Base2,
        shape = AuroraShapes.Small,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(10.dp)
        ) {
            StatusDot(status = agentStatus.toDotStatus())
            Spacer(Modifier.width(8.dp))
            Text(
                "Agent 状态：${agentStatus.label()} · 暂无执行中任务",
                style = AuroraTypography.Caption,
                color = AuroraColorTokens.TextBody,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun PanelDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .height(1.dp)
            .background(AuroraColorTokens.LineSecondary)
    )
}

/** 任务板：AI 经 task.add / task.status 维护的子任务 */
@Composable
private fun TaskBoardSection(tasks: List<AgentTask>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = "子任务",
            style = AuroraTypography.Caption,
            color = AuroraColorTokens.TextCaption
        )
        if (tasks.isEmpty()) {
            Text(
                text = "暂无子任务",
                style = AuroraTypography.Tech,
                color = AuroraColorTokens.TextCaption
            )
        } else {
            tasks.forEach { task ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(status = taskStatusDot(task.status))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = task.title,
                        style = AuroraTypography.Caption,
                        color = AuroraColorTokens.TextBody,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = task.status,
                        style = AuroraTypography.Tech,
                        color = AuroraColorTokens.TextCaption
                    )
                }
            }
        }
    }
}

private fun taskStatusDot(status: String): DotStatus = when (status.uppercase()) {
    AgentTask.STATUS_RUNNING -> DotStatus.RUNNING
    AgentTask.STATUS_DONE -> DotStatus.SUCCESS
    AgentTask.STATUS_FAILED -> DotStatus.ERROR
    else -> DotStatus.IDLE
}

/** 卡片摘要 + 预览入口 */
@Composable
private fun CardSummarySection(
    card: CharacterCard?,
    onOpenPreview: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = card?.name?.ifBlank { "未命名角色" } ?: "未选择角色卡",
            style = AuroraTypography.PanelTitle,
            color = AuroraColorTokens.TextTitle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = buildString {
                append(card?.spec?.replace("chara_card_", "V") ?: "V2")
                card?.characterBook?.entries?.takeIf { it.isNotEmpty() }
                    ?.let { append(" · 世界书${it.size}条") }
                card?.regexScripts?.takeIf { it.isNotEmpty() }
                    ?.let { append(" · 正则${it.size}条") }
            },
            style = AuroraTypography.Tech,
            color = AuroraColorTokens.TextCaption,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "打开角色卡预览",
            style = AuroraTypography.Caption,
            color = AuroraColorTokens.AuroraAccent,
            modifier = Modifier
                .clickable(onClick = onOpenPreview)
                .padding(vertical = 4.dp)
        )
    }
}
