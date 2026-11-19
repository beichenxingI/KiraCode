package com.kirakira.kiracode.data.repository

import com.kirakira.kiracode.data.local.db.dao.PreferenceDao
import com.kirakira.kiracode.data.local.db.entity.PreferenceDimensionEntity
import com.kirakira.kiracode.data.local.db.entity.PreferenceOptionEntity
import com.kirakira.kiracode.domain.model.DefaultPreferences
import com.kirakira.kiracode.domain.model.PreferenceDimension
import com.kirakira.kiracode.domain.model.PreferenceOption
import com.kirakira.kiracode.domain.repository.PreferenceRepository
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext

class PreferenceRepositoryImpl @Inject constructor(
    private val dao: PreferenceDao
) : PreferenceRepository {

    override fun observeDimensions(): Flow<List<PreferenceDimension>> =
        combine(dao.observeDimensions(), dao.observeAllOptions()) { dimensions, options ->
            dimensions.map { dimension ->
                PreferenceDimension(
                    id = dimension.id,
                    label = dimension.label,
                    isBuiltin = dimension.isBuiltin,
                    sortOrder = dimension.sortOrder,
                    options = options
                        .asSequence()
                        .filter { it.dimensionId == dimension.id }
                        .sortedBy { it.sortOrder }
                        .map { it.toDomain() }
                        .toList()
                )
            }
        }

    override suspend fun ensureDefaults() = withContext(Dispatchers.IO) {
        DefaultPreferences.dimensions.forEach { dimension ->
            dao.insertDimension(dimension.toEntity())
            dimension.options.forEach { dao.insertOption(it.toEntity()) }
        }
    }

    override suspend fun addDimension(label: String): Result<PreferenceDimension> =
        withContext(Dispatchers.IO) {
            runCatching {
                val order = (dao.getAllDimensions().maxOfOrNull { it.sortOrder } ?: -1) + 1
                val entity = PreferenceDimensionEntity(
                    id = UUID.randomUUID().toString(),
                    label = label.trim(),
                    isBuiltin = false,
                    sortOrder = order
                )
                dao.insertDimension(entity)
                entity.toDomain()
            }
        }

    override suspend fun updateDimension(id: String, label: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val current = dao.getDimensionById(id) ?: error("维度不存在")
                dao.updateDimension(current.copy(label = label.trim()))
            }
        }

    override suspend fun deleteDimension(id: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val current = dao.getDimensionById(id) ?: return@runCatching
                if (current.isBuiltin) error("内置维度不可删除")
                dao.deleteOptionsForDimension(id)
                dao.deleteDimension(current)
            }
        }

    override suspend fun addOption(
        dimensionId: String,
        label: String,
        rule: String
    ): Result<PreferenceOption> = withContext(Dispatchers.IO) {
        runCatching {
            val order = (dao.getOptionsForDimension(dimensionId).maxOfOrNull { it.sortOrder } ?: -1) + 1
            val entity = PreferenceOptionEntity(
                id = UUID.randomUUID().toString(),
                dimensionId = dimensionId,
                label = label.trim(),
                rule = rule.trim(),
                isBuiltin = false,
                sortOrder = order
            )
            dao.insertOption(entity)
            entity.toDomain()
        }
    }

    override suspend fun updateOption(id: String, label: String, rule: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val current = dao.getOptionById(id) ?: error("选项不存在")
                dao.updateOption(current.copy(label = label.trim(), rule = rule.trim()))
            }
        }

    override suspend fun deleteOption(id: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val current = dao.getOptionById(id) ?: return@runCatching
                if (current.isBuiltin) error("内置选项不可删除")
                dao.deleteOption(current)
            }
        }

    private fun PreferenceDimension.toEntity() = PreferenceDimensionEntity(
        id = id,
        label = label,
        isBuiltin = isBuiltin,
        sortOrder = sortOrder
    )

    private fun PreferenceOption.toEntity() = PreferenceOptionEntity(
        id = id,
        dimensionId = dimensionId,
        label = label,
        rule = rule,
        isBuiltin = isBuiltin,
        sortOrder = sortOrder
    )

    private fun PreferenceDimensionEntity.toDomain() = PreferenceDimension(
        id = id,
        label = label,
        isBuiltin = isBuiltin,
        sortOrder = sortOrder
    )

    private fun PreferenceOptionEntity.toDomain() = PreferenceOption(
        id = id,
        dimensionId = dimensionId,
        label = label,
        rule = rule,
        isBuiltin = isBuiltin,
        sortOrder = sortOrder
    )
}
