package com.example.gpsplace.debug

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.example.gpsplace.R
import com.example.gpsplace.data.local.HourCount
import com.example.gpsplace.data.local.Role
import com.example.gpsplace.data.remote.RemoteAlert
import com.example.gpsplace.data.remote.RemoteLink
import com.example.gpsplace.domain.SeoulTime
import com.example.gpsplace.domain.StayDetector
import com.example.gpsplace.domain.TrackPoint
import com.example.gpsplace.domain.Zone
import com.example.gpsplace.domain.ZoneDetector
import com.example.gpsplace.domain.ZoneState
import com.example.gpsplace.domain.ZoneType
import com.example.gpsplace.nav.GuardianBottomBar
import com.example.gpsplace.nav.GuardianTab
import com.example.gpsplace.nav.HistoryTab
import com.example.gpsplace.ui.ScreenState
import com.example.gpsplace.ui.components.RecordStatus
import com.example.gpsplace.ui.screens.history.DayData
import com.example.gpsplace.ui.screens.history.DayError
import com.example.gpsplace.ui.screens.history.DayPartial
import com.example.gpsplace.ui.screens.history.HistoryBody
import com.example.gpsplace.ui.screens.s01_role.RoleScreen
import com.example.gpsplace.ui.screens.s02_link.LinkRequestBody
import com.example.gpsplace.ui.screens.s02_link.LinkRequestUi
import com.example.gpsplace.ui.screens.s03_home.GuardianHomeBody
import com.example.gpsplace.ui.screens.s03_home.HomeData
import com.example.gpsplace.ui.screens.s03_home.HomeEmpty
import com.example.gpsplace.ui.screens.s03_home.HomePartial
import com.example.gpsplace.ui.screens.s06_stay_detail.StayDetail
import com.example.gpsplace.ui.screens.s06_stay_detail.StayDetailBody
import com.example.gpsplace.ui.screens.s08_alerts.AlertsBody
import com.example.gpsplace.ui.screens.s08_alerts.AlertsEmpty
import com.example.gpsplace.ui.screens.s09_alert_detail.AlertDetailBody
import com.example.gpsplace.ui.screens.s10_zones.ZonesBody
import com.example.gpsplace.ui.screens.s11_zone_edit.DeleteZoneDialog
import com.example.gpsplace.ui.screens.s11_zone_edit.ZoneEditBody
import com.example.gpsplace.ui.screens.s11_zone_edit.ZoneForm
import com.example.gpsplace.ui.screens.s12_link_manage.LinkManageBody
import com.example.gpsplace.ui.screens.s12_link_manage.LinkManageUi
import com.example.gpsplace.ui.screens.s13_code.CodeEntryBody
import com.example.gpsplace.ui.screens.s13_code.CodeError
import com.example.gpsplace.ui.screens.s14_consent.ConsentBody
import com.example.gpsplace.ui.screens.s14_consent.ConsentError
import com.example.gpsplace.ui.screens.s15_permission.PermissionScreen
import com.example.gpsplace.ui.screens.s16_protected_home.ProtectedHomeBody
import com.example.gpsplace.ui.screens.s16_protected_home.RecordingData
import com.example.gpsplace.ui.screens.s16_protected_home.RecordingError
import com.example.gpsplace.ui.screens.s16_protected_home.RecordingErrorKind
import com.example.gpsplace.ui.screens.s16_protected_home.RecordingPartial
import com.example.gpsplace.ui.screens.s17_sync.SyncBody
import com.example.gpsplace.ui.screens.s17_sync.SyncUi
import com.example.gpsplace.ui.components.ConfirmDialog
import com.example.gpsplace.ui.theme.AppColors
import com.google.android.gms.maps.model.LatLng
import java.time.LocalDate
import kotlin.math.sin

// 디버그 전용 “화면 상태 갤러리”: UI 설계서 4장용으로 실제 화면 본체를 예시 데이터로 상태별로 띄운다.
// adb shell am start -S -n com.example.gpsplace/.MainActivity --es gallery S03_default
// 예시 하루: 집에서 출발 → 시장 → 공원. 체류·구역 알림은 앱의 판정 로직(StayDetector·ZoneDetector)으로 계산한다.

const val GALLERY_ROUTE = "debug/gallery"
internal var galleryId: String? = null

private val noop: () -> Unit = {}

private object Sample {
    val today: LocalDate = SeoulTime.today(System.currentTimeMillis())
    val yesterday: LocalDate = today.minusDays(1)

