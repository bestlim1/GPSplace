package com.example.gpsplace.ui.screens.s12_link_manage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.gpsplace.AppContainer
import com.example.gpsplace.R
import com.example.gpsplace.data.local.Role
import com.example.gpsplace.data.repo.LinkState
import com.example.gpsplace.ui.Formats
import com.example.gpsplace.ui.appViewModel
import com.example.gpsplace.ui.components.AppCard
import com.example.gpsplace.ui.components.AppTopBar
import com.example.gpsplace.ui.components.ConfirmDialog
import com.example.gpsplace.ui.components.DangerButton
import com.example.gpsplace.ui.components.InfoRow
import com.example.gpsplace.ui.components.LinkedChip
import com.example.gpsplace.ui.components.TopBarNavigation
import com.example.gpsplace.ui.theme.AppColors
import com.example.gpsplace.ui.theme.AppType
import com.example.gpsplace.ui.theme.Spacing
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LinkManageUi(
    val role: Role? = null,
    val active: Boolean = false,
    val since: Long? = null,
    val lastReceived: Long? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class LinkManageViewModel(private val container: AppContainer) : ViewModel() {
    val state: StateFlow<LinkManageUi> = container.settings.link.flatMapLatest { local ->
        val linkId = local.linkId
        if (linkId == null || !local.linkActive) {
            flowOf(LinkManageUi(role = local.role))
        } else {
            combine(
                container.links.observeLink(linkId).onStart { emit(LinkState.Failed(IllegalStateException())) }.catch { },
                container.settings.lastUploadAt,
            ) { linkState, lastUpload ->
                val link = (linkState as? LinkState.Active)?.link
                LinkManageUi(
                    role = local.role,
                    active = linkState !is LinkState.Released,
                    since = link?.createdAt,
                    // 마지막 수신: 보호자는 받은 최신 위치 시각, 보호대상자는 마지막 전송 시각
                    lastReceived = if (local.role == Role.Guardian) link?.lastPoint?.t else lastUpload,
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LinkManageUi())

    /** D-01 [연결 해제]: links.status = released, 보호대상자는 기록 중지 */
    fun release(onDone: () -> Unit) {
        viewModelScope.launch {
            val linkId = container.settings.currentLink().linkId
            container.recording.stop()
            if (linkId != null) container.links.release(linkId) else container.links.onReleased()
            onDone()
        }
    }
}

/** S-12 연결 관리 · 공통 · FR-01, NFR-04. 뒤로 = 들어온 홈 */
@Composable
fun LinkManageScreen(onBack: () -> Unit, onReleased: () -> Unit) {
    val viewModel = appViewModel { container, _ -> LinkManageViewModel(container) }
    val ui by viewModel.state.collectAsStateWithLifecycle()
    var confirm by rememberSaveable { mutableStateOf(false) }
    LinkManageBody(
        ui = ui,
        onBack = onBack,
        showConfirm = confirm,
        onRequestRelease = { confirm = true },
        onDismissConfirm = { confirm = false },
        onConfirmRelease = {
            confirm = false
            viewModel.release(onReleased)
        },
    )
}

/** S-12 화면 본체 (+ D-01). 상태만 받아 그린다 (디버그 상태 갤러리에서도 쓴다). */
@Composable
fun LinkManageBody(
    ui: LinkManageUi,
    onBack: () -> Unit,
    showConfirm: Boolean,
    onRequestRelease: () -> Unit,
    onDismissConfirm: () -> Unit,
    onConfirmRelease: () -> Unit,
) {
    val now = System.currentTimeMillis()

    Scaffold(
        containerColor = AppColors.White,
        topBar = { AppTopBar(title = stringResource(R.string.s12_title), navigation = TopBarNavigation.Back, onNavigate = onBack) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.ScreenHorizontal, vertical = Spacing.L),
            verticalArrangement = Arrangement.spacedBy(Spacing.L),
        ) {
            val protectedUser = ui.role == Role.Protected
            AppCard {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    if (ui.active) LinkedChip(stringResource(R.string.status_linked))
                    InfoRow(
                        stringResource(R.string.s12_device),
                        stringResource(if (protectedUser) R.string.s12_device_guardian else R.string.s12_device_protected),
                        protectedUser = protectedUser,
                    )
                    InfoRow(stringResource(R.string.s12_since), ui.since?.let { Formats.fullDay(it) } ?: "–", protectedUser = protectedUser)
                    InfoRow(
                        stringResource(R.string.s12_last_received),
                        ui.lastReceived?.let { Formats.timeOrDayTime(it, now) } ?: "–",
                        protectedUser = protectedUser,
                    )
                }
            }
            Text(
                stringResource(R.string.s12_collect_info),
                style = if (protectedUser) AppType.BodyProtected else AppType.Body,
                color = AppColors.Text2,
            )
            Text(
                stringResource(R.string.s12_release_note),
                style = if (protectedUser) AppType.BodyProtected else AppType.Body,
                color = AppColors.Text,
                modifier = Modifier.padding(top = Spacing.Section),
            )
            DangerButton(
                text = stringResource(R.string.s12_release),
                onClick = onRequestRelease,
                enabled = ui.active,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    if (showConfirm) {
        ConfirmDialog(
            title = stringResource(R.string.d01_title),
            message = stringResource(R.string.d01_message),
            dismissLabel = stringResource(R.string.action_cancel),
            confirmLabel = stringResource(R.string.s12_release),
            destructive = true,
            onDismiss = onDismissConfirm,
            onConfirm = onConfirmRelease,
        )
    }
}
