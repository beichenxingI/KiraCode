package com.kirakira.kiracode.ui.workspace

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kirakira.kiracode.domain.model.CharacterCard
import com.kirakira.kiracode.domain.model.st.RegexPlacement
import com.kirakira.kiracode.domain.model.st.StBookEntry
import com.kirakira.kiracode.domain.model.st.StCharacterBook
import com.kirakira.kiracode.domain.model.st.StRegexScript
import com.kirakira.kiracode.ui.theme.AuroraColorTokens

// ==================== 基本信息 ====================
// （WorkspaceCardSheet 外壳已删除；此处仅保留被监控面板预览模式复用的 Tab 组件）

@Composable
internal fun BasicInfoTab(card: CharacterCard, onChange: (CharacterCard) -> Unit) {
    LazyColumn(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            SheetField("角色名称", card.name) { onChange(card.copy(name = it)) }
        }
        item {
            SheetField("昵称", card.nickname.orEmpty()) { onChange(card.copy(nickname = it.takeIf { s -> s.isNotBlank() })) }
        }
        item {
            SheetField("创作者", card.creator) { onChange(card.copy(creator = it)) }
        }
        item {
            SheetField("版本 (character_version)", card.version) { onChange(card.copy(version = it)) }
        }
        item {
            SheetField("标签（逗号分隔）", card.tags.joinToString(", ")) { value ->
                onChange(card.copy(tags = value.split(',', '，').map { t -> t.trim() }.filter { it.isNotEmpty() }))
            }
        }
        item {
            SheetField("规格", card.spec, readOnly = true) {}
        }
    }
}

// ==================== 世界书 ====================

@Composable
internal fun WorldBookTab(card: CharacterCard, onChange: (CharacterCard) -> Unit) {
    val book = card.characterBook ?: StCharacterBook()
    var expandedUid by remember { mutableStateOf<Long?>(null) }

    Box(Modifier.fillMaxWidth()) {
        LazyColumn(
            Modifier.fillMaxWidth().heightIn(max = 430.dp).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (book.entries.isEmpty()) {
                item {
                    EmptyHint("暂无世界书条目。点击右下角 + 新增；世界书用于管理世界观、专有名词等按需注入的设定。")
                }
            }
            items(book.entries, key = { it.id }) { entry ->
                val expanded = expandedUid == entry.id
                Surface(
                    color = AuroraColorTokens.Base2,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().clickable { expandedUid = if (expanded) null else entry.id }
                ) {
                    Column(Modifier.padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // 状态灯：常驻=蓝 常规=绿 停用=灰
                            Box(Modifier.size(8.dp).background(
                                when {
                                    !entry.enabled -> AuroraColorTokens.TextCaption
                                    entry.constant -> AuroraColorTokens.AuroraAccent
                                    else -> AuroraColorTokens.SignalWarning
                                },
                                CircleShape
                            ))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = entry.comment.ifBlank { "条目 #${entry.id}" },
                                fontSize = 13.sp, fontWeight = FontWeight.Medium,
                                color = AuroraColorTokens.TextBody,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = {
                                onChange(card.copy(characterBook = book.copy(entries = book.entries.filterNot { it.id == entry.id })))
                            }, modifier = Modifier.size(26.dp)) {
                                Icon(Icons.Filled.Delete, "删除", Modifier.size(15.dp), tint = AuroraColorTokens.SignalError)
                            }
                            Icon(expandIcon(expanded), null, tint = AuroraColorTokens.TextCaption, modifier = Modifier.size(16.dp))
                        }
                        if (!expanded) {
                            val missingKeys = !entry.constant && entry.keys.all { it.isBlank() }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Filled.VpnKey,
                                    "关键词",
                                    tint = if (missingKeys) AuroraColorTokens.SignalWarning
                                    else AuroraColorTokens.AuroraAccent,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = if (missingKeys) "⚠ 未设触发词"
                                    else entry.keys.joinToString("/"),
                                    fontSize = 11.sp,
                                    color = if (missingKeys) AuroraColorTokens.SignalWarning
                                    else AuroraColorTokens.AuroraAccent,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        } else {
                            EntryEditor(entry) { updated ->
                                onChange(card.copy(characterBook = book.copy(entries = book.entries.map { if (it.id == entry.id) updated else it })))
                            }
                        }
                    }
                }
            }
        }

        // 新增条目按钮
        Surface(
            color = AuroraColorTokens.AuroraAccent,
            shape = CircleShape,
            shadowElevation = 4.dp,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 8.dp)
                .clickable {
                    val nextId = (book.entries.maxOfOrNull { it.id } ?: -1L) + 1
                    val newEntry = StBookEntry(id = nextId, comment = "新条目 #${nextId + 1}", position = "before_char")
                    onChange(card.copy(characterBook = book.copy(entries = book.entries + newEntry)))
                    expandedUid = nextId
                }
        ) {
            Icon(Icons.Filled.Add, "新增条目", tint = AuroraColorTokens.Base0, modifier = Modifier.padding(10.dp))
        }
    }
}

