package com.kirakira.kiracode.ui.workspace

import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.kirakira.kiracode.domain.model.ChatMessage
import com.kirakira.kiracode.ui.theme.AuroraColorTokens
import com.kirakira.kiracode.ui.theme.AuroraShapes
import com.kirakira.kiracode.ui.theme.IndustrialMotion
import com.kirakira.kiracode.ui.theme.IndustrialSpacing
import com.kirakira.kiracode.ui.theme.IndustrialTypography
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ============ 事件流配色（AuroraColorTokens） ============
private val StreamText = AuroraColorTokens.TextTitle
private val StreamMuted = AuroraColorTokens.TextCaption
private val StreamDim = AuroraColorTokens.TextTech
private val StreamGreen = AuroraColorTokens.SignalSuccess
private val StreamPurple = AuroraColorTokens.SignalThinking

/**
 * 时间轴条目：消息与事件卡按时间戳排序后的统一时间轴。
 */
private sealed class TimelineItem {
    abstract val timestamp: Long
    abstract val key: String

    data class Msg(val message: ChatMessage) : TimelineItem() {
        override val timestamp: Long get() = message.timestamp
        override val key: String get() = "msg_${message.id}"
    }

    data class Card(val card: AgentCardUi) : TimelineItem() {
        override val timestamp: Long get() = card.timestamp
        override val key: String get() = "card_${card.id}"
    }
}

/**
 * 中央事件时间轴（Agent 工作台范式）：
 *
 * - 垂直滚动时间轴，按时间顺序交错渲染（不是聊天记录、无左右气泡）；
 * - 用户输入 → 全宽轻量行（青色侧线 + 角色标签）；
 * - AI 正文 → 正文区域（Mono 标签 + 无卡；流式纯文本，定稿 Markdown 渲染）；
 * - 思维链 → 紫色细线块（可折叠、流式指示）；
 * - 工具调用 / 结果 / 追问 / 计划 / 进度 / Diff / 失败 / 降级节点交错；
 * - 流式光标；自动跟随（用户上滑回看时不打扰，回到底部恢复）。
 */
