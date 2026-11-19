package com.kirakira.kiracode.data.repository

import com.kirakira.kiracode.data.local.db.AppDatabase
import com.kirakira.kiracode.data.mapper.MessageMapper
import com.kirakira.kiracode.data.mapper.ReferenceCardMapper
import com.kirakira.kiracode.data.mapper.SessionMapper
import com.kirakira.kiracode.domain.model.CharacterCard
import com.kirakira.kiracode.domain.model.Message
import com.kirakira.kiracode.domain.model.Session
import com.kirakira.kiracode.domain.repository.MessageRepository
import com.kirakira.kiracode.domain.repository.ReferenceCardRepository
import com.kirakira.kiracode.domain.repository.SessionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 会话仓库实现
 */
@Singleton
class SessionRepositoryImpl @Inject constructor(
    private val database: AppDatabase,
    private val sessionMapper: SessionMapper
) : SessionRepository {

    override suspend fun createSession(session: Session): Result<Long> = withContext(Dispatchers.IO) {
        runCatching {
            val entity = sessionMapper.toEntity(session)
            database.sessionDao().insert(entity)
        }
    }

    override suspend fun getSession(id: Long): Result<Session?> = withContext(Dispatchers.IO) {
        runCatching {
            database.sessionDao().getById(id)?.let { sessionMapper.toDomain(it) }
        }
    }

    override suspend fun getSessionByUuid(uuid: String): Result<Session?> = withContext(Dispatchers.IO) {
        runCatching {
            database.sessionDao().getByUuid(uuid)?.let { sessionMapper.toDomain(it) }
        }
    }

    override suspend fun getActiveSessions(limit: Int, offset: Int): Result<List<Session>> = withContext(Dispatchers.IO) {
        runCatching {
            sessionMapper.toDomainList(database.sessionDao().getActiveSessions(limit, offset))
        }
    }

    override suspend fun getArchivedSessions(limit: Int, offset: Int): Result<List<Session>> = withContext(Dispatchers.IO) {
        runCatching {
            sessionMapper.toDomainList(database.sessionDao().getArchivedSessions(limit, offset))
        }
    }

    override fun observeActiveSessions(): Flow<List<Session>> {
        return database.sessionDao().getActiveSessionsFlow().map { sessionMapper.toDomainList(it) }
    }

    override suspend fun getActiveCount(): Result<Int> = withContext(Dispatchers.IO) {
        runCatching { database.sessionDao().getActiveCount() }
    }

    override suspend fun updateTitle(id: Long, title: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            database.sessionDao().updateTitle(id, title, System.currentTimeMillis())
            Unit
        }
    }

    override suspend fun updateSummary(id: Long, summary: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            database.sessionDao().updateSummary(id, summary, System.currentTimeMillis())
            Unit
        }
    }

    override suspend fun updateStats(id: Long, messageCount: Int, totalTokens: Int): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            database.sessionDao().updateStats(id, messageCount, totalTokens, System.currentTimeMillis())
            Unit
        }
    }

    override suspend fun incrementStats(id: Long, deltaTokens: Int): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val affected = database.sessionDao()
                .incrementStats(id, deltaTokens, System.currentTimeMillis())
            if (affected == 0) {
                android.util.Log.w("SessionRepo", "incrementStats: no row matched id=$id")
            }
        }
    }

    override suspend fun setArchived(id: Long, archived: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            database.sessionDao().setArchived(id, archived, System.currentTimeMillis())
            Unit
        }
    }

    override suspend fun setPinned(id: Long, pinned: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            database.sessionDao().setPinned(id, pinned)
            Unit
        }
    }

    override suspend fun updateSystemPrompt(id: Long, systemPrompt: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            database.sessionDao().updateSystemPrompt(id, systemPrompt, System.currentTimeMillis())
            Unit
        }
    }

    override suspend fun updateWorkspace(
        id: Long,
        workingCardJson: String,
        workMode: String,
        referenceIdsJson: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            database.sessionDao().updateWorkspace(id, workingCardJson, workMode, referenceIdsJson, System.currentTimeMillis())
            Unit
        }
    }

    override suspend fun updateModelSettings(
        id: Long,
        model: String,
        temperature: Float,
        maxTokens: Int
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            database.sessionDao().updateModelSettings(id, model, temperature, maxTokens)
            Unit
        }
    }

    override suspend fun updateSelectedPreferences(id: Long, preferenceIdsJson: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                database.sessionDao().updateSelectedPreferences(id, preferenceIdsJson, System.currentTimeMillis())
                Unit
            }
        }

    override suspend fun updateEventSnapshot(id: Long, snapshotJson: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                database.sessionDao().updateEventSnapshot(id, snapshotJson, System.currentTimeMillis())
                Unit
            }
        }

    override suspend fun deleteSession(id: Long): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            database.sessionDao().deleteById(id)
            Unit
        }
    }

    override suspend fun deleteAllArchived(): Result<Int> = withContext(Dispatchers.IO) {
        runCatching { database.sessionDao().deleteAllArchived() }
    }
}

