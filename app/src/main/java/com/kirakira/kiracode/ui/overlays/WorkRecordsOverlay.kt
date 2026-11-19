package com.kirakira.kiracode.ui.overlays

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kirakira.kiracode.ui.common.AuroraDialog
import com.kirakira.kiracode.ui.common.AuroraDialogActions
import com.kirakira.kiracode.ui.common.AuroraDialogTextField
import com.kirakira.kiracode.ui.common.AuroraDialogTitle
import com.kirakira.kiracode.ui.theme.IndustrialColorTokens
import com.kirakira.kiracode.ui.theme.IndustrialShapes
import com.kirakira.kiracode.ui.theme.IndustrialSpacing
import com.kirakira.kiracode.ui.theme.IndustrialTypography
import com.kirakira.kiracode.ui.theme.bouncyClickable
import com.kirakira.kiracode.ui.workspace.WorkRecord
import com.kirakira.kiracode.ui.workspace.formatRecordTime

/**
 * 工作记录浮窗（统一入口）：完整滚动 + 可折叠搜索 + 最近/最早排序 + 置顶优先 +
 * 重命名 + 左滑删除 + 点击恢复已有会话（不新建会话）。
 */
@Composable
fun WorkRecordsOverlay(
    records: List<WorkRecord>,
    currentSessionId: Long?,
    onDismiss: () -> Unit,
    onSelectRecord: (WorkRecord) -> Unit,
    onToggleFavorite: (id: Long, isFavorite: Boolean) -> Unit,
    onRename: (id: Long, newTitle: String) -> Unit,
    onDelete: (id: Long) -> Unit,
    onCreateNew: () -> Unit,
    modifier: Modifier = Modifier
) {
    var query by rememberSaveable { mutableStateOf("") }
    var searchExpanded by rememberSaveable { mutableStateOf(false) }
    var sortDescending by rememberSaveable { mutableStateOf(true) }
    var renameTarget by remember { mutableStateOf<WorkRecord?>(null) }

    val filtered = remember(records, query, sortDescending) {
        val base = compareByDescending<WorkRecord> { it.isFavorite }
        records
            .filter { (it.cardName.ifBlank { "新会话" }).contains(query.trim(), ignoreCase = true) }
            .sortedWith(
                if (sortDescending) base.thenByDescending { it.lastEditTime }
                else base.thenBy { it.lastEditTime }
            )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = IndustrialShapes.Small,
            color = IndustrialColorTokens.Base1,
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = IndustrialSpacing.Page)
                .fillMaxHeight(0.85f)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // 头部：折叠时标题 + 数量 + 搜索/关闭；展开时搜索框占满一行
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = IndustrialSpacing.Overlay, vertical = IndustrialSpacing.Item)
                ) {
                    if (searchExpanded) {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            placeholder = {
                                Text(
                                    "搜索标题",
                                    style = IndustrialTypography.Tech,
                                    color = IndustrialColorTokens.TextCaption
                                )
                            },
                            singleLine = true,
                            shape = IndustrialShapes.Micro,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(IndustrialSpacing.Tight))
                        IconButton(onClick = {
                            query = ""
                            searchExpanded = false
                        }) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "收起搜索",
                                tint = IndustrialColorTokens.TextCaption
                            )
                        }
                    } else {
                        Text(
                            "工作记录",
                            style = IndustrialTypography.PanelTitle,
                            color = IndustrialColorTokens.TextTitle
                        )
                        Spacer(Modifier.width(IndustrialSpacing.Item))
                        Text(
                            "${filtered.size} 条",
                            style = IndustrialTypography.Tech,
                            color = IndustrialColorTokens.TextTech
                        )
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = { searchExpanded = true }) {
                            Icon(
                                Icons.Filled.Search,
                                contentDescription = "搜索",
                                tint = IndustrialColorTokens.TextCaption
                            )
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "关闭",
                                tint = IndustrialColorTokens.TextCaption
                            )
                        }
                    }
                }

                // 排序行
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = IndustrialSpacing.Overlay)
                ) {
                    Spacer(Modifier.weight(1f))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.bouncyClickable { sortDescending = !sortDescending }
                    ) {
                        Icon(
                            Icons.Filled.Sort,
                            contentDescription = null,
                            tint = IndustrialColorTokens.TextCaption,
                            modifier = Modifier.size(IndustrialSpacing.IconSmall)
                        )
                        Spacer(Modifier.width(IndustrialSpacing.Tight))
                        Text(
                            if (sortDescending) "最近" else "最早",
                            style = IndustrialTypography.Tech,
                            color = IndustrialColorTokens.TextTech
                        )
                    }
                }

                Spacer(Modifier.height(IndustrialSpacing.Item))

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(
                        horizontal = IndustrialSpacing.Overlay,
                        vertical = IndustrialSpacing.Compact
                    ),
                    verticalArrangement = Arrangement.spacedBy(IndustrialSpacing.Tight)
                ) {
                    if (filtered.isEmpty()) {
                        item {
                            if (query.isBlank()) {
                                Box(
                                    modifier = Modifier
                                        .fillParentMaxHeight(0.7f)
                                        .fillMaxWidth(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(
                                            Icons.Filled.History,
                                            contentDescription = null,
                                            tint = IndustrialColorTokens.TextCaption.copy(alpha = 0.4f),
                                            modifier = Modifier.size(48.dp)
                                        )
                                        Spacer(Modifier.height(12.dp))
                                        Text(
                                            "暂无工作记录",
                                            style = IndustrialTypography.Caption,
                                            color = IndustrialColorTokens.TextCaption
                                        )
                                        Spacer(Modifier.height(16.dp))
                                        Button(
                                            onClick = onCreateNew,
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = IndustrialColorTokens.SignalThinking.copy(alpha = 0.15f),
                                                contentColor = IndustrialColorTokens.SignalThinking
                                            )
                                        ) {
                                            Text("开始新会话")
                                        }
                                    }
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = IndustrialSpacing.Group),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "没有匹配的记录",
                                        style = IndustrialTypography.Caption,
                                        color = IndustrialColorTokens.TextCaption
                                    )
                                }
                            }
                        }
                    }

                    items(filtered, key = { it.id }) { record ->
                        OverlayRecordRow(
                            record = record,
                            selected = record.id == currentSessionId,
                            onSessionClick = {
                                onSelectRecord(record)
                                onDismiss()
                            },
                            onToggleFavorite = { onToggleFavorite(record.id, !record.isFavorite) },
                            onRenameClick = { renameTarget = record },
                            onSwipeDelete = { onDelete(record.id) }
                        )
                    }
                }
            }
        }
    }

    renameTarget?.let { target ->
        RenameDialog(
            currentTitle = target.cardName,
            onDismiss = { renameTarget = null },
            onConfirm = { newTitle ->
                renameTarget = null
                onRename(target.id, newTitle)
            }
        )
    }
}

