package com.arcadia.shell.scraper

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScraperScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val workManager get() = WorkManager.getInstance(context)

    /**
     * Enqueued as unique work with [ExistingWorkPolicy.KEEP], so repeatedly asking to scrape while a
     * pass is already running does not stack up duplicate jobs fighting over the same rows.
     */
    fun enqueue(replace: Boolean = false) {
        val request = OneTimeWorkRequestBuilder<ScrapeWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .build()

        workManager.enqueueUniqueWork(
            ScrapeWorker.UNIQUE_NAME,
            if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
            request,
        )
    }

    fun cancel() {
        workManager.cancelUniqueWork(ScrapeWorker.UNIQUE_NAME)
    }

    fun isRunning(): Flow<Boolean> = workManager
        .getWorkInfosForUniqueWorkFlow(ScrapeWorker.UNIQUE_NAME)
        .map { infos -> infos.any { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED } }
}
