package com.example.gpsplace.ui.screens.s10_zones

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.gpsplace.AppContainer
import com.example.gpsplace.R
import com.example.gpsplace.domain.Zone
import com.example.gpsplace.domain.ZoneType
import com.example.gpsplace.ui.ScreenState
import com.example.gpsplace.ui.appViewModel
import com.example.gpsplace.ui.components.AppIcons
import com.example.gpsplace.ui.components.AppTopBar
import com.example.gpsplace.ui.components.DelayedLoading
import com.example.gpsplace.ui.components.EmptyState
import com.example.gpsplace.ui.components.ErrorState
import com.example.gpsplace.ui.components.ListRow
import com.example.gpsplace.ui.components.PrimaryButton
import com.example.gpsplace.ui.components.SkeletonList
import com.example.gpsplace.ui.components.TopBarNavigation
import com.example.gpsplace.ui.radiusText
import com.example.gpsplace.ui.theme.AppColors
import com.example.gpsplace.ui.theme.AppType
import com.example.gpsplace.ui.theme.Spacing
import com.example.gpsplace.ui.timeoutToError
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

typealias ZonesState = ScreenState<List<Zone>, Unit, Unit, Nothing>

@OptIn(ExperimentalCoroutinesApi::class)
class ZonesViewModel(container: AppContainer) : ViewModel() {
    val state: StateFlow<ZonesState> = container.settings.link
        .map { it.linkId }
        .distinctUntilChanged()
        .flatMapLatest { linkId ->
            if (linkId == null) flowOf<ZonesState>(ScreenState.Empty(Unit))
            else combine(container.zones.observeZones(linkId), container.network.online) { zones, online ->
                val result: ZonesState = when {
                    zones.data.isEmpty() && zones.fromCache && online -> ScreenState.Loading
                    zones.data.isEmpty() -> ScreenState.Empty(Unit)
                    else -> ScreenState.Content(zones.data)
                }
                result
            }.catch { emit(ScreenState.Error(Unit)) }.timeoutToError(Unit)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ScreenState.Loading)
}

/** S-10 구역 관리 · FR-04. 행 → S-11(편집), 하단 고정 [구역 추가] → S-11(추가). 뒤로 = S-08 */
@Composable
fun ZonesScreen(onBack: () -> Unit, onEdit: (String?) -> Unit) {
    val viewModel = appViewModel { container, _ -> ZonesViewModel(container) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    ZonesBody(state, onBack, onEdit)
}

/** S-10 화면 본체. 상태만 받아 그린다 (디버그 상태 갤러리에서도 쓴다). */
@Composable
fun ZonesBody(state: ZonesState, onBack: () -> Unit, onEdit: (String?) -> Unit) {
    Scaffold(
        containerColor = AppColors.White,
        topBar = { AppTopBar(title = stringResource(R.string.s10_title), navigation = TopBarNavigation.Back, onNavigate = onBack) },
        bottomBar = {
            PrimaryButton(
                text = stringResource(R.string.s08_add_zone),
                onClick = { onEdit(null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(Spacing.ScreenHorizontal),
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
                ScreenState.Loading -> DelayedLoading { SkeletonList(rows = 3) }
                is ScreenState.Empty -> EmptyState(
                    title = stringResource(R.string.s08_empty_nozones_title),
                    description = stringResource(R.string.s08_empty_nozones_desc),
                    icon = AppIcons.Safe,
                )
                is ScreenState.Error -> ErrorState(
                    title = stringResource(R.string.s08_error_title),
                    cause = stringResource(R.string.cause_timeout),
                    actionLabel = stringResource(R.string.cd_back),
                    onAction = onBack,
                )
                is ScreenState.Content -> current.data.forEach { zone ->
                    ZoneRow(zone, onClick = { onEdit(zone.id) })
                    HorizontalDivider(color = AppColors.Divider)
                }
            }
            Text(
                stringResource(R.string.s10_note),
                style = AppType.Caption,
                color = AppColors.Text2,
                modifier = Modifier.padding(horizontal = Spacing.ScreenHorizontal, vertical = Spacing.L),
            )
        }
    }
}

@Composable
private fun ZoneRow(zone: Zone, onClick: () -> Unit) {
    val safe = zone.type == ZoneType.Safe
    ListRow(onClick = onClick) {
        Icon(if (safe) AppIcons.Safe else AppIcons.Caution, contentDescription = null, tint = if (safe) AppColors.Safe else AppColors.Caution)
        Column(modifier = Modifier.weight(1f)) {
            Text(zone.name, style = AppType.Body, color = AppColors.Text)
            Text(
                stringResource(
                    R.string.s10_row_detail,
                    stringResource(if (safe) R.string.zone_safe else R.string.zone_caution),
                    radiusText(zone.radiusM),
                ),
                style = AppType.Caption,
                color = AppColors.Text2,
            )
        }
        Icon(AppIcons.NextDay, contentDescription = null, tint = AppColors.Text2)
    }
}
