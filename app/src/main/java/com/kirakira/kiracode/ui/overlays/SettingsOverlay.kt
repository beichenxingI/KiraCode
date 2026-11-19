package com.kirakira.kiracode.ui.overlays

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kirakira.kiracode.domain.model.ModelConfig
import com.kirakira.kiracode.domain.model.RegexGenerationMode
import com.kirakira.kiracode.domain.model.TrainDimension
import com.kirakira.kiracode.domain.model.TrainPersonaConfig
import com.kirakira.kiracode.domain.model.TrainPhase
import com.kirakira.kiracode.ui.config.ModelConfigViewModel
import com.kirakira.kiracode.ui.common.AuroraDialog
import com.kirakira.kiracode.ui.common.AuroraDialogActions
import com.kirakira.kiracode.ui.common.AuroraDialogTextField
import com.kirakira.kiracode.ui.common.AuroraDialogTitle
import com.kirakira.kiracode.ui.theme.AuroraColorTokens
import com.kirakira.kiracode.ui.theme.AuroraShapes
import com.kirakira.kiracode.ui.theme.AuroraTypography
import com.kirakira.kiracode.ui.theme.IndustrialSpacing
import com.kirakira.kiracode.ui.theme.IndustrialTypography
import com.kirakira.kiracode.ui.theme.bouncyClickable
import com.kirakira.kiracode.ui.workspace.WorkspaceViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 设置浮窗（唯一设置入口）：LLM 方案 / 运行参数 / 灵魂 / 提示词 / 诊断 / 关于。
 *
 * - LLM 方案：多配置组（新增/编辑/复制/删除/设为当前/测试连接/温度/MaxTokens）；
 * - 运行参数：联网搜索/制卡师/自动总结/上下文窗口/总结阈值/思考强度档位与探测；
 * - 灵魂：灵魂列表（启用/删除）+ 训练配置 + 选卡训练 + 训练进度 + 保存；
 * - 提示词：多套自定义提示词（启用/编辑/删除/新建）；
 * - 诊断：运行日志（刷新/复制/清空/级别筛选）；
 * - 关于：应用信息/源码/协议/检查更新。
 *
 * 即时保存型浮窗：所有设置即改即存（DataStore），关闭无需确认。
 */
private data class SettingsTabDef(val label: String, val icon: ImageVector)

private val SETTINGS_TABS = listOf(
    SettingsTabDef("LLM 方案", Icons.Outlined.Memory),
    SettingsTabDef("运行参数", Icons.Outlined.Tune),
    SettingsTabDef("灵魂", Icons.Outlined.Psychology),
    SettingsTabDef("提示词", Icons.Outlined.EditNote),
    SettingsTabDef("诊断", Icons.Outlined.BugReport),
    SettingsTabDef("关于", Icons.Outlined.Info)
)

