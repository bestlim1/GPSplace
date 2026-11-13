package com.example.gpsplace.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.example.gpsplace.ui.theme.AppColors
import com.example.gpsplace.ui.theme.Sizes
import com.example.gpsplace.ui.theme.Spacing

/**
 * 로딩 중 목록 자리: 실제 행 모양(번호 원 + 두 줄 글자)의 회색 막대.
 * 화면의 로딩 문구가 있으면 [contentDescription]으로 넘겨 TalkBack이 읽게 한다.
 */
@Composable
fun SkeletonList(
    rows: Int,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clearAndSetSemantics { if (contentDescription != null) this.contentDescription = contentDescription },
    ) {
        repeat(rows) { SkeletonRow() }
    }
}

@Composable
private fun SkeletonRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.ListRowMin)
            .padding(horizontal = Spacing.ScreenHorizontal, vertical = Spacing.ListRowVertical),
        horizontalArrangement = Arrangement.spacedBy(Spacing.M),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(32.dp)
                .background(AppColors.Divider, CircleShape)
        )
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            Bar(widthFraction = 0.6f, height = 16.dp)
            Bar(widthFraction = 0.35f, height = 12.dp)
        }
    }
}

@Composable
private fun Bar(widthFraction: Float, height: androidx.compose.ui.unit.Dp) {
    Box(
        Modifier
            .fillMaxWidth(widthFraction)
            .height(height)
            .background(AppColors.Divider, RoundedCornerShape(Spacing.Xs))
    )
}
