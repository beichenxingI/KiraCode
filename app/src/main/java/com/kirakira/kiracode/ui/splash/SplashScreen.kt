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
        is SplashUiState.Onboarding -> {
            FirstRunOnboardingScreen(onAccept = { viewModel.acceptFirstRunTerms() })
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

    private val _uiState = kotlinx.coroutines.flow.MutableStateFlow<SplashUiState>(
        SplashUiState.Loading(message = "正在准备...")
    )
    val uiState: kotlinx.coroutines.flow.StateFlow<SplashUiState> = _uiState

    init {
        checkFirstLaunch()
    }

    private fun checkFirstLaunch() {
        viewModelScope.launch {
            try {
                val needsOnboarding = requiresFirstRunOnboarding(
                    isFirstLaunch = preferencesRepository.isFirstLaunch.first(),
                    disclaimerAccepted = preferencesRepository.disclaimerAccepted.first()
                )
                _uiState.value = if (needsOnboarding) {
                    SplashUiState.Onboarding
                } else {
                    SplashUiState.Branding
                }
            } catch (e: Exception) {
                _uiState.value = SplashUiState.Error("无法读取首次启动配置")
            }
        }
    }

    fun onBrandingComplete() {
        viewModelScope.launch {
            try {
                val needsOnboarding = requiresFirstRunOnboarding(
                    isFirstLaunch = preferencesRepository.isFirstLaunch.first(),
                    disclaimerAccepted = preferencesRepository.disclaimerAccepted.first()
                )
                if (needsOnboarding) {
                    _uiState.value = SplashUiState.Onboarding
                } else {
                    proceed()
                }
            } catch (e: Exception) {
                _uiState.value = SplashUiState.Ready
            }
        }
    }

    private suspend fun proceed() {
        _uiState.value = SplashUiState.Loading(message = "正在初始化...")
        delay(700)
        _uiState.value = SplashUiState.Ready
    }

    fun acceptFirstRunTerms() {
        viewModelScope.launch {
            try {
                preferencesRepository.acceptFirstRunTerms()
                _uiState.value = SplashUiState.Ready
            } catch (e: Exception) {
                _uiState.value = SplashUiState.Error("无法保存首次引导确认，请重试")
            }
        }
    }

    fun retry() {
        checkFirstLaunch()
    }
}

sealed interface SplashUiState {
    data object Branding : SplashUiState
    data object Onboarding : SplashUiState
    data class Loading(val message: String) : SplashUiState
    data object Ready : SplashUiState
    data class Error(val message: String) : SplashUiState
}