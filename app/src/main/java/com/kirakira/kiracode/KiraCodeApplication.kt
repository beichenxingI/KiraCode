package com.kirakira.kiracode

import android.app.Application
import android.util.Log
import dagger.hilt.android.HiltAndroidApp
import java.io.File

/**
 * KiraCard Application 类
 * Hilt 依赖注入入口，标记 @HiltAndroidApp 启用 Hilt 代码生成
 */
@HiltAndroidApp
class KiraCodeApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        installCrashLogger()
    }

    /**
     * 崩溃兜底日志：所有未捕获异常写入 files/logs/crash_log.txt。
     * logcat 不可用（"完全没有输出"）时仍可从设备取回完整堆栈定位崩溃点；
     * 记录后交回系统默认 handler，保持崩溃对话框等系统行为不变。
     */
    private fun installCrashLogger() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val dir = File(filesDir, "logs").apply { mkdirs() }
                File(dir, "crash_log.txt").appendText(
                    buildString {
                        appendLine("=== ${System.currentTimeMillis()} thread=${thread.name} ===")
                        appendLine(Log.getStackTraceString(throwable))
                    }
                )
            }
            previous?.uncaughtException(thread, throwable)
        }
    }
}
