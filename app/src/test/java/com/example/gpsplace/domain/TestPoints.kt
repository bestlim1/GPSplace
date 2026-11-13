package com.example.gpsplace.domain

// 테스트용 좌표: 서울시청 기준으로 북쪽으로 몇 m 떨어진 점을 만든다.
// 위도만 바꾸면 하버사인 거리 = 반지름 × 위도 차(라디안)라서 경계값(49m·51m)을 정확히 만들 수 있다.
const val BASE_LAT = 37.5663
const val BASE_LNG = 126.9779
private const val EARTH_RADIUS_M = 6_371_008.8

fun northOf(meters: Double, t: Long): TrackPoint =
    TrackPoint(lat = BASE_LAT + Math.toDegrees(meters / EARTH_RADIUS_M), lng = BASE_LNG, t = t)

fun sec(s: Int): Long = s * 1000L

/** [fromSec]부터 [toSec]까지 [stepSec]마다 같은 거리의 점 */
fun pointsEvery(fromSec: Int, toSec: Int, stepSec: Int = 30, meters: Double = 0.0): List<TrackPoint> =
    (fromSec..toSec step stepSec).map { northOf(meters, sec(it)) }
