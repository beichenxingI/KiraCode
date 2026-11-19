package com.kirakira.kiracode.ui.overlays

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kirakira.kiracode.domain.model.CharacterCard
import com.kirakira.kiracode.ui.edit.EditState
import com.kirakira.kiracode.ui.edit.EditViewModel
import com.kirakira.kiracode.ui.common.AuroraDialog
import com.kirakira.kiracode.ui.common.AuroraDialogActions
import com.kirakira.kiracode.ui.common.AuroraDialogText
import com.kirakira.kiracode.ui.common.AuroraDialogTitle
import com.kirakira.kiracode.ui.theme.IndustrialColorTokens
import com.kirakira.kiracode.ui.theme.IndustrialShapes
import com.kirakira.kiracode.ui.theme.IndustrialSpacing
import com.kirakira.kiracode.ui.theme.IndustrialTypography
import com.kirakira.kiracode.ui.theme.bouncyClickable

/**
 * 编辑浮窗：全字段表单（基础信息/角色设定/对话设定/世界观与进阶）。
 *
 * - 编辑状态由 EditViewModel 管理（保存写草稿）；
 * - 关闭时做脏状态判断（未保存改动 → 确认丢弃/存草稿）；
 * - 不创建新的工作会话；
 * - 导出走 L2 ExportDialog。
 */
