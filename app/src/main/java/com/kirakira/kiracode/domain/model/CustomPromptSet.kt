package com.kirakira.kiracode.domain.model

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * 自定义提示词套（KiraCode V2 Beta）。
 *
 * 用户可维护多套自定义创作规则，每套有名称与内容；
 * 在数据控制面板中可快速切换「当前启用」的一套；重启不丢。
 */
@Serializable
data class CustomPromptSet(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val content: String,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * 自定义提示词套列表编解码（DataStore JSON 存储）。
 */
object CustomPromptSetCodec {
    private val json = kotlinx.serialization.json.Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encodeList(list: List<CustomPromptSet>): String = runCatching {
        json.encodeToString(
            kotlinx.serialization.builtins.ListSerializer(CustomPromptSet.serializer()), list
        )
    }.getOrDefault("[]")

    fun decodeList(json_: String?): List<CustomPromptSet> {
        if (json_.isNullOrBlank()) return emptyList()
        return runCatching {
            json.decodeFromString(
                kotlinx.serialization.builtins.ListSerializer(CustomPromptSet.serializer()), json_
            )
        }.getOrDefault(emptyList())
    }
}
