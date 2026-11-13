package com.example.gpsplace.data.remote

import com.example.gpsplace.domain.AlertType
import com.example.gpsplace.domain.Stay
import com.example.gpsplace.domain.TrackPoint
import com.example.gpsplace.domain.Zone
import com.example.gpsplace.domain.ZoneType
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot

// Firestore 문서 ↔ 앱 모델. 필드 이름은 docs/ARCHITECTURE.md 데이터 모델과 같다.

data class RemoteLink(
    val id: String,
    val guardianUid: String,
    val protectedUid: String,
    val active: Boolean,
    val createdAt: Long?,
    val lastPoint: TrackPoint?,
    val pendingCount: Int,
    /** 보호대상자 기기의 위치 권한 */
    val locationGranted: Boolean,
    /** 보호대상자 기기의 위치 서비스(GPS) */
    val serviceOn: Boolean,
    val reportedAt: Long?,
)

data class RemoteAlert(
    val id: String,
    val zoneId: String,
    val zoneName: String,
    val type: AlertType,
    val p1: TrackPoint,
    val p2: TrackPoint,
    val createdAt: Long,
    /** 알림을 만들 때의 구역 중심·반경 (구역이 나중에 바뀌거나 지워져도 S-09에 그대로 보인다) */
    val zoneLat: Double?,
    val zoneLng: Double?,
    val radiusM: Double?,
)

fun Timestamp?.millis(): Long? = this?.toDate()?.time

@Suppress("UNCHECKED_CAST")
private fun Any?.asMap(): Map<String, Any?>? = this as? Map<String, Any?>

private fun Map<String, Any?>.num(key: String): Double? = (this[key] as? Number)?.toDouble()

fun Map<String, Any?>.toTrackPoint(): TrackPoint? {
    val lat = num("lat") ?: return null
    val lng = num("lng") ?: return null
    val t = (this["t"] as? Number)?.toLong() ?: return null
    return TrackPoint(lat, lng, t, (this["accuracy"] as? Number)?.toFloat())
}

fun TrackPoint.toMap(withAccuracy: Boolean = false): Map<String, Any> = buildMap {
    put("lat", lat)
    put("lng", lng)
    put("t", t)
    if (withAccuracy && accuracy != null) put("accuracy", accuracy.toDouble())
}

fun DocumentSnapshot.toRemoteLink(): RemoteLink? {
    if (!exists()) return null
    val permission = get("permission").asMap()
    return RemoteLink(
        id = id,
        guardianUid = getString("guardianUid") ?: return null,
        protectedUid = getString("protectedUid") ?: return null,
        active = getString("status") == "active",
        createdAt = getTimestamp("createdAt").millis(),
        lastPoint = get("lastPoint").asMap()?.toTrackPoint(),
        pendingCount = (getLong("pendingCount") ?: 0L).toInt(),
        locationGranted = permission?.get("location") != "denied",
        serviceOn = permission?.get("service") != "off",
        reportedAt = getTimestamp("reportedAt").millis(),
    )
}

/** pointHours 시간 문서 하나의 points 맵을 펼친다. */
fun DocumentSnapshot.hourPoints(): List<TrackPoint> =
    get("points").asMap()?.values?.mapNotNull { it.asMap()?.toTrackPoint() }.orEmpty()

fun DocumentSnapshot.toStay(): Stay? = Stay(
    id = id,
    startAt = getLong("startAt") ?: return null,
    endAt = getLong("endAt") ?: return null,
    lat = getDouble("lat") ?: return null,
    lng = getDouble("lng") ?: return null,
)

fun DocumentSnapshot.toZone(): Zone? = Zone(
    id = id,
    name = getString("name") ?: return null,
    type = ZoneType.fromKey(getString("type")),
    lat = getDouble("lat") ?: return null,
    lng = getDouble("lng") ?: return null,
    radiusM = getDouble("radiusM") ?: return null,
)

fun DocumentSnapshot.toRemoteAlert(): RemoteAlert? {
    val zone = get("zone").asMap()
    return RemoteAlert(
        id = id,
        zoneId = getString("zoneId") ?: return null,
        zoneName = getString("zoneName") ?: "",
        type = AlertType.fromKey(getString("type")),
        p1 = get("p1").asMap()?.toTrackPoint() ?: return null,
        p2 = get("p2").asMap()?.toTrackPoint() ?: return null,
        createdAt = getTimestamp("createdAt").millis() ?: return null,
        zoneLat = zone?.num("lat"),
        zoneLng = zone?.num("lng"),
        radiusM = zone?.num("radiusM"),
    )
}
