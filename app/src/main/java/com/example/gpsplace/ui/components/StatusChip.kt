package com.example.gpsplace.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.example.gpsplace.R
import com.example.gpsplace.ui.theme.AppColors
import com.example.gpsplace.ui.theme.AppType
import com.example.gpsplace.ui.theme.Corners
import com.example.gpsplace.ui.theme.MapStroke
import com.example.gpsplace.ui.theme.Sizes
import com.example.gpsplace.ui.theme.Spacing

/** 기록 상태. 색(초록·주황·빨강·회색) + 아이콘 + 글자로 구분한다 (UI 보고서 6.4). */
enum class RecordStatus(
    @StringRes val label: Int,
    val color: Color,
    val icon: ImageVector,
    val dashedBorder: Boolean = false,
) {
    Recording(R.string.status_recording, AppColors.Safe, AppIcons.Recording),
    Delayed(R.string.status_delayed, AppColors.Caution, AppIcons.Paused),
    NotRecording(R.string.status_not_recording, AppColors.Error, AppIcons.Paused, dashedBorder = true),
    PendingUpload(R.string.status_pending_upload, AppColors.Missing, AppIcons.Upload),
}

@Composable
fun StatusChip(status: RecordStatus, modifier: Modifier = Modifier) {
    val label = stringResource(status.label)
    val shape = RoundedCornerShape(Corners.Chip)
    val border = if (status.dashedBorder) {
        Modifier.drawBehind {
            val stroke = 1.dp.toPx()
            drawRoundRect(
                color = status.color,
                topLeft = Offset(stroke / 2, stroke / 2),
                size = Size(size.width - stroke, size.height - stroke),
                cornerRadius = CornerRadius(Corners.Chip.toPx()),
                style = Stroke(
                    width = stroke,
                    pathEffect = PathEffect.dashPathEffect(
                        floatArrayOf(MapStroke.MissingDash.toPx(), MapStroke.MissingGap.toPx())
                    ),
                ),
            )
        }
    } else {
        Modifier.border(1.dp, status.color, shape)
    }
    Row(
        modifier = modifier
            .heightIn(min = Sizes.Chip)
            .then(border)
            .padding(horizontal = Spacing.S, vertical = 2.dp)
            .clearAndSetSemantics { contentDescription = label },
        horizontalArrangement = Arrangement.spacedBy(Spacing.Xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(status.icon, contentDescription = null, tint = status.color, modifier = Modifier.size(16.dp))
        Text(label, style = AppType.Caption, color = status.color)
    }
}
