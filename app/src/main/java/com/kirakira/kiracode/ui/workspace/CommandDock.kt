package com.kirakira.kiracode.ui.workspace

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import com.kirakira.kiracode.domain.model.RefSelection
import com.kirakira.kiracode.ui.theme.AuroraColorTokens
import com.kirakira.kiracode.ui.theme.AuroraShapes
import com.kirakira.kiracode.ui.theme.IndustrialSpacing
import com.kirakira.kiracode.ui.theme.IndustrialTypography

/** 内置命令（输入 / 时提示；/help /clear /export 有实际行为） */
private val builtinCommands = listOf(
    "/help 命令说明",
    "/clear 释放上下文",
    "/export 导出角色卡"
)

/**
 * 底部命令 Dock：
 * - / 命令提示（输入 / 时点选回填）；
 * - 已挂载参考卡 chips（当前会话已挂载资料，可卸载）；
 * - 本次消息附件 chips（文件名 + 移除）；
 * - 输入框（执行中仍可编辑下一条待发送文本）；
 * - 执行按钮：空闲=执行，运行中=停止。
 */
@Composable
fun CommandDock(
    value: String,
    onValueChange: (String) -> Unit,
    onExecute: (String) -> Unit,
    agentStatus: AgentStatus,
    onStop: () -> Unit,
    onFilesSelected: (List<android.net.Uri>) -> Unit,
    referenceFiles: List<ReferenceFile>,
    onRemoveFile: (ReferenceFile) -> Unit,
    refSelections: List<RefSelection>,
    refCardNames: Map<String, String>,
    onUnmountRef: (String) -> Unit,
    lastUserMessage: String? = null,
    onRetry: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val filePicker = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) onFilesSelected(uris)
    }
    Column(modifier = modifier.fillMaxWidth()) {
        // / 命令提示（输入 / 时出现，点选回填）
        if (value.startsWith("/")) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(IndustrialSpacing.Tight),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = IndustrialSpacing.Page, vertical = IndustrialSpacing.Tight)
            ) {
                items(builtinCommands) { cmd ->
                    Surface(
                        color = AuroraColorTokens.AuroraViolet.copy(alpha = 0.14f),
                        shape = AuroraShapes.Micro,
                        onClick = { onValueChange(cmd.substringBefore(" ")) }
                    ) {
                        Text(
                            cmd,
                            style = IndustrialTypography.Tech,
                            color = AuroraColorTokens.AuroraViolet,
                            modifier = Modifier.padding(
                                horizontal = IndustrialSpacing.Compact,
                                vertical = IndustrialSpacing.Tight
                            )
                        )
                    }
                }
            }
        }

        // 已挂载参考卡（当前会话已挂载资料：可卸载）
        if (refSelections.isNotEmpty()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(IndustrialSpacing.Tight),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = IndustrialSpacing.Page, vertical = IndustrialSpacing.Tight)
            ) {
                items(refSelections, key = { it.uuid }) { sel ->
                    RefChip(
                        label = "已挂载：${refCardNames[sel.uuid] ?: sel.uuid.take(8)}",
                        onRemove = { onUnmountRef(sel.uuid) }
                    )
                }
            }
        }

        // 本次消息附件（文件名 + 移除）
        if (referenceFiles.isNotEmpty()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(IndustrialSpacing.Tight),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = IndustrialSpacing.Page, vertical = IndustrialSpacing.Tight)
            ) {
                items(referenceFiles, key = { it.uri.toString() }) { file ->
                    RefChip(
                        label = file.name,
                        fileIcon = when {
                            file.name.endsWith(".json", ignoreCase = true) -> Icons.Filled.DataObject
                            file.name.endsWith(".png", ignoreCase = true) -> Icons.Filled.Image
                            else -> Icons.Filled.Description
                        },
                        onRemove = { onRemoveFile(file) }
                    )
                }
            }
        }

        Surface(
            color = AuroraColorTokens.Base2,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.Top,
                modifier = Modifier.padding(IndustrialSpacing.Tight)
            ) {
                IconButton(
                    onClick = {
                        filePicker.launch(
                            arrayOf(
                                "application/json",
                                "image/png",
                                "text/plain",
                                "text/*"
                            )
                        )
                    },
                    modifier = Modifier
                        .padding(top = 10.dp)
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(AuroraColorTokens.Base1)
                ) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = "添加附件文件",
                        tint = AuroraColorTokens.TextCaption,
                        modifier = Modifier.size(IndustrialSpacing.IconMedium)
                    )
                }
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text(
                            when {
                                agentStatus.isWaitingForUser -> "回答上方问题，或输入你的决定"
                                agentStatus.isBusy -> "执行中…可编辑下一条待发送文本"
                                else -> "输入命令或描述你的需求，输入 / 查看命令列表"
                            },
                            style = IndustrialTypography.Tech,
                            color = AuroraColorTokens.TextCaption
                        )
                    },
                    textStyle = IndustrialTypography.Body.copy(color = AuroraColorTokens.TextTitle),
                    minLines = 1,
                    maxLines = 5,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        cursorColor = AuroraColorTokens.AuroraViolet
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default)
                )
                when {
                    agentStatus.isBusy -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(IndustrialSpacing.IconMedium),
                            color = AuroraColorTokens.AuroraAccent,
                            strokeWidth = IndustrialSpacing.Hairline
                        )
                        Spacer(Modifier.width(IndustrialSpacing.Tight))
                        Surface(
                            onClick = onStop,
                            shape = CircleShape,
                            color = AuroraColorTokens.AuroraAccent,
                            modifier = Modifier.padding(top = 10.dp).size(36.dp)
                        ) {
                            Icon(
                                Icons.Filled.Stop,
                                contentDescription = "停止执行",
                                tint = AuroraColorTokens.Base0,
                                modifier = Modifier
                                    .padding(9.dp)
                                    .size(IndustrialSpacing.IconMedium)
                            )
                        }
                    }
                    agentStatus == AgentStatus.ERROR && lastUserMessage != null -> {
                        Surface(
                            onClick = onRetry,
                            shape = CircleShape,
                            color = AuroraColorTokens.Base1,
                            modifier = Modifier.padding(top = 10.dp).size(36.dp)
                        ) {
                            Icon(
                                Icons.Filled.Refresh,
                                contentDescription = "重试上一条指令",
                                tint = AuroraColorTokens.SignalError,
                                modifier = Modifier
                                    .padding(9.dp)
                                    .size(IndustrialSpacing.IconMedium)
                            )
                        }
                    }
                    else -> {
                        val canSend = value.isNotBlank()
                        Surface(
                            onClick = { if (canSend) onExecute(value) },
                            enabled = canSend,
                            shape = CircleShape,
                            color = if (canSend) AuroraColorTokens.AuroraAccent else AuroraColorTokens.Base1,
                            modifier = Modifier.padding(top = 10.dp).size(36.dp)
                        ) {
                            Icon(
                                Icons.Filled.ArrowUpward,
                                contentDescription = "执行",
                                tint = if (canSend) AuroraColorTokens.Base0
                                else AuroraColorTokens.TextCaption,
                                modifier = Modifier
                                    .padding(9.dp)
                                    .size(IndustrialSpacing.IconMedium)
                            )
                        }
                    }
                }
            }
        }
    }
}

