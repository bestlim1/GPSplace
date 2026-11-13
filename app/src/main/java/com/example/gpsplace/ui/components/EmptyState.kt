package com.example.gpsplace.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.gpsplace.ui.theme.AppColors
import com.example.gpsplace.ui.theme.AppType
import com.example.gpsplace.ui.theme.Sizes
import com.example.gpsplace.ui.theme.Spacing

/**
 * 비어 있음 상태: 아이콘 원 + 제목 + 이유/언제 생기는지 + (있으면) 버튼.
 * 높이를 고정하지 않으므로 글자 크기를 키우면 감싸는 화면이 스크롤을 맡는다.
 */
@Composable
fun EmptyState(
    title: String,
    description: String?,
    modifier: Modifier = Modifier,
    icon: ImageVector = AppIcons.Info,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    protectedUser: Boolean = false,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.ScreenHorizontal, vertical = Spacing.Section),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.M),
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .background(AppColors.Surface, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = AppColors.Text2, modifier = Modifier.size(Sizes.Icon))
        }
        Text(title, style = AppType.Title, color = AppColors.Text, textAlign = TextAlign.Center)
        if (description != null) {
            Text(
                description,
                style = if (protectedUser) AppType.BodyProtected else AppType.Body,
                color = AppColors.Text2,
                textAlign = TextAlign.Center,
            )
        }
        if (actionLabel != null && onAction != null) {
            PrimaryButton(
                text = actionLabel,
                onClick = onAction,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.S),
                protectedUser = protectedUser,
            )
        }
    }
}
