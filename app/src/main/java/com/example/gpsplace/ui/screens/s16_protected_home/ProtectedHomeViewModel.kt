package com.example.gpsplace.ui.screens.s16_protected_home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gpsplace.AppContainer
import com.example.gpsplace.data.local.Role
import com.example.gpsplace.data.repo.LinkState
import com.example.gpsplace.ui.ScreenState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class RecordingData(val lastCollectedAt: Long, val pendingCount: Int)

enum class RecordingErrorKind { Permission, LocationOff, Storage }

data class RecordingError(val kind: RecordingErrorKind, val lastNormalAt: Long?, val storedCount: Int)

enum class RecordingPartial { Offline }

typealias RecordingState = ScreenState<RecordingData, Unit, RecordingError, RecordingPartial>

/** S-16 위치 기록 (보호대상자 홈): 기록 상태·마지막 수집·전송 대기를 보여 준다 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProtectedHomeViewModel(private val container: AppContainer) : ViewModel() {
    private val pointDao = container.database.pointDao()

    private data class Local(
        val linked: Boolean,
        val storageFull: Boolean,
        val latestT: Long?,
        val pending: Int,
        val total: Int,
    )

    private val local = combine(
        container.settings.link,
        container.settings.storageFull,
        pointDao.observeLatest(),
        pointDao.observeUnsyncedCount(),
        pointDao.observeTotalCount(),
    ) { link, storageFull, latest, pending, total ->
        Local(
            linked = link.role == Role.Protected && link.linkActive && link.linkId != null,
            storageFull = storageFull,
            latestT = latest?.t,
            pending = pending,
            total = total,
        )
    }

    val state: StateFlow<RecordingState> = combine(local, container.recording.permissions, container.network.online) { local, permissions, online ->
        when {
            !local.linked -> ScreenState.Empty(Unit)
            local.storageFull -> ScreenState.Error(RecordingError(RecordingErrorKind.Storage, local.latestT, local.total))
            !permissions.location -> ScreenState.Error(RecordingError(RecordingErrorKind.Permission, local.latestT, local.total))
            !permissions.locationServiceOn -> ScreenState.Error(RecordingError(RecordingErrorKind.LocationOff, local.latestT, local.total))
            local.latestT == null -> ScreenState.Loading
            else -> ScreenState.Content(
                RecordingData(local.latestT, local.pending),
                partial = if (!online) RecordingPartial.Offline else null,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ScreenState.Loading)

    init {
        // 보호자가 연결을 해제하면 기록을 멈추고 비어 있음 상태로 (S-12 해제 후)
        viewModelScope.launch {
            container.settings.link
                .map { if (it.role == Role.Protected && it.linkActive) it.linkId else null }
                .distinctUntilChanged()
                .flatMapLatest { linkId -> if (linkId == null) emptyFlow() else container.links.observeLink(linkId) }
                .catch { }
                .collect { linkState ->
                    if (linkState is LinkState.Released) {
                        container.links.onReleased()
                        container.recording.stop()
                    }
                }
        }
    }

    /** 화면 진입·설정에서 복귀할 때: 권한·위치 서비스·저장 공간을 다시 확인하고 기록을 켜거나 끈다 */
    fun onResume(freeBytes: Long) {
        viewModelScope.launch {
            if (freeBytes > STORAGE_RECOVERED_BYTES) container.settings.setStorageFull(false)
            container.recording.sync()
        }
    }

    companion object {
        private const val STORAGE_RECOVERED_BYTES = 50L * 1024 * 1024
    }
}