/** 记录卡片：圆角 + 左滑删除 + 星标 + 标题/时间 + 重命名入口 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OverlayRecordRow(
    record: WorkRecord,
    selected: Boolean,
    onSessionClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onRenameClick: () -> Unit,
    onSwipeDelete: () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onSwipeDelete()
                true
            } else {
                false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .clip(IndustrialShapes.Small)
                    .background(IndustrialColorTokens.SignalError),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "删除",
                    tint = Color.White,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
            }
        }
    ) {
        Surface(
            shape = IndustrialShapes.Small,
            color = IndustrialColorTokens.Base2,
            border = BorderStroke(
                1.dp,
                if (selected) IndustrialColorTokens.SignalWarning.copy(alpha = 0.5f)
                else Color.Transparent
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp)
                .bouncyClickable(onClick = onSessionClick)
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = if (record.isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                        contentDescription = if (record.isFavorite) "取消置顶" else "置顶",
                        tint = if (record.isFavorite) IndustrialColorTokens.SignalWarning
                        else IndustrialColorTokens.SignalNeutral,
                        modifier = Modifier.size(16.dp)
                    )
                }

                Spacer(Modifier.width(8.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = record.cardName.ifBlank { "新会话" },
                        style = IndustrialTypography.Caption,
                        color = if (selected) IndustrialColorTokens.TextTitle
                        else IndustrialColorTokens.TextBody,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = formatRecordTime(record.lastEditTime),
                        style = IndustrialTypography.Tech,
                        color = IndustrialColorTokens.TextTech
                    )
                }

                IconButton(
                    onClick = onRenameClick,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Filled.Edit,
                        contentDescription = "重命名",
                        tint = IndustrialColorTokens.TextCaption,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

/** 重命名弹窗 */
@Composable
private fun RenameDialog(
    currentTitle: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var input by remember { mutableStateOf(currentTitle) }
    AuroraDialog(
        onDismissRequest = onDismiss,
        maxWidth = 400.dp
    ) {
        AuroraDialogTitle("重命名工作记录")
        AuroraDialogTextField(
            value = input,
            onValueChange = { input = it },
            label = "名称",
            singleLine = true
        )
        AuroraDialogActions(
            confirmText = "确定",
            onConfirm = { if (input.isNotBlank()) onConfirm(input.trim()) },
            onDismiss = onDismiss,
            confirmEnabled = input.isNotBlank()
        )
    }
}
