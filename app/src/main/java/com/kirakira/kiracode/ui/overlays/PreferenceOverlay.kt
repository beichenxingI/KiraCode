package com.kirakira.kiracode.ui.overlays

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kirakira.kiracode.domain.model.PreferenceDimension
import com.kirakira.kiracode.domain.model.PreferenceOption
import com.kirakira.kiracode.ui.common.AuroraDialog
import com.kirakira.kiracode.ui.common.AuroraDialogActions
import com.kirakira.kiracode.ui.common.AuroraDialogTextField
import com.kirakira.kiracode.ui.common.AuroraDialogTitle
import com.kirakira.kiracode.ui.theme.AuroraColorTokens
import com.kirakira.kiracode.ui.theme.AuroraShapes
import com.kirakira.kiracode.ui.theme.AuroraTypography

/**
 * 创作偏好浮窗：选择模式点胶囊切换选中；编辑模式可改规则、增删用户自建维度/选项。
 * 目录是全局的（Room），选中是会话级的（Session）。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PreferenceOverlay(
    catalog: List<PreferenceDimension>,
    selectedIds: List<String>,
    onToggle: (String) -> Unit,
    onAddDimension: (String) -> Unit,
    onUpdateDimension: (String, String) -> Unit,
    onDeleteDimension: (String) -> Unit,
    onAddOption: (String, String, String) -> Unit,
    onUpdateOption: (String, String, String) -> Unit,
    onDeleteOption: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var editMode by rememberSaveable { mutableStateOf(false) }

    OverlayShell(onDismiss = onDismiss) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "创作偏好",
                    style = AuroraTypography.PanelTitle,
                    color = AuroraColorTokens.TextTitle
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { editMode = !editMode }) {
                        Text(
                            if (editMode) "完成" else "编辑",
                            style = AuroraTypography.Caption,
                            color = AuroraColorTokens.AuroraAccent
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "关闭",
                            tint = AuroraColorTokens.TextCaption
                        )
                    }
                }
            }

            HorizontalDivider(color = AuroraColorTokens.LinePrimary)

            if (selectedIds.isEmpty() && !editMode) {
                Text(
                    "选择偏好后，AI 在创作时会优先满足这些方向。\n不选择则使用默认模式。",
                    style = AuroraTypography.Caption,
                    color = AuroraColorTokens.TextCaption,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                items(catalog, key = { it.id }) { dimension ->
                    DimensionSection(
                        dimension = dimension,
                        selectedIds = selectedIds,
                        editMode = editMode,
                        onToggle = onToggle,
                        onUpdateDimension = onUpdateDimension,
                        onDeleteDimension = onDeleteDimension,
                        onAddOption = onAddOption,
                        onUpdateOption = onUpdateOption,
                        onDeleteOption = onDeleteOption
                    )
                }

                if (editMode) {
                    item {
                        AddDimensionRow(onAdd = onAddDimension)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DimensionSection(
    dimension: PreferenceDimension,
    selectedIds: List<String>,
    editMode: Boolean,
    onToggle: (String) -> Unit,
    onUpdateDimension: (String, String) -> Unit,
    onDeleteDimension: (String) -> Unit,
    onAddOption: (String, String, String) -> Unit,
    onUpdateOption: (String, String, String) -> Unit,
    onDeleteOption: (String) -> Unit
) {
    var showRename by remember { mutableStateOf(false) }

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                dimension.label,
                style = AuroraTypography.Caption,
                color = AuroraColorTokens.TextCaption,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            if (editMode) {
                IconButton(
                    onClick = { showRename = true },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        Icons.Filled.Edit,
                        contentDescription = "重命名维度",
                        tint = AuroraColorTokens.TextCaption,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
            if (editMode && !dimension.isBuiltin) {
                IconButton(
                    onClick = { onDeleteDimension(dimension.id) },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        Icons.Filled.DeleteOutline,
                        contentDescription = "删除维度",
                        tint = AuroraColorTokens.SignalError,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            dimension.options.forEach { option ->
                PreferenceChip(
                    option = option,
                    selected = option.id in selectedIds,
                    editMode = editMode,
                    onToggle = { onToggle(option.id) },
                    onUpdate = onUpdateOption,
                    onDelete = onDeleteOption
                )
            }

            if (editMode) {
                AddOptionChip(
                    dimensionId = dimension.id,
                    onAdd = onAddOption
                )
            }
        }
    }

    if (showRename) {
        PreferenceEditDialog(
            title = "重命名维度",
            initialLabel = dimension.label,
            initialRule = "",
            showRule = false,
            onSave = { label, _ ->
                onUpdateDimension(dimension.id, label)
                showRename = false
            },
            onDismiss = { showRename = false }
        )
    }
}

@Composable
private fun PreferenceChip(
    option: PreferenceOption,
    selected: Boolean,
    editMode: Boolean,
    onToggle: () -> Unit,
    onUpdate: (String, String, String) -> Unit,
    onDelete: (String) -> Unit
) {
    var showEditDialog by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(50),
        color = if (selected) {
            AuroraColorTokens.AuroraAccent.copy(alpha = 0.15f)
        } else {
            AuroraColorTokens.Base2
        },
        border = BorderStroke(
            1.dp,
            if (selected) AuroraColorTokens.AuroraAccent.copy(alpha = 0.6f)
            else AuroraColorTokens.LinePrimary
        ),
        modifier = Modifier.clickable {
            if (editMode) showEditDialog = true else onToggle()
        }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
        ) {
            if (selected && !editMode) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint = AuroraColorTokens.AuroraAccent,
                    modifier = Modifier
                        .size(12.dp)
                        .padding(end = 4.dp)
                )
                Spacer(Modifier.width(4.dp))
            }
            Text(
                option.label,
                style = AuroraTypography.Caption,
                color = if (selected) AuroraColorTokens.AuroraAccent else AuroraColorTokens.TextBody
            )
            if (editMode && !option.isBuiltin) {
                Spacer(Modifier.width(4.dp))
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "删除选项",
                    tint = AuroraColorTokens.TextCaption,
                    modifier = Modifier
                        .size(12.dp)
                        .clickable { onDelete(option.id) }
                )
            }
        }
    }

    if (showEditDialog) {
        PreferenceEditDialog(
            title = "编辑选项",
            initialLabel = option.label,
            initialRule = option.rule,
            showRule = true,
            onSave = { label, rule ->
                onUpdate(option.id, label, rule)
                showEditDialog = false
            },
            onDismiss = { showEditDialog = false }
        )
    }
}

@Composable
private fun AddOptionChip(
    dimensionId: String,
    onAdd: (String, String, String) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(50),
        color = AuroraColorTokens.Base2,
        border = BorderStroke(1.dp, AuroraColorTokens.LinePrimary),
        modifier = Modifier.clickable { showDialog = true }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
        ) {
            Icon(
                Icons.Filled.Add,
                contentDescription = null,
                tint = AuroraColorTokens.AuroraAccent,
                modifier = Modifier.size(12.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                "新增",
                style = AuroraTypography.Caption,
                color = AuroraColorTokens.AuroraAccent
            )
        }
    }

    if (showDialog) {
        PreferenceEditDialog(
            title = "新增选项",
            initialLabel = "",
            initialRule = "",
            showRule = true,
            onSave = { label, rule ->
                onAdd(dimensionId, label, rule)
                showDialog = false
            },
            onDismiss = { showDialog = false }
        )
    }
}

@Composable
private fun AddDimensionRow(onAdd: (String) -> Unit) {
    var showDialog by remember { mutableStateOf(false) }

    TextButton(onClick = { showDialog = true }) {
        Icon(
            Icons.Filled.Add,
            contentDescription = null,
            tint = AuroraColorTokens.AuroraAccent,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            "新增维度",
            style = AuroraTypography.Caption,
            color = AuroraColorTokens.AuroraAccent
        )
    }

    if (showDialog) {
        PreferenceEditDialog(
            title = "新增维度",
            initialLabel = "",
            initialRule = "",
            showRule = false,
            onSave = { label, _ ->
                onAdd(label)
                showDialog = false
            },
            onDismiss = { showDialog = false }
        )
    }
}

@Composable
private fun PreferenceEditDialog(
    title: String,
    initialLabel: String,
    initialRule: String,
    showRule: Boolean,
    onSave: (label: String, rule: String) -> Unit,
    onDismiss: () -> Unit
) {
    var label by remember { mutableStateOf(initialLabel) }
    var rule by remember { mutableStateOf(initialRule) }

    AuroraDialog(
        onDismissRequest = onDismiss,
        maxWidth = 480.dp,
        scrollable = true
    ) {
        AuroraDialogTitle(title)
        AuroraDialogTextField(
            value = label,
            onValueChange = { label = it },
            label = "名称",
            singleLine = true
        )
        if (showRule) {
            AuroraDialogTextField(
                value = rule,
                onValueChange = { rule = it },
                label = "注入规则",
                singleLine = false,
                minLines = 4,
                maxLines = 8
            )
        }
        AuroraDialogActions(
            confirmText = "保存",
            onConfirm = { onSave(label.trim(), rule.trim()) },
            onDismiss = onDismiss,
            confirmEnabled = label.isNotBlank() && (!showRule || rule.isNotBlank())
        )
    }
}
