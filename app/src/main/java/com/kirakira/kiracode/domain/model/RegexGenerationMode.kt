package com.kirakira.kiracode.domain.model

/** 角色卡正则生成策略。 */
enum class RegexGenerationMode(val label: String, val description: String) {
    FULL_FRONTEND("状态前端", "整块 XML 输出替换为完整 HTML 前端，${'$'}1 注入正文"),
    FOLDABLE_TAGS("语义折叠", "每类标签一条正则，inline style 自包含，折叠/着色"),
    FOLDABLE_REASONING("折叠思维链", "单条正则折叠 <think> 推理块，零依赖"),
    LIGHTWEIGHT("轻量着色", "2-3 条极简着色正则，兼容任何平台");

    companion object {
        val DEFAULT: RegexGenerationMode = FOLDABLE_TAGS

        fun fromName(name: String?): RegexGenerationMode =
            entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}
