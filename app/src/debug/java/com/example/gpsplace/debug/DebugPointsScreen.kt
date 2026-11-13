package com.example.gpsplace.debug

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import com.example.gpsplace.GPSplaceApp
import com.example.gpsplace.data.local.PointEntity
import com.example.gpsplace.data.remote.hourPoints
import com.example.gpsplace.domain.SeoulTime
import com.example.gpsplace.sync.UploadWorker
import com.example.gpsplace.ui.components.AppTopBar
import com.example.gpsplace.ui.components.PrimaryButton
import com.example.gpsplace.ui.components.TopBarNavigation
import com.example.gpsplace.ui.theme.AppColors
import com.example.gpsplace.ui.theme.AppType
import com.example.gpsplace.ui.theme.Spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File

// 디버그 전용 (PLAN.md M1·M4): 오늘 점 목록과 간격(초), 정확도 50m 이내 비율, CSV 내보내기,
// 서버 시간 문서의 점 합계(= 로컬 점 수인지, 다시 올려도 늘지 않는지 확인).

const val DEBUG_POINTS_ROUTE = "debug/points"

private data class PointsReport(
    val points: List<PointEntity>,
    val unsynced: Int,
    val within50: Int,
    val intervals: List<Long>,
    val serverCount: Int?,
)

@Composable
fun DebugPointsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val container = (context.applicationContext as GPSplaceApp).container
    val scope = rememberCoroutineScope()
    var report by remember { mutableStateOf<PointsReport?>(null) }

    suspend fun load() {
        val today = SeoulTime.today(container.clock())
        val points = container.database.pointDao().pointsSince(SeoulTime.startOfDay(today))
        val intervals = points.zipWithNext { a, b -> (b.t - a.t) / 1000 }
        val linkId = container.settings.currentLink().linkId
        val serverCount = linkId?.let {
            runCatching {
                container.firebase.pointHours(it).whereEqualTo("dayKey", SeoulTime.dayKey(today)).get().await()
                    .documents.sumOf { doc -> doc.hourPoints().size }
            }.getOrNull()
        }
        report = PointsReport(
            points = points,
            unsynced = points.count { it.syncedAt == null },
            within50 = points.count { it.accuracy <= 50f },
            intervals = intervals,
            serverCount = serverCount,
        )
    }

    LaunchedEffect(Unit) { load() }

    Scaffold(
        containerColor = AppColors.White,
        topBar = { AppTopBar(title = "오늘 위치 점 (디버그)", navigation = TopBarNavigation.Back, onNavigate = onBack) },
    ) { padding ->
        val r = report
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            item {
                Column(Modifier.padding(Spacing.ScreenHorizontal), verticalArrangement = Arrangement.spacedBy(Spacing.Xs)) {
                    if (r == null) {
                        Text("불러오는 중", style = AppType.Body)
                    } else {
                        val n = r.points.size
                        Text("로컬 점 ${n}건 · 미전송 ${r.unsynced}건", style = AppType.Body)
                        Text("서버 점 ${r.serverCount ?: "확인 불가"}건 (오늘 시간 문서 합계)", style = AppType.Body)
                        Text("정확도 50m 이내 ${r.within50}/$n (${if (n == 0) 0 else r.within50 * 100 / n}%)", style = AppType.Body)
                        if (r.intervals.isNotEmpty()) {
                            val i = r.intervals
                            Text(
                                "간격 ≤35초 ${i.count { it <= 35 }} · 36~60초 ${i.count { it in 36..60 }} · " +
                                    "61~120초 ${i.count { it in 61..120 }} · 120초 초과 ${i.count { it > 120 }} · 최대 ${i.max()}초",
                                style = AppType.Caption,
                                color = AppColors.Text2,
                            )
                        }
                        PrimaryButton(
                            text = "CSV 내보내기",
                            onClick = { scope.launch { shareCsv(context, r.points) } },
                            modifier = Modifier.fillMaxWidth().padding(top = Spacing.S),
                        )
                        PrimaryButton(
                            text = "새로 고침",
                            onClick = { scope.launch { load() } },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        PrimaryButton(
                            text = "오늘 기록 전부 다시 보내기 (중복 시험)",
                            onClick = {
                                scope.launch {
                                    container.database.pointDao().markDayUnsynced(SeoulTime.dayKey(container.clock()))
                                    UploadWorker.enqueue(context, now = true)
                                    load()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
            val rows = r?.points?.reversed().orEmpty()
            items(rows.take(300), key = { it.id }) { p ->
                val index = r!!.points.indexOf(p)
                val gap = if (index > 0) (p.t - r.points[index - 1].t) / 1000 else null
                Text(
                    "${SeoulTime.hm(p.t)}:${(p.t / 1000 % 60).toString().padStart(2, '0')} · ±${p.accuracy.toInt()}m · " +
                        "${gap?.let { "간격 ${it}초" } ?: "첫 점"} · ${if (p.syncedAt == null) "미전송" else "전송됨"}",
                    style = AppType.Caption,
                    color = if (gap != null && gap > 120) AppColors.Error else AppColors.Text,
                    modifier = Modifier.padding(horizontal = Spacing.ScreenHorizontal, vertical = Spacing.Xs),
                )
                HorizontalDivider(color = AppColors.Divider)
            }
        }
    }
}

/** TEST_LOG.md에 붙일 수 있게 CSV로 공유 (t, 시각, 위도, 경도, 정확도, 간격, 전송) */
private suspend fun shareCsv(context: Context, points: List<PointEntity>) {
    val file = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val today = points.firstOrNull()?.dayKey ?: "empty"
        File(dir, "points_$today.csv").apply {
            bufferedWriter().use { out ->
                out.write("t,time_kst,lat,lng,accuracy_m,interval_s,synced\n")
                points.forEachIndexed { i, p ->
                    val gap = if (i > 0) (p.t - points[i - 1].t) / 1000 else ""
                    out.write("${p.t},${SeoulTime.hm(p.t)},${p.lat},${p.lng},${p.accuracy},$gap,${p.syncedAt != null}\n")
                }
            }
        }
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.debugfiles", file)
    context.startActivity(
        Intent.createChooser(
            Intent(Intent.ACTION_SEND).setType("text/csv").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
            "CSV 내보내기",
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}
