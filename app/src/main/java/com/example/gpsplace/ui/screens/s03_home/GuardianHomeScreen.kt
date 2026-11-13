package com.example.gpsplace.ui.screens.s03_home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.gpsplace.R
import com.example.gpsplace.domain.SeoulTime
import com.example.gpsplace.ui.Formats
import com.example.gpsplace.ui.ScreenState
import com.example.gpsplace.ui.TraceUntilReady
import com.example.gpsplace.ui.appViewModel
import com.example.gpsplace.ui.components.AppCard
import com.example.gpsplace.ui.components.AppIcons
import com.example.gpsplace.ui.components.AppMap
import com.example.gpsplace.ui.components.AppTopBar
import com.example.gpsplace.ui.components.BannerTone
import com.example.gpsplace.ui.components.DelayedLoading
import com.example.gpsplace.ui.components.EmptyState
import com.example.gpsplace.ui.components.ErrorState
import com.example.gpsplace.ui.components.LatestMarker
import com.example.gpsplace.ui.components.ListRow
import com.example.gpsplace.ui.components.RouteLayer
import com.example.gpsplace.ui.components.SettingsAction
import com.example.gpsplace.ui.components.SkeletonList
import com.example.gpsplace.ui.components.StateBanner
import com.example.gpsplace.ui.components.StatusChip
import com.example.gpsplace.ui.components.StayMarker
import com.example.gpsplace.ui.components.ZoneLayer
import com.example.gpsplace.ui.components.defaultCamera
import com.example.gpsplace.ui.components.fit
import com.example.gpsplace.ui.components.latLng
import com.example.gpsplace.ui.relativeText
import com.example.gpsplace.ui.theme.AppColors
import com.example.gpsplace.ui.theme.AppType
import com.example.gpsplace.ui.theme.Corners
import com.example.gpsplace.ui.theme.Spacing
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.launch

/**
 * S-03 위치 (보호자 홈). 위치와 시각을 반드시 한 카드에 둔다 (UI 보고서 5장, FR-02 문제 정의).
 * 체류·알림 행은 탭 전환으로 처리한다.
 */
@Composable
fun GuardianHomeScreen(
    onOpenLinkManage: () -> Unit,
    onOpenStays: () -> Unit,
    onOpenAlerts: () -> Unit,
    onLink: () -> Unit,
) {
    val viewModel = appViewModel { container, _ -> GuardianHomeViewModel(container) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    TraceUntilReady("S-03 홈 카드", state is ScreenState.Content)
    GuardianHomeBody(state, viewModel::retry, onOpenLinkManage, onOpenStays, onOpenAlerts, onLink)
}

/** S-03 화면 본체. 상태만 받아 그린다 (디버그 상태 갤러리에서도 쓴다). */
@Composable
fun GuardianHomeBody(
    state: HomeState,
    onRetry: () -> Unit,
    onOpenLinkManage: () -> Unit,
    onOpenStays: () -> Unit,
    onOpenAlerts: () -> Unit,
    onLink: () -> Unit,
) {
    Scaffold(
        containerColor = AppColors.White,
        topBar = {
            AppTopBar(
                title = stringResource(R.string.s03_title),
                actions = { if (state !is ScreenState.Empty || (state as ScreenState.Empty).reason != HomeEmpty.NoLink) SettingsAction(onOpenLinkManage) },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            when (val current = state) {
                ScreenState.Loading -> {
                    MapPlaceholder()
                    DelayedLoading { SkeletonList(rows = 2, contentDescription = stringResource(R.string.map_loading)) }
                }
                is ScreenState.Empty -> when (current.reason) {
                    HomeEmpty.NoLink -> {
                        MapPlaceholder(dimmed = true)
                        EmptyState(
                            title = stringResource(R.string.s03_empty_nolink_title),
                            description = stringResource(R.string.s03_empty_nolink_desc),
                            icon = AppIcons.Person,
                            actionLabel = stringResource(R.string.s03_link_again),
                            onAction = onLink,
                        )
                    }
                    HomeEmpty.NoFirstLocation -> {
                        MapPlaceholder()
                        EmptyState(
                            title = stringResource(R.string.s03_empty_first_title),
                            description = stringResource(R.string.s03_empty_first_desc),
                            icon = AppIcons.Place,
                        )
                    }
                }
                is ScreenState.Error -> {
                    MapPlaceholder()
                    ErrorState(
                        title = stringResource(R.string.s03_error_title),
                        cause = stringResource(R.string.cause_timeout),
                        actionLabel = stringResource(R.string.action_retry),
                        onAction = onRetry,
                    )
                }
                is ScreenState.Content -> HomeContent(current.data, current.partial, onOpenStays, onOpenAlerts)
            }
        }
    }
}

@Composable
private fun mapHeight() = (LocalConfiguration.current.screenHeightDp * 0.45f).coerceAtLeast(260f).dp

@Composable
private fun MapPlaceholder(dimmed: Boolean = false) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(mapHeight())
            .alpha(if (dimmed) 0.4f else 1f)
            .background(AppColors.MapBase),
        contentAlignment = Alignment.Center,
    ) {
        if (!dimmed) Text(stringResource(R.string.map_loading), style = AppType.Caption, color = AppColors.Text2)
    }
}

