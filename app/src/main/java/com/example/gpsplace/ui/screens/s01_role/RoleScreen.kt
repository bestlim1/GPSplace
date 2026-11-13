package com.example.gpsplace.ui.screens.s01_role

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import com.example.gpsplace.AppContainer
import com.example.gpsplace.R
import com.example.gpsplace.data.local.Role
import com.example.gpsplace.ui.appViewModel
import com.example.gpsplace.ui.components.AppCard
import com.example.gpsplace.ui.components.AppIcons
import com.example.gpsplace.ui.theme.AppColors
import com.example.gpsplace.ui.theme.AppType
import com.example.gpsplace.ui.theme.Sizes
import com.example.gpsplace.ui.theme.Spacing
import kotlinx.coroutines.launch

class RoleViewModel(private val container: AppContainer) : ViewModel() {
    /** 역할은 기기에 바로 저장하고, 서버 저장은 앱 범위에서 끝까지 진행한다 */
    fun choose(role: Role) {
        container.appScope.launch { container.links.chooseRole(role) }
    }
}

/** S-01 시작·역할 선택 · FR-01. 뒤로 = 앱 종료. */
@Composable
fun RoleScreen(onGuardian: () -> Unit, onProtected: () -> Unit) {
    val viewModel = appViewModel { container, _ -> RoleViewModel(container) }
    Scaffold(containerColor = AppColors.White) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.ScreenHorizontal, vertical = Spacing.Section),
            verticalArrangement = Arrangement.spacedBy(Spacing.L),
        ) {
            Spacer(Modifier.height(Spacing.Xl))
            Text(stringResource(R.string.s01_heading), style = AppType.BigStatus, color = AppColors.Text)
            Text(stringResource(R.string.s01_sub), style = AppType.Body, color = AppColors.Text2)
            Spacer(Modifier.height(Spacing.S))
            RoleCard(
                icon = AppIcons.Person,
                title = stringResource(R.string.s01_guardian),
                description = stringResource(R.string.s01_guardian_desc),
                onClick = {
                    viewModel.choose(Role.Guardian)
                    onGuardian()
                },
            )
            RoleCard(
                icon = AppIcons.Place,
                title = stringResource(R.string.s01_protected),
                description = stringResource(R.string.s01_protected_desc),
                onClick = {
                    viewModel.choose(Role.Protected)
                    onProtected()
                },
            )
            Text(stringResource(R.string.s01_note), style = AppType.Caption, color = AppColors.Text2)
        }
    }
}

@Composable
private fun RoleCard(icon: ImageVector, title: String, description: String, onClick: () -> Unit) {
    AppCard(onClick = onClick) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.L),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = AppColors.Primary, modifier = Modifier.size(Sizes.TouchTarget))
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.Xs)) {
                Text(title, style = AppType.Title, color = AppColors.Text)
                Text(description, style = AppType.Body, color = AppColors.Text2)
            }
        }
    }
}
