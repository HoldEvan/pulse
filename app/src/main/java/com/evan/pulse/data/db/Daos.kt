package com.evan.pulse.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects ORDER BY id")
    suspend fun all(): List<ProjectEntity>

    @Query("SELECT * FROM projects ORDER BY id")
    fun allNow(): List<ProjectEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(items: List<ProjectEntity>)

    @Query("DELETE FROM projects")
    fun clear()
}

@Dao
interface EnvironmentDao {
    @Query("SELECT * FROM environments ORDER BY id")
    fun allNow(): List<EnvironmentEntity>

    @Query("SELECT * FROM environment_vars ORDER BY envId, position")
    fun allVarsNow(): List<EnvironmentVarEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(items: List<EnvironmentEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAllVars(items: List<EnvironmentVarEntity>)

    @Query("DELETE FROM environments")
    fun clear()

    @Query("DELETE FROM environment_vars")
    fun clearVars()
}

@Dao
interface CollectionDao {
    @Query("SELECT * FROM collections ORDER BY position, id")
    fun allNow(): List<CollectionEntity>

    @Query("SELECT * FROM saved_requests ORDER BY position, id")
    fun allRequestsNow(): List<SavedRequestEntity>

    @Query("SELECT * FROM collection_vars ORDER BY collectionId, position")
    fun allVarsNow(): List<CollectionVarEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(items: List<CollectionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAllRequests(items: List<SavedRequestEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAllVars(items: List<CollectionVarEntity>)

    @Query("DELETE FROM collections")
    fun clear()

    @Query("DELETE FROM saved_requests")
    fun clearRequests()

    @Query("DELETE FROM collection_vars")
    fun clearVars()
}

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history ORDER BY createdAt DESC, id DESC")
    fun allNow(): List<HistoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(items: List<HistoryEntity>)

    @Query("DELETE FROM history")
    fun clear()
}

@Dao
interface CookieDao {
    @Query("SELECT * FROM cookies ORDER BY projectId, domain, name")
    fun allNow(): List<CookieEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(items: List<CookieEntity>)

    @Query("DELETE FROM cookies")
    fun clear()
}

@Dao
interface MonitorDao {
    @Query("SELECT * FROM monitor_targets ORDER BY id")
    fun allTargetsNow(): List<MonitorTargetEntity>

    @Query("SELECT * FROM monitor_records ORDER BY checkedAt DESC, id DESC")
    fun allRecordsNow(): List<MonitorRecordEntity>

    @Query("SELECT * FROM monitor_history ORDER BY checkedAt DESC, id DESC")
    fun allHistoryNow(): List<MonitorHistoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAllTargets(items: List<MonitorTargetEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAllRecords(items: List<MonitorRecordEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAllHistory(items: List<MonitorHistoryEntity>)

    @Query("DELETE FROM monitor_targets")
    fun clearTargets()

    @Query("DELETE FROM monitor_records")
    fun clearRecords()

    @Query("DELETE FROM monitor_history")
    fun clearHistory()
}

@Dao
interface ProtocolDao {
    @Query("SELECT * FROM ws_sessions ORDER BY id")
    fun allWsNow(): List<WsSessionEntity>

    @Query("SELECT * FROM mqtt_subscriptions ORDER BY position")
    fun allMqttNow(): List<MqttSubscriptionEntity>

    @Query("SELECT * FROM mqtt_sessions ORDER BY id")
    fun allMqttSessionsNow(): List<MqttSessionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAllMqttSessions(items: List<MqttSessionEntity>)

    @Query("DELETE FROM mqtt_sessions")
    fun clearMqttSessions()

    @Query("SELECT * FROM sse_history ORDER BY id")
    fun allSseHistoryNow(): List<SseHistoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAllSseHistory(items: List<SseHistoryEntity>)

    @Query("DELETE FROM sse_history")
    fun clearSseHistory()

    @Query("SELECT * FROM ws_templates ORDER BY position, id")
    fun allTemplatesNow(): List<WsTemplateEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAllWs(items: List<WsSessionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAllMqtt(items: List<MqttSubscriptionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAllTemplates(items: List<WsTemplateEntity>)

    @Query("DELETE FROM ws_sessions")
    fun clearWs()

    @Query("DELETE FROM mqtt_subscriptions")
    fun clearMqtt()

    @Query("DELETE FROM ws_templates")
    fun clearTemplates()
}

@Dao
interface MetaDao {
    @Query("SELECT value FROM store_meta WHERE key = :key")
    fun valueNow(key: String): String?

    @Query("SELECT * FROM store_meta")
    fun allNow(): List<StoreMetaEntity>

    @Upsert
    fun upsert(item: StoreMetaEntity)

    @Upsert
    fun upsertAll(items: List<StoreMetaEntity>)
}
