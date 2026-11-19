package com.kirakira.kiracode.ui.overlays

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.kirakira.kiracode.domain.model.CharacterCard
import com.kirakira.kiracode.ui.config.CardRepositoryViewModel
import com.kirakira.kiracode.ui.finished.FinishedViewModel
import com.kirakira.kiracode.ui.common.AuroraDialog
import com.kirakira.kiracode.ui.common.AuroraDialogActions
import com.kirakira.kiracode.ui.common.AuroraDialogText
import com.kirakira.kiracode.ui.common.AuroraDialogTitle
import com.kirakira.kiracode.ui.common.resolveCoverCoilModel
import com.kirakira.kiracode.ui.theme.AuroraColorTokens
import com.kirakira.kiracode.ui.theme.AuroraShapes
import com.kirakira.kiracode.ui.theme.AuroraTypography
import com.kirakira.kiracode.ui.theme.IndustrialSpacing
import com.kirakira.kiracode.ui.theme.IndustrialTypography
import com.kirakira.kiracode.ui.theme.bouncyClickable

/**
 * 资料库浮窗：参考卡 / 成品 / 草稿 三 Tab。
 *
 * - 导出是卡片操作（L2 ExportDialog），不是资料分类；
 * - 参考卡：搜索/多选挂载/已挂载可见/单个与全部卸载/打开到工作台/复制/删除/导出；
 * - 成品：打开到当前工作台/复制为新会话/删除确认/导出；
 * - 草稿：继续编辑/删除/导出（转成品经编辑保存流完成）。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryOverlay(
    initialTab: Int,
    currentRefUuids: List<String>,
    onDismiss: () -> Unit,
    onOpenInWorkspace: (CharacterCard) -> Unit,
    onDuplicateAsSession: (CharacterCard) -> Unit,
    onUseAsReference: (List<String>) -> Unit,
    onExportRequest: (CharacterCard) -> Unit,
    onEditCard: (CharacterCard) -> Unit,
    onOpenImport: () -> Unit,
    modifier: Modifier = Modifier
) {
    var tab by rememberSaveable { mutableIntStateOf(initialTab) }
    var deleteConfirm by remember { mutableStateOf<Pair<String, String>?>(null) }
    var actionCard by remember { mutableStateOf<CharacterCard?>(null) }
    var confirmEditCard by remember { mutableStateOf<CharacterCard?>(null) }
    val repoVm: CardRepositoryViewModel = hiltViewModel()
    val finishedVm: FinishedViewModel = hiltViewModel()
    val repoState by repoVm.uiState.collectAsStateWithLifecycle()
    val finishedState by finishedVm.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        repoVm.refresh()
        finishedVm.load()
    }

    OverlayShell(onDismiss = onDismiss, modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize()) {
                // 头部：标题 + 关闭
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp)
                ) {
                    Text(
                        "卡片仓库",
                        style = AuroraTypography.PanelTitle.copy(color = AuroraColorTokens.TextTitle)
                    )
                    Spacer(Modifier.weight(1f))
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "关闭",
                        tint = AuroraColorTokens.TextCaption,
                        modifier = Modifier
                            .size(IndustrialSpacing.IconMedium)
                            .bouncyClickable(onClick = onDismiss)
                    )
                }

                HorizontalDivider(color = AuroraColorTokens.LinePrimary)

                // Tab：卡片仓库 / 成品区 / 草稿
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf("卡片仓库", "成品区", "草稿").forEachIndexed { i, label ->
                        val active = tab == i
                        Surface(
                            color = if (active) AuroraColorTokens.AuroraAccent.copy(alpha = 0.12f)
                            else Color.Transparent,
                            shape = RoundedCornerShape(50),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (active) AuroraColorTokens.AuroraAccent.copy(alpha = 0.5f)
                                else AuroraColorTokens.LinePrimary
                            ),
                            modifier = Modifier.bouncyClickable { tab = i }
                        ) {
                            Text(
                                label,
                                style = AuroraTypography.Caption.copy(
                                    color = if (active) AuroraColorTokens.AuroraAccent
                                    else AuroraColorTokens.TextCaption
                                ),
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                            )
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    if (tab == 0) {
                        Text(
                            "导入",
                            style = AuroraTypography.Caption.copy(color = AuroraColorTokens.AuroraAccent),
                            modifier = Modifier
                                .bouncyClickable(onClick = onOpenImport)
                                .padding(horizontal = 8.dp)
                        )
                    }
                }

                HorizontalDivider(color = AuroraColorTokens.LinePrimary)

                Spacer(Modifier.height(IndustrialSpacing.Tight))

                when (tab) {
                    0 -> ReferenceTab(
                        state = repoState,
                        currentRefUuids = currentRefUuids,
                        onToggleSelect = repoVm::toggleSelection,
                        onSelectAll = repoVm::selectAll,
                        onClearSelection = repoVm::clearSelection,
                        onUseSelected = onUseAsReference,
                        onCardClick = { actionCard = it },
                        onDuplicate = { repoVm.duplicateCard(it.uuid) },
                        onExport = onExportRequest,
                        onDelete = { uuid, name -> deleteConfirm = uuid to name }
                    )
                    1 -> FinishedTab(
                        state = finishedState,
                        onToggleSelect = finishedVm::toggleSelect,
                        onOpenInWorkspace = onOpenInWorkspace,
                        onDuplicateAsSession = onDuplicateAsSession,
                        onExport = onExportRequest,
                        onDelete = { uuid, name -> deleteConfirm = uuid to name }
                    )
                    else -> DraftTab(
                        state = finishedState,
                        onEditCard = onEditCard,
                        onExport = onExportRequest,
                        onDelete = { uuid, name -> deleteConfirm = uuid to name }
                    )
                }
            }
        }

    // 卡片操作菜单：挂载为参考 / 以此卡为底创作 / 删除
    actionCard?.let { card ->
        val mounted = card.uuid in currentRefUuids
        Dialog(
            onDismissRequest = { actionCard = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.4f))
                        .clickable { actionCard = null }
                )
                Surface(
                    color = AuroraColorTokens.Base1,
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .navigationBarsPadding()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            card.name.ifBlank { "未命名角色" },
                            style = AuroraTypography.PanelTitle,
                            color = AuroraColorTokens.TextTitle,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(8.dp))
                        LibraryActionRow(
                            icon = Icons.Filled.Link,
                            title = if (mounted) "取消挂载" else "挂载为参考",
                            subtitle = if (mounted) "从当前创作上下文中移除这张卡"
                            else "AI 创作时参考这张卡的风格",
                            tint = AuroraColorTokens.AuroraAccent
                        ) {
                            actionCard = null
                            onUseAsReference(
                                if (mounted) currentRefUuids - card.uuid
                                else (currentRefUuids + card.uuid).distinct()
                            )
                        }
                        LibraryActionRow(
                            icon = Icons.Filled.Edit,
                            title = "以此卡为底创作",
                            subtitle = "将创建新会话，并用这张卡替换当前工作区的角色卡",
                            tint = AuroraColorTokens.AuroraViolet
                        ) {
                            actionCard = null
                            confirmEditCard = card
                        }
                        LibraryActionRow(
                            icon = Icons.Filled.Delete,
                            title = "删除",
                            subtitle = null,
                            tint = AuroraColorTokens.SignalError,
                            danger = true
                        ) {
                            actionCard = null
                            deleteConfirm = card.uuid to card.name.ifBlank { "未命名角色" }
                        }
                    }
                }
            }
        }
    }

    confirmEditCard?.let { card ->
        AuroraDialog(onDismissRequest = { confirmEditCard = null }) {
            AuroraDialogTitle("以此卡为底创作？")
            AuroraDialogText(
                "将创建新会话，并用「${card.name.ifBlank { "未命名角色" }}」替换当前工作区的角色卡。" +
                    "当前工作区未保存的内容可能丢失。"
            )
            AuroraDialogActions(
                confirmText = "继续创作",
                onConfirm = {
                    confirmEditCard = null
                    onOpenInWorkspace(card)
                },
                onDismiss = { confirmEditCard = null },
                confirmColor = AuroraColorTokens.AuroraViolet
            )
        }
    }

    // 删除确认（浮窗内单发）
    deleteConfirm?.let { (uuid, name) ->
        AuroraDialog(onDismissRequest = { deleteConfirm = null }) {
            AuroraDialogTitle("删除角色卡")
            AuroraDialogText("删除「$name」？不可恢复。")
            AuroraDialogActions(
                confirmText = "删除",
                onConfirm = {
                    deleteConfirm = null
                    if (tab == 0) repoVm.deleteCard(uuid) else if (tab == 1) finishedVm.deleteCard(uuid) else finishedVm.deleteDraft(uuid)
                },
                onDismiss = { deleteConfirm = null },
                confirmColor = AuroraColorTokens.SignalError
            )
        }
    }
}

// CONT_LIB

/** 卡片操作菜单行 */
@Composable
private fun LibraryActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String?,
    tint: Color,
    danger: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = AuroraShapes.Small,
        color = if (danger) AuroraColorTokens.SignalError.copy(alpha = 0.08f)
        else AuroraColorTokens.Base2,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(16.dp)
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
                    title,
                    style = AuroraTypography.Body,
                    color = if (danger) AuroraColorTokens.SignalError
                    else AuroraColorTokens.TextTitle
                )
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = AuroraTypography.Caption,
                        color = AuroraColorTokens.TextCaption
                    )
                }
            }
        }
    }
}

