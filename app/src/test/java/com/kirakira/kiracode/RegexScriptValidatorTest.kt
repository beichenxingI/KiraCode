package com.kirakira.kiracode

import com.kirakira.kiracode.domain.agent.CardOperation
import com.kirakira.kiracode.domain.agent.CardOperationApplier
import com.kirakira.kiracode.domain.agent.ToolRegistry
import com.kirakira.kiracode.domain.agent.core.CardObservation
import com.kirakira.kiracode.domain.agent.core.UpdateCardAction
import com.kirakira.kiracode.domain.agent.tools.RegexScriptValidator
import com.kirakira.kiracode.domain.model.CharacterCard
import com.kirakira.kiracode.domain.model.RegexGenerationMode
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RegexScriptValidatorTest {

    private val validFrontend = """
        ```html
        <!DOCTYPE html>
        <html lang="zh-CN">
        <head>
          <meta charset="UTF-8">
          <meta name="viewport" content="width=device-width, initial-scale=1.0">
          <title>Scene UI</title>
          <style>
            body { color: hotpink; }
            .panel { border-radius: 33px; }
          </style>
        </head>
        <body>
          <main id="app"></main>
          <script type="text/plain" id="raw-scene">${'$'}1</script>
          <script>
            document.getElementById('app').textContent =
              document.getElementById('raw-scene').textContent;
          </script>
        </body>
        </html>
        ```
    """.trimIndent()

    private fun add(frontend: String, flagsOmitted: Boolean = true): CardOperation.RegexAdd =
        CardOperation.RegexAdd(
            scriptName = "前端外壳渲染",
            findRegex = "/<scene_body>([\\s\\S]*?)<\\/scene_body>/g",
            replaceString = frontend,
            placement = if (flagsOmitted) emptyList() else listOf(2),
            markdownOnly = if (flagsOmitted) null else true,
            promptOnly = if (flagsOmitted) null else false
        )

    @Test
    fun `complete fenced html5 single file passes and receives safe defaults`() {
        val result = RegexScriptValidator.validateAndNormalize(
            add(validFrontend),
            RegexGenerationMode.FULL_FRONTEND
        )

        assertTrue(result.errors.joinToString(), result.isValid)
        val normalized = result.operation as CardOperation.RegexAdd
        assertEquals(listOf(2), normalized.placement)
        assertEquals(true, normalized.markdownOnly)
        assertEquals(false, normalized.promptOnly)
    }

    @Test
    fun `full frontend without fence or complete document fails`() {
        val fragment = """<div class="scene-shell"><style>body{color:red}</style><script>x()</script></div>"""
        val noDoctype = validFrontend.replace("<!DOCTYPE html>", "")
        val noHtmlClose = validFrontend.replace("</html>", "")

        listOf(fragment, noDoctype, noHtmlClose).forEach { replaceString ->
            val result = RegexScriptValidator.validateAndNormalize(
                add(replaceString),
                RegexGenerationMode.FULL_FRONTEND
            )
            assertFalse("应拒绝不完整前端: $replaceString", result.isValid)
            assertTrue(result.errors.isNotEmpty())
        }
    }

    @Test
    fun `compressed or literal-newline full frontend fails`() {
        val compressed = validFrontend.replace("\n", "")
        val fakeNewlines = "```html\n" +
            "<!DOCTYPE html>\\n<html>\\n<head>\\n<style>\\nbody{color:red}\\n</style>\\n</head>" +
            "\\n<body>\\n<div>${'$'}1</div>\\n</body>\\n</html>\n```"
        val mixedRealAndFakeNewlines = validFrontend.replace(
            "<main id=\"app\"></main>",
            "<main id=\"app\">${"\\n  ".repeat(6)}</main>"
        )
        val inlineFence = validFrontend.replaceFirst("```html\n", "```html<!DOCTYPE html>\n", ignoreCase = true)

        listOf(compressed, fakeNewlines, mixedRealAndFakeNewlines, inlineFence).forEach { replaceString ->
            val result = RegexScriptValidator.validateAndNormalize(
                add(replaceString),
                RegexGenerationMode.FULL_FRONTEND
            )
            assertFalse("应拒绝压缩/伪换行前端", result.isValid)
        }
    }

    @Test
    fun `full frontend without injection slot or with invalid schema fails`() {
        val noSlot = validFrontend.replace("${'$'}1", "")
        val noHeadClose = validFrontend.replace("</head>", "")
        val noBody = validFrontend.replace("<body>", "<div>").replace("</body>", "</div>")

        listOf(noSlot, noHeadClose, noBody).forEach { replaceString ->
            val result = RegexScriptValidator.validateAndNormalize(
                add(replaceString),
                RegexGenerationMode.FULL_FRONTEND
            )
            assertFalse("应拒绝缺少必要结构的完整前端", result.isValid)
        }
    }

    @Test
    fun `inline details and span templates pass without document checks`() {
        val details = CardOperation.RegexAdd(
            scriptName = "心理活动",
            findRegex = "/thinking([\\s\\S]*?)<\\/think>/g",
            replaceString = "<details style=\"color:#8ab\"><summary>内心</summary>${'$'}1</details>",
            placement = emptyList()
        )
        val span = CardOperation.RegexAdd(
            scriptName = "动作",
            findRegex = "/\\*([^*\\r\\n]{1,300})\\*/g",
            replaceString = "<span class=\"act\" style=\"color:orange\">${'$'}1</span>"
        )

        listOf(details, span).forEach { operation ->
            val result = RegexScriptValidator.validateAndNormalize(
                operation,
                RegexGenerationMode.FOLDABLE_TAGS
            )
            assertTrue(result.errors.joinToString(), result.isValid)
            assertEquals(true, (result.operation as CardOperation.RegexAdd).markdownOnly)
        }
    }

    @Test
    fun `html beautification with both persistence flags false is rejected`() {
        val unsafe = (CardOperation.RegexAdd(
            scriptName = "不安全美化",
            findRegex = "/「([^」\\r\\n]+)」/g",
            replaceString = "<span style=\"color:red\">${'$'}1</span>",
            markdownOnly = false,
            promptOnly = false
        ))

        val result = RegexScriptValidator.validateAndNormalize(
            unsafe,
            RegexGenerationMode.LIGHTWEIGHT
        )

        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("永久污染聊天记录") })
    }

    @Test
    fun `regex validation rejects uncompilable pattern and missing capture group`() {
        val badRegex = add(validFrontend).copy(findRegex = "/<scene_body>([\\s\\S]*?/g")
        val badGroup = add(validFrontend).copy(replaceString = validFrontend.replace("${'$'}1", "${'$'}2"))

        assertFalse(RegexScriptValidator.validateAndNormalize(
            badRegex,
            RegexGenerationMode.FULL_FRONTEND
        ).isValid)
        assertFalse(RegexScriptValidator.validateAndNormalize(
            badGroup,
            RegexGenerationMode.FULL_FRONTEND
        ).isValid)
    }

    @Test
    fun `visual choices never affect validation`() {
        val visualVariants = listOf(
            validFrontend.replace("hotpink", "oklch(0.7 0.2 30)"),
            validFrontend.replace("border-radius: 33px;", "border-radius: 0;"),
            validFrontend.replace("body { color: hotpink; }", "body { background: linear-gradient(red, blue); }"),
            validFrontend.replace("<main id=\"app\"></main>", "<main id=\"app\"><p>free design</p></main>")
        )

        visualVariants.forEach { replaceString ->
            val result = RegexScriptValidator.validateAndNormalize(
                add(replaceString),
                RegexGenerationMode.FULL_FRONTEND
            )
            assertTrue(result.errors.joinToString(), result.isValid)
        }
    }

    @Test
    fun `regex update is normalized and validated like add`() {
        val result = RegexScriptValidator.validateAndNormalize(
            CardOperation.RegexUpdate(
                idOrName = "旧前端",
                scriptName = "新前端",
                findRegex = "/<scene_body>([\\s\\S]*?)<\\/scene_body>/g",
                replaceString = validFrontend
            ),
            RegexGenerationMode.FULL_FRONTEND
        )

        assertTrue(result.errors.joinToString(), result.isValid)
        assertTrue(result.operation is CardOperation.RegexUpdate)
    }

    @Test
    fun `tool registry blocks invalid frontend and returns actionable error`() = runTest {
        val registry = ToolRegistry { card, operations ->
            Result.success(CardOperationApplier.apply(card, operations))
        }
        registry.setCurrentCard(CharacterCard(name = "测试卡"))
        val observation = registry.execute(
            UpdateCardAction(
                operations = listOf(add("<div><style>x</style><script>y</script></div>")),
                functionName = "update_card_regex_add"
            ),
            RegexGenerationMode.FULL_FRONTEND
        ) as CardObservation

        assertTrue(observation.isError)
        assertTrue(observation.content.contains("完整前端正则未写入"))
        assertTrue(observation.content.contains("视觉设计不受限制"))
        assertTrue(registry.getCurrentCard()!!.regexScripts.isEmpty())
    }
}
