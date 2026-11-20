package com.example.gpsplace.ui.screens.s05_stays

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.gpsplace.R
import com.example.gpsplace.domain.Gap
import com.example.gpsplace.domain.GapDetector
import com.example.gpsplace.domain.Stay
import com.example.gpsplace.ui.Formats
import com.example.gpsplace.ui.ScreenState
import com.example.gpsplace.ui.TraceUntilReady
import com.example.gpsplace.ui.components.AppIcons
import com.example.gpsplace.ui.components.DelayedLoading
import com.example.gpsplace.ui.components.EmptyState
import com.example.gpsplace.ui.components.ErrorState
import com.example.gpsplace.ui.components.ListRow
import com.example.gpsplace.ui.components.NumberCircle
import com.example.gpsplace.ui.components.SkeletonList
import com.example.gpsplace.ui.components.StateBanner
import com.example.gpsplace.ui.durationText
import com.example.gpsplace.ui.screens.history.DayError
import com.example.gpsplace.ui.screens.history.DayPartial
import com.example.gpsplace.ui.screens.history.DayState
import com.example.gpsplace.ui.theme.AppColors
import com.example.gpsplace.ui.theme.AppType
import com.example.gpsplace.ui.theme.Corners
import com.example.gpsplace.ui.theme.Spacing
import java.time.LocalDate

private sealed interface StayItem {
    val sortKey: Long

    data class Row(val stay: Stay, val number: Int, val latest: Boolean) : StayItem {
        override val sortKey get() = stay.startAt
    }

    data class Split(val gap: Gap) : StayItem {
        override val sortKey get() = gap.startAt
    }
}

/** S-05 기록 – 체류 목록 · FR-03, FR-05. 최신이 맨 위, 행 전체를 누르면 S-06 */
@Composable
fun StaysTab(state: DayState, date: LocalDate, isToday: Boolean, onRetry: () -> Unit, onOpenStay: (String) -> Unit) {
    TraceUntilReady("S-05 체류 목록 $date", state is ScreenState.Content || state is ScreenState.Empty)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        when (state) {
            ScreenState.Loading -> DelayedLoading { SkeletonList(rows = 3) }
            is ScreenState.Empty -> if (isToday) {
                StaysEmpty(today = true)
            } else {
                EmptyState(
                    title = stringResource(R.string.s04_empty_title),
                    description = stringResource(R.string.s04_empty_desc, Formats.dayShort(date)),
                    icon = AppIcons.Calendar,
                )
            }
            is ScreenState.Error -> ErrorState(
                title = stringResource(R.string.s05_error_title),
                cause = stringResource(if (state.cause == DayError.OfflineNoData) R.string.s05_cause_offline else R.string.cause_timeout),
                actionLabel = stringResource(R.string.action_retry),
                onAction = onRetry,
            )
            is ScreenState.Content -> {
                val data = state.data
                if (state.partial == DayPartial.Offline) {
                    StateBanner(
                        message = stringResource(R.string.s05_banner_offline, Formats.hm(data.points.last().t)),
                        icon = AppIcons.CloudOff,
                    )
                }
                if (data.stays.isEmpty()) {
                    StaysEmpty(today = isToday)
                } else {
                    val latestId = data.stays.maxByOrNull { it.startAt }?.id
                    val items = data.stays.mapIndexed { i, stay -> StayItem.Row(stay, i + 1, stay.id == latestId) } +
                        GapDetector.gaps(data.points)
                            .filter { gap -> gap.startAt >= data.stays.first().startAt && gap.endAt <= data.stays.last().endAt }
                            .map { StayItem.Split(it) }
                    items.sortedByDescending { it.sortKey }.forEach { item ->
                        when (item) {
                            is StayItem.Row -> StayRow(item, onOpenStay)
                            is StayItem.Split -> SplitBand(item.gap)
                        }
                        HorizontalDivider(color = AppColors.Divider)
                    }
                    Text(
                        stringResource(R.string.s05_note),
                        style = AppType.Caption,
                        color = AppColors.Text2,
                        modifier = Modifier.padding(horizontal = Spacing.ScreenHorizontal, vertical = Spacing.L),
                    )
                }
            }
        }
    }
}

@Composable
private fun StaysEmpty(today: Boolean) {
    EmptyState(
        title = stringResource(if (today) R.string.s05_empty_title else R.string.s05_empty_title_past),
        description = stringResource(R.string.s05_empty_desc),
        icon = AppIcons.Place,
    )
}

@Composable
private fun StayRow(item: StayItem.Row, onOpenStay: (String) -> Unit) {
    ListRow(onClick = { onOpenStay(item.stay.id) }) {
        NumberCircle(item.number, size = 32.dp)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.Xs)) {
            // 글자가 커져 한 줄에 안 들어가면 태그를 통째로 다음 줄로 넘긴다 (단어 중간 줄바꿈 방지)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.S),
                verticalArrangement = Arrangement.spacedBy(Spacing.Xs),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.time_range, Formats.hm(item.stay.startAt), Formats.hm(item.stay.endAt)),
                    style = AppType.Body,
                    color = AppColors.Text,
                )
                if (item.latest) {
                    Text(
                        stringResource(R.string.s05_last_stay),
                        style = AppType.Caption,
                        color = AppColors.Primary,
                        softWrap = false,
                        modifier = Modifier
                            .border(1.dp, AppColors.Primary, RoundedCornerShape(Corners.Chip))
                            .padding(horizontal = Spacing.S, vertical = 2.dp),
                    )
                }
            }
            Text(stringResource(R.string.s05_duration, durationText(item.stay.durationSec)), style = AppType.Caption, color = AppColors.Text2)
        }
        androidx.compose.material3.Icon(AppIcons.NextDay, contentDescription = null, tint = AppColors.Text2)
    }
}

/** 행 사이 빗금 띠: “14:05 ~ 14:16 기록 간격 2분 초과 · 구간 분리” */
@Composable
private fun SplitBand(gap: Gap) {
    Text(
        stringResource(R.string.s05_gap, Formats.hm(gap.startAt), Formats.hm(gap.endAt)),
        style = AppType.Caption,
        color = AppColors.Text,
        modifier = Modifier
            .fillMaxWidth()
            .background(AppColors.Surface)
            .drawBehind {
                val step = 10.dp.toPx()
                var x = -size.height
                while (x < size.width) {
                    drawLine(AppColors.Divider, Offset(x, size.height), Offset(x + size.height, 0f), strokeWidth = 1.dp.toPx())
                    x += step
                }
            }
            .padding(horizontal = Spacing.ScreenHorizontal, vertical = Spacing.S),
    )
}
