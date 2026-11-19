package com.kirakira.kiracode.ui.workspace

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kirakira.kiracode.domain.model.CharacterCard
import com.kirakira.kiracode.domain.model.ExportFormat
import com.kirakira.kiracode.domain.model.PreferenceDimension
import com.kirakira.kiracode.ui.overlays.PreferenceOverlay
import com.kirakira.kiracode.ui.common.AnimationConstants
import com.kirakira.kiracode.ui.common.AuroraDialog
import com.kirakira.kiracode.ui.common.AuroraDialogActions
import com.kirakira.kiracode.ui.common.AuroraDialogText
import com.kirakira.kiracode.ui.common.AuroraDialogTextField
import com.kirakira.kiracode.ui.common.AuroraDialogTitle
import com.kirakira.kiracode.ui.export.ExportDialog
import com.kirakira.kiracode.ui.theme.AuroraColorTokens
import com.kirakira.kiracode.ui.theme.AuroraShapes
import com.kirakira.kiracode.ui.theme.AuroraTypography
import com.kirakira.kiracode.ui.theme.IndustrialSpacing
import kotlinx.coroutines.launch

// 工作台配色（已收敛至 AuroraColorTokens）
private val WsText = AuroraColorTokens.TextTitle
private val WsMuted = AuroraColorTokens.TextCaption

/** 修改弹窗目标（继续改 Diff / 修改计划） */
internal enum class ModifyTarget { PLAN, DIFF }

/**
 * Agent 工作台主界面（横屏工作台，阶段 B）：
 *
 * - 顶部：状态条（菜单 + 品牌 + 当前工作记录 + Agent状态 + 模型 + 监控 + 设置 + 新建）；
 * - 左侧：RecordRail 工作记录栏（可折叠）；
 * - 中央：EventStream 事件时间轴 + 控制条 + CommandBar 命令输入栏；
 * - 右侧：RightPreviewPanel 监控面板（可隐藏）。
 *
 * 横屏为唯一方向：小屏横屏用面板开关（记录栏/监控面板），大屏横屏三栏同显。
 */