/** 参考卡 Tab：搜索 + 网格 + 多选挂载 + 已挂载可见 + 单个/全部卸载 */
@Composable
private fun ReferenceTab(
    state: com.kirakira.kiracode.ui.config.CardRepositoryUiState,
    currentRefUuids: List<String>,
    onToggleSelect: (String) -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onUseSelected: (List<String>) -> Unit,
    onCardClick: (CharacterCard) -> Unit,
    onDuplicate: (CharacterCard) -> Unit,
    onExport: (CharacterCard) -> Unit,
    onDelete: (uuid: String, name: String) -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    var selectMode by rememberSaveable { mutableStateOf(false) }
    val isCompact = LocalConfiguration.current.screenWidthDp < 600
    val cards = state.cards.filter { it.name.contains(query.trim(), ignoreCase = true) }

    Column(modifier = Modifier.fillMaxSize()) {
        // 搜索 + 多选开关
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = IndustrialSpacing.Overlay)
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("搜索卡片库", style = IndustrialTypography.Tech, color = AuroraColorTokens.TextCaption) },
                singleLine = true,
                shape = AuroraShapes.Small,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(IndustrialSpacing.Item))
            Text(
                if (selectMode) "完成" else "选择",
                style = IndustrialTypography.Tech,
                color = AuroraColorTokens.SignalThinking,
                modifier = Modifier
                    .bouncyClickable {
                        selectMode = !selectMode
                        if (!selectMode) onClearSelection()
                    }
                    .padding(horizontal = IndustrialSpacing.Tight)
            )
        }

        // 多选操作栏（含全部卸载）
        if (selectMode) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = IndustrialSpacing.Overlay, vertical = IndustrialSpacing.Tight)
            ) {
                Text(
                    "已选 ${state.selectedIds.size} 张",
                    style = IndustrialTypography.Tech,
                    color = AuroraColorTokens.TextTech
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "全选",
                    style = IndustrialTypography.Tech,
                    color = AuroraColorTokens.TextBody,
                    modifier = Modifier
                        .bouncyClickable(onClick = onSelectAll)
                        .padding(horizontal = IndustrialSpacing.Tight)
                )
                Text(
                    "全部卸载",
                    style = IndustrialTypography.Tech,
                    color = AuroraColorTokens.SignalError,
                    modifier = Modifier
                        .bouncyClickable { onUseSelected(emptyList()) }
                        .padding(horizontal = IndustrialSpacing.Tight)
                )
                Surface(
                    color = AuroraColorTokens.SignalWarning,
                    shape = AuroraShapes.Small,
                    modifier = Modifier.bouncyClickable {
                        onUseSelected(state.selectedIds.toList())
                        selectMode = false
                    }
                ) {
                    Text(
                        "挂载所选",
                        style = IndustrialTypography.Tech,
                        color = AuroraColorTokens.Base0,
                        modifier = Modifier.padding(
                            horizontal = IndustrialSpacing.Item,
                            vertical = IndustrialSpacing.Tight
                        )
                    )
                }
            }
        } else if (currentRefUuids.isNotEmpty()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = IndustrialSpacing.Overlay, vertical = IndustrialSpacing.Tight)
            ) {
                Text(
                    "已挂载 ${currentRefUuids.size} 张卡片",
                    style = IndustrialTypography.Tech,
                    color = AuroraColorTokens.SignalWarning
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "全部卸载",
                    style = IndustrialTypography.Tech,
                    color = AuroraColorTokens.SignalError,
                    modifier = Modifier
                        .bouncyClickable { onUseSelected(emptyList()) }
                        .padding(horizontal = IndustrialSpacing.Tight)
                )
            }
        }

        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "加载中…",
                    style = IndustrialTypography.Caption,
                    color = AuroraColorTokens.TextCaption
                )
            }
        } else if (cards.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (query.isBlank()) "卡片库为空" else "没有匹配的卡片",
                        style = IndustrialTypography.Caption,
                        color = AuroraColorTokens.TextCaption
                    )
                    Text(
                        "通过「导入」添加 JSON/PNG 角色卡",
                        style = IndustrialTypography.Tech,
                        color = AuroraColorTokens.TextTech
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = if (isCompact) GridCells.Fixed(1) else GridCells.Fixed(2),
                contentPadding = PaddingValues(IndustrialSpacing.Panel),
                verticalArrangement = Arrangement.spacedBy(IndustrialSpacing.Item),
                horizontalArrangement = Arrangement.spacedBy(IndustrialSpacing.Item),
                modifier = Modifier.fillMaxSize()
            ) {
                items(cards, key = { it.uuid }) { card ->
                    LibraryCardCell(
                        card = card,
                        selected = card.uuid in state.selectedIds,
                        mounted = card.uuid in currentRefUuids,
                        selectMode = selectMode,
                        onClick = {
                            if (selectMode) onToggleSelect(card.uuid) else onCardClick(card)
                        },
                        onDuplicate = { onDuplicate(card) },
                        onExport = { onExport(card) },
                        onDelete = { onDelete(card.uuid, card.name.ifBlank { "未命名角色" }) }
                    )
                }
            }
        }
    }
}

