package com.example.gpsplace.domain

enum class ZoneType(val key: String) {
    Safe("safe"),
    Caution("caution");

    companion object {
        fun fromKey(key: String?): ZoneType = entries.firstOrNull { it.key == key } ?: Safe
    }
}

enum class AlertType(val key: String) {
    SafeExit("safe_exit"),
    CautionEnter("caution_enter");

    companion object {
        fun fromKey(key: String?): AlertType = entries.firstOrNull { it.key == key } ?: SafeExit
    }
}

data class Zone(
    val id: String,
    val name: String,
    val type: ZoneType,
    val lat: Double,
    val lng: Double,
    val radiusM: Double,
)

/**
 * 구역 하나에 대한 판정 상태.
 * [confirmedInside]: 연속 2점으로 확정된 상태(null = 아직 모름). [lastInside]·[lastPoint]: 직전 점.
 */
data class ZoneState(
    val confirmedInside: Boolean? = null,
    val lastInside: Boolean? = null,
    val lastPoint: TrackPoint? = null,
)

/** [zone]은 판정 당시의 구역 (나중에 구역을 고치거나 지워도 알림 상세에 그때 모양이 남는다). */
data class ZoneAlert(
    val zone: Zone,
    val type: AlertType,
    val p1: TrackPoint,
    val p2: TrackPoint,
) {
    val zoneId: String get() = zone.id
    val zoneName: String get() = zone.name

    /** 같은 알림을 다시 올려도 한 건이 되도록 구역 ID와 판정 위치 시각으로 만든다. */
    val id: String get() = "${zoneId}_${p2.t}"
}

/**
 * FR-04 구역 판정.
 * - 연속 2점이 모두 구역 안이면 inside, 모두 밖이면 outside로 확정한다.
 * - 안전 구역 inside→outside, 주의 구역 outside→inside로 확정 상태가 바뀔 때만 알림 1건.
 * - 처음 두 점은 상태만 정하고 알림을 만들지 않는다.
 * - 구역을 수정하면 다음 점부터 새 중심·반경으로 판정한다(상태는 유지). 삭제한 구역은 호출하지 않는다.
 */
object ZoneDetector {
    fun step(zone: Zone, state: ZoneState, point: TrackPoint): Pair<ZoneState, ZoneAlert?> {
        val inside = Geo.distanceMeters(zone.lat, zone.lng, point.lat, point.lng) <= zone.radiusM
        var confirmed = state.confirmedInside
        var alert: ZoneAlert? = null
        val previous = state.lastPoint
        if (state.lastInside != null && state.lastInside == inside && previous != null) {
            if (confirmed != null && confirmed != inside) {
                val type = when {
                    zone.type == ZoneType.Safe && !inside -> AlertType.SafeExit
                    zone.type == ZoneType.Caution && inside -> AlertType.CautionEnter
                    else -> null
                }
                if (type != null) alert = ZoneAlert(zone, type, p1 = previous, p2 = point)
            }
            confirmed = inside
        }
        return ZoneState(confirmedInside = confirmed, lastInside = inside, lastPoint = point) to alert
    }

    /** 지금 등록된 구역 전부에 새 점을 적용한다. 삭제된 구역의 상태는 버려서 알림이 생기지 않는다. */
    fun stepAll(
        zones: List<Zone>,
        states: Map<String, ZoneState>,
        point: TrackPoint,
    ): Pair<Map<String, ZoneState>, List<ZoneAlert>> {
        val nextStates = mutableMapOf<String, ZoneState>()
        val alerts = mutableListOf<ZoneAlert>()
        for (zone in zones) {
            val (state, alert) = step(zone, states[zone.id] ?: ZoneState(), point)
            nextStates[zone.id] = state
            if (alert != null) alerts += alert
        }
        return nextStates to alerts
    }
}
