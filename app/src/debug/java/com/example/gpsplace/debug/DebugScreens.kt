package com.example.gpsplace.debug

import android.content.Intent
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.example.gpsplace.R
import com.example.gpsplace.nav.GuardianTab
import com.example.gpsplace.nav.HistoryTab
import com.example.gpsplace.nav.Routes
import com.example.gpsplace.nav.navigateToGuardianTab
import com.example.gpsplace.nav.openHistory
import com.example.gpsplace.ui.components.AppTopBar
import com.example.gpsplace.ui.theme.AppColors
import com.example.gpsplace.ui.theme.AppType
import com.example.gpsplace.ui.theme.Sizes
import com.example.gpsplace.ui.theme.Spacing

// 디버그 빌드 전용 “화면 목록” (PLAN.md M0). src/debug/AndroidManifest.xml의 두 번째 아이콘으로 들어온다.
// 데이터가 필요한 화면은 실제 연결 상태에 따라 비어 있음·오류 상태로 보일 수 있다.

const val DEBUG_SCREENS_ROUTE = "debug/screens"
private const val DEBUG_LAUNCHER = "com.example.gpsplace.DebugScreensLauncher"

/**
 * “GPSplace 화면 목록” 아이콘으로 열렸으면 목록에서 시작한다. 일반 아이콘은 원래 시작 화면 그대로.
 * `--es gallery <ID>`로 열면 상태 갤러리의 그 화면 하나만 띄운다 (UI 설계서 스크린샷용).
 */
fun debugStartRoute(intent: Intent?): String? {
    intent?.getStringExtra("gallery")?.let {
        galleryId = it
        return GALLERY_ROUTE
    }
    return if (intent?.component?.className == DEBUG_LAUNCHER) DEBUG_SCREENS_ROUTE else null
}

fun NavGraphBuilder.debugScreens(navController: NavHostController) {
    composable(DEBUG_SCREENS_ROUTE) { DebugScreenList(onOpen = { it.open(navController) }) }
    composable(DEBUG_POINTS_ROUTE) { DebugPointsScreen(onBack = { navController.popBackStack() }) }
    composable(GALLERY_ROUTE) { StateGalleryScreen() }
}

private class DebugEntry(val id: String, @StringRes val name: Int, val open: NavHostController.() -> Unit)

// 보호자 화면은 실제와 같은 스택이 되도록 S-03에서 시작해 탭 전환·스택 이동으로 연다.
private val debugEntries = listOf(
    DebugEntry("S-01", R.string.s01_name) { navigate(Routes.ROLE) },
    DebugEntry("S-02", R.string.s02_name) { navigate(Routes.GUARDIAN_LINK) },
    DebugEntry("S-03", R.string.s03_name) { openGuardian() },
    DebugEntry("S-04", R.string.s04_name) { openGuardian(); openHistory(HistoryTab.Path) },
    DebugEntry("S-05", R.string.s05_name) { openGuardian(); openHistory(HistoryTab.Stay) },
    DebugEntry("S-06", R.string.s06_name) { openGuardian(); openHistory(HistoryTab.Stay); navigate(Routes.stay("s0")) },
    DebugEntry("S-07", R.string.s07_name) {
        openGuardian()
        openHistory(HistoryTab.Path)
        getBackStackEntry(Routes.GUARDIAN_HISTORY).savedStateHandle[Routes.ARG_SHEET] = true
    },
    DebugEntry("S-08", R.string.s08_name) { openAlerts() },
    DebugEntry("S-09", R.string.s09_name) { openAlerts(); navigate(Routes.alert("debug-alert")) },
    DebugEntry("S-10", R.string.s10_name) { openAlerts(); navigate(Routes.GUARDIAN_ZONES) },
    DebugEntry("S-11", R.string.s11_name) { openAlerts(); navigate(Routes.GUARDIAN_ZONES); navigate(Routes.zoneEdit()) },
    DebugEntry("S-12", R.string.s12_name) { navigate(Routes.LINK_MANAGE) },
    DebugEntry("S-13", R.string.s13_name) { navigate(Routes.PROTECTED_CODE) },
    DebugEntry("S-14", R.string.s14_name) { navigate(Routes.PROTECTED_CODE); navigate(Routes.consent("000000")) },
    DebugEntry("S-15", R.string.s15_name) { navigate(Routes.PROTECTED_PERMISSION) },
    DebugEntry("S-16", R.string.s16_name) { navigate(Routes.PROTECTED_HOME) },
    DebugEntry("S-17", R.string.s17_name) { navigate(Routes.PROTECTED_HOME); navigate(Routes.PROTECTED_SYNC) },
    DebugEntry("M1", R.string.debug_points_title) { navigate(DEBUG_POINTS_ROUTE) },
)

/** S-03부터 새로 열고, 이전 디버그 이동에서 저장된 기록·알림 탭 스택을 지운다. */
private fun NavHostController.openGuardian() {
    navigate(Routes.GRAPH_GUARDIAN)
    // 탭 그래프 route는 보호자 그래프 안에서만 찾을 수 있으므로 navigate 다음에 지운다.
    clearBackStack(GuardianTab.History.graphRoute)
    clearBackStack(GuardianTab.Alerts.graphRoute)
}

private fun NavHostController.openAlerts() {
    openGuardian()
    navigateToGuardianTab(GuardianTab.Alerts)
}

@Composable
private fun DebugScreenList(onOpen: (DebugEntry) -> Unit) {
    Scaffold(
        containerColor = AppColors.White,
        topBar = { AppTopBar(title = stringResource(R.string.debug_screens_title)) },
    ) { innerPadding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            items(debugEntries, key = { it.id }) { entry ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = Sizes.ListRowMin)
                        .clickable { onOpen(entry) }
                        .padding(horizontal = Spacing.ScreenHorizontal, vertical = Spacing.ListRowVertical),
                    verticalArrangement = Arrangement.spacedBy(Spacing.Xs),
                ) {
                    Text(entry.id, style = AppType.Body, color = AppColors.Text)
                    Text(stringResource(entry.name), style = AppType.Caption, color = AppColors.Text2)
                }
                HorizontalDivider(color = AppColors.Divider)
            }
        }
    }
}
