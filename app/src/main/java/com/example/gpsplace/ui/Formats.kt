package com.example.gpsplace.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.gpsplace.R
import com.example.gpsplace.domain.SeoulTime
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** 화면에 보이는 날짜·시간·거리 표기 (Asia/Seoul) */
object Formats {
    private val DAY = DateTimeFormatter.ofPattern("M월 d일 (E)", Locale.KOREAN)
    private val DAY_SHORT = DateTimeFormatter.ofPattern("M월 d일", Locale.KOREAN)
    private val FULL_DAY = DateTimeFormatter.ofPattern("yyyy년 M월 d일", Locale.KOREAN)
    private val DAY_TIME = DateTimeFormatter.ofPattern("M월 d일 HH:mm", Locale.KOREAN)

    /** “10월 29일 (목)” */
    fun day(date: LocalDate): String = date.format(DAY)

    /** “10월 25일” */
    fun dayShort(date: LocalDate): String = date.format(DAY_SHORT)

    /** “2026년 10월 29일” */
    fun fullDay(t: Long): String = Instant.ofEpochMilli(t).atZone(SeoulTime.ZONE).format(FULL_DAY)

    /** “14:32” */
    fun hm(t: Long): String = SeoulTime.hm(t)

    /** 오늘이면 “14:32”, 아니면 “10월 28일 14:32” */
    fun timeOrDayTime(t: Long, now: Long): String =
        if (SeoulTime.dayKey(t) == SeoulTime.dayKey(now)) hm(t)
        else Instant.ofEpochMilli(t).atZone(SeoulTime.ZONE).format(DAY_TIME)

    /** 위도·경도 소수 다섯째 자리(약 1m) */
    fun coordinate(lat: Double, lng: Double): String = String.format(Locale.US, "%.5f, %.5f", lat, lng)
}

@Composable
fun durationText(seconds: Long): String {
    val minutes = (seconds / 60).toInt()
    return if (minutes < 60) stringResource(R.string.duration_minutes, minutes)
    else stringResource(R.string.duration_hours_minutes, minutes / 60, minutes % 60)
}

@Composable
fun radiusText(meters: Double): String =
    if (meters < 1000) stringResource(R.string.radius_m, meters.toInt())
    else stringResource(R.string.radius_km, String.format(Locale.US, "%.1f", meters / 1000).removeSuffix(".0"))

@Composable
fun relativeText(t: Long, now: Long): String {
    val minutes = ((now - t).coerceAtLeast(0) / 60_000L).toInt()
    return if (minutes < 1) stringResource(R.string.just_now) else stringResource(R.string.minutes_ago, minutes)
}

/** “오늘”·“어제”·“10월 27일 (화)” */
@Composable
fun dayGroupText(date: LocalDate, today: LocalDate): String = when (date) {
    today -> stringResource(R.string.today)
    today.minusDays(1) -> stringResource(R.string.yesterday)
    else -> Formats.day(date)
}
