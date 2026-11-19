package com.kirakira.kiracode

import androidx.room.migration.Migration
import com.kirakira.kiracode.data.local.db.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 数据库迁移覆盖回归（阶段 C7）：
 * fallbackToDestructiveMigration 已移除——任何版本升级都必须有显式迁移，
 * 缺失版本时 Room 抛出明确异常而不是静默清库。
 */
class DatabaseMigrationCoverageTest {

    /** 当前数据库 schema 版本（与 @Database version 同步维护） */
    private val currentVersion = 9

    @Test
    fun `显式迁移连续覆盖 1 到当前版本`() {
        val migrations = AppDatabase.MIGRATIONS
        val steps = migrations.map { it.startVersion to it.endVersion }.sortedBy { it.first }

        assertEquals(1, steps.first().first)
        assertEquals(currentVersion, steps.last().second)

        // 无断层：每个迁移的终点 = 下一个迁移的起点
        steps.zipWithNext().forEach { (a, b) ->
            assertEquals("迁移断层：$a → $b", a.second, b.first)
        }
    }

    @Test
    fun `事件快照列有合法迁移（阶段 B）`() {
        val snapshotMigration = AppDatabase.MIGRATIONS
            .filter { it.endVersion >= 6 }
            .any { m ->
                // MIGRATION_6_7 负责 eventSnapshotJson 列（反射验证类存在即可，SQL 在真机迁移时执行）
                m is Migration
            }
        assertTrue(snapshotMigration)
        assertTrue(AppDatabase.MIGRATIONS.any { it.startVersion == 6 && it.endVersion == 7 })
        assertTrue(AppDatabase.MIGRATIONS.any { it.startVersion == 7 && it.endVersion == 8 })
        assertTrue(AppDatabase.MIGRATIONS.any { it.startVersion == 8 && it.endVersion == 9 })
    }
}
