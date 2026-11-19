package com.kirakira.kiracode.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.kirakira.kiracode.domain.model.CharacterCard
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.encodeToString

/**
 * 角色卡实体类 (Room)
 * 对应 CharacterCard 领域模型，用于数据库持久化
 */
@Entity(tableName = "character_cards")
data class CharacterCardEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    // 基础信息
    val name: String,
    val description: String,
    val personality: String,
    val firstMessage: String,
    val creator: String = "KiraCard",

    // 示例对话
    val exampleDialogue: String = "",

    // 世界观/背景
    val scenario: String = "",
    val depth: Int = 0,

    // 扩展字段
    val creatorNotes: String = "",
    val systemPrompt: String = "",
    val postHistoryInstructions: String = "",

    // 元数据
    val uuid: String,
    val createdAt: Long,
    val updatedAt: Long,
    val version: String = "1.0",
    val avatar: String? = null,
    val spec: String = "chara_card_v2",
    val specVersion: String = "2.0",

    // 序列化存储的复杂字段 (JSON 字符串)
    val tagsJson: String = "[]",
    val alternateGreetingsJson: String = "[]",
    val extensionsJson: String = "{}",
    // V2.0 新增：世界书/正则/线格式原文（无损保真）
    val characterBookJson: String = "null",
    val regexScriptsJson: String = "[]",
    val rawJson: String = "null"
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true; allowSpecialFloatingPointValues = true }

        fun fromDomain(card: CharacterCard): CharacterCardEntity {
            return CharacterCardEntity(
                name = card.name,
                description = card.description,
                personality = card.personality,
                firstMessage = card.firstMessage,
                creator = card.creator,
                exampleDialogue = card.exampleDialogue,
                scenario = card.scenario,
                depth = card.depth,
                creatorNotes = card.creatorNotes,
                systemPrompt = card.systemPrompt,
                postHistoryInstructions = card.postHistoryInstructions,
                uuid = card.uuid,
                createdAt = card.createdAt,
                updatedAt = card.updatedAt,
                version = card.version,
                avatar = card.avatar,
                spec = card.spec,
                specVersion = card.specVersion,
                tagsJson = json.encodeToString(card.tags),
                alternateGreetingsJson = json.encodeToString(card.alternateGreetings),
                extensionsJson = encodeExtensions(card.extensions),
                characterBookJson = encodeElement(card.characterBook?.let { com.kirakira.kiracode.domain.model.st.StCardCodec.encodeBook(it) }),
                regexScriptsJson = json.encodeToString(
                    kotlinx.serialization.builtins.ListSerializer(com.kirakira.kiracode.domain.model.st.StRegexScript.serializer()),
                    card.regexScripts
                ),
                rawJson = encodeElement(card.rawData)
            )
        }

        /**
         * 将 JsonElement? 序列化为字符串；null 时输出 "null"
         */
        private fun encodeExtensions(ext: JsonElement?): String {
            return if (ext == null) "{}" else json.encodeToString(JsonElement.serializer(), ext)
        }

        private fun encodeElement(element: JsonElement?): String {
            return if (element == null) "null" else json.encodeToString(JsonElement.serializer(), element)
        }
    }

    /**
     * 转换为领域模型
     */
    fun toDomain(): CharacterCard {
        return CharacterCard(
            name = name,
            description = description,
            personality = personality,
            firstMessage = firstMessage,
            creator = creator,
            tags = decodeStringListSafe(tagsJson),
            exampleDialogue = exampleDialogue,
            scenario = scenario,
            depth = depth,
            creatorNotes = creatorNotes,
            systemPrompt = systemPrompt,
            postHistoryInstructions = postHistoryInstructions,
            alternateGreetings = decodeStringListSafe(alternateGreetingsJson),
            uuid = uuid,
            createdAt = createdAt,
            updatedAt = updatedAt,
            version = version,
            avatar = avatar,
            spec = spec,
            specVersion = specVersion,
            nickname = null,
            characterBook = decodeJsonElementOrNull(characterBookJson)?.let {
                runCatching { com.kirakira.kiracode.domain.model.st.StCardCodec.decodeBook(it) }.getOrNull()
            },
            regexScripts = runCatching {
                json.decodeFromString(
                    kotlinx.serialization.builtins.ListSerializer(com.kirakira.kiracode.domain.model.st.StRegexScript.serializer()),
                    regexScriptsJson
                )
            }.getOrDefault(emptyList()),
            extensions = decodeExtensions(extensionsJson),
            rawData = decodeJsonObjectOrNull(rawJson)
        )
    }

    /**
     * 将字符串解析为 JsonElement？若内容为空对象则返回 null
     */
    private fun decodeExtensions(jsonString: String): JsonElement? {
        val parsed = json.decodeFromString(JsonElement.serializer(), jsonString)
        return if (parsed is JsonObject && parsed.isEmpty()) null else parsed
    }
}

