package com.example.gpsplace.ui.screens.s04_path

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.gpsplace.R
import com.example.gpsplace.domain.GapDetector
import com.example.gpsplace.ui.Formats
import com.example.gpsplace.ui.ScreenState
import com.example.gpsplace.ui.TraceUntilReady
import com.example.gpsplace.ui.components.AppIcons
import com.example.gpsplace.ui.components.AppMap
import com.example.gpsplace.ui.components.DelayedLoading
import com.example.gpsplace.ui.components.EmptyState
import com.example.gpsplace.ui.components.ErrorState
import com.example.gpsplace.ui.components.LatestMarker
import com.example.gpsplace.ui.components.NumberCircle
import com.example.gpsplace.ui.components.RouteLayer
import com.example.gpsplace.ui.components.StartMarker
import com.example.gpsplace.ui.components.StateBanner
import com.example.gpsplace.ui.components.StayMarker
import com.example.gpsplace.ui.components.defaultCamera
import com.example.gpsplace.ui.components.fit
import com.example.gpsplace.ui.components.latLng
import com.example.gpsplace.ui.screens.history.DayData
import com.example.gpsplace.ui.screens.history.DayError
import com.example.gpsplace.ui.screens.history.DayPartial
import com.example.gpsplace.ui.screens.history.DayState
import com.example.gpsplace.ui.theme.AppColors
import com.example.gpsplace.ui.theme.AppType
import com.example.gpsplace.ui.theme.MapStroke
import com.example.gpsplace.ui.theme.Spacing
import com.google.maps.android.compose.rememberCameraPositionState
import java.time.LocalDate

/** S-04 기록 – 경로 · FR-02, FR-05. 선택 날짜 전체 경로, 출발, 체류 번호, 마지막 점 시각 + 범례 */
@Composable
fun PathTab(state: DayState, date: LocalDate, onRetry: () -> Unit, onPickDate: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        when (state) {
            ScreenState.Loading -> MapBox {
                DelayedLoading {
                    Text(stringResource(R.string.s04_loading, Formats.dayShort(date)), style = AppType.Caption, color = AppColors.Text2)
                }
            }
            is ScreenState.Empty -> EmptyState(
                title = stringResource(R.string.s04_empty_title),
                description = stringResource(R.string.s04_empty_desc, Formats.dayShort(date)),
                icon = AppIcons.Calendar,
                actionLabel = stringResource(R.string.s04_pick_other),
                onAction = onPickDate,
            )
            is ScreenState.Error -> ErrorState(
                title = stringResource(R.string.s04_error_title),
                cause = stringResource(if (state.cause == DayError.OfflineNoData) R.string.s05_cause_offline else R.string.cause_timeout),
                actionLabel = stringResource(R.string.action_retry),
                onAction = onRetry,
                secondaryActionLabel = stringResource(R.string.s04_change_date),
                onSecondaryAction = onPickDate,
            )
            is ScreenState.Content -> PathContent(state.data, state.partial)
        }
    }
}

@Composable
private fun mapHeight() = (LocalConfiguration.current.screenHeightDp * 0.5f).coerceAtLeast(280f).dp

@Composable
private fun MapBox(content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(mapHeight())
            .background(AppColors.MapBase),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun PathContent(data: DayData, partial: DayPartial?) {
    val first = data.points.first()
    val last = data.points.last()
    if (partial == DayPartial.Offline) {
        StateBanner(
            message = stringResource(R.string.s04_banner_offline),
            baseTime = stringResource(R.string.s04_saved_range, Formats.hm(first.t), Formats.hm(last.t)),
            icon = AppIcons.CloudOff,
        )
    }
    val cameraState = rememberCameraPositionState(key = data.date.toString()) { position = defaultCamera() }
    val padding = with(LocalDensity.current) { 48.dp.roundToPx() }
    var loaded by remember { mutableStateOf(false) }
    LaunchedEffect(loaded, data.date, data.points.size) {
        if (loaded) cameraState.fit(data.points.map { it.latLng() }, padding)
    }
    TraceUntilReady("S-04 경로 지도 ${data.date}", loaded)
    AppMap(
        cameraPositionState = cameraState,
        modifier = Modifier.fillMaxWidth().height(mapHeight()),
        onMapLoaded = { loaded = true },
    ) {
        RouteLayer(data.points)
        StartMarker(first)
        data.stays.forEachIndexed { index, stay -> StayMarker(stay, index + 1) }
        LatestMarker(last, dashed = false)
    }
    Legend()
    Column(
        modifier = Modifier.padding(horizontal = Spacing.ScreenHorizontal, vertical = Spacing.M),
        verticalArrangement = Arrangement.spacedBy(Spacing.S),
    ) {
        Text(stringResource(R.string.s04_collected_range, Formats.hm(first.t), Formats.hm(last.t)), style = AppType.Body, color = AppColors.Text)
        Text(stringResource(R.string.s04_stay_count, data.stays.size), style = AppType.Body, color = AppColors.Text)
        val gaps = GapDetector.gaps(data.points)
        if (gaps.isNotEmpty()) {
            val ranges = gaps.map { stringResource(R.string.time_range_tilde, Formats.hm(it.startAt), Formats.hm(it.endAt)) }.joinToString(", ")
            Text(stringResource(R.string.s04_missing, ranges, gaps.size), style = AppType.Body, color = AppColors.Missing)
        }
    }
}

/** 범례: 이동 경로 · 기록 누락 · 체류 · 출발 (색 없이도 모양으로 구분) */
@Composable
private fun Legend() {
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.ScreenHorizontal, vertical = Spacing.S),
        horizontalArrangement = Arrangement.spacedBy(Spacing.L),
        verticalArrangement = Arrangement.spacedBy(Spacing.S),
    ) {
        LegendItem(stringResource(R.string.map_route)) { LineSample(dashed = false) }
        LegendItem(stringResource(R.string.map_missing)) { LineSample(dashed = true) }
        LegendItem(stringResource(R.string.map_stay)) { NumberCircle(1, size = 18.dp) }
        LegendItem(stringResource(R.string.map_start)) {
            Box(Modifier.size(12.dp).background(AppColors.Text).border(1.dp, AppColors.White))
        }
    }
}

@Composable
private fun LegendItem(label: String, sample: @Composable () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Xs), verticalAlignment = Alignment.CenterVertically) {
        sample()
        Text(label, style = AppType.MapLabel, color = AppColors.Text2)
    }
}

@Composable
private fun LineSample(dashed: Boolean) {
    Box(
        Modifier
            .width(24.dp)
            .height(8.dp)
            .drawBehind {
                val y = size.height / 2
                drawLine(
                    color = if (dashed) AppColors.Missing else AppColors.Primary,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = (if (dashed) MapStroke.Missing else MapStroke.Route).toPx(),
                    pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(MapStroke.MissingDash.toPx(), MapStroke.MissingGap.toPx())) else null,
                )
            }
    )
}
