package com.kirakira.kiracode.ui.splash

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInOutCubic
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.InfiniteTransition
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.graphics.Brush
import com.kirakira.kiracode.ui.theme.IndustrialColorTokens
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.SyncAlt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.kirakira.kiracode.R
import com.kirakira.kiracode.domain.repository.PreferencesRepository
import com.kirakira.kiracode.ui.navigation.MainActivity
import com.kirakira.kiracode.ui.theme.AuroraColorTokens
import com.kirakira.kiracode.ui.theme.AuroraTypography
import kotlinx.coroutines.flow.first
import com.kirakira.kiracode.ui.navigation.AppNavHost
import com.kirakira.kiracode.ui.theme.KiraCodeThemeWithPreferences
import com.kirakira.kiracode.ui.theme.SystemBarsColorSync
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 启动/引导页面
 * 首次启动显示引导流程，后续启动显示品牌页快速进入主界面
 */
@AndroidEntryPoint
class SplashActivity : ComponentActivity() {

    @Inject
    lateinit var preferencesRepository: PreferencesRepository

    private val splashViewModel: SplashViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            KiraCodeThemeWithPreferences(preferencesRepository) {
                SplashScreen(
                    viewModel = splashViewModel,
                    onComplete = {
                        android.util.Log.d("SplashActivity", "=== Splash 完成 → 进入 MainActivity ===")
                        val intent = Intent(this@SplashActivity, MainActivity::class.java)
                        startActivity(intent)
                        finish()
                    }
                )
            }
        }
    }
}

@Composable
fun SplashScreen(
    viewModel: SplashViewModel,
    onComplete: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val state = uiState
    when (state) {
        is SplashUiState.Branding -> {
            BrandingScreen(onAnimationEnd = { viewModel.onBrandingComplete() })
        }
        is SplashUiState.Disclaimer -> {
            DisclaimerScreen(onAccept = { viewModel.acceptDisclaimer() })
        }
        is SplashUiState.Onboarding -> {
            OnboardingScreen(
                currentStep = state.currentStep,
                totalSteps = state.totalSteps,
                onNext = { viewModel.nextStep() },
                onPrev = { viewModel.prevStep() },
                onSkip = { viewModel.skipOnboarding() },
                onComplete = { viewModel.completeOnboarding() }
            )
        }
        is SplashUiState.Loading -> {
            LoadingScreen(message = state.message)
        }
        is SplashUiState.Ready -> {
            LaunchedEffect(Unit) {
                delay(500)
                onComplete()
            }
            LoadingScreen(message = "准备就绪...")
        }
        is SplashUiState.Error -> {
            ErrorScreen(
                message = state.message,
                onRetry = { viewModel.retry() }
            )
        }
    }
}

/**
 * 品牌展示页（V2 Beta 简约版）：KiraCode 标识淡入 + 一句初心标语，
 * 无任何按钮，约 1.6s 后自动进入主界面。
 */
@Composable
fun BrandingScreen(
    onAnimationEnd: () -> Unit
) {
    var showTitle by remember { mutableStateOf(false) }
    var showTagline by remember { mutableStateOf(false) }
    val animationFinished = remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        showTitle = true
        delay(320)
        showTagline = true
        // 自动进入：总时长约 1.8s，给扫光留出时间；无需任何点击
        delay(1500)
        if (!animationFinished.value) {
            animationFinished.value = true
            onAnimationEnd()
        }
    }

    val titleAlpha by animateFloatAsState(
        targetValue = if (showTitle) 1f else 0f,
        animationSpec = tween(700, easing = FastOutSlowInEasing)
    )
    val titleScale by animateFloatAsState(
        targetValue = if (showTitle) 1f else 0.94f,
        animationSpec = tween(700, easing = FastOutSlowInEasing)
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        IndustrialColorTokens.Base0,
                        MaterialTheme.colorScheme.surface,
                        IndustrialColorTokens.Base0
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize().padding(horizontal = 40.dp)
        ) {
            // 应用名：淡入 + 轻微缩放
            Text(
                text = "KiraCard",
                fontSize = 42.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = Color.White,
                modifier = Modifier.graphicsLayer {
                    alpha = titleAlpha
                    scaleX = titleScale
                    scaleY = titleScale
                }
            )

            Spacer(Modifier.height(28.dp))

            // 初心标语
            AnimatedVisibility(visible = showTagline) {
                SweepTextEffect(
                    text = "描一人  生万象",
                    style = AuroraTypography.Body.copy(
                        fontSize = 16.sp,
                        letterSpacing = 3.sp
                    ),
                    modifier = Modifier.padding(top = 16.dp)
                )
            }

            Spacer(Modifier.height(48.dp))

            Text(
                text = "V2 Beta · AGPLv3",
                fontSize = 11.sp,
                color = IndustrialColorTokens.TextTitle.copy(alpha = 0.25f)
            )
        }
    }
}