/**
 * 草稿实体类 (Room)
 * 用于存储编辑中的角色卡草稿
 */
@Entity(tableName = "drafts")
data class DraftEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    // 关联的角色卡数据 (复用 CharacterCardEntity 字段)
    val name: String,
    val description: String,
    val personality: String,
    val firstMessage: String,
    val creator: String = "KiraCard",

    val exampleDialogue: String = "",
    val scenario: String = "",
    val depth: Int = 0,
    val creatorNotes: String = "",
    val systemPrompt: String = "",
    val postHistoryInstructions: String = "",

    val uuid: String,
    val createdAt: Long,
    val updatedAt: Long,
    val version: String = "1.0",
    val avatar: String? = null,
    val spec: String = "chara_card_v2",
    val specVersion: String = "2.0",

    val tagsJson: String = "[]",
    val alternateGreetingsJson: String = "[]",
    val extensionsJson: String = "{}",
    val characterBookJson: String = "null",
    val regexScriptsJson: String = "[]",
    val rawJson: String = "null"
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true; allowSpecialFloatingPointValues = true }

        private fun encodeElement(element: JsonElement?): String =
            if (element == null) "null" else json.encodeToString(JsonElement.serializer(), element)

        fun fromDomain(card: CharacterCard): DraftEntity {
            return DraftEntity(
                name = card.name,
                description = card.description,
                personality = card.personality,
                firstMessage = card.firstMessage,
                creator = card.creator,
                exampleDialogue = card.exampleDialogue,
                scenario = card.scenario,
                depth = card.depth,
                creatorNotes = card.creatorNotes,
                systemPrompt = card.systemPrompt,
                postHistoryInstructions = card.postHistoryInstructions,
                uuid = card.uuid,
                createdAt = card.createdAt,
                updatedAt = card.updatedAt,
                version = card.version,
                avatar = card.avatar,
                spec = card.spec,
                specVersion = card.specVersion,
                tagsJson = json.encodeToString(card.tags),
                alternateGreetingsJson = json.encodeToString(card.alternateGreetings),
                extensionsJson = if (card.extensions == null) "{}" else json.encodeToString(JsonElement.serializer(), card.extensions),
                characterBookJson = encodeElement(card.characterBook?.let { com.kirakira.kiracode.domain.model.st.StCardCodec.encodeBook(it) }),
                regexScriptsJson = json.encodeToString(
                    kotlinx.serialization.builtins.ListSerializer(com.kirakira.kiracode.domain.model.st.StRegexScript.serializer()),
                    card.regexScripts
                ),
                rawJson = encodeElement(card.rawData)
            )
        }
    }

    fun toDomain(): CharacterCard {
        val parsedExt: JsonElement? = runCatching {
            json.decodeFromString(JsonElement.serializer(), extensionsJson)
        }.getOrNull()
        val ext: JsonElement? = if (parsedExt is JsonObject && parsedExt.isEmpty()) null else parsedExt

        return CharacterCard(
            name = name,
            description = description,
            personality = personality,
            firstMessage = firstMessage,
            creator = creator,
            tags = decodeStringListSafe(tagsJson),
            exampleDialogue = exampleDialogue,
            scenario = scenario,
            depth = depth,
            creatorNotes = creatorNotes,
            systemPrompt = systemPrompt,
            postHistoryInstructions = postHistoryInstructions,
            alternateGreetings = decodeStringListSafe(alternateGreetingsJson),
            uuid = uuid,
            createdAt = createdAt,
            updatedAt = updatedAt,
            version = version,
            avatar = avatar,
            spec = spec,
            specVersion = specVersion,
            nickname = null,
            characterBook = decodeJsonElementOrNull(characterBookJson)?.let {
                runCatching { com.kirakira.kiracode.domain.model.st.StCardCodec.decodeBook(it) }.getOrNull()
            },
            regexScripts = runCatching {
                json.decodeFromString(
                    kotlinx.serialization.builtins.ListSerializer(com.kirakira.kiracode.domain.model.st.StRegexScript.serializer()),
                    regexScriptsJson
                )
            }.getOrDefault(emptyList()),
            extensions = ext,
            rawData = decodeJsonObjectOrNull(rawJson)
        )
    }
}

/**
 * 会话实体类 (Room)
 * 存储聊天会话的元数据
 */
