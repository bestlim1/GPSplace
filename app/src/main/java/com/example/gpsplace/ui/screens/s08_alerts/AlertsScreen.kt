package com.example.gpsplace.ui.screens.s08_alerts

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.gpsplace.ui.screens.s15_permission.findActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.gpsplace.AppContainer
import com.example.gpsplace.R
import com.example.gpsplace.data.local.Role
import com.example.gpsplace.data.remote.Fetched
import com.example.gpsplace.data.remote.RemoteAlert
import com.example.gpsplace.domain.AlertType
import com.example.gpsplace.domain.SeoulTime
import com.example.gpsplace.domain.Zone
import com.example.gpsplace.location.PermissionState
import com.example.gpsplace.ui.Formats
import com.example.gpsplace.ui.ScreenState
import com.example.gpsplace.ui.appViewModel
import com.example.gpsplace.ui.components.AppIcons
import com.example.gpsplace.ui.components.AppTextButton
import com.example.gpsplace.ui.components.AppTopBar
import com.example.gpsplace.ui.components.DelayedLoading
import com.example.gpsplace.ui.components.EmptyState
import com.example.gpsplace.ui.components.ErrorState
import com.example.gpsplace.ui.components.ListRow
import com.example.gpsplace.ui.components.SkeletonList
import com.example.gpsplace.ui.components.StateBanner
import com.example.gpsplace.ui.dayGroupText
import com.example.gpsplace.ui.theme.AppColors
import com.example.gpsplace.ui.theme.AppType
import com.example.gpsplace.ui.theme.Spacing
import com.example.gpsplace.ui.timeoutToError
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

enum class AlertsEmpty { NoZones, NoAlerts }

typealias AlertsState = ScreenState<List<RemoteAlert>, AlertsEmpty, Unit, Unit>

@OptIn(ExperimentalCoroutinesApi::class)
class AlertsViewModel(private val container: AppContainer) : ViewModel() {
    private val retry = MutableStateFlow(0)
    private val _notificationsOn = MutableStateFlow(true)

    /** 휴대폰 알림 권한 (NFR-05) — 화면 열 때·앱 복귀 때 다시 확인 */
    val notificationsOn: StateFlow<Boolean> = _notificationsOn

    val state: StateFlow<AlertsState> = combine(
        container.settings.link.map { if (it.role == Role.Guardian && it.linkActive) it.linkId else null }.distinctUntilChanged(),
        retry,
    ) { id, _ -> id }
        .flatMapLatest { linkId ->
            if (linkId == null) {
                flowOf<AlertsState>(ScreenState.Empty(AlertsEmpty.NoZones))
            } else {
                combine(
                    container.tracks.observeAlerts(linkId),
                    container.zones.observeZones(linkId).catch { emit(Fetched(emptyList<Zone>(), true)) },
                    container.network.online,
                ) { alerts, zones, online ->
                    val result: AlertsState = when {
                        alerts.data.isEmpty() && alerts.fromCache && online -> ScreenState.Loading
                        alerts.data.isNotEmpty() -> ScreenState.Content(alerts.data)
                        zones.data.isEmpty() -> ScreenState.Empty(AlertsEmpty.NoZones)
                        else -> ScreenState.Empty(AlertsEmpty.NoAlerts)
                    }
                    result
                }.catch { emit(ScreenState.Error(Unit)) }.timeoutToError(Unit)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ScreenState.Loading)

    fun refreshPermission(context: android.content.Context) {
        _notificationsOn.value = PermissionState.notificationsEnabled(context)
    }

    fun retry() {
        retry.value++
    }
}

/** S-08 알림 내역 · FR-04, NFR-05. 날짜 그룹(오늘·어제), 행 → S-09 */
@Composable
fun AlertsScreen(onOpenZones: () -> Unit, onOpenAlert: (String) -> Unit, onAddZone: () -> Unit) {
    val viewModel = appViewModel { container, _ -> AlertsViewModel(container) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val notificationsOn by viewModel.notificationsOn.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LifecycleResumeEffect(Unit) {
        viewModel.refreshPermission(context)
        onPauseOrDispose { }
    }

    fun openNotificationSettings() {
        context.startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    // Android 13+: 먼저 권한 요청 창을 띄우고, ‘다시 묻지 않음’이면 앱 알림 설정을 연다
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.refreshPermission(context)
        val activity = context.findActivity()
        if (!granted && activity != null &&
            !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.POST_NOTIFICATIONS)
        ) {
            openNotificationSettings()
        }
    }

    val onNotificationAction = {
        val runtimeDenied = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (runtimeDenied) permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) else openNotificationSettings()
    }
    AlertsBody(state, notificationsOn, onOpenZones, onOpenAlert, onAddZone, viewModel::retry, onNotificationAction)
}

