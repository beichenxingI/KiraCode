package com.kirakira.kiracode.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.kirakira.kiracode.ui.theme.IndustrialColorTokens
import com.kirakira.kiracode.ui.theme.IndustrialTypography

@Composable
fun MarkerText(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = text.uppercase(),  // Marker 总是大写
        style = IndustrialTypography.Marker,
        color = IndustrialColorTokens.TextTech,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
    )
}
