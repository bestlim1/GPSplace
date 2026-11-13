package com.example.gpsplace.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** S-17 시간대별 전송 행 */
data class HourCount(val hourKey: String, val firstT: Long, val lastT: Long, val count: Int)

@Dao
interface PointDao {
    /** 같은 수집 시각이 이미 있으면 무시한다 (-1 반환). */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(point: PointEntity): Long

    @Query("SELECT * FROM points WHERE t >= :fromT ORDER BY t")
    suspend fun pointsSince(fromT: Long): List<PointEntity>

    @Query("SELECT * FROM points WHERE syncedAt IS NULL ORDER BY t LIMIT :limit")
    suspend fun oldestUnsynced(limit: Int): List<PointEntity>

    @Query("SELECT * FROM points WHERE syncedAt IS NULL ORDER BY t DESC LIMIT 1")
    suspend fun newestUnsynced(): PointEntity?

    @Query("UPDATE points SET syncedAt = :at WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<Long>, at: Long)

    @Query("SELECT COUNT(*) FROM points WHERE syncedAt IS NULL")
    suspend fun unsyncedCount(): Int

    @Query("SELECT COUNT(*) FROM points WHERE syncedAt IS NULL")
    fun observeUnsyncedCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM points")
    fun observeTotalCount(): Flow<Int>

    @Query("SELECT * FROM points ORDER BY t DESC LIMIT 1")
    fun observeLatest(): Flow<PointEntity?>

    @Query("SELECT * FROM points ORDER BY t DESC LIMIT 1")
    suspend fun latest(): PointEntity?

    @Query("SELECT COUNT(*) FROM points WHERE dayKey = :dayKey")
    fun observeDayCount(dayKey: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM points WHERE dayKey = :dayKey AND syncedAt IS NOT NULL")
    fun observeDaySyncedCount(dayKey: String): Flow<Int>

    @Query("SELECT MAX(syncedAt) FROM points")
    fun observeLastSyncedAt(): Flow<Long?>

    @Query(
        "SELECT hourKey, MIN(t) AS firstT, MAX(t) AS lastT, COUNT(*) AS count FROM points " +
            "WHERE dayKey = :dayKey AND syncedAt IS NOT NULL GROUP BY hourKey ORDER BY hourKey DESC"
    )
    fun observeSyncedByHour(dayKey: String): Flow<List<HourCount>>

    @Query(
        "SELECT hourKey, MIN(t) AS firstT, MAX(t) AS lastT, COUNT(*) AS count FROM points " +
            "WHERE syncedAt IS NULL GROUP BY hourKey ORDER BY hourKey DESC"
    )
    fun observeUnsyncedByHour(): Flow<List<HourCount>>

    /** 디버그 시험용: 그날 점을 다시 보낸다 (FR-06 “다시 보내도 서버 점 수 그대로” 확인) */
    @Query("UPDATE points SET syncedAt = NULL WHERE dayKey = :dayKey")
    suspend fun markDayUnsynced(dayKey: String)

    @Query("DELETE FROM points")
    suspend fun clear()
}

@Dao
interface StayDao {
    @Query("SELECT * FROM stays WHERE id IN (:ids)")
    suspend fun byIds(ids: List<String>): List<StayEntity>

    @Upsert
    suspend fun upsert(stays: List<StayEntity>)

    @Query("SELECT * FROM stays WHERE syncedAt IS NULL ORDER BY startAt")
    suspend fun unsynced(): List<StayEntity>

    @Query("UPDATE stays SET syncedAt = :at WHERE id = :id AND endAt = :endAt")
    suspend fun markSynced(id: String, endAt: Long, at: Long)

    @Query("DELETE FROM stays")
    suspend fun clear()
}

@Dao
interface AlertDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(alerts: List<AlertEntity>)

    @Query("SELECT * FROM alerts WHERE syncedAt IS NULL ORDER BY createdAt")
    suspend fun unsynced(): List<AlertEntity>

    @Query("UPDATE alerts SET syncedAt = :at WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<String>, at: Long)

    @Query("DELETE FROM alerts")
    suspend fun clear()
}

@Dao
interface ZoneDao {
    @Query("SELECT * FROM zones_cache")
    suspend fun all(): List<ZoneCacheEntity>

    @Upsert
    suspend fun upsert(zones: List<ZoneCacheEntity>)

    @Query("DELETE FROM zones_cache WHERE zoneId NOT IN (:keepIds)")
    suspend fun deleteOthers(keepIds: List<String>)

    @Query("DELETE FROM zones_cache")
    suspend fun clear()
}
