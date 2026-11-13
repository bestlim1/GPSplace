package com.example.gpsplace.ui.screens.s02_link

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.gpsplace.R
import com.example.gpsplace.location.PermissionState
import com.example.gpsplace.ui.appViewModel
import com.example.gpsplace.ui.components.AppCard
import com.example.gpsplace.ui.components.AppIcons
import com.example.gpsplace.ui.components.AppTopBar
import com.example.gpsplace.ui.components.ConfirmDialog
import com.example.gpsplace.ui.components.DelayedLoading
import com.example.gpsplace.ui.components.ErrorState
import com.example.gpsplace.ui.components.PrimaryButton
import com.example.gpsplace.ui.components.SecondaryButton
import com.example.gpsplace.ui.components.TopBarNavigation
import com.example.gpsplace.ui.theme.AppColors
import com.example.gpsplace.ui.theme.AppType
import com.example.gpsplace.ui.theme.Corners
import com.example.gpsplace.ui.theme.Sizes
import com.example.gpsplace.ui.theme.Spacing
import kotlinx.coroutines.delay

/** “482913” → “482 913” (3+3으로 나눠 옮겨 적기 쉽게, 밀러의 법칙) */
fun groupCode(code: String) = if (code.length == 6) "${code.take(3)} ${code.drop(3)}" else code

/**
 * S-02 연결 요청 · 보호자 · FR-01.
 * 뒤로: 대기 중이면 D-04 확인 → [onExit] (최초 연결이면 S-01, 재연결이면 S-03).
 */
@Composable
fun LinkRequestScreen(onExit: () -> Unit, onLinked: () -> Unit, onHome: () -> Unit) {
    val viewModel = appViewModel { container, _ -> LinkRequestViewModel(container) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmCancel by rememberSaveable { mutableStateOf(false) }
    val waiting = state as? LinkRequestUi.Waiting
    val context = LocalContext.current

    // 보호자 기기는 구역 알림을 받으려면 알림 권한이 필요하다 (Android 13+, NFR-05). 연결 완료 후 한 번 묻는다.
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onLinked() }
    val startViewing = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !PermissionState.notificationsEnabled(context)) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            onLinked()
        }
    }

    BackHandler(enabled = waiting != null) { confirmCancel = true }
    LinkRequestBody(
        state = state,
        onBack = { if (waiting != null) confirmCancel = true else onExit() },
        onNewCode = viewModel::newCode,
        onCountdownFinished = viewModel::onCountdownFinished,
        onStartViewing = startViewing,
        onHome = onHome,
        showCancelConfirm = confirmCancel,
        onDismissCancel = { confirmCancel = false },
        onConfirmCancel = {
            confirmCancel = false
            viewModel.cancelRequest(onExit)
        },
    )
}

