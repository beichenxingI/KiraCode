package com.kirakira.kiracode.data.repository

import com.kirakira.kiracode.data.local.db.AppDatabase
import com.kirakira.kiracode.data.mapper.CharacterCardMapper
import com.kirakira.kiracode.data.mapper.DraftMapper
import com.kirakira.kiracode.domain.model.CharacterCard
import com.kirakira.kiracode.domain.repository.CharacterCardRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 角色卡仓库实现
 * 聚合 Room 数据库，实现领域层接口
 */
@Singleton
class CharacterCardRepositoryImpl @Inject constructor(
    private val database: AppDatabase,
    private val characterCardMapper: CharacterCardMapper,
    private val draftMapper: DraftMapper,
    private val warehouse: com.kirakira.kiracode.data.local.WarehouseStorage
) : CharacterCardRepository {

    // ========== 草稿管理 ==========

    override suspend fun saveDraft(card: CharacterCard): Result<Long> = withContext(Dispatchers.IO) {
        runCatching {
            // 草稿同样文件化，避免大卡撑爆 CursorWindow
            val json = com.kirakira.kiracode.domain.model.st.StCardCodec.toJsonString(card, pretty = false)
            val filePath = warehouse.writeCard("draft_${card.uuid}", json) ?: error("写入草稿文件失败")
            val light = draftMapper.toEntity(card).copy(
                description = card.description.take(300),
                personality = card.personality.take(200),
                firstMessage = card.firstMessage.take(300),
                scenario = card.scenario.take(300),
                characterBookJson = "null",
                regexScriptsJson = "[]",
                rawJson = "file:$filePath"
            )
            database.draftDao().insert(light)
        }
    }

    override suspend fun getDraft(id: Long): Result<CharacterCard?> = withContext(Dispatchers.IO) {
        runCatching {
            database.draftDao().getById(id)?.let { restoreDraft(it) }
        }
    }

    override suspend fun getAllDrafts(): Result<List<CharacterCard>> = withContext(Dispatchers.IO) {
        runCatching {
            database.draftDao().getAll(100, 0).mapNotNull { e -> runCatching { restoreDraft(e) }.getOrNull() }
        }
    }

    private fun restoreDraft(entity: com.kirakira.kiracode.data.local.db.entity.DraftEntity): CharacterCard {
        val raw = entity.rawJson
        val card = if (raw.startsWith("file:")) {
            val json = warehouse.readCard(raw.removePrefix("file:"))
            if (json != null) com.kirakira.kiracode.domain.model.st.StCardCodec.decode(json).getOrElse { entity.toDomain() }
            else entity.toDomain()
        } else entity.toDomain()
        val coverRef = entity.avatar?.takeIf { it.isNotBlank() } ?: card.avatar
        val coverAbs = warehouse.resolveCoverPath(coverRef)
        val restored = if (coverAbs != card.avatar) card.copy(avatar = coverAbs) else card
        // StCardCodec.decode 不还原 uuid；必须用 DB 行的稳定 uuid，否则按 uuid 删除会静默失败
        return restored.copy(uuid = entity.uuid)
    }

    override suspend fun deleteDraft(id: Long): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            database.draftDao().getById(id)?.let { e ->
                warehouse.deleteCard(e.rawJson.removePrefix("file:"))
                database.draftDao().deleteById(id)
            }
            Unit
        }
    }

    override suspend fun deleteDraftByUuid(uuid: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            database.draftDao().getByUuid(uuid)?.let { e ->
                warehouse.deleteCard(e.rawJson.removePrefix("file:"))
                database.draftDao().deleteById(e.id)
            }
            Unit
        }
    }

    // ========== 历史记录 (预留，正式版实现) ==========

    override suspend fun saveHistory(card: CharacterCard): Result<Long> = withContext(Dispatchers.IO) {
        runCatching {
            // 完整 JSON 落盘（大卡不进 SQLite 单行）
            val json = com.kirakira.kiracode.domain.model.st.StCardCodec.toJsonString(card, pretty = false)
            val filePath = warehouse.writeCard(card.uuid, json) ?: error("写入卡片文件失败")
            val coverPair = warehouse.extractCoverBytes(card.avatar)
            val coverBytes = coverPair?.first
            val coverPath = if (coverBytes != null) warehouse.writeCover(card.uuid, coverBytes, coverPair.second) else null
            val avatarRef = when {
                coverPath != null -> coverPath
                card.avatar?.startsWith("http") == true -> card.avatar
                else -> null
            }
            val light = characterCardMapper.toEntity(card).copy(
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
                avatar = avatarRef
            )
            val existingId = database.characterCardDao().getByUuid(card.uuid)?.id
            if (existingId != null && existingId > 0) {
                database.characterCardDao().update(light.copy(id = existingId))
                existingId
            } else {
                database.characterCardDao().insert(light)
            }
        }
    }

    private fun restoreFinished(entity: com.kirakira.kiracode.data.local.db.entity.CharacterCardEntity): CharacterCard {
        val raw = entity.rawJson
        val card = if (raw.startsWith("file:")) {
            val json = warehouse.readCard(raw.removePrefix("file:"))
            if (json != null) com.kirakira.kiracode.domain.model.st.StCardCodec.decode(json).getOrElse { entity.toDomain() }
            else entity.toDomain()
        } else entity.toDomain()
        val coverRef = entity.avatar?.takeIf { it.isNotBlank() } ?: card.avatar
        val coverAbs = warehouse.resolveCoverPath(coverRef)
        val restored = if (coverAbs != card.avatar) card.copy(avatar = coverAbs) else card
        // 同 restoreDraft：uuid 必须取 DB 实体，保证 getHistoryIdByUuid 可命中
        return restored.copy(uuid = entity.uuid)
    }

    override suspend fun getHistory(limit: Int, offset: Int): Result<List<CharacterCard>> = withContext(Dispatchers.IO) {
        runCatching {
            database.characterCardDao().getAll(limit, offset).mapNotNull { e -> runCatching { restoreFinished(e) }.getOrNull() }
        }
    }

    override suspend fun getHistoryIdByUuid(uuid: String): Result<Long?> = withContext(Dispatchers.IO) {
        runCatching {
            database.characterCardDao().getByUuid(uuid)?.id
        }
    }

    override suspend fun deleteFinishedCard(id: Long): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            database.characterCardDao().getById(id)?.let { e ->
                warehouse.deleteCard(e.rawJson.removePrefix("file:"))
                warehouse.deleteCover(e.uuid)
                database.characterCardDao().deleteById(id)
            }
            Unit
        }
    }
}
