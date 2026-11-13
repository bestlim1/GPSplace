package com.example.gpsplace.domain

/** 체류 한 건. [id]는 구간 첫 점(앵커) 시각으로 만들어 체류가 이어지는 동안 바뀌지 않는다. */
data class Stay(
    val id: String,
    val startAt: Long,
    val endAt: Long,
    val lat: Double,
    val lng: Double,
) {
    val durationSec: Long get() = (endAt - startAt) / 1000
}

/**
 * FR-03 체류 판정 (분석서 수치 그대로).
 * - 앵커 = 구간 첫 점. 새 점이 앵커에서 50m 이내이고 직전 점과 간격 2분 이하이면 같은 후보 구간.
 * - 후보가 5분 이상 이어지면 체류. 대표 좌표 = 구간 점들의 중심, 시작 = 앵커 시각, 종료 = 마지막 점 시각.
 * - 간격 2분 초과 또는 앵커에서 50m 밖이면 구간을 끊고 그 점을 새 앵커로 시작한다.
 */
object StayDetector {
    const val RADIUS_M = 50.0
    const val MIN_DURATION_MS = 5 * 60_000L
    const val MAX_GAP_MS = 2 * 60_000L

    fun detect(points: List<TrackPoint>): List<Stay> {
        val stays = mutableListOf<Stay>()
        var segment = mutableListOf<TrackPoint>()

        fun closeSegment() {
            if (segment.isNotEmpty() && segment.last().t - segment.first().t >= MIN_DURATION_MS) {
                stays += Stay(
                    id = stayId(segment.first().t),
                    startAt = segment.first().t,
                    endAt = segment.last().t,
                    lat = segment.map { it.lat }.average(),
                    lng = segment.map { it.lng }.average(),
                )
            }
        }

        for (point in points.sortedBy { it.t }) {
            val anchor = segment.firstOrNull()
            val previous = segment.lastOrNull()
            val continues = anchor != null && previous != null &&
                point.t - previous.t <= MAX_GAP_MS &&
                Geo.distanceMeters(anchor, point) <= RADIUS_M
            if (continues) {
                segment += point
            } else {
                closeSegment()
                segment = mutableListOf(point)
            }
        }
        closeSegment()
        return stays
    }

    fun stayId(anchorT: Long) = "s$anchorT"
}
