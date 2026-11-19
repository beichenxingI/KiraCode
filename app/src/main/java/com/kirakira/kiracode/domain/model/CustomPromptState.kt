package com.kirakira.kiracode.domain.model

/**
 * 自定义提示词三态（P0：默认模板不再自动注入，规则必须用户主动启用）。
 *
 * - NotConfigured：新用户/从未配置/内容被清空 → 不注入任何【我的创作规则】段；
 * - Disabled：已保存内容但用户关闭开关 → 不注入，但保留文本便于重新开启时恢复；
 * - Enabled：内容非空且开关打开 → 注入。
 */
sealed interface CustomPromptState {

    object NotConfigured : CustomPromptState

    data class Disabled(val text: String) : CustomPromptState

    data class Enabled(val text: String) : CustomPromptState

    companion object {

        /**
         * 纯函数映射（可测试）。
         * @param enabled 新开关键（null=未写入新键）
         * @param newText 新文本键
         * @param legacyText 旧单套 customPrompt 键（迁移来源）
         */
        fun from(enabled: Boolean?, newText: String?, legacyText: String?): CustomPromptState {
            val text = newText ?: legacyText
            return when {
                text.isNullOrBlank() -> NotConfigured
                enabled == true -> Enabled(text)
                // 旧键迁移：历史上只要保存过即处于启用状态（旧实现无开关概念）
                enabled == null && newText == null && !legacyText.isNullOrBlank() -> Enabled(legacyText)
                else -> Disabled(text)
            }
        }
    }
}
