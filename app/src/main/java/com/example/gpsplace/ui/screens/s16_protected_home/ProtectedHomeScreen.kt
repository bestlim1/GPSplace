package com.example.gpsplace.ui.screens.s16_protected_home

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.gpsplace.R
import com.example.gpsplace.ui.Formats
import com.example.gpsplace.ui.ScreenState
import com.example.gpsplace.ui.appViewModel
import com.example.gpsplace.ui.components.AppIcons
import com.example.gpsplace.ui.components.AppTopBar
import com.example.gpsplace.ui.components.DelayedLoading
import com.example.gpsplace.ui.components.EmptyState
import com.example.gpsplace.ui.components.ErrorState
import com.example.gpsplace.ui.components.ListRow
import com.example.gpsplace.ui.components.SettingsAction
import com.example.gpsplace.ui.components.StateBanner
import com.example.gpsplace.ui.theme.AppColors
import com.example.gpsplace.ui.theme.AppType
import com.example.gpsplace.ui.theme.Spacing
import java.text.NumberFormat

/**
 * S-16 위치 기록 (보호대상자 홈) · FR-02, FR-06, NFR-05. 상태 1개 + 주 행동 1개 (UI 보고서 5장).
 * 진입·설정에서 복귀할 때마다 권한·위치 서비스 상태를 다시 확인한다.
 */
@Composable
fun ProtectedHomeScreen(
    onOpenLinkManage: () -> Unit,
    onOpenSync: () -> Unit,
    onOpenPermission: () -> Unit,
    onLink: () -> Unit,
) {
    val viewModel = appViewModel { container, _ -> ProtectedHomeViewModel(container) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LifecycleResumeEffect(Unit) {
        viewModel.onResume(context.filesDir.usableSpace)
        onPauseOrDispose { }
    }
    ProtectedHomeBody(state, onOpenLinkManage, onOpenSync, onOpenPermission, onLink)
}

/** S-16 화면 본체. 상태만 받아 그린다 (디버그 상태 갤러리에서도 쓴다). */
@Composable
fun ProtectedHomeBody(
    state: RecordingState,
    onOpenLinkManage: () -> Unit,
    onOpenSync: () -> Unit,
    onOpenPermission: () -> Unit,
    onLink: () -> Unit,
) {
    val context = LocalContext.current
    Scaffold(
        containerColor = AppColors.White,
        topBar = {
            AppTopBar(
                title = stringResource(R.string.s16_title),
                actions = { if (state !is ScreenState.Empty) SettingsAction(onOpenLinkManage) },
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
                ScreenState.Loading -> DelayedLoading {
                    StatusCircle(searching = true)
                    Text(
                        stringResource(R.string.s16_loading_title),
                        style = AppType.BigStatus,
                        color = AppColors.Text,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.ScreenHorizontal),
                    )
                    Text(
                        stringResource(R.string.s16_loading_desc),
                        style = AppType.BodyProtected,
                        color = AppColors.Text2,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.ScreenHorizontal, vertical = Spacing.S),
                    )
                }
                is ScreenState.Empty -> EmptyState(
                    title = stringResource(R.string.s16_empty_title),
                    description = stringResource(R.string.s16_empty_desc),
                    icon = AppIcons.Paused,
                    actionLabel = stringResource(R.string.s16_link),
                    onAction = onLink,
                    protectedUser = true,
                )
                is ScreenState.Error -> RecordingErrorContent(current.cause, onOpenPermission) { intent ->
                    context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
                is ScreenState.Content -> {
                    if (current.partial == RecordingPartial.Offline) {
                        StateBanner(message = stringResource(R.string.s16_banner_offline), icon = AppIcons.CloudOff)
                    }
                    RecordingContent(current.data, onOpenSync)
                }
            }
        }
    }
}

