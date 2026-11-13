package com.example.gpsplace.ui.screens.s13_code

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gpsplace.AppContainer
import com.example.gpsplace.R
import com.example.gpsplace.data.repo.ClaimResult
import com.example.gpsplace.ui.appViewModel
import com.example.gpsplace.ui.components.AppIcons
import com.example.gpsplace.ui.components.AppTopBar
import com.example.gpsplace.ui.components.PrimaryButton
import com.example.gpsplace.ui.components.TopBarNavigation
import com.example.gpsplace.ui.theme.AppColors
import com.example.gpsplace.ui.theme.AppType
import com.example.gpsplace.ui.theme.Corners
import com.example.gpsplace.ui.theme.Sizes
import com.example.gpsplace.ui.theme.Spacing
import kotlinx.coroutines.launch

enum class CodeError { None, Invalid, Offline }

class CodeEntryViewModel(private val container: AppContainer, private val saved: SavedStateHandle) : ViewModel() {
    var code by mutableStateOf(saved.get<String>("code").orEmpty())
        private set
    var error by mutableStateOf(CodeError.None)
        private set
    var checking by mutableStateOf(false)
        private set

    fun onCodeChange(value: String) {
        code = value.filter { it.isDigit() }.take(6)
        saved["code"] = code
    }

    /** [다음]: 코드 확인 → S-14. 틀리면 입력값을 유지하고 오류 표시 */
    fun submit(onValid: (String) -> Unit) {
        if (code.length != 6 || checking) return
        checking = true
        viewModelScope.launch {
            val result = container.links.claimCode(code)
            checking = false
            error = when (result) {
                ClaimResult.Ok -> CodeError.None.also { onValid(code) }
                ClaimResult.Invalid -> CodeError.Invalid
                ClaimResult.Offline -> CodeError.Offline
            }
        }
    }
}

/** S-13 연결 코드 입력 · 보호대상자 · FR-01. 본문 18sp, 6칸(3+3)이지만 입력 필드는 하나 */
@Composable
fun CodeEntryScreen(onBack: () -> Unit, onNext: (String) -> Unit) {
    val vm = appViewModel { container, saved -> CodeEntryViewModel(container, saved) }
    CodeEntryBody(vm.code, vm.error, vm.checking, vm::onCodeChange, onSubmit = { vm.submit(onNext) }, onBack = onBack)
}

/** S-13 화면 본체. 상태만 받아 그린다 (디버그 상태 갤러리에서도 쓴다). */
@Composable
fun CodeEntryBody(
    code: String,
    error: CodeError,
    checking: Boolean,
    onCodeChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        containerColor = AppColors.White,
        topBar = { AppTopBar(title = stringResource(R.string.s13_title), navigation = TopBarNavigation.Back, onNavigate = onBack) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.ScreenHorizontal, vertical = Spacing.Section),
            verticalArrangement = Arrangement.spacedBy(Spacing.L),
        ) {
            Text(stringResource(R.string.s13_guide), style = AppType.BodyProtected, color = AppColors.Text)
            CodeField(
                code = code,
                isError = error != CodeError.None,
                onChange = onCodeChange,
                onDone = onSubmit,
            )
            if (error != CodeError.None) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalAlignment = Alignment.Top) {
                    Icon(AppIcons.Error, contentDescription = stringResource(R.string.cd_error), tint = AppColors.Error, modifier = Modifier.size(Sizes.Icon))
                    Text(
                        stringResource(if (error == CodeError.Offline) R.string.s13_error_offline else R.string.s13_error),
                        style = AppType.BodyProtected,
                        color = AppColors.Error,
                    )
                }
            }
            Text(stringResource(R.string.s13_valid), style = AppType.BodyProtected, color = AppColors.Text2)
            Spacer(Modifier.heightIn(min = Spacing.L))
            PrimaryButton(
                text = stringResource(R.string.s13_next),
                onClick = onSubmit,
                enabled = code.length == 6 && !checking,
                protectedUser = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** 숫자 키패드 하나로 받고, 화면에는 3칸 + 3칸으로 그린다. 오류면 테두리를 두 배로 */
@Composable
private fun CodeField(code: String, isError: Boolean, onChange: (String) -> Unit, onDone: () -> Unit) {
    val description = stringResource(R.string.cd_code_field)
    val errorText = stringResource(R.string.s13_error)
    BasicTextField(
        value = code,
        onValueChange = onChange,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = description
                if (isError) error(errorText)
            },
        decorationBox = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                for (i in 0 until 6) {
                    if (i == 3) Spacer(Modifier.width(Spacing.L))
                    val filled = i < code.length
                    val focused = i == code.length.coerceAtMost(5)
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 3.dp)
                            .widthIn(min = 40.dp)
                            .heightIn(min = 56.dp)
                            .border(
                                width = if (isError) 2.dp else if (focused) 2.dp else 1.dp,
                                color = if (isError) AppColors.Error else if (focused) AppColors.Primary else AppColors.Text2,
                                shape = RoundedCornerShape(Corners.Button),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(if (filled) code[i].toString() else "", style = AppType.BigStatus, color = AppColors.Text)
                    }
                }
            }
        },
    )
}
