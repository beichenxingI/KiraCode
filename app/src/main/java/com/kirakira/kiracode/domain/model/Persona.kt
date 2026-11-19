package com.kirakira.kiracode.domain.model

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * 灵魂：由多张角色卡训练（或手动编写）而成的创作风格提示词。
 *
 * 每次仅可启用一个人格；启用后其提示词注入系统消息，
 * 位于固定协议之后、用户自定义规则之前（风格基线，自定义规则可针对性覆盖细节）。
 */
@Serializable
data class Persona(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String = "",
    val prompt: String,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * 人格列表序列化编解码（DataStore JSON 存储）。
 */
object PersonaCodec {
    private val json = kotlinx.serialization.json.Json {
        ignoreUnknownKeys = true
        encodeDefaults = true // createdAt 等带默认值字段也参与编码，保证往返一致
    }

    fun encodeList(list: List<Persona>): String = runCatching {
        json.encodeToString(
            kotlinx.serialization.builtins.ListSerializer(Persona.serializer()), list
        )
    }.getOrDefault("[]")

    fun decodeList(json_: String?): List<Persona> {
        if (json_.isNullOrBlank()) return emptyList()
        return runCatching {
            json.decodeFromString(
                kotlinx.serialization.builtins.ListSerializer(Persona.serializer()), json_
            )
        }.getOrDefault(emptyList())
    }
}
