package com.example.gpsplace.domain

/** UC-01 A4: 보호자 화면에서 마지막 수집 후 5분이 넘으면 갱신 지연 상태. */
object DelayPolicy {
    const val DELAY_MS = 5 * 60_000L

    fun isDelayed(lastPointT: Long, now: Long): Boolean = now - lastPointT > DELAY_MS

    /** “25분째”·“25분 전”에 쓰는 경과 분 */
    fun elapsedMinutes(lastPointT: Long, now: Long): Long = ((now - lastPointT).coerceAtLeast(0)) / 60_000L
}