@Composable
fun EditOverlay(
    initialCard: CharacterCard,
    onDismiss: () -> Unit,
    onExportRequest: (CharacterCard) -> Unit,
    modifier: Modifier = Modifier
) {
    val vm: EditViewModel = hiltViewModel()

    LaunchedEffect(initialCard) {
        vm.setInitialCard(initialCard)
    }

    val state by vm.uiState.state.collectAsStateWithLifecycle()
    val card = vm.uiState.card
    val canExport = vm.uiState.canExport
    val dirty = card != initialCard
    var confirmClose by remember { mutableStateOf(false) }
    var advancedOpen by remember { mutableStateOf(false) }

    fun requestClose() {
        if (dirty) confirmClose = true else onDismiss()
    }

    Dialog(
        onDismissRequest = ::requestClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = IndustrialShapes.Small,
            color = IndustrialColorTokens.Base1,
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = IndustrialSpacing.Page)
                .fillMaxHeight(0.92f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding()
            ) {
                // 头部：标题 + 保存草稿 + 导出 + 关闭
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = IndustrialSpacing.Overlay, vertical = IndustrialSpacing.Item)
                ) {
                    Text(
                        "编辑角色卡",
                        style = IndustrialTypography.PanelTitle,
                        color = IndustrialColorTokens.TextTitle
                    )
                    Spacer(Modifier.weight(1f))
                    Icon(
                        Icons.Filled.Save,
                        contentDescription = "保存草稿",
                        tint = IndustrialColorTokens.SignalThinking,
                        modifier = Modifier
                            .size(IndustrialSpacing.IconMedium)
                            .bouncyClickable { vm.onSaveDraftClick() }
                    )
                    Spacer(Modifier.width(IndustrialSpacing.Item))
                    Icon(
                        Icons.Filled.Upload,
                        contentDescription = "导出",
                        tint = if (canExport) IndustrialColorTokens.TextCaption else IndustrialColorTokens.SignalNeutral,
                        modifier = Modifier
                            .size(IndustrialSpacing.IconMedium)
                            .bouncyClickable(enabled = canExport) { onExportRequest(vm.getCurrentCard()) }
                    )
                    Spacer(Modifier.width(IndustrialSpacing.Item))
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "关闭",
                        tint = IndustrialColorTokens.TextCaption,
                        modifier = Modifier
                            .size(IndustrialSpacing.IconMedium)
                            .bouncyClickable(onClick = ::requestClose)
                    )
                }

                // 表单
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = IndustrialSpacing.Overlay,
                        vertical = IndustrialSpacing.Compact
                    ),
                    verticalArrangement = Arrangement.spacedBy(IndustrialSpacing.Item)
                ) {
                    item {
                        OverlaySection("基础信息") {
                            LabeledField("角色名称 *", card.name, { vm.updateField("name", it) }, singleLine = true, isError = card.name.isBlank())
                            LabeledField("创建者", card.creator, { vm.updateField("creator", it) }, singleLine = true)
                        }
                    }
                    item {
                        OverlaySection("角色设定") {
                            LabeledField("人设描述 *", card.description, { vm.updateField("description", it) }, minLines = 6, isError = card.description.isBlank())
                            LabeledField("性格 *", card.personality, { vm.updateField("personality", it) }, minLines = 2, isError = card.personality.isBlank())
                        }
                    }
                    item {
                        OverlaySection("对话设定") {
                            LabeledField("首条消息 *", card.firstMessage, { vm.updateField("firstMessage", it) }, minLines = 5, isError = card.firstMessage.isBlank())
                            LabeledField("示例对话", card.exampleDialogue, { vm.updateField("exampleDialogue", it) }, minLines = 8, fontFamily = FontFamily.Monospace)
                        }
                    }
                    item {
                        OverlaySection(
                            title = "世界观与进阶",
                            trailing = {
                                Icon(
                                    imageVector = if (advancedOpen) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                    contentDescription = if (advancedOpen) "收起" else "展开",
                                    tint = IndustrialColorTokens.TextCaption,
                                    modifier = Modifier
                                        .size(IndustrialSpacing.IconSmall)
                                        .bouncyClickable { advancedOpen = !advancedOpen }
                                )
                            }
                        ) {
                            if (advancedOpen) {
                                LabeledField("场景设定", card.scenario, { vm.updateField("scenario", it) }, minLines = 3)
                                LabeledField("系统提示词", card.systemPrompt, { vm.updateField("systemPrompt", it) }, minLines = 3)
                                AlternateGreetingsEditor(
                                    greetings = card.alternateGreetings,
                                    onGreetingsChange = { vm.updateField("alternateGreetings", it) }
                                )
                                TagsEditor(
                                    tags = card.tags,
                                    onTagsChange = { vm.updateField("tags", it) }
                                )
                                LabeledField("创作者备注", card.creatorNotes, { vm.updateField("creatorNotes", it) }, minLines = 3)
                            }
                        }
                    }
                    item {
                        Surface(
                            color = IndustrialColorTokens.Base2,
                            shape = IndustrialShapes.Micro,
                            modifier = Modifier
                                .fillMaxWidth()
                                .bouncyClickable { vm.onSaveDraftClick() }
                        ) {
                            Text(
                                "保存草稿",
                                style = IndustrialTypography.Caption,
                                color = IndustrialColorTokens.SignalThinking,
                                modifier = Modifier.padding(
                                    horizontal = IndustrialSpacing.Item,
                                    vertical = IndustrialSpacing.Item
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    // 保存中遮罩
    if (state is EditState.Saving) {
        Surface(
            color = IndustrialColorTokens.Base0.copy(alpha = 0.6f),
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                Text(
                    "正在保存草稿...",
                    style = IndustrialTypography.Caption,
                    color = IndustrialColorTokens.TextBody
                )
            }
        }
    }

    // 关闭确认（脏状态）
    if (confirmClose) {
        AuroraDialog(onDismissRequest = { confirmClose = false }) {
            AuroraDialogTitle("放弃未保存的修改？")
            AuroraDialogText("当前有未保存的字段改动。")
            AuroraDialogActions(
                confirmText = "放弃修改",
                onConfirm = {
                    confirmClose = false
                    onDismiss()
                },
                onDismiss = { confirmClose = false },
                dismissText = "继续编辑",
                confirmColor = IndustrialColorTokens.SignalError
            )
        }
    }
}

@Composable
private fun OverlaySection(
    title: String,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Surface(
        color = IndustrialColorTokens.Base2,
        shape = IndustrialShapes.Small,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(IndustrialSpacing.Panel),
            verticalArrangement = Arrangement.spacedBy(IndustrialSpacing.Item)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    title,
                    style = IndustrialTypography.SectionLabel,
                    color = IndustrialColorTokens.TextCaption
                )
                if (trailing != null) {
                    Spacer(Modifier.weight(1f))
                    trailing()
                }
            }
            content()
        }
    }
}