/** 成品 Tab：打开到工作台/复制为新会话/导出/删除确认 */
@Composable
private fun FinishedTab(
    state: com.kirakira.kiracode.ui.finished.FinishedUiState,
    onToggleSelect: (String) -> Unit,
    onOpenInWorkspace: (CharacterCard) -> Unit,
    onDuplicateAsSession: (CharacterCard) -> Unit,
    onExport: (CharacterCard) -> Unit,
    onDelete: (uuid: String, name: String) -> Unit
) {
    if (state.isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "加载中…",
                style = IndustrialTypography.Caption,
                color = AuroraColorTokens.TextCaption
            )
        }
    } else if (state.cards.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "还没有成品卡",
                    style = IndustrialTypography.Caption,
                    color = AuroraColorTokens.TextCaption
                )
                Text(
                    "在工作台完成创作后点「存入成品区」",
                    style = IndustrialTypography.Tech,
                    color = AuroraColorTokens.TextTech
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(IndustrialSpacing.Panel),
            verticalArrangement = Arrangement.spacedBy(IndustrialSpacing.Item)
        ) {
            items(state.cards, key = { it.uuid }) { card ->
                LibraryListRow(
                    card = card,
                    mounted = false,
                    onOpen = { onOpenInWorkspace(card) },
                    onDuplicate = { onDuplicateAsSession(card) },
                    onExport = { onExport(card) },
                    onDelete = { onDelete(card.uuid, card.name.ifBlank { "未命名角色" }) }
                )
            }
        }
    }
}