    fun at(h: Int, m: Int, s: Int = 0, date: LocalDate = today): Long =
        date.atTime(h, m, s).atZone(SeoulTime.ZONE).toInstant().toEpochMilli()

    val HOME = LatLng(37.55650, 126.90200)
    private val CORNER = LatLng(37.55520, 126.90330)
    val MARKET = LatLng(37.55590, 126.90610)
    private val CENTER = LatLng(37.55440, 126.90520)
    private val ROAD = LatLng(37.55380, 126.90120)
    val PARK = LatLng(37.55250, 126.89700)
    private val BACK = LatLng(37.55430, 126.89940)
    private val P1 = LatLng(37.54950, 126.89950)
    private val P2 = LatLng(37.55150, 126.90350)
    private val P3 = LatLng(37.55450, 126.90450)

    private fun point(p: LatLng, t: Long) = TrackPoint(p.latitude, p.longitude, t, 6f)

    /** (시각, 위치, 다음 구간 기록 누락?)을 30초마다 잇는다. 같은 위치 두 개 사이는 머무는 구간 */
    private fun track(vararg legs: Triple<Long, LatLng, Boolean>): List<TrackPoint> {
        val points = mutableListOf<TrackPoint>()
        for (i in 0 until legs.size - 1) {
            val (t0, a, missingAfter) = legs[i]
            val (t1, b, _) = legs[i + 1]
            if (missingAfter) {
                points += point(a, t0)
                continue
            }
            var t = t0
            while (t < t1) {
                val f = (t - t0).toDouble() / (t1 - t0)
                val jitter = sin(t / 37_000.0) * 0.00002
                points += TrackPoint(a.latitude + (b.latitude - a.latitude) * f + jitter, a.longitude + (b.longitude - a.longitude) * f - jitter, t, 6f)
                t += 30_000
            }
        }
        val last = legs.last()
        points += point(last.second, last.first)
        return points.sortedBy { it.t }.distinctBy { it.t }
    }

    private fun leg(h: Int, m: Int, p: LatLng, date: LocalDate, missingAfter: Boolean = false) = Triple(at(h, m, 0, date), p, missingAfter)

    /**
     * 오늘: 집(08:10~08:41) → 시장(08:48~09:35) → 복지관(09:38~11:05, 11:05~11:40 기록 누락, 11:40~12:30)
     * → 큰길(주의 구역) → 공원(12:41~14:05) → 14:32 귀가 중. 걷는 구간은 분당 약 60m.
     */
    val todayPoints: List<TrackPoint> = track(
        leg(8, 10, HOME, today), leg(8, 41, HOME, today), leg(8, 44, CORNER, today), leg(8, 48, MARKET, today),
        leg(9, 35, MARKET, today), leg(9, 38, CENTER, today), leg(11, 5, CENTER, today, missingAfter = true),
        leg(11, 40, CENTER, today), leg(12, 30, CENTER, today), leg(12, 35, ROAD, today), leg(12, 41, PARK, today),
        leg(14, 5, PARK, today), leg(14, 12, P1, today), leg(14, 19, P2, today), leg(14, 25, P3, today), leg(14, 32, BACK, today),
    )

    /** 어제: 08:10~18:05, 11:05~11:40 기록 누락 1구간 (그사이 복지관에서 큰길로 이동) */
    val yesterdayPoints: List<TrackPoint> = track(
        leg(8, 10, HOME, yesterday), leg(8, 45, HOME, yesterday), leg(8, 48, CORNER, yesterday), leg(8, 52, MARKET, yesterday),
        leg(10, 15, MARKET, yesterday), leg(10, 18, CENTER, yesterday), leg(11, 5, CENTER, yesterday, missingAfter = true),
        leg(11, 40, ROAD, yesterday), leg(11, 46, PARK, yesterday), leg(13, 10, PARK, yesterday), leg(13, 17, P1, yesterday),
        leg(13, 24, P2, yesterday), leg(13, 30, P3, yesterday), leg(13, 36, BACK, yesterday), leg(13, 41, HOME, yesterday),
        leg(18, 5, HOME, yesterday),
    )

    val todayStays = StayDetector.detect(todayPoints)
    val yesterdayStays = StayDetector.detect(yesterdayPoints)

