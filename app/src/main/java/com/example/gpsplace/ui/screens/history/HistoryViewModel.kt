package com.example.gpsplace.ui.screens.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gpsplace.AppContainer
import com.example.gpsplace.data.local.Role
import com.example.gpsplace.data.remote.Fetched
import com.example.gpsplace.domain.SeoulTime
import com.example.gpsplace.domain.Stay
import com.example.gpsplace.domain.TrackPoint
import com.example.gpsplace.ui.ScreenState
import com.example.gpsplace.ui.timeoutToError
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

data class DayData(
    val date: LocalDate,
    val points: List<TrackPoint>,
    /** 시작 시각 순. 번호 = 순서 + 1 */
    val stays: List<Stay>,
)

enum class DayError { Timeout, OfflineNoData }

enum class DayPartial { Offline }

typealias DayState = ScreenState<DayData, LocalDate, DayError, DayPartial>

/** 기록 탭(S-04 경로 · S-05 체류)이 같은 날짜를 공유한다 (FR-05). */
@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModel(private val container: AppContainer) : ViewModel() {
    private val date = MutableStateFlow<LocalDate?>(null)
    private val retry = MutableStateFlow(0)

    val today: LocalDate get() = SeoulTime.today(container.clock())

    private val linkId = container.settings.link
        .map { if (it.role == Role.Guardian && it.linkActive) it.linkId else null }
        .distinctUntilChanged()

    val state: StateFlow<DayState> = combine(linkId, date.filterNotNull(), retry) { id, d, _ -> id to d }
        .flatMapLatest { (id, d) ->
            if (id == null) flowOf(ScreenState.Empty(d)) else observeDay(id, d).timeoutToError(DayError.Timeout)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ScreenState.Loading)

    fun setDate(value: LocalDate) {
        date.value = value
    }

    fun retry() {
        retry.value++
    }

    private fun observeDay(linkId: String, day: LocalDate): Flow<DayState> {
        val dayKey = SeoulTime.dayKey(day)
        val live = day == today
        val points = container.tracks.observeDayPoints(linkId, dayKey, live)
            .catch { emit(Fetched(emptyList(), fromCache = true)) }
        val stays = container.tracks.observeDayStays(linkId, dayKey)
            .catch { emit(Fetched(emptyList(), fromCache = true)) }
        return combine(points, stays, container.network.online) { p, s, online ->
            val fromCache = p.fromCache || s.fromCache
            when {
                p.data.isEmpty() && p.fromCache && online -> ScreenState.Loading
                p.data.isEmpty() && !p.fromCache -> ScreenState.Empty(day)
                p.data.isEmpty() -> ScreenState.Error(DayError.OfflineNoData)
                else -> ScreenState.Content(
                    DayData(day, p.data, s.data),
                    partial = if (fromCache && !online) DayPartial.Offline else null,
                )
            }
        }
    }
}
