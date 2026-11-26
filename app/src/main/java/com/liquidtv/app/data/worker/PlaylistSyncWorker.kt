package com.liquidtv.app.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.liquidtv.app.domain.repository.SubscriptionRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

@HiltWorker
class PlaylistSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val subscriptionRepository: SubscriptionRepository,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        if (!subscriptionRepository.hasAnySubscription()) {
            return Result.success()
        }
        val syncResult = subscriptionRepository.syncActiveSubscription()
        subscriptionRepository.syncActiveEpg()
        return if (syncResult.isSuccess) Result.success() else Result.retry()
    }

    companion object {
        private const val WORK_NAME = "liquidtv_auto_playlist_sync"

        fun schedule(context: Context, enabled: Boolean, intervalHours: Int) {
            val workManager = WorkManager.getInstance(context)
            if (!enabled) {
                workManager.cancelUniqueWork(WORK_NAME)
                return
            }
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = PeriodicWorkRequestBuilder<PlaylistSyncWorker>(
                intervalHours.coerceIn(1, 168).toLong(),
                TimeUnit.HOURS,
            )
                .setConstraints(constraints)
                .build()
            workManager.enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }
    }
}
