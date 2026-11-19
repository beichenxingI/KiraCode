package com.kirakira.kiracode.data.mapper

import com.kirakira.kiracode.data.local.db.entity.CharacterCardEntity
import com.kirakira.kiracode.data.local.db.entity.DraftEntity
import com.kirakira.kiracode.data.local.db.entity.MessageEntity
import com.kirakira.kiracode.data.local.db.entity.ReferenceCardEntity
import com.kirakira.kiracode.data.local.db.entity.SessionEntity
import com.kirakira.kiracode.domain.model.CharacterCard
import com.kirakira.kiracode.domain.model.Message
import com.kirakira.kiracode.domain.model.Session
import kotlinx.serialization.json.Json

/**
 * CharacterCard 领域模型与实体转换器
 */
class CharacterCardMapper {

    fun toEntity(card: CharacterCard): CharacterCardEntity {
        return CharacterCardEntity.fromDomain(card)
    }

    fun toDomain(entity: CharacterCardEntity): CharacterCard {
        return entity.toDomain()
    }

    fun toDomainList(entities: List<CharacterCardEntity>): List<CharacterCard> {
        return entities.map { toDomain(it) }
    }
}

/**
 * Draft 领域模型与实体转换器
 */
class DraftMapper {

    fun toEntity(card: CharacterCard): DraftEntity {
        return DraftEntity.fromDomain(card)
    }

    fun toDomain(entity: DraftEntity): CharacterCard {
        return entity.toDomain()
    }

    fun toDomainList(entities: List<DraftEntity>): List<CharacterCard> {
        return entities.map { toDomain(it) }
    }
}

/**
 * Session 领域模型与实体转换器
 */
class SessionMapper {

    private val json = Json { ignoreUnknownKeys = true }

    fun toEntity(session: Session): SessionEntity {
        return SessionEntity(
            id = session.id,
            title = session.title,
            summary = session.summary,
            systemPrompt = session.systemPrompt,
            model = session.model,
            temperature = session.temperature,
            maxTokens = session.maxTokens,
            referenceCardId = session.referenceCardId,
            uuid = session.uuid,
            createdAt = session.createdAt,
            updatedAt = session.updatedAt,
            messageCount = session.messageCount,
            totalTokens = session.totalTokens,
            isArchived = session.isArchived,
            isPinned = session.isPinned,
            workingCardJson = session.workingCardJson,
            workMode = session.workMode,
            selectedReferenceIdsJson = session.selectedReferenceIdsJson,
            selectedPreferenceIdsJson = session.selectedPreferenceIdsJson,
            eventSnapshotJson = session.eventSnapshotJson
        )
    }

    fun toDomain(entity: SessionEntity): Session {
        return Session(
            id = entity.id,
            title = entity.title,
            summary = entity.summary,
            systemPrompt = entity.systemPrompt,
            model = entity.model,
            temperature = entity.temperature,
            maxTokens = entity.maxTokens,
            referenceCardId = entity.referenceCardId,
            uuid = entity.uuid,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
            messageCount = entity.messageCount,
            totalTokens = entity.totalTokens,
            isArchived = entity.isArchived,
            isPinned = entity.isPinned,
            workingCardJson = entity.workingCardJson,
            workMode = entity.workMode,
            selectedReferenceIdsJson = entity.selectedReferenceIdsJson,
            selectedPreferenceIdsJson = entity.selectedPreferenceIdsJson,
            eventSnapshotJson = entity.eventSnapshotJson
        )
    }

    fun toDomainList(entities: List<SessionEntity>): List<Session> {
        return entities.map { toDomain(it) }
    }
}

/**
 * Message 领域模型与实体转换器
 */
class MessageMapper {

    private val json = Json { ignoreUnknownKeys = true }

    fun toEntity(message: Message): MessageEntity {
        return MessageEntity(
            id = message.id,
            sessionId = message.sessionId,
            role = message.role,
            content = message.content,
            name = message.name,
            toolCallsJson = message.toolCallsJson,
            toolCallId = message.toolCallId,
            tokens = message.tokens,
            createdAt = message.createdAt,
            metadataJson = message.metadataJson,
            thinking = message.thinking
        )
    }

    fun toDomain(entity: MessageEntity): Message {
        return Message(
            id = entity.id,
            sessionId = entity.sessionId,
            role = entity.role,
            content = entity.content,
            name = entity.name,
            toolCallsJson = entity.toolCallsJson,
            toolCallId = entity.toolCallId,
            tokens = entity.tokens,
            createdAt = entity.createdAt,
            metadataJson = entity.metadataJson,
            thinking = entity.thinking
        )
    }

    fun toDomainList(entities: List<MessageEntity>): List<Message> {
        return entities.map { toDomain(it) }
    }
}

/**
 * ReferenceCard 领域模型与实体转换器
 */
class ReferenceCardMapper {

    private val json = Json { ignoreUnknownKeys = true }

    fun toEntity(card: CharacterCard, source: String = "import"): ReferenceCardEntity {
        return ReferenceCardEntity.fromDomain(card, source)
    }

    fun toDomain(entity: ReferenceCardEntity): CharacterCard {
        return entity.toDomain()
    }

    fun toDomainList(entities: List<ReferenceCardEntity>): List<CharacterCard> {
        return entities.map { toDomain(it) }
    }
}