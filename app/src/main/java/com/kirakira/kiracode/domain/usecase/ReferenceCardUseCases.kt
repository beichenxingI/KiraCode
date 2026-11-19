package com.kirakira.kiracode.domain.usecase

import com.kirakira.kiracode.domain.exception.StorageException
import com.kirakira.kiracode.domain.model.CharacterCard
import com.kirakira.kiracode.domain.repository.ReferenceCardRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * 保存参考卡用例
 */
class SaveReferenceCardUseCase @Inject constructor(
    private val repository: ReferenceCardRepository
) {

    suspend operator fun invoke(card: CharacterCard, source: String = "import"): Result<Long> = withContext(Dispatchers.IO) {
        repository.saveReferenceCard(card, source)
    }
}

/**
 * 获取参考卡用例
 */
class GetReferenceCardUseCase @Inject constructor(
    private val repository: ReferenceCardRepository
) {

    suspend operator fun invoke(id: Long): Result<CharacterCard?> = withContext(Dispatchers.IO) {
        repository.getReferenceCard(id)
    }

    suspend fun getByUuid(uuid: String): Result<CharacterCard?> = withContext(Dispatchers.IO) {
        repository.getReferenceCardByUuid(uuid)
    }
}

/**
 * 获取参考卡列表用例
 */
class ListReferenceCardsUseCase @Inject constructor(
    private val repository: ReferenceCardRepository
) {

    suspend operator fun invoke(limit: Int = 100, offset: Int = 0): Result<List<CharacterCard>> = withContext(Dispatchers.IO) {
        repository.getAllReferenceCards(limit, offset)
    }

    fun observeAll(): kotlinx.coroutines.flow.Flow<List<CharacterCard>> = repository.observeReferenceCards()

    suspend fun getBySource(source: String): Result<List<CharacterCard>> = withContext(Dispatchers.IO) {
        repository.getReferenceCardsBySource(source)
    }

    suspend fun getCount(): Result<Int> = withContext(Dispatchers.IO) {
        repository.getReferenceCardCount()
    }
}

/**
 * 更新参考卡风格特征用例
 */
class UpdateReferenceCardStyleFeaturesUseCase @Inject constructor(
    private val repository: ReferenceCardRepository
) {

    suspend operator fun invoke(id: Long, styleFeaturesJson: String): Result<Unit> = withContext(Dispatchers.IO) {
        repository.updateStyleFeatures(id, styleFeaturesJson)
    }
}

/**
 * 删除参考卡用例
 */
class DeleteReferenceCardUseCase @Inject constructor(
    private val repository: ReferenceCardRepository
) {

    suspend operator fun invoke(id: Long): Result<Unit> = withContext(Dispatchers.IO) {
        repository.deleteReferenceCard(id)
    }
}

/**
 * 删除所有参考卡用例
 */
class DeleteAllReferenceCardsUseCase @Inject constructor(
    private val repository: ReferenceCardRepository
) {

    suspend operator fun invoke(): Result<Int> = withContext(Dispatchers.IO) {
        repository.deleteAllReferenceCards()
    }
}