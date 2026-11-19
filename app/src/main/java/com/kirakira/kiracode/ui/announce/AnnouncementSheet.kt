package com.kirakira.kiracode.ui.announce

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

/**
 * 公告/下载 KiraKira 弹窗：展示最新版本信息并提供下载跳转。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnnouncementSheet(
    state: AnnounceUiState,
    onDismiss: () -> Unit,
    onRetry: () -> Unit = {}
) {
    val context = LocalContext.current

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (val s = state) {
                is AnnounceUiState.Loading -> {
                    Box(modifier = Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is AnnounceUiState.Failed -> {
                    Text("获取公告失败", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text(s.message, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.clickable { onRetry() }
                    ) {
                        Text(
                            "重试", fontSize = 13.sp, color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp)
                        )
                    }
                }
                is AnnounceUiState.Ready -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            s.announcement.title.ifBlank { "KiraKira 最新公告" },
                            fontSize = 17.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        if (s.announcement.forceUpdate) {
                            Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(6.dp)) {
                                Text(
                                    "强制更新", fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Text(
                        "版本 ${s.announcement.version.ifBlank { "未知" }} · 最低兼容 ${s.announcement.minAppVersion.ifBlank { "-" }}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    s.announcement.imageUrl?.takeIf { it.isNotBlank() }?.let { url ->
                        AsyncImage(
                            model = url,
                            contentDescription = "公告配图",
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 200.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )
                    }
                    Text(
                        s.announcement.content.ifBlank { "（无正文）" },
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    val url = s.announcement.downloadUrl
                    Surface(
                        color = if (url.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = url.isNotBlank()) {
                                runCatching {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                }
                            }
                    ) {
                        Text(
                            text = if (url.isNotBlank()) "下载 KiraKira" else "暂无下载链接",
                            fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                            color = if (url.isNotBlank()) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                        )
                    }
                }
            }
        }
    }
}

sealed interface AnnounceUiState {
    data object Loading : AnnounceUiState
    data class Failed(val message: String) : AnnounceUiState
    data class Ready(val announcement: com.kirakira.kiracode.data.remote.Announcement) : AnnounceUiState
}
