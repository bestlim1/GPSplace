package com.example.gpsplace.nav

import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.navigation.NavHostController
import com.example.gpsplace.ui.theme.AppColors

/** 선택된 탭 = 파랑 굵은 글자 + 아이콘 뒤 배경 (UI 보고서 6.4). */
@Composable
fun GuardianBottomBar(
    currentTab: GuardianTab?,
    onSelect: (GuardianTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationBar(modifier = modifier, containerColor = AppColors.White) {
        GuardianTab.entries.forEach { tab ->
            val selected = tab == currentTab
            NavigationBarItem(
                selected = selected,
                onClick = { onSelect(tab) },
                // 라벨이 같은 뜻을 읽어 주므로 아이콘 설명은 비운다.
                icon = { Icon(tab.icon, contentDescription = null) },
                label = {
                    Text(
                        stringResource(tab.label),
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = AppColors.Primary,
                    selectedTextColor = AppColors.Primary,
                    indicatorColor = AppColors.Surface,
                    unselectedIconColor = AppColors.Text2,
                    unselectedTextColor = AppColors.Text2,
                ),
            )
        }
    }
}

/**
 * 탭 전환. 백스택에 쌓지 않고 탭별 스택을 저장·복원한다.
 * 위치 탭으로 갈 때는 S-03 위를 걷어 내고, 지금 탭을 다시 누르면 그 탭 루트로 돌아간다.
 */
fun NavHostController.navigateToGuardianTab(tab: GuardianTab, currentTab: GuardianTab? = null) {
    when {
        tab == currentTab -> popBackStack(tab.rootRoute, inclusive = false)
        tab == GuardianTab.Location -> popBackStack(Routes.GUARDIAN_HOME, inclusive = false, saveState = true)
        else -> navigate(tab.graphRoute) {
            popUpTo(Routes.GUARDIAN_HOME) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
}

/** 기록 탭 안 상단 탭 고르기. S-03의 ‘오늘 체류’ 바로가기처럼 탭 전환 뒤에 체류 탭을 열 때 쓴다. */
fun NavHostController.selectHistoryTab(tab: HistoryTab) {
    getBackStackEntry(Routes.GUARDIAN_HISTORY).savedStateHandle[Routes.ARG_TAB] = tab.key
}

/** 기록 탭 첫 화면으로 가서 상단 탭·날짜를 정한다. [dateKey] null = 오늘 */
fun NavHostController.openHistory(tab: HistoryTab, dateKey: String? = null, currentTab: GuardianTab? = null) {
    if (currentTab != GuardianTab.History) navigateToGuardianTab(GuardianTab.History, currentTab)
    popBackStack(Routes.GUARDIAN_HISTORY, inclusive = false)
    val handle = getBackStackEntry(Routes.GUARDIAN_HISTORY).savedStateHandle
    handle[Routes.ARG_TAB] = tab.key
    handle[Routes.ARG_DATE] = dateKey
}

/** 연결·권한 설정 화면을 스택에서 모두 지우고 [route]를 루트로 */
fun NavHostController.navigateClearing(route: String) {
    navigate(route) {
        popUpTo(graph.id) { inclusive = true }
        launchSingleTop = true
    }
}
