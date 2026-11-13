package com.example.gpsplace.ui.screens.s15_permission

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import com.example.gpsplace.R
import com.example.gpsplace.location.PermissionState
import com.example.gpsplace.ui.components.AppIcons
import com.example.gpsplace.ui.components.AppTextButton
import com.example.gpsplace.ui.components.PrimaryButton
import com.example.gpsplace.ui.screens.s02_link.StepRow
import com.example.gpsplace.ui.theme.AppColors
import com.example.gpsplace.ui.theme.AppType
import com.example.gpsplace.ui.theme.Spacing

/**
 * S-15 위치 권한 안내 · 보호대상자 · NFR-05, FR-02.
 * [권한 허용하기] → 앱 사용 중 위치 권한 → 설정의 ‘항상 허용’ → 알림 권한 → S-16(기록 시작).
 * [나중에 하기]·뒤로 → S-16 권한 없음 상태(수집 안 함, UC-01 A2).
 */
@Composable
fun PermissionScreen(onFinished: () -> Unit) {
    val context = LocalContext.current
    val activity = context.findActivity()

    BackHandler(onBack = onFinished)

    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        onFinished()
    }

    fun askNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !PermissionState.notificationsEnabled(context)) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            onFinished()
        }
    }

    val backgroundLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        askNotifications()
    }

    val foregroundLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val granted = result.values.any { it }
        when {
            granted -> backgroundLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            // ‘다시 묻지 않음’ 상태면 요청 창이 뜨지 않으므로 앱 설정 화면을 연다
            activity != null && !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.ACCESS_FINE_LOCATION) ->
                openAppSettings(context)
            else -> onFinished()
        }
    }

    fun requestAll() {
        if (PermissionState.hasLocation(context)) {
            backgroundLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        } else {
            foregroundLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }

    Scaffold(containerColor = AppColors.White) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.ScreenHorizontal, vertical = Spacing.Section),
            verticalArrangement = Arrangement.spacedBy(Spacing.L),
        ) {
            Spacer(Modifier.height(Spacing.L))
            Icon(AppIcons.Recenter, contentDescription = null, tint = AppColors.Primary, modifier = Modifier.size(56.dp))
            Text(stringResource(R.string.s15_title), style = AppType.BigStatus, color = AppColors.Text)
            Text(stringResource(R.string.s15_desc), style = AppType.BodyProtected, color = AppColors.Text)
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.M)) {
                StepRow(1, stringResource(R.string.s15_step1), protectedUser = true)
                StepRow(2, stringResource(R.string.s15_step2), protectedUser = true)
            }
            Spacer(Modifier.height(Spacing.S))
            PrimaryButton(
                text = stringResource(R.string.s15_allow),
                onClick = ::requestAll,
                protectedUser = true,
                modifier = Modifier.fillMaxWidth(),
            )
            AppTextButton(text = stringResource(R.string.s15_later), onClick = onFinished, modifier = Modifier.fillMaxWidth())
            Text(stringResource(R.string.s15_later_note), style = AppType.BodyProtected, color = AppColors.Text2)
        }
    }
}

fun openAppSettings(context: Context) {
    context.startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}

fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
