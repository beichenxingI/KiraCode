package com.kirakira.kiracode.ui.splash

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kirakira.kiracode.ui.theme.AuroraColorTokens
import com.kirakira.kiracode.ui.theme.AuroraTypography
import com.kirakira.kiracode.ui.theme.bouncyClickable

internal data class OnboardingPoint(
    val index: String,
    val title: String,
    val body: String
)

internal val ONBOARDING_POINTS = listOf(
    OnboardingPoint(
        index = "01",
        title = "许愿式写卡",
        body = "说出你想要的角色、世界或气氛，剩下的交给 AI 去规划与落笔。"
    ),
    OnboardingPoint(
        index = "02",
        title = "你主导，它执笔",
        body = "AI 写大纲、填设定、跑自洽审查；每一步都可以随时叫停、改写。没有你参与的角色卡，没有灵魂。"
    ),
    OnboardingPoint(
        index = "03",
        title = "SillyTavern V3 全字段",
        body = "世界书、正则前端、开场白、封面——导出即用，无损往返。"
    ),
    OnboardingPoint(
        index = "04",
        title = "你的专属文风",
        body = "喂几张喜欢的卡，AI 学会你的用词节奏与叙事偏好，越写越像你。"
    )
)

internal const val ONBOARDING_CTA = "同意免责声明并开始创作"
internal const val DISCLAIMER_TOGGLE = "点击查看《开源工具免责声明》"

internal val DISCLAIMER_TEXT = """
    本软件为开源免费工具，按“原样”提供，不提供任何形式的明示或暗示担保。
    使用者应对自己的使用行为负责，包括但不限于：
    · 使用本软件生成的内容；
    · 通过本软件调用的第三方 AI API 所产生的费用；
    · 导入或导出的角色卡数据。

    作者不对因使用或无法使用本软件而导致的任何损失承担责任。
    本软件不鼓励、不支持任何违反法律法规或第三方服务条款的行为。
""".trimIndent()

internal fun requiresFirstRunOnboarding(
    isFirstLaunch: Boolean,
    disclaimerAccepted: Boolean
): Boolean = isFirstLaunch || !disclaimerAccepted

@Composable
fun FirstRunOnboardingScreen(onAccept: () -> Unit) {
    var entered by remember { mutableStateOf(false) }
    var disclaimerExpanded by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        entered = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AuroraColorTokens.Base0)
    ) {
        AuroraOnboardingBackdrop()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .widthIn(max = 560.dp)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 24.dp)
                ) {
                    OnboardingHeader(entered = entered)

                    Spacer(Modifier.height(34.dp))

                    ONBOARDING_POINTS.forEachIndexed { index, point ->
                        OnboardingPointCard(
                            point = point,
                            index = index,
                            entered = entered
                        )
                        if (index != ONBOARDING_POINTS.lastIndex) {
                            Spacer(Modifier.height(10.dp))
                        }
                    }

                    Spacer(Modifier.height(36.dp))

                    OnboardingQuote(entered = entered)

                    Spacer(Modifier.height(30.dp))

                    DisclaimerAccordion(
                        expanded = disclaimerExpanded,
                        onToggle = { disclaimerExpanded = !disclaimerExpanded }
                    )

                    Spacer(Modifier.height(10.dp))
                }
            }

            OnboardingCtaDock(
                onAccept = onAccept,
                modifier = Modifier.graphicsLayer { alpha = if (entered) 1f else 0f }
            )
        }
    }
}

@Composable
private fun AuroraOnboardingBackdrop() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        AuroraColorTokens.Base0,
                        AuroraColorTokens.Base1,
                        AuroraColorTokens.Base0
                    )
                )
            )
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        AuroraColorTokens.AuroraViolet.copy(alpha = 0.16f),
                        Color.Transparent
                    ),
                    center = Offset(size.width * 0.84f, size.height * 0.02f),
                    radius = size.width * 0.82f
                )
            )
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        AuroraColorTokens.AuroraAccent.copy(alpha = 0.08f),
                        Color.Transparent
                    ),
                    center = Offset(size.width * 0.02f, size.height * 0.67f),
                    radius = size.width * 0.72f
                )
            )
            val gridColor = Color.White.copy(alpha = 0.018f)
            for (i in 1..5) {
                val x = size.width * i / 6f
                drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
            }
        }
    }
}

@Composable
private fun OnboardingHeader(entered: Boolean) {
    val alpha by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(620, easing = FastOutSlowInEasing),
        label = "onboardingHeaderAlpha"
    )
    val offset by animateFloatAsState(
        targetValue = if (entered) 0f else 22f,
        animationSpec = tween(620, easing = FastOutSlowInEasing),
        label = "onboardingHeaderOffset"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                this.alpha = alpha
                translationY = offset
            }
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(66.dp)
                .shadow(22.dp, CircleShape)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        listOf(
                            AuroraColorTokens.AuroraViolet.copy(alpha = 0.32f),
                            AuroraColorTokens.AuroraAccent.copy(alpha = 0.12f)
                        )
                    )
                )
                .border(
                    width = 1.dp,
                    color = AuroraColorTokens.AuroraViolet.copy(alpha = 0.48f),
                    shape = CircleShape
                )
        ) {
            Icon(
                imageVector = Icons.Filled.AutoAwesome,
                contentDescription = null,
                tint = AuroraColorTokens.AuroraAccent,
                modifier = Modifier.size(28.dp)
            )
        }

        Spacer(Modifier.height(20.dp))

        Text(
            text = "KiraCard",
            fontSize = 36.sp,
            lineHeight = 40.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.4.sp,
            color = AuroraColorTokens.TextTitle
        )
        Text(
            text = "Chat 式角色卡生成器",
            fontSize = 13.sp,
            lineHeight = 20.sp,
            letterSpacing = 1.4.sp,
            color = AuroraColorTokens.TextCaption,
            modifier = Modifier.padding(top = 7.dp)
        )
        Box(
            modifier = Modifier
                .padding(top = 18.dp)
                .width(42.dp)
                .height(1.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color.Transparent,
                            AuroraColorTokens.AuroraAccent.copy(alpha = 0.75f),
                            Color.Transparent
                        )
                    )
                )
        )
    }
}