/**
 * 消息仓库实现
 */
@Singleton
class MessageRepositoryImpl @Inject constructor(
    private val database: AppDatabase,
    private val messageMapper: MessageMapper
) : MessageRepository {

    override suspend fun insertMessage(message: Message): Result<Long> = withContext(Dispatchers.IO) {
        runCatching {
            val entity = messageMapper.toEntity(message)
            database.messageDao().insert(entity)
        }
    }

    override suspend fun insertMessages(messages: List<Message>): Result<List<Long>> = withContext(Dispatchers.IO) {
        runCatching {
            val entities = messages.map { messageMapper.toEntity(it) }
            database.messageDao().insertAll(entities)
        }
    }

    override suspend fun getMessagesBySessionId(sessionId: Long, limit: Int, offset: Int): Result<List<Message>> = withContext(Dispatchers.IO) {
        runCatching {
            messageMapper.toDomainList(database.messageDao().getBySessionId(sessionId, limit, offset))
        }
    }

    override suspend fun getRecentMessages(sessionId: Long, limit: Int): Result<List<Message>> = withContext(Dispatchers.IO) {
        runCatching {
            messageMapper.toDomainList(database.messageDao().getRecentBySessionId(sessionId, limit))
        }
    }

    override fun observeMessages(sessionId: Long): Flow<List<Message>> {
        return database.messageDao().getBySessionIdFlow(sessionId).map { messageMapper.toDomainList(it) }
    }

    override suspend fun getMessageCount(sessionId: Long): Result<Int> = withContext(Dispatchers.IO) {
        runCatching { database.messageDao().getCountBySessionId(sessionId) }
    }

    override suspend fun getTotalTokens(sessionId: Long): Result<Int> = withContext(Dispatchers.IO) {
        runCatching { database.messageDao().getTotalTokensBySessionId(sessionId) ?: 0 }
    }

    override suspend fun deleteMessagesBySessionId(sessionId: Long): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            database.messageDao().deleteBySessionId(sessionId)
            Unit
        }
    }

    override suspend fun trimOldMessages(sessionId: Long, keepCount: Int): Result<Int> = withContext(Dispatchers.IO) {
        runCatching { database.messageDao().trimOldMessages(sessionId, keepCount) }
    }
}

/**
 * 参考卡仓库实现
 */