@Composable
fun EventStream(
    messages: List<ChatMessage>,
    agentCards: List<AgentCardUi>,
    isSending: Boolean,
    liveReasoning: String?,
    cardCallbacks: AgentCardCallbacks,
    listState: LazyListState = rememberLazyListState(),
    modifier: Modifier = Modifier
) {
    if (messages.isEmpty() && !isSending && agentCards.isEmpty() && liveReasoning == null) return

    val timeline = remember(messages, agentCards) {
        (messages.map { TimelineItem.Msg(it) } + agentCards.map { TimelineItem.Card(it) })
            .sortedBy { it.timestamp }
    }
    val isCompact = LocalConfiguration.current.screenWidthDp < 600
    val showLiveReasoning = isSending && liveReasoning != null
    val showCaret = isSending && liveReasoning == null && timeline.lastOrNull()?.let {
        it is TimelineItem.Msg && it.message.role != "assistant"
    } != false
    val tailCount = (if (showLiveReasoning) 1 else 0) + (if (showCaret) 1 else 0)

    // 自动跟随：仅当用户停留在底部附近时滚动；上滑回看时不打扰
    val isUserNearBottom by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            if (info.totalItemsCount == 0) true
            else listState.firstVisibleItemIndex >= info.totalItemsCount - 2
        }
    }
    // 新条目出现：动画滚动到底（单一 effect，随条目数/尾部指示变化触发）
    LaunchedEffect(timeline.size, tailCount) {
        val lastIndex = timeline.size + tailCount - 1
        if (lastIndex >= 0 && isUserNearBottom) {
            runCatching { listState.animateScrollToItem(lastIndex) }
        }
    }
    // 流式内容增长：瞬时跟随（单协程 snapshotFlow，不逐字符重启 effect）
    val currentMessages by rememberUpdatedState(messages)
    LaunchedEffect(listState) {
        snapshotFlow { currentMessages.lastOrNull()?.content?.length ?: 0 }
            .collect {
                if (isUserNearBottom) {
                    val info = listState.layoutInfo
                    val lastIndex = info.totalItemsCount - 1
                    if (lastIndex >= 0) runCatching { listState.scrollToItem(lastIndex) }
                }
            }
    }
    // 诊断：UI 内容长度采样（仅 DEBUG，~100ms 一次，记录流式增长轨迹）
    if (true) {
        val uiSample = remember { mutableListOf<Int>() }
        var lastSampleNanos = 0L
        LaunchedEffect(listState, messages, isSending, liveReasoning) {
            if (isSending) {
                snapshotFlow {
                    val len = currentMessages.lastOrNull()?.content?.length ?: 0
                    val reasoningLen = liveReasoning?.length ?: 0
                    val now = SystemClock.elapsedRealtimeNanos()
                    if (now - lastSampleNanos >= 100_000_000) { // ~100ms
                        lastSampleNanos = now
                    }
                    listOf(len, reasoningLen)
                }
                    .collect { (len, reasoningLen) ->
                        uiSample.add(len)
                        if (uiSample.size % 5 == 0) {
                            com.kirakira.kiracode.util.AppLogger.d(
                                "UI_SAMPLE",
                                "len_seq=${uiSample.joinToString(",")} liveR_len=$reasoningLen isSending=$isSending"
                )
            }
        }
    }
    }
    }

    // 进场动画：只对新事件触发一次（初始时间轴与重组不重复播放）
    val seenKeys = remember { mutableSetOf<String>() }
    var timelineInitialized by remember { mutableStateOf(false) }
    if (!timelineInitialized) {
        seenKeys.addAll(timeline.map { it.key })
        timelineInitialized = true
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            horizontal = 8.dp,
            vertical = IndustrialSpacing.Panel
        ),
        verticalArrangement = Arrangement.spacedBy(IndustrialSpacing.Item)
    ) {
        items(timeline, key = { it.key }) { item ->
            val isNew = item.key !in seenKeys
            var reveal by remember(item.key) { mutableStateOf(!isNew) }
            LaunchedEffect(item.key) {
                if (!reveal) {
                    reveal = true
                    seenKeys.add(item.key)
                }
            }
            AnimatedVisibility(
                visible = reveal,
                enter = slideInVertically(
                    animationSpec = spring(dampingRatio = 0.62f, stiffness = 520f)
                ) { it / 2 } + fadeIn(tween(150))
            ) {
                Box {
                    when (item) {
                        is TimelineItem.Msg ->
                            if (item.message.role == "user") UserEventRow(item.message)
                            else AssistantBody(item.message)
                        is TimelineItem.Card -> AgentCardItem(item.card, cardCallbacks, isCompact)
                    }
                }
            }
        }
        if (showLiveReasoning) {
            item(key = "live_reasoning") {
                ReasoningBlock(content = liveReasoning.orEmpty(), streaming = true, forceExpanded = true)
            }
        }
        if (showCaret) {
            item(key = "stream_caret") { StreamingCaret() }
        }
    }
}

/** shimmer：流式占位的脉动透明度（唯一循环动画） */
@Composable
private fun Modifier.shimmer(): Modifier {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(IndustrialMotion.LOOP_DURATION, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            RepeatMode.Reverse
        ),
        label = "shimmerAlpha"
    )
    return this.alpha(alpha)
}

/** 思维链块：紫色细侧线 + 标题 + 可折叠 + 流式指示（无卡片） */
@Composable
fun ReasoningBlock(
    content: String,
    streaming: Boolean,
    forceExpanded: Boolean = false
) {
    if (content.isBlank() && !streaming) return
    var expanded by rememberSaveable { mutableStateOf(false) }
    val showExpanded = expanded || forceExpanded

    Surface(
        color = AuroraColorTokens.Base1,
        shape = AuroraShapes.Small,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp)
    ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !forceExpanded) { expanded = !expanded }
                    .padding(horizontal = IndustrialSpacing.Compact, vertical = IndustrialSpacing.Tight)
            ) {
            Box(
                Modifier
                    .width(IndustrialSpacing.ThinkingLineWidth)
                    .background(StreamPurple.copy(alpha = 0.55f))
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = IndustrialSpacing.Item, top = IndustrialSpacing.Tight, bottom = IndustrialSpacing.Tight)
            ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Psychology,
                    contentDescription = "思维链",
                    tint = StreamPurple,
                    modifier = Modifier
                        .size(IndustrialSpacing.IconSmall)
                        .let { if (streaming) it.shimmer() else it }
                )
                Spacer(Modifier.width(IndustrialSpacing.Tight))
                Text(
                    text = if (streaming) "正在思考…" else "思维链",
                    style = IndustrialTypography.Caption,
                    color = StreamMuted,
                    modifier = Modifier.let { if (streaming) it.shimmer() else it }
                )
                if (content.isNotBlank()) {
                    Spacer(Modifier.width(IndustrialSpacing.Tight))
                    Text(
                        text = "${content.length} 字",
                        style = IndustrialTypography.Marker,
                        color = StreamDim
                    )
                }
                Spacer(Modifier.weight(1f))
                if (!forceExpanded) {
                    Icon(
                        imageVector = if (showExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = if (showExpanded) "收起" else "展开",
                        tint = StreamDim,
                        modifier = Modifier.size(IndustrialSpacing.IconSmall)
                    )
                }
            }
            AnimatedVisibility(
                visible = showExpanded,
                enter = expandVertically(IndustrialMotion.expand()) + fadeIn(IndustrialMotion.feedback()),
                exit = shrinkVertically(IndustrialMotion.feedback()) + fadeOut(IndustrialMotion.feedback())
            ) {
                Text(
                    text = content.ifBlank { "正在思考…" },
                    style = IndustrialTypography.Caption.copy(fontStyle = FontStyle.Italic),
                    color = StreamMuted,
                    modifier = Modifier
                        .padding(top = IndustrialSpacing.Tight)
                        .animateContentSize(
                            animationSpec = tween(80, easing = LinearOutSlowInEasing)
                        )
                        .then(if (streaming && content.isBlank()) Modifier.shimmer() else Modifier)
                )
            }
        }
    }
    }
}

