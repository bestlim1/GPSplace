package com.example.gpsplace.ui.screens.s17_sync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.gpsplace.AppContainer
import com.example.gpsplace.R
import com.example.gpsplace.data.local.HourCount
import com.example.gpsplace.data.repo.UploadProgress
import com.example.gpsplace.domain.SeoulTime
import com.example.gpsplace.sync.UploadWorker
import com.example.gpsplace.ui.Formats
import com.example.gpsplace.ui.appViewModel
import com.example.gpsplace.ui.components.AppCard
import com.example.gpsplace.ui.components.AppIcons
import com.example.gpsplace.ui.components.AppTopBar
import com.example.gpsplace.ui.components.EmptyState
import com.example.gpsplace.ui.components.ErrorState
import com.example.gpsplace.ui.components.ListRow
import com.example.gpsplace.ui.components.StateBanner
import com.example.gpsplace.ui.components.TopBarNavigation
import com.example.gpsplace.ui.theme.AppColors
import com.example.gpsplace.ui.theme.AppType
import com.example.gpsplace.ui.theme.Spacing
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class SyncUi(
    val pending: Int,
    val sentToday: Int,
    val lastSentAt: Long?,
    val sentByHour: List<HourCount>,
    val pendingByHour: List<HourCount>,
    val progress: UploadProgress?,
    val failed: Boolean,
    val online: Boolean,
    val loaded: Boolean = true,
)

@OptIn(ExperimentalCoroutinesApi::class)
class SyncViewModel(private val container: AppContainer) : ViewModel() {
    private val dao = container.database.pointDao()
    private val today = SeoulTime.dayKey(container.clock())

    private val counts = combine(
        dao.observeUnsyncedCount(),
        dao.observeDaySyncedCount(today),
        dao.observeLastSyncedAt(),
        dao.observeSyncedByHour(today),
        dao.observeUnsyncedByHour(),
    ) { pending, sentToday, lastSentAt, sentByHour, pendingByHour ->
        SyncUi(pending, sentToday, lastSentAt, sentByHour, pendingByHour, null, failed = false, online = true)
    }

    val state: StateFlow<SyncUi> = combine(
        counts,
        container.sync.progress,
        container.settings.lastUploadError,
        container.network.online,
    ) { base, progress, error, online ->
        base.copy(progress = progress, failed = error != null && base.pending > 0, online = online)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        SyncUi(0, 0, null, emptyList(), emptyList(), null, failed = false, online = true, loaded = false),
    )

    fun retryNow(context: android.content.Context) = UploadWorker.enqueue(context, now = true)
}

/** S-17 전송 상태 · 보호대상자 · FR-06, NFR-03 */
@Composable
fun SyncScreen(onBack: () -> Unit) {
    val viewModel = appViewModel { container, _ -> SyncViewModel(container) }
    val ui by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    SyncBody(ui, onBack, onRetryNow = { viewModel.retryNow(context) })
}

/** S-17 화면 본체. 상태만 받아 그린다 (디버그 상태 갤러리에서도 쓴다). */
@Composable
fun SyncBody(ui: SyncUi, onBack: () -> Unit, onRetryNow: () -> Unit) {
    Scaffold(
        containerColor = AppColors.White,
        topBar = { AppTopBar(title = stringResource(R.string.s17_title), navigation = TopBarNavigation.Back, onNavigate = onBack) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            when {
                !ui.loaded -> Unit
                ui.progress != null -> Sending(ui.progress)
                ui.sentToday == 0 && ui.pending == 0 -> EmptyState(
                    title = stringResource(R.string.s17_empty_title),
                    description = stringResource(R.string.s17_empty_desc),
                    icon = AppIcons.Upload,
                    protectedUser = true,
                )
                !ui.online && ui.pending > 0 -> {
                    StateBanner(
                        message = stringResource(R.string.s17_banner_offline, ui.pending),
                        baseTime = stringResource(R.string.s17_banner_offline_detail),
                        icon = AppIcons.CloudOff,
                    )
                    HourGroup(stringResource(R.string.s17_pending_group), ui.pendingByHour)
                    HourGroup(stringResource(R.string.s17_today_group), ui.sentByHour)
                }
                ui.failed -> ErrorState(
                    title = stringResource(R.string.s17_error_title),
                    cause = stringResource(R.string.cause_timeout),
                    detail = stringResource(R.string.s17_error_kept, ui.pending),
                    actionLabel = stringResource(R.string.s17_retry_now),
                    onAction = onRetryNow,
                    protectedUser = true,
                )
                else -> {
                    Summary(ui)
                    if (ui.pendingByHour.isNotEmpty()) HourGroup(stringResource(R.string.s17_pending_group), ui.pendingByHour)
                    HourGroup(stringResource(R.string.s17_today_group), ui.sentByHour)
                }
            }
        }
    }
}

@Composable
private fun Sending(progress: UploadProgress) {
    Column(
        modifier = Modifier.padding(horizontal = Spacing.ScreenHorizontal, vertical = Spacing.Section),
        verticalArrangement = Arrangement.spacedBy(Spacing.M),
    ) {
        Text(stringResource(R.string.s17_sending, progress.sent, progress.total), style = AppType.Title, color = AppColors.Text)
        LinearProgressIndicator(
            progress = { if (progress.total == 0) 0f else progress.sent.toFloat() / progress.total },
            color = AppColors.Primary,
            trackColor = AppColors.Surface,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(stringResource(R.string.s17_no_resend), style = AppType.BodyProtected, color = AppColors.Text2)
    }
}

@Composable
private fun Summary(ui: SyncUi) {
    Column(Modifier.padding(Spacing.ScreenHorizontal)) {
        AppCard {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                Text(stringResource(R.string.s17_pending, ui.pending), style = AppType.BodyProtected, color = AppColors.Text)
                Text(stringResource(R.string.s17_sent_today, ui.sentToday), style = AppType.BodyProtected, color = AppColors.Text)
                ui.lastSentAt?.let {
                    Text(
                        stringResource(R.string.s17_last_sent, Formats.timeOrDayTime(it, System.currentTimeMillis())),
                        style = AppType.BodyProtected,
                        color = AppColors.Text2,
                    )
                }
            }
        }
    }
}

@Composable
private fun HourGroup(title: String, rows: List<HourCount>) {
    if (rows.isEmpty()) return
    Text(
        title,
        style = AppType.Title,
        color = AppColors.Text,
        modifier = Modifier.padding(start = Spacing.ScreenHorizontal, end = Spacing.ScreenHorizontal, top = Spacing.Section, bottom = Spacing.S),
    )
    rows.forEach { row ->
        ListRow {
            Text(
                stringResource(R.string.s17_hour_row, Formats.hm(row.firstT), Formats.hm(row.lastT), row.count),
                style = AppType.BodyProtected,
                color = AppColors.Text,
            )
        }
        HorizontalDivider(color = AppColors.Divider)
    }
}
