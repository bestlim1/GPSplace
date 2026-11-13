package com.example.gpsplace.ui.theme

import androidx.compose.ui.unit.dp

// 부록 A 여백 (4dp 단위)
object Spacing {
    val Xs = 4.dp
    val S = 8.dp
    val M = 12.dp
    val L = 16.dp
    val Xl = 24.dp
    val Xxl = 32.dp

    /** 화면 좌우 */
    val ScreenHorizontal = 16.dp
    /** 카드 안 (12~16) */
    val CardPadding = 16.dp
    /** 목록 행 위아래 */
    val ListRowVertical = 12.dp
    /** 섹션 사이 */
    val Section = 24.dp
    /** 위험 버튼과 다른 버튼 사이 (이상) */
    val DangerGap = 24.dp
}

// 부록 A 버튼·컴포넌트 크기. 글자가 들어가는 곳은 최소 높이로만 쓰고 고정 높이로 쓰지 않는다.
object Sizes {
    val TouchTarget = 48.dp
    val Button = 48.dp
    val ButtonProtected = 56.dp
    val ListRowMin = 64.dp
    val Chip = 24.dp
    val Icon = 24.dp
    val Stroke = 2.dp
    val CardBorder = 1.dp
}

object Corners {
    val Button = 8.dp
    val Card = 12.dp
    val Chip = 12.dp
    val Sheet = 16.dp
}

// 부록 A 지도 표기
object MapStroke {
    val Route = 4.dp
    val Missing = 2.dp
    val MissingDash = 4.dp
    val MissingGap = 3.dp
    val Zone = 2.dp
    const val ZoneFillAlpha = 0.15f
}