/** 用户输入：右侧气泡（右下小角），与 AI 正文区隔开 */
@Composable
private fun UserEventRow(message: ChatMessage) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 48.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
        contentAlignment = Alignment.CenterEnd
    ) {
        Surface(
            color = AuroraColorTokens.AuroraAccent.copy(alpha = 0.10f),
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = 18.dp,
                bottomEnd = 4.dp
            )
        ) {
            Column(
                modifier = Modifier.padding(
                    horizontal = IndustrialSpacing.Panel,
                    vertical = IndustrialSpacing.Compact
                )
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "你",
                        style = IndustrialTypography.Marker,
                        color = AuroraColorTokens.AuroraAccent
                    )
                    Spacer(Modifier.width(IndustrialSpacing.Tight))
                    Text(
                        text = formatTime(message.timestamp),
                        style = IndustrialTypography.Marker,
                        color = StreamDim
                    )
                }
                Text(
                    text = message.content,
                    style = IndustrialTypography.Body,
                    color = StreamText,
                    modifier = Modifier.padding(top = IndustrialSpacing.Tight)
                )
            }
        }
    }
}

/** AI 正文：无厚重卡片，Mono 标签 + 正文区域；流式纯文本，定稿 Markdown 渲染 */
@Composable
private fun AssistantBody(message: ChatMessage) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        if (message.thinking.isNotBlank()) {
            ReasoningBlock(content = message.thinking, streaming = false)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Kira",
                style = IndustrialTypography.Marker,
                color = StreamPurple
            )
            Spacer(Modifier.width(IndustrialSpacing.Tight))
            Text(
                text = formatTime(message.timestamp),
                style = IndustrialTypography.Marker,
                color = StreamDim
            )
            if (message.isStreaming) {
                Spacer(Modifier.width(IndustrialSpacing.Tight))
                Text(
                    text = "生成中…",
                    style = IndustrialTypography.Marker,
                    color = StreamMuted
                )
            }
        }
        when {
            message.content.isNotBlank() && !message.isStreaming -> Text(
                text = MarkdownText.render(message.content, StreamText, IndustrialTypography.Body.fontSize),
                style = IndustrialTypography.Body,
                color = StreamText,
                modifier = Modifier.padding(top = IndustrialSpacing.Tight)
            )
            message.content.isNotBlank() -> Text(
                text = message.content,
                style = IndustrialTypography.Body,
                color = StreamText,
                modifier = Modifier
                    .padding(top = IndustrialSpacing.Tight)
                    .animateContentSize(
                        animationSpec = tween(80, easing = LinearOutSlowInEasing)
                    )
            )
            message.isStreaming -> Text(
                text = "…",
                style = IndustrialTypography.Body,
                color = StreamMuted,
                modifier = Modifier
                    .padding(top = IndustrialSpacing.Tight)
                    .shimmer()
            )
        }
    }
}

/** 流式光标（无思维链时的等待指示） */
@Composable
private fun StreamingCaret() {
    Text(
        text = "▍",
        style = IndustrialTypography.Body,
        color = StreamMuted,
        modifier = Modifier
            .padding(start = IndustrialSpacing.Page)
            .shimmer()
    )
}

private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

private fun formatTime(timestamp: Long): String =
    runCatching { timeFormat.format(Date(timestamp)) }.getOrDefault("")
