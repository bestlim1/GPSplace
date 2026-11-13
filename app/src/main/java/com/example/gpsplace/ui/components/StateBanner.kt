package com.example.gpsplace.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.gpsplace.ui.theme.AppColors
import com.example.gpsplace.ui.theme.AppType
import com.example.gpsplace.ui.theme.Sizes
import com.example.gpsplace.ui.theme.Spacing

enum class BannerTone { Caution, Error }

/**
 * 부분 상태 배너. 화면 위쪽 전체 폭에 두고, 데이터는 아래에 그대로 둔다.
 * 아이콘 + 원인([message]) + 기준 시각([baseTime]) + (있으면) 행동 링크.
 */
@Composable
fun StateBanner(
    message: String,
    modifier: Modifier = Modifier,
    tone: BannerTone = BannerTone.Caution,
    baseTime: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    icon: ImageVector = if (tone == BannerTone.Error) AppIcons.Error else AppIcons.Info,
) {
    val background = if (tone == BannerTone.Error) AppColors.BannerError else AppColors.BannerCaution
    val iconTint = if (tone == BannerTone.Error) AppColors.Error else AppColors.Caution
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(background)
            .padding(horizontal = Spacing.ScreenHorizontal, vertical = Spacing.M),
        horizontalArrangement = Arrangement.spacedBy(Spacing.M),
        verticalAlignment = Alignment.Top,
    ) {
        // 배너 문구가 원인을 그대로 말하므로 아이콘은 장식으로 둔다.
        Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(Sizes.Icon))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Spacing.Xs),
        ) {
            Text(message, style = AppType.Body, color = AppColors.Text)
            if (baseTime != null) {
                Text(baseTime, style = AppType.Caption, color = AppColors.Text2)
            }
            if (actionLabel != null && onAction != null) {
                AppTextButton(text = actionLabel, onClick = onAction)
            }
        }
    }
}
