package com.example.gpsplace.domain

/** 기록 누락 구간: [from] 다음 점이 [to]이고 그 사이가 2분을 넘는다. */
data class Gap(val from: TrackPoint, val to: TrackPoint) {
    val startAt: Long get() = from.t
    val endAt: Long get() = to.t
}

/** 2분 넘게 점이 없는 구간을 찾는다. 경로는 이 구간을 실선으로 잇지 않고 점선 + “기록 누락”으로 그린다. */
object GapDetector {
    const val MAX_GAP_MS = StayDetector.MAX_GAP_MS

    fun gaps(points: List<TrackPoint>): List<Gap> =
        points.sortedBy { it.t }.zipWithNext().filter { (a, b) -> b.t - a.t > MAX_GAP_MS }.map { (a, b) -> Gap(a, b) }

    /** 누락 구간에서 끊은 연속 구간들. 각 구간은 실선 하나로 그린다. */
    fun segments(points: List<TrackPoint>): List<List<TrackPoint>> {
        val result = mutableListOf<MutableList<TrackPoint>>()
        for (point in points.sortedBy { it.t }) {
            val current = result.lastOrNull()
            if (current == null || point.t - current.last().t > MAX_GAP_MS) {
                result += mutableListOf(point)
            } else {
                current += point
            }
        }
        return result
    }
}
