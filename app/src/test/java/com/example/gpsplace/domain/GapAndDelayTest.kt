package com.example.gpsplace.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class GapAndDelayTest {

    @Test
    fun `FR-05 2분 넘는 간격은 기록 누락 구간`() {
        // 120초 → 241초(2분 1초 공백) 한 곳만 누락. 361초 → 420초는 59초라 이어진다.
        val points = pointsEvery(0, 120) + pointsEvery(241, 361) + pointsEvery(420, 480)
        val gaps = GapDetector.gaps(points)
        assertEquals(1, gaps.size)
        assertEquals(sec(120), gaps[0].startAt)
        assertEquals(sec(241), gaps[0].endAt)
    }

    @Test
    fun `FR-05 누락 구간에서 경로를 끊어 실선 구간을 나눈다`() {
        val segments = GapDetector.segments(pointsEvery(0, 120) + pointsEvery(300, 360))
        assertEquals(2, segments.size)
        assertEquals(5, segments[0].size)
        assertEquals(3, segments[1].size)
    }

    @Test
    fun `UC-01 A4 마지막 수집 후 5분을 넘으면 갱신 지연`() {
        val last = 1_000_000L
        assertFalse(DelayPolicy.isDelayed(last, last + 5 * 60_000L))
        assertTrue(DelayPolicy.isDelayed(last, last + 5 * 60_000L + 1))
        assertEquals(25L, DelayPolicy.elapsedMinutes(last, last + 25 * 60_000L + 59_000L))
    }

    @Test
    fun `날짜 구분은 Asia Seoul 기준`() {
        // 2026-10-29T15:30:00Z = 서울 10월 30일 00:30
        val t = java.time.Instant.parse("2026-10-29T15:30:00Z").toEpochMilli()
        assertEquals("2026-10-30", SeoulTime.dayKey(t))
        assertEquals("2026-10-30_00", SeoulTime.hourKey(t))
        assertEquals(0, SeoulTime.hour(t))
        assertEquals(java.time.Instant.parse("2026-10-29T15:00:00Z").toEpochMilli(), SeoulTime.hourStart(t))
        assertEquals(t - 30 * 60_000L, SeoulTime.startOfDay(LocalDate.of(2026, 10, 30)))
    }
}
