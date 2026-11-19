package com.kirakira.kiracode.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val IndustrialDarkColorScheme = darkColorScheme(
    primary = IndustrialColorTokens.SignalWarning,
    onPrimary = IndustrialColorTokens.Base0,
    secondary = IndustrialColorTokens.SignalSuccess,
    tertiary = IndustrialColorTokens.SignalThinking,
    background = IndustrialColorTokens.Base0,
    surface = IndustrialColorTokens.Base1,
    surfaceVariant = IndustrialColorTokens.Base2,
    onBackground = IndustrialColorTokens.TextBody,
    onSurface = IndustrialColorTokens.TextBody,
    error = IndustrialColorTokens.SignalError,
    outline = IndustrialColorTokens.LinePrimary
)

@Composable
fun IndustrialTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = IndustrialDarkColorScheme,
        content = content
    )
}
