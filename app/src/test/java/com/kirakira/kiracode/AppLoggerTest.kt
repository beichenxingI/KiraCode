package com.kirakira.kiracode

import com.kirakira.kiracode.util.AppLogger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 诊断日志回归（日志页闪退修复）：
 *
 * 1. 条数达到上限后淘汰最旧项（O(1) 淘汰，不遍历队列）；
 * 2. 并发添加日志不抛异常且保持有界；
 * 3. 清空与后台写入并发时快照一致（size ≤ 上限）；
 * 4. 超长 stack trace 截断保存，显示/复制正常；
 * 5. seq 单调唯一——诊断页 LazyColumn 的唯一稳定 key
 *    （旧 key 由 timestamp+level+hashCode 派生，同毫秒重复内容条目会产生重复 key
 *      导致滚动重组抛 IllegalArgumentException 闪退）。
 */
class AppLoggerTest {

    @Before
    fun setUp() {
        AppLogger.clear()
    }

    @Test
    fun `达到上限后淘汰最旧项`() {
        repeat(600) { AppLogger.d("T", "msg-$it") }
        val logs = AppLogger.getAllLogs()
        assertEquals(500, logs.size)
        // 最旧的 100 条被淘汰：第一条应为 msg-100
        assertEquals("msg-100", logs.first().message)
        assertEquals("msg-599", logs.last().message)
        // 全量文本与快照一致
        val text = AppLogger.getLogsAsText()
        assertTrue(text.contains("msg-100"))
        assertTrue(!text.contains("\n[") || !text.contains("msg-99\n"))
    }

    @Test
    fun `并发添加不抛异常且保持有界`() {
        val threads = 8
        val perThread = 200
        val pool = Executors.newFixedThreadPool(threads)
        val start = CountDownLatch(1)
        val done = CountDownLatch(threads)
        repeat(threads) { t ->
            pool.execute {
                try {
                    start.await()
                    repeat(perThread) { i -> AppLogger.d("T$t", "并发-$i") }
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                } finally {
                    done.countDown()
                }
            }
        }
        start.countDown()
        assertTrue(done.await(10, TimeUnit.SECONDS))
        pool.shutdown()
        assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS))
        val logs = AppLogger.getAllLogs()
        assertEquals(500, logs.size)
        // seq 严格递增且唯一（并发下无重复/回退）
        assertEquals(logs.map { it.seq }.sorted(), logs.map { it.seq })
        assertEquals(500, logs.map { it.seq }.toSet().size)
    }

    @Test
    fun `清空与写入真并发_快照始终有界且格式完整`() {
        val pool = Executors.newFixedThreadPool(4)
        val stop = AtomicBoolean(false)
        val writerFuture = pool.submit {
            var i = 0
            while (!stop.get()) {
                AppLogger.d("W", "写入-${i++}")
            }
        }
        // 并发清空 50 次，每次立即取快照：快照必须有界且格式完整（不出现半写入/损坏条目）
        repeat(50) {
            AppLogger.clear()
            val snapshot = AppLogger.getAllLogs()
            assertTrue(snapshot.size <= 500)
            snapshot.forEach { assertTrue(it.format().isNotEmpty()) }
        }
        stop.set(true)
        writerFuture.get(5, TimeUnit.SECONDS)
        pool.shutdown()
        assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS))
    }

    @Test
    fun `超长消息截断保存_复制全文不超限`() {
        val huge = "x".repeat(20_000)
        AppLogger.e("T", huge)
        val logs = AppLogger.getAllLogs()
        assertEquals(1, logs.size)
        assertTrue(logs[0].message.length <= 8_000)
        val text = AppLogger.getLogsAsText()
        assertTrue(text.length <= 8_000 + 100)
    }

    @Test
    fun `同毫秒重复内容条目_seq仍然唯一`() {
        // 模拟流式期间同毫秒写入的重复日志（旧行为会产生重复 LazyColumn key）
        val ts = System.currentTimeMillis()
        repeat(10) {
            AppLogger.d("SSE", "收到 reasoningContent: 同内容")
        }
        val logs = AppLogger.getAllLogs()
        assertEquals(10, logs.size)
        assertEquals(10, logs.map { it.seq }.toSet().size)
        // seq 单调递增
        assertEquals(logs.map { it.seq }, logs.map { it.seq }.sorted())
        // 时间戳为真实写入时刻（不再依赖 timestamp 唯一性）
        assertTrue(logs.all { it.timestamp >= ts - 1 })
    }

    @Test
    fun `format包含时间级别与标签`() {
        AppLogger.w("Tag", "hello")
        val formatted = AppLogger.getAllLogs()[0].format()
        assertTrue(formatted.startsWith("["))
        assertTrue(formatted.contains("W/Tag: hello"))
    }
}