@Composable
fun WorkspaceScreen(
    viewModel: WorkspaceViewModel,
    initialSessionId: Long? = null,
    initialWorkingCard: CharacterCard? = null
) {
    val uiState by viewModel.uiState.collectAsState()
    val workspace by viewModel.workspace.collectAsState()
    val liveReasoning by viewModel.liveReasoning.collectAsState()
    val sessionListState by viewModel.sessionListState.collectAsState()
    val previewCollapsed by viewModel.previewCollapsed.collectAsState()
    val sidebarCollapsed by viewModel.sidebarCollapsed.collectAsState()
    val availableModels by viewModel.availableModels.collectAsState()
    val referenceFiles by viewModel.referenceFiles.collectAsState()
    val allCards by viewModel.allCards.collectAsState()
    val referenceCardList by viewModel.referenceCards.collectAsState()
    val vmExecuting by viewModel.isExecuting.collectAsState()
    val preferenceCatalog by viewModel.preferenceCatalog.collectAsState()
    val sessionPreferences by viewModel.sessionPreferences.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    // 本地 UI 状态
    var inputText by rememberSaveable { mutableStateOf("") }
    var pendingExportFormat by remember { mutableStateOf(ExportFormat.PNG) }
    var guessSheetTarget by remember { mutableStateOf<Pair<String, String>?>(null) }
    var showModifyDialog by remember { mutableStateOf<ModifyTarget?>(null) }
    var showLicenseDialog by remember { mutableStateOf(false) }
    val overlayState = remember { com.kirakira.kiracode.ui.system.OverlayHostState() }
    val allRecords = remember(sessionListState.sessions) {
        sessionsToRecords(sessionListState.sessions).sortedFavoritesFirst()
    }
    // 响应式布局状态（宽屏由 VM 停靠状态控制，窄屏走抽屉/Sheet）
    val windowSize = rememberWindowSizeClass()
    var drawerOpen by rememberSaveable { mutableStateOf(false) }
    var monitorSheetOpen by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(windowSize) {
        drawerOpen = false
        monitorSheetOpen = false
    }

    // Agent 卡片交互回调（追问/计划/Diff/脑补）
    val cardCallbacks = AgentCardCallbacks(
        activeCardId = workspace.pendingInteraction?.cardId,
        onAnswerOption = { viewModel.answerPending(it) },
        onSkipQuestion = { viewModel.skipQuestion() },
        onPlanConfirm = { viewModel.confirmPlan() },
        onPlanModify = { showModifyDialog = ModifyTarget.PLAN },
        onDiffAccept = { viewModel.decideDiff(true) },
        onDiffReject = { viewModel.decideDiff(false) },
        onDiffContinue = { showModifyDialog = ModifyTarget.DIFF },
        onContinueFromBudget = { viewModel.continueFromBudget(it) },
        onSkipAndFinish = { viewModel.skipAndFinish() },
        onAbandonTask = { viewModel.abandonTask() },
        onGuessClick = { key, label -> guessSheetTarget = key to label }
    )

    val isSending = (uiState as? ChatUiState.Ready)?.isSending == true
    val messages = (uiState as? ChatUiState.Ready)?.messages ?: emptyList()
    val errorMsg = (uiState as? ChatUiState.Error)?.message
        ?: (uiState as? ChatUiState.Ready)?.error
    val agentStatus = deriveAgentStatus(
        isSending = isSending,
        isExecuting = vmExecuting,
        pendingInteraction = workspace.pendingInteraction,
        errorMessage = errorMsg
    )
    val lastUserMessage = messages.lastOrNull { it.role == "user" }?.content
    val streamEmpty = messages.isEmpty() && !isSending &&
        workspace.agentCards.isEmpty() && liveReasoning == null
    val currentSessionTitle = sessionListState.sessions
        .firstOrNull { it.id == viewModel.currentSessionId }
        ?.title?.ifBlank { "新会话" }
    val reasoningState = viewModel.reasoningStateFor(workspace.activeModelName)
    // chip 名称映射：成品卡仓库 + 参考卡仓库（两个数据源合并，参考卡也能显示真实名称）
    val refCardNames = remember(allCards, referenceCardList) {
        allCards.associate { it.uuid to it.name } + referenceCardList.associate { it.uuid to it.name }
    }

    // 模型切换/面板打开时触发思考强度探测（有缓存则跳过）
    LaunchedEffect(workspace.activeModelName) {
        viewModel.ensureReasoningProbe()
    }

    // 初始化：恢复上次会话 / 带卡建会话；首启声明
    LaunchedEffect(Unit) {
        viewModel.refreshModelInfo()
        viewModel.loadSessions()
        viewModel.refreshCards()
        if (initialSessionId != null && initialSessionId > 0) {
            viewModel.openSession(initialSessionId)
            initialWorkingCard?.let { viewModel.setWorkingCard(it) }
        } else if (initialWorkingCard != null) {
            val created = viewModel.createNewSession(
                title = "${initialWorkingCard.name} · 创作", keepWorkingCard = true
            )
            if (created.isSuccess) viewModel.setWorkingCard(initialWorkingCard)
        } else if (viewModel.currentSessionId == null) {
            viewModel.restoreLastSession()
        }
        if (viewModel.isFirstLaunch.value) showLicenseDialog = true
    }

    // 提示与错误
    LaunchedEffect(workspace.toast) {
        workspace.toast?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearToast()
        }
    }
    LaunchedEffect(errorMsg) {
        if (errorMsg != null) snackbarHostState.showSnackbar(errorMsg)
    }

    // 命令执行：/ 命令有实际行为；其它文本作为需求交给 Agent
    fun executeCommand(raw: String) {
        val text = raw.trim()
        inputText = ""
        when {
            text.equals("/help", ignoreCase = true) -> scope.launch {
                snackbarHostState.showSnackbar(
                    "命令：/help 说明 · /clear 释放上下文 · /export 导出角色卡；其它文本作为需求直接执行"
                )
            }
            text.equals("/clear", ignoreCase = true) -> viewModel.releaseContext()
            text.equals("/export", ignoreCase = true) -> {
                val card = workspace.workingCard
                if (card != null) {
                    overlayState.openSecondary(com.kirakira.kiracode.ui.system.SecondaryOverlay.Export(card))
                } else {
                    scope.launch { snackbarHostState.showSnackbar("无当前工作卡可导出") }
                }
            }
            else -> viewModel.onUserSend(text)
        }
    }

    val centerContent: @Composable (Modifier) -> Unit = { layoutModifier ->
        Box(layoutModifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            if (streamEmpty) {
                WorkspaceHero(
                    catalog = preferenceCatalog,
                    selectedIds = sessionPreferences.selectedOptionIds,
                    onToggle = viewModel::togglePreferenceOption,
                    onAddDimension = viewModel::addPreferenceDimension,
                    onUpdateDimension = viewModel::updatePreferenceDimension,
                    onDeleteDimension = viewModel::deletePreferenceDimension,
                    onAddOption = viewModel::addPreferenceOption,
                    onUpdateOption = viewModel::updatePreferenceOption,
                    onDeleteOption = viewModel::deletePreferenceOption,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                EventStream(
                    messages = messages,
                    agentCards = workspace.agentCards,
                    isSending = isSending,
                    liveReasoning = liveReasoning,
                    cardCallbacks = cardCallbacks,
                    listState = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 900.dp)
                )
            }
        }
    }

    val runConfigStrip: @Composable () -> Unit = {
        RunConfigStrip(
            models = availableModels,
            currentModel = workspace.activeModelName,
            reasoningState = reasoningState,
            currentEffort = workspace.reasoningEffort,
            currentMode = workspace.workMode,
            onSelectModel = { viewModel.setChatModel(it) },
            onSelectEffort = { viewModel.setReasoningEffort(it) },
            onSelectMode = { viewModel.setWorkMode(it) }
        )
    }

    val commandDock: @Composable () -> Unit = {
        CommandDock(
            value = inputText,
            onValueChange = { inputText = it },
            onExecute = ::executeCommand,
            agentStatus = agentStatus,
            onStop = { viewModel.stopGeneration() },
            onFilesSelected = { viewModel.addReferenceFiles(it) },
            referenceFiles = referenceFiles,
            onRemoveFile = { viewModel.removeReferenceFile(it) },
            refSelections = workspace.refSelections,
            refCardNames = refCardNames,
            onUnmountRef = { uuid ->
                viewModel.setSelectedReferenceIds(
                    workspace.refSelections.map { it.uuid } - uuid
                )
            },
            lastUserMessage = lastUserMessage,
            onRetry = { lastUserMessage?.let { viewModel.onUserSend(it) } }
        )
    }

    val standardBottom: @Composable () -> Unit = {
        Column {
            runConfigStrip()
            commandDock()
        }
    }

    val compactBottom: @Composable () -> Unit = {
        Column {
            runConfigStrip()
            CompactBottomArea(
                planBook = workspace.planBook,
                agentStatus = agentStatus,
                isSending = isSending,
                contextUsagePercent = workspace.contextInfo.usagePercentInt,
                inputDock = commandDock
            )
        }
    }

    val recordRail: @Composable (Boolean, Modifier) -> Unit = { collapsed, railModifier ->
        RecordRail(
            sessions = sessionListState.sessions,
            currentSessionId = viewModel.currentSessionId,
            collapsed = collapsed,
            onSessionClick = { viewModel.openSession(it) },
            onToggleFavorite = { id, fav -> viewModel.pinSession(id, fav) },
            onShowAllRecords = { overlayState.open(com.kirakira.kiracode.ui.system.Overlay.WorkRecords) },
            onImportCard = { overlayState.open(com.kirakira.kiracode.ui.system.Overlay.Import) },
            onCardRepositoryClick = { overlayState.open(com.kirakira.kiracode.ui.system.Overlay.Library(initialTab = 0)) },
            onNavigateToFinished = { overlayState.open(com.kirakira.kiracode.ui.system.Overlay.Library(initialTab = 1)) },
            onSettingsClick = { overlayState.open(com.kirakira.kiracode.ui.system.Overlay.Settings()) },
            modifier = railModifier
        )
    }

    val monitorPanel: @Composable (Modifier, () -> Unit) -> Unit = { panelModifier, onClose ->
        MonitorPanel(
            planBook = workspace.planBook,
            tasks = workspace.tasks,
            contextInfo = workspace.contextInfo,
            agentStatus = agentStatus,
            modelName = workspace.activeModelName,
            workMode = workspace.workMode,
            reasoningEffort = workspace.reasoningEffort,
            card = workspace.workingCard,
            onCardChange = { viewModel.setWorkingCard(it) },
            onPickCover = { uri -> viewModel.updateWorkingCardAvatar(uri) },
            onClearCover = { viewModel.clearWorkingCardAvatar() },
            onClose = onClose,
            onSaveToFinished = { viewModel.saveWorkingCardToFinished() },
            modifier = panelModifier
        )
    }

    val compactTopBar: @Composable () -> Unit = {
        CompactTopBar(
            sessionTitle = currentSessionTitle,
            agentStatus = agentStatus,
            modelName = workspace.activeModelName,
            onMenuClick = { drawerOpen = true },
            onMonitorClick = { monitorSheetOpen = true },
            onNewSessionClick = { viewModel.startNewSession() }
        )
    }

    val expandedTopBar: @Composable () -> Unit = {
        WorkspaceStatusBar(
            sessionTitle = currentSessionTitle,
            agentStatus = agentStatus,
            modelName = workspace.activeModelName,
            onMenuClick = { viewModel.toggleSidebar() },
            onMonitorClick = { viewModel.togglePreview() },
            onSettingsClick = { overlayState.open(com.kirakira.kiracode.ui.system.Overlay.Settings()) },
            onNewSessionClick = { viewModel.startNewSession() }
        )
    }

    com.kirakira.kiracode.ui.system.OverlayHost(
        state = overlayState,
        content = {
            Scaffold(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding(),
                containerColor = AuroraColorTokens.Base0,
                snackbarHost = { WorkspaceSnackbarHost(snackbarHostState, windowSize) }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .padding(innerPadding)
                        .fillMaxSize()
                ) {
                    when (windowSize) {
                        WindowSizeClass.COMPACT -> CompactWorkspaceLayout(
                            drawerOpen = drawerOpen,
                            onDrawerClose = { drawerOpen = false },
                            topBar = compactTopBar,
                            content = centerContent,
                            bottom = compactBottom,
                            drawerContent = { recordRail(false, Modifier.fillMaxSize()) },
                            monitorSheetOpen = monitorSheetOpen,
                            onMonitorDismiss = { monitorSheetOpen = false },
                            monitorContent = {
                                monitorPanel(Modifier.fillMaxWidth()) { monitorSheetOpen = false }
                            }
                        )

                        WindowSizeClass.MEDIUM -> MediumWorkspaceLayout(
                            railContent = { recordRail(true, Modifier.fillMaxSize()) },
                            topBar = compactTopBar,
                            content = centerContent,
                            bottom = compactBottom,
                            drawerOpen = drawerOpen,
                            onDrawerClose = { drawerOpen = false },
                            drawerContent = { recordRail(false, Modifier.fillMaxSize()) },
                            monitorOpen = monitorSheetOpen,
                            onMonitorClose = { monitorSheetOpen = false },
                            monitorContent = {
                                monitorPanel(Modifier.fillMaxSize()) { monitorSheetOpen = false }
                            }
                        )

                        WindowSizeClass.EXPANDED -> ExpandedWorkspaceLayout(
                            topBar = expandedTopBar,
                            sidebarCollapsed = sidebarCollapsed,
                            sidebarContent = { recordRail(sidebarCollapsed, Modifier.fillMaxSize()) },
                            content = centerContent,
                            bottom = standardBottom,
                            monitorVisible = !previewCollapsed,
                            monitorContent = {
                                monitorPanel(Modifier.fillMaxSize()) { viewModel.togglePreview() }
                            }
                        )
                    }
                }
            }
        },
        l1Content = { overlay ->
            when (overlay) {
                is com.kirakira.kiracode.ui.system.Overlay.WorkRecords -> com.kirakira.kiracode.ui.overlays.WorkRecordsOverlay(
                    records = allRecords,
                    currentSessionId = viewModel.currentSessionId,
                    onDismiss = { overlayState.closeL1() },
                    onSelectRecord = { viewModel.openSession(it.id) },
                    onToggleFavorite = { id, fav -> viewModel.pinSession(id, fav) },
                    onRename = { id, title -> scope.launch { viewModel.renameSession(id, title) } },
                    onDelete = { id -> scope.launch { viewModel.deleteSession(id) } },
                    onCreateNew = {
                        overlayState.closeL1()
                        viewModel.startNewSession()
                    }
                )
                is com.kirakira.kiracode.ui.system.Overlay.Library -> {
                    val openInWorkspace: (CharacterCard) -> Unit = { card ->
                        overlayState.closeL1()
                        scope.launch {
                            viewModel.createNewSession(title = "${card.name} · 创作", keepWorkingCard = false)
                            viewModel.setWorkingCard(card)
                        }
                    }
                    com.kirakira.kiracode.ui.overlays.LibraryOverlay(
                        initialTab = overlay.initialTab,
                        currentRefUuids = workspace.refSelections.map { it.uuid },
                        onDismiss = { overlayState.closeL1() },
                        onOpenInWorkspace = openInWorkspace,
                        onDuplicateAsSession = { card ->
                            overlayState.closeL1()
                            scope.launch {
                                viewModel.createNewSession(title = "${card.name} · 创作", keepWorkingCard = false)
                                viewModel.setWorkingCard(card)
                            }
                        },
                        onUseAsReference = { uuids -> viewModel.setSelectedReferenceIds(uuids) },
                        onExportRequest = { card -> overlayState.openSecondary(com.kirakira.kiracode.ui.system.SecondaryOverlay.Export(card, direct = true)) },
                        onEditCard = { card -> overlayState.open(com.kirakira.kiracode.ui.system.Overlay.Edit(card)) },
                        onOpenImport = { overlayState.open(com.kirakira.kiracode.ui.system.Overlay.Import) }
                    )
                }
                is com.kirakira.kiracode.ui.system.Overlay.Settings -> com.kirakira.kiracode.ui.overlays.SettingsOverlay(
                    initialTab = overlay.initialTab,
                    viewModel = viewModel,
                    onDismiss = { overlayState.closeL1() }
                )
                is com.kirakira.kiracode.ui.system.Overlay.Import -> com.kirakira.kiracode.ui.overlays.ImportOverlay(
                    onDismiss = { overlayState.closeL1() },
                    onOpenInWorkspace = { card ->
                        overlayState.closeL1()
                        scope.launch {
                            viewModel.createNewSession(title = "${card.name} · 创作", keepWorkingCard = false)
                            viewModel.setWorkingCard(card)
                        }
                    }
                )
                is com.kirakira.kiracode.ui.system.Overlay.Edit -> {
                    val exportCard: (CharacterCard) -> Unit = { c ->
                        overlayState.openSecondary(com.kirakira.kiracode.ui.system.SecondaryOverlay.Export(c, direct = true))
                    }
                    com.kirakira.kiracode.ui.overlays.EditOverlay(
                        initialCard = overlay.card,
                        onDismiss = { overlayState.closeL1() },
                        onExportRequest = exportCard
                    )
                }
            }
        },
        l2Content = { secondary ->
            when (secondary) {
                is com.kirakira.kiracode.ui.system.SecondaryOverlay.Export -> {
                    if (secondary.direct) {
                        val finishedVm: com.kirakira.kiracode.ui.finished.FinishedViewModel =
                            androidx.hilt.navigation.compose.hiltViewModel()
                        val finishedState by finishedVm.uiState.collectAsState()
                        com.kirakira.kiracode.ui.export.ExportDialog(
                            card = secondary.card,
                            isExporting = finishedState.exporting,
                            onDismiss = { overlayState.closeL2() },
                            onExport = { format, _ ->
                                finishedVm.exportCard(secondary.card, format == ExportFormat.PNG)
                            }
                        )
                    } else {
                        com.kirakira.kiracode.ui.export.ExportDialog(
                            card = secondary.card,
                            initialFormat = pendingExportFormat,
                            isExporting = workspace.exporting,
                            onDismiss = { overlayState.closeL2() },
                            onExport = { format, fileName ->
                                viewModel.exportWorkingCard(format, fileName)
                            }
                        )
                    }
                }
            }
        }
    )

    // ============ 对话框 ============

    // 首次启动 AGPLv3 声明引导（功能保留）
    if (showLicenseDialog) {
        FirstLaunchLicenseDialog(
            onAgree = {
                viewModel.completeFirstLaunch()
                showLicenseDialog = false
            },
            onDismiss = { showLicenseDialog = false }
        )
    }

    // 工作记录浮窗经 OverlayHost 打开（L1）

    // 导出经 OverlayHost L2 打开（工作卡/资料库统一入口）

    // 脑补标注弹窗（Lightbulb 点击 → 重写 / 给选项）
    guessSheetTarget?.let { (_, fieldLabel) ->
        GuessSheetDialog(
            fieldLabel = fieldLabel,
            onDismiss = { guessSheetTarget = null },
            onRewrite = { instruction ->
                guessSheetTarget = null
                viewModel.requestRewrite(fieldLabel, instruction)
            },
            onGiveOptions = {
                guessSheetTarget = null
                viewModel.requestOptions(fieldLabel)
            }
        )
    }

    // 修改弹窗（继续改 Diff / 修改计划）
    showModifyDialog?.let { target ->
        ModifyDialog(
            title = if (target == ModifyTarget.DIFF) {
                val label = (workspace.pendingInteraction as? PendingInteraction.Diff)?.fieldLabel ?: "该字段"
                "继续修改「$label」"
            } else "修改制卡计划",
            placeholder = if (target == ModifyTarget.DIFF) "描述想怎么改（如：再口语化一点）" else "描述想怎么改计划（如：把第 3 步和第 4 步合并）",
            onDismiss = { showModifyDialog = null },
            onSubmit = { feedback ->
                showModifyDialog = null
                if (target == ModifyTarget.DIFF) viewModel.submitDiffFeedback(feedback)
                else viewModel.submitPlanFeedback(feedback)
            }
        )
    }
}

