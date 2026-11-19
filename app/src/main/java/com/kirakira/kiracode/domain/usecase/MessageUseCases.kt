package com.kirakira.kiracode.domain.usecase

import com.kirakira.kiracode.domain.exception.StorageException
import com.kirakira.kiracode.domain.model.Message
import com.kirakira.kiracode.domain.repository.MessageRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * 插入消息用例
 */
class InsertMessageUseCase @Inject constructor(
    private val repository: MessageRepository
) {

    suspend operator fun invoke(message: Message): Result<Long> = withContext(Dispatchers.IO) {
        repository.insertMessage(message)
    }

    suspend fun insertAll(messages: List<Message>): Result<List<Long>> = withContext(Dispatchers.IO) {
        repository.insertMessages(messages)
    }
}

/**
 * 获取会话消息用例
 */
class GetMessagesUseCase @Inject constructor(
    private val repository: MessageRepository
) {

    suspend operator fun invoke(
        sessionId: Long,
        limit: Int = 100,
        offset: Int = 0
    ): Result<List<Message>> = withContext(Dispatchers.IO) {
        repository.getMessagesBySessionId(sessionId, limit, offset)
    }

    suspend fun getRecent(sessionId: Long, limit: Int): Result<List<Message>> = withContext(Dispatchers.IO) {
        repository.getRecentMessages(sessionId, limit)
    }

    fun observe(sessionId: Long): kotlinx.coroutines.flow.Flow<List<Message>> = repository.observeMessages(sessionId)

    suspend fun getCount(sessionId: Long): Result<Int> = withContext(Dispatchers.IO) {
        repository.getMessageCount(sessionId)
    }

    suspend fun getTotalTokens(sessionId: Long): Result<Int> = withContext(Dispatchers.IO) {
        repository.getTotalTokens(sessionId)
    }
}

/**
 * 删除会话消息用例
 */
class DeleteMessagesBySessionUseCase @Inject constructor(
    private val repository: MessageRepository
) {

    suspend operator fun invoke(sessionId: Long): Result<Unit> = withContext(Dispatchers.IO) {
        repository.deleteMessagesBySessionId(sessionId)
    }
}

/**
 * 修剪旧消息用例 (保留最近 N 条)
 */
class TrimOldMessagesUseCase @Inject constructor(
    private val repository: MessageRepository
) {

    suspend operator fun invoke(sessionId: Long, keepCount: Int): Result<Int> = withContext(Dispatchers.IO) {
        repository.trimOldMessages(sessionId, keepCount)
    }
}