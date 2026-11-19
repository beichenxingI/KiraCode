package com.kirakira.kiracode.data.local.db.converter

import androidx.room.TypeConverter
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Room TypeConverters
 * 处理 List<String> 与数据库存储类型 (String) 的转换
 * 使用 kotlinx.serialization 进行 JSON 序列化/反序列化
 */
class Converters {

    private val json = Json { ignoreUnknownKeys = true }
    private val listSerializer = ListSerializer(String.serializer())

    @TypeConverter
    fun listToJson(list: List<String>): String = json.encodeToString(listSerializer, list)

    @TypeConverter
    fun jsonToList(jsonString: String): List<String> = json.decodeFromString(listSerializer, jsonString)
}
