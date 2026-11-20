package com.example.gpsplace.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.gpsplace.R
import com.example.gpsplace.domain.GapDetector
import com.example.gpsplace.domain.Geo
import com.example.gpsplace.domain.Stay
import com.example.gpsplace.domain.TrackPoint
import com.example.gpsplace.domain.Zone
import com.example.gpsplace.domain.ZoneType
import com.example.gpsplace.ui.Formats
import com.example.gpsplace.ui.theme.AppColors
import com.example.gpsplace.ui.theme.AppType
import com.example.gpsplace.ui.theme.MapStroke
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.Dash
import com.google.android.gms.maps.model.Gap
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.PatternItem
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.GoogleMapComposable
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.MarkerComposable
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline

// 지도 표기 (SCREENS.md 공통 · 부록 A): 경로 실선 / 기록 누락 점선 + 라벨 / 출발 네모 / 체류 흰 원+번호 /
// 최신 위치 채운 원 + 시각 라벨 / 안전 구역 실선 원+방패 / 주의 구역 점선 원+삼각형.
// 지도에는 실제로 수집한 좌표를 이은 경로만 그린다 (FR-07 Won't).

val SEOUL = LatLng(37.5663, 126.9779)

fun TrackPoint.latLng() = LatLng(lat, lng)

/** 지도 + 불러오는 동안 “지도를 불러오는 중” */
@Composable
fun AppMap(
    cameraPositionState: CameraPositionState,
    modifier: Modifier = Modifier,
    onMapLoaded: () -> Unit = {},
    zoomControls: Boolean = true,
    content: @Composable @GoogleMapComposable () -> Unit,
) {
    var loaded by remember { mutableStateOf(false) }
    Box(modifier = modifier.background(AppColors.MapBase)) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = remember { MapProperties(isBuildingEnabled = false) },
            uiSettings = remember {
                // 구글 기본 확대·축소 버튼은 42×40dp라 터치 영역 48dp 기준에 못 미쳐 직접 그린다 (아래 ZoomButtons)
                MapUiSettings(
                    zoomControlsEnabled = false,
                    mapToolbarEnabled = false,
                    myLocationButtonEnabled = false,
                    compassEnabled = false,
                    rotationGesturesEnabled = false,
                    tiltGesturesEnabled = false,
                )
            },
            onMapLoaded = {
                loaded = true
                onMapLoaded()
            },
            content = content,
        )
        if (zoomControls && loaded) {
            ZoomButtons(cameraPositionState, Modifier.align(Alignment.BottomEnd).padding(12.dp))
        }
        if (!loaded) {
            Box(Modifier.fillMaxSize().background(AppColors.MapBase), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.map_loading), style = AppType.Caption, color = AppColors.Text2)
            }
        }
    }
}

/** 확대·축소 버튼 48dp (S-03 “우측 확대·축소 버튼”) */
@Composable
private fun ZoomButtons(cameraPositionState: CameraPositionState, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    Column(
        modifier = modifier
            .background(AppColors.White, RoundedCornerShape(8.dp))
            .border(1.dp, AppColors.Divider, RoundedCornerShape(8.dp)),
    ) {
        IconButton(onClick = { scope.launch { cameraPositionState.animate(CameraUpdateFactory.zoomIn()) } }) {
            Icon(AppIcons.Add, contentDescription = stringResource(R.string.cd_zoom_in), tint = AppColors.Text)
        }
        Box(Modifier.size(width = 48.dp, height = 1.dp).background(AppColors.Divider))
        IconButton(onClick = { scope.launch { cameraPositionState.animate(CameraUpdateFactory.zoomOut()) } }) {
            Icon(AppIcons.Remove, contentDescription = stringResource(R.string.cd_zoom_out), tint = AppColors.Text)
        }
    }
}

/**
 * 점들이 모두 보이도록 카메라를 맞춘다. 한 점이면 16배율.
 * 점이 좁게 모여 있으면(머무는 동안의 GPS 흔들림) 경계에 맞추면 건물 단위까지 확대되므로 17배율로 가운데에 둔다.
 * (move 직후에는 position이 아직 이전 값이라 확대 수준을 나중에 고칠 수 없다)
 */