/** 草稿 Tab：继续编辑/导出/删除（转成品经编辑保存流完成） */
@Composable
private fun DraftTab(
    state: com.kirakira.kiracode.ui.finished.FinishedUiState,
    onEditCard: (CharacterCard) -> Unit,
    onExport: (CharacterCard) -> Unit,
    onDelete: (uuid: String, name: String) -> Unit
) {
    if (state.drafts.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "暂无草稿",
                style = IndustrialTypography.Caption,
                color = AuroraColorTokens.TextCaption
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(IndustrialSpacing.Panel),
            verticalArrangement = Arrangement.spacedBy(IndustrialSpacing.Item)
        ) {
            items(state.drafts, key = { it.uuid }) { card ->
                LibraryListRow(
                    card = card,
                    mounted = false,
                    draft = true,
                    onOpen = { onEditCard(card) },
                    onExport = { onExport(card) },
                    onDelete = { onDelete(card.uuid, card.name.ifBlank { "未命名草稿" }) }
                )
            }
        }
    }
}

/** 网格卡片单元：封面 + 名称 + 摘要 + 已挂载标记 + 操作菜单 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LibraryCardCell(
    card: CharacterCard,
    selected: Boolean,
    mounted: Boolean,
    selectMode: Boolean,
    onClick: () -> Unit,
    onDuplicate: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    Surface(
        color = AuroraColorTokens.Base2,
        shape = AuroraShapes.Medium,
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = { menuOpen = true })
    ) {
        Column(modifier = Modifier.padding(IndustrialSpacing.Item)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IndustrialSpacing.CoverThumb)
                    .clip(AuroraShapes.Small)
                    .background(AuroraColorTokens.LinePrimary),
                contentAlignment = Alignment.Center
            ) {
                if (card.avatar != null) {
                    AsyncImage(
                            model = resolveCoverCoilModel(card.avatar),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text(
                        card.name.take(1).ifBlank { "?" },
                        style = IndustrialTypography.Metric,
                        color = AuroraColorTokens.TextTech
                    )
                }
                if (mounted) {
                    Surface(
                        color = AuroraColorTokens.SignalWarning,
                        shape = AuroraShapes.Small,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(IndustrialSpacing.Tight)
                    ) {
                        Text(
                            "已挂载",
                            style = IndustrialTypography.Marker,
                            color = AuroraColorTokens.Base0,
                            modifier = Modifier.padding(horizontal = IndustrialSpacing.Tight)
                        )
                    }
                }
                if (selectMode && selected) {
                    Icon(
                        Icons.Filled.Done,
                        contentDescription = "已选",
                        tint = AuroraColorTokens.SignalWarning,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(IndustrialSpacing.Tight)
                    )
                }
            }
            Spacer(Modifier.height(IndustrialSpacing.Tight))
            Text(
                card.name.ifBlank { "未命名角色" },
                style = IndustrialTypography.Caption,
                color = AuroraColorTokens.TextTitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                card.description.ifBlank { "（无描述）" }.take(50),
                style = IndustrialTypography.Tech,
                color = AuroraColorTokens.TextCaption,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "更多",
                    style = IndustrialTypography.Marker,
                    color = AuroraColorTokens.TextTech,
                    modifier = Modifier
                        .bouncyClickable { menuOpen = true }
                        .padding(vertical = IndustrialSpacing.Tight)
                )
                Box {
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("复制副本", style = IndustrialTypography.Caption, color = AuroraColorTokens.TextBody) },
                            onClick = {
                                menuOpen = false
                                onDuplicate()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("导出", style = IndustrialTypography.Caption, color = AuroraColorTokens.TextBody) },
                            onClick = {
                                menuOpen = false
                                onExport()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("删除", style = IndustrialTypography.Caption, color = AuroraColorTokens.SignalError) },
                            onClick = {
                                menuOpen = false
                                onDelete()
                            }
                        )
                    }
                }
            }
        }
    }
}

/** 列表行：封面 + 名称 + 摘要 + 打开/复制为新会话/导出/删除 */
@Composable
private fun LibraryListRow(
    card: CharacterCard,
    mounted: Boolean,
    draft: Boolean = false,
    onOpen: () -> Unit,
    onDuplicate: (() -> Unit)? = null,
    onExport: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        color = AuroraColorTokens.Base2,
        shape = AuroraShapes.Medium,
        modifier = Modifier
            .fillMaxWidth()
            .bouncyClickable(onClick = onOpen)
    ) {
        Column(modifier = Modifier.padding(IndustrialSpacing.Panel)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(IndustrialSpacing.AvatarThumb)
                        .clip(CircleShape)
                        .background(AuroraColorTokens.LinePrimary),
                    contentAlignment = Alignment.Center
                ) {
                    if (card.avatar != null) {
                        AsyncImage(
                        model = resolveCoverCoilModel(card.avatar),
                            contentDescription = null,
                            modifier = Modifier.size(IndustrialSpacing.AvatarThumb)
                        )
                    } else {
                        Text(
                            card.name.take(1).ifBlank { "?" },
                            style = IndustrialTypography.Caption,
                            color = AuroraColorTokens.TextTech
                        )
                    }
                }
                Spacer(Modifier.width(IndustrialSpacing.Item))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        card.name.ifBlank { if (draft) "未命名草稿" else "未命名角色" },
                        style = IndustrialTypography.Caption,
                        color = AuroraColorTokens.TextTitle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        listOfNotNull(
                            "v${card.specVersion}",
                            card.characterBook?.entries?.size?.let { "世界书${it}条" },
                            card.regexScripts.size.takeIf { it > 0 }?.let { "正则${it}条" }
                        ).joinToString(" · "),
                        style = IndustrialTypography.Tech,
                        color = AuroraColorTokens.TextTech,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    if (draft) "继续编辑" else "打开",
                    style = IndustrialTypography.Tech,
                    color = AuroraColorTokens.SignalThinking
                )
                if (onDuplicate != null) {
                    Spacer(Modifier.width(IndustrialSpacing.Item))
                    Text(
                        "复制为新会话",
                        style = IndustrialTypography.Tech,
                        color = AuroraColorTokens.TextBody,
                        modifier = Modifier.bouncyClickable(onClick = onDuplicate)
                    )
                }
                Spacer(Modifier.width(IndustrialSpacing.Item))
                Text(
                    "导出",
                    style = IndustrialTypography.Tech,
                    color = AuroraColorTokens.TextBody,
                    modifier = Modifier.bouncyClickable(onClick = onExport)
                )
                Spacer(Modifier.width(IndustrialSpacing.Item))
                Icon(
                    Icons.Filled.DeleteOutline,
                    contentDescription = "删除",
                    tint = AuroraColorTokens.SignalError,
                    modifier = Modifier
                        .size(IndustrialSpacing.IconSmall)
                        .bouncyClickable(onClick = onDelete)
                )
            }
            Spacer(Modifier.height(IndustrialSpacing.Tight))
            Text(
                card.description.ifBlank { "（无描述）" }.take(80),
                style = IndustrialTypography.Tech,
                color = AuroraColorTokens.TextCaption,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