@Composable
fun SettingsOverlay(
    initialTab: Int,
    viewModel: WorkspaceViewModel,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var tab by rememberSaveable { mutableIntStateOf(initialTab) }
    val isCompact = LocalConfiguration.current.screenWidthDp < 600

    OverlayShell(onDismiss = onDismiss, modifier = modifier) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "设置",
                    style = AuroraTypography.PanelTitle.copy(color = AuroraColorTokens.TextTitle)
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "关闭",
                        tint = AuroraColorTokens.TextCaption
                    )
                }
            }

            HorizontalDivider(color = AuroraColorTokens.LinePrimary)

            if (isCompact) {
                SettingsTabBar(selectedIndex = tab, onSelect = { tab = it })
                HorizontalDivider(color = AuroraColorTokens.LinePrimary)
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    SettingsContent(tab = tab, viewModel = viewModel)
                }
            } else {
                Row(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    SettingsSidebar(selectedIndex = tab, onSelect = { tab = it })
                    VerticalDivider(color = AuroraColorTokens.LinePrimary)
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        SettingsContent(tab = tab, viewModel = viewModel)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsTabBar(
    selectedIndex: Int,
    onSelect: (Int) -> Unit
) {
    LazyRow(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = 16.dp,
            vertical = 10.dp
        ),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        itemsIndexed(SETTINGS_TABS) { i, tab ->
            val selected = i == selectedIndex
            Surface(
                shape = RoundedCornerShape(50),
                color = if (selected) AuroraColorTokens.AuroraAccent.copy(alpha = 0.12f)
                else Color.Transparent,
                border = BorderStroke(
                    1.dp,
                    if (selected) AuroraColorTokens.AuroraAccent.copy(alpha = 0.5f)
                    else AuroraColorTokens.LinePrimary
                ),
                modifier = Modifier.clickable { onSelect(i) }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = null,
                        tint = if (selected) AuroraColorTokens.AuroraAccent
                        else AuroraColorTokens.TextCaption,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = tab.label,
                        style = AuroraTypography.Caption.copy(
                            color = if (selected) AuroraColorTokens.AuroraAccent
                            else AuroraColorTokens.TextCaption
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsSidebar(
    selectedIndex: Int,
    onSelect: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .width(176.dp)
            .fillMaxHeight()
            .background(AuroraColorTokens.Base0)
            .padding(horizontal = 8.dp, vertical = 12.dp)
    ) {
        SETTINGS_TABS.forEachIndexed { i, tab ->
            val selected = i == selectedIndex
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (selected) AuroraColorTokens.AuroraAccent.copy(alpha = 0.12f)
                        else Color.Transparent
                    )
                    .clickable { onSelect(i) }
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Icon(
                    imageVector = tab.icon,
                    contentDescription = null,
                    tint = if (selected) AuroraColorTokens.AuroraAccent
                    else AuroraColorTokens.TextCaption,
                    modifier = Modifier.size(17.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = tab.label,
                    style = AuroraTypography.Body.copy(
                        color = if (selected) AuroraColorTokens.AuroraAccent
                        else AuroraColorTokens.TextCaption
                    )
                )
            }
        }
    }
}

@Composable
private fun SettingsContent(tab: Int, viewModel: WorkspaceViewModel) {
    when (tab) {
        0 -> LlmConfigTab()
        1 -> RunParamsTab(viewModel)
        2 -> PersonaTab(viewModel)
        3 -> PromptTab(viewModel)
        4 -> DiagnosticsTab()
        else -> AboutTab()
    }
}

/** LLM 方案 Tab：多配置组管理（唯一配置路径；旧单 Key 为回退，不再提供写入口） */
@Composable
private fun LlmConfigTab() {
    val vm: ModelConfigViewModel = hiltViewModel()
    val state by vm.uiState.collectAsStateWithLifecycle()
    val temperature by vm.temperature.collectAsStateWithLifecycle(initialValue = 0.8f)
    val maxTokens by vm.maxTokens.collectAsStateWithLifecycle(initialValue = 4096)
    var editTarget by remember { mutableStateOf<ModelConfig?>(null) }
    var creating by remember { mutableStateOf(false) }
    var menuTarget by remember { mutableStateOf<ModelConfig?>(null) }

    LaunchedEffect(state.error) {
        if (state.error != null) {
            delay(3000)
            vm.clearError()
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = IndustrialSpacing.Overlay,
            vertical = IndustrialSpacing.Compact
        ),
        verticalArrangement = Arrangement.spacedBy(IndustrialSpacing.Item)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "模型配置组",
                    style = IndustrialTypography.SectionLabel,
                    color = AuroraColorTokens.TextCaption
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "新增",
                    style = IndustrialTypography.Tech,
                    color = AuroraColorTokens.SignalThinking,
                    modifier = Modifier
                        .bouncyClickable { creating = true }
                        .padding(horizontal = IndustrialSpacing.Tight)
                )
            }
        }
        if (state.configs.isEmpty()) {
            item {
                Text(
                    "暂无配置组：新增一个配置组并测试连接",
                    style = IndustrialTypography.Caption,
                    color = AuroraColorTokens.TextCaption
                )
            }
        }
        state.error?.let { err ->
            item {
                Surface(
                    color = AuroraColorTokens.SignalError.copy(alpha = 0.12f),
                    shape = AuroraShapes.Small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        err,
                        style = AuroraTypography.Caption.copy(color = AuroraColorTokens.SignalError),
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
        }
        items(state.configs, key = { it.id.ifBlank { it.hashCode().toString() } }) { config ->
            val active = config.id == state.activeId
            var modelsOpen by remember(config.id) { mutableStateOf(false) }
            var modelQuery by remember(config.id) { mutableStateOf("") }
            Surface(
                color = AuroraColorTokens.Base2,
                shape = AuroraShapes.Small,
                modifier = Modifier
                    .fillMaxWidth()
                    .bouncyClickable {
                        if (config.baseUrl.isNotBlank() && config.apiKey.isNotBlank()) {
                            vm.setActiveConfig(config.id)
                        } else {
                            editTarget = config
                        }
                    }
            ) {
                Column(
                    modifier = Modifier
                        .padding(IndustrialSpacing.Item)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            config.name.ifBlank { "未命名配置组" },
                            style = IndustrialTypography.Caption,
                            color = AuroraColorTokens.TextTitle,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        if (active) {
                            Text(
                                "当前",
                                style = IndustrialTypography.Marker,
                                color = AuroraColorTokens.SignalWarning
                            )
                            Spacer(Modifier.width(IndustrialSpacing.Item))
                        }
                        Box {
                            Icon(
                                Icons.Filled.MoreVert,
                                contentDescription = "配置组操作",
                                tint = AuroraColorTokens.TextCaption,
                                modifier = Modifier
                                    .size(IndustrialSpacing.IconSmall)
                                    .bouncyClickable { menuTarget = config }
                            )
                            DropdownMenu(expanded = menuTarget == config, onDismissRequest = { menuTarget = null }) {
                                DropdownMenuItem(
                                    text = { Text("测试连接（拉取模型）", style = IndustrialTypography.Caption, color = AuroraColorTokens.TextBody) },
                                    onClick = {
                                        menuTarget = null
                                        vm.fetchModels(config.id)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("编辑", style = IndustrialTypography.Caption, color = AuroraColorTokens.TextBody) },
                                    onClick = {
                                        menuTarget = null
                                        editTarget = config
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("设为当前", style = IndustrialTypography.Caption, color = AuroraColorTokens.TextBody) },
                                    onClick = {
                                        menuTarget = null
                                        vm.setActiveConfig(config.id)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("删除", style = IndustrialTypography.Caption, color = AuroraColorTokens.SignalError) },
                                    onClick = {
                                        menuTarget = null
                                        vm.deleteConfig(config.id)
                                    }
                                )
                            }
                        }
                    }
                    Text(
                        config.baseUrl.ifBlank { "未配置 Base URL" },
                        style = IndustrialTypography.Tech,
                        color = AuroraColorTokens.TextTech,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    // 模型行：数量 + 当前使用 + 展开/收起选择列表
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .bouncyClickable(enabled = config.models.isNotEmpty()) { modelsOpen = !modelsOpen }
                    ) {
                        Text(
                            buildString {
                                append("模型 ${config.models.size} 个")
                                config.selectedModel?.let { append(" · 使用 ${it}") }
                                if (state.fetchInProgress == config.id) append(" · 连接中…")
                            },
                            style = IndustrialTypography.Tech,
                            color = if (state.fetchInProgress == config.id) AuroraColorTokens.SignalWarning
                            else AuroraColorTokens.TextTech,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (config.models.isNotEmpty()) {
                            Spacer(Modifier.width(IndustrialSpacing.Tight))
                            Icon(
                                imageVector = if (modelsOpen) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                contentDescription = if (modelsOpen) "收起模型列表" else "展开模型列表",
                                tint = AuroraColorTokens.TextCaption,
                                modifier = Modifier.size(IndustrialSpacing.IconSmall)
                            )
                        }
                    }

                    // 模型选择列表（可搜索；finite 高度 + verticalScroll，安全）
                    if (modelsOpen && config.models.isNotEmpty()) {
                        Spacer(Modifier.height(IndustrialSpacing.Tight))
                        OutlinedTextField(
                            value = modelQuery,
                            onValueChange = { modelQuery = it },
                            placeholder = { Text("搜索模型…", style = IndustrialTypography.Tech, color = AuroraColorTokens.TextCaption) },
                            singleLine = true,
                            shape = AuroraShapes.Small,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(IndustrialSpacing.Tight))
                        val filteredModels = remember(config.models, modelQuery) {
                            if (modelQuery.isBlank()) config.models
                            else config.models.filter { it.contains(modelQuery.trim(), ignoreCase = true) }
                        }
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 240.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            if (filteredModels.isEmpty()) {
                                Text(
                                    "没有匹配的模型",
                                    style = IndustrialTypography.Tech,
                                    color = AuroraColorTokens.TextTech,
                                    modifier = Modifier.padding(vertical = IndustrialSpacing.Compact)
                                )
                            }
                            filteredModels.forEach { model ->
                                val selected = model == config.selectedModel
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            vm.selectModel(config.id, model)
                                        }
                                        .background(
                                            if (selected) AuroraColorTokens.SignalWarning.copy(alpha = 0.1f)
                                            else Color.Transparent
                                        )
                                        .padding(
                                            horizontal = IndustrialSpacing.Tight,
                                            vertical = IndustrialSpacing.Tight
                                        )
                                ) {
                                    if (selected) {
                                        Icon(
                                            Icons.Filled.Check,
                                            contentDescription = null,
                                            tint = AuroraColorTokens.SignalWarning,
                                            modifier = Modifier.size(IndustrialSpacing.IconSmall)
                                        )
                                        Spacer(Modifier.width(IndustrialSpacing.Tight))
                                    }
                                    Text(
                                        text = model,
                                        style = IndustrialTypography.Tech,
                                        color = if (selected) AuroraColorTokens.TextTitle
                                        else AuroraColorTokens.TextBody,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(IndustrialSpacing.Tight))
                        Text(
                            "拉取模型",
                            style = IndustrialTypography.Tech,
                            color = if (state.fetchInProgress == config.id) AuroraColorTokens.SignalNeutral
                            else AuroraColorTokens.SignalThinking,
                            modifier = Modifier
                                .bouncyClickable(enabled = state.fetchInProgress == null) { vm.fetchModels(config.id) }
                                .padding(vertical = IndustrialSpacing.Tight)
                        )
                    } else if (modelsOpen && config.models.isEmpty()) {
                        Spacer(Modifier.height(IndustrialSpacing.Tight))
                        Text(
                            "尚未拉取模型列表",
                            style = IndustrialTypography.Tech,
                            color = AuroraColorTokens.TextTech,
                            modifier = Modifier.padding(vertical = IndustrialSpacing.Tight)
                        )
                        Text(
                            "拉取模型",
                            style = IndustrialTypography.Tech,
                            color = if (state.fetchInProgress == config.id) AuroraColorTokens.SignalNeutral
                            else AuroraColorTokens.SignalThinking,
                            modifier = Modifier
                                .bouncyClickable(enabled = state.fetchInProgress == null) { vm.fetchModels(config.id) }
                                .padding(vertical = IndustrialSpacing.Tight)
                        )
                    }
                }
            }
        }
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = IndustrialSpacing.Item),
                verticalArrangement = Arrangement.spacedBy(IndustrialSpacing.Tight)
            ) {
                Text(
                    "默认参数（全局回退）",
                    style = IndustrialTypography.SectionLabel,
                    color = AuroraColorTokens.TextCaption
                )
                Text(
                    "温度 ${"%.1f".format(temperature)}",
                    style = IndustrialTypography.Tech,
                    color = AuroraColorTokens.TextTech
                )
                androidx.compose.material3.Slider(
                    value = temperature,
                    onValueChange = { vm.setTemperature(it) },
                    valueRange = 0.0f..1.5f
                )
                Text(
                    "生成卡片最大 Token 数  $maxTokens",
                    style = IndustrialTypography.Tech,
                    color = AuroraColorTokens.TextTech
                )
                androidx.compose.material3.Slider(
                    value = maxTokens.toFloat(),
                    onValueChange = { vm.setMaxTokens(it.toInt()) },
                    valueRange = 512f..32768f,
                    steps = 0
                )
                Text(
                    "仅限制角色卡生成请求的单次输出长度，不影响对话上下文窗口",
                    style = AuroraTypography.Caption.copy(color = AuroraColorTokens.TextCaption)
                )
            }
        }
    }

    // 新增/编辑配置组弹窗
    if (creating || editTarget != null) {
        ConfigEditDialog(
            initial = editTarget,
            onDismiss = {
                creating = false
                editTarget = null
            },
            onConfirm = { name, baseUrl, apiKey ->
                val target = editTarget
                if (target != null) {
                    vm.updateConfig(target.copy(name = name, baseUrl = baseUrl, apiKey = apiKey))
                } else {
                    vm.addConfig(name, baseUrl, apiKey)
                }
                creating = false
                editTarget = null
            }
        )
    }
}

