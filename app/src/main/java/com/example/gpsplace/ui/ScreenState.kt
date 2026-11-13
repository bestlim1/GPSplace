package com.example.gpsplace.ui

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch

/**
 * 모든 데이터 화면의 상태 (UI 보고서 4장).
 * Error는 “무엇이 실패 + 원인 + 다음 행동”, Partial은 데이터 위 배너 + 기준 시각.
 */
sealed interface ScreenState<out T, out E, out C, out P> {
    data object Loading : ScreenState<Nothing, Nothing, Nothing, Nothing>
    data class Empty<E>(val reason: E) : ScreenState<Nothing, E, Nothing, Nothing>
    data class Error<C>(val cause: C) : ScreenState<Nothing, Nothing, C, Nothing>
    data class Content<T, P>(val data: T, val partial: P? = null) : ScreenState<T, Nothing, Nothing, P>
}

/** 10초 안에 결과가 없으면 오류로 바꾼다. 이미 데이터를 보였다면 그대로 둔다. */
fun <T, E, C, P> Flow<ScreenState<T, E, C, P>>.timeoutToError(
    cause: C,
    timeoutMs: Long = LOAD_TIMEOUT_MS,
): Flow<ScreenState<T, E, C, P>> = channelFlow {
    var settled = false
    val timer = launch {
        delay(timeoutMs)
        if (!settled) send(ScreenState.Error(cause))
    }
    collect { state ->
        if (state !is ScreenState.Loading) {
            settled = true
            timer.cancel()
        }
        send(state)
    }
}

/** 로딩 표시는 요청 후 300ms가 지나도 결과가 없을 때부터 */
const val LOADING_DELAY_MS = 300L
const val LOAD_TIMEOUT_MS = 10_000L