private fun expandIcon(expanded: Boolean): androidx.compose.ui.graphics.vector.ImageVector =
    if (expanded) Icons.Filled.KeyboardArrowUp
    else Icons.Filled.KeyboardArrowDown

@Composable
private fun EntryEditor(entry: StBookEntry, onChange: (StBookEntry) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SheetField("标题 (comment)", entry.comment) { onChange(entry.copy(comment = it)) }
        SheetField("触发关键词（逗号分隔）", entry.keys.joinToString(", ")) { value ->
            onChange(entry.copy(keys = value.split(',', '，').map { k -> k.trim() }.filter { it.isNotEmpty() }))
        }
        SheetField("次级关键词（逗号分隔）", entry.secondaryKeys.joinToString(", ")) { value ->
            onChange(entry.copy(secondaryKeys = value.split(',', '，').map { k -> k.trim() }.filter { it.isNotEmpty() }))
        }
        SheetField("内容 (content)", entry.content, minLines = 4) { onChange(entry.copy(content = it)) }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text("常驻 (constant)", fontSize = 12.sp, color = AuroraColorTokens.TextCaption)
            Switch(checked = entry.constant, onCheckedChange = { onChange(entry.copy(constant = it)) }, modifier = Modifier.height(24.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text("启用", fontSize = 12.sp, color = AuroraColorTokens.TextCaption)
            Switch(checked = entry.enabled, onCheckedChange = { onChange(entry.copy(enabled = it)) }, modifier = Modifier.height(24.dp))
        }
        SheetField("插入顺序 (越小越靠前)", entry.insertionOrder.toString()) { value ->
            onChange(entry.copy(insertionOrder = value.toIntOrNull() ?: entry.insertionOrder))
        }
        SheetField("位置 (before_char / after_char)", entry.position ?: "before_char") { value ->
            val pos = when (value.trim()) {
                "after_char", "1" -> "after_char"
                else -> "before_char"
            }
            onChange(entry.copy(position = pos))
        }
    }
}

// ==================== 正则 ====================

@Composable
internal fun RegexTab(card: CharacterCard, onChange: (CharacterCard) -> Unit) {
    var editingId by remember { mutableStateOf<String?>(null) }
    val scripts = card.regexScripts

    Box(Modifier.fillMaxWidth()) {
        LazyColumn(
            Modifier.fillMaxWidth().heightIn(max = 430.dp).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (scripts.isEmpty()) {
                item { EmptyHint("暂无正则脚本。正则用于对 AI 输出做替换美化，例如去除 <think> 标签。") }
            }
            items(scripts, key = { it.id }) { script ->
                val expanded = editingId == script.id
                Surface(
                    color = AuroraColorTokens.Base2,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().clickable { editingId = if (expanded) null else script.id }
                ) {
                    Column(Modifier.padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(8.dp).background(
                                if (script.disabled) AuroraColorTokens.TextCaption else AuroraColorTokens.AuroraAccent,
                                CircleShape
                            ))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = script.scriptName.ifBlank { "未命名规则" },
                                fontSize = 13.sp, fontWeight = FontWeight.Medium,
                                color = AuroraColorTokens.TextBody,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = {
                                onChange(card.copy(regexScripts = scripts.filterNot { it.id == script.id }))
                            }, modifier = Modifier.size(26.dp)) {
                                Icon(Icons.Filled.Delete, "删除", Modifier.size(15.dp), tint = AuroraColorTokens.SignalError)
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Search, "正则", tint = AuroraColorTokens.TextCaption, modifier = Modifier.size(12.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = script.findRegex,
                                fontSize = 11.sp,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                color = AuroraColorTokens.TextCaption,
                                maxLines = if (expanded) 6 else 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (expanded) {
                            RegexEditor(script) { updated ->
                                onChange(card.copy(regexScripts = scripts.map { if (it.id == script.id) updated else it }))
                            }
                        }
                    }
                }
            }
        }

        Surface(
            color = AuroraColorTokens.AuroraAccent,
            shape = CircleShape,
            shadowElevation = 4.dp,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 8.dp)
                .clickable {
                    val newScript = StRegexScript(scriptName = "新规则", placement = listOf(RegexPlacement.AI_OUTPUT))
                    onChange(card.copy(regexScripts = scripts + newScript))
                    editingId = newScript.id
                }
        ) {
            Icon(Icons.Filled.Add, "新增正则", tint = AuroraColorTokens.Base0, modifier = Modifier.padding(10.dp))
        }
    }
}