/** 配置组编辑弹窗 */
@Composable
private fun ConfigEditDialog(
    initial: ModelConfig?,
    onDismiss: () -> Unit,
    onConfirm: (name: String, baseUrl: String, apiKey: String) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var baseUrl by remember { mutableStateOf(initial?.baseUrl ?: "") }
    var apiKey by remember { mutableStateOf(initial?.apiKey ?: "") }

    AuroraDialog(
        onDismissRequest = onDismiss,
        maxWidth = 480.dp,
        scrollable = true
    ) {
        AuroraDialogTitle(if (initial == null) "新增配置组" else "编辑配置组")
        AuroraDialogTextField(
            value = name,
            onValueChange = { name = it },
            label = "名称",
            singleLine = true
        )
        AuroraDialogTextField(
            value = baseUrl,
            onValueChange = { baseUrl = it },
            label = "Base URL",
            singleLine = true
        )
        AuroraDialogTextField(
            value = apiKey,
            onValueChange = { apiKey = it },
            label = "API Key",
            singleLine = true
        )
        AuroraDialogActions(
            confirmText = "保存",
            onConfirm = {
                if (name.isNotBlank() && baseUrl.isNotBlank()) {
                    onConfirm(name.trim(), baseUrl.trim(), apiKey.trim())
                }
            },
            onDismiss = onDismiss,
            confirmEnabled = name.isNotBlank() && baseUrl.isNotBlank()
        )
    }
}

