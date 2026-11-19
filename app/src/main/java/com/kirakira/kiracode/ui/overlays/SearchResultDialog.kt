package com.kirakira.kiracode.ui.overlays

import android.content.ActivityNotFoundException
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kirakira.kiracode.ui.common.AuroraDialog
import com.kirakira.kiracode.ui.common.AuroraDialogActions
import com.kirakira.kiracode.ui.common.AuroraDialogCaption
import com.kirakira.kiracode.ui.common.AuroraDialogTitle
import com.kirakira.kiracode.ui.theme.AuroraColorTokens
import com.kirakira.kiracode.ui.theme.AuroraShapes
import com.kirakira.kiracode.ui.theme.AuroraTypography

/** 单条搜索结果（浮窗展示用；与 AgentCardUi.Search 配套） */
data class SearchResultItem(
    val title: String,
    val url: String,
    val snippet: String
)

/**
 * 搜索结果预览浮窗：标题 / URL / 摘要，点击条目跳转浏览器。
 * 打开浏览器失败（无浏览器应用等）时静默忽略，不影响浮窗本身。
 */
@Composable
fun SearchResultDialog(
    query: String,
    results: List<SearchResultItem>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    AuroraDialog(
        onDismissRequest = onDismiss,
        maxWidth = 480.dp,
        maxHeight = 560.dp
    ) {
        AuroraDialogTitle("搜索结果")
        AuroraDialogCaption("查询：$query")

        if (results.isEmpty()) {
            AuroraDialogCaption("无结果数据")
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(results) { result ->
                    SearchResultCard(result = result) {
                        try {
                            context.startActivity(
                                android.content.Intent(
                                    android.content.Intent.ACTION_VIEW,
                                    android.net.Uri.parse(result.url)
                                )
                            )
                        } catch (_: ActivityNotFoundException) {
                        } catch (_: Exception) {
                        }
                    }
                }
            }
        }

        AuroraDialogActions(
            confirmText = "关闭",
            onConfirm = onDismiss
        )
    }
}

@Composable
private fun SearchResultCard(
    result: SearchResultItem,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = AuroraColorTokens.Base2,
        shape = AuroraShapes.Small
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = result.title,
                style = AuroraTypography.Body,
                color = AuroraColorTokens.AuroraAccent
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = result.url,
                style = AuroraTypography.Caption,
                color = AuroraColorTokens.TextCaption,
                maxLines = 2
            )
            if (result.snippet.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = result.snippet,
                    style = AuroraTypography.Caption,
                    color = AuroraColorTokens.TextBody
                )
            }
        }
    }
}
