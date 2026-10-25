package com.evan.pulse.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * 应用状态数据库。
 *
 * 版本策略：每次改表结构同时递增 [VERSION] 并在 [MIGRATIONS] 补一条迁移，
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

        /** v1：从单文件 JSON 迁移而来，首次启动时导入旧数据 */
        const val VERSION = 1

        fun build(context: Context): PulseDatabase =
            Room.databaseBuilder(context, PulseDatabase::class.java, NAME)
                // 现有写入路径都在调用方线程同步执行（StateFlow 更新后立即落盘），
                // 关闭主线程限制以保持行为一致；后续可改为单一写协程队列。
                .allowMainThreadQueries()
                .build()
    }
}
