package com.example.gpsplace.ui.screens.s11_zone_edit

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.gpsplace.R
import com.example.gpsplace.domain.ZoneType
import com.example.gpsplace.ui.appViewModel
import com.example.gpsplace.ui.components.AppIcons
import com.example.gpsplace.ui.components.AppMap
import com.example.gpsplace.ui.components.AppTopBar
import com.example.gpsplace.ui.components.ConfirmDialog
import com.example.gpsplace.ui.components.DelayedLoading
import com.example.gpsplace.ui.components.LoadingMessage
import com.example.gpsplace.ui.components.PrimaryButton
import com.example.gpsplace.ui.components.TopBarNavigation
import com.example.gpsplace.ui.radiusText
import com.example.gpsplace.ui.theme.AppColors
import com.example.gpsplace.ui.theme.AppType
import com.example.gpsplace.ui.theme.MapStroke
import com.example.gpsplace.ui.theme.Sizes
import com.example.gpsplace.ui.theme.Spacing
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.Dash
import com.google.android.gms.maps.model.Gap
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.flow.filter

/**
 * S-11 구역 추가·편집 (폼) · FR-04.
 * 뒤로/×: 키보드가 열려 있으면 키보드만 닫히고(시스템), 변경이 있으면 D-02, 없으면 S-10.
 */
@Composable
fun ZoneEditScreen(onClose: () -> Unit) {
    val viewModel = appViewModel { container, saved -> ZoneEditViewModel(container, saved) }
    var confirmLeave by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    fun requestClose() {
        if (viewModel.changed) confirmLeave = true else onClose()
    }

    BackHandler(onBack = ::requestClose)

    ZoneEditBody(
        isEdit = viewModel.isEdit,
        loading = viewModel.loading,
        form = viewModel.form,
        nameError = viewModel.nameError,
        saving = viewModel.saving,
        onName = viewModel::onName,
        onType = viewModel::onType,
        onRadius = viewModel::onRadius,
        onCenter = viewModel::onCenter,
        onSave = { viewModel.save(onSaved = onClose) },
        onDelete = { confirmDelete = true },
        onClose = ::requestClose,
    )

    if (confirmLeave) {
        ConfirmDialog(
            title = stringResource(R.string.d02_title),
            message = stringResource(R.string.d02_message),
            dismissLabel = stringResource(R.string.d02_keep),
            confirmLabel = stringResource(R.string.d02_leave),
            onDismiss = { confirmLeave = false },
            onConfirm = {
                confirmLeave = false
                onClose()
            },
        )
    }
    if (confirmDelete) {
        DeleteZoneDialog(
            name = viewModel.form.name,
            onDismiss = { confirmDelete = false },
            onConfirm = {
                confirmDelete = false
                viewModel.delete(onDeleted = onClose)
            },
        )
    }
}

/** D-03 구역 삭제 확인 */
@Composable
fun DeleteZoneDialog(name: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    ConfirmDialog(
        title = stringResource(R.string.d03_title, name),
        message = stringResource(R.string.d03_message),
        dismissLabel = stringResource(R.string.action_cancel),
        confirmLabel = stringResource(R.string.d03_delete),
        destructive = true,
        onDismiss = onDismiss,
        onConfirm = onConfirm,
    )
}

/** S-11 화면 본체. 상태만 받아 그린다 (디버그 상태 갤러리에서도 쓴다). */
@Composable
fun ZoneEditBody(
    isEdit: Boolean,
    loading: Boolean,
    form: ZoneForm,
    nameError: Boolean,
    saving: Boolean,
    onName: (String) -> Unit,
    onType: (ZoneType) -> Unit,
    onRadius: (Float) -> Unit,
    onCenter: (com.google.android.gms.maps.model.LatLng) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit,
) {
    Scaffold(
        containerColor = AppColors.White,
        topBar = {
            AppTopBar(
                title = stringResource(if (isEdit) R.string.s11_title_edit else R.string.s11_title_add),
                navigation = TopBarNavigation.Close,
                onNavigate = onClose,
            )
        },
    ) { padding ->
        if (loading) {
            DelayedLoading { LoadingMessage(stringResource(R.string.map_loading), Modifier.padding(padding)) }
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            CenterMap(form, onCenter)
            Column(
                modifier = Modifier.padding(horizontal = Spacing.ScreenHorizontal, vertical = Spacing.L),
                verticalArrangement = Arrangement.spacedBy(Spacing.L),
            ) {
                NameField(form.name, nameError, onName)
                TypeSelector(form.type, onType)
                RadiusSlider(form.radiusM, onRadius)
                PrimaryButton(
                    text = stringResource(R.string.s11_save),
                    onClick = onSave,
                    enabled = !saving,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (isEdit) {
                    // 위험 동작은 주요 버튼과 24dp 이상 떨어뜨리고 텍스트 버튼으로 비중을 낮춘다
                    TextButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.textButtonColors(contentColor = AppColors.Error),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = Spacing.DangerGap)
                            .heightIn(min = Sizes.Button),
                    ) {
                        Icon(AppIcons.Caution, contentDescription = null, modifier = Modifier.size(Sizes.Icon))
                        Text(stringResource(R.string.s11_delete), modifier = Modifier.padding(start = Spacing.S))
                    }
                }
            }
        }
    }
}

/** 지도 가운데 십자 고정, 지도를 움직여 중심을 정한다. 반경 원 미리보기 */
@Composable
private fun CenterMap(form: ZoneForm, onCenter: (com.google.android.gms.maps.model.LatLng) -> Unit) {
    val cameraState = rememberCameraPositionState { position = CameraPosition.fromLatLngZoom(form.center, 15f) }
    val density = LocalDensity.current
    LaunchedEffect(cameraState) {
        snapshotFlow { cameraState.isMoving to cameraState.position.target }
            .filter { (moving, _) -> !moving }
            .collect { (_, target) -> onCenter(target) }
    }
    LaunchedEffect(Unit) {
        cameraState.move(CameraUpdateFactory.newLatLngZoom(form.center, 15f))
    }
    val color = if (form.type == ZoneType.Safe) AppColors.Safe else AppColors.Caution
    Box(
        Modifier
            .fillMaxWidth()
            .height((LocalConfiguration.current.screenHeightDp * 0.42f).coerceAtLeast(240f).dp)
    ) {
        AppMap(cameraPositionState = cameraState, modifier = Modifier.fillMaxSize()) {
            Circle(
                center = cameraState.position.target,
                radius = form.radiusM,
                strokeColor = color,
                strokeWidth = with(density) { MapStroke.Zone.toPx() },
                strokePattern = if (form.type == ZoneType.Caution) {
                    with(density) { listOf(Dash(MapStroke.MissingDash.toPx()), Gap(MapStroke.MissingGap.toPx())) }
                } else null,
                fillColor = color.copy(alpha = MapStroke.ZoneFillAlpha),
            )
        }
        Icon(
            if (form.type == ZoneType.Safe) AppIcons.Safe else AppIcons.Caution,
            contentDescription = null,
            tint = color,
            modifier = Modifier
                .align(Alignment.Center)
                .size(28.dp),
        )
        Text(
            stringResource(R.string.s11_map_hint),
            style = AppType.Caption,
            color = AppColors.Text,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(Spacing.M)
                .background(AppColors.White, RoundedCornerShape(8.dp))
                .padding(horizontal = Spacing.M, vertical = Spacing.Xs),
        )
    }
}

@Composable
private fun NameField(name: String, error: Boolean, onName: (String) -> Unit) {
    OutlinedTextField(
        value = name,
        onValueChange = onName,
        label = { Text(stringResource(R.string.s11_name_label)) },
        singleLine = true,
        isError = error,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        trailingIcon = if (error) {
            { Icon(AppIcons.Error, contentDescription = stringResource(R.string.cd_error), tint = AppColors.Error) }
        } else null,
        supportingText = if (error) {
            { Text(stringResource(R.string.s11_name_error), style = AppType.Caption) }
        } else null,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = AppColors.Primary,
            unfocusedBorderColor = AppColors.Text2,
            errorBorderColor = AppColors.Error,
            errorSupportingTextColor = AppColors.Error,
            errorLabelColor = AppColors.Error,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun TypeSelector(selected: ZoneType, onSelect: (ZoneType) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
        Text(stringResource(R.string.s11_type), style = AppType.Caption, color = AppColors.Text2)
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            ZoneType.entries.forEachIndexed { index, type ->
                val safe = type == ZoneType.Safe
                SegmentedButton(
                    selected = selected == type,
                    onClick = { onSelect(type) },
                    shape = SegmentedButtonDefaults.itemShape(index, ZoneType.entries.size),
                    icon = {
                        Icon(
                            if (safe) AppIcons.Safe else AppIcons.Caution,
                            contentDescription = null,
                            tint = if (safe) AppColors.Safe else AppColors.Caution,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = AppColors.Surface,
                        activeContentColor = AppColors.Text,
                        inactiveContainerColor = AppColors.White,
                        inactiveContentColor = AppColors.Text2,
                        activeBorderColor = AppColors.Text2,
                        inactiveBorderColor = AppColors.Text2,
                    ),
                    modifier = Modifier.heightIn(min = Sizes.Button),
                ) {
                    Text(stringResource(if (safe) R.string.zone_safe else R.string.zone_caution))
                }
            }
        }
    }
}

@Composable
private fun RadiusSlider(radiusM: Double, onChange: (Float) -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.s11_radius, radiusText(radiusM)), style = AppType.Body, color = AppColors.Text)
        }
        Slider(
            value = radiusM.toFloat(),
            onValueChange = onChange,
            valueRange = ZoneEditViewModel.MIN_RADIUS_M.toFloat()..ZoneEditViewModel.MAX_RADIUS_M.toFloat(),
            steps = ((ZoneEditViewModel.MAX_RADIUS_M - ZoneEditViewModel.MIN_RADIUS_M) / ZoneEditViewModel.RADIUS_STEP_M).toInt() - 1,
            colors = SliderDefaults.colors(thumbColor = AppColors.Primary, activeTrackColor = AppColors.Primary, inactiveTrackColor = AppColors.Surface),
        )
    }
}