@Composable
private fun StatusCircle(searching: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.Section),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .drawBehind {
                    drawCircle(
                        color = if (searching) AppColors.Missing else AppColors.Safe,
                        style = Stroke(
                            width = 3.dp.toPx(),
                            pathEffect = if (searching) PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx())) else null,
                        ),
                    )
                }
                .background(if (searching) AppColors.White else AppColors.Safe.copy(alpha = 0.15f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (searching) {
                CircularProgressIndicator(color = AppColors.Primary, modifier = Modifier.size(40.dp))
            } else {
                Icon(AppIcons.Recording, contentDescription = null, tint = AppColors.Safe, modifier = Modifier.size(48.dp))
            }
        }
    }
}

@Composable
private fun RecordingContent(data: RecordingData, onOpenSync: () -> Unit) {
    StatusCircle(searching = false)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.ScreenHorizontal),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.S),
    ) {
        Text(stringResource(R.string.status_recording), style = AppType.BigStatus, color = AppColors.Text)
        Text(
            stringResource(R.string.s16_last_collected, Formats.timeOrDayTime(data.lastCollectedAt, System.currentTimeMillis())),
            style = AppType.BodyProtected,
            color = AppColors.Text2,
        )
    }
    Spacer(Modifier.height(Spacing.Section))
    HorizontalDivider(color = AppColors.Divider)
    ListRow(onClick = onOpenSync) {
        Icon(
            if (data.pendingCount > 0) AppIcons.Upload else AppIcons.Check,
            contentDescription = null,
            tint = if (data.pendingCount > 0) AppColors.Missing else AppColors.Safe,
        )
        Text(
            if (data.pendingCount > 0) stringResource(R.string.s16_pending, data.pendingCount) else stringResource(R.string.s16_all_sent),
            style = AppType.BodyProtected,
            color = AppColors.Text,
            modifier = Modifier.weight(1f),
        )
        Icon(AppIcons.NextDay, contentDescription = null, tint = AppColors.Text2)
    }
    HorizontalDivider(color = AppColors.Divider)
    ListRow {
        Icon(AppIcons.Person, contentDescription = null, tint = AppColors.Primary)
        Text(stringResource(R.string.s16_guardian_count), style = AppType.BodyProtected, color = AppColors.Text)
    }
    HorizontalDivider(color = AppColors.Divider)
    Text(
        stringResource(R.string.s16_note),
        style = AppType.BodyProtected,
        color = AppColors.Text2,
        modifier = Modifier.padding(horizontal = Spacing.ScreenHorizontal, vertical = Spacing.Section),
    )
}

@Composable
private fun RecordingErrorContent(error: RecordingError, onOpenPermission: () -> Unit, startActivity: (Intent) -> Unit) {
    val lastNormal = error.lastNormalAt?.let {
        val now = System.currentTimeMillis()
        val time = Formats.timeOrDayTime(it, now)
        stringResource(R.string.s16_last_normal, if (time.length <= 5) "${stringResource(R.string.today)} $time" else time)
    }
    when (error.kind) {
        RecordingErrorKind.Permission -> ErrorState(
            title = stringResource(R.string.s16_error_title),
            cause = stringResource(R.string.s16_cause_permission),
            detail = lastNormal,
            actionLabel = stringResource(R.string.s16_fix_permission),
            onAction = onOpenPermission,
            protectedUser = true,
        )
        RecordingErrorKind.LocationOff -> ErrorState(
            title = stringResource(R.string.s16_error_title),
            cause = stringResource(R.string.s16_cause_service),
            detail = lastNormal,
            actionLabel = stringResource(R.string.s16_turn_on_location),
            onAction = { startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) },
            protectedUser = true,
        )
        RecordingErrorKind.Storage -> ErrorState(
            title = stringResource(R.string.s16_storage_title),
            cause = stringResource(R.string.s16_cause_storage),
            detail = stringResource(R.string.s16_storage_kept, NumberFormat.getIntegerInstance().format(error.storedCount)),
            actionLabel = stringResource(R.string.s16_check_storage),
            onAction = { startActivity(Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)) },
            protectedUser = true,
        )
    }
}