/** S-02 화면 본체 (+ D-04). 상태만 받아 그린다 (디버그 상태 갤러리에서도 쓴다). */
@Composable
fun LinkRequestBody(
    state: LinkRequestUi,
    onBack: () -> Unit,
    onNewCode: () -> Unit,
    onCountdownFinished: () -> Unit,
    onStartViewing: () -> Unit,
    onHome: () -> Unit,
    showCancelConfirm: Boolean,
    onDismissCancel: () -> Unit,
    onConfirmCancel: () -> Unit,
) {
    val waiting = state as? LinkRequestUi.Waiting
    Scaffold(
        containerColor = AppColors.White,
        topBar = {
            AppTopBar(
                title = stringResource(R.string.s02_title),
                navigation = TopBarNavigation.Back,
                onNavigate = onBack,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.ScreenHorizontal, vertical = Spacing.L),
            verticalArrangement = Arrangement.spacedBy(Spacing.L),
        ) {
            when (val current = state) {
                LinkRequestUi.Loading -> {
                    Steps()
                    CodeBox(code = null, expiresAt = 0, onExpired = {})
                }
                is LinkRequestUi.Waiting -> {
                    Steps()
                    CodeBox(code = current.code, expiresAt = current.expiresAt, onExpired = onCountdownFinished)
                    WaitingBox()
                    Spacer(Modifier.height(Spacing.S))
                    SecondaryButton(
                        text = stringResource(R.string.s02_new_code),
                        onClick = onNewCode,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                LinkRequestUi.Linked -> Done(onStartViewing)
                is LinkRequestUi.Failed -> ErrorState(
                    title = stringResource(R.string.s02_error_title),
                    cause = stringResource(if (current.denied) R.string.s02_cause_denied else R.string.s02_cause_expired),
                    detail = stringResource(R.string.s02_error_detail),
                    actionLabel = stringResource(R.string.s02_new_code),
                    onAction = onNewCode,
                    secondaryActionLabel = stringResource(R.string.s02_go_home),
                    onSecondaryAction = onHome,
                )
                is LinkRequestUi.IssueError -> ErrorState(
                    title = stringResource(R.string.s02_issue_failed),
                    cause = stringResource(if (current.offline) R.string.cause_offline else R.string.cause_timeout),
                    actionLabel = stringResource(R.string.action_retry),
                    onAction = onNewCode,
                )
            }
        }
    }

    if (showCancelConfirm && waiting != null) {
        ConfirmDialog(
            title = stringResource(R.string.d04_title),
            message = stringResource(R.string.d04_message, groupCode(waiting.code)),
            dismissLabel = stringResource(R.string.d04_keep),
            confirmLabel = stringResource(R.string.d04_cancel),
            onDismiss = onDismissCancel,
            onConfirm = onConfirmCancel,
        )
    }
}

@Composable
private fun Steps() {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
        StepRow(1, stringResource(R.string.s02_step1))
        StepRow(2, stringResource(R.string.s02_step2))
    }
}

@Composable
fun StepRow(number: Int, text: String, protectedUser: Boolean = false) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.M), verticalAlignment = Alignment.Top) {
        Box(
            Modifier
                .size(Sizes.Icon)
                .background(AppColors.Primary, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(number.toString(), style = AppType.Caption, color = AppColors.White)
        }
        Text(text, style = if (protectedUser) AppType.BodyProtected else AppType.Body, color = AppColors.Text)
    }
}

/** 코드 6자리 3+3 + “남은 시간 09:12” + “유효 10분”. 만드는 중이면 코드 자리를 회색으로 */
@Composable
private fun CodeBox(code: String?, expiresAt: Long, onExpired: () -> Unit) {
    AppCard {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.S),
        ) {
            if (code == null) {
                Box(
                    Modifier
                        .width(180.dp)
                        .height(40.dp)
                        .background(AppColors.Divider, RoundedCornerShape(Corners.Button))
                )
                DelayedLoading {
                    Text(stringResource(R.string.s02_loading), style = AppType.Caption, color = AppColors.Text2)
                }
            } else {
                val spoken = code.toCharArray().joinToString(" ")
                Text(
                    groupCode(code),
                    style = AppType.Code,
                    color = AppColors.Text,
                    modifier = Modifier.semantics { contentDescription = spoken },
                )
                Countdown(expiresAt, onExpired)
                Text(stringResource(R.string.s02_valid), style = AppType.Caption, color = AppColors.Text2)
            }
        }
    }
}

@Composable
private fun Countdown(expiresAt: Long, onExpired: () -> Unit) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(expiresAt) {
        while (true) {
            now = System.currentTimeMillis()
            if (now >= expiresAt) {
                onExpired()
                break
            }
            delay(1_000)
        }
    }
    val remainingSec = ((expiresAt - now).coerceAtLeast(0) / 1000).toInt()
    val text = "%02d:%02d".format(remainingSec / 60, remainingSec % 60)
    Text(stringResource(R.string.s02_remaining, text), style = AppType.Body, color = AppColors.Text)
}

@Composable
private fun WaitingBox() {
    AppCard {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.M), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(color = AppColors.Primary, modifier = Modifier.size(Sizes.Icon), strokeWidth = Sizes.Stroke)
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.Xs)) {
                Text(stringResource(R.string.s02_waiting_title), style = AppType.Body, color = AppColors.Text)
                Text(stringResource(R.string.s02_waiting_desc), style = AppType.Caption, color = AppColors.Text2)
            }
        }
    }
}

@Composable
private fun Done(onLinked: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.Section),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.M),
    ) {
        Icon(AppIcons.Check, contentDescription = null, tint = AppColors.Safe, modifier = Modifier.size(64.dp))
        Text(stringResource(R.string.s02_done_title), style = AppType.BigStatus, color = AppColors.Text)
        Text(stringResource(R.string.s02_done_desc), style = AppType.Body, color = AppColors.Text2, textAlign = TextAlign.Center)
        Spacer(Modifier.height(Spacing.S))
        PrimaryButton(text = stringResource(R.string.s02_start), onClick = onLinked, modifier = Modifier.fillMaxWidth())
    }
}
