package com.example.gpsplace.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.example.gpsplace.ui.theme.AppColors
import com.example.gpsplace.ui.theme.AppType

/**
 * D-01~D-04 확인 다이얼로그. 버튼은 오른쪽 정렬, 실행 버튼이 오른쪽 끝.
 * 뒤로가기·바깥 터치는 왼쪽 버튼([dismissLabel])과 같다.
 * [destructive]이면 실행 버튼을 위험 버튼(테두리 + ⚠)으로 그린다 (D-01, D-03).
 */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    dismissLabel: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    destructive: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = AppType.Title, color = AppColors.Text) },
        text = { Text(message, style = AppType.Body, color = AppColors.Text2) },
        dismissButton = { AppTextButton(text = dismissLabel, onClick = onDismiss) },
        confirmButton = {
            if (destructive) {
                DangerButton(text = confirmLabel, onClick = onConfirm)
            } else {
                AppTextButton(text = confirmLabel, onClick = onConfirm)
            }
        },
    )
}
