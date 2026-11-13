package com.example.gpsplace.debug

import android.content.Intent
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController

// 릴리스 빌드에는 디버그 화면 목록을 넣지 않는다. src/debug의 같은 파일과 시그니처를 맞춘다.

@Suppress("UNUSED_PARAMETER")
fun debugStartRoute(intent: Intent?): String? = null

@Suppress("UNUSED_PARAMETER")
fun NavGraphBuilder.debugScreens(navController: NavHostController) = Unit
