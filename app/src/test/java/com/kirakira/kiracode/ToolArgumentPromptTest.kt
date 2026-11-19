package com.kirakira.kiracode

import com.kirakira.kiracode.domain.model.WorkMode
import com.kirakira.kiracode.domain.prompt.BuiltinSystemPrompt
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolArgumentPromptTest {

    @Test
    fun `builtin prompt documents multiline escaping with positive and negative examples`() {
        val prompt = BuiltinSystemPrompt.build(WorkMode.AGENT)

        assertTrue(prompt.contains("工具参数（arguments）必须是合法的 JSON 字符串"))
        assertTrue(prompt.contains("换行符必须写成 \\n"))
        assertTrue(prompt.contains("正确：\"replaceString\": \"<div>\\n  <p>内容</p>\\n</div>\""))
        assertTrue(prompt.contains("错误：\"replaceString\": \"<div>"))
    }
}
