package com.kirakira.kiracode.ui.overlays

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kirakira.kiracode.domain.model.CharacterCard
import com.kirakira.kiracode.ui.generate.GenerateState
import com.kirakira.kiracode.ui.generate.GenerateViewModel
import com.kirakira.kiracode.ui.importcard.ImportCardViewModel
import com.kirakira.kiracode.ui.theme.AuroraColorTokens
import com.kirakira.kiracode.ui.theme.AuroraShapes
import com.kirakira.kiracode.ui.theme.AuroraTypography
import com.kirakira.kiracode.ui.theme.IndustrialSpacing
import com.kirakira.kiracode.ui.theme.IndustrialTypography
import com.kirakira.kiracode.ui.theme.bouncyClickable

/**
 * 导入浮窗：文件 / 粘贴 / AI 生成 三模式。
 *
 * - 解析预览 → 保存为参考卡 / 打开到当前工作台（含新会话）；
 * - AI 生成保留 GenerateViewModel 与用例逻辑；生成成功后保存并提供打开入口；
 * - 解析/生成进行中禁关（关闭钮禁用）。
 */
@Composable
fun ImportOverlay(
    onDismiss: () -> Unit,
    onOpenInWorkspace: (CharacterCard) -> Unit,
    modifier: Modifier = Modifier
) {
    var mode by rememberSaveable { mutableIntStateOf(0) }
    val vm: ImportCardViewModel = hiltViewModel()
    val genVm: GenerateViewModel = hiltViewModel()
    val importState by vm.uiState.collectAsStateWithLifecycle()
    val genState by genVm.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val busy = importState.isLoading ||
        genState is GenerateState.Loading

    // 文件选择器：JSON / PNG 角色卡
    val filePicker = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val bytes = runCatching {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            }.getOrNull()
            if (bytes != null) vm.importFromBytes(uri.lastPathSegment, bytes) else vm.notifyReadFailure()
        }
    }

    OverlayShell(
        onDismiss = { if (!busy) onDismiss() },
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
        ) {
                // 头部
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp)
                ) {
                    Text(
                        "导入角色卡",
                        style = AuroraTypography.PanelTitle.copy(color = AuroraColorTokens.TextTitle)
                    )
                    Spacer(Modifier.weight(1f))
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "关闭",
                        tint = if (busy) AuroraColorTokens.SignalNeutral else AuroraColorTokens.TextCaption,
                        modifier = Modifier
                            .size(IndustrialSpacing.IconMedium)
                            .bouncyClickable(enabled = !busy, onClick = onDismiss)
                    )
                }

                HorizontalDivider(color = AuroraColorTokens.LinePrimary)

                // 模式 Tab
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("文件", "粘贴", "AI 生成").forEachIndexed { i, label ->
                        val active = mode == i
                        Surface(
                            color = if (active) AuroraColorTokens.AuroraAccent.copy(alpha = 0.12f)
                            else Color.Transparent,
                            shape = RoundedCornerShape(50),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (active) AuroraColorTokens.AuroraAccent.copy(alpha = 0.5f)
                                else AuroraColorTokens.LinePrimary
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .bouncyClickable { mode = i }
                        ) {
                            Text(
                                label,
                                style = AuroraTypography.Caption.copy(
                                    color = if (active) AuroraColorTokens.AuroraAccent
                                    else AuroraColorTokens.TextCaption
                                ),
                                modifier = Modifier.padding(vertical = 7.dp),
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                HorizontalDivider(color = AuroraColorTokens.LinePrimary)

                Spacer(Modifier.height(IndustrialSpacing.Item))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    when (mode) {
                        0 -> {
                            Text(
                                "从文件导入（支持 .json 与内嵌卡数据的 .png）",
                                style = IndustrialTypography.Caption,
                                color = AuroraColorTokens.TextBody
                            )
                            Spacer(Modifier.height(IndustrialSpacing.Item))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(AuroraShapes.Medium)
                                    .background(AuroraColorTokens.Base2)
                                    .dashedAuroraBorder()
                                    .bouncyClickable(enabled = !importState.isLoading) {
                                        filePicker.launch("*/*")
                                    }
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Outlined.FileUpload,
                                        contentDescription = null,
                                        tint = AuroraColorTokens.AuroraAccent.copy(alpha = 0.6f),
                                        modifier = Modifier.size(40.dp)
                                    )
                                    Spacer(Modifier.height(10.dp))
                                    Text(
                                        "选择文件",
                                        style = AuroraTypography.Caption.copy(
                                            color = AuroraColorTokens.AuroraAccent
                                        )
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        "支持 .json / 内嵌卡数据的 .png",
                                        style = IndustrialTypography.Tech,
                                        color = AuroraColorTokens.TextCaption
                                    )
                                }
                            }
                        }
                        1 -> {
                            OutlinedTextField(
                                value = importState.jsonText,
                                onValueChange = vm::onJsonTextChanged,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = IndustrialSpacing.InputHeight * 4, max = IndustrialSpacing.InputHeight * 8),
                                label = { Text("JSON 文本", style = IndustrialTypography.Tech) },
                                placeholder = { Text("在此粘贴 SillyTavern 角色卡 JSON …", style = IndustrialTypography.Tech, color = AuroraColorTokens.TextCaption) },
                                isError = importState.errorMessage != null,
                                supportingText = {
                                    importState.errorMessage?.let {
                                        Text(it, style = IndustrialTypography.Tech, color = AuroraColorTokens.SignalError)
                                    }
                                },
                                keyboardOptions = KeyboardOptions(
                                    capitalization = KeyboardCapitalization.None,
                                    keyboardType = KeyboardType.Text
                                ),
                                shape = AuroraShapes.Small
                            )
                            Spacer(Modifier.height(IndustrialSpacing.Item))
                            Surface(
                                color = AuroraColorTokens.Base2,
                                shape = AuroraShapes.Small,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .bouncyClickable(
                                        enabled = importState.jsonText.isNotBlank() && !importState.isLoading
                                    ) { vm.parse() }
                            ) {
                                Text(
                                    "解析 JSON",
                                    style = IndustrialTypography.Caption,
                                    color = if (importState.jsonText.isNotBlank()) AuroraColorTokens.SignalThinking
                                    else AuroraColorTokens.SignalNeutral,
                                    modifier = Modifier.padding(
                                        horizontal = IndustrialSpacing.Item,
                                        vertical = IndustrialSpacing.Item
                                    )
                                )
                            }
                        }
                        else -> GenerateStep(
                            genVm = genVm,
                            genState = genState
                        )
                    }

                    if (importState.isLoading) {
                        Box(modifier = Modifier.fillMaxWidth().padding(IndustrialSpacing.Panel), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(modifier = Modifier.size(IndustrialSpacing.IconLarge))
                        }
                    }

                    // 解析结果预览与操作
                    importState.parsedCard?.let { card ->
                        Spacer(Modifier.height(IndustrialSpacing.Item))
                        ParsedPreview(card = card)
                        Spacer(Modifier.height(IndustrialSpacing.Item))
                        Row(horizontalArrangement = Arrangement.spacedBy(IndustrialSpacing.Item)) {
                            Surface(
                                color = AuroraColorTokens.Base2,
                                shape = AuroraShapes.Small,
                                modifier = Modifier
                                    .weight(1f)
                                    .bouncyClickable { vm.saveAsyncWithFeedback(card) { _, _ -> } }
                            ) {
                                Text(
                                    "保存为参考卡",
                                    style = IndustrialTypography.Tech,
                                    color = AuroraColorTokens.SignalThinking,
                                    modifier = Modifier.padding(
                                        horizontal = IndustrialSpacing.Item,
                                        vertical = IndustrialSpacing.Item
                                    )
                                )
                            }
                            Surface(
                                color = AuroraColorTokens.SignalWarning,
                                shape = AuroraShapes.Small,
                                modifier = Modifier
                                    .weight(1f)
                                    .bouncyClickable {
                                        vm.saveAsyncWithFeedback(card) { _, _ -> }
                                        onOpenInWorkspace(card)
                                    }
                            ) {
                                Text(
                                    "打开到工作台",
                                    style = IndustrialTypography.Tech,
                                    color = AuroraColorTokens.Base0,
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
}

/** AI 生成步骤（GenerateViewModel + 用例逻辑） */
@Composable
private fun GenerateStep(
    genVm: GenerateViewModel,
    genState: GenerateState
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(IndustrialSpacing.Item)
    ) {
        when (genState) {
            is GenerateState.Loading -> {
                Text(
                    genState.progressMessage,
                    style = IndustrialTypography.Tech,
                    color = AuroraColorTokens.SignalWarning
                )
                CircularProgressIndicator(modifier = Modifier.size(IndustrialSpacing.IconLarge))
            }
            is GenerateState.Success -> {
                Text(
                    "生成完成",
                    style = IndustrialTypography.SectionLabel,
                    color = AuroraColorTokens.SignalSuccess
                )
                ParsedPreview(card = genState.generatedCard)
                Row(horizontalArrangement = Arrangement.spacedBy(IndustrialSpacing.Item)) {
                    Surface(
                        color = AuroraColorTokens.SignalWarning,
                        shape = AuroraShapes.Small,
                        modifier = Modifier
                            .weight(1f)
                            .bouncyClickable { genVm.backToIdle() }
                    ) {
                        Text(
                            "完成",
                            style = IndustrialTypography.Tech,
                            color = AuroraColorTokens.Base0,
                            modifier = Modifier.padding(
                                horizontal = IndustrialSpacing.Item,
                                vertical = IndustrialSpacing.Item
                            )
                        )
                    }
                }
            }
            else -> {
                val idle = genState as? GenerateState.Idle
                val error = genState as? GenerateState.Error
                val description = idle?.description ?: error?.description ?: ""
                val styleTemplate = idle?.styleTemplate ?: error?.styleTemplate

                OutlinedTextField(
                    value = description,
                    onValueChange = genVm::onDescriptionChanged,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = IndustrialSpacing.InputHeight * 3, max = IndustrialSpacing.InputHeight * 6),
                    label = { Text("新角色描述", style = IndustrialTypography.Tech) },
                    placeholder = { Text("例如：一个穿越到现代的古代剑客……", style = IndustrialTypography.Tech, color = AuroraColorTokens.TextCaption) },
                    shape = AuroraShapes.Small
                )
                error?.let {
                    Text(
                        it.message,
                        style = IndustrialTypography.Tech,
                        color = AuroraColorTokens.SignalError
                    )
                }
                Text(
                    "风格模板",
                    style = IndustrialTypography.SectionLabel,
                    color = AuroraColorTokens.TextCaption
                )
                Row(horizontalArrangement = Arrangement.spacedBy(IndustrialSpacing.Item)) {
                    genVm.getStyleTemplates().take(3).forEach { tmpl ->
                        val active = styleTemplate?.id == tmpl.id
                        Surface(
                            color = if (active) AuroraColorTokens.SignalWarning else AuroraColorTokens.Base2,
                            shape = AuroraShapes.Small,
                            modifier = Modifier.bouncyClickable {
                                genVm.onStyleTemplateSelected(if (active) null else tmpl)
                            }
                        ) {
                            Text(
                                tmpl.name,
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
                    "生成角色卡",
                    style = IndustrialTypography.Caption,
                    color = if (description.isNotBlank()) AuroraColorTokens.SignalThinking
                    else AuroraColorTokens.SignalNeutral,
                    modifier = Modifier
                        .bouncyClickable(enabled = description.isNotBlank()) { genVm.generate() }
                        .padding(vertical = IndustrialSpacing.Tight)
                )
            }
        }
    }
}

/** 解析结果预览 */
@Composable
private fun ParsedPreview(card: CharacterCard) {
    Surface(
        color = AuroraColorTokens.Base2,
        shape = AuroraShapes.Medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(IndustrialSpacing.Panel),
            verticalArrangement = Arrangement.spacedBy(IndustrialSpacing.Tight)
        ) {
            Text(
                card.name.ifBlank { "未命名角色" },
                style = IndustrialTypography.PanelTitle,
                color = AuroraColorTokens.TextTitle
            )
            Text(
                card.description.ifBlank { "（无描述）" },
                style = IndustrialTypography.Tech,
                color = AuroraColorTokens.TextCaption,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                listOfNotNull(
                    "v${card.specVersion}",
                    card.characterBook?.entries?.size?.let { "世界书${it}条" },
                    card.regexScripts.size.takeIf { it > 0 }?.let { "正则${it}条" }
                ).joinToString(" · "),
                style = IndustrialTypography.Tech,
                color = AuroraColorTokens.TextTech
            )
        }
    }
}

private fun Modifier.dashedAuroraBorder(): Modifier = drawBehind {
    val strokeWidth = 1.5.dp.toPx()
    val dash = PathEffect.dashPathEffect(
        intervals = floatArrayOf(12.dp.toPx(), 8.dp.toPx()),
        phase = 0f
    )
    drawRoundRect(
        color = AuroraColorTokens.AuroraAccent.copy(alpha = 0.35f),
        topLeft = Offset.Zero,
        size = Size(size.width, size.height),
        cornerRadius = CornerRadius(18.dp.toPx()),
        style = Stroke(width = strokeWidth, pathEffect = dash)
    )
}