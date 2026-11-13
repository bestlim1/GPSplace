package com.example.gpsplace.ui.screens.s02_link

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gpsplace.AppContainer
import com.example.gpsplace.data.repo.CodeState
import com.example.gpsplace.data.repo.IssuedCode
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

sealed interface LinkRequestUi {
    data object Loading : LinkRequestUi
    data class Waiting(val code: String, val expiresAt: Long) : LinkRequestUi
    data object Linked : LinkRequestUi
    data class Failed(val denied: Boolean, val code: String) : LinkRequestUi
    data class IssueError(val offline: Boolean) : LinkRequestUi
}

/** S-02 연결 요청: 코드 발급 → 동의 대기 → 완료/거부/만료 */
class LinkRequestViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow<LinkRequestUi>(LinkRequestUi.Loading)
    val state: StateFlow<LinkRequestUi> = _state.asStateFlow()
    private var watchJob: Job? = null
    private var currentCode: String? = null

    init {
        viewModelScope.launch {
            val local = container.settings.currentLink()
            val pending = local.pendingCode
            if (pending != null && local.pendingCodeExpiresAt > container.clock()) {
                watch(IssuedCode(pending, local.pendingCodeExpiresAt))
            } else {
                issue()
            }
        }
    }

    /** [새 코드 만들기]: 이전 코드는 cancelled */
    fun newCode() {
        viewModelScope.launch { issue() }
    }

    private suspend fun issue() {
        _state.value = LinkRequestUi.Loading
        watchJob?.cancel()
        try {
            watch(container.links.issueCode(previous = currentCode))
        } catch (e: Exception) {
            _state.value = LinkRequestUi.IssueError(offline = !container.network.online.value)
        }
    }

    private fun watch(issued: IssuedCode) {
        currentCode = issued.code
        _state.value = LinkRequestUi.Waiting(issued.code, issued.expiresAt)
        watchJob?.cancel()
        watchJob = viewModelScope.launch {
            container.links.observeCode(issued.code)
                .catch { /* 오프라인 등: 대기 화면을 유지하고 남은 시간으로 만료를 판단한다 */ }
                .collect { codeState ->
                    when (codeState) {
                        is CodeState.Linked -> {
                            container.links.completeAsGuardian(codeState.linkId)
                            _state.value = LinkRequestUi.Linked
                        }
                        CodeState.Denied -> fail(denied = true, issued.code)
                        CodeState.Expired -> fail(denied = false, issued.code)
                        CodeState.Cancelled, CodeState.Waiting -> Unit
                    }
                }
        }
    }

    /** 남은 시간이 0이 되면 화면에서 부른다 */
    fun onCountdownFinished() {
        val waiting = _state.value as? LinkRequestUi.Waiting ?: return
        viewModelScope.launch { fail(denied = false, waiting.code) }
    }

    private suspend fun fail(denied: Boolean, code: String) {
        watchJob?.cancel()
        container.settings.setPendingCode(null, 0)
        _state.value = LinkRequestUi.Failed(denied, code)
    }

    /** D-04 [요청 취소] */
    fun cancelRequest(onDone: () -> Unit) {
        val code = currentCode
        watchJob?.cancel()
        container.appScope.launch { if (code != null) container.links.cancelCode(code) }
        onDone()
    }
}
