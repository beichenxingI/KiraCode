package com.kirakira.kiracode.ui.workspace

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Engineering
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kirakira.kiracode.domain.model.WorkMode
import com.kirakira.kiracode.ui.theme.AuroraColorTokens
import com.kirakira.kiracode.ui.theme.AuroraShapes
import com.kirakira.kiracode.ui.theme.IndustrialSpacing
import com.kirakira.kiracode.ui.theme.IndustrialTypography

/**
 * 运行配置条：模型/LLM 方案 · 思考强度 · 工作模式 三段独立分段控制。
 * 模型从已保存方案读取；思考强度反映 provider/model 支持状态（分段档位）；
 * 工作模式沿用当前执行语义，YOLO 带全局操作警示。
 */
@Composable
fun RunConfigStrip(
    models: List<String>,
    currentModel: String,
    reasoningState: com.kirakira.kiracode.domain.model.ReasoningUiState,
    currentEffort: String?,
    currentMode: WorkMode,
    onSelectModel: (String) -> Unit,
    onSelectEffort: (String?) -> Unit,
    onSelectMode: (WorkMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(IndustrialSpacing.Item)
    ) {
        ModelSegment(
            models = models,
            currentModel = currentModel,
            onSelectModel = onSelectModel,
            modifier = Modifier.weight(1f, fill = false)
        )
        EffortSegment(
            reasoningState = reasoningState,
            currentEffort = currentEffort,
            onSelectEffort = onSelectEffort
        )
        ModeSegment(
            currentMode = currentMode,
            onSelectMode = onSelectMode
        )
    }
}

/** 模型段：当前模型 + 已保存方案下拉 */
@Composable
private fun ModelSegment(
    models: List<String>,
    currentModel: String,
    onSelectModel: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val modelShort = currentModel.ifBlank { "未配置模型" }
        .substringBefore("/").take(18)

    Box(modifier = modifier) {
        SegmentChip(
            label = modelShort,
            onClick = { expanded = true }
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (models.isEmpty()) {
                DropdownMenuItem(
                    text = { Text("暂无模型：请在设置中添加并测试连接", style = IndustrialTypography.Caption) },
                    onClick = { expanded = false }
                )
            }
            models.forEach { m ->
                DropdownMenuItem(
                    text = {
                        Text(
                            m,
                            style = IndustrialTypography.Caption,
                            fontWeight = if (m == currentModel) FontWeight.SemiBold else FontWeight.Normal
                        )
                    },
                    onClick = {
                        onSelectModel(m)
                        expanded = false
                    }
                )
            }
        }
    }
}

/** 思考强度段：分段档位（自动/低/中/高），严格反映支持状态 */
@Composable
private fun EffortSegment(
    reasoningState: com.kirakira.kiracode.domain.model.ReasoningUiState,
    currentEffort: String?,
    onSelectEffort: (String?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val effortLabel = when (currentEffort) {
        null -> "自动"
        "low" -> "低"
        "medium" -> "中"
        "high" -> "高"
        else -> currentEffort
    }
    val stateLabel = when {
        reasoningState.probing -> "探测中"
        !reasoningState.supported -> "不支持"
        else -> effortLabel
    }

    Box {
        SegmentChip(
            label = "强度 $stateLabel",
            onClick = { if (reasoningState.supported) expanded = true }
        )
        if (reasoningState.supported) {
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                listOf(
                    null to "自动",
                    "low" to "低（low）",
                    "medium" to "中（medium）",
                    "high" to "高（high）"
                ).forEach { (value, label) ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                "思考强度：$label",
                                style = IndustrialTypography.Caption,
                                fontWeight = if (currentEffort == value) FontWeight.SemiBold else FontWeight.Normal
                            )
                        },
                        onClick = {
                            onSelectEffort(value)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

/** 工作模式段：3 档（仅会话/代理人/全局 YOLO），YOLO 带全局操作警示 */
@Composable
private fun ModeSegment(
    currentMode: WorkMode,
    onSelectMode: (WorkMode) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        SegmentChip(
            label = currentMode.displayName,
            onClick = { expanded = true },
            warning = currentMode.warningText != null
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            WorkMode.entries.forEach { mode ->
                DropdownMenuItem(
                    leadingIcon = {
                        Icon(
                            imageVector = when (mode) {
                                WorkMode.CHAT -> Icons.Default.Chat
                                WorkMode.AGENT -> Icons.Default.Engineering
                                else -> Icons.Default.FlashOn
                            },
                            contentDescription = null,
                            tint = if (mode.warningText != null) AuroraColorTokens.KiraGold
                            else AuroraColorTokens.TextCaption,
                            modifier = Modifier.size(IndustrialSpacing.IconSmall)
                        )
                    },
                    text = {
                        Column(modifier = Modifier.padding(vertical = IndustrialSpacing.Tight)) {
                            Text(
                                mode.displayName,
                                style = IndustrialTypography.Caption,
                                fontWeight = if (mode == currentMode) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (mode.warningText != null) AuroraColorTokens.KiraGold
                                else AuroraColorTokens.TextTitle
                            )
                            Text(
                                mode.description,
                                style = IndustrialTypography.Tech,
                                color = AuroraColorTokens.TextCaption,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (mode.warningText != null) {
                                Text(
                                    mode.warningText,
                                    style = IndustrialTypography.Tech,
                                    color = AuroraColorTokens.SignalError,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    },
                    onClick = {
                        onSelectMode(mode)
                        expanded = false
                    }
                )
            }
        }
    }
}

/** 分段控件芯片：Aurora 胶囊 + 选中态微光 */
@Composable
private fun SegmentChip(
    label: String,
    onClick: () -> Unit,
    warning: Boolean = false
) {
    val accent = if (warning) AuroraColorTokens.KiraGold else AuroraColorTokens.AuroraAccent
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = accent.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.45f)),
        modifier = Modifier.height(IndustrialSpacing.ChipHeight)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = IndustrialSpacing.Item, vertical = IndustrialSpacing.Tight),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                label,
                style = IndustrialTypography.Caption,
                color = accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Icon(
                Icons.Filled.ArrowDropDown,
                contentDescription = null,
                tint = accent.copy(alpha = 0.8f),
                modifier = Modifier.size(IndustrialSpacing.IconSmall)
            )
        }
    }
}
