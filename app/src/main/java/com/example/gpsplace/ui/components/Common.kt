package com.example.gpsplace.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.gpsplace.ui.LOADING_DELAY_MS
import com.example.gpsplace.ui.theme.AppColors
import com.example.gpsplace.ui.theme.AppType
import com.example.gpsplace.ui.theme.Corners
import com.example.gpsplace.ui.theme.Sizes
import com.example.gpsplace.ui.theme.Spacing
import kotlinx.coroutines.delay

/** 로딩 표시는 300ms가 지나도 결과가 없을 때부터 보인다 (UI 보고서 4장). */
@Composable
fun DelayedLoading(content: @Composable () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(LOADING_DELAY_MS)
        visible = true
    }
    if (visible) content()
}

/** 진행 표시 + 문구 */
@Composable
fun LoadingMessage(title: String, modifier: Modifier = Modifier, description: String? = null) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.ScreenHorizontal, vertical = Spacing.Section),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.M),
    ) {
        CircularProgressIndicator(color = AppColors.Primary, modifier = Modifier.size(32.dp))
        Text(title, style = AppType.Body, color = AppColors.Text, textAlign = TextAlign.Center)
        if (description != null) Text(description, style = AppType.Caption, color = AppColors.Text2, textAlign = TextAlign.Center)
    }
}

/** 목록 행: 높이 64dp 이상, 행 전체가 터치 영역 */
@Composable
fun ListRow(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.ListRowMin)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = Spacing.ScreenHorizontal, vertical = Spacing.ListRowVertical),
        horizontalArrangement = Arrangement.spacedBy(Spacing.M),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** 카드: 모서리 12 · 테두리 1 Divider */
@Composable
fun AppCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(Corners.Card)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .border(Sizes.CardBorder, AppColors.Divider, shape)
            .background(AppColors.White, shape)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(Spacing.CardPadding),
    ) {
        content()
    }
}

/** 이름 / 값 한 줄 (S-06, S-09, S-12, S-14) */
@Composable
fun InfoRow(label: String, value: String, modifier: Modifier = Modifier, protectedUser: Boolean = false) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.S),
        horizontalArrangement = Arrangement.spacedBy(Spacing.L),
    ) {
        Text(
            label,
            style = AppType.Caption,
            color = AppColors.Text2,
            modifier = Modifier.weight(0.4f),
        )
        Text(
            value,
            style = if (protectedUser) AppType.BodyProtected else AppType.Body,
            color = AppColors.Text,
            modifier = Modifier.weight(0.6f),
        )
    }
}

/** 아이콘 + 글자 칩 (연결 상태 “연결됨”) */
@Composable
fun LinkedChip(label: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .heightIn(min = Sizes.Chip)
            .border(1.dp, AppColors.Safe, RoundedCornerShape(Corners.Chip))
            .padding(horizontal = Spacing.S, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(Spacing.Xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(AppIcons.Check, contentDescription = null, tint = AppColors.Safe, modifier = Modifier.size(16.dp))
        Text(label, style = AppType.Caption, color = AppColors.Safe)
    }
}