@Composable
private fun HomeContent(data: HomeData, partial: HomePartial?, onOpenStays: () -> Unit, onOpenAlerts: () -> Unit) {
    when (partial) {
        is HomePartial.Offline -> StateBanner(
            message = stringResource(R.string.s03_banner_offline, Formats.hm(partial.receivedAt ?: data.now)),
            icon = AppIcons.CloudOff,
        )
        HomePartial.PermissionOff -> StateBanner(message = stringResource(R.string.s03_banner_permission), tone = BannerTone.Error)
        HomePartial.ServiceOff -> StateBanner(message = stringResource(R.string.s03_banner_service_off), tone = BannerTone.Error)
        HomePartial.Pending -> StateBanner(
            message = stringResource(R.string.s03_banner_pending),
            baseTime = stringResource(R.string.s03_banner_pending_detail),
            icon = AppIcons.Upload,
        )
        is HomePartial.Delayed -> StateBanner(message = stringResource(R.string.s03_banner_delayed, partial.minutes.toInt()))
        null -> Unit
    }

    HomeMap(data)

    Column(
        modifier = Modifier.padding(horizontal = Spacing.ScreenHorizontal, vertical = Spacing.L),
        verticalArrangement = Arrangement.spacedBy(Spacing.M),
    ) {
        val last = data.lastPoint ?: return@Column
        AppCard {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.CenterVertically) {
                    StatusChip(data.chip)
                    Text(Formats.day(SeoulTime.today(data.now)), style = AppType.Caption, color = AppColors.Text2)
                }
                Text(stringResource(R.string.s03_latest), style = AppType.Caption, color = AppColors.Text2)
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.M), verticalAlignment = Alignment.Bottom) {
                    Text(
                        stringResource(R.string.s03_collected, Formats.timeOrDayTime(last.t, data.now)),
                        style = AppType.BigStatus,
                        color = AppColors.Text,
                    )
                    Text(relativeText(last.t, data.now), style = AppType.Body, color = if (data.delayed) AppColors.Caution else AppColors.Text2)
                }
                if (data.delayed) Text(stringResource(R.string.s03_showing_last), style = AppType.Caption, color = AppColors.Text2)
            }
        }
    }
    HorizontalDivider(color = AppColors.Divider)
    ListRow(onClick = onOpenStays) {
        Text(stringResource(R.string.s03_stays_today, data.stays.size), style = AppType.Body, color = AppColors.Text, modifier = Modifier.weight(1f))
        if (partial is HomePartial.Offline) {
            Text(stringResource(R.string.s03_stays_base, Formats.hm(partial.receivedAt ?: data.now)), style = AppType.Caption, color = AppColors.Text2)
        }
        Icon(AppIcons.NextDay, contentDescription = null, tint = AppColors.Text2)
    }
    HorizontalDivider(color = AppColors.Divider)
    ListRow(onClick = onOpenAlerts) {
        Text(stringResource(R.string.s03_alerts_today, data.alertsToday), style = AppType.Body, color = AppColors.Text, modifier = Modifier.weight(1f))
        Icon(AppIcons.NextDay, contentDescription = null, tint = AppColors.Text2)
    }
    HorizontalDivider(color = AppColors.Divider)
}

@Composable
private fun HomeMap(data: HomeData) {
    val cameraState = rememberCameraPositionState { position = defaultCamera() }
    val scope = rememberCoroutineScope()
    val padding = with(LocalDensity.current) { 48.dp.roundToPx() }
    var mapLoaded by remember { mutableStateOf(false) }
    var fitted by rememberSaveable { mutableStateOf(false) }
    val focus = remember(data.points, data.lastPoint) {
        (data.points.map { it.latLng() } + listOfNotNull(data.lastPoint?.latLng())).ifEmpty { emptyList() }
    }
    LaunchedEffect(mapLoaded, focus) {
        if (mapLoaded && !fitted && focus.isNotEmpty()) {
            cameraState.fit(focus, padding)
            fitted = true
        }
    }
    Box(Modifier.fillMaxWidth().height(mapHeight())) {
        AppMap(cameraPositionState = cameraState, modifier = Modifier.fillMaxSize(), onMapLoaded = { mapLoaded = true }) {
            data.zones.forEach { ZoneLayer(it) }
            RouteLayer(data.points)
            data.stays.forEachIndexed { index, stay -> StayMarker(stay, index + 1) }
            data.lastPoint?.let { LatestMarker(it, dashed = data.delayed) }
        }
        SmallFloatingActionButton(
            onClick = { scope.launch { data.lastPoint?.let { cameraState.fit(listOf(it.latLng()), padding) } } },
            containerColor = AppColors.White,
            contentColor = AppColors.Primary,
            shape = RoundedCornerShape(Corners.Button),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(Spacing.M),
        ) {
            Icon(AppIcons.Recenter, contentDescription = stringResource(R.string.cd_map_recenter))
        }
    }
}