@Singleton
class ReferenceCardRepositoryImpl @Inject constructor(
    private val database: AppDatabase,
    private val referenceCardMapper: ReferenceCardMapper,
    private val warehouse: com.kirakira.kiracode.data.local.WarehouseStorage
) : ReferenceCardRepository {

    override suspend fun saveReferenceCard(card: CharacterCard, source: String): Result<Long> = withContext(Dispatchers.IO) {
        runCatching {
            // 完整卡 JSON 落盘（几十 MB 的 V3 卡装不进 SQLite 单行）
            val json = com.kirakira.kiracode.domain.model.st.StCardCodec.toJsonString(card, pretty = false)
            val filePath = warehouse.writeCard(card.uuid, json)
            if (filePath == null) error("写入卡片文件失败")

            // 封面抽离落盘，避免 base64 data URI 撑爆 CursorWindow
            val coverPair = warehouse.extractCoverBytes(card.avatar)
            val coverBytes = coverPair?.first
            val coverPath = if (coverBytes != null) warehouse.writeCover(card.uuid, coverBytes, coverPair.second) else null
            // HTTP 头像保留 URL（读的时候能被 Coil 加载）
            val avatarRef = when {
                coverPath != null -> coverPath
                card.avatar?.startsWith("http") == true -> card.avatar
                else -> null
            }

            // 数据库行只存元数据 + 文件路径；大字段（描述/世界书/正则/raw）全置空
            val uuid = card.uuid
            val existing = database.referenceCardDao().getByUuid(uuid)
            val base = referenceCardMapper.toEntity(card, source)
            val light = base.copy(
                // 元数据保留（截断防单行过大）
                description = card.description.take(300),
                personality = card.personality.take(200),
                firstMessage = card.firstMessage.take(300),
                scenario = card.scenario.take(300),
                creatorNotes = "",
                systemPrompt = "",
                postHistoryInstructions = "",
                exampleDialogue = "",
                characterBookJson = "null",
                regexScriptsJson = "[]",
                rawJson = "file:$filePath",
                avatar = avatarRef,
                styleFeaturesJson = "{}"
            )
            val savedId = if (existing != null) {
                database.referenceCardDao().update(light.copy(id = existing.id, createdAt = existing.createdAt))
                existing.id
            } else {
                database.referenceCardDao().insert(light)
            }
            android.util.Log.d("KiraCard/Repo", "saveReferenceCard OK uuid=$uuid name=${card.name} id=$savedId file=$filePath cover=$coverPath")
            savedId
        }.onFailure { e ->
            android.util.Log.e("KiraCard/Repo", "saveReferenceCard FAIL name=${card.name} err=${e.message}", e)
        }
    }

    /** 从数据库行恢复完整卡：rawJson 为 file: 标记时从文件读完整 JSON */
    private fun restore(entity: com.kirakira.kiracode.data.local.db.entity.ReferenceCardEntity): CharacterCard {
        val raw = entity.rawJson
        val decoded = if (raw.startsWith("file:")) {
            val path = raw.removePrefix("file:")
            val json = warehouse.readCard(path)
            if (json != null) {
                com.kirakira.kiracode.domain.model.st.StCardCodec.decode(json).getOrElse { entity.toDomain() }
            } else entity.toDomain()
        } else entity.toDomain()
        // uuid 收口：StCardCodec.decode 不还原 uuid（默认随机生成），会导致挂载记录与注入查找跨加载失配；
        // 统一使用 DB 行的稳定 uuid
        val card = decoded.copy(uuid = entity.uuid)
        // 封面以 DB 的相对路径为准（JSON 内可能仍是 data URI 或不含封面）
        val coverRef = entity.avatar?.takeIf { it.isNotBlank() } ?: card.avatar
        val coverAbs = warehouse.resolveCoverPath(coverRef)
        return if (coverAbs != card.avatar) card.copy(avatar = coverAbs) else card
    }

    override suspend fun getReferenceCard(id: Long): Result<CharacterCard?> = withContext(Dispatchers.IO) {
        runCatching {
            database.referenceCardDao().getById(id)?.let { restore(it) }
        }
    }

    override suspend fun getReferenceCardByUuid(uuid: String): Result<CharacterCard?> = withContext(Dispatchers.IO) {
        runCatching {
            database.referenceCardDao().getByUuid(uuid)?.let { restore(it) }
        }
    }

    override suspend fun getAllReferenceCards(limit: Int, offset: Int): Result<List<CharacterCard>> = withContext(Dispatchers.IO) {
        runCatching {
            database.referenceCardDao().getAll(limit, offset).mapNotNull { e -> runCatching { restore(e) }.getOrNull() }
        }
    }

    override fun observeReferenceCards(): Flow<List<CharacterCard>> {
        return database.referenceCardDao().getAllFlow().map { entities ->
            // 逐行容错 + 上游异常降级为空列表：仓库页任何情况下不崩溃
            entities.mapNotNull { entity -> runCatching { restore(entity) }.getOrNull() }
        }.catch { emit(emptyList()) }
    }

    override suspend fun getReferenceCardsBySource(source: String): Result<List<CharacterCard>> = withContext(Dispatchers.IO) {
        runCatching {
            database.referenceCardDao().getBySource(source).mapNotNull { e -> runCatching { restore(e) }.getOrNull() }
        }
    }

    override suspend fun updateStyleFeatures(id: Long, styleFeaturesJson: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            database.referenceCardDao().updateStyleFeatures(id, styleFeaturesJson, System.currentTimeMillis())
            Unit
        }
    }

    override suspend fun deleteReferenceCard(id: Long): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            database.referenceCardDao().getById(id)?.let { e ->
                warehouse.deleteCard(e.rawJson.removePrefix("file:"))
                warehouse.deleteCover(e.uuid)
                database.referenceCardDao().deleteById(id)
            }
            Unit
        }
    }

    override suspend fun deleteReferenceCardByUuid(uuid: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val entity = database.referenceCardDao().getByUuid(uuid)
            if (entity != null) {
                warehouse.deleteCard(entity.rawJson.removePrefix("file:"))
                warehouse.deleteCover(entity.uuid)
                database.referenceCardDao().deleteById(entity.id)
            }
            Unit
        }
    }

    override suspend fun deleteAllReferenceCards(): Result<Int> = withContext(Dispatchers.IO) {
        runCatching { database.referenceCardDao().deleteAll() }
    }

    override suspend fun getReferenceCardCount(): Result<Int> = withContext(Dispatchers.IO) {
        runCatching { database.referenceCardDao().getCount() }
    }
}