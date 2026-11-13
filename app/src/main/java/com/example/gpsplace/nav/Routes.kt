package com.example.gpsplace.nav

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.gpsplace.R
import com.example.gpsplace.ui.components.AppIcons

// route 문자열은 docs/SCREENS.md와 같게 둔다. 그래프 route(GRAPH_*, TAB_*)는 문서에 없는 내부 이름이다.
// S-14의 ?code=는 S-13에서 확인한 코드를 넘기는 선택 인자다.
object Routes {
    const val ARG_TAB = "tab"
    const val ARG_DATE = "date"
    const val ARG_SHEET = "sheet"
    const val ARG_STAY_ID = "stayId"
    const val ARG_ALERT_ID = "alertId"
    const val ARG_ZONE_ID = "zoneId"
    const val ARG_CODE = "code"

    // 연결·권한 설정 (완료 시 스택에서 제거)
    const val ROLE = "role"                                   // S-01
    const val GUARDIAN_LINK = "guardian/link"                 // S-02
    const val PROTECTED_CODE = "protected/code"               // S-13
    const val PROTECTED_CONSENT = "protected/consent?$ARG_CODE={$ARG_CODE}" // S-14
    const val PROTECTED_PERMISSION = "protected/permission"   // S-15

    // 보호자 홈 영역 (하단 탭 3개, 탭마다 자기 스택)
    const val GRAPH_GUARDIAN = "graph/guardian"
    const val GUARDIAN_HOME = "guardian/home"                                     // S-03
    const val GUARDIAN_HISTORY = "guardian/history?$ARG_TAB={$ARG_TAB}&$ARG_DATE={$ARG_DATE}" // S-04, S-05 (+ S-07 시트)
    const val GUARDIAN_STAY = "guardian/stay/{$ARG_STAY_ID}"                      // S-06
    const val GUARDIAN_ALERTS = "guardian/alerts"                                 // S-08
    const val GUARDIAN_ALERT = "guardian/alert/{$ARG_ALERT_ID}"                   // S-09
    const val GUARDIAN_ZONES = "guardian/zones"                                   // S-10
    const val GUARDIAN_ZONE_EDIT = "guardian/zone/edit?$ARG_ZONE_ID={$ARG_ZONE_ID}" // S-11

    // 공통
    const val LINK_MANAGE = "link/manage"                     // S-12

    // 보호대상자 (탭 없음, 루트 S-16)
    const val PROTECTED_HOME = "protected/home"               // S-16
    const val PROTECTED_SYNC = "protected/sync"               // S-17

    fun consent(code: String) = "protected/consent?$ARG_CODE=$code"
    fun stay(stayId: String) = "guardian/stay/$stayId"
    fun alert(alertId: String) = "guardian/alert/$alertId"
    fun zoneEdit(zoneId: String? = null) =
        if (zoneId == null) "guardian/zone/edit" else "guardian/zone/edit?$ARG_ZONE_ID=$zoneId"
}

/** 보호자 하단 탭. 시작 탭 = 위치(S-03). */
enum class GuardianTab(
    val graphRoute: String,
    val rootRoute: String,
    @StringRes val label: Int,
    val icon: ImageVector,
) {
    Location("tab/location", Routes.GUARDIAN_HOME, R.string.tab_location, AppIcons.TabLocation),
    History("tab/history", Routes.GUARDIAN_HISTORY, R.string.tab_history, AppIcons.TabHistory),
    Alerts("tab/alerts", Routes.GUARDIAN_ALERTS, R.string.tab_alerts, AppIcons.TabAlerts),
}

/** 기록 탭 안 상단 탭. route의 tab 값(path·stay)과 같다. */
enum class HistoryTab(val key: String, @StringRes val label: Int) {
    Path("path", R.string.history_tab_path),
    Stay("stay", R.string.history_tab_stay);

    companion object {
        fun fromKey(key: String?): HistoryTab = entries.firstOrNull { it.key == key } ?: Path
    }
}