@Composable
private fun OnboardingPointCard(
    point: OnboardingPoint,
    index: Int,
    entered: Boolean
) {
    val alpha by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(520, delayMillis = 80 + index * 70, easing = FastOutSlowInEasing),
        label = "onboardingPointAlpha$index"
    )
    val offset by animateFloatAsState(
        targetValue = if (entered) 0f else 20f,
        animationSpec = tween(520, delayMillis = 80 + index * 70, easing = FastOutSlowInEasing),
        label = "onboardingPointOffset$index"
    )

    Surface(
        color = AuroraColorTokens.Base1.copy(alpha = 0.72f),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, AuroraColorTokens.LineSecondary),
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                this.alpha = alpha
                translationY = offset
            }
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 15.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                AuroraColorTokens.AuroraViolet.copy(alpha = 0.16f),
                                AuroraColorTokens.AuroraAccent.copy(alpha = 0.06f)
                            )
                        )
                    )
                    .border(
                        1.dp,
                        AuroraColorTokens.LinePrimary.copy(alpha = 0.9f),
                        RoundedCornerShape(13.dp)
                    )
            ) {
                Text(
                    text = point.index,
                    color = AuroraColorTokens.AuroraAccent,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = point.title,
                    color = AuroraColorTokens.TextTitle,
                    fontSize = 16.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = point.body,
                    color = AuroraColorTokens.TextCaption,
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun OnboardingQuote(entered: Boolean) {
    val alpha by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(680, delayMillis = 360, easing = FastOutSlowInEasing),
        label = "onboardingQuoteAlpha"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { this.alpha = alpha }
    ) {
        Text(
            text = "降低创作的门槛，",
            color = AuroraColorTokens.TextTitle,
            fontSize = 19.sp,
            lineHeight = 28.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
        Text(
            text = "让每个想法都变成可玩的世界。",
            color = AuroraColorTokens.AuroraAccent,
            fontSize = 19.sp,
            lineHeight = 28.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun DisclaimerAccordion(
    expanded: Boolean,
    onToggle: () -> Unit
) {
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(220, easing = FastOutSlowInEasing),
        label = "disclaimerArrow"
    )

    Surface(
        color = AuroraColorTokens.Base1.copy(alpha = 0.58f),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, AuroraColorTokens.LineSecondary),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .bouncyClickable { onToggle() }
                    .padding(horizontal = 15.dp, vertical = 14.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Security,
                    contentDescription = null,
                    tint = AuroraColorTokens.TextTech,
                    modifier = Modifier.size(17.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = DISCLAIMER_TOGGLE,
                    color = AuroraColorTokens.TextCaption,
                    style = AuroraTypography.Caption,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = Icons.Outlined.KeyboardArrowDown,
                    contentDescription = if (expanded) "收起免责声明" else "展开免责声明",
                    tint = AuroraColorTokens.TextTech,
                    modifier = Modifier
                        .size(19.dp)
                        .rotate(arrowRotation)
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn(tween(180)) + expandVertically(tween(220)),
                exit = fadeOut(tween(140)) + shrinkVertically(tween(180))
            ) {
                Column(modifier = Modifier.padding(start = 15.dp, end = 15.dp, bottom = 15.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(AuroraColorTokens.LineSecondary)
                    )
                    Text(
                        text = DISCLAIMER_TEXT,
                        color = AuroraColorTokens.TextBody.copy(alpha = 0.78f),
                        fontSize = 12.sp,
                        lineHeight = 19.sp,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun OnboardingCtaDock(
    onAccept: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = AuroraColorTokens.Base0.copy(alpha = 0.97f),
        modifier = modifier.fillMaxWidth()
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color.Transparent,
                                AuroraColorTokens.LinePrimary,
                                Color.Transparent
                            )
                        )
                    )
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .widthIn(max = 512.dp)
                        .fillMaxWidth()
                        .shadow(
                            elevation = 18.dp,
                            shape = RoundedCornerShape(18.dp)
                        )
                        .clip(RoundedCornerShape(18.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    AuroraColorTokens.AuroraAccentStrong,
                                    AuroraColorTokens.AuroraAccent
                                )
                            )
                        )
                        .bouncyClickable { onAccept() }
                        .padding(horizontal = 18.dp, vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AutoAwesome,
                            contentDescription = null,
                            tint = AuroraColorTokens.Base0.copy(alpha = 0.82f),
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(Modifier.width(9.dp))
                        Text(
                            text = ONBOARDING_CTA,
                            color = AuroraColorTokens.Base0,
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.2.sp
                        )
                    }
                }
            }
        }
    }
}