    val zones = listOf(
        Zone("z1", "집", ZoneType.Safe, HOME.latitude, HOME.longitude, 200.0),
        Zone("z2", "큰길", ZoneType.Caution, 37.55330, 126.89960, 80.0),
    )

    private fun alertsFor(points: List<TrackPoint>): List<RemoteAlert> {
        var states = mapOf<String, ZoneState>()
        val result = mutableListOf<RemoteAlert>()
        for (p in points) {
            val (next, alerts) = ZoneDetector.stepAll(zones, states, p)
            states = next
            alerts.forEach { a ->
                result += RemoteAlert(a.id, a.zoneId, a.zoneName, a.type, a.p1, a.p2, a.p2.t, a.zone.lat, a.zone.lng, a.zone.radiusM)
            }
        }
        return result
    }

    val alerts: List<RemoteAlert> = (alertsFor(todayPoints) + alertsFor(yesterdayPoints)).sortedByDescending { it.createdAt }

    val now = at(14, 34)
    val last = todayPoints.last()

    fun link(pending: Int = 0, location: Boolean = true, service: Boolean = true, lastPoint: TrackPoint = last) = RemoteLink(
        id = "L1", guardianUid = "g", protectedUid = "p", active = true, createdAt = at(9, 0, 0, today.minusDays(3)),
        lastPoint = lastPoint, pendingCount = pending, locationGranted = location, serviceOn = service, reportedAt = now,
    )

    fun home(chip: RecordStatus = RecordStatus.Recording, delayed: Boolean = false, link: RemoteLink = link(), now: Long = this.now,
             points: List<TrackPoint> = todayPoints) = HomeData(
        link = link, lastPoint = link.lastPoint ?: points.last(), points = points, stays = StayDetector.detect(points), zones = zones,
        alertsToday = alerts.count { SeoulTime.dayKey(it.createdAt) == SeoulTime.dayKey(now) }, now = now, delayed = delayed, chip = chip,
    )

    fun hours(points: List<TrackPoint>): List<HourCount> = points.groupBy { SeoulTime.hourKey(it.t) }
        .map { (k, v) -> HourCount(k, v.first().t, v.last().t, v.size) }.sortedByDescending { it.hourKey }
}

