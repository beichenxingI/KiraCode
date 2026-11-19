package com.kirakira.kiracode.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 通用 UI 状态组件
 * 包含空态、骨架屏、错误边界等
 */

// ============ 空态组件 ============

/**
 * 通用空态展示
 */
@Composable
fun KiraCodeEmptyState(
    modifier: Modifier = Modifier.fillMaxSize(),
    icon: ImageVector,
    title: String,
    message: String,
    actionText: String? = null,
    onActionClick: (() -> Unit)? = null
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(96.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
            Text(
                text = title,
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = message,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            actionText?.let { text ->
                onActionClick?.let { click ->
                    KiraCodePrimaryButton(
                        onClick = click,
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .width(200.dp)
                    ) {
                        Text(text = text)
                    }
                }
            }
        }
    }
}

/**
 * 预定义空态：无会话
 */
@Composable
fun EmptySessionsState(
    modifier: Modifier = Modifier.fillMaxSize(),
    onCreateClick: () -> Unit
) {
    KiraCodeEmptyState(
        modifier = modifier,
        icon = Icons.Filled.ChatBubbleOutline,
        title = "暂无会话",
        message = "点击右上角菜单创建新的聊天会话",
        actionText = "新建会话",
        onActionClick = onCreateClick
    )
}

/**
 * 预定义空态：无消息
 */
@Composable
fun EmptyMessagesState(
    modifier: Modifier = Modifier.fillMaxSize(),
    onSendFirstMessage: () -> Unit
) {
    KiraCodeEmptyState(
        modifier = modifier,
        icon = Icons.Filled.Message,
        title = "开始对话吧",
        message = "输入下方消息与 AI 助手开始聊天"
    )
}

/**
 * 预定义空态：无参考卡
 */
@Composable
fun EmptyReferenceCardsState(
    modifier: Modifier = Modifier.fillMaxSize(),
    onImportClick: () -> Unit
) {
    KiraCodeEmptyState(
        modifier = modifier,
        icon = Icons.Filled.LibraryAdd,
        title = "暂无参考卡",
        message = "导入 SillyTavern 角色卡作为风格参考",
        actionText = "导入参考卡",
        onActionClick = onImportClick
    )
}

/**
 * 预定义空态：搜索无结果
 */
@Composable
fun EmptySearchState(
    modifier: Modifier = Modifier.fillMaxSize(),
    query: String,
    onClearClick: () -> Unit
) {
    KiraCodeEmptyState(
        modifier = modifier,
        icon = Icons.Filled.SearchOff,
        title = "未找到相关结果",
        message = "尝试调整搜索关键词：\"$query\"",
        actionText = "清空搜索",
        onActionClick = onClearClick
    )
}

/**
 * 预定义空态：网络错误
 */
@Composable
fun NetworkErrorState(
    modifier: Modifier = Modifier.fillMaxSize(),
    message: String = "网络连接失败，请检查网络设置",
    onRetry: () -> Unit
) {
    KiraCodeEmptyState(
        modifier = modifier,
        icon = Icons.Filled.WifiOff,
        title = "连接失败",
        message = message,
        actionText = "重试",
        onActionClick = onRetry
    )
}

// ============ 骨架屏组件 ============

/**
 * 卡片骨架屏
 */
@Composable
fun CardSkeleton(
    modifier: Modifier = Modifier.fillMaxWidth(),
    showAvatar: Boolean = true,
    lines: Int = 3
) {
    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp))
            .padding(16.dp)
            .fillMaxWidth()
    ) {
        Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
            if (showAvatar) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)
                ) {
                    SkeletonShimmer(
                        modifier = Modifier.size(48.dp)
                            .background(MaterialTheme.colorScheme.surface, CircleShape)
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
                    ) {
                        repeat(2) {
                            SkeletonShimmer(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(16.dp)
                                    .width((0.6 + (Math.random() * 0.3)).dp)
                            )
                        }
                    }
                }
            } else {
                repeat(lines) {
                    SkeletonShimmer(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(16.dp)
                            .width((0.5 + (Math.random() * 0.4)).dp)
                    )
                }
            }
        }
    }
}

/**
 * 消息列表骨架屏
 */
