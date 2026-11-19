package com.kirakira.kiracode.domain.repository

import com.kirakira.kiracode.domain.model.CharacterCard

/**
 * 角色卡仓库接口 - Domain 层定义，Data 层实现
 *
 * MVP 阶段：草稿与历史记录统一作为 [CharacterCard] 存储（草稿表只保存正在编辑的快照）。
 */
interface CharacterCardRepository {

    // ============ 草稿管理 ============

    /**
     * 保存草稿（新建或更新），返回新建/更新后的草稿行 ID
     */
    suspend fun saveDraft(card: CharacterCard): Result<Long>

    /**
     * 根据 ID 查询单个草稿
     */
    suspend fun getDraft(id: Long): Result<CharacterCard?>

    /**
     * 查询全部草稿，按更新时间倒序
     */
    suspend fun getAllDrafts(): Result<List<CharacterCard>>

    /**
     * 删除指定草稿
     */
    suspend fun deleteDraft(id: Long): Result<Unit>

    suspend fun deleteDraftByUuid(uuid: String): Result<Unit>

    // ============ 历史记录 / 成品区 (V3.0) ============

    /**
     * 保存角色卡到成品区（按 uuid upsert：已存在则更新，否则新增），返回行 ID
     */
    suspend fun saveHistory(card: CharacterCard): Result<Long>

    /**
     * 查询历史记录/成品卡，按更新时间倒序，支持分页
     */
    suspend fun getHistory(limit: Int = 100, offset: Int = 0): Result<List<CharacterCard>>

    /**
     * V3.0 成品区：根据 uuid 查询成品卡行 ID（不存在返回 null）
     */
    suspend fun getHistoryIdByUuid(uuid: String): Result<Long?>

    /**
     * V3.0 成品区：按行 ID 删除成品卡
     */
    suspend fun deleteFinishedCard(id: Long): Result<Unit>
}