/** 空工作台（「我们该做什么？」） */
@Composable
private fun WorkspaceSnackbarHost(
    hostState: SnackbarHostState,
    windowSize: WindowSizeClass
) {
    val bottomOffset = if (windowSize == WindowSizeClass.EXPANDED) 112.dp else 176.dp
    SnackbarHost(
        hostState = hostState,
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .padding(bottom = bottomOffset)
    ) { data ->
        Snackbar(
            snackbarData = data,
            containerColor = AuroraColorTokens.Base2,
            contentColor = AuroraColorTokens.TextTitle,
            shape = AuroraShapes.Small,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun WorkspaceHero(
    catalog: List<PreferenceDimension>,
    selectedIds: List<String>,
    onToggle: (String) -> Unit,
    onAddDimension: (String) -> Unit,
    onUpdateDimension: (String, String) -> Unit,
    onDeleteDimension: (String) -> Unit,
    onAddOption: (String, String, String) -> Unit,
    onUpdateOption: (String, String, String) -> Unit,
    onDeleteOption: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showPreferenceOverlay by rememberSaveable { mutableStateOf(false) }
    val hasPrefs = selectedIds.isNotEmpty()
    val selectedLabels = catalog
        .flatMap { it.options }
        .filter { it.id in selectedIds }
        .take(3)
        .joinToString(" · ") { it.label }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp)
        ) {
            Text(
                "唯你所愿 赴你所期",
                style = com.kirakira.kiracode.ui.theme.AuroraTypography.Body.copy(
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = AuroraColorTokens.TextTitle,
                    letterSpacing = 2.sp
                ),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "释放想象力，让 AI 去实现",
                style = com.kirakira.kiracode.ui.theme.AuroraTypography.Body.copy(
                    color = AuroraColorTokens.TextCaption
                ),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(24.dp))
            Surface(
                shape = RoundedCornerShape(50),
                color = if (hasPrefs) {
                    AuroraColorTokens.AuroraAccent.copy(alpha = 0.1f)
                } else {
                    AuroraColorTokens.Base2
                },
                border = BorderStroke(
                    1.dp,
                    if (hasPrefs) AuroraColorTokens.AuroraAccent.copy(alpha = 0.4f)
                    else AuroraColorTokens.LinePrimary
                ),
                modifier = Modifier.clickable { showPreferenceOverlay = true }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Icon(
                        Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        tint = if (hasPrefs) AuroraColorTokens.AuroraAccent
                        else AuroraColorTokens.TextCaption,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        selectedLabels.ifBlank { "创作偏好" },
                        style = com.kirakira.kiracode.ui.theme.AuroraTypography.Caption.copy(
                            color = if (hasPrefs) AuroraColorTokens.AuroraAccent
                            else AuroraColorTokens.TextCaption
                        )
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "试试：一个住在港口公会的傲娇接待员",
                style = com.kirakira.kiracode.ui.theme.AuroraTypography.Caption.copy(
                    color = AuroraColorTokens.SignalNeutral
                ),
                textAlign = TextAlign.Center
            )
        }
    }

    if (showPreferenceOverlay) {
        PreferenceOverlay(
            catalog = catalog,
            selectedIds = selectedIds,
            onToggle = onToggle,
            onAddDimension = onAddDimension,
            onUpdateDimension = onUpdateDimension,
            onDeleteDimension = onDeleteDimension,
            onAddOption = onAddOption,
            onUpdateOption = onUpdateOption,
            onDeleteOption = onDeleteOption,
            onDismiss = { showPreferenceOverlay = false }
        )
    }
}

/** 脑补标注弹窗：重写（输入要求）/ 给选项（AI 出 3 个候选） */
@Composable
private fun GuessSheetDialog(
    fieldLabel: String,
    onDismiss: () -> Unit,
    onRewrite: (String) -> Unit,
    onGiveOptions: () -> Unit
) {
    var rewriteInput by remember { mutableStateOf("") }
    var rewriteMode by remember { mutableStateOf(false) }
    AuroraDialog(
        onDismissRequest = onDismiss,
        maxWidth = 420.dp,
        scrollable = true
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.Lightbulb,
                contentDescription = null,
                tint = AuroraColorTokens.AuroraAccent,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            AuroraDialogTitle("AI 脑补 · $fieldLabel")
        }

        AuroraDialogText("这一段是 AI 自行补充的，没有和你确认过。你可以：")

        if (!rewriteMode) {
            Surface(
                color = AuroraColorTokens.Base2,
                shape = AuroraShapes.Micro,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { rewriteMode = true }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Icon(
                        Icons.Filled.Edit,
                        contentDescription = null,
                        tint = AuroraColorTokens.AuroraAccent,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "我来重写",
                        style = AuroraTypography.Body,
                        color = AuroraColorTokens.TextTitle
                    )
                }
            }
            Surface(
                color = AuroraColorTokens.Base2,
                shape = AuroraShapes.Micro,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onGiveOptions() }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Icon(
                        Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        tint = AuroraColorTokens.AuroraAccent,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "让 AI 给 3 个选项",
                        style = AuroraTypography.Body,
                        color = AuroraColorTokens.TextTitle
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = onDismiss,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = AuroraColorTokens.TextCaption
                    )
                ) {
                    Text("取消")
                }
            }
        } else {
            AuroraDialogTextField(
                value = rewriteInput,
                onValueChange = { rewriteInput = it },
                label = "重写要求",
                placeholder = "描述你想要的样子…",
                singleLine = false,
                minLines = 2,
                maxLines = 5
            )
            AuroraDialogActions(
                confirmText = "执行",
                onConfirm = { if (rewriteInput.isNotBlank()) onRewrite(rewriteInput.trim()) },
                onDismiss = onDismiss,
                confirmEnabled = rewriteInput.isNotBlank()
            )
        }
    }
}

/** 修改弹窗：继续改 Diff / 修改计划的反馈输入 */
@Composable
private fun ModifyDialog(
    title: String,
    placeholder: String,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit
) {
    var input by remember { mutableStateOf("") }
    AuroraDialog(
        onDismissRequest = onDismiss,
        maxWidth = 420.dp,
        scrollable = true
    ) {
        AuroraDialogTitle(title)
        AuroraDialogTextField(
            value = input,
            onValueChange = { input = it },
            label = "修改意见",
            placeholder = placeholder,
            singleLine = false,
            minLines = 2,
            maxLines = 5
        )
        AuroraDialogActions(
            confirmText = "执行",
            onConfirm = { if (input.isNotBlank()) onSubmit(input.trim()) },
            onDismiss = onDismiss,
            confirmEnabled = input.isNotBlank()
        )
    }
}
