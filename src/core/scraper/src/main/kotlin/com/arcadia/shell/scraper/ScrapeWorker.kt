package com.arcadia.shell.scraper

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * Drains the scrape queue in bounded batches.
 *
 * Dependencies are pulled from an entry point rather than through `@HiltWorker`. Constructor
 * injection for workers requires replacing WorkManager's default initializer with a custom
 * factory, which is a lot of moving parts for a single worker, and any mistake in that wiring only
 * shows up as a runtime crash.
 */
class ScrapeWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ScrapeWorkerEntryPoint {
        fun scrapeCoordinator(): ScrapeCoordinator
    }

    override suspend fun doWork(): Result {
        val coordinator = EntryPointAccessors
            .fromApplication(applicationContext, ScrapeWorkerEntryPoint::class.java)
            .scrapeCoordinator()

        repeat(MAX_BATCHES_PER_RUN) {
            val result = runCatching { coordinator.scrapeBatch() }.getOrElse {
                // Retrying lets WorkManager apply its own backoff, which is the right response to
                // a rate limit or a network blip.
                return Result.retry()
            }

            if (result.credentialsMissing) return Result.success()
            if (result.processed == 0 || !result.hasMore) return Result.success()
        }

        // Work is left over, so the run ends and asks to be retried instead of holding a single
        // long-lived job that the system becomes steadily more likely to kill. Progress is durable
        // because every scraped game is marked in the database as it completes.
        return Result.retry()
    }

    companion object {
        const val UNIQUE_NAME = "arcadia-scrape"

        /** At the coordinator's batch size this is 500 games per run. */
        private const val MAX_BATCHES_PER_RUN = 20
    }
}