/** 运行参数 Tab：联网搜索/制卡师/自动总结/上下文窗口/总结阈值/思考强度 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RunParamsTab(viewModel: WorkspaceViewModel) {
    val ws by viewModel.workspace.collectAsStateWithLifecycle()
    val reasoningState = viewModel.reasoningStateFor(ws.activeModelName)

    LaunchedEffect(Unit) {
        viewModel.ensureReasoningProbe()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = IndustrialSpacing.Overlay, vertical = IndustrialSpacing.Compact),
        verticalArrangement = Arrangement.spacedBy(IndustrialSpacing.Item)
    ) {
        SettingsSwitchRow("联网搜索", "AI 按需检索外部资料", ws.webSearchEnabled) {
            viewModel.setWebSearchEnabled(it)
        }
        SettingsSwitchRow(
            "自洽审查",
            "AI 会主动检查角色卡的设定一致性、角色行为合理性、时间线与数值准确性。发现潜在冲突时会提出异议，但不会阻止你的创作。",
            ws.cardMasterEnabled,
            descriptionMaxLines = 3
        ) {
            viewModel.setCardMasterEnabled(it)
        }
        SettingsSwitchRow("自动总结", "上下文接近窗口上限时自动压缩", ws.autoSummarize) {
            viewModel.setAutoSummarize(it)
        }

        Column(verticalArrangement = Arrangement.spacedBy(IndustrialSpacing.Tight)) {
            Text(
                "正则生成模式",
                style = IndustrialTypography.SectionLabel,
                color = AuroraColorTokens.TextTitle
            )
            Text(
                "控制新生成或重构的正则；对应输出协议自动内嵌到 description 末尾。",
                style = IndustrialTypography.Tech,
                color = AuroraColorTokens.TextCaption
            )
            RegexGenerationMode.entries.forEach { mode ->
                val active = ws.regexGenerationMode == mode
                Surface(
                    color = if (active) AuroraColorTokens.SignalWarning.copy(alpha = 0.16f) else AuroraColorTokens.Base2,
                    shape = AuroraShapes.Small,
                    border = BorderStroke(
                        1.dp,
                        if (active) AuroraColorTokens.SignalWarning.copy(alpha = 0.65f) else AuroraColorTokens.LinePrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .bouncyClickable { viewModel.setRegexGenerationMode(mode) }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(
                            horizontal = IndustrialSpacing.Item,
                            vertical = IndustrialSpacing.Tight
                        )
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                mode.label,
                                style = IndustrialTypography.Caption,
                                color = if (active) AuroraColorTokens.SignalWarning else AuroraColorTokens.TextTitle
                            )
                            Text(
                                mode.description,
                                style = IndustrialTypography.Tech,
                                color = AuroraColorTokens.TextCaption
                            )
                        }
                        if (active) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = "当前模式",
                                tint = AuroraColorTokens.SignalWarning,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }

        // 上下文窗口
        Column(verticalArrangement = Arrangement.spacedBy(IndustrialSpacing.Tight)) {
            Text(
                "上下文窗口 ${ws.contextWindowTokens} tokens",
                style = IndustrialTypography.Tech,
                color = AuroraColorTokens.TextTech
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(IndustrialSpacing.Item),
                verticalArrangement = Arrangement.spacedBy(IndustrialSpacing.Tight),
                modifier = Modifier.fillMaxWidth()
            ) {
                listOf(32000, 64000, 128000, 200000, 512000, 1000000, 2000000).forEach { preset ->
                    val active = ws.contextWindowTokens == preset
                    Surface(
                        color = if (active) AuroraColorTokens.SignalWarning else AuroraColorTokens.Base2,
                        shape = AuroraShapes.Small,
                        modifier = Modifier.bouncyClickable { viewModel.setContextWindowTokens(preset) }
                    ) {
                        Text(
                            formatContextLength(preset),
                            style = IndustrialTypography.Tech,
                            color = if (active) AuroraColorTokens.Base0 else AuroraColorTokens.TextBody,
                            modifier = Modifier.padding(
                                horizontal = IndustrialSpacing.Item,
                                vertical = IndustrialSpacing.Tight
                            )
                        )
                    }
                }
            }
        }

        // 总结阈值
        Column(verticalArrangement = Arrangement.spacedBy(IndustrialSpacing.Tight)) {
            Text(
                "总结阈值 ${"%.0f".format(ws.summarizeThreshold * 100)}%",
                style = IndustrialTypography.Tech,
                color = AuroraColorTokens.TextTech
            )
            androidx.compose.material3.Slider(
                value = ws.summarizeThreshold,
                onValueChange = { viewModel.setSummarizeThreshold(it) },
                valueRange = 0.5f..0.95f
            )
        }

        // 思考强度档位（分段，反映支持状态）
        Column(verticalArrangement = Arrangement.spacedBy(IndustrialSpacing.Tight)) {
            val stateLabel = when {
                reasoningState.probing -> "探测中"
                !reasoningState.supported -> "当前模型不支持"
                else -> reasoningState.familyLabel
            }
            Text(
                "思考强度（${stateLabel}）",
                style = IndustrialTypography.Tech,
                color = AuroraColorTokens.TextTech
            )
            Row(horizontalArrangement = Arrangement.spacedBy(IndustrialSpacing.Item)) {
                listOf(
                    null to "自动",
                    "low" to "低",
                    "medium" to "中",
                    "high" to "高"
                ).forEach { (value, label) ->
                    val active = ws.reasoningEffort == value
                    Surface(
                        color = if (active) AuroraColorTokens.SignalWarning else AuroraColorTokens.Base2,
                        shape = AuroraShapes.Small,
                        modifier = Modifier.bouncyClickable(
                            enabled = reasoningState.supported
                        ) { viewModel.setReasoningEffort(value) }
                    ) {
                        Text(
                            label,
                            style = IndustrialTypography.Tech,
                            color = if (active) AuroraColorTokens.Base0
                            else if (!reasoningState.supported) AuroraColorTokens.SignalNeutral
                            else AuroraColorTokens.TextBody,
                            modifier = Modifier.padding(
                                horizontal = IndustrialSpacing.Item,
                                vertical = IndustrialSpacing.Tight
                            )
                        )
                    }
                }
            }
        }

        HorizontalDivider(color = AuroraColorTokens.LinePrimary)
        SettingsSwitchRow(
            "不限制工具调用次数",
            "适合大型角色卡重构。AI 最多执行 200 轮，不再触发预算决策中断。",
            ws.unlimitedToolCalls
        ) { viewModel.setUnlimitedToolCalls(it) }
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    descriptionMaxLines: Int = 2,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = IndustrialTypography.Caption,
                color = AuroraColorTokens.TextTitle
            )
            Text(
                description,
                style = IndustrialTypography.Tech,
                color = AuroraColorTokens.TextCaption,
                maxLines = descriptionMaxLines,
                overflow = TextOverflow.Ellipsis
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** 灵魂 Tab：灵魂列表（启用/删除）+ 训练配置 + 选卡训练 + 训练进度 + 保存 */
@Composable
private fun PersonaTab(viewModel: WorkspaceViewModel) {
    val trainVm: com.kirakira.kiracode.ui.trainpersona.TrainPersonaViewModel = hiltViewModel()
    val personas by viewModel.personas.collectAsStateWithLifecycle()
    val trainState by trainVm.trainState.collectAsStateWithLifecycle()
    val resultPrompt by trainVm.resultPrompt.collectAsStateWithLifecycle()
    val cards by trainVm.cards.collectAsStateWithLifecycle()
    val selected by trainVm.selected.collectAsStateWithLifecycle()
    val query by trainVm.query.collectAsStateWithLifecycle()
    val config by trainVm.config.collectAsStateWithLifecycle()
    val modelConfigs by trainVm.modelConfigs.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = IndustrialSpacing.Overlay, vertical = IndustrialSpacing.Compact),
        verticalArrangement = Arrangement.spacedBy(IndustrialSpacing.Item)
    ) {
        TrainingConfigPanel(
            config = config,
            modelConfigs = modelConfigs,
            onConfigChange = trainVm::updateConfig
        )

        Spacer(Modifier.height(IndustrialSpacing.Tight))

        Text(
            "已保存灵魂",
            style = IndustrialTypography.SectionLabel,
            color = AuroraColorTokens.TextCaption
        )
        if (personas.isEmpty()) {
            Text(
                "暂无灵魂：选参考卡训练后保存",
                style = IndustrialTypography.Tech,
                color = AuroraColorTokens.TextTech
            )
        }
        personas.forEach { persona ->
            val active = persona.id == viewModel.activePersonaId.collectAsStateWithLifecycle(initialValue = null).value
            Surface(
                color = AuroraColorTokens.Base2,
                shape = AuroraShapes.Small,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(IndustrialSpacing.Item)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            persona.name,
                            style = IndustrialTypography.Caption,
                            color = AuroraColorTokens.TextTitle,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        if (active) {
                            Text(
                                "当前",
                                style = IndustrialTypography.Marker,
                                color = AuroraColorTokens.SignalWarning
                            )
                            Spacer(Modifier.width(IndustrialSpacing.Item))
                        }
                        Text(
                            "启用",
                            style = IndustrialTypography.Tech,
                            color = AuroraColorTokens.SignalThinking,
                            modifier = Modifier.bouncyClickable { viewModel.activatePersona(persona.id) }
                        )
                        Spacer(Modifier.width(IndustrialSpacing.Item))
                        Icon(
                            Icons.Filled.DeleteOutline,
                            contentDescription = "删除灵魂",
                            tint = AuroraColorTokens.SignalError,
                            modifier = Modifier
                                .size(IndustrialSpacing.IconSmall)
                                .bouncyClickable { viewModel.deletePersona(persona.id) }
                        )
                    }
                    if (persona.description.isNotBlank()) {
                        Text(
                            persona.description,
                            style = IndustrialTypography.Tech,
                            color = AuroraColorTokens.TextCaption,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // 训练区
        Column(verticalArrangement = Arrangement.spacedBy(IndustrialSpacing.Item)) {
            Text(
                "训练新灵魂",
                style = IndustrialTypography.SectionLabel,
                color = AuroraColorTokens.TextCaption
            )
            OutlinedTextField(
                value = query,
                onValueChange = { trainVm.setQuery(it) },
                placeholder = { Text("搜索参考卡", style = IndustrialTypography.Tech, color = AuroraColorTokens.TextCaption) },
                singleLine = true,
                shape = AuroraShapes.Small,
                modifier = Modifier.fillMaxWidth()
            )
            val filtered = remember(cards, query) {
                if (query.isBlank()) cards.take(12) else cards.filter {
                    it.name.contains(query.trim(), ignoreCase = true)
                }.take(12)
            }
            filtered.forEach { card ->
                val isSelected = card.uuid in selected
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .bouncyClickable { trainVm.toggleSelect(card.uuid) }
                ) {
                    Box(
                        Modifier
                            .size(IndustrialSpacing.IconSmall)
                            .background(
                                if (isSelected) AuroraColorTokens.SignalWarning
                                else AuroraColorTokens.LinePrimary,
                                AuroraShapes.Small
                            )
                    )
                    Spacer(Modifier.width(IndustrialSpacing.Item))
                    Text(
                        card.name.ifBlank { "未命名参考卡" },
                        style = IndustrialTypography.Caption,
                        color = AuroraColorTokens.TextBody,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            when (val ts = trainState) {
                is com.kirakira.kiracode.ui.trainpersona.TrainUiState.Running -> {
                    Text(
                        text = when (val phase = ts.phase) {
                            is TrainPhase.AnalyzingDimension ->
                                "${phase.dimension.label} · ${phase.action}（第 ${phase.round}/5 轮）"
                            TrainPhase.Synthesizing -> "合成灵魂提示词"
                            TrainPhase.Complete -> "完成"
                        },
                        style = IndustrialTypography.Tech,
                        color = AuroraColorTokens.SignalWarning
                    )
                    LinearProgressIndicator(
                        progress = { ts.progress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "取消训练",
                        style = IndustrialTypography.Tech,
                        color = AuroraColorTokens.SignalError,
                        modifier = Modifier.bouncyClickable(onClick = trainVm::cancelTraining)
                    )
                }
                is com.kirakira.kiracode.ui.trainpersona.TrainUiState.Error -> {
                    Text(
                        "训练失败：${ts.message}",
                        style = IndustrialTypography.Tech,
                        color = AuroraColorTokens.SignalError
                    )
                    if (ts.partialResults.isNotEmpty()) {
                        Text(
                            "已完成 ${ts.partialResults.size} 个维度：${ts.partialResults.keys.joinToString("、") { it.label }}",
                            style = IndustrialTypography.Tech,
                            color = AuroraColorTokens.TextCaption
                        )
                    }
                    Text(
                        "重试",
                        style = IndustrialTypography.Tech,
                        color = AuroraColorTokens.SignalThinking,
                        modifier = Modifier.bouncyClickable { trainVm.startTraining() }
                    )
                }
                is com.kirakira.kiracode.ui.trainpersona.TrainUiState.Result -> {
                    Text(
                        "训练完成",
                        style = IndustrialTypography.Tech,
                        color = AuroraColorTokens.SignalSuccess
                    )
                }
                else -> {
                    Text(
                        "开始训练",
                        style = IndustrialTypography.Tech,
                        color = if (selected.isNotEmpty()) AuroraColorTokens.SignalThinking
                        else AuroraColorTokens.SignalNeutral,
                        modifier = Modifier.bouncyClickable(enabled = selected.isNotEmpty()) { trainVm.startTraining() }
                    )
                }
            }
            if (resultPrompt != null) {
                Text(
                    "灵魂提示词预览",
                    style = IndustrialTypography.SectionLabel,
                    color = AuroraColorTokens.TextCaption
                )
                Text(
                    resultPrompt.orEmpty().take(400),
                    style = IndustrialTypography.Tech,
                    color = AuroraColorTokens.TextCaption
                )
                var personaName by remember(resultPrompt) { mutableStateOf("") }
                OutlinedTextField(
                    value = personaName,
                    onValueChange = { personaName = it },
                    label = { Text("灵魂名称", style = IndustrialTypography.Tech) },
                    singleLine = true,
                    shape = AuroraShapes.Small,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "保存灵魂",
                    style = IndustrialTypography.Tech,
                    color = if (personaName.isNotBlank()) AuroraColorTokens.SignalThinking
                    else AuroraColorTokens.SignalNeutral,
                    modifier = Modifier.bouncyClickable(enabled = personaName.isNotBlank()) {
                        val prompt = resultPrompt
                        if (prompt != null) {
                            scope.launch { trainVm.savePersona(personaName.trim(), "", prompt) }
                        }
                    }
                )
            }
        }
    }
}

/** 灵魂训练配置面板：模型 / 温度 / 维度 / 自定义偏好 */
@Composable
private fun TrainingConfigPanel(
    config: TrainPersonaConfig,
    modelConfigs: List<ModelConfig>,
    onConfigChange: (TrainPersonaConfig.() -> TrainPersonaConfig) -> Unit
) {
    var modelMenuOpen by remember { mutableStateOf(false) }
    val selectedModelName = modelConfigs.firstOrNull { it.id == config.modelId }
        ?.let { it.name.ifBlank { it.id } }
        ?: "未选择模型"

    Text(
        "训练配置",
        style = IndustrialTypography.SectionLabel,
        color = AuroraColorTokens.TextCaption
    )

    Box {
        Surface(
            color = AuroraColorTokens.Base2,
            shape = AuroraShapes.Small,
            modifier = Modifier
                .fillMaxWidth()
                .bouncyClickable { modelMenuOpen = true }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = IndustrialSpacing.Item, vertical = IndustrialSpacing.Tight),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "模型：$selectedModelName",
                    style = IndustrialTypography.Tech,
                    color = AuroraColorTokens.TextBody,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    Icons.Filled.ExpandMore,
                    contentDescription = "选择模型",
                    tint = AuroraColorTokens.TextCaption,
                    modifier = Modifier.size(IndustrialSpacing.IconSmall)
                )
            }
        }
        DropdownMenu(expanded = modelMenuOpen, onDismissRequest = { modelMenuOpen = false }) {
            if (modelConfigs.isEmpty()) {
                DropdownMenuItem(
                    text = { Text("暂无模型配置（请先在 LLM 方案中添加）", style = IndustrialTypography.Tech) },
                    onClick = {},
                    enabled = false
                )
            } else {
                modelConfigs.forEach { cfg ->
                    DropdownMenuItem(
                        text = { Text(cfg.name.ifBlank { cfg.id }, style = IndustrialTypography.Tech) },
                        onClick = {
                            onConfigChange { copy(modelId = cfg.id) }
                            modelMenuOpen = false
                        }
                    )
                }
            }
        }
    }

    Text(
        "分析温度：${String.format(java.util.Locale.US, "%.2f", config.temperature)}",
        style = IndustrialTypography.Tech,
        color = AuroraColorTokens.TextTech
    )
    Slider(
        value = config.temperature,
        onValueChange = { value -> onConfigChange { copy(temperature = value) } },
        valueRange = 0f..1.5f
    )

    Text(
        "上下文预算",
        style = IndustrialTypography.Tech,
        color = AuroraColorTokens.TextTech
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(IndustrialSpacing.Item)
    ) {
        listOf(
            128_000 to "128K",
            512_000 to "512K",
            1_000_000 to "1M",
            2_000_000 to "2M"
        ).forEach { (value, label) ->
            val active = config.contextBudget == value
            Surface(
                color = if (active) AuroraColorTokens.SignalWarning else AuroraColorTokens.Base2,
                shape = AuroraShapes.Small,
                modifier = Modifier
                    .weight(1f)
                    .bouncyClickable { onConfigChange { copy(contextBudget = value) } }
            ) {
                Text(
                    label,
                    style = IndustrialTypography.Tech,
                    color = if (active) AuroraColorTokens.Base0 else AuroraColorTokens.TextBody,
                    modifier = Modifier.padding(
                        horizontal = IndustrialSpacing.Tight,
                        vertical = IndustrialSpacing.Tight
                    )
                )
            }
        }
    }
    Text(
        "按模型实际上下文窗口选择，默认 1M；仅用于训练输入预算，与输出 Max Tokens 独立",
        style = IndustrialTypography.Caption,
        color = AuroraColorTokens.TextCaption
    )

    Text(
        "分析维度（至少选 1 个）",
        style = IndustrialTypography.Tech,
        color = AuroraColorTokens.TextTech
    )
    TrainDimension.entries.chunked(3).forEach { rowDims ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(IndustrialSpacing.Item)
        ) {
            rowDims.forEach { dimension ->
                val active = dimension in config.selectedDimensions
                Surface(
                    color = if (active) AuroraColorTokens.SignalWarning else AuroraColorTokens.Base2,
                    shape = AuroraShapes.Small,
                    modifier = Modifier
                        .weight(1f)
                        .bouncyClickable {
                            onConfigChange {
                                copy(
                                    selectedDimensions = if (dimension in selectedDimensions) {
                                        selectedDimensions - dimension
                                    } else {
                                        selectedDimensions + dimension
                                    }
                                )
                            }
                        }
                ) {
                    Text(
                        dimension.label,
                        style = IndustrialTypography.Tech,
                        color = if (active) AuroraColorTokens.Base0 else AuroraColorTokens.TextBody,
                        modifier = Modifier.padding(
                            horizontal = IndustrialSpacing.Tight,
                            vertical = IndustrialSpacing.Tight
                        )
                    )
                }
            }
            repeat(3 - rowDims.size) { Spacer(Modifier.weight(1f)) }
        }
    }

    OutlinedTextField(
        value = config.customPrompt,
        onValueChange = { text -> onConfigChange { copy(customPrompt = text) } },
        label = { Text("自定义分析偏好（可选）", style = IndustrialTypography.Tech) },
        minLines = 3,
        maxLines = 6,
        shape = AuroraShapes.Small,
        modifier = Modifier.fillMaxWidth()
    )
}

/** 提示词 Tab：多套自定义提示词（启用/编辑/删除/新建） */
@Composable
private fun PromptTab(viewModel: WorkspaceViewModel) {
    val sets by viewModel.customPromptSets.collectAsStateWithLifecycle()
    val promptState by viewModel.customPromptState.collectAsStateWithLifecycle()
    var editTarget by remember { mutableStateOf<com.kirakira.kiracode.domain.model.CustomPromptSet?>(null) }
    var creating by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = IndustrialSpacing.Overlay, vertical = IndustrialSpacing.Compact),
        verticalArrangement = Arrangement.spacedBy(IndustrialSpacing.Item)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "自定义提示词",
                style = IndustrialTypography.SectionLabel,
                color = AuroraColorTokens.TextCaption
            )
            Spacer(Modifier.weight(1f))
            Text(
                "新建",
                style = IndustrialTypography.Tech,
                color = AuroraColorTokens.SignalThinking,
                modifier = Modifier
                    .bouncyClickable { creating = true }
                    .padding(horizontal = IndustrialSpacing.Tight)
            )
        }
        // P0：三态状态显示——未启用时明确告知（不展示默认模板误导用户以为已生效）
        val statusText = when (val st = promptState) {
            is com.kirakira.kiracode.domain.model.CustomPromptState.Enabled ->
                "已启用自定义规则（注入每轮创作）"
            is com.kirakira.kiracode.domain.model.CustomPromptState.Disabled ->
                "已保存但未启用（内容已保留，可重新开启）"
            com.kirakira.kiracode.domain.model.CustomPromptState.NotConfigured ->
                "未启用自定义规则"
        }
        Text(
            statusText,
            style = IndustrialTypography.Tech,
            color = if (promptState is com.kirakira.kiracode.domain.model.CustomPromptState.Enabled)
                AuroraColorTokens.SignalSuccess else AuroraColorTokens.TextTech
        )
        // P0：默认模板按钮——只有用户主动点击才保存并启用（不再自动注入）
        if (promptState !is com.kirakira.kiracode.domain.model.CustomPromptState.Enabled) {
            Text(
                "使用默认模板",
                style = IndustrialTypography.Tech,
                color = AuroraColorTokens.SignalThinking,
                modifier = Modifier
                    .bouncyClickable {
                        viewModel.saveCustomPromptSet(
                            "默认创作规则",
                            com.kirakira.kiracode.domain.prompt.BuiltinSystemPrompt.DEFAULT_CUSTOM_PROMPT
                        )
                    }
                    .padding(horizontal = IndustrialSpacing.Tight)
            )
        }
        if (sets.isEmpty()) {
            Text(
                "暂无提示词套：新建一套角色卡创作规则",
                style = IndustrialTypography.Tech,
                color = AuroraColorTokens.TextTech
            )
        }
        sets.forEach { set ->
            val active = set.id == viewModel.activeCustomPromptSetId.collectAsStateWithLifecycle(initialValue = null).value
            Surface(
                color = AuroraColorTokens.Base2,
                shape = AuroraShapes.Small,
                modifier = Modifier
                    .fillMaxWidth()
                    .bouncyClickable { viewModel.activateCustomPromptSet(set.id) }
            ) {
                Column(modifier = Modifier.padding(IndustrialSpacing.Item)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            set.name,
                            style = IndustrialTypography.Caption,
                            color = AuroraColorTokens.TextTitle,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        if (active) {
                            Text(
                                "当前",
                                style = IndustrialTypography.Marker,
                                color = AuroraColorTokens.SignalWarning
                            )
                            Spacer(Modifier.width(IndustrialSpacing.Item))
                            // P0：停用开关——保留文本，仅关闭注入
                            Text(
                                "停用",
                                style = IndustrialTypography.Tech,
                                color = AuroraColorTokens.TextBody,
                                modifier = Modifier.bouncyClickable { viewModel.activateCustomPromptSet(null) }
                            )
                            Spacer(Modifier.width(IndustrialSpacing.Item))
                        }
                        Text(
                            "编辑",
                            style = IndustrialTypography.Tech,
                            color = AuroraColorTokens.TextBody,
                            modifier = Modifier.bouncyClickable { editTarget = set }
                        )
                        Spacer(Modifier.width(IndustrialSpacing.Item))
                        Text(
                            "删除",
                            style = IndustrialTypography.Tech,
                            color = AuroraColorTokens.SignalError,
                            modifier = Modifier.bouncyClickable { viewModel.deleteCustomPromptSet(set.id) }
                        )
                    }
                    Text(
                        set.content.take(80),
                        style = IndustrialTypography.Tech,
                        color = AuroraColorTokens.TextCaption,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }

    // 新建/编辑提示词弹窗
    if (creating || editTarget != null) {
        PromptSetEditDialog(
            initial = editTarget,
            onDismiss = {
                creating = false
                editTarget = null
            },
            onConfirm = { name, content ->
                viewModel.saveCustomPromptSet(name, content, editTarget?.id)
                creating = false
                editTarget = null
            }
        )
    }
}

/** 提示词套编辑弹窗 */
@Composable
private fun PromptSetEditDialog(
    initial: com.kirakira.kiracode.domain.model.CustomPromptSet?,
    onDismiss: () -> Unit,
    onConfirm: (name: String, content: String) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var content by remember { mutableStateOf(initial?.content ?: "") }

    AuroraDialog(
        onDismissRequest = onDismiss,
        maxWidth = 480.dp,
        scrollable = true
    ) {
        AuroraDialogTitle(if (initial == null) "新建提示词" else "编辑提示词")
        AuroraDialogTextField(
            value = name,
            onValueChange = { name = it },
            label = "名称",
            singleLine = true
        )
        AuroraDialogTextField(
            value = content,
            onValueChange = { content = it },
            label = "提示词内容",
            singleLine = false,
            minLines = 4,
            maxLines = 10
        )
        AuroraDialogActions(
            confirmText = "保存",
            onConfirm = {
                if (name.isNotBlank() && content.isNotBlank()) {
                    onConfirm(name.trim(), content)
                }
            },
            onDismiss = onDismiss,
            confirmEnabled = name.isNotBlank() && content.isNotBlank()
        )
    }
}

/** 单条日志最大显示字符数（超出截断；完整内容仍可通过「复制全部日志」获取） */
private const val LOG_DISPLAY_MAX = 1_200

/** 诊断 Tab：运行日志（刷新/复制/清空/级别筛选） */
@Composable
private fun DiagnosticsTab() {
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var logs by remember { mutableStateOf(com.kirakira.kiracode.util.AppLogger.getAllLogs()) }
    var levelFilter by rememberSaveable { mutableStateOf("ALL") }

    val filtered = remember(logs, levelFilter) {
        if (levelFilter == "ALL") logs else logs.filter { it.level == levelFilter }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = IndustrialSpacing.Overlay, vertical = IndustrialSpacing.Compact),
        verticalArrangement = Arrangement.spacedBy(IndustrialSpacing.Item)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(IndustrialSpacing.Tight)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(IndustrialSpacing.Item)
            ) {
                listOf("ALL", "E", "W", "D").forEach { level ->
                    val active = levelFilter == level
                    Surface(
                        color = if (active) AuroraColorTokens.SignalWarning else AuroraColorTokens.Base2,
                        shape = AuroraShapes.Small,
                        modifier = Modifier.bouncyClickable { levelFilter = level }
                    ) {
                        Text(
                            level,
                            style = IndustrialTypography.Marker,
                            color = if (active) AuroraColorTokens.Base0 else AuroraColorTokens.TextBody,
                            modifier = Modifier.padding(
                                horizontal = IndustrialSpacing.Item,
                                vertical = IndustrialSpacing.Tight
                            )
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                Text(
                    "刷新",
                    style = IndustrialTypography.Tech,
                    color = AuroraColorTokens.SignalThinking,
                    modifier = Modifier.bouncyClickable {
                        logs = com.kirakira.kiracode.util.AppLogger.getAllLogs()
                    }
                )
                Spacer(Modifier.width(IndustrialSpacing.Item))
                Icon(
                    Icons.Filled.ContentCopy,
                    contentDescription = "复制全部日志",
                    tint = AuroraColorTokens.TextCaption,
                    modifier = Modifier
                        .size(IndustrialSpacing.IconSmall)
                        .bouncyClickable {
                            // 后台 dispatcher 构建全文（500 条含 stackTrace 可能达数 MB，避免主线程卡顿）
                            scope.launch(kotlinx.coroutines.Dispatchers.Default) {
                                val text = com.kirakira.kiracode.util.AppLogger.getLogsAsText()
                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                    clipboard.setText(AnnotatedString(text))
                                }
                            }
                        }
                )
                Spacer(Modifier.width(IndustrialSpacing.Tight))
                Icon(
                    Icons.Filled.DeleteOutline,
                    contentDescription = "清空日志",
                    tint = AuroraColorTokens.SignalError,
                    modifier = Modifier
                        .size(IndustrialSpacing.IconSmall)
                        .bouncyClickable {
                            com.kirakira.kiracode.util.AppLogger.clear()
                            logs = emptyList()
                        }
                )
            }
        }
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(IndustrialSpacing.Tight)
        ) {
            if (filtered.isEmpty()) {
                item {
                    Text(
                        "暂无日志 · 发送消息后点「刷新」查看",
                        style = IndustrialTypography.Tech,
                        color = AuroraColorTokens.TextTech
                    )
                }
            }
            items(filtered, key = { it.seq }) { log ->
                val levelColor = when (log.level) {
                    "E" -> AuroraColorTokens.SignalError
                    "W" -> AuroraColorTokens.SignalWarning
                    "D" -> AuroraColorTokens.SignalSuccess
                    else -> AuroraColorTokens.TextBody
                }
                // 格式化按条目记忆；显示截断防超长 stackTrace 文本测量卡顿（完整内容可复制）
                val display = remember(log) { runCatching { log.format() }.getOrDefault("") }
                val displayText = if (display.length > LOG_DISPLAY_MAX) {
                    display.take(LOG_DISPLAY_MAX) + "…（截断，共${display.length}字，可复制全文）"
                } else display
                Surface(
                    color = levelColor.copy(alpha = 0.08f),
                    shape = AuroraShapes.Small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = displayText,
                        style = IndustrialTypography.Tech,
                        color = levelColor,
                        modifier = Modifier.padding(IndustrialSpacing.Item)
                    )
                }
            }
        }
    }
}

