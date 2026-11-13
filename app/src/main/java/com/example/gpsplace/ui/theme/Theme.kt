package com.example.gpsplace.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

// 부록 A에는 밝은 색표만 있으므로 다크 모드·다이내믹 컬러는 쓰지 않는다.
// Material 기본 보라색이 섞이지 않도록 모든 역할을 부록 A 색으로 채운다.
private val LightColors = lightColorScheme(
    primary = AppColors.Primary,
    onPrimary = AppColors.White,
    primaryContainer = AppColors.Surface,
    onPrimaryContainer = AppColors.Primary,
    inversePrimary = AppColors.Surface,
    secondary = AppColors.Primary,
    onSecondary = AppColors.White,
    secondaryContainer = AppColors.Surface,
    onSecondaryContainer = AppColors.Text,
    tertiary = AppColors.Primary,
    onTertiary = AppColors.White,
    tertiaryContainer = AppColors.Surface,
    onTertiaryContainer = AppColors.Text,
    background = AppColors.White,
    onBackground = AppColors.Text,
    surface = AppColors.White,
    onSurface = AppColors.Text,
    surfaceVariant = AppColors.Surface,
    onSurfaceVariant = AppColors.Text2,
    surfaceTint = AppColors.White,
    inverseSurface = AppColors.Text,
    inverseOnSurface = AppColors.White,
    error = AppColors.Error,
    onError = AppColors.White,
    errorContainer = AppColors.BannerError,
    onErrorContainer = AppColors.Text,
    outline = AppColors.Text2,
    outlineVariant = AppColors.Divider,
    surfaceBright = AppColors.White,
    surfaceDim = AppColors.Surface,
    surfaceContainerLowest = AppColors.White,
    surfaceContainerLow = AppColors.White,
    surfaceContainer = AppColors.White,
    surfaceContainerHigh = AppColors.White,
    surfaceContainerHighest = AppColors.Surface,
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(Corners.Button),
    medium = RoundedCornerShape(Corners.Card),
    large = RoundedCornerShape(Corners.Sheet),
    extraLarge = RoundedCornerShape(Corners.Sheet),
)

@Composable
fun GPSplaceTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        typography = Typography,
        shapes = AppShapes,
        content = content
    )
}
