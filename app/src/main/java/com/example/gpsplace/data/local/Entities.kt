package com.example.gpsplace.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.gpsplace.domain.AlertType
import com.example.gpsplace.domain.Stay
import com.example.gpsplace.domain.TrackPoint
import com.example.gpsplace.domain.Zone
import com.example.gpsplace.domain.ZoneAlert
import com.example.gpsplace.domain.ZoneState
import com.example.gpsplace.domain.ZoneType

/** 보호대상자 기기의 위치 점. 점 하나가 행 하나, [syncedAt] == null이면 미전송. 같은 수집 시각은 한 번만 저장한다. */
@Entity(
    tableName = "points",
    indices = [Index(value = ["t"], unique = true), Index("syncedAt"), Index("dayKey")],
)
data class PointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val lat: Double,
    val lng: Double,
    val accuracy: Float,
    val t: Long,
    val dayKey: String,
    val hourKey: String,
    val syncedAt: Long? = null,
) {
    fun toTrackPoint() = TrackPoint(lat, lng, t, accuracy)
}

/** 체류. 종료 시각이 갱신되면 [syncedAt]을 비워 다시 올린다. */
@Entity(tableName = "stays", indices = [Index("dayKey"), Index("syncedAt")])
data class StayEntity(
    @PrimaryKey val id: String,
    val startAt: Long,
    val endAt: Long,
    val lat: Double,
    val lng: Double,
    val dayKey: String,
    val durationSec: Long,
    val syncedAt: Long? = null,
) {
    companion object {
        fun from(stay: Stay, dayKey: String) = StayEntity(
            id = stay.id,
            startAt = stay.startAt,
            endAt = stay.endAt,
            lat = stay.lat,
            lng = stay.lng,
            dayKey = dayKey,
            durationSec = stay.durationSec,
        )
    }
}

/** 구역 알림. ID = "{zoneId}_{p2.t}"라 다시 올려도 한 건이다. */
@Entity(tableName = "alerts", indices = [Index("syncedAt")])
data class AlertEntity(
    @PrimaryKey val id: String,
    val zoneId: String,
    val zoneName: String,
    val type: String,
    val p1Lat: Double,
    val p1Lng: Double,
    val p1T: Long,
    val p2Lat: Double,
    val p2Lng: Double,
    val p2T: Long,
    val zoneLat: Double,
    val zoneLng: Double,
    val radiusM: Double,
    val createdAt: Long,
    val syncedAt: Long? = null,
) {
    companion object {
        fun from(alert: ZoneAlert, createdAt: Long) = AlertEntity(
            id = alert.id,
            zoneId = alert.zoneId,
            zoneName = alert.zoneName,
            type = alert.type.key,
            p1Lat = alert.p1.lat,
            p1Lng = alert.p1.lng,
            p1T = alert.p1.t,
            p2Lat = alert.p2.lat,
            p2Lng = alert.p2.lng,
            p2T = alert.p2.t,
            zoneLat = alert.zone.lat,
            zoneLng = alert.zone.lng,
            radiusM = alert.zone.radiusM,
            createdAt = createdAt,
        )
    }

    val alertType: AlertType get() = AlertType.fromKey(type)
}

/** 보호자가 등록한 구역의 사본 + 이 기기의 판정 상태. 오프라인에서도 마지막 위치로 판정한다 (NFR-03). */
@Entity(tableName = "zones_cache")
data class ZoneCacheEntity(
    @PrimaryKey val zoneId: String,
    val name: String,
    val type: String,
    val lat: Double,
    val lng: Double,
    val radiusM: Double,
    val updatedAt: Long,
    val confirmedInside: Boolean? = null,
    val lastInside: Boolean? = null,
    val lastLat: Double? = null,
    val lastLng: Double? = null,
    val lastT: Long? = null,
) {
    fun toZone() = Zone(zoneId, name, ZoneType.fromKey(type), lat, lng, radiusM)

    fun toState() = ZoneState(
        confirmedInside = confirmedInside,
        lastInside = lastInside,
        lastPoint = if (lastLat != null && lastLng != null && lastT != null) TrackPoint(lastLat, lastLng, lastT) else null,
    )

    fun withState(state: ZoneState) = copy(
        confirmedInside = state.confirmedInside,
        lastInside = state.lastInside,
        lastLat = state.lastPoint?.lat,
        lastLng = state.lastPoint?.lng,
        lastT = state.lastPoint?.t,
    )
}
