package com.kirakira.kiracode.ui.workspace

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import com.kirakira.kiracode.ui.theme.AuroraColorTokens

/**
 * 轻量 Markdown 渲染器（纯 Compose，流式友好）。
 *
 * 支持：标题(#/##/###)、粗体(**x**)、斜体(*x*)、行内代码(`x`)、
 * 代码块(```)、引用(>)、无序/有序列表(- / 1.)、表格(|)。
 *
 * 每次对完整文本重新渲染，短文本下性能可接受，边流式输出边渲染。
 */
object MarkdownText {

    private val QuoteColor = AuroraColorTokens.TextCaption
    private val CodeColor = AuroraColorTokens.SignalSuccess

    fun render(markdown: String, baseColor: Color, baseSize: TextUnit): AnnotatedString {
        return buildAnnotatedString {
            var inCodeBlock = false
            val codeBuf = StringBuilder()

            fun flushCode() {
                if (codeBuf.isNotEmpty()) {
                    withStyle(SpanStyle(fontFamily = FontFamily.Monospace, fontSize = baseSize, color = CodeColor)) {
                        append(codeBuf.toString().trimEnd())
                    }
                    append('\n')
                    codeBuf.clear()
                }
            }

            val lines = markdown.split('\n')
            for (line in lines) {
                if (line.trimStart().startsWith("```")) {
                    if (inCodeBlock) { flushCode(); inCodeBlock = false } else { inCodeBlock = true }
                    continue
                }
                if (inCodeBlock) { codeBuf.append(line).append('\n'); continue }

                when {
                    line.trimStart().startsWith("### ") -> {
                        inline(baseColor, line.trimStart().removePrefix("### "), bold = true, scale = 1.15f)
                        append('\n')
                    }
                    line.trimStart().startsWith("## ") -> {
                        inline(baseColor, line.trimStart().removePrefix("## "), bold = true, scale = 1.25f)
                        append('\n')
                    }
                    line.trimStart().startsWith("# ") -> {
                        inline(baseColor, line.trimStart().removePrefix("# "), bold = true, scale = 1.4f)
                        append('\n')
                    }
                    line.trimStart().startsWith(">") -> {
                        withStyle(SpanStyle(color = QuoteColor, fontSize = baseSize * 0.92f)) {
                            append("▎ ")
                            inline(QuoteColor, line.trimStart().removePrefix(">").trim(), bold = false, scale = 0.92f)
                        }
                        append('\n')
                    }
                    else -> {
                        val bullet = Regex("""^\s*[-*+]\s+(.*)$""").matchEntire(line)
                        val ordered = Regex("""^\s*(\d+)\.\s+(.*)$""").matchEntire(line)
                        val table = line.trimStart().startsWith("|")
                        val tableSep = Regex("""\|[\s:\-|]+\|""").matches(line.trim())

                        when {
                            tableSep -> { /* 表格分隔行跳过 */ append('\n') }
                            bullet != null -> {
                                append("  • ")
                                inline(baseColor, bullet.groupValues[1], bold = false, scale = 1f)
                                append('\n')
                            }
                            ordered != null -> {
                                append("  ${ordered.groupValues[1]}. ")
                                inline(baseColor, ordered.groupValues[2], bold = false, scale = 1f)
                                append('\n')
                            }
                            table -> {
                                val cells = line.trim().trim('|').split("|").map { it.trim() }
                                cells.forEachIndexed { i, c ->
                                    if (i > 0) append("   ")
                                    inline(baseColor, c, bold = false, scale = 1f)
                                }
                                append('\n')
                            }
                            line.isBlank() -> append('\n')
                            else -> {
                                inline(baseColor, line, bold = false, scale = 1f)
                                append('\n')
                            }
                        }
                    }
                }
            }
            flushCode()
        }
    }

    /** 在 AnnotatedString.Builder 作用域内解析行内标记并追加 */
    private fun androidx.compose.ui.text.AnnotatedString.Builder.inline(
        baseColor: Color,
        text: String,
        bold: Boolean,
        scale: Float
    ) {
        var rest = text
        // 先处理行内代码
        while (true) {
            val s = rest.indexOf('`')
            if (s < 0) break
            val e = rest.indexOf('`', s + 1)
            if (e < 0) break
            inlineBold(baseColor, rest.substring(0, s), bold)
            withStyle(SpanStyle(fontFamily = FontFamily.Monospace, color = CodeColor, background = AuroraColorTokens.LineSecondary)) {
                append(rest.substring(s + 1, e))
            }
            rest = rest.substring(e + 1)
        }
        inlineBold(baseColor, rest, bold)
    }

    private fun androidx.compose.ui.text.AnnotatedString.Builder.inlineBold(
        baseColor: Color,
        text: String,
        bold: Boolean
    ) {
        val weight = if (bold) FontWeight.Bold else FontWeight.Normal
        // 粗体 **x**
        val boldRe = Regex("""\*\*(.+?)\*\*""")
        var last = 0
        for (m in boldRe.findAll(text)) {
            inlineItalic(baseColor, text.substring(last, m.range.first), weight)
            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = baseColor)) {
                append(m.groupValues[1])
            }
            last = m.range.last + 1
        }
        inlineItalic(baseColor, text.substring(last), weight)
    }

    private fun androidx.compose.ui.text.AnnotatedString.Builder.inlineItalic(
        baseColor: Color,
        text: String,
        weight: FontWeight
    ) {
        val itRe = Regex("""\*(?!\*)(.+?)\*""")
        var last = 0
        for (m in itRe.findAll(text)) {
            withStyle(SpanStyle(fontWeight = weight, color = baseColor)) {
                append(text.substring(last, m.range.first))
            }
            withStyle(SpanStyle(fontStyle = FontStyle.Italic, fontWeight = weight, color = baseColor)) {
                append(m.groupValues[1])
            }
            last = m.range.last + 1
        }
        if (last < text.length) {
            withStyle(SpanStyle(fontWeight = weight, color = baseColor)) {
                append(text.substring(last))
            }
        }
    }
}