/** 갤러리 항목 이름(ID) → 화면 */
private val entries: Map<String, @Composable () -> Unit> = buildMap {
    val s = Sample
    // ---- FR-01 기기 연결 ----
    put("S01") { RoleScreen(onGuardian = noop, onProtected = noop) }
    fun s02(state: LinkRequestUi, confirm: Boolean = false): @Composable () -> Unit = {
        LinkRequestBody(state, noop, noop, noop, noop, noop, confirm, noop, noop)
    }
    val waiting = LinkRequestUi.Waiting("482913", System.currentTimeMillis() + 9 * 60_000L + 12_000L)
    put("S02_loading", s02(LinkRequestUi.Loading))
    put("S02_default", s02(waiting))
    put("S02_done", s02(LinkRequestUi.Linked))
    put("S02_error_denied", s02(LinkRequestUi.Failed(denied = true, code = "482913")))
    put("S02_error_expired", s02(LinkRequestUi.Failed(denied = false, code = "482913")))
    put("S02_error_offline", s02(LinkRequestUi.IssueError(offline = true)))
    put("D04", s02(waiting, confirm = true))
    put("S13_default") { CodeEntryBody("482913", CodeError.None, false, {}, noop, noop) }
    put("S13_error") { CodeEntryBody("482931", CodeError.Invalid, false, {}, noop, noop) }
    put("S14_default") { ConsentBody(ConsentError.None, false, noop, noop, noop) }
    put("S14_error") { ConsentBody(ConsentError.Expired, false, noop, noop, noop) }
    put("S15") { PermissionScreen(onFinished = noop) }
    val linkUi = LinkManageUi(Role.Guardian, active = true, since = s.at(9, 0, 0, s.today.minusDays(3)), lastReceived = s.last.t)
    put("S12") { LinkManageBody(linkUi, noop, false, noop, noop, noop) }
    put("D01") { LinkManageBody(linkUi, noop, true, noop, noop, noop) }

    // ---- FR-02 위치 (S-03) ----
    fun s03(state: com.example.gpsplace.ui.screens.s03_home.HomeState): @Composable () -> Unit = {
        GuardianFrame(GuardianTab.Location) { GuardianHomeBody(state, noop, noop, noop, noop, noop) }
    }
    put("S03_default", s03(ScreenState.Content(s.home())))
    put("S03_loading", s03(ScreenState.Loading))
    put("S03_empty_first", s03(ScreenState.Empty(HomeEmpty.NoFirstLocation)))
    put("S03_empty_nolink", s03(ScreenState.Empty(HomeEmpty.NoLink)))
    put("S03_error", s03(ScreenState.Error(Unit)))
    val late = s.now + 23 * 60_000L
    put("S03_delayed", s03(ScreenState.Content(s.home(RecordStatus.Delayed, delayed = true, now = late), HomePartial.Delayed(25))))
    put("S03_offline", s03(ScreenState.Content(s.home(), HomePartial.Offline(s.last.t))))
    put("S03_permission", s03(ScreenState.Content(s.home(RecordStatus.NotRecording, link = s.link(location = false)), HomePartial.PermissionOff)))
    val received = s.todayPoints.filter { it.t <= s.at(13, 50) }
    put("S03_pending", s03(ScreenState.Content(
        s.home(RecordStatus.PendingUpload, link = s.link(pending = 23), points = received),
        HomePartial.Pending,
    )))

    // ---- FR-05 날짜별 경로 (S-04) · FR-03 체류 (S-05) ----
    fun history(tab: HistoryTab, date: LocalDate, state: com.example.gpsplace.ui.screens.history.DayState, picker: Boolean = false): @Composable () -> Unit = {
        GuardianFrame(GuardianTab.History) {
            HistoryBody(tab, date, s.today, state, picker, {}, {}, {}, noop, {})
        }
    }
    val yesterdayData = DayData(s.yesterday, s.yesterdayPoints, s.yesterdayStays)
    val todayData = DayData(s.today, s.todayPoints, s.todayStays)
    put("S04_default", history(HistoryTab.Path, s.yesterday, ScreenState.Content(yesterdayData)))
    put("S04_loading", history(HistoryTab.Path, s.yesterday, ScreenState.Loading))
    put("S04_empty", history(HistoryTab.Path, s.today.minusDays(5), ScreenState.Empty(s.today.minusDays(5))))
    put("S04_error", history(HistoryTab.Path, s.yesterday, ScreenState.Error(DayError.Timeout)))
    val offlinePoints = s.todayPoints.filter { it.t <= s.at(12, 40) }
    put("S04_offline", history(HistoryTab.Path, s.today, ScreenState.Content(DayData(s.today, offlinePoints, StayDetector.detect(offlinePoints)), DayPartial.Offline)))
    put("S07", history(HistoryTab.Path, s.yesterday, ScreenState.Content(yesterdayData), picker = true))
    put("S05_default", history(HistoryTab.Stay, s.today, ScreenState.Content(todayData)))
    put("S05_loading", history(HistoryTab.Stay, s.today, ScreenState.Loading))
    put("S05_empty", history(HistoryTab.Stay, s.today, ScreenState.Content(DayData(s.today, s.todayPoints.take(6), emptyList()))))
    put("S05_error", history(HistoryTab.Stay, s.today, ScreenState.Error(DayError.OfflineNoData)))
    put("S05_offline", history(HistoryTab.Stay, s.today, ScreenState.Content(DayData(s.today, offlinePoints, StayDetector.detect(offlinePoints)), DayPartial.Offline)))
    val stay = s.todayStays.last()
    put("S06") {
        GuardianFrame(GuardianTab.History) { StayDetailBody(ScreenState.Content(StayDetail(stay, s.todayStays.size)), noop) }
    }

    // ---- FR-04 구역 알림 (S-08~S-11) ----
    fun s08(state: com.example.gpsplace.ui.screens.s08_alerts.AlertsState, notificationsOn: Boolean = true): @Composable () -> Unit = {
        GuardianFrame(GuardianTab.Alerts) { AlertsBody(state, notificationsOn, noop, {}, noop, noop, noop) }
    }
    put("S08_default", s08(ScreenState.Content(s.alerts)))
    put("S08_loading", s08(ScreenState.Loading))
    put("S08_empty_nozones", s08(ScreenState.Empty(AlertsEmpty.NoZones)))
    put("S08_empty_noalerts", s08(ScreenState.Empty(AlertsEmpty.NoAlerts)))
    put("S08_error", s08(ScreenState.Error(Unit)))
    put("S08_notifications_off", s08(ScreenState.Content(s.alerts), notificationsOn = false))
    val exitAlert = s.alerts.first { it.type == com.example.gpsplace.domain.AlertType.SafeExit }
    put("S09") { GuardianFrame(GuardianTab.Alerts) { AlertDetailBody(ScreenState.Content(exitAlert), noop, noop) } }
    put("S10") { GuardianFrame(GuardianTab.Alerts) { ZonesBody(ScreenState.Content(s.zones), noop, {}) } }
    val homeForm = ZoneForm("집", ZoneType.Safe, 200.0, s.HOME)
    fun s11(form: ZoneForm, edit: Boolean, error: Boolean, dialog: @Composable () -> Unit = {}): @Composable () -> Unit = {
        GuardianFrame(GuardianTab.Alerts) {
            ZoneEditBody(edit, false, form, error, false, {}, {}, {}, {}, noop, noop, noop)
        }
        dialog()
    }
    put("S11_default", s11(homeForm, edit = true, error = false))
    put("S11_error", s11(ZoneForm("", ZoneType.Caution, 150.0, s.PARK), edit = false, error = true))
    put("D02", s11(ZoneForm("학교 앞", ZoneType.Caution, 150.0, s.PARK), edit = false, error = false) {
        ConfirmDialog(
            title = stringResource(R.string.d02_title),
            message = stringResource(R.string.d02_message),
            dismissLabel = stringResource(R.string.d02_keep),
            confirmLabel = stringResource(R.string.d02_leave),
            onDismiss = noop,
            onConfirm = noop,
        )
    })
    put("D03", s11(homeForm, edit = true, error = false) { DeleteZoneDialog("집", noop, noop) })

    // ---- FR-02·FR-06 보호대상자 위치 기록 (S-16) ----
    fun s16(state: com.example.gpsplace.ui.screens.s16_protected_home.RecordingState): @Composable () -> Unit = {
        ProtectedHomeBody(state, noop, noop, noop, noop)
    }
    put("S16_default", s16(ScreenState.Content(RecordingData(s.last.t, 0))))
    put("S16_loading", s16(ScreenState.Loading))
    put("S16_empty", s16(ScreenState.Empty(Unit)))
    put("S16_error_permission", s16(ScreenState.Error(RecordingError(RecordingErrorKind.Permission, s.at(11, 20), 412))))
    put("S16_error_gps", s16(ScreenState.Error(RecordingError(RecordingErrorKind.LocationOff, s.at(11, 20), 412))))
    put("S16_error_storage", s16(ScreenState.Error(RecordingError(RecordingErrorKind.Storage, s.at(11, 20), 1240))))
    put("S16_offline", s16(ScreenState.Content(RecordingData(s.last.t, 23), RecordingPartial.Offline)))

    // ---- FR-06 전송 상태 (S-17) ----
    val sent = s.todayPoints.filter { it.t <= s.at(14, 20) }
    val unsent = s.todayPoints.filter { it.t > s.at(14, 20) }
    val base = SyncUi(0, s.todayPoints.size, s.last.t, s.hours(s.todayPoints).take(5), emptyList(), null, failed = false, online = true)
    put("S17_default") { SyncBody(base, noop, noop) }
    put("S17_loading") { SyncBody(base.copy(pending = 23, progress = com.example.gpsplace.data.repo.UploadProgress(12, 23)), noop, noop) }
    put("S17_empty") { SyncBody(SyncUi(0, 0, null, emptyList(), emptyList(), null, failed = false, online = true), noop, noop) }
    put("S17_error") { SyncBody(base.copy(pending = 23, failed = true), noop, noop) }
    put("S17_offline") {
        SyncBody(SyncUi(unsent.size, sent.size, s.at(14, 20), s.hours(sent).take(4), s.hours(unsent), null, failed = false, online = false), noop, noop)
    }
}

val galleryIds: List<String> get() = entries.keys.toList()

/** 실제 앱과 같은 하단 탭 틀 (AppNavHost와 같은 inset 처리) */
@Composable
private fun GuardianFrame(tab: GuardianTab, content: @Composable () -> Unit) {
    Scaffold(
        containerColor = AppColors.White,
        contentWindowInsets = WindowInsets(0),
        bottomBar = { GuardianBottomBar(currentTab = tab, onSelect = {}) },
    ) { padding ->
        Box(Modifier.padding(padding).consumeWindowInsets(padding)) { content() }
    }
}

@Composable
fun StateGalleryScreen() {
    val id = galleryId ?: return
    entries[id]?.invoke()
}
