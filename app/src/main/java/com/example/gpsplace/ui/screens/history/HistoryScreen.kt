package com.example.gpsplace.ui.screens.history

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.gpsplace.domain.SeoulTime
import com.example.gpsplace.nav.HistoryTab
import com.example.gpsplace.ui.appViewModel
import com.example.gpsplace.ui.components.DateBar
import com.example.gpsplace.ui.screens.s04_path.PathTab
import com.example.gpsplace.ui.screens.s05_stays.StaysTab
import com.example.gpsplace.ui.screens.s07_date_picker.DatePickerSheet
import com.example.gpsplace.ui.theme.AppColors
import java.time.LocalDate

/**
 * 기록 탭: DateBar + 상단 탭 [경로|체류]가 같은 날짜를 공유한다 (S-04, S-05).
 * 탭 라벨 터치로만 전환하고 지도 위 스와이프 전환은 두지 않는다.
 * [dateKey]가 null이면 오늘. 날짜·상단 탭은 이 화면 항목에 저장되어 다른 탭에 다녀와도 유지된다.
 */
@Composable
fun HistoryScreen(
    selectedTab: HistoryTab,
    onSelectTab: (HistoryTab) -> Unit,
    dateKey: String?,
    onDateChange: (String) -> Unit,
    openPicker: Boolean,
    onPickerConsumed: () -> Unit,
    onOpenStay: (String) -> Unit,
) {
    val viewModel = appViewModel { container, _ -> HistoryViewModel(container) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val today = viewModel.today
    val date = dateKey?.let { runCatching { SeoulTime.date(it) }.getOrNull() }?.takeIf { it <= today } ?: today
    var showPicker by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(date) { viewModel.setDate(date) }
    LaunchedEffect(openPicker) {
        if (openPicker) {
            showPicker = true
            onPickerConsumed()
        }
    }

    HistoryBody(
        selectedTab = selectedTab,
        date = date,
        today = today,
        state = state,
        showPicker = showPicker,
        onSelectTab = onSelectTab,
        onDateChange = { onDateChange(SeoulTime.dayKey(it)) },
        onShowPicker = { showPicker = it },
        onRetry = viewModel::retry,
        onOpenStay = onOpenStay,
    )
}

/** S-04·S-05(+ S-07 시트) 화면 본체. 상태만 받아 그린다 (디버그 상태 갤러리에서도 쓴다). */
@Composable
fun HistoryBody(
    selectedTab: HistoryTab,
    date: LocalDate,
    today: LocalDate,
    state: DayState,
    showPicker: Boolean,
    onSelectTab: (HistoryTab) -> Unit,
    onDateChange: (LocalDate) -> Unit,
    onShowPicker: (Boolean) -> Unit,
    onRetry: () -> Unit,
    onOpenStay: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        DateBar(
            date = date,
            today = today,
            onPrevious = { onDateChange(date.minusDays(1)) },
            onNext = { if (date < today) onDateChange(date.plusDays(1)) },
            onPick = { onShowPicker(true) },
        )
        PrimaryTabRow(
            selectedTabIndex = selectedTab.ordinal,
            containerColor = AppColors.White,
            contentColor = AppColors.Primary,
        ) {
            HistoryTab.entries.forEach { tab ->
                Tab(
                    selected = tab == selectedTab,
                    onClick = { onSelectTab(tab) },
                    text = { Text(stringResource(tab.label)) },
                    selectedContentColor = AppColors.Primary,
                    unselectedContentColor = AppColors.Text2,
                )
            }
        }
        when (selectedTab) {
            HistoryTab.Path -> PathTab(
                state = state,
                date = date,
                onRetry = onRetry,
                onPickDate = { onShowPicker(true) },
            )
            HistoryTab.Stay -> StaysTab(
                state = state,
                date = date,
                isToday = date == today,
                onRetry = onRetry,
                onOpenStay = onOpenStay,
            )
        }
    }

    if (showPicker) {
        DatePickerSheet(
            selected = date,
            today = today,
            onDismiss = { onShowPicker(false) },
            onConfirm = {
                onShowPicker(false)
                onDateChange(it)
            },
        )
    }
}