@Entity(
    tableName = "sessions",
    indices = [Index(value = ["updatedAt"], name = "idx_sessions_updatedAt")]
)
data class SessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val title: String,
    val summary: String = "", // 自动总结的上下文摘要
    val systemPrompt: String = "", // 会话级系统提示词
    val model: String = "gpt-4o-mini",
    val temperature: Float = 0.8f,
    val maxTokens: Int = 4096,
    val referenceCardId: Long? = null, // 关联的参考卡 ID
    val uuid: String = java.util.UUID.randomUUID().toString(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val messageCount: Int = 0,
    val totalTokens: Int = 0,
    val isArchived: Boolean = false,
    // V3.0 会话置顶
    val isPinned: Boolean = false,
    // V2.0 工作区持久化：会话绑定的工作卡与模式（恢复工作区即恢复卡）
    val workingCardJson: String = "",
    val workMode: String = "AGENT",
    val selectedReferenceIdsJson: String = "[]",
    val selectedPreferenceIdsJson: String = "[]",
    // 阶段 B：定稿事件与计划/任务快照
    val eventSnapshotJson: String = "[]"
) {
    companion object {
        fun create(
            title: String,
            model: String = "gpt-4o-mini",
            temperature: Float = 0.8f,
            maxTokens: Int = 4096,
            referenceCardId: Long? = null
        ): SessionEntity {
            val now = System.currentTimeMillis()
            return SessionEntity(
                title = title,
                model = model,
                temperature = temperature,
                maxTokens = maxTokens,
                referenceCardId = referenceCardId,
                createdAt = now,
                updatedAt = now
            )
        }
    }
}

/**
 * 消息实体类 (Room)
 * 存储会话中的单条消息
 */
@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["sessionId", "createdAt"], name = "idx_messages_session_created"),
        Index(value = ["sessionId", "role"], name = "idx_messages_session_role")
    ]
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val sessionId: Long,
    val role: String, // "system" | "user" | "assistant" | "tool"
    val content: String,
    val name: String? = null,
    val toolCallsJson: String? = null,
    val toolCallId: String? = null,
    val tokens: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val metadataJson: String = "{}", // 扩展元数据 (如生成参数、引用的卡片等)
    @ColumnInfo(name = "thinking", defaultValue = "")
    val thinking: String = ""
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true; allowSpecialFloatingPointValues = true }

        fun create(
            sessionId: Long,
            role: String,
            content: String,
            name: String? = null,
            tokens: Int = 0,
            metadataJson: String = "{}"
        ): MessageEntity {
            return MessageEntity(
                sessionId = sessionId,
                role = role,
                content = content,
                name = name,
                tokens = tokens,
                metadataJson = metadataJson
            )
        }

        fun createSystemMessage(sessionId: Long, content: String): MessageEntity {
            return create(sessionId, "system", content)
        }

        fun createUserMessage(sessionId: Long, content: String): MessageEntity {
            return create(sessionId, "user", content)
        }

        fun createAssistantMessage(sessionId: Long, content: String, tokens: Int = 0): MessageEntity {
            return create(sessionId, "assistant", content, tokens = tokens)
        }
    }
}

/**
 * 参考卡实体类 (Room)
 * 存储用户导入的参考角色卡，用于风格模仿
 */
