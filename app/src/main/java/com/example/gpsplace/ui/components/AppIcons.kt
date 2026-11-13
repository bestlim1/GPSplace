package com.example.gpsplace.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

// 화면에서 쓰는 아이콘을 의미별로 한곳에 모은다.
// material-icons-core에 없는 방패·일시정지·업로드·점·오류는 Material Icons 경로(Apache 2.0)로 직접 정의한다.
object AppIcons {
    /** 안전 구역 */
    val Safe: ImageVector by lazy { icon("Shield", "M12,1L3,5v6c0,5.55 3.84,10.74 9,12c5.16,-1.26 9,-6.45 9,-12V5L12,1z") }
    /** 주의 구역 (삼각형) */
    val Caution: ImageVector get() = Icons.Filled.Warning
    /** 상태 칩: 기록 중 */
    val Recording: ImageVector by lazy { icon("Dot", "M12,6a6,6 0,1 0,0 12a6,6 0,1 0,0 -12z") }
    /** 상태 칩: 갱신 지연·기록 안 됨 */
    val Paused: ImageVector by lazy { icon("Pause", "M6,19h4V5H6v14zM14,5v14h4V5h-4z") }
    /** 상태 칩: 전송 대기 */
    val Upload: ImageVector by lazy { icon("Upload", "M9,16h6v-6h4l-7,-7 -7,7h4zM5,18h14v2H5z") }
    val Error: ImageVector by lazy {
        icon("Error", "M12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10S17.52,2 12,2zM13,17h-2v-2h2v2zM13,13h-2V7h2v6z")
    }
    val Info: ImageVector get() = Icons.Filled.Info
    val Calendar: ImageVector get() = Icons.Filled.DateRange
    val Back: ImageVector get() = Icons.AutoMirrored.Filled.ArrowBack
    val Close: ImageVector get() = Icons.Filled.Close
    val Settings: ImageVector get() = Icons.Filled.Settings
    val Check: ImageVector get() = Icons.Filled.CheckCircle
    val PrevDay: ImageVector get() = Icons.AutoMirrored.Filled.KeyboardArrowLeft
    val NextDay: ImageVector get() = Icons.AutoMirrored.Filled.KeyboardArrowRight
    val DropDown: ImageVector get() = Icons.Filled.ArrowDropDown
    val Add: ImageVector get() = Icons.Filled.Add
    val Remove: ImageVector by lazy { icon("Remove", "M19,13H5v-2h14v2z") }
    val Place: ImageVector get() = Icons.Filled.Place
    val Person: ImageVector get() = Icons.Filled.Person
    val Recenter: ImageVector get() = Icons.Filled.LocationOn
    val CloudOff: ImageVector by lazy {
        icon(
            "CloudOff",
            "M19.35,10.04C18.67,6.59 15.64,4 12,4c-1.48,0 -2.85,0.43 -4.01,1.17l1.46,1.46C10.21,6.23 11.08,6 12,6c3.04,0 5.5,2.46 5.5,5.5v0.5H19c1.66,0 3,1.34 3,3 0,1.13 -0.64,2.11 -1.56,2.62l1.45,1.45C23.16,18.16 24,16.68 24,15c0,-2.64 -2.05,-4.78 -4.65,-4.96zM3,5.27l2.75,2.74C2.56,8.15 0,10.77 0,14c0,3.31 2.69,6 6,6h11.73l2,2L21,20.73 4.27,4 3,5.27zM7.73,10l8,8H6c-2.21,0 -4,-1.79 -4,-4s1.79,-4 4,-4h1.73z",
        )
    }

    // 보호자 하단 탭
    val TabLocation: ImageVector get() = Icons.Filled.LocationOn
    val TabHistory: ImageVector get() = Icons.AutoMirrored.Filled.List
    val TabAlerts: ImageVector get() = Icons.Filled.Notifications
}

private fun icon(name: String, pathData: String): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).addPath(pathData = addPathNodes(pathData), fill = SolidColor(Color.Black)).build()
