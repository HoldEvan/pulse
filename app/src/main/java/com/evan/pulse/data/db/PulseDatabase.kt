package com.evan.pulse.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 应用状态数据库。
 *
 * 版本策略：每次改表结构同时递增 [VERSION] 并补一条从上一版到新版的迁移，
 * 保证升级安装不丢用户数据；schema 由 KSP 导出到 `app/schemas` 便于比对。
 */
@Database(
    entities = [
        ProjectEntity::class,
        EnvironmentEntity::class,
        EnvironmentVarEntity::class,
        CollectionEntity::class,
        CollectionVarEntity::class,
        SavedRequestEntity::class,
        HistoryEntity::class,
        CookieEntity::class,
        MonitorTargetEntity::class,
        MonitorRecordEntity::class,
        MonitorHistoryEntity::class,
        WsSessionEntity::class,
        MqttSessionEntity::class,
        SseHistoryEntity::class,
        MqttSubscriptionEntity::class,
        WsTemplateEntity::class,
        StoreMetaEntity::class
    ],
    version = PulseDatabase.VERSION,
    exportSchema = true
)
abstract class PulseDatabase : RoomDatabase() {

    abstract fun projectDao(): ProjectDao
    abstract fun environmentDao(): EnvironmentDao
    abstract fun collectionDao(): CollectionDao
    abstract fun historyDao(): HistoryDao
    abstract fun cookieDao(): CookieDao
    abstract fun monitorDao(): MonitorDao
    abstract fun protocolDao(): ProtocolDao
    abstract fun metaDao(): MetaDao

    companion object {
        const val NAME = "pulse.db"

        /**
         * v1：从单文件 JSON 迁移而来，首次启动时导入旧数据；v2：历史记录增加断言快照；
         * v3：新增 MQTT 事件日志表与 SSE 会话历史表
         */
        const val VERSION = 3

        /** v1 → v2：历史记录补一列断言快照，旧记录默认为空 */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `history` ADD COLUMN `assertions` TEXT NOT NULL DEFAULT ''")
            }
        }

        /** v2 → v3：新增 MQTT 事件日志表与 SSE 会话历史表，与 Room 导出的 schema 保持一致 */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `mqtt_sessions` (" +
                        "`id` INTEGER NOT NULL, `broker` TEXT NOT NULL, `text` TEXT NOT NULL, " +
                        "`isSystem` INTEGER NOT NULL, `timestamp` INTEGER NOT NULL, PRIMARY KEY(`id`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `sse_history` (" +
                        "`id` INTEGER NOT NULL, `method` TEXT NOT NULL, `url` TEXT NOT NULL, " +
                        "`startedAt` INTEGER NOT NULL, `endedAt` INTEGER NOT NULL, `frames` INTEGER NOT NULL, " +
                        "`outcome` TEXT NOT NULL, `transcript` TEXT NOT NULL, PRIMARY KEY(`id`))"
                )
            }
        }

        fun build(context: Context): PulseDatabase =
            Room.databaseBuilder(context, PulseDatabase::class.java, NAME)
                // 现有写入路径都在调用方线程同步执行（StateFlow 更新后立即落盘），
                // 关闭主线程限制以保持行为一致；后续可改为单一写协程队列。
                .allowMainThreadQueries()
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
    }
}
