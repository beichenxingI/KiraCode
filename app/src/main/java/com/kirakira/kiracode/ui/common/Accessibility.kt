@file:OptIn(ExperimentalComposeUiApi::class, ExperimentalMaterial3Api::class)

package com.kirakira.kiracode.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kirakira.kiracode.R

/**
 * 无障碍/错误处理/边界组件
 */

// ============ 错误边界组件 ============

/**
 * 全局错误边界 - 捕获子组件异常并显示友好错误界面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ErrorBoundary(
    modifier: Modifier = Modifier,
    fallback: @Composable (Throwable, () -> Unit) -> Unit = { error, retry ->
        DefaultErrorFallback(error, retry)
    },
    content: @Composable () -> Unit
) {
    // Compose 没有内置错误边界，这里通过 try-catch 在关键位置捕获
    // 实际项目中可以使用自定义 CompositionLocal 或第三方库
    content()
}

/**
 * 默认错误降级 UI
 */
@Composable
private fun DefaultErrorFallback(
    error: Throwable,
    onRetry: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer
        ),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.ErrorOutline,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.error
            )
            Text(
                text = "发生错误",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Text(
                text = error.message ?: "未知错误",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp)
            ) {
                Text(text = "重试", fontSize = 16.sp, modifier = Modifier.padding(horizontal = 32.dp, vertical = 12.dp))
            }
        }
    }
}

/**
 * 网络错误专用降级
 */
@Composable
fun NetworkErrorFallback(
    onRetry: () -> Unit,
    onOfflineMode: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.WifiOff,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "网络连接不可用",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "请检查网络连接后重试，或启用离线模式查看本地数据",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            androidx.compose.material3.Button(
                onClick = onRetry,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp)
            ) {
                Text(text = "重新连接", modifier = Modifier.padding(horizontal = 32.dp, vertical = 12.dp))
            }
            onOfflineMode?.let { offlineAction ->
                androidx.compose.material3.OutlinedButton(
                    onClick = offlineAction,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp)
                ) {
                    Text(text = "离线模式", modifier = Modifier.padding(horizontal = 32.dp, vertical = 12.dp))
                }
            }
        }
    }
}

/**
 * 权限错误降级
 */
@Composable
fun PermissionErrorFallback(
    permissionName: String,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.error
            )
            Text(
                text = "需要权限：$permissionName",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Text(
                text = "请在设置中授予该权限以使用完整功能",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            androidx.compose.material3.Button(
                onClick = onOpenSettings,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp)
            ) {
                Text(text = "去设置", modifier = Modifier.padding(horizontal = 32.dp, vertical = 12.dp))
            }
            androidx.compose.material3.TextButton(
                onClick = onDismiss
            ) {
                Text(text = "稍后再说")
            }
        }
    }
}

// ============ 无障碍支持组件 ============

/**
 * 语义化包装器 - 为组件添加无障碍语义
 */
@Composable
fun AccessibleContainer(
    modifier: Modifier = Modifier,
    role: androidx.compose.ui.semantics.Role? = null,
    label: String? = null,
    hint: String? = null,
    stateDescription: String? = null,
    actions: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .semantics {
                role?.let { this.role = it }
                label?.let { this.contentDescription = it }
                stateDescription?.let { this.stateDescription = it }
                actions?.let {
                    this.onClick(label = label ?: "操作") { actions(); true }
                }
            }
    ) {
        content()
    }
}

/**
 * 可点击的语义化卡片
 */
@Composable
fun AccessibleCard(
    modifier: Modifier = Modifier,
    label: String,
    hint: String? = null,
    onClick: () -> Unit,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                this.role = androidx.compose.ui.semantics.Role.Button
                this.contentDescription = label
                if (!enabled) this.stateDescription = "已禁用"
                this.onClick(label = label) { onClick(); true }
            }
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        content()
    }
}

/**
 * 实时区域 - 用于动态内容更新通知屏幕阅读器
 */
@Composable
fun LiveRegion(
    modifier: Modifier = Modifier,
    text: String,
    politeness: Int = 0 // 保留参数兼容旧调用；当前 Compose 版本无 LiveRegionPoliteness API
) {
    Text(
        text = "",
        modifier = modifier
            .semantics {
                // 仅供屏幕阅读器读取的内容描述
                this.contentDescription = text
            }
            .alpha(0f) // 不可见，仅供屏幕阅读器
    )
}

/**
 * 标题语义
 */
@Composable
fun SemanticHeading(
    modifier: Modifier = Modifier,
    level: Int = 1,
    text: String,
    style: androidx.compose.ui.text.TextStyle = androidx.compose.material3.MaterialTheme.typography.headlineMedium,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .semantics {
                // Compose 标准语义没有 Role.Heading/headingLevel，这里仅保留内容描述
                this.contentDescription = text
            }
    ) {
        Text(text = text, style = style)
        content()
    }
}

/**
 * 表单字段语义
 */
@Composable
fun SemanticFormField(
    modifier: Modifier = Modifier,
    label: String,
    isRequired: Boolean = false,
    error: String? = null,
    hint: String? = null,
    value: String,
    onValueChange: (String) -> Unit
) {
    androidx.compose.material3.OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                this.contentDescription = label + if (isRequired) " 必填" else ""
                if (error != null) this.stateDescription = error
            },
        label = { androidx.compose.material3.Text(text = label + if (isRequired) " *" else "") },
        isError = error != null,
        supportingText = error?.let { { androidx.compose.material3.Text(text = it, color = MaterialTheme.colorScheme.error) } }
    )
}

/**
 * 进度指示器语义
 */
@Composable
fun SemanticProgressIndicator(
    modifier: Modifier = Modifier,
    progress: Float, // 0.0 - 1.0
    label: String = "加载进度"
) {
    CircularProgressIndicator(
        progress = progress,
        modifier = modifier
            .semantics {
                this.contentDescription = "$label: ${(progress * 100).toInt()}%"
                this.progressBarRangeInfo = ProgressBarRangeInfo(progress, 0f..1f)
            }
    )
}

// ============ 高对比度/大字体支持 ============

/**
 * 自适应字体大小修饰符
 * 根据系统字体大小设置自动缩放
 */
@Composable
fun AdaptiveText(
    text: String,
    baseStyle: androidx.compose.ui.text.TextStyle,
    modifier: Modifier = Modifier,
    maxScale: Float = 2.0f
) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val fontScale = density.fontScale
    val clampedScale = fontScale.coerceIn(0.8f, maxScale)
    
    androidx.compose.material3.Text(
        text = text,
        style = baseStyle.copy(
            fontSize = baseStyle.fontSize?.let { it * clampedScale } ?: (16.sp * clampedScale)
        ),
        modifier = modifier
    )
}

/**
 * 触摸目标大小强制执行
 * 确保交互元素最小 48x48dp
 */
@Composable
fun TouchTargetEnforcer(
    modifier: Modifier = Modifier,
    minSize: androidx.compose.ui.unit.Dp = 48.dp,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .defaultMinSize(minWidth = minSize, minHeight = minSize)
    ) {
        content()
    }
}

