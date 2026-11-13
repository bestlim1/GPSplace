package com.example.gpsplace.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.example.gpsplace.R
import com.example.gpsplace.ui.Formats
import com.example.gpsplace.ui.theme.AppColors
import com.example.gpsplace.ui.theme.AppType
import com.example.gpsplace.ui.theme.Corners
import com.example.gpsplace.ui.theme.Sizes
import com.example.gpsplace.ui.theme.Spacing
import java.time.LocalDate

/**
 * ◀ · 날짜 칩(달력 아이콘, “10월 29일 (목) · 오늘” ▾) · ▶. 오늘이면 ▶ 비활성. 칩을 누르면 S-07.
 * 하루 앞뒤 이동은 칩 바로 옆 48dp 버튼으로 (UI 보고서 5장).
 */
@Composable
fun DateBar(
    date: LocalDate,
    today: LocalDate,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onPick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = if (date == today) stringResource(R.string.date_today_suffix, Formats.day(date)) else Formats.day(date)
    val pickDescription = stringResource(R.string.cd_pick_date)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.S, vertical = Spacing.Xs),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrevious) {
            Icon(AppIcons.PrevDay, contentDescription = stringResource(R.string.cd_prev_day), tint = AppColors.Text)
        }
        Row(
            modifier = Modifier
                .weight(1f, fill = false)
                .heightIn(min = Sizes.TouchTarget)
                .border(Sizes.CardBorder, AppColors.Divider, RoundedCornerShape(Corners.Button))
                .clickable(role = Role.Button, onClick = onPick)
                .semantics { contentDescription = "$label, $pickDescription" }
                .padding(horizontal = Spacing.M, vertical = Spacing.S),
            horizontalArrangement = Arrangement.spacedBy(Spacing.S),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(AppIcons.Calendar, contentDescription = null, tint = AppColors.Primary, modifier = Modifier.size(Sizes.Icon))
            Text(label, style = AppType.Body, color = AppColors.Text)
            Icon(AppIcons.DropDown, contentDescription = null, tint = AppColors.Text2)
        }
        IconButton(
            onClick = onNext,
            enabled = date < today,
            colors = IconButtonDefaults.iconButtonColors(contentColor = AppColors.Text, disabledContentColor = AppColors.Divider),
        ) {
            Icon(AppIcons.NextDay, contentDescription = stringResource(R.string.cd_next_day))
        }
    }
}
