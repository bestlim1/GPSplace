package com.example.gpsplace.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.example.gpsplace.R
import com.example.gpsplace.ui.theme.AppColors
import com.example.gpsplace.ui.theme.AppType
import com.example.gpsplace.ui.theme.Corners
import com.example.gpsplace.ui.theme.Sizes
import com.example.gpsplace.ui.theme.Spacing

/**
 * 오류 상태: 아이콘 + 제목(무엇이 실패) + “원인: …” 상자 + 다음 행동 버튼.
 * 오류 코드만 보여 주지 않는다. [detail]에는 “기록 23건은 이 휴대폰에 그대로 보관 중입니다.” 같은 뒤따르는 문장을 넣는다.
 */
@Composable
fun ErrorState(
    title: String,
    cause: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    detail: String? = null,
    secondaryActionLabel: String? = null,
    onSecondaryAction: (() -> Unit)? = null,
    protectedUser: Boolean = false,
) {
    val bodyStyle = if (protectedUser) AppType.BodyProtected else AppType.Body
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.ScreenHorizontal, vertical = Spacing.Section),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.M),
    ) {
        Icon(
            AppIcons.Error,
            contentDescription = stringResource(R.string.cd_error),
            tint = AppColors.Error,
            modifier = Modifier.size(Sizes.Icon),
        )
        Text(title, style = AppType.Title, color = AppColors.Text, textAlign = TextAlign.Center)
        Text(
            stringResource(R.string.error_cause, cause),
            style = bodyStyle,
            color = AppColors.Text,
            modifier = Modifier
                .fillMaxWidth()
                .background(AppColors.BannerError, RoundedCornerShape(Corners.Card))
                .padding(Spacing.CardPadding),
        )
        if (detail != null) {
            Text(detail, style = bodyStyle, color = AppColors.Text2, textAlign = TextAlign.Center)
        }
        PrimaryButton(
            text = actionLabel,
            onClick = onAction,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Spacing.S),
            protectedUser = protectedUser,
        )
        if (secondaryActionLabel != null && onSecondaryAction != null) {
            SecondaryButton(
                text = secondaryActionLabel,
                onClick = onSecondaryAction,
                modifier = Modifier.fillMaxWidth(),
                protectedUser = protectedUser,
            )
        }
    }
}
