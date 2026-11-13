package com.example.gpsplace.ui.screens.s03_home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gpsplace.AppContainer
import com.example.gpsplace.data.local.Role
import com.example.gpsplace.data.remote.Fetched
import com.example.gpsplace.data.remote.RemoteLink
import com.example.gpsplace.data.repo.LinkState
import com.example.gpsplace.domain.DelayPolicy
import com.example.gpsplace.domain.SeoulTime
import com.example.gpsplace.domain.Stay
import com.example.gpsplace.domain.TrackPoint
import com.example.gpsplace.domain.Zone
import com.example.gpsplace.ui.ScreenState
import com.example.gpsplace.ui.components.RecordStatus
import com.example.gpsplace.ui.timeoutToError
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

data class HomeData(
    val link: RemoteLink,
    val lastPoint: TrackPoint?,
    val points: List<TrackPoint>,
    val stays: List<Stay>,
    val zones: List<Zone>,
    val alertsToday: Int,
    val now: Long,
    val delayed: Boolean,
    val chip: RecordStatus,
)

enum class HomeEmpty { NoLink, NoFirstLocation }

sealed interface HomePartial {
    data class Offline(val receivedAt: Long?) : HomePartial
    data object PermissionOff : HomePartial
    data object ServiceOff : HomePartial
    data object Pending : HomePartial
    data class Delayed(val minutes: Long) : HomePartial
}

typealias HomeState = ScreenState<HomeData, HomeEmpty, Unit, HomePartial>

/**
 * S-03 위치 (보호자 홈) · FR-02, FR-04, NFR-05.
 * links 문서와 오늘 시간 문서에 스냅샷 리스너를 붙여 바뀐 것만 받는다 (읽기 아끼기).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GuardianHomeViewModel(private val container: AppContainer) : ViewModel() {
    private val retry = MutableStateFlow(0)

    /** 30초마다 “n분 전”·갱신 지연을 다시 계산 */
    private val ticker: Flow<Long> = flow {
        while (true) {
            emit(container.clock())
            delay(30_000)
        }
    }

    private val linkId: Flow<String?> = container.settings.link
        .map { if (it.role == Role.Guardian && it.linkActive) it.linkId else null }
        .distinctUntilChanged()

    val state: StateFlow<HomeState> = combine(linkId, retry) { id, _ -> id }
        .flatMapLatest { id -> if (id == null) flowOf(ScreenState.Empty(HomeEmpty.NoLink)) else observe(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ScreenState.Loading)

    private fun observe(linkId: String): Flow<HomeState> {
        val today = SeoulTime.dayKey(container.clock())
        val points = container.tracks.observeDayPoints(linkId, today, live = true)
            .catch { emit(Fetched(emptyList(), fromCache = true)) }
            .onStart { emit(Fetched(emptyList(), fromCache = true)) }
        val stays = container.tracks.observeDayStays(linkId, today)
            .catch { emit(Fetched(emptyList(), fromCache = true)) }
            .onStart { emit(Fetched(emptyList(), fromCache = true)) }
        val zones = container.zones.observeZones(linkId)
            .catch { emit(Fetched(emptyList(), fromCache = true)) }
            .onStart { emit(Fetched(emptyList(), fromCache = true)) }
        val alertsToday = container.tracks.observeAlerts(linkId)
            .map { fetched -> fetched.data.count { SeoulTime.dayKey(it.createdAt) == today } }
            .catch { emit(0) }
            .onStart { emit(0) }

        val data = combine(points, stays, zones, alertsToday) { p, s, z, a -> Quad(p, s, z, a) }

        return combine(container.links.observeLink(linkId), data, container.network.online, ticker) { linkState, d, online, now ->
            when (linkState) {
                LinkState.Released -> {
                    container.links.onReleased()
                    ScreenState.Empty(HomeEmpty.NoLink)
                }
                is LinkState.Failed -> ScreenState.Loading
                is LinkState.Active -> build(linkState, d, online, now)
            }
        }.timeoutToError(Unit)
    }

    private data class Quad(
        val points: Fetched<List<TrackPoint>>,
        val stays: Fetched<List<Stay>>,
        val zones: Fetched<List<Zone>>,
        val alertsToday: Int,
    )

    private fun build(linkState: LinkState.Active, d: Quad, online: Boolean, now: Long): HomeState {
        val link = linkState.link
        val lastPoint = listOfNotNull(link.lastPoint, d.points.data.lastOrNull()).maxByOrNull { it.t }
        if (lastPoint == null) return ScreenState.Empty(HomeEmpty.NoFirstLocation)
        val delayed = DelayPolicy.isDelayed(lastPoint.t, now)
        val notRecording = !link.locationGranted || !link.serviceOn
        val chip = when {
            notRecording -> RecordStatus.NotRecording
            link.pendingCount > 0 -> RecordStatus.PendingUpload
            delayed -> RecordStatus.Delayed
            else -> RecordStatus.Recording
        }
        val partial = when {
            !online || linkState.fromCache -> HomePartial.Offline(lastPoint.t)
            !link.locationGranted -> HomePartial.PermissionOff
            !link.serviceOn -> HomePartial.ServiceOff
            link.pendingCount > 0 -> HomePartial.Pending
            delayed -> HomePartial.Delayed(DelayPolicy.elapsedMinutes(lastPoint.t, now))
            else -> null
        }
        return ScreenState.Content(
            HomeData(
                link = link,
                lastPoint = lastPoint,
                points = d.points.data,
                stays = d.stays.data,
                zones = d.zones.data,
                alertsToday = d.alertsToday,
                now = now,
                delayed = delayed,
                chip = chip,
            ),
            partial,
        )
    }

    fun retry() {
        retry.value++
    }
}
