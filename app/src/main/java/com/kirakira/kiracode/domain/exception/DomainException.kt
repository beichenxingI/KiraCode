package com.kirakira.kiracode.domain.exception

/**
 * 业务领域异常基类
 * 所有领域层异常均继承此类，便于统一处理
 */
open class DomainException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

/**
 * JSON 解析异常
 */
class ParseException(
    message: String,
    val rawInput: String? = null,
    cause: Throwable? = null
) : DomainException(message, cause)

/**
 * 数据校验异常
 */
class ValidationException(
    message: String,
    val fieldErrors: Map<String, String> = emptyMap(),
    cause: Throwable? = null
) : DomainException(message, cause)

/**
 * 网络请求异常
 */
class NetworkException(
    message: String,
    val errorCode: String? = null,
    val statusCode: Int? = null,
    val rawResponse: String? = null,
    cause: Throwable? = null
) : DomainException(message, cause)

/**
 * API 业务错误异常 (如内容过滤、配额不足等)
 */
class ApiException(
    message: String,
    val errorCode: String,
    val details: Map<String, Any> = emptyMap(),
    val rawResponse: String? = null,
    cause: Throwable? = null
) : DomainException(message, cause)

/**
 * 存储操作异常 (Room/DataStore)
 */
class StorageException(
    message: String,
    val operation: String? = null,
    cause: Throwable? = null
) : DomainException(message, cause)

/**
 * 导出操作异常
 */
class ExportException(
    message: String,
    val format: String? = null,
    cause: Throwable? = null
) : DomainException(message, cause)

/**
 * Mock API 专用异常 (测试用)
 */
class MockApiException(
    message: String,
    val errorCode: String = "MOCK_ERROR",
    cause: Throwable? = null
) : DomainException(message, cause)