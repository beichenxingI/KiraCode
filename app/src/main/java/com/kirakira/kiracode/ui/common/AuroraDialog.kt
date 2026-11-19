package com.kirakira.kiracode.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.kirakira.kiracode.ui.theme.AuroraColorTokens
import com.kirakira.kiracode.ui.theme.AuroraTypography

/**
 * Aurora 统一弹窗壳：Dialog + Surface + 20dp 圆角 + Base1 背景 + 0.5dp 描边。
 * 所有 AlertDialog 迁移到此处，避免 Material3 默认的青绿色容器。
 */
@Composable
fun AuroraDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    maxWidth: Dp = 400.dp,
    maxHeight: Dp? = null,
    scrollable: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    Dialog(onDismissRequest = onDismissRequest) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = AuroraColorTokens.Base1,
            contentColor = AuroraColorTokens.TextBody,
            border = BorderStroke(0.5.dp, AuroraColorTokens.LinePrimary),
            modifier = modifier
                .widthIn(max = maxWidth)
                .then(if (maxHeight != null) Modifier.heightIn(max = maxHeight) else Modifier)
                .padding(horizontal = 24.dp)
                .imePadding()
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .then(
                        if (scrollable) Modifier.verticalScroll(rememberScrollState())
                        else Modifier
                    ),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                content = content
            )
        }
    }
}

@Composable
fun AuroraDialogTitle(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        style = AuroraTypography.PanelTitle,
        color = AuroraColorTokens.TextTitle,
        modifier = modifier
    )
}

@Composable
fun AuroraDialogText(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        style = AuroraTypography.Body,
        color = AuroraColorTokens.TextBody,
        modifier = modifier
    )
}

@Composable
fun AuroraDialogCaption(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        style = AuroraTypography.Caption,
        color = AuroraColorTokens.TextCaption,
        modifier = modifier
    )
}

/**
 * 底部按钮行：默认取消 + 确认；危险操作把 confirmColor 设为 SignalError。
 */
@Composable
fun AuroraDialogActions(
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: (() -> Unit)? = null,
    dismissText: String = "取消",
    confirmColor: Color = AuroraColorTokens.AuroraAccent,
    confirmEnabled: Boolean = true,
    dismissEnabled: Boolean = true,
    modifier: Modifier = Modifier,
    leading: @Composable () -> Unit = {}
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically
    ) {
        leading()
        if (onDismiss != null) {
            TextButton(
                onClick = onDismiss,
                enabled = dismissEnabled,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = AuroraColorTokens.TextCaption
                )
            ) {
                Text(dismissText)
            }
        }
        TextButton(
            onClick = onConfirm,
            enabled = confirmEnabled,
            colors = ButtonDefaults.textButtonColors(
                contentColor = confirmColor
            )
        ) {
            Text(confirmText)
        }
    }
}

/**
 * Aurora 风格输入框：Base2 容器、12dp 圆角、无下划线。
 */
@Composable
fun AuroraDialogTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else 8,
    enabled: Boolean = true
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, style = AuroraTypography.Caption) },
        placeholder = placeholder?.let { text ->
            { Text(text, style = AuroraTypography.Caption) }
        },
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = AuroraColorTokens.Base2,
            unfocusedContainerColor = AuroraColorTokens.Base2,
            disabledContainerColor = AuroraColorTokens.Base2,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            focusedTextColor = AuroraColorTokens.TextBody,
            unfocusedTextColor = AuroraColorTokens.TextBody,
            disabledTextColor = AuroraColorTokens.TextCaption,
            focusedLabelColor = AuroraColorTokens.TextCaption,
            unfocusedLabelColor = AuroraColorTokens.TextCaption,
            cursorColor = AuroraColorTokens.AuroraAccent
        ),
        modifier = modifier.fillMaxWidth()
    )
}