/** chip：已挂载参考卡 / 本次消息附件（文件名 + 移除） */
@Composable
private fun RefChip(
    label: String,
    fileIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onRemove: () -> Unit
) {
    Surface(
        color = AuroraColorTokens.AuroraViolet.copy(alpha = 0.14f),
        shape = AuroraShapes.Small
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(
                start = IndustrialSpacing.Item,
                end = IndustrialSpacing.Tight,
                top = IndustrialSpacing.Tight,
                bottom = IndustrialSpacing.Tight
            )
        ) {
            if (fileIcon != null) {
                Icon(
                    imageVector = fileIcon,
                    contentDescription = null,
                    tint = AuroraColorTokens.AuroraViolet,
                    modifier = Modifier.size(IndustrialSpacing.IconSmall)
                )
                Spacer(Modifier.width(IndustrialSpacing.Tight))
            }
            Text(
                label,
                style = IndustrialTypography.Tech,
                color = AuroraColorTokens.TextTitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = IndustrialSpacing.StatusModelMaxWidth)
            )
            Spacer(Modifier.width(IndustrialSpacing.Tight))
            Icon(
                Icons.Filled.Close,
                contentDescription = "移除",
                tint = AuroraColorTokens.TextCaption,
                modifier = Modifier
                    .size(IndustrialSpacing.IconSmall)
                    .clickable(onClick = onRemove)
            )
        }
    }
}
