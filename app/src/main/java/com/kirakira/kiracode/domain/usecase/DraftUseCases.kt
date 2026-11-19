package com.kirakira.kiracode.domain.usecase

import com.kirakira.kiracode.domain.exception.StorageException
import com.kirakira.kiracode.domain.model.CharacterCard
import com.kirakira.kiracode.domain.repository.CharacterCardRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * 保存草稿用例
 * 将编辑中的角色卡保存到本地数据库
 */
class SaveDraftUseCase @Inject constructor(
    private val repository: CharacterCardRepository
) {

    suspend operator fun invoke(card: CharacterCard): Result<Long> = withContext(Dispatchers.IO) {
        val cardWithTimestamp = card.copyWithUpdatedTimestamp()
        repository.saveDraft(cardWithTimestamp)
    }
}

/**
 * 加载草稿列表用例
 */
class LoadDraftsUseCase @Inject constructor(
    private val repository: CharacterCardRepository
) {

    suspend operator fun invoke(): Result<List<CharacterCard>> = withContext(Dispatchers.IO) {
        repository.getAllDrafts()
    }
}
