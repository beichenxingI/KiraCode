package com.kirakira.kiracode.domain.model

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * 草稿模型 - 用于保存编辑过程中的中间状态
 */
@Serializable
data class Draft(
    val id: String = UUID.randomUUID().toString(),
    val card: CharacterCard,
    val referenceCard: CharacterCard?,  // 关联的参考卡（可选）
    val generationRequest: GenerationRequest?, // 生成请求记录（可选）
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun withUpdatedCard(newCard: CharacterCard): Draft {
        return this.copy(
            card = newCard,
            updatedAt = System.currentTimeMillis()
        )
    }
}