package com.kirakira.kiracode.ui.export

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kirakira.kiracode.domain.model.CharacterCard
import com.kirakira.kiracode.domain.model.ExportFormat
import com.kirakira.kiracode.domain.model.st.StCardCodec
import com.kirakira.kiracode.ui.common.AuroraDialog
import com.kirakira.kiracode.ui.common.AuroraDialogActions
import com.kirakira.kiracode.ui.common.AuroraDialogCaption
import com.kirakira.kiracode.ui.common.AuroraDialogTextField
import com.kirakira.kiracode.ui.common.AuroraDialogTitle
import com.kirakira.kiracode.ui.theme.AuroraColorTokens

/**
 * 导出对话框（V2.1 CodeX 极简）：模态半透明背景。
 *
 * - 格式单选：PNG（含封面+卡数据，推荐）/ JSON；
 * - 文件名输入（占位：角色名_时间戳）；
 * - 预估大小（JSON=实际字节；PNG=封面+卡数据估算）；
 * - 导出（主按钮紫）→ 进度条 → 完成后关闭（Toast 由宿主 Snackbar 提示）。
 */
@Composable
fun ExportDialog(
    card: CharacterCard,
    initialFormat: ExportFormat = ExportFormat.PNG,
    isExporting: Boolean = false,
    onDismiss: () -> Unit,
    onExport: (ExportFormat, fileName: String?) -> Unit
) {
    var format by remember { mutableStateOf(initialFormat) }
    var fileName by remember { mutableStateOf("") }
    val defaultName = "${card.name.ifBlank { "角色卡" }}_${System.currentTimeMillis() / 1000}"
    val effectiveName = fileName.trim().ifBlank { defaultName }

    // 大小预估
    val estimatedBytes = remember(format, card) {
        when (format) {
            ExportFormat.JSON ->
                StCardCodec.toJsonString(card, pretty = true).toByteArray(Charsets.UTF_8).size.toLong()
            ExportFormat.PNG -> {
                val coverBytes = card.avatar
                    ?.takeIf { it.startsWith("data:") }
                    ?.let { (it.length * 3L) / 4 } ?: 0L
                coverBytes + 220_000L // 基底画布 + 卡数据估算
            }
        }
    }

    AuroraDialog(
        onDismissRequest = { if (!isExporting) onDismiss() },
        scrollable = true
    ) {
        AuroraDialogTitle("导出角色卡")

        AuroraDialogCaption("格式")
        ExportFormatOption(
            selected = format == ExportFormat.PNG,
            label = "PNG",
            hint = "含封面图 + 卡数据，可直接拖进酒馆",
            onClick = { if (!isExporting) format = ExportFormat.PNG }
        )
        ExportFormatOption(
            selected = format == ExportFormat.JSON,
            label = "JSON",
            hint = "纯卡数据，体积最小",
            onClick = { if (!isExporting) format = ExportFormat.JSON }
        )

        AuroraDialogTextField(
            value = fileName,
            onValueChange = { if (!isExporting) fileName = it },
            label = "文件名",
            placeholder = defaultName,
            singleLine = true,
            enabled = !isExporting
        )

        AuroraDialogCaption("预计 ${formatBytes(estimatedBytes)} · 保存到 Download 目录")

        if (isExporting) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text(
                "导出中…",
                fontSize = 12.sp,
                color = AuroraColorTokens.AuroraAccent
            )
        }

        AuroraDialogActions(
            confirmText = if (isExporting) "导出中…" else "导出",
            onConfirm = { onExport(format, effectiveName) },
            onDismiss = onDismiss,
            confirmEnabled = !isExporting,
            dismissEnabled = !isExporting,
            confirmColor = if (isExporting) AuroraColorTokens.TextCaption
            else AuroraColorTokens.AuroraAccent
        )
    }
}

@Composable
private fun ExportFormatOption(
    selected: Boolean,
    label: String,
    hint: String,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
            enabled = true,
            colors = RadioButtonDefaults.colors(
                selectedColor = AuroraColorTokens.AuroraAccent,
                unselectedColor = AuroraColorTokens.TextCaption
            )
        )
        Column {
            Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(hint, fontSize = 11.sp, color = AuroraColorTokens.TextCaption)
        }
    }
}

/** 字节 → 人类可读 */
private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
    bytes >= 1024 -> String.format("%.1f KB", bytes / 1024.0)
    else -> "$bytes B"
}
