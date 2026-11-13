package com.example.gpsplace.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ZoneDetectorTest {
    private val home = Zone("home", "집", ZoneType.Safe, BASE_LAT, BASE_LNG, radiusM = 200.0)
    private val road = Zone("road", "큰길", ZoneType.Caution, BASE_LAT, BASE_LNG, radiusM = 100.0)

    /** 거리(m) 목록을 30초 간격 점으로 바꿔 차례로 넣고 생긴 알림을 돌려준다. */
    private fun run(zone: Zone, metersSeq: List<Double>, start: ZoneState = ZoneState()): Pair<ZoneState, List<ZoneAlert>> {
        var state = start
        val alerts = mutableListOf<ZoneAlert>()
        metersSeq.forEachIndexed { i, m ->
            val (next, alert) = ZoneDetector.step(zone, state, northOf(m, sec(i * 30)))
            state = next
            if (alert != null) alerts += alert
        }
        return state to alerts
    }

    @Test
    fun `FR-04 안전 구역 연속 2점이 밖이면 이탈 알림 1건`() {
        val (_, alerts) = run(home, listOf(10.0, 20.0, 300.0, 310.0))
        assertEquals(1, alerts.size)
        val alert = alerts.single()
        assertEquals(AlertType.SafeExit, alert.type)
        assertEquals(sec(60), alert.p1.t)
        assertEquals(sec(90), alert.p2.t)
        assertEquals("home_${sec(90)}", alert.id)
    }

    @Test
    fun `FR-04 1점만 구역 밖이면 알림 없음`() {
        val (state, alerts) = run(home, listOf(10.0, 20.0, 300.0, 20.0, 10.0))
        assertTrue(alerts.isEmpty())
        assertEquals(true, state.confirmedInside)
    }

    @Test
    fun `FR-04 처음 두 점은 상태만 정하고 알림 없음`() {
        val (safeState, safeAlerts) = run(home, listOf(300.0, 310.0))
        assertTrue(safeAlerts.isEmpty())
        assertEquals(false, safeState.confirmedInside)

        val (cautionState, cautionAlerts) = run(road, listOf(10.0, 20.0))
        assertTrue(cautionAlerts.isEmpty())
        assertEquals(true, cautionState.confirmedInside)
    }

    @Test
    fun `FR-04 같은 이탈이 이어져도 알림은 1건`() {
        val (_, alerts) = run(home, listOf(10.0, 10.0, 300.0, 300.0, 400.0, 500.0, 600.0))
        assertEquals(1, alerts.size)
    }

    @Test
    fun `FR-04 주의 구역 연속 2점이 안이면 진입 알림 1건`() {
        val (_, alerts) = run(road, listOf(300.0, 250.0, 50.0, 40.0))
        assertEquals(AlertType.CautionEnter, alerts.single().type)
    }

    @Test
    fun `FR-04 안전 구역 진입과 주의 구역 이탈은 알림을 만들지 않는다`() {
        assertTrue(run(home, listOf(300.0, 300.0, 10.0, 10.0)).second.isEmpty())
        assertTrue(run(road, listOf(10.0, 10.0, 300.0, 300.0)).second.isEmpty())
    }

    @Test
    fun `FR-04 다시 들어왔다 나가면 새 이탈 알림`() {
        val (_, alerts) = run(home, listOf(10.0, 10.0, 300.0, 300.0, 10.0, 10.0, 300.0, 300.0))
        assertEquals(2, alerts.size)
    }

    @Test
    fun `FR-04 구역을 수정하면 다음 점부터 새 반경으로 판정`() {
        // 반경 200m에서 150m 지점은 안 → 반경을 100m로 줄이면 같은 자리가 밖 → 이탈 1건
        val (state, before) = run(home, listOf(150.0, 150.0))
        assertTrue(before.isEmpty())
        val edited = home.copy(radiusM = 100.0)
        val (_, after) = run(edited, listOf(150.0, 150.0), start = state)
        assertEquals(AlertType.SafeExit, after.single().type)
    }

    @Test
    fun `FR-04 삭제한 구역은 알림 0건`() {
        var states = mapOf<String, ZoneState>()
        val alerts = mutableListOf<ZoneAlert>()
        listOf(10.0, 10.0).forEachIndexed { i, m ->
            val (s, a) = ZoneDetector.stepAll(listOf(home), states, northOf(m, sec(i * 30)))
            states = s; alerts += a
        }
        // 구역 삭제 뒤에는 목록에 없음
        listOf(300.0, 300.0, 400.0).forEachIndexed { i, m ->
            val (s, a) = ZoneDetector.stepAll(emptyList(), states, northOf(m, sec(60 + i * 30)))
            states = s; alerts += a
        }
        assertTrue(alerts.isEmpty())
        assertNull(states["home"])
    }

    @Test
    fun `FR-04 반경 경계 - 반경 안쪽은 안, 바깥쪽은 밖`() {
        assertEquals(true, run(home, listOf(199.99, 199.99)).first.confirmedInside)
        assertEquals(false, run(home, listOf(200.01, 200.01)).first.confirmedInside)
    }
}
