package com.kirakira.kiracode.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.kirakira.kiracode.data.local.db.converter.Converters
import com.kirakira.kiracode.data.local.db.dao.CharacterCardDao
import com.kirakira.kiracode.data.local.db.dao.DraftDao
import com.kirakira.kiracode.data.local.db.dao.MessageDao
import com.kirakira.kiracode.data.local.db.dao.PreferenceDao
import com.kirakira.kiracode.data.local.db.dao.ReferenceCardDao
import com.kirakira.kiracode.data.local.db.dao.SessionDao
import com.kirakira.kiracode.data.local.db.entity.CharacterCardEntity
import com.kirakira.kiracode.data.local.db.entity.DraftEntity
import com.kirakira.kiracode.data.local.db.entity.MessageEntity
import com.kirakira.kiracode.data.local.db.entity.PreferenceDimensionEntity
import com.kirakira.kiracode.data.local.db.entity.PreferenceOptionEntity
import com.kirakira.kiracode.data.local.db.entity.ReferenceCardEntity
import com.kirakira.kiracode.data.local.db.entity.SessionEntity

/**
 * Room 数据库主类
 * 包含角色卡表、草稿表、会话表、消息表、参考卡表
 */
@Database(
    entities = [
        CharacterCardEntity::class,
        DraftEntity::class,
        SessionEntity::class,
        MessageEntity::class,
        ReferenceCardEntity::class,
        PreferenceDimensionEntity::class,
        PreferenceOptionEntity::class
    ],
    version = 9,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun characterCardDao(): CharacterCardDao
    abstract fun draftDao(): DraftDao
    abstract fun sessionDao(): SessionDao
    abstract fun messageDao(): MessageDao
    abstract fun referenceCardDao(): ReferenceCardDao
    abstract fun preferenceDao(): PreferenceDao

    companion object {
        @Suppress("UNUSED_PARAMETER")
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // 版本 1 -> 2 迁移：新增 sessions、messages、reference_cards 表
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // 创建 sessions 表
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS sessions (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        title TEXT NOT NULL,
                        summary TEXT NOT NULL DEFAULT '',
                        systemPrompt TEXT NOT NULL DEFAULT '',
                        model TEXT NOT NULL DEFAULT 'gpt-4o-mini',
                        temperature REAL NOT NULL DEFAULT 0.8,
                        maxTokens INTEGER NOT NULL DEFAULT 4096,
                        referenceCardId INTEGER,
                        uuid TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        messageCount INTEGER NOT NULL DEFAULT 0,
                        totalTokens INTEGER NOT NULL DEFAULT 0,
                        isArchived INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
                database.execSQL("CREATE INDEX IF NOT EXISTS idx_sessions_updatedAt ON sessions (updatedAt)")

                // 创建 messages 表
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS messages (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        sessionId INTEGER NOT NULL,
                        role TEXT NOT NULL,
                        content TEXT NOT NULL,
                        name TEXT,
                        toolCallsJson TEXT,
                        toolCallId TEXT,
                        tokens INTEGER NOT NULL DEFAULT 0,
                        createdAt INTEGER NOT NULL,
                        metadataJson TEXT NOT NULL DEFAULT '{}',
                        FOREIGN KEY (sessionId) REFERENCES sessions (id) ON DELETE CASCADE
                    )
                """.trimIndent())
                database.execSQL("CREATE INDEX IF NOT EXISTS idx_messages_session_created ON messages (sessionId, createdAt)")
                database.execSQL("CREATE INDEX IF NOT EXISTS idx_messages_session_role ON messages (sessionId, role)")

                // 创建 reference_cards 表
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS reference_cards (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        description TEXT NOT NULL,
                        personality TEXT NOT NULL,
                        firstMessage TEXT NOT NULL,
                        creator TEXT NOT NULL DEFAULT 'KiraCode',
                        exampleDialogue TEXT NOT NULL DEFAULT '',
                        scenario TEXT NOT NULL DEFAULT '',
                        depth INTEGER NOT NULL DEFAULT 0,
                        creatorNotes TEXT NOT NULL DEFAULT '',
                        systemPrompt TEXT NOT NULL DEFAULT '',
                        postHistoryInstructions TEXT NOT NULL DEFAULT '',
                        tagsJson TEXT NOT NULL DEFAULT '[]',
                        alternateGreetingsJson TEXT NOT NULL DEFAULT '[]',
                        extensionsJson TEXT NOT NULL DEFAULT '{}',
                        uuid TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        avatar TEXT,
                        spec TEXT NOT NULL DEFAULT 'chara_card_v2',
                        specVersion TEXT NOT NULL DEFAULT '2.0',
                        source TEXT NOT NULL DEFAULT 'import',
                        styleFeaturesJson TEXT NOT NULL DEFAULT '{}'
                    )
                """.trimIndent())
                database.execSQL("CREATE INDEX IF NOT EXISTS idx_reference_cards_name ON reference_cards (name)")
            }
        }

        // 版本 2 -> 3 迁移：V2.0 无损保真字段（世界书/正则/线格式原文）
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                val tables = listOf("character_cards", "drafts", "reference_cards")
                for (table in tables) {
                    database.execSQL("ALTER TABLE $table ADD COLUMN characterBookJson TEXT NOT NULL DEFAULT 'null'")
                    database.execSQL("ALTER TABLE $table ADD COLUMN regexScriptsJson TEXT NOT NULL DEFAULT '[]'")
                    database.execSQL("ALTER TABLE $table ADD COLUMN rawJson TEXT NOT NULL DEFAULT 'null'")
                }
            }
        }

        // 版本 3 -> 4 迁移：会话工作区持久化（工作卡/模式/参考卡选择）
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE sessions ADD COLUMN workingCardJson TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE sessions ADD COLUMN workMode TEXT NOT NULL DEFAULT 'AGENT'")
                database.execSQL("ALTER TABLE sessions ADD COLUMN selectedReferenceIdsJson TEXT NOT NULL DEFAULT '[]'")
            }
        }

        // 版本 4 -> 5 迁移：V3.0 会话置顶
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE sessions ADD COLUMN isPinned INTEGER NOT NULL DEFAULT 0")
            }
        }

        // 版本 5 -> 6 迁移：仓库/会话文件化后，清空历史遗留的超大字段列，
        // 避免 READ 时 CursorWindow「Row too big」崩溃（数据已改由文件存储）。
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                for (table in listOf("character_cards", "drafts", "reference_cards")) {
                    database.execSQL("UPDATE $table SET characterBookJson = 'null', regexScriptsJson = '[]', rawJson = 'null'")
                    database.execSQL("UPDATE $table SET description = substr(description, 1, 500), personality = substr(personality, 1, 500), firstMessage = substr(firstMessage, 1, 500), scenario = substr(scenario, 1, 500), creatorNotes = '', systemPrompt = '', postHistoryInstructions = '', exampleDialogue = ''")
                    database.execSQL("UPDATE $table SET extensionsJson = '{}', tagsJson = '[]', alternateGreetingsJson = '[]', avatar = NULL")
                }
                // 会话工作卡 JSON 同样可能超大
                database.execSQL("UPDATE sessions SET workingCardJson = ''")
            }
        }

        // 版本 6 -> 7 迁移：会话事件快照列（定稿事件/计划/任务，恢复事件流用）
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE sessions ADD COLUMN eventSnapshotJson TEXT NOT NULL DEFAULT '[]'")
            }
        }

        // 版本 7 -> 8 迁移：助手消息持久化思维链
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE messages ADD COLUMN thinking TEXT NOT NULL DEFAULT ''")
            }
        }

        // 版本 8 -> 9 迁移：会话偏好选择 + 全局偏好目录
        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE sessions ADD COLUMN selectedPreferenceIdsJson TEXT NOT NULL DEFAULT '[]'"
                )
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS preference_dimensions (
                        id TEXT PRIMARY KEY NOT NULL,
                        label TEXT NOT NULL,
                        isBuiltin INTEGER NOT NULL,
                        sortOrder INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS preference_options (
                        id TEXT PRIMARY KEY NOT NULL,
                        dimensionId TEXT NOT NULL,
                        label TEXT NOT NULL,
                        rule TEXT NOT NULL,
                        isBuiltin INTEGER NOT NULL,
                        sortOrder INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS idx_preference_options_dimensionId " +
                        "ON preference_options (dimensionId)"
                )
            }
        }

        // 全部显式迁移（1→9）；缺失版本将抛出明确异常，绝不静默清库
        internal val MIGRATIONS: Array<Migration> = arrayOf(
            MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7,
            MIGRATION_7_8, MIGRATION_8_9
        )

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "kiracode_database"
                )
                    .addMigrations(*MIGRATIONS)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}