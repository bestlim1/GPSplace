package com.example.gpsplace.ui.screens.s07_date_picker

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.example.gpsplace.R
import com.example.gpsplace.ui.components.AppTextButton
import com.example.gpsplace.ui.components.PrimaryButton
import com.example.gpsplace.ui.theme.AppColors
import com.example.gpsplace.ui.theme.AppType
import com.example.gpsplace.ui.theme.Corners
import com.example.gpsplace.ui.theme.Spacing
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

private fun LocalDate.utcMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

/**
 * S-07 날짜 선택 (바텀시트) · FR-05. 오늘 이후 날짜는 고를 수 없다.
 * 뒤로·바깥 터치·[취소] = 날짜 변경 없이 닫기.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerSheet(
    selected: LocalDate,
    today: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit,
) {
    val todayMillis = today.utcMillis()
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = selected.utcMillis(),
        yearRange = (today.year - 5)..today.year,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= todayMillis
            override fun isSelectableYear(year: Int) = year <= today.year
        },
    )
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = AppColors.White,
        shape = RoundedCornerShape(topStart = Corners.Sheet, topEnd = Corners.Sheet),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = Spacing.L),
        ) {
            Text(
                stringResource(R.string.s07_title),
                style = AppType.Title,
                color = AppColors.Text,
                modifier = Modifier.padding(horizontal = Spacing.ScreenHorizontal),
            )
            DatePicker(
                state = pickerState,
                title = null,
                headline = null,
                showModeToggle = false,
                colors = DatePickerDefaults.colors(
                    containerColor = AppColors.White,
                    selectedDayContainerColor = AppColors.Primary,
                    selectedDayContentColor = AppColors.White,
                    todayDateBorderColor = AppColors.Primary,
                    todayContentColor = AppColors.Primary,
                    disabledDayContentColor = AppColors.Divider,
                ),
            )
            Text(
                stringResource(R.string.s07_future_note),
                style = AppType.Caption,
                color = AppColors.Text2,
                modifier = Modifier.padding(horizontal = Spacing.ScreenHorizontal),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.ScreenHorizontal, vertical = Spacing.M),
                horizontalArrangement = Arrangement.spacedBy(Spacing.S, androidx.compose.ui.Alignment.End),
            ) {
                AppTextButton(text = stringResource(R.string.action_cancel), onClick = onDismiss)
                PrimaryButton(
                    text = stringResource(R.string.s07_confirm),
                    onClick = {
                        val millis = pickerState.selectedDateMillis
                        if (millis == null) onDismiss()
                        else onConfirm(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                    },
                )
            }
        }
    }
}