@Entity(
    tableName = "reference_cards",
    indices = [Index(value = ["name"], name = "idx_reference_cards_name")]
)
data class ReferenceCardEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val name: String,
    val description: String,
    val personality: String,
    val firstMessage: String,
    val creator: String = "KiraCard",
    val exampleDialogue: String = "",
    val scenario: String = "",
    val depth: Int = 0,
    val creatorNotes: String = "",
    val systemPrompt: String = "",
    val postHistoryInstructions: String = "",
    val tagsJson: String = "[]",
    val alternateGreetingsJson: String = "[]",
    val extensionsJson: String = "{}",
    val uuid: String = java.util.UUID.randomUUID().toString(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val avatar: String? = null,
    val spec: String = "chara_card_v2",
    val specVersion: String = "2.0",
    val source: String = "import", // "import" | "generated" | "manual"
    val styleFeaturesJson: String = "{}", // 提取的风格特征 JSON
    val characterBookJson: String = "null",
    val regexScriptsJson: String = "[]",
    val rawJson: String = "null"
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true; allowSpecialFloatingPointValues = true }

        private fun encodeElement(element: JsonElement?): String =
            if (element == null) "null" else json.encodeToString(JsonElement.serializer(), element)

        fun fromDomain(card: CharacterCard, source: String = "import"): ReferenceCardEntity {
            return ReferenceCardEntity(
                name = card.name,
                description = card.description,
                personality = card.personality,
                firstMessage = card.firstMessage,
                creator = card.creator,
                exampleDialogue = card.exampleDialogue,
                scenario = card.scenario,
                depth = card.depth,
                creatorNotes = card.creatorNotes,
                systemPrompt = card.systemPrompt,
                postHistoryInstructions = card.postHistoryInstructions,
                tagsJson = json.encodeToString(card.tags),
                alternateGreetingsJson = json.encodeToString(card.alternateGreetings),
                extensionsJson = if (card.extensions == null) "{}" else json.encodeToString(JsonElement.serializer(), card.extensions),
                uuid = card.uuid,
                createdAt = card.createdAt,
                updatedAt = card.updatedAt,
                avatar = card.avatar,
                spec = card.spec,
                specVersion = card.specVersion,
                source = source,
                characterBookJson = encodeElement(card.characterBook?.let { com.kirakira.kiracode.domain.model.st.StCardCodec.encodeBook(it) }),
                regexScriptsJson = json.encodeToString(
                    kotlinx.serialization.builtins.ListSerializer(com.kirakira.kiracode.domain.model.st.StRegexScript.serializer()),
                    card.regexScripts
                ),
                rawJson = encodeElement(card.rawData)
            )
        }
    }

    fun toDomain(): CharacterCard {
        val parsedExt: JsonElement? = runCatching {
            json.decodeFromString(JsonElement.serializer(), extensionsJson)
        }.getOrNull()
        val ext: JsonElement? = if (parsedExt is JsonObject && parsedExt.isEmpty()) null else parsedExt

        return CharacterCard(
            name = name,
            description = description,
            personality = personality,
            firstMessage = firstMessage,
            creator = creator,
            tags = decodeStringListSafe(tagsJson),
            exampleDialogue = exampleDialogue,
            scenario = scenario,
            depth = depth,
            creatorNotes = creatorNotes,
            systemPrompt = systemPrompt,
            postHistoryInstructions = postHistoryInstructions,
            alternateGreetings = decodeStringListSafe(alternateGreetingsJson),
            uuid = uuid,
            createdAt = createdAt,
            updatedAt = updatedAt,
            version = specVersion,
            avatar = avatar,
            spec = spec,
            specVersion = specVersion,
            nickname = null,
            characterBook = decodeJsonElementOrNull(characterBookJson)?.let {
                runCatching { com.kirakira.kiracode.domain.model.st.StCardCodec.decodeBook(it) }.getOrNull()
            },
            regexScripts = runCatching {
                json.decodeFromString(
                    kotlinx.serialization.builtins.ListSerializer(com.kirakira.kiracode.domain.model.st.StRegexScript.serializer()),
                    regexScriptsJson
                )
            }.getOrDefault(emptyList()),
            extensions = ext,
            rawData = decodeJsonObjectOrNull(rawJson)
        )
    }
}

/**
 * 将 JSON 字符串解析为 JsonElement；"null"/空串/非法内容返回 null
 */
internal fun decodeJsonElementOrNull(jsonString: String): JsonElement? {
    if (jsonString.isBlank() || jsonString.trim() == "null") return null
    return runCatching {
        Json { ignoreUnknownKeys = true }.decodeFromString(JsonElement.serializer(), jsonString)
    }.getOrNull()
}

/**
 * Parse a JSON string into a JsonObject; "null"/blank/invalid content returns null
 */
internal fun decodeJsonObjectOrNull(jsonString: String): JsonObject? =
    decodeJsonElementOrNull(jsonString) as? JsonObject

/**
 * 防御性解析字符串列表字段（tags/alternate_greetings 等）。
 * 历史版本可能写入过非法 JSON（如纯文本），此处绝不抛异常——脏数据回退为空列表，
 * 保证任何数据库行都能安全转换为领域模型（仓库页闪退的根治点）。
 */
internal fun decodeStringListSafe(jsonString: String?): List<String> {
    if (jsonString.isNullOrBlank()) return emptyList()
    return runCatching {
        val element = Json { ignoreUnknownKeys = true }.decodeFromString(JsonElement.serializer(), jsonString)
        when (element) {
            is JsonArray -> element.mapNotNull { (it as? JsonPrimitive)?.content }
            else -> emptyList()
        }
    }.getOrDefault(emptyList())
}

private typealias JsonArray = kotlinx.serialization.json.JsonArray
private typealias JsonPrimitive = kotlinx.serialization.json.JsonPrimitive
