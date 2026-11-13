package com.example.gpsplace.ui.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.example.gpsplace.R
import com.example.gpsplace.ui.theme.AppColors
import com.example.gpsplace.ui.theme.AppType

enum class TopBarNavigation { None, Back, Close }

/** 앱바: 제목 + ←(또는 ×) + 오른쪽 동작(⚙, [구역 관리] 등). 상단에는 자주 안 쓰는 동작만 둔다. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    title: String,
    modifier: Modifier = Modifier,
    navigation: TopBarNavigation = TopBarNavigation.None,
    onNavigate: () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = { Text(title, style = AppType.Title) },
        modifier = modifier,
        navigationIcon = {
            when (navigation) {
                TopBarNavigation.None -> Unit
                TopBarNavigation.Back -> IconButton(onClick = onNavigate) {
                    Icon(AppIcons.Back, contentDescription = stringResource(R.string.cd_back))
                }
                TopBarNavigation.Close -> IconButton(onClick = onNavigate) {
                    Icon(AppIcons.Close, contentDescription = stringResource(R.string.cd_close))
                }
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = AppColors.White,
            titleContentColor = AppColors.Text,
            navigationIconContentColor = AppColors.Text,
            actionIconContentColor = AppColors.Text,
        ),
    )
}

/** 앱바 ⚙ → S-12 연결 관리 */
@Composable
fun SettingsAction(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(AppIcons.Settings, contentDescription = stringResource(R.string.cd_settings))
    }
}
