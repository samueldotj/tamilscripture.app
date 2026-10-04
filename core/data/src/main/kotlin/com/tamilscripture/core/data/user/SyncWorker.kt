package com.tamilscripture.core.data.user

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.tamilscripture.core.data.GraphHost
import com.tamilscripture.core.data.net.HttpException
import java.util.concurrent.TimeUnit

/**
 * Account sync (M6-6): soon after a change, when the app starts, and twice a day. Only with
 * a connection; a failed sync retries with backoff, and changes wait on the device meanwhile.
 */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val graph = (applicationContext as? GraphHost)?.graph ?: return Result.failure()
        return try {
            graph.userData.sync()
            Result.success()
        } catch (e: HttpException) {
            // 401/403: the session is gone; the account screen shows it. Others retry.
            if (e.code == 401 || e.code == 403) Result.failure() else Result.retry()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val ONCE = "account-sync"
        private const val PERIODIC = "account-sync-periodic"
        private val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        /** A sync in a few seconds; changes made in the meantime join it. */
        fun syncSoon(context: Context, delaySeconds: Long = 5) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                ONCE, ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<SyncWorker>().setConstraints(online).setInitialDelay(delaySeconds, TimeUnit.SECONDS)
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build(),
            )
        }

        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC, ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<SyncWorker>(12, TimeUnit.HOURS).setConstraints(online).build(),
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(ONCE)
            WorkManager.getInstance(context).cancelUniqueWork(PERIODIC)
        }
    }
}
