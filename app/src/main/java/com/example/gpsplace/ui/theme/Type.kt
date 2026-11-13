package com.example.gpsplace.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// 부록 A 글자 기준. sp만 쓴다.
object AppType {
    /** 연결 코드 “482 913” */
    val Code = style(32, 40, FontWeight.Bold)
    /** 큰 상태 (S-16 “기록 중”) */
    val BigStatus = style(24, 32, FontWeight.Bold)
    /** 화면 제목 */
    val Title = style(20, 28, FontWeight.Medium)
    /** 본문 (보호자) */
    val Body = style(16, 24, FontWeight.Normal)
    /** 본문 (보호대상자 화면) */
    val BodyProtected = style(18, 26, FontWeight.Normal)
    /** 보조 (시각·캡션) */
    val Caption = style(14, 20, FontWeight.Normal)
    /** 최소 — 지도 라벨·범례에만 쓴다 */
    val MapLabel = style(12, 16, FontWeight.Medium)
}

private fun style(size: Int, lineHeight: Int, weight: FontWeight) = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = 0.sp,
)

// Material 컴포넌트가 쓰는 역할을 부록 A 크기에 맞춘다. 12sp는 labelSmall(지도 라벨)에만 둔다.
val Typography = Typography(
    headlineSmall = AppType.Title,
    titleLarge = AppType.Title,
    titleMedium = AppType.Body.copy(fontWeight = FontWeight.Medium),
    titleSmall = AppType.Caption.copy(fontWeight = FontWeight.Medium),
    bodyLarge = AppType.Body,
    bodyMedium = AppType.Body,
    bodySmall = AppType.Caption,
    labelLarge = AppType.Body.copy(fontWeight = FontWeight.Medium),
    labelMedium = AppType.Caption.copy(fontWeight = FontWeight.Medium),
    labelSmall = AppType.MapLabel,
)
