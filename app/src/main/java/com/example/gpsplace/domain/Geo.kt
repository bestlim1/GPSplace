package com.example.gpsplace.domain

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** 수집된 위치 한 점. [t]는 수집 시각(epoch ms). */
data class TrackPoint(
    val lat: Double,
    val lng: Double,
    val t: Long,
    val accuracy: Float? = null,
)

object Geo {
    private const val EARTH_RADIUS_M = 6_371_008.8

    /** 두 좌표 사이 거리(m), 하버사인 공식 */
    fun distanceMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2).let { it * it } +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).let { it * it }
        return 2 * EARTH_RADIUS_M * asin(sqrt(a.coerceIn(0.0, 1.0)))
    }

    fun distanceMeters(a: TrackPoint, b: TrackPoint): Double = distanceMeters(a.lat, a.lng, b.lat, b.lng)
}
