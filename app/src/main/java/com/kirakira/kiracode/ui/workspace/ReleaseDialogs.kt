package com.kirakira.kiracode.ui.workspace

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kirakira.kiracode.domain.model.CharacterCard
import com.kirakira.kiracode.ui.common.AuroraDialog
import com.kirakira.kiracode.ui.common.AuroraDialogActions
import com.kirakira.kiracode.ui.common.AuroraDialogCaption
import com.kirakira.kiracode.ui.common.AuroraDialogText
import com.kirakira.kiracode.ui.common.AuroraDialogTitle

/** AGPLv3 声明文案（核心条款摘要；完整协议见项目根目录 LICENSE 文件） */
const val AGPL_DECLARATION: String =
    "本工具为开源项目，采用 GNU Affero General Public License v3.0（AGPLv3）协议。\n\n" +
    "任何基于本代码的衍生服务，无论以何种形式运行（包括网络服务形式），" +
    "都必须向用户开放完整源代码。本工具旨在降低个人玩家创作角色卡的门槛，" +
    "严禁利用本代码搭建闭源商业化服务。\n\n" +
    "Copyright (C) 2026 北辰星"

/**
 * KiraCode V2 Beta 相关对话框：首启 AGPLv3 声明引导、灵魂训练。
 */

/** 首次启动许可与项目声明 */
@Composable
fun FirstLaunchLicenseDialog(
    onAgree: () -> Unit,
    onDismiss: () -> Unit
) {
    AuroraDialog(
        onDismissRequest = onDismiss,
        maxWidth = 480.dp,
        maxHeight = 560.dp,
        scrollable = true
    ) {
        AuroraDialogTitle("欢迎使用 KiraCard")
        AuroraDialogCaption("AI 角色卡创作 Agent · KiraCard V2 Beta")
        AuroraDialogText(AGPL_DECLARATION)
        AuroraDialogActions(
            confirmText = "同意并开始创作",
            onConfirm = onAgree,
            onDismiss = onDismiss,
            dismissText = "退出"
        )
    }
}