suspend fun CameraPositionState.fit(points: List<LatLng>, paddingPx: Int) {
    when (points.size) {
        0 -> return
        1 -> move(CameraUpdateFactory.newLatLngZoom(points[0], 16f))
        else -> {
            val bounds = LatLngBounds.builder().apply { points.forEach { include(it) } }.build()
            val spanM = Geo.distanceMeters(bounds.southwest.latitude, bounds.southwest.longitude, bounds.northeast.latitude, bounds.northeast.longitude)
            if (spanM < FIT_MIN_SPAN_M) move(CameraUpdateFactory.newLatLngZoom(bounds.center, 17f))
            else move(CameraUpdateFactory.newLatLngBounds(bounds, paddingPx))
        }
    }
}

/** 이보다 좁게 모인 점들은 경계 맞춤 대신 17배율(화면 폭 약 300m)로 보여 준다 */
private const val FIT_MIN_SPAN_M = 150.0

fun defaultCamera() = CameraPosition.fromLatLngZoom(SEOUL, 11f)

/** 원 전체가 보이도록 원의 동서남북 끝점 */
fun circleBounds(lat: Double, lng: Double, radiusM: Double): List<LatLng> {
    val dLat = Math.toDegrees(radiusM / 6_371_008.8)
    val dLng = dLat / kotlin.math.cos(Math.toRadians(lat))
    return listOf(LatLng(lat + dLat, lng), LatLng(lat - dLat, lng), LatLng(lat, lng + dLng), LatLng(lat, lng - dLng))
}

@Composable
private fun dashPattern(dash: Dp, gap: Dp): List<PatternItem> {
    val density = LocalDensity.current
    return with(density) { listOf(Dash(dash.toPx()), Gap(gap.toPx())) }
}

/** 경로: 2분 넘게 끊긴 곳은 실선으로 잇지 않고 점선 + “기록 누락” */
@Composable
@GoogleMapComposable
fun RouteLayer(points: List<TrackPoint>) {
    val density = LocalDensity.current
    val routeWidth = with(density) { MapStroke.Route.toPx() }
    val missingWidth = with(density) { MapStroke.Missing.toPx() }
    val missingPattern = dashPattern(MapStroke.MissingDash, MapStroke.MissingGap)
    GapDetector.segments(points).forEach { segment ->
        if (segment.size >= 2) {
            Polyline(points = segment.map { it.latLng() }, color = AppColors.Primary, width = routeWidth)
        }
    }
    GapDetector.gaps(points).forEach { gap ->
        Polyline(
            points = listOf(gap.from.latLng(), gap.to.latLng()),
            color = AppColors.Missing,
            width = missingWidth,
            pattern = missingPattern,
        )
        val mid = LatLng((gap.from.lat + gap.to.lat) / 2, (gap.from.lng + gap.to.lng) / 2)
        MapLabelMarker(mid, stringResource(R.string.map_missing), AppColors.Missing)
    }
}

