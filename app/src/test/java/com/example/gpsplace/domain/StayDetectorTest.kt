package com.example.gpsplace.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StayDetectorTest {

    @Test
    fun `FR-03 50m 안에서 5분 이상 머물면 체류 1건`() {
        val stays = StayDetector.detect(pointsEvery(0, 300, meters = 10.0))
        assertEquals(1, stays.size)
        assertEquals(sec(0), stays[0].startAt)
        assertEquals(sec(300), stays[0].endAt)
        assertEquals(300L, stays[0].durationSec)
    }

    @Test
    fun `FR-03 4분 59초는 체류가 아니다`() {
        val points = pointsEvery(0, 270) + northOf(0.0, sec(299))
        assertTrue(StayDetector.detect(points).isEmpty())
    }

    @Test
    fun `FR-03 앵커에서 49m 떨어진 점은 같은 구간`() {
        val points = (0..360 step 30).map { s -> northOf(if (s % 60 == 0) 0.0 else 49.0, sec(s)) }
        val stays = StayDetector.detect(points)
        assertEquals(1, stays.size)
        assertEquals(sec(0), stays[0].startAt)
        assertEquals(sec(360), stays[0].endAt)
    }

    @Test
    fun `FR-03 앵커에서 51m 떨어진 점이 나오면 구간을 끊고 새 앵커로 시작`() {
        // 0~180초 앵커 근처(3분, 체류 아님) → 210초에 51m → 240~600초 다시 원래 자리(6분)
        val points = pointsEvery(0, 180) + northOf(51.0, sec(210)) + pointsEvery(240, 600)
        val stays = StayDetector.detect(points)
        assertEquals(1, stays.size)
        assertEquals(sec(240), stays[0].startAt)
        assertEquals(sec(600), stays[0].endAt)
    }

    @Test
    fun `FR-03 간격 2분 1초는 구간을 분리해 체류로 판정하지 않는다`() {
        // 0~180초(3분) + 2분 1초 공백 + 301~481초(3분) — 합치면 8분이지만 분리되어 체류 없음
        val points = pointsEvery(0, 180) + pointsEvery(301, 481)
        assertTrue(StayDetector.detect(points).isEmpty())
    }

    @Test
    fun `FR-03 간격이 정확히 2분이면 같은 구간`() {
        val points = pointsEvery(0, 180) + pointsEvery(300, 420)
        val stays = StayDetector.detect(points)
        assertEquals(1, stays.size)
        assertEquals(sec(420), stays[0].endAt)
    }

    @Test
    fun `FR-03 체류 중이면 같은 ID로 종료 시각만 갱신`() {
        val first = StayDetector.detect(pointsEvery(0, 360)).single()
        val later = StayDetector.detect(pointsEvery(0, 480)).single()
        assertEquals(first.id, later.id)
        assertEquals(sec(0), later.startAt)
        assertEquals(sec(480), later.endAt)
    }

    @Test
    fun `FR-03 대표 좌표는 구간 점들의 중심`() {
        val points = (0..360 step 30).map { s -> northOf(if ((s / 30) % 2 == 0) 0.0 else 20.0, sec(s)) }
        val stay = StayDetector.detect(points).single()
        val center = points.map { it.lat }.average()
        assertEquals(center, stay.lat, 1e-12)
        assertEquals(BASE_LNG, stay.lng, 1e-12)
    }

    @Test
    fun `FR-03 순서가 섞여 들어와도 시각 순으로 판정`() {
        val stays = StayDetector.detect(pointsEvery(0, 300).shuffled(java.util.Random(7)))
        assertEquals(1, stays.size)
    }

    @Test
    fun `FR-03 두 곳에서 각각 머물면 체류 2건`() {
        val home = pointsEvery(0, 360)
        val store = (390..900 step 30).map { northOf(500.0, sec(it)) }
        val stays = StayDetector.detect(home + store)
        assertEquals(2, stays.size)
        assertEquals(sec(390), stays[1].startAt)
    }
}
