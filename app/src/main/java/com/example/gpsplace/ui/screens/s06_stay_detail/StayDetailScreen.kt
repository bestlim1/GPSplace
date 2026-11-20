package com.example.gpsplace.ui.screens.s06_stay_detail

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
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
import com.example.gpsplace.domain.SeoulTime
import com.example.gpsplace.domain.Stay
import com.example.gpsplace.domain.StayDetector
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
import com.example.gpsplace.ui.components.StayMarker
import com.example.gpsplace.ui.components.TopBarNavigation
import com.example.gpsplace.ui.durationText
import com.example.gpsplace.ui.theme.AppColors
import com.example.gpsplace.ui.theme.AppType
import com.example.gpsplace.ui.theme.MapStroke
import com.example.gpsplace.ui.theme.Spacing
import com.example.gpsplace.ui.timeoutToError
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.Dash
import com.google.android.gms.maps.model.Gap
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class StayDetail(val stay: Stay, val number: Int)

typealias StayDetailState = ScreenState<StayDetail, Unit, Unit, Nothing>

@OptIn(ExperimentalCoroutinesApi::class)
class StayDetailViewModel(container: AppContainer, saved: SavedStateHandle) : ViewModel() {
    private val stayId: String = saved.get<String>(Routes.ARG_STAY_ID).orEmpty()

    /** 번호(체류 n)를 알려면 그날 체류 목록이 필요하다. stayId = "s{앵커 시각}"이라 날짜를 바로 안다. */
    val state: StateFlow<StayDetailState> = container.settings.link
        .map { it.linkId }
        .distinctUntilChanged()
        .flatMapLatest { linkId ->
            val anchorT = stayId.removePrefix("s").toLongOrNull()
            if (linkId == null || anchorT == null) {
                flowOf<StayDetailState>(ScreenState.Error(Unit))
            } else {
                container.tracks.observeDayStays(linkId, SeoulTime.dayKey(anchorT)).map { fetched ->
                    val index = fetched.data.indexOfFirst { it.id == stayId }
                    val result: StayDetailState =
                        if (index < 0) ScreenState.Loading else ScreenState.Content(StayDetail(fetched.data[index], index + 1))
                    result
                }.catch { emit(ScreenState.Error(Unit)) }.timeoutToError(Unit)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ScreenState.Loading)
}

/** S-06 체류 상세 · FR-03. 대표 좌표 핀(번호) + 반경 50m 점선 원. 뒤로 = S-05 (스크롤 위치 유지) */
@Composable
fun StayDetailScreen(onBack: () -> Unit) {
    val viewModel = appViewModel { container, saved -> StayDetailViewModel(container, saved) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    StayDetailBody(state, onBack)
}

/** S-06 화면 본체. 상태만 받아 그린다 (디버그 상태 갤러리에서도 쓴다). */
@Composable
fun StayDetailBody(state: StayDetailState, onBack: () -> Unit) {
    val title = (state as? ScreenState.Content)?.data?.number?.let { stringResource(R.string.s06_title, it) } ?: stringResource(R.string.s06_name)
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
                    title = stringResource(R.string.s05_error_title),
                    cause = stringResource(R.string.cause_timeout),
                    actionLabel = stringResource(R.string.cd_back),
                    onAction = onBack,
                )
                is ScreenState.Empty -> Unit
                is ScreenState.Content -> Detail(current.data)
            }
        }
    }
}

@Composable
private fun Detail(detail: StayDetail) {
    val stay = detail.stay
    val center = LatLng(stay.lat, stay.lng)
    val cameraState = key(stay.id) { rememberCameraPositionState { position = CameraPosition.fromLatLngZoom(center, 17f) } }
    val density = LocalDensity.current
    AppMap(
        cameraPositionState = cameraState,
        modifier = Modifier
            .fillMaxWidth()
            .height((LocalConfiguration.current.screenHeightDp * 0.4f).coerceAtLeast(240f).dp),
    ) {
        Circle(
            center = center,
            radius = StayDetector.RADIUS_M,
            strokeColor = AppColors.Text,
            strokeWidth = with(density) { MapStroke.Zone.toPx() },
            strokePattern = with(density) { listOf(Dash(MapStroke.MissingDash.toPx()), Gap(MapStroke.MissingGap.toPx())) },
            fillColor = AppColors.Text.copy(alpha = 0.05f),
        )
        StayMarker(stay, detail.number)
    }
    Column(
        modifier = Modifier.padding(horizontal = Spacing.ScreenHorizontal, vertical = Spacing.L),
        verticalArrangement = Arrangement.spacedBy(Spacing.Xs),
    ) {
        InfoRow(stringResource(R.string.s06_start), Formats.hm(stay.startAt))
        InfoRow(stringResource(R.string.s06_end), Formats.hm(stay.endAt))
        InfoRow(stringResource(R.string.s06_duration), durationText(stay.durationSec))
        InfoRow(stringResource(R.string.s06_center), Formats.coordinate(stay.lat, stay.lng))
        Text(
            stringResource(R.string.s06_note),
            style = AppType.Caption,
            color = AppColors.Text2,
            modifier = Modifier.padding(top = Spacing.L),
        )
    }
}