/** 备选问候语编辑器 */
@Composable
private fun AlternateGreetingsEditor(
    greetings: List<String>,
    onGreetingsChange: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(IndustrialSpacing.Tight)) {
        Text(
            "备选问候语",
            style = IndustrialTypography.Tech,
            color = IndustrialColorTokens.TextCaption
        )
        greetings.forEachIndexed { index, greeting ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = greeting,
                    onValueChange = { newValue ->
                        val updated = greetings.toMutableList().apply { set(index, newValue) }
                        onGreetingsChange(updated.joinToString("\n"))
                    },
                    modifier = Modifier.weight(1f),
                    label = { Text("#${index + 1}", style = IndustrialTypography.Tech) },
                    minLines = 2,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default)
                )
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "删除问候语",
                    tint = IndustrialColorTokens.SignalError,
                    modifier = Modifier
                        .size(IndustrialSpacing.IconSmall)
                        .clickable {
                            val updated = greetings.toMutableList().apply { removeAt(index) }
                            onGreetingsChange(updated.joinToString("\n"))
                        }
                )
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onGreetingsChange((greetings + "").joinToString("\n")) }
        ) {
            Icon(
                Icons.Filled.Add,
                contentDescription = null,
                tint = IndustrialColorTokens.SignalThinking,
                modifier = Modifier.size(IndustrialSpacing.IconSmall)
            )
            Spacer(Modifier.width(IndustrialSpacing.Tight))
            Text(
                "添加问候语",
                style = IndustrialTypography.Tech,
                color = IndustrialColorTokens.SignalThinking
            )
        }
    }
}

/** 标签编辑器 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun TagsEditor(
    tags: List<String>,
    onTagsChange: (String) -> Unit
) {
    var newTag by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(IndustrialSpacing.Tight)) {
        Text(
            "标签",
            style = IndustrialTypography.Tech,
            color = IndustrialColorTokens.TextCaption
        )
        if (tags.isNotEmpty()) {
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(IndustrialSpacing.Tight)
            ) {
                tags.forEachIndexed { index, tag ->
                    InputChip(
                        selected = false,
                        onClick = {
                            val updated = tags.toMutableList().apply { removeAt(index) }
                            onTagsChange(updated.joinToString(","))
                        },
                        label = { Text(tag, style = IndustrialTypography.Tech) }
                    )
                }
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = newTag,
                onValueChange = { newTag = it },
                modifier = Modifier.weight(1f),
                label = { Text("添加标签", style = IndustrialTypography.Tech) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
            )
            Icon(
                Icons.Filled.Add,
                contentDescription = "添加标签",
                tint = IndustrialColorTokens.SignalThinking,
                modifier = Modifier
                    .size(IndustrialSpacing.IconSmall)
                    .clickable {
                        if (newTag.isNotBlank()) {
                            val updated = tags + newTag.trim()
                            onTagsChange(updated.joinToString(","))
                            newTag = ""
                        }
                    }
            )
        }
    }
}

/** 带标签的输入框 */
@Composable
private fun LabeledField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    singleLine: Boolean = false,
    minLines: Int = 1,
    isError: Boolean = false,
    fontFamily: FontFamily? = null
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(IndustrialSpacing.Tight)
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(label, style = IndustrialTypography.Tech) },
            singleLine = singleLine,
            minLines = if (singleLine) 1 else minLines,
            isError = isError && value.isBlank(),
            supportingText = if (isError && value.isBlank()) {
                { Text("必填", style = IndustrialTypography.Tech, color = IndustrialColorTokens.SignalError) }
            } else {
                null
            },
            keyboardOptions = KeyboardOptions(imeAction = if (singleLine) ImeAction.Next else ImeAction.Default),
            textStyle = com.kirakira.kiracode.ui.theme.IndustrialTypography.Body.copy(fontFamily = fontFamily)
        )
    }
}
