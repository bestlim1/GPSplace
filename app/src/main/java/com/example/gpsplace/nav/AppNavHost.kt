package com.example.gpsplace.nav

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.gpsplace.debug.debugScreens
import com.example.gpsplace.ui.screens.history.HistoryScreen
import com.example.gpsplace.ui.screens.s01_role.RoleScreen
import com.example.gpsplace.ui.screens.s02_link.LinkRequestScreen
import com.example.gpsplace.ui.screens.s03_home.GuardianHomeScreen
import com.example.gpsplace.ui.screens.s06_stay_detail.StayDetailScreen
import com.example.gpsplace.ui.screens.s08_alerts.AlertsScreen
import com.example.gpsplace.ui.screens.s09_alert_detail.AlertDetailScreen
import com.example.gpsplace.ui.screens.s10_zones.ZonesScreen
import com.example.gpsplace.ui.screens.s11_zone_edit.ZoneEditScreen
import com.example.gpsplace.ui.screens.s12_link_manage.LinkManageScreen
import com.example.gpsplace.ui.screens.s13_code.CodeEntryScreen
import com.example.gpsplace.ui.screens.s14_consent.ConsentScreen
import com.example.gpsplace.ui.screens.s15_permission.PermissionScreen
import com.example.gpsplace.ui.screens.s16_protected_home.ProtectedHomeScreen
import com.example.gpsplace.ui.screens.s17_sync.SyncScreen
import com.example.gpsplace.ui.theme.AppColors

/**
 * 앱 전체 내비게이션 (UI 보고서 2장). 보호자 홈 영역(S-03~S-11)에서만 하단 탭을 보인다.
 * [openAlertId]: 구역 알림 푸시로 들어오면 S-03 → S-08 → S-09 백스택을 만든다.
 */
@Composable
fun AppNavHost(
    startDestination: String,
    navController: NavHostController = rememberNavController(),
    openAlertId: String? = null,
    onAlertOpened: () -> Unit = {},
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val hierarchy = backStackEntry?.destination?.hierarchy
    val inGuardianArea = hierarchy?.any { it.route == Routes.GRAPH_GUARDIAN } == true
    val currentTab = GuardianTab.entries.firstOrNull { tab -> hierarchy?.any { it.route == tab.graphRoute } == true }

    LaunchedEffect(openAlertId) {
        if (openAlertId != null && startDestination == Routes.GRAPH_GUARDIAN) {
            navController.navigateToGuardianTab(GuardianTab.Alerts)
            navController.navigate(Routes.alert(openAlertId))
            onAlertOpened()
        }
    }

    Scaffold(
        containerColor = AppColors.White,
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (inGuardianArea) {
                GuardianBottomBar(
                    currentTab = currentTab,
                    onSelect = { navController.navigateToGuardianTab(it, currentTab) },
                )
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
        ) {
            val back: () -> Unit = { navController.popBackStack() }
            val openLinkManage: () -> Unit = { navController.navigate(Routes.LINK_MANAGE) }

            // 연결·권한 설정 — 완료하면 스택에서 지운다
            composable(Routes.ROLE) {
                RoleScreen(
                    onGuardian = { navController.navigate(Routes.GUARDIAN_LINK) },
                    onProtected = { navController.navigate(Routes.PROTECTED_CODE) },
                )
            }
            composable(Routes.GUARDIAN_LINK) {
                LinkRequestScreen(
                    // 최초 연결이면 S-01, 재연결(S-03에서 옴)이면 S-03
                    onExit = { if (!navController.popBackStack()) navController.navigateClearing(Routes.ROLE) },
                    onLinked = { navController.navigateClearing(Routes.GRAPH_GUARDIAN) },
                    onHome = { navController.navigateClearing(Routes.ROLE) },
                )
            }
            composable(Routes.PROTECTED_CODE) {
                CodeEntryScreen(onBack = back, onNext = { code -> navController.navigate(Routes.consent(code)) })
            }
            composable(
                route = Routes.PROTECTED_CONSENT,
                arguments = listOf(navArgument(Routes.ARG_CODE) { type = NavType.StringType; nullable = true; defaultValue = null }),
            ) {
                ConsentScreen(
                    onBack = back,
                    onLinked = { navController.navigateClearing(Routes.PROTECTED_PERMISSION) },
                    onDenied = { navController.navigateClearing(Routes.ROLE) },
                )
            }
            composable(Routes.PROTECTED_PERMISSION) {
                PermissionScreen(onFinished = { navController.navigateClearing(Routes.PROTECTED_HOME) })
            }

            guardianArea(navController, back, openLinkManage, { currentTab })

            // 공통
            composable(Routes.LINK_MANAGE) { LinkManageScreen(onBack = back, onReleased = back) }

            // 보호대상자
            composable(Routes.PROTECTED_HOME) {
                ProtectedHomeScreen(
                    onOpenLinkManage = openLinkManage,
                    onOpenSync = { navController.navigate(Routes.PROTECTED_SYNC) },
                    onOpenPermission = { navController.navigate(Routes.PROTECTED_PERMISSION) },
                    onLink = { navController.navigate(Routes.PROTECTED_CODE) },
                )
            }
            composable(Routes.PROTECTED_SYNC) { SyncScreen(onBack = back) }

            // 디버그 빌드에서만 화면 목록이 들어간다 (릴리스는 빈 함수).
            debugScreens(navController)
        }
    }
}