/**
 * 扫光文字：暗色底字 + 一道亮光从左到右扫过一次（1200ms）。
 */
@Composable
fun SweepTextEffect(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier
) {
    val shimmerWidth = 200.dp
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val density = LocalDensity.current
    val screenWidthPx = with(density) { screenWidth.toPx() }
    val shimmerWidthPx = with(density) { shimmerWidth.toPx() }
    val sweep = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        delay(200)
        sweep.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 1200, easing = LinearEasing)
        )
    }

    val startX = sweep.value * (screenWidthPx + shimmerWidthPx) - shimmerWidthPx
    val brush = Brush.horizontalGradient(
        colors = listOf(
            AuroraColorTokens.TextCaption,
            AuroraColorTokens.TextCaption,
            Color.White,
            AuroraColorTokens.TextTitle,
            Color.White,
            AuroraColorTokens.TextCaption,
            AuroraColorTokens.TextCaption
        ),
        startX = startX,
        endX = startX + shimmerWidthPx
    )

    Text(
        text = text,
        style = style.copy(brush = brush),
        modifier = modifier
    )
}

/**
 * 免责声明页：首次进入需用户明确同意后才能继续。
 */
@Composable
fun DisclaimerScreen(
    onAccept: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(IndustrialColorTokens.Base0, MaterialTheme.colorScheme.surface, IndustrialColorTokens.Base0)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp)
        ) {
            Text(
                text = "免责声明",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(Modifier.height(24.dp))
            Text(
                text = DISCLAIMER_TEXT,
                fontSize = 14.sp,
                lineHeight = 22.sp,
                color = IndustrialColorTokens.TextBody.copy(alpha = 0.8f)
            )
            Spacer(Modifier.height(32.dp))
            com.kirakira.kiracode.ui.common.KiraCodePrimaryButton(
                onClick = onAccept,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("同意并继续", fontSize = 16.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

private val DISCLAIMER_TEXT = """
本软件为开源免费工具，按“原样”提供，不提供任何形式的明示或暗示担保。
使用者应对自己的使用行为负责，包括但不限于：

· 使用本软件生成的内容；
· 通过本软件调用的第三方 AI API 所产生的费用；
· 导入或导出的角色卡数据。

作者不对因使用或无法使用本软件而导致的任何损失承担责任。
本软件不鼓励、不支持任何违反法律法规或第三方服务条款的行为。
""".trimIndent()

/**
 * 引导页（V2 Beta 重构：Apple 美区官网风格——纵向滚动揭示，逐条淡入上移，
 * 大量留白、精致大标题，最后以目标宣言收束）。
 */
@Composable
fun OnboardingScreen(
    currentStep: Int,
    totalSteps: Int,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onSkip: () -> Unit,
    onComplete: () -> Unit
) {
    // 逐条揭示：进入后依次淡入上移（向下滑动揭示感由滚动+错峰动画共同构成）
    var revealed by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (revealed < 6) {
            delay(260)
            revealed++
        }
    }

    data class IntroItem(val title: String, val body: String)

    val items = listOf(
        IntroItem("对话式创作", "用一句话下指令，AI 作为 Agent 替你完成角色卡的创建与修改。"),
        IntroItem("全自动模式", "AI 先写计划书，再逐阶段自动执行，直到一张完整的角色卡落地。"),
        IntroItem("世界书与正则", "SillyTavern V3 全字段支持：世界书条目、正则脚本，导入导出无损往返。"),
        IntroItem("灵魂", "选几张喜欢的卡让 AI 分析，训练出属于你的创作风格灵魂。")
    )

    val titleAlpha by animateFloatAsState(
        targetValue = if (revealed >= 1) 1f else 0f,
        animationSpec = tween(700, easing = FastOutSlowInEasing)
    )
    val titleOffset by animateFloatAsState(
        targetValue = if (revealed >= 1) 0f else 40f,
        animationSpec = tween(700, easing = FastOutSlowInEasing)
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(IndustrialColorTokens.Base0, MaterialTheme.colorScheme.surface, IndustrialColorTokens.Base0)
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(84.dp))

            // 标识与名称
            Icon(
                imageVector = Icons.Filled.AutoAwesome,
                contentDescription = null,
                tint = IndustrialColorTokens.SignalThinking,
                modifier = Modifier.size(44.dp).graphicsLayer {
                    alpha = titleAlpha
                    translationY = titleOffset
                }
            )
            Spacer(Modifier.height(18.dp))
            Text(
                text = "KiraCard",
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = Color.White,
                modifier = Modifier.graphicsLayer {
                    alpha = titleAlpha
                    translationY = titleOffset
                }
            )
            Text(
                text = "对话式 AI 角色卡创作 Agent",
                fontSize = 14.sp,
                color = IndustrialColorTokens.TextCaption.copy(alpha = 0.6f),
                modifier = Modifier.padding(top = 8.dp).graphicsLayer { alpha = titleAlpha }
            )

            Spacer(Modifier.height(56.dp))

            // 核心功能逐条揭示（向下滑动的揭示节奏）
            items.forEachIndexed { i, item ->
                val a by animateFloatAsState(
                    targetValue = if (revealed >= i + 2) 1f else 0f,
                    animationSpec = tween(650, easing = FastOutSlowInEasing)
                )
                val oy by animateFloatAsState(
                    targetValue = if (revealed >= i + 2) 0f else 48f,
                    animationSpec = tween(650, easing = FastOutSlowInEasing)
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 14.dp)
                        .graphicsLayer {
                            alpha = a
                            translationY = oy
                        }
                ) {
                    Text(
                        item.title,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = IndustrialColorTokens.TextTitle
                    )
                    Text(
                        item.body,
                        fontSize = 14.sp,
                        lineHeight = 21.sp,
                        color = IndustrialColorTokens.TextCaption.copy(alpha = 0.6f),
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }

            Spacer(Modifier.height(48.dp))

            // 目标宣言（最后揭示）
            val ga by animateFloatAsState(
                targetValue = if (revealed >= 6) 1f else 0f,
                animationSpec = tween(750, easing = FastOutSlowInEasing)
            )
            val gOffset by animateFloatAsState(
                targetValue = if (revealed >= 6) 0f else 56f,
                animationSpec = tween(750, easing = FastOutSlowInEasing)
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        alpha = ga
                        translationY = gOffset
                    }
            ) {
                Text(
                    text = "降低个人创作的门槛，",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Text(
                    text = "人人皆是制卡师。",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = IndustrialColorTokens.SignalThinking,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            Spacer(Modifier.height(64.dp))

            com.kirakira.kiracode.ui.common.KiraCodePrimaryButton(
                onClick = onComplete,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("开始创作", fontSize = 16.sp, fontWeight = FontWeight.Medium)
            }
            TextButton(
                onClick = onSkip,
                modifier = Modifier.padding(top = 8.dp, bottom = 32.dp)
            ) {
                Text("跳过", fontSize = 14.sp, color = IndustrialColorTokens.TextCaption.copy(alpha = 0.5f))
            }
        }
    }
}

data class OnboardingStep(
    val id: Int,
    val title: String,
    val description: String,
    val icon: ImageVector
)

/**
 * 加载页
 */
@Composable
fun LoadingScreen(message: String) {
    Box(
        modifier = androidx.compose.ui.Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            androidx.compose.material3.CircularProgressIndicator(
                modifier = androidx.compose.ui.Modifier.size(48.dp),
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = message,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 错误页
 */
@Composable
fun ErrorScreen(
    message: String,
    onRetry: () -> Unit
) {
    Box(
        modifier = androidx.compose.ui.Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = androidx.compose.ui.Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            androidx.compose.material3.Icon(
                imageVector = Icons.Filled.AutoAwesome,
                contentDescription = null,
                modifier = androidx.compose.ui.Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.error
            )
            Text(
                text = "启动失败",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error
            )
            Text(
                text = message,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            com.kirakira.kiracode.ui.common.KiraCodePrimaryButton(
                onClick = onRetry,
                modifier = androidx.compose.ui.Modifier.padding(top = 16.dp).width(200.dp)
            ) {
                Text("重试", fontSize = 16.sp)
            }
        }
    }
}

/**
 * Splash ViewModel
 */
@dagger.hilt.android.lifecycle.HiltViewModel
class SplashViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository
) : androidx.lifecycle.ViewModel() {

    private val _uiState = kotlinx.coroutines.flow.MutableStateFlow<SplashUiState>(SplashUiState.Branding)
    val uiState: kotlinx.coroutines.flow.StateFlow<SplashUiState> = _uiState

    init {
        checkFirstLaunch()
    }

    private fun checkFirstLaunch() {
        viewModelScope.launch {
            try {
                val isFirstLaunch = preferencesRepository.isFirstLaunch.first()
                if (isFirstLaunch) {
                    _uiState.value = SplashUiState.Onboarding(currentStep = 0, totalSteps = 4)
                } else {
                    // 非首次启动，直接品牌页
                    _uiState.value = SplashUiState.Branding
                }
            } catch (e: Exception) {
                // 读取偏好失败时默认进入品牌页，避免崩溃
                _uiState.value = SplashUiState.Branding
            }
        }
    }

    fun onBrandingComplete() {
        viewModelScope.launch {
            try {
                val accepted = preferencesRepository.disclaimerAccepted.first()
                if (!accepted) {
                    // 未同意免责声明 → 必须先同意
                    _uiState.value = SplashUiState.Disclaimer
                    return@launch
                }
                proceed()
            } catch (e: Exception) {
                _uiState.value = SplashUiState.Ready
            }
        }
    }

    private suspend fun proceed() {
        val isFirstLaunch = preferencesRepository.isFirstLaunch.first()
        if (isFirstLaunch) {
            _uiState.value = SplashUiState.Onboarding(currentStep = 0, totalSteps = 4)
        } else {
            _uiState.value = SplashUiState.Loading(message = "正在初始化...")
            kotlinx.coroutines.delay(1000)
            _uiState.value = SplashUiState.Ready
        }
    }

    fun acceptDisclaimer() {
        viewModelScope.launch {
            try {
                preferencesRepository.setDisclaimerAccepted(true)
                proceed()
            } catch (e: Exception) {
                _uiState.value = SplashUiState.Ready
            }
        }
    }

    fun nextStep() {
        val current = _uiState.value
        if (current is SplashUiState.Onboarding) {
            val nextStep = current.currentStep + 1
            if (nextStep >= current.totalSteps) {
                completeOnboarding()
            } else {
                _uiState.value = current.copy(currentStep = nextStep)
            }
        }
    }

    fun prevStep() {
        val current = _uiState.value
        if (current is SplashUiState.Onboarding) {
            val prevStep = current.currentStep - 1
            if (prevStep >= 0) {
                _uiState.value = current.copy(currentStep = prevStep)
            }
        }
    }

    fun skipOnboarding() {
        completeOnboarding()
    }

    fun completeOnboarding() {
        viewModelScope.launch {
            try {
                preferencesRepository.setFirstLaunchCompleted()
                _uiState.value = SplashUiState.Loading(message = "正在保存配置...")
                delay(500)
                _uiState.value = SplashUiState.Ready
            } catch (e: Exception) {
                _uiState.value = SplashUiState.Ready
            }
        }
    }

    fun retry() {
        checkFirstLaunch()
    }
}

sealed interface SplashUiState {
    data object Branding : SplashUiState
    data object Disclaimer : SplashUiState
    data class Onboarding(
        val currentStep: Int,
        val totalSteps: Int
    ) : SplashUiState
    data class Loading(val message: String) : SplashUiState
    data object Ready : SplashUiState
    data class Error(val message: String) : SplashUiState
}