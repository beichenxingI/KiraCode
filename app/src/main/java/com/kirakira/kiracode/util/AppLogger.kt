package com.kirakira.kiracode.util

import android.util.Log
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale

/**
 * App 内日志收集器
 * 用于诊断流式输出、降级、超时等问题；
 * 同时镜像到 logcat（tag 不变），设置页「查看日志」可直接阅读/复制。
 *
 * 线程安全设计：
 * - 有界 ArrayDeque + 单锁：O(1) 追加/淘汰（避免 ConcurrentLinkedQueue.size() 的 O(n) 遍历——
 *   流式期间每个 delta 一次日志，O(n) size 会在主线程放大成 O(n²)）；
 * - [LogEntry.seq] 单调自增：作为日志页 LazyColumn 的唯一稳定 key
 *   （时间戳毫秒精度 + 重复消息会产生同 key 条目，曾导致滚动重组崩溃）；
 * - getAllLogs/getLogsAsText 返回同一锁下的一致快照（刷新/清空/后台写入并发安全）。
 */
object AppLogger {
    private const val MAX_LOGS = 500 // 最多保留 500 条
    /** 单条日志最大保留长度（含 E 级 stackTrace；超出截断，防巨型字符串撑爆内存与文本测量） */
    private const val MAX_MESSAGE_LENGTH = 8_000

    private val lock = Any()
    private val logs = ArrayDeque<LogEntry>(MAX_LOGS)
    private var seqCounter = 0L

    data class LogEntry(
        val timestamp: Long,
        val tag: String,
        val level: String,
        val message: String,
        /** 单调自增序号：唯一稳定 key（勿用 timestamp+hashCode 派生，同毫秒重复内容会冲突） */
        val seq: Long
    ) {
        fun format(): String {
            // SimpleDateFormat 非线程安全：用 ThreadLocal 实例（format 可能从 UI/复制/日志后台并发调用）
            val time = timeFormatHolder.get().format(Date(timestamp))
            return "[$time] $level/$tag: $message"
        }
    }

    private val timeFormatHolder = object : ThreadLocal<SimpleDateFormat>() {
        override fun initialValue() = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
    }

    fun d(tag: String, message: String) {
        Log.d(tag, message)
        addLog("D", tag, message)
    }

    fun w(tag: String, message: String) {
        Log.w(tag, message)
        addLog("W", tag, message)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        Log.e(tag, message, throwable)
        val msg = if (throwable != null) "$message\n${throwable.stackTraceToString()}" else message
        addLog("E", tag, msg)
    }

    private fun addLog(level: String, tag: String, message: String) {
        synchronized(lock) {
            val entry = LogEntry(
                timestamp = System.currentTimeMillis(),
                tag = tag,
                level = level,
                message = message.take(MAX_MESSAGE_LENGTH),
                seq = ++seqCounter
            )
            logs.addLast(entry)
            if (logs.size > MAX_LOGS) {
                logs.removeFirst() // 淘汰最旧
            }
        }
    }

    /** 线程安全快照（锁下拷贝，遍历期间不受并发写入影响） */
    fun getAllLogs(): List<LogEntry> = synchronized(lock) { logs.toList() }

    /** 全文文本：锁内仅拷贝快照，格式化在锁外进行（复制全部日志可在后台 dispatcher 调用） */
    fun getLogsAsText(): String =
        getAllLogs().joinToString("\n") { it.format() }

    fun clear() {
        synchronized(lock) { logs.clear() }
    }
}