@Composable
fun MessageListSkeleton(
    modifier: Modifier = Modifier.fillMaxSize(),
    itemCount: Int = 5
) {
    androidx.compose.foundation.lazy.LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp)
    ) {
        items(itemCount) { index ->
            MessageBubbleSkeleton(isUser = index % 2 == 0)
        }
    }
}

/**
 * 单条消息气泡骨架屏
 */
@Composable
fun MessageBubbleSkeleton(
    isUser: Boolean = false,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = if (isUser) androidx.compose.foundation.layout.Arrangement.End else androidx.compose.foundation.layout.Arrangement.Start
    ) {
        if (!isUser) {
            SkeletonShimmer(
                modifier = Modifier.size(32.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), CircleShape)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }

        Card(
            modifier = Modifier
                .widthIn(min = 60.dp, max = 260.dp)
                .padding(vertical = 4.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
            ),
            shape = RoundedCornerShape(
                topStart = CornerSize(16.dp),
                topEnd = CornerSize(16.dp),
                bottomStart = CornerSize(if (isUser) 16.dp else 4.dp),
                bottomEnd = CornerSize(if (isUser) 4.dp else 16.dp)
            )
        ) {
            SkeletonShimmer(
                modifier = Modifier
                    .padding(12.dp)
                    .fillMaxWidth((0.4f + (Math.random() * 0.5f)).toFloat())
                    .height((30 + (Math.random() * 60)).dp)
            )
        }

        if (isUser) {
            Spacer(modifier = Modifier.width(8.dp))
            SkeletonShimmer(
                modifier = Modifier.size(32.dp)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f), CircleShape)
            )
        }
    }
}

/**
 * 会话列表骨架屏
 */
@Composable
fun SessionListSkeleton(
    modifier: Modifier = Modifier.fillMaxSize(),
    itemCount: Int = 8
) {
    androidx.compose.foundation.lazy.LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)
    ) {
        items(itemCount) {
            SessionItemSkeleton()
        }
    }
}

/**
 * 会话项骨架屏
 */
@Composable
fun SessionItemSkeleton(
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        com.kirakira.kiracode.ui.common.SkeletonShimmer(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .height(60.dp)
        )
    }
}

/**
 * 设置项骨架屏
 */
@Composable
fun SettingsSectionSkeleton(
    modifier: Modifier = Modifier.fillMaxWidth(),
    itemCount: Int = 4
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp)) {
            // 标题骨架
            com.kirakira.kiracode.ui.common.SkeletonShimmer(
                modifier = Modifier
                    .fillMaxWidth(0.3f)
                    .height(20.dp)
            )
            Divider()
            // 设置项骨架
            repeat(itemCount) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp)) {
                        SkeletonShimmer(
                            modifier = Modifier
                                .fillMaxWidth(0.5f)
                                .height(18.dp)
                        )
                        SkeletonShimmer(
                            modifier = Modifier
                                .fillMaxWidth(0.4f)
                                .height(14.dp)
                        )
                    }
                    SkeletonShimmer(
                        modifier = Modifier.size(48.dp)
                    )
                }
                if (it < itemCount - 1) Divider()
            }
        }
    }
}

/**
 * 列表项键生成器 - 用于 LazyColumn 稳定键
 */
object ListKeys {
    fun messageKey(message: com.kirakira.kiracode.domain.model.ChatMessage): String = message.id
    fun sessionKey(session: com.kirakira.kiracode.domain.model.ChatSession): String = session.uuid
    fun referenceCardKey(card: com.kirakira.kiracode.domain.model.CharacterCard): String = card.uuid
    fun draftKey(id: Long): String = "draft_$id"
    fun characterCardKey(card: com.kirakira.kiracode.domain.model.CharacterCard): String = card.uuid
    fun tagKey(tag: String): String = "tag_$tag"
    fun greetingKey(greeting: String): String = "greeting_${greeting.hashCode()}"
}

/**
 * 预加载配置 - 用于 LazyColumn 预加载
 */
object PreloadConfig {
    // 预加载下一页的项数
    const val PREFETCH_DISTANCE = 10
    
    // 可见项之外额外渲染的项数
    const val EXTRA_RENDER_AHEAD = 5
    const val EXTRA_RENDER_BEHIND = 5
}