package com.example.gpsplace

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.gpsplace.data.local.Role
import com.example.gpsplace.debug.debugStartRoute
import com.example.gpsplace.nav.AppNavHost
import com.example.gpsplace.nav.Routes
import com.example.gpsplace.notify.NotificationChannels
import com.example.gpsplace.ui.theme.AppColors
import com.example.gpsplace.ui.theme.GPSplaceTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var openAlertId by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 밝은 테마만 쓰므로 시스템 바 아이콘도 항상 어두운 색으로 둔다.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        val container = (application as GPSplaceApp).container
        if (savedInstanceState == null) readOpenIntent(intent)
        container.appScope.launch {
            container.recording.sync()
            runCatching { container.links.syncUserDoc() }
        }
        setContent {
            GPSplaceTheme {
                val start by produceState<String?>(null) {
                    value = debugStartRoute(intent) ?: startRoute(container)
                }
                val route = start
                if (route == null) {
                    Box(Modifier.fillMaxSize().background(AppColors.White))
                } else {
                    AppNavHost(
                        startDestination = route,
                        openAlertId = openAlertId,
                        onAlertOpened = { openAlertId = null },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readOpenIntent(intent)
    }

    private fun readOpenIntent(intent: Intent?) {
        if (intent?.getStringExtra(NotificationChannels.EXTRA_OPEN) == NotificationChannels.OPEN_ALERT) {
            openAlertId = intent.getStringExtra(NotificationChannels.EXTRA_ALERT_ID)
        }
    }

    /**
     * 앱 아이콘 진입점 (UI 보고서 2.3): 최초 S-01 / 보호자 연결됨 S-03 / 보호자 대기 중 S-02 / 보호대상자 연결됨 S-16.
     * 오프라인에서도 열리도록 기기에 남긴 연결 상태로 정한다.
     */
    private suspend fun startRoute(container: AppContainer): String {
        val link = container.settings.currentLink()
        return when {
            link.role == Role.Guardian && link.linkActive -> Routes.GRAPH_GUARDIAN
            link.role == Role.Guardian && link.pendingCode != null && link.pendingCodeExpiresAt > container.clock() -> Routes.GUARDIAN_LINK
            link.role == Role.Protected && link.linkActive -> Routes.PROTECTED_HOME
            else -> Routes.ROLE
        }
    }
}
