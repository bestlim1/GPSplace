package com.example.gpsplace.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 모든 날짜 구분(dayKey, hourKey, “오늘”)은 Asia/Seoul 기준이다. */
object SeoulTime {
    val ZONE: ZoneId = ZoneId.of("Asia/Seoul")
    private val DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val HM = DateTimeFormatter.ofPattern("HH:mm")

    /** "YYYY-MM-DD" */
    fun dayKey(t: Long): String = Instant.ofEpochMilli(t).atZone(ZONE).format(DAY)

    /** "YYYY-MM-DD_HH" — Firestore pointHours 문서 ID */
    fun hourKey(t: Long): String {
        val z = Instant.ofEpochMilli(t).atZone(ZONE)
        return "${z.format(DAY)}_${z.hour.toString().padStart(2, '0')}"
    }

    fun hour(t: Long): Int = Instant.ofEpochMilli(t).atZone(ZONE).hour

    /** t가 속한 시간대(정시)의 시작 시각 */
    fun hourStart(t: Long): Long =
        Instant.ofEpochMilli(t).atZone(ZONE).withMinute(0).withSecond(0).withNano(0).toInstant().toEpochMilli()

    fun date(dayKey: String): LocalDate = LocalDate.parse(dayKey, DAY)

    fun dayKey(date: LocalDate): String = date.format(DAY)

    fun today(now: Long): LocalDate = Instant.ofEpochMilli(now).atZone(ZONE).toLocalDate()

    fun startOfDay(date: LocalDate): Long = date.atStartOfDay(ZONE).toInstant().toEpochMilli()

    /** "14:32" */
    fun hm(t: Long): String = Instant.ofEpochMilli(t).atZone(ZONE).format(HM)
}
