package com.example.gpsplace.ui.screens.s09_alert_detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.gpsplace.AppContainer
import com.example.gpsplace.R
import com.example.gpsplace.data.remote.RemoteAlert
import com.example.gpsplace.domain.AlertType
import com.example.gpsplace.domain.Zone
import com.example.gpsplace.domain.ZoneType
import com.example.gpsplace.nav.Routes
import com.example.gpsplace.ui.Formats
import com.example.gpsplace.ui.ScreenState
import com.example.gpsplace.ui.appViewModel
import com.example.gpsplace.ui.components.AppMap
import com.example.gpsplace.ui.components.AppTopBar
import com.example.gpsplace.ui.components.DelayedLoading
import com.example.gpsplace.ui.components.ErrorState
import com.example.gpsplace.ui.components.InfoRow
import com.example.gpsplace.ui.components.LoadingMessage
import com.example.gpsplace.ui.components.MapLabelMarker
import com.example.gpsplace.ui.components.TopBarNavigation
import com.example.gpsplace.ui.components.ZoneLayer
import com.example.gpsplace.ui.components.circleBounds
import com.example.gpsplace.ui.components.defaultCamera
import com.example.gpsplace.ui.components.fit
import com.example.gpsplace.ui.components.latLng
import com.example.gpsplace.ui.radiusText
import com.example.gpsplace.ui.theme.AppColors
import com.example.gpsplace.ui.theme.AppType
import com.example.gpsplace.ui.theme.Spacing
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

class AlertDetailViewModel(private val container: AppContainer, saved: SavedStateHandle) : ViewModel() {
    private val alertId: String = saved.get<String>(Routes.ARG_ALERT_ID).orEmpty()
    private val _state = MutableStateFlow<ScreenState<RemoteAlert, Unit, Unit, Nothing>>(ScreenState.Loading)
    val state: StateFlow<ScreenState<RemoteAlert, Unit, Unit, Nothing>> = _state

    init {
        load()
    }

    fun load() {
        _state.value = ScreenState.Loading
        viewModelScope.launch {
            _state.value = try {
                val linkId = container.settings.currentLink().linkId ?: error("연결 없음")
                container.firebase.ensureSignedIn()
                val alert = withTimeout(10_000) { container.tracks.alert(linkId, alertId) }
                if (alert != null) ScreenState.Content(alert) else ScreenState.Error(Unit)
            } catch (e: Exception) {
                ScreenState.Error(Unit)
            }
        }
    }
}

/** S-09 알림 상세 · FR-04 (푸시로도 진입). 구역 원 + 판정한 연속 위치 2개(①②). 뒤로 = S-08 */
@Composable
fun AlertDetailScreen(onBack: () -> Unit) {
    val viewModel = appViewModel { container, saved -> AlertDetailViewModel(container, saved) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    AlertDetailBody(state, onBack, viewModel::load)
}

/** S-09 화면 본체. 상태만 받아 그린다 (디버그 상태 갤러리에서도 쓴다). */
@Composable
fun AlertDetailBody(state: ScreenState<RemoteAlert, Unit, Unit, Nothing>, onBack: () -> Unit, onRetry: () -> Unit) {
    val alert = (state as? ScreenState.Content)?.data
    val title = when (alert?.type) {
        AlertType.SafeExit -> stringResource(R.string.alert_safe_exit)
        AlertType.CautionEnter -> stringResource(R.string.alert_caution_enter)
        null -> stringResource(R.string.s09_name)
    }
    Scaffold(
        containerColor = AppColors.White,
        topBar = { AppTopBar(title = title, navigation = TopBarNavigation.Back, onNavigate = onBack) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            when (val current = state) {
                ScreenState.Loading -> DelayedLoading { LoadingMessage(stringResource(R.string.map_loading)) }
                is ScreenState.Error -> ErrorState(
                    title = stringResource(R.string.s08_error_title),
                    cause = stringResource(R.string.cause_timeout),
                    actionLabel = stringResource(R.string.action_retry),
                    onAction = onRetry,
                )
                is ScreenState.Empty -> Unit
                is ScreenState.Content -> Detail(current.data)
            }
        }
    }
}

@Composable
private fun Detail(alert: RemoteAlert) {
    val exit = alert.type == AlertType.SafeExit
    val zoneType = if (exit) ZoneType.Safe else ZoneType.Caution
    val zone = if (alert.zoneLat != null && alert.zoneLng != null && alert.radiusM != null) {
        Zone(alert.zoneId, alert.zoneName, zoneType, alert.zoneLat, alert.zoneLng, alert.radiusM)
    } else null
    val cameraState = key(alert.id) { rememberCameraPositionState { position = defaultCamera() } }
    val padding = with(LocalDensity.current) { 48.dp.roundToPx() }
    var loaded by remember { mutableStateOf(false) }
    LaunchedEffect(loaded) {
        if (loaded) {
            val focus = listOf(alert.p1.latLng(), alert.p2.latLng()) + (zone?.let { circleBounds(it.lat, it.lng, it.radiusM) } ?: emptyList())
            cameraState.fit(focus, padding)
        }
    }
    AppMap(
        cameraPositionState = cameraState,
        modifier = Modifier
            .fillMaxWidth()
            .height((LocalConfiguration.current.screenHeightDp * 0.45f).coerceAtLeast(260f).dp),
        onMapLoaded = { loaded = true },
    ) {
        zone?.let { ZoneLayer(it) }
        // 두 점이 가까워도 라벨이 겹치지 않게 ①은 점 왼쪽, ②는 점 오른쪽에 붙인다
        MapLabelMarker(alert.p1.latLng(), "① ${Formats.hm(alert.p1.t)}", AppColors.Text, anchor = Offset(1.05f, 0.5f))
        MapLabelMarker(alert.p2.latLng(), "② ${Formats.hm(alert.p2.t)}", AppColors.Text, anchor = Offset(-0.05f, 0.5f))
    }
    val typeLabel = stringResource(if (exit) R.string.zone_safe else R.string.zone_caution)
    Column(
        modifier = Modifier.padding(horizontal = Spacing.ScreenHorizontal, vertical = Spacing.L),
        verticalArrangement = Arrangement.spacedBy(Spacing.Xs),
    ) {
        InfoRow(
            stringResource(R.string.s09_zone),
            if (zone != null) stringResource(R.string.s09_zone_value, alert.zoneName, typeLabel, radiusText(zone.radiusM))
            else "${alert.zoneName} · $typeLabel",
        )
        InfoRow(stringResource(R.string.s09_judgement), stringResource(if (exit) R.string.s09_judgement_exit else R.string.s09_judgement_enter))
        InfoRow(stringResource(R.string.s09_points_time), stringResource(R.string.s09_points_value, Formats.hm(alert.p1.t), Formats.hm(alert.p2.t)))
        InfoRow(stringResource(R.string.s09_created), Formats.timeOrDayTime(alert.createdAt, System.currentTimeMillis()))
        Text(
            stringResource(if (exit) R.string.s09_note_exit else R.string.s09_note_enter),
            style = AppType.Caption,
            color = AppColors.Text2,
            modifier = Modifier.padding(top = Spacing.L),
        )
    }
}
