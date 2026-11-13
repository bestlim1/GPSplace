package com.example.gpsplace.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.gpsplace.GPSplaceApp
import com.example.gpsplace.data.repo.UploadOutcome
import java.util.concurrent.TimeUnit

/** 미전송 기록 전송 (FR-06). 네트워크가 연결되면 실행되고, 실패하면 1분부터 지수 백오프로 다시 시도한다. */
class UploadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as GPSplaceApp).container
        return when (container.sync.uploadAll()) {
            UploadOutcome.Done, UploadOutcome.Stopped -> Result.success()
            UploadOutcome.Retry -> Result.retry()
        }
    }

    companion object {
        private const val NAME = "upload"

        /** 새 점이 생길 때마다 부른다. 이미 대기 중이면 그대로 두고, 실행 중인 작업이 남은 점까지 이어서 보낸다. */
        fun enqueue(context: Context, now: Boolean = false) {
            val request = OneTimeWorkRequestBuilder<UploadWorker>()
                .setConstraints(Constraints(requiredNetworkType = NetworkType.CONNECTED))
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                NAME,
                if (now) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
                request,
            )
        }
    }
}