/** 보호자 홈 영역: 탭마다 중첩 그래프 하나 = 탭별 스택 (목록 → 상세 → 폼). */
private fun NavGraphBuilder.guardianArea(
    navController: NavHostController,
    back: () -> Unit,
    openLinkManage: () -> Unit,
    currentTab: () -> GuardianTab?,
) {
    navigation(route = Routes.GRAPH_GUARDIAN, startDestination = GuardianTab.Location.graphRoute) {
        navigation(route = GuardianTab.Location.graphRoute, startDestination = Routes.GUARDIAN_HOME) {
            composable(Routes.GUARDIAN_HOME) {
                GuardianHomeScreen(
                    onOpenLinkManage = openLinkManage,
                    onOpenStays = { navController.openHistory(HistoryTab.Stay, dateKey = null, currentTab = currentTab()) },
                    onOpenAlerts = { navController.navigateToGuardianTab(GuardianTab.Alerts, currentTab()) },
                    onLink = { navController.navigate(Routes.GUARDIAN_LINK) },
                )
            }
        }

        navigation(route = GuardianTab.History.graphRoute, startDestination = Routes.GUARDIAN_HISTORY) {
            composable(
                route = Routes.GUARDIAN_HISTORY,
                arguments = listOf(
                    navArgument(Routes.ARG_TAB) { type = NavType.StringType; defaultValue = HistoryTab.Path.key },
                    navArgument(Routes.ARG_DATE) { type = NavType.StringType; nullable = true; defaultValue = null },
                ),
            ) { entry ->
                // 상단 탭·날짜는 이 화면 항목의 SavedStateHandle에 둔다 → 탭 전환·프로세스 종료 뒤에도 유지
                val handle = entry.savedStateHandle
                val tabKey by handle.getStateFlow(Routes.ARG_TAB, HistoryTab.Path.key).collectAsStateWithLifecycle()
                val dateKey by handle.getStateFlow<String?>(Routes.ARG_DATE, null).collectAsStateWithLifecycle()
                val sheet by handle.getStateFlow(Routes.ARG_SHEET, false).collectAsStateWithLifecycle()
                HistoryScreen(
                    selectedTab = HistoryTab.fromKey(tabKey),
                    onSelectTab = { handle[Routes.ARG_TAB] = it.key },
                    dateKey = dateKey,
                    onDateChange = { handle[Routes.ARG_DATE] = it },
                    openPicker = sheet,
                    onPickerConsumed = { handle[Routes.ARG_SHEET] = false },
                    onOpenStay = { navController.navigate(Routes.stay(it)) },
                )
            }
            composable(
                route = Routes.GUARDIAN_STAY,
                arguments = listOf(navArgument(Routes.ARG_STAY_ID) { type = NavType.StringType }),
            ) {
                StayDetailScreen(onBack = back)
            }
        }

        navigation(route = GuardianTab.Alerts.graphRoute, startDestination = Routes.GUARDIAN_ALERTS) {
            composable(Routes.GUARDIAN_ALERTS) {
                AlertsScreen(
                    onOpenZones = { navController.navigate(Routes.GUARDIAN_ZONES) },
                    onOpenAlert = { navController.navigate(Routes.alert(it)) },
                    onAddZone = { navController.navigate(Routes.zoneEdit()) },
                )
            }
            composable(
                route = Routes.GUARDIAN_ALERT,
                arguments = listOf(navArgument(Routes.ARG_ALERT_ID) { type = NavType.StringType }),
            ) {
                AlertDetailScreen(onBack = back)
            }
            composable(Routes.GUARDIAN_ZONES) {
                ZonesScreen(onBack = back, onEdit = { navController.navigate(Routes.zoneEdit(it)) })
            }
            composable(
                route = Routes.GUARDIAN_ZONE_EDIT,
                arguments = listOf(navArgument(Routes.ARG_ZONE_ID) { type = NavType.StringType; nullable = true; defaultValue = null }),
            ) {
                ZoneEditScreen(onClose = back)
            }
        }
    }
}
