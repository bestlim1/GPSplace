package com.example.gpsplace.ui.theme

import androidx.compose.ui.graphics.Color

// UI 설계 보고서 부록 A의 색. 여기에 없는 색은 새로 만들지 않는다.
object AppColors {
    /** 기본 버튼, 경로 선, 선택된 탭 */
    val Primary = Color(0xFF1D5BB9)
    /** 본문·제목 */
    val Text = Color(0xFF1A1A1A)
    /** 시각·캡션 */
    val Text2 = Color(0xFF5C6166)
    /** 카드·그룹 배경 */
    val Surface = Color(0xFFF3F5F7)
    /** 안전 구역 (+실선·방패) */
    val Safe = Color(0xFF1B7A3A)
    /** 주의 구역 (+점선·삼각형) */
    val Caution = Color(0xFFA85A00)
    /** 오류 문구·위험 동작 */
    val Error = Color(0xFFC0282D)
    /** 기록 누락 점선 */
    val Missing = Color(0xFF6B6F73)
    /** 지도 바탕 */
    val MapBase = Color(0xFFECE9E3)
    /** 부분 상태 배너 */
    val BannerCaution = Color(0xFFFFF3E0)
    /** 오류 배너 */
    val BannerError = Color(0xFFFDECEC)
    /** 구분선 (장식) */
    val Divider = Color(0xFFD5D8DC)
    /** 화면 바탕, 기본 버튼 글자 (6장 명암비 표의 배경색) */
    val White = Color(0xFFFFFFFF)
}