@Composable
private fun RegexEditor(script: StRegexScript, onChange: (StRegexScript) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SheetField("名称 (scriptName)", script.scriptName) { onChange(script.copy(scriptName = it)) }
        SheetField("查找正则 (findRegex)", script.findRegex, minLines = 2, monospace = true) { onChange(script.copy(findRegex = it)) }
        SheetField("替换为 (replaceString)", script.replaceString, minLines = 2, monospace = true) { onChange(script.copy(replaceString = it)) }
        SheetField("作用域 (1=用户输入 2=AI输出)", script.placement.joinToString(",")) { value ->
            val parsed = value.split(',').mapNotNull { it.trim().toIntOrNull() }.filter { it in 1..5 }
            if (parsed.isNotEmpty()) onChange(script.copy(placement = parsed))
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text("禁用", fontSize = 12.sp, color = AuroraColorTokens.TextCaption)
            Switch(checked = script.disabled, onCheckedChange = { onChange(script.copy(disabled = it)) }, modifier = Modifier.height(24.dp))
        }
    }
}

// ==================== 实时预览 ====================

/**
 * 角色卡实时预览：只读渲染全部字段，数据源为当前工作卡实例，
 * Agent 操作应用 / 手动编辑后即时反映。
 */
@Composable
internal fun CardPreviewTab(card: CharacterCard) {
    LazyColumn(
        Modifier.fillMaxWidth().heightIn(max = 470.dp).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item { PreviewSection("名称", card.name.ifBlank { "（未命名）" }) }
        if (card.nickname != null) item { PreviewSection("昵称", card.nickname!!) }
        item { PreviewSection("描述 (description)", card.description.ifBlank { "（空）" }) }
        item { PreviewSection("性格 (personality)", card.personality.ifBlank { "（空）" }) }
        item { PreviewSection("场景 (scenario)", card.scenario.ifBlank { "（空）" }) }
        item { PreviewSection("开场白 (first_mes)", card.firstMessage.ifBlank { "（空）" }, monospace = false, maxLines = 10) }
        if (card.alternateGreetings.isNotEmpty()) {
            item {
                PreviewSection(
                    "备选开场白 (${card.alternateGreetings.size})",
                    card.alternateGreetings.mapIndexed { i, g -> "[$i] $g" }.joinToString("\n\n"),
                    maxLines = 12
                )
            }
        }
        item { PreviewSection("对话示例 (mes_example)", card.exampleDialogue.ifBlank { "（空）" }, monospace = true, maxLines = 10) }
        if (card.systemPrompt.isNotBlank()) item { PreviewSection("系统提示词", card.systemPrompt, maxLines = 6) }
        if (card.postHistoryInstructions.isNotBlank()) item { PreviewSection("历史末尾指令 (jailbreak)", card.postHistoryInstructions, maxLines = 5) }
        if (card.creatorNotes.isNotBlank()) item { PreviewSection("创作者备注", card.creatorNotes, maxLines = 5) }
        if (card.tags.isNotEmpty()) {
            item { PreviewSection("标签", card.tags.joinToString("、")) }
        }

        // 世界书条目摘要
        val book = card.characterBook
        if (book != null && book.entries.isNotEmpty()) {
            item { PreviewSectionHeader("世界书 (${book.entries.size} 条)") }
            items(book.entries, key = { "pv${it.id}" }) { entry ->
                Surface(
                    color = AuroraColorTokens.Base2,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(6.dp).background(
                                when {
                                    !entry.enabled -> AuroraColorTokens.TextCaption
                                    entry.constant -> AuroraColorTokens.AuroraAccent
                                    else -> AuroraColorTokens.SignalWarning
                                },
                                CircleShape
                            ))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                entry.comment.ifBlank { "#${entry.id}" },
                                fontSize = 12.sp, fontWeight = FontWeight.Medium,
                                color = AuroraColorTokens.TextBody
                            )
                        }
                        Spacer(Modifier.height(3.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.VpnKey, "关键词", tint = AuroraColorTokens.AuroraAccent, modifier = Modifier.size(11.dp))
                            Spacer(Modifier.width(3.dp))
                            Text(
                                "${entry.keys.joinToString("/").ifBlank { "-" }}" +
                                        (if (entry.constant) " · 常驻" else "") +
                                        " · 顺序${entry.insertionOrder}",
                                fontSize = 10.sp,
                                color = AuroraColorTokens.AuroraAccent
                            )
                        }
                        if (entry.content.isNotBlank()) {
                            Text(
                                entry.content,
                                fontSize = 11.sp,
                                color = AuroraColorTokens.TextCaption,
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        // 正则摘要
        if (card.regexScripts.isNotEmpty()) {
            item { PreviewSectionHeader("正则脚本 (${card.regexScripts.size} 条)") }
            items(card.regexScripts, key = { "pr${it.id}" }) { script ->
                Surface(
                    color = AuroraColorTokens.Base2,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(10.dp)) {
                        Text(
                            (if (script.disabled) "[停用] " else "") + script.scriptName.ifBlank { "未命名" },
                            fontSize = 12.sp, fontWeight = FontWeight.Medium,
                            color = if (script.disabled) AuroraColorTokens.TextCaption else AuroraColorTokens.TextBody
                        )
                        Text(
                            script.findRegex,
                            fontSize = 10.sp,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            color = AuroraColorTokens.SignalWarning,
                            maxLines = 2, overflow = TextOverflow.Ellipsis
                        )
                        if (script.replaceString.isNotBlank()) {
                            Text(
                                "→ ${script.replaceString}",
                                fontSize = 10.sp,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                color = AuroraColorTokens.TextCaption,
                                maxLines = 2, overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        item {
            Text(
                text = "${card.spec} · ${card.version} · by ${card.creator}",
                fontSize = 10.sp,
                color = AuroraColorTokens.TextCaption,
                modifier = Modifier.padding(vertical = 6.dp)
            )
        }
    }
}

@Composable
private fun PreviewSection(title: String, body: String, monospace: Boolean = false, maxLines: Int = 6) {
    Surface(
        color = AuroraColorTokens.Base2,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(10.dp)) {
            Text(title, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = AuroraColorTokens.AuroraAccent)
            Spacer(Modifier.height(3.dp))
            Text(
                body,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                fontFamily = if (monospace) androidx.compose.ui.text.font.FontFamily.Monospace else null,
                color = AuroraColorTokens.TextBody,
                maxLines = maxLines,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun PreviewSectionHeader(title: String) {
    Text(
        title,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        color = AuroraColorTokens.TextBody,
        modifier = Modifier.padding(top = 4.dp)
    )
}

// ==================== 公共小组件 ====================

@Composable
private fun SheetField(
    label: String,
    value: String,
    readOnly: Boolean = false,
    minLines: Int = 1,
    monospace: Boolean = false,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        readOnly = readOnly,
        label = { Text(label, fontSize = 11.sp) },
        minLines = minLines,
        textStyle = androidx.compose.ui.text.TextStyle(
            fontSize = 13.sp,
            fontFamily = if (monospace) androidx.compose.ui.text.font.FontFamily.Monospace else null,
            color = AuroraColorTokens.TextBody
        ),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
internal fun EmptyHint(text: String) {
    Surface(
        color = AuroraColorTokens.Base2,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            color = AuroraColorTokens.TextCaption,
            modifier = Modifier.padding(14.dp)
        )
    }
}