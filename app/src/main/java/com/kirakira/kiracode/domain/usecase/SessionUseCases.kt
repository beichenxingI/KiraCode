package com.kirakira.kiracode.domain.usecase

import com.kirakira.kiracode.domain.exception.StorageException
import com.kirakira.kiracode.domain.model.Session
import com.kirakira.kiracode.domain.repository.SessionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * 创建会话用例
 */
class CreateSessionUseCase @Inject constructor(
    private val repository: SessionRepository
) {

    suspend operator fun invoke(
        title: String,
        model: String = "gpt-4o-mini",
        temperature: Float = 0.8f,
        maxTokens: Int = 4096,
        referenceCardId: Long? = null
    ): Result<Long> = withContext(Dispatchers.IO) {
        val session = Session.create(
            title = title,
            model = model,
            temperature = temperature,
            maxTokens = maxTokens,
            referenceCardId = referenceCardId
        )
        repository.createSession(session)
    }
}

/**
 * 获取会话用例
 */
class GetSessionUseCase @Inject constructor(
    private val repository: SessionRepository
) {

    suspend operator fun invoke(id: Long): Result<Session?> = withContext(Dispatchers.IO) {
        repository.getSession(id)
    }

    suspend fun getByUuid(uuid: String): Result<Session?> = withContext(Dispatchers.IO) {
        repository.getSessionByUuid(uuid)
    }
}

/**
 * 获取会话列表用例
 */
class ListSessionsUseCase @Inject constructor(
    private val repository: SessionRepository
) {

    suspend operator fun invoke(limit: Int = 50, offset: Int = 0): Result<List<Session>> = withContext(Dispatchers.IO) {
        repository.getActiveSessions(limit, offset)
    }

    suspend fun getArchived(limit: Int = 50, offset: Int = 0): Result<List<Session>> = withContext(Dispatchers.IO) {
        repository.getArchivedSessions(limit, offset)
    }

    fun observeActive(): kotlinx.coroutines.flow.Flow<List<Session>> = repository.observeActiveSessions()

    suspend fun getActiveCount(): Result<Int> = withContext(Dispatchers.IO) {
        repository.getActiveCount()
    }
}

/**
 * 更新会话标题用例
 */
class UpdateSessionTitleUseCase @Inject constructor(
    private val repository: SessionRepository
) {

    suspend operator fun invoke(id: Long, title: String): Result<Unit> = withContext(Dispatchers.IO) {
        repository.updateTitle(id, title)
    }
}

/**
 * 更新会话摘要用例 (用于上下文总结)
 */
class UpdateSessionSummaryUseCase @Inject constructor(
    private val repository: SessionRepository
) {

    suspend operator fun invoke(id: Long, summary: String): Result<Unit> = withContext(Dispatchers.IO) {
        repository.updateSummary(id, summary)
    }
}

/**
 * 更新会话统计用例
 */
class UpdateSessionStatsUseCase @Inject constructor(
    private val repository: SessionRepository
) {

    suspend operator fun invoke(id: Long, messageCount: Int, totalTokens: Int): Result<Unit> = withContext(Dispatchers.IO) {
        repository.updateStats(id, messageCount, totalTokens)
    }
}

/**
 * 设置会话归档状态用例
 */
class SetSessionArchivedUseCase @Inject constructor(
    private val repository: SessionRepository
) {

    suspend operator fun invoke(id: Long, archived: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        repository.setArchived(id, archived)
    }
}

/**
 * 更新会话系统提示词用例
 */
class UpdateSessionSystemPromptUseCase @Inject constructor(
    private val repository: SessionRepository
) {

    suspend operator fun invoke(id: Long, systemPrompt: String): Result<Unit> = withContext(Dispatchers.IO) {
        repository.updateSystemPrompt(id, systemPrompt)
    }
}

/**
 * 删除会话用例
 */
class DeleteSessionUseCase @Inject constructor(
    private val repository: SessionRepository
) {

    suspend operator fun invoke(id: Long): Result<Unit> = withContext(Dispatchers.IO) {
        repository.deleteSession(id)
    }
}

/**
 * 删除所有归档会话用例
 */
class DeleteAllArchivedSessionsUseCase @Inject constructor(
    private val repository: SessionRepository
) {

    suspend operator fun invoke(): Result<Int> = withContext(Dispatchers.IO) {
        repository.deleteAllArchived()
    }
}