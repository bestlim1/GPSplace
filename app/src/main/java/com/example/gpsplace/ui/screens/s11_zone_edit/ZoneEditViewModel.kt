package com.example.gpsplace.ui.screens.s11_zone_edit

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gpsplace.AppContainer
import com.example.gpsplace.data.remote.toRemoteLink
import com.example.gpsplace.domain.ZoneType
import com.example.gpsplace.nav.Routes
import com.example.gpsplace.ui.components.SEOUL
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

data class ZoneForm(val name: String, val type: ZoneType, val radiusM: Double, val center: LatLng)

/** S-11 구역 추가·편집 (폼) · FR-04. 이름 1~10자, 반경 50m~2km, 중심은 지도 가운데 */
class ZoneEditViewModel(private val container: AppContainer, saved: SavedStateHandle) : ViewModel() {
    val zoneId: String? = saved.get<String>(Routes.ARG_ZONE_ID)
    val isEdit: Boolean get() = zoneId != null

    var loading by mutableStateOf(true)
        private set
    var form by mutableStateOf(ZoneForm("", ZoneType.Safe, DEFAULT_RADIUS_M, SEOUL))
        private set
    var nameError by mutableStateOf(false)
        private set
    var saving by mutableStateOf(false)
        private set
    private var initial: ZoneForm? = null

    val changed: Boolean get() = !loading && initial != null && form != initial

    init {
        viewModelScope.launch {
            val linkId = container.settings.currentLink().linkId
            val loaded = withTimeoutOrNull(10_000) {
                if (linkId == null) return@withTimeoutOrNull null
                if (zoneId != null) {
                    container.zones.zone(linkId, zoneId)?.let { ZoneForm(it.name, it.type, it.radiusM, LatLng(it.lat, it.lng)) }
                } else {
                    // 새 구역은 보호대상자의 최신 위치에서 시작한다
                    val link = runCatching { container.firebase.link(linkId).get().await().toRemoteLink() }.getOrNull()
                    val center = link?.lastPoint?.let { LatLng(it.lat, it.lng) } ?: SEOUL
                    ZoneForm("", ZoneType.Safe, DEFAULT_RADIUS_M, center)
                }
            } ?: form
            form = loaded
            initial = loaded
            loading = false
        }
    }

    fun onName(value: String) {
        form = form.copy(name = value)
        if (nameError && isValidName(value)) nameError = false
    }

    fun onType(type: ZoneType) {
        form = form.copy(type = type)
    }

    fun onRadius(meters: Float) {
        form = form.copy(radiusM = (meters / RADIUS_STEP_M).toInt() * RADIUS_STEP_M)
    }

    fun onCenter(center: LatLng) {
        if (!loading) form = form.copy(center = center)
    }

    /** 이름이 비었거나 10자를 넘으면 저장하지 않고 안내만 한다 (다른 입력은 유지) */
    fun save(onSaved: () -> Unit) {
        if (!isValidName(form.name)) {
            nameError = true
            return
        }
        if (saving) return
        saving = true
        viewModelScope.launch {
            val linkId = container.settings.currentLink().linkId
            if (linkId != null) {
                container.zones.save(
                    linkId = linkId,
                    zoneId = zoneId,
                    name = form.name.trim(),
                    type = form.type,
                    lat = form.center.latitude,
                    lng = form.center.longitude,
                    radiusM = form.radiusM,
                )
            }
            saving = false
            onSaved()
        }
    }

    fun delete(onDeleted: () -> Unit) {
        val id = zoneId ?: return
        viewModelScope.launch {
            container.settings.currentLink().linkId?.let { container.zones.delete(it, id) }
            onDeleted()
        }
    }

    companion object {
        const val MIN_RADIUS_M = 50.0
        const val MAX_RADIUS_M = 2000.0
        const val RADIUS_STEP_M = 50.0
        const val DEFAULT_RADIUS_M = 200.0

        fun isValidName(name: String) = name.trim().length in 1..10
    }
}
