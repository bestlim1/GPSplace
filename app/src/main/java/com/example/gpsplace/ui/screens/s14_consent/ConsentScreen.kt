package com.example.gpsplace.ui.screens.s14_consent

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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gpsplace.AppContainer
import com.example.gpsplace.R
import com.example.gpsplace.location.PermissionState
import com.example.gpsplace.nav.Routes
import com.example.gpsplace.ui.appViewModel
import com.example.gpsplace.ui.components.AppCard
import com.example.gpsplace.ui.components.AppTopBar
import com.example.gpsplace.ui.components.BannerTone
import com.example.gpsplace.ui.components.InfoRow
import com.example.gpsplace.ui.components.PrimaryButton
import com.example.gpsplace.ui.components.SecondaryButton
import com.example.gpsplace.ui.components.StateBanner
import com.example.gpsplace.ui.components.TopBarNavigation
import com.example.gpsplace.ui.theme.AppColors
import com.example.gpsplace.ui.theme.AppType
import com.example.gpsplace.ui.theme.Spacing
import kotlinx.coroutines.launch

enum class ConsentError { None, Expired, Offline }

class ConsentViewModel(private val container: AppContainer, saved: SavedStateHandle) : ViewModel() {
    val code: String = saved.get<String>(Routes.ARG_CODE).orEmpty()
    var busy by mutableStateOf(false)
        private set
    var error by mutableStateOf(ConsentError.None)
        private set

    /** [동의하고 연결]: links 생성 → S-15 */
    fun agree(context: android.content.Context, onLinked: () -> Unit) {
        if (busy) return
        busy = true
        viewModelScope.launch {
            val permissions = PermissionState.read(context)
            error = try {
                container.links.consent(code, permissions.location, permissions.locationServiceOn)
                onLinked()
                ConsentError.None
            } catch (e: Exception) {
                if (container.network.online.value) ConsentError.Expired else ConsentError.Offline
            }
            busy = false
        }
    }

    /** [거부]: 코드 denied → S-01 */
    fun deny(onDenied: () -> Unit) {
        if (busy) return
        busy = true
        viewModelScope.launch {
            container.links.deny(code)
            busy = false
            onDenied()
        }
    }
}

/** S-14 연결 동의 · 보호대상자 · FR-01. 위치 좌표·수집 시각 외의 정보는 수집하지 않는다 */
@Composable
fun ConsentScreen(onBack: () -> Unit, onLinked: () -> Unit, onDenied: () -> Unit) {
    val vm = appViewModel { container, saved -> ConsentViewModel(container, saved) }
    val context = androidx.compose.ui.platform.LocalContext.current
    ConsentBody(vm.error, vm.busy, onAgree = { vm.agree(context, onLinked) }, onDeny = { vm.deny(onDenied) }, onBack = onBack)
}

/** S-14 화면 본체. 상태만 받아 그린다 (디버그 상태 갤러리에서도 쓴다). */
@Composable
fun ConsentBody(error: ConsentError, busy: Boolean, onAgree: () -> Unit, onDeny: () -> Unit, onBack: () -> Unit) {
    Scaffold(
        containerColor = AppColors.White,
        topBar = { AppTopBar(title = stringResource(R.string.s14_title), navigation = TopBarNavigation.Back, onNavigate = onBack) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            if (error != ConsentError.None) {
                StateBanner(
                    message = stringResource(R.string.s14_error_title) + " · " + stringResource(
                        R.string.error_cause,
                        stringResource(if (error == ConsentError.Offline) R.string.cause_offline else R.string.s14_cause_expired),
                    ),
                    tone = BannerTone.Error,
                )
            }
            Column(
                modifier = Modifier.padding(horizontal = Spacing.ScreenHorizontal, vertical = Spacing.Section),
                verticalArrangement = Arrangement.spacedBy(Spacing.L),
            ) {
                Text(stringResource(R.string.s14_heading), style = AppType.Title, color = AppColors.Text)
                AppCard {
                    Column {
                        InfoRow(stringResource(R.string.s14_info), stringResource(R.string.s14_info_value), protectedUser = true)
                        InfoRow(stringResource(R.string.s14_period), stringResource(R.string.s14_period_value), protectedUser = true)
                        InfoRow(stringResource(R.string.s14_viewer), stringResource(R.string.s14_viewer_value), protectedUser = true)
                        InfoRow(stringResource(R.string.s14_release), stringResource(R.string.s14_release_value), protectedUser = true)
                    }
                }
                Text(stringResource(R.string.s14_no_health), style = AppType.BodyProtected, color = AppColors.Text2)
                PrimaryButton(
                    text = stringResource(R.string.s14_agree),
                    onClick = onAgree,
                    enabled = !busy,
                    protectedUser = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                SecondaryButton(
                    text = stringResource(R.string.s14_deny),
                    onClick = onDeny,
                    enabled = !busy,
                    protectedUser = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