/** 关于 Tab：应用信息/源码/协议/检查更新 */
@Composable
private fun AboutTab() {
    val vm: com.kirakira.kiracode.ui.about.AboutViewModel = hiltViewModel()
    val announceState by vm.announceState.collectAsStateWithLifecycle()
    val licenseText by vm.licenseText.collectAsStateWithLifecycle()
    var licenseExpanded by rememberSaveable { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = IndustrialSpacing.Overlay, vertical = IndustrialSpacing.Compact),
        verticalArrangement = Arrangement.spacedBy(IndustrialSpacing.Item)
    ) {
        Text(
                    "KiraCard · Beta",
            style = IndustrialTypography.PanelTitle,
            color = AuroraColorTokens.TextTitle
        )
        Text(
                    "免费开源，唯爱而生。",
            style = IndustrialTypography.Caption,
            color = AuroraColorTokens.TextBody
        )
        AboutLinkRow("源码", "github.com/beichenxingI/KiraCode") {
            runCatching {
                context.startActivity(
                    android.content.Intent(
                        android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse("https://github.com/beichenxingI/KiraCode")
                    )
                )
            }
        }
        AboutLinkRow("检查更新", "获取 KiraKira 最新公告与下载") {
            vm.checkUpdate()
        }

        // 协议（可展开）
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .bouncyClickable { licenseExpanded = !licenseExpanded }
        ) {
            Text(
                if (licenseExpanded) "AGPLv3 协议（收起）" else "AGPLv3 协议（展开）",
                style = IndustrialTypography.Tech,
                color = AuroraColorTokens.TextTech
            )
            if (licenseExpanded) {
                Text(
                    licenseText.orEmpty().take(2000),
                    style = IndustrialTypography.Tech,
                    color = AuroraColorTokens.TextCaption
                )
            }
        }
    }

    // 公告弹窗（复用 AnnouncementSheet）
    if (announceState != null) {
        com.kirakira.kiracode.ui.announce.AnnouncementSheet(
            state = announceState!!,
            onDismiss = { vm.dismissAnnouncement() },
            onRetry = { vm.checkUpdate() }
        )
    }
}

@Composable
private fun AboutLinkRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .bouncyClickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = IndustrialTypography.Caption,
                color = AuroraColorTokens.TextTitle
            )
            Text(
                subtitle,
                style = IndustrialTypography.Tech,
                color = AuroraColorTokens.TextTech,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            "→",
            style = IndustrialTypography.Body,
            color = AuroraColorTokens.SignalThinking
        )
    }
}

/** 上下文长度预设标签：≥1M 显示 M，否则显示 K */
private fun formatContextLength(value: Int): String = when {
    value >= 1_000_000 -> "${value / 1_000_000}M"
    else -> "${value / 1000}K"
}