/** S-08 화면 본체. 상태만 받아 그린다 (디버그 상태 갤러리에서도 쓴다). */
@Composable
fun AlertsBody(
    state: AlertsState,
    notificationsOn: Boolean,
    onOpenZones: () -> Unit,
    onOpenAlert: (String) -> Unit,
    onAddZone: () -> Unit,
    onRetry: () -> Unit,
    onNotificationAction: () -> Unit,
) {
    Scaffold(
        containerColor = AppColors.White,
        topBar = {
            AppTopBar(
                title = stringResource(R.string.s08_title),
                actions = { AppTextButton(text = stringResource(R.string.s08_action_zones), onClick = onOpenZones) },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            if (!notificationsOn) {
                StateBanner(
                    message = stringResource(R.string.s08_banner_notifications),
                    actionLabel = stringResource(R.string.s08_open_settings),
                    onAction = onNotificationAction,
                )
            }
            when (val current = state) {
                ScreenState.Loading -> DelayedLoading { SkeletonList(rows = 4) }
                is ScreenState.Empty -> when (current.reason) {
                    AlertsEmpty.NoZones -> EmptyState(
                        title = stringResource(R.string.s08_empty_nozones_title),
                        description = stringResource(R.string.s08_empty_nozones_desc),
                        icon = AppIcons.Safe,
                        actionLabel = stringResource(R.string.s08_add_zone),
                        onAction = onAddZone,
                    )
                    AlertsEmpty.NoAlerts -> EmptyState(
                        title = stringResource(R.string.s08_empty_noalerts_title),
                        description = null,
                        icon = AppIcons.TabAlerts,
                    )
                }
                is ScreenState.Error -> ErrorState(
                    title = stringResource(R.string.s08_error_title),
                    cause = stringResource(R.string.cause_timeout),
                    actionLabel = stringResource(R.string.action_retry),
                    onAction = onRetry,
                )
                is ScreenState.Content -> AlertList(current.data, onOpenAlert)
            }
        }
    }
}

@Composable
private fun AlertList(alerts: List<RemoteAlert>, onOpenAlert: (String) -> Unit) {
    val today = SeoulTime.today(System.currentTimeMillis())
    alerts.groupBy { SeoulTime.today(it.createdAt) }.toSortedMap(compareByDescending<LocalDate> { it }).forEach { (date, dayAlerts) ->
        Text(
            dayGroupText(date, today),
            style = AppType.Caption,
            color = AppColors.Text2,
            modifier = Modifier.padding(start = Spacing.ScreenHorizontal, end = Spacing.ScreenHorizontal, top = Spacing.L, bottom = Spacing.Xs),
        )
        dayAlerts.forEach { alert ->
            AlertRow(alert, onClick = { onOpenAlert(alert.id) })
            HorizontalDivider(color = AppColors.Divider)
        }
    }
}

@Composable
fun AlertRow(alert: RemoteAlert, onClick: () -> Unit) {
    val exit = alert.type == AlertType.SafeExit
    ListRow(onClick = onClick) {
        Icon(
            if (exit) AppIcons.Safe else AppIcons.Caution,
            contentDescription = null,
            tint = if (exit) AppColors.Safe else AppColors.Caution,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                stringResource(if (exit) R.string.alert_safe_exit else R.string.alert_caution_enter),
                style = AppType.Body,
                color = AppColors.Text,
            )
            Text(
                stringResource(R.string.alert_row_detail, alert.zoneName, Formats.hm(alert.p2.t)),
                style = AppType.Caption,
                color = AppColors.Text2,
            )
        }
        Icon(AppIcons.NextDay, contentDescription = null, tint = AppColors.Text2)
    }
}