/** 지도 글자 라벨. [anchor]로 점의 어느 쪽에 붙일지 정한다 (가까운 두 점의 라벨이 겹치지 않게) */
@Composable
@GoogleMapComposable
fun MapLabelMarker(
    position: LatLng,
    label: String,
    color: androidx.compose.ui.graphics.Color,
    anchor: Offset = Offset(0.5f, 0.5f),
) {
    val state = remember(position) { MarkerState(position) }
    MarkerComposable(label, anchor, state = state, anchor = anchor, title = label) {
        Text(
            label,
            style = AppType.MapLabel,
            color = color,
            modifier = Modifier
                .background(AppColors.White, RoundedCornerShape(8.dp))
                .border(1.dp, color, RoundedCornerShape(8.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

/** 출발 네모 */
@Composable
@GoogleMapComposable
fun StartMarker(point: TrackPoint) {
    val label = stringResource(R.string.map_start)
    val state = remember(point) { MarkerState(point.latLng()) }
    MarkerComposable(point.t, state = state, anchor = Offset(0.5f, 0.5f), title = label) {
        Box(
            Modifier
                .size(14.dp)
                .border(2.dp, AppColors.White)
                .background(AppColors.Text)
        )
    }
}

/** 체류 흰 원 + 번호 */
@Composable
@GoogleMapComposable
fun StayMarker(stay: Stay, number: Int) {
    val title = stringResource(R.string.cd_stay_number, number)
    val state = remember(stay.id, stay.lat, stay.lng) { MarkerState(LatLng(stay.lat, stay.lng)) }
    MarkerComposable(stay.id, number, state = state, anchor = Offset(0.5f, 0.5f), title = title, zIndex = 2f) {
        NumberCircle(number)
    }
}

@Composable
fun NumberCircle(number: Int, modifier: Modifier = Modifier, size: Dp = 26.dp) {
    Box(
        modifier = modifier
            .size(size)
            .background(AppColors.White, CircleShape)
            .border(2.dp, AppColors.Text, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(number.toString(), style = AppType.MapLabel.copy(fontWeight = FontWeight.Bold), color = AppColors.Text)
    }
}

/** 최신 위치: 채운 원 + 시각 라벨. 갱신 지연이면 테두리를 점선으로 */
@Composable
@GoogleMapComposable
fun LatestMarker(point: TrackPoint, dashed: Boolean) {
    val time = Formats.hm(point.t)
    val state = remember(point) { MarkerState(point.latLng()) }
    MarkerComposable(point.t, dashed, state = state, anchor = Offset(0.5f, 0.3f), title = time, zIndex = 3f) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .drawBehind {
                        val stroke = 2.dp.toPx()
                        drawCircle(
                            color = if (dashed) AppColors.Missing else AppColors.White,
                            radius = size.minDimension / 2 - stroke / 2,
                            style = Stroke(
                                width = stroke,
                                pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())) else null,
                            ),
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(16.dp).background(if (dashed) AppColors.Missing else AppColors.Primary, CircleShape))
            }
            Text(
                time,
                style = AppType.MapLabel,
                color = AppColors.Text,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .background(AppColors.White, RoundedCornerShape(8.dp))
                    .border(1.dp, AppColors.Divider, RoundedCornerShape(8.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}

/** 구역: 안전 = 실선 원 + 방패, 주의 = 점선 원 + 삼각형, 15% 채움, 이름과 종류 글자 */
@Composable
@GoogleMapComposable
fun ZoneLayer(zone: Zone, showLabel: Boolean = true) {
    val density = LocalDensity.current
    val color = if (zone.type == ZoneType.Safe) AppColors.Safe else AppColors.Caution
    val center = LatLng(zone.lat, zone.lng)
    Circle(
        center = center,
        radius = zone.radiusM,
        strokeColor = color,
        strokeWidth = with(density) { MapStroke.Zone.toPx() },
        strokePattern = if (zone.type == ZoneType.Caution) dashPattern(MapStroke.MissingDash, MapStroke.MissingGap) else null,
        fillColor = color.copy(alpha = MapStroke.ZoneFillAlpha),
    )
    if (showLabel) {
        val typeLabel = stringResource(if (zone.type == ZoneType.Safe) R.string.zone_safe else R.string.zone_caution)
        val state = remember(zone.id, zone.lat, zone.lng) { MarkerState(center) }
        // 이름표는 원 중심 아래에 둔다 (중심에 오는 체류 번호·최신 위치와 겹치지 않게)
        MarkerComposable(zone.id, zone.name, zone.type, state = state, anchor = Offset(0.5f, -0.8f), title = "${zone.name} · $typeLabel") {
            ZoneTag(zone.name, zone.type, typeLabel)
        }
    }
}

@Composable
fun ZoneTag(name: String, type: ZoneType, typeLabel: String) {
    val color = if (type == ZoneType.Safe) AppColors.Safe else AppColors.Caution
    Row(
        modifier = Modifier
            .background(AppColors.White, RoundedCornerShape(8.dp))
            .border(1.dp, color, RoundedCornerShape(8.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(if (type == ZoneType.Safe) AppIcons.Safe else AppIcons.Caution, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
        Text("$name · $typeLabel", style = AppType.MapLabel, color = color)
    }
}
