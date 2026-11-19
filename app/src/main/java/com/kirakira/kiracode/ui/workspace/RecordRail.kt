package com.kirakira.kiracode.ui.workspace

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kirakira.kiracode.domain.model.ChatSession
import com.kirakira.kiracode.ui.theme.AuroraColorTokens
import com.kirakira.kiracode.ui.theme.AuroraShapes
import com.kirakira.kiracode.ui.theme.IndustrialSpacing
import com.kirakira.kiracode.ui.theme.IndustrialTypography
import com.kirakira.kiracode.ui.theme.bouncyClickable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 工作记录（会话 → 记录视图；置顶=isPinned） */
data class WorkRecord(
    val id: Long,
    val cardName: String,
    val lastEditTime: Long,
    val isFavorite: Boolean
)

internal fun sessionsToRecords(sessions: List<ChatSession>): List<WorkRecord> =
    sessions.map { WorkRecord(it.id, it.title, it.lastMessageTime, it.isPinned) }

internal fun List<WorkRecord>.sortedFavoritesFirst(): List<WorkRecord> =
    sortedWith(compareByDescending<WorkRecord> { it.isFavorite }.thenByDescending { it.lastEditTime })

/** 记录行时间：yyyy-MM-dd HH:mm */
internal fun formatRecordTime(timestamp: Long): String {
    if (timestamp <= 0L) return ""
    return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))
}

/** 左侧工作记录栏：完整滚动记录列表 + 底部功能入口；收起时为图标轨 */
@Composable
fun RecordRail(
    sessions: List<ChatSession>,
    currentSessionId: Long?,
    collapsed: Boolean,
    onSessionClick: (Long) -> Unit,
    onToggleFavorite: (id: Long, isFavorite: Boolean) -> Unit,
    onShowAllRecords: () -> Unit,
    onImportCard: () -> Unit,
    onCardRepositoryClick: () -> Unit,
    onNavigateToFinished: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = AuroraColorTokens.Base1,
        modifier = modifier
            .fillMaxHeight()
            .padding(vertical = 8.dp)
    ) {
        if (collapsed) {
            RecordIconsRail(
                onShowAllRecords = onShowAllRecords,
                onImportCard = onImportCard,
                onCardRepositoryClick = onCardRepositoryClick,
                onNavigateToFinished = onNavigateToFinished,
                onSettingsClick = onSettingsClick
            )
        } else {
            val records = sessionsToRecords(sessions).sortedFavoritesFirst()
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IndustrialSpacing.StatusBarHeight)
                        .padding(horizontal = IndustrialSpacing.Panel)
                ) {
                    Text(
                        "工作记录",
                        style = IndustrialTypography.SectionLabel,
                        color = AuroraColorTokens.TextCaption
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        "全部",
                        style = IndustrialTypography.Tech,
                        color = AuroraColorTokens.TextCaption,
                        modifier = Modifier
                            .bouncyClickable(onClick = onShowAllRecords)
                            .padding(horizontal = IndustrialSpacing.Tight, vertical = IndustrialSpacing.Tight)
                    )
                }
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(records, key = { it.id }) { record ->
                        RecordItem(
                            record = record,
                            selected = record.id == currentSessionId,
                            onSessionClick = onSessionClick,
                            onToggleFavorite = onToggleFavorite
                        )
                    }
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = IndustrialSpacing.Compact)
                ) {
                    RailMenuRow(Icons.Filled.Upload, "导入角色卡", onImportCard)
                    RailMenuRow(Icons.Filled.Inventory2, "卡片仓库", onCardRepositoryClick)
        RailMenuRow(Icons.Filled.CheckCircle, "成品区", onNavigateToFinished)
        RailMenuRow(Icons.Filled.Settings, "设置", onSettingsClick)
                }
            }
        }
    }
}

/** 记录行：角色名 · 日期时间；选中态=黄色左条 + Base2 底 */
@Composable
fun RecordItem(
    record: WorkRecord,
    selected: Boolean,
    onSessionClick: (Long) -> Unit,
    onToggleFavorite: (id: Long, isFavorite: Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (selected) AuroraColorTokens.AuroraAccent.copy(alpha = 0.12f)
                else Color.Transparent
            )
            .heightIn(min = 48.dp)
            .bouncyClickable { onSessionClick(record.id) }
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = IndustrialSpacing.Panel, vertical = IndustrialSpacing.Tight)
        ) {
            Text(
                text = record.cardName.ifBlank { "新会话" },
                style = IndustrialTypography.Caption,
                color = if (selected) AuroraColorTokens.AuroraAccent else AuroraColorTokens.TextBody,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = formatRecordTime(record.lastEditTime),
                style = IndustrialTypography.Tech,
                color = AuroraColorTokens.TextCaption
            )
        }
        IconButton(
            onClick = { onToggleFavorite(record.id, !record.isFavorite) },
            modifier = Modifier.size(IndustrialSpacing.InlineButtonSize)
        ) {
            Icon(
                imageVector = if (record.isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                contentDescription = if (record.isFavorite) "取消置顶" else "置顶",
                tint = if (record.isFavorite) AuroraColorTokens.KiraGold else AuroraColorTokens.SignalNeutral,
                modifier = Modifier.size(IndustrialSpacing.IconSmall)
            )
        }
    }
}

@Composable
private fun RailMenuRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(IndustrialSpacing.ButtonHeight)
            .bouncyClickable(onClick = onClick)
            .padding(horizontal = IndustrialSpacing.Panel)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = AuroraColorTokens.TextCaption,
            modifier = Modifier.size(IndustrialSpacing.IconMedium)
        )
        Spacer(Modifier.width(IndustrialSpacing.Item))
        Text(
            label,
            style = IndustrialTypography.Caption,
            color = AuroraColorTokens.TextBody
        )
    }
}

/** 收起态图标轨：全部记录 / 导入 / 仓库 / 成品区 / 设置 */
@Composable
private fun RecordIconsRail(
    onShowAllRecords: () -> Unit,
    onImportCard: () -> Unit,
    onCardRepositoryClick: () -> Unit,
    onNavigateToFinished: () -> Unit,
    onSettingsClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(IndustrialSpacing.Tight),
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = IndustrialSpacing.Compact)
    ) {
        RailIconButton(Icons.Filled.History, "全部工作记录", onShowAllRecords, selected = true)
        RailIconButton(Icons.Filled.Upload, "导入角色卡", onImportCard)
        RailIconButton(Icons.Filled.Inventory2, "卡片仓库", onCardRepositoryClick)
        RailIconButton(Icons.Filled.CheckCircle, "成品区", onNavigateToFinished)
        RailIconButton(Icons.Filled.Settings, "设置", onSettingsClick)
    }
}

@Composable
private fun RailIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
    selected: Boolean = false
) {
    Box(
        modifier = Modifier
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .size(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (selected) AuroraColorTokens.AuroraAccent.copy(alpha = 0.12f)
                else Color.Transparent
            )
            .bouncyClickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = description,
            tint = if (selected) AuroraColorTokens.AuroraAccent else AuroraColorTokens.TextCaption,
            modifier = Modifier.size(IndustrialSpacing.IconMedium)
        )
    }
}
