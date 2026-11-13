package com.example.gpsplace.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.gpsplace.ui.theme.AppColors
import com.example.gpsplace.ui.theme.Corners
import com.example.gpsplace.ui.theme.Sizes
import com.example.gpsplace.ui.theme.Spacing

// 부록 A 버튼: 기본 / 보조 / 텍스트 / ⚠ 위험. 높이 48 (보호대상자 56), 모서리 8. 기본 버튼은 화면당 1개.

private val ButtonShape = RoundedCornerShape(Corners.Button)

private fun buttonHeight(protectedUser: Boolean) = if (protectedUser) Sizes.ButtonProtected else Sizes.Button

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    protectedUser: Boolean = false,
) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = buttonHeight(protectedUser)),
        enabled = enabled,
        shape = ButtonShape,
    ) {
        Text(text)
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    protectedUser: Boolean = false,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = buttonHeight(protectedUser)),
        enabled = enabled,
        shape = ButtonShape,
        border = BorderStroke(1.dp, AppColors.Primary),
    ) {
        Text(text)
    }
}

@Composable
fun AppTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    TextButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = Sizes.Button),
        enabled = enabled,
        shape = ButtonShape,
    ) {
        Text(text)
    }
}

/** 위험 동작(연결 해제·구역 삭제)은 테두리 + 아이콘으로 그리고 반드시 확인 다이얼로그를 거친다. */
@Composable
fun DangerButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = Sizes.Button),
        enabled = enabled,
        shape = ButtonShape,
        border = BorderStroke(1.dp, AppColors.Error),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.Error),
    ) {
        // 버튼 글자가 의미를 전달하므로 아이콘은 장식으로 둔다.
        Icon(AppIcons.Caution, contentDescription = null, modifier = Modifier.size(Sizes.Icon))
        Spacer(Modifier.width(Spacing.S))
        Text(text)
    }
}
