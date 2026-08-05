package com.arcadia.shell.scraper

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * Hashes ROMs for RetroAchievements across the library.
 *
 * Input [KEY_REHASH_ALL] = true recomputes every hashable ROM; otherwise only missing MD5s.
 */
class LibraryHashWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface LibraryHashWorkerEntryPoint {
        fun libraryHashCoordinator(): LibraryHashCoordinator
    }

    override suspend fun doWork(): Result {
        val coordinator = EntryPointAccessors
            .fromApplication(applicationContext, LibraryHashWorkerEntryPoint::class.java)
            .libraryHashCoordinator()
        val rehashAll = inputData.getBoolean(KEY_REHASH_ALL, false)
        var offset = inputData.getInt(KEY_OFFSET, 0)

        var totalHashed = 0
        val startedAt = System.currentTimeMillis()

        while (System.currentTimeMillis() - startedAt < MAX_RUNTIME_MS) {
            val result = runCatching {
                coordinator.hashBatch(rehashAll = rehashAll, offset = offset)
            }.getOrElse {
                return Result.retry()
            }

            totalHashed += result.hashed
            offset = result.nextOffset
            setProgress(
                workDataOf(
                    KEY_PROGRESS_HASHED to totalHashed,
                    KEY_PROGRESS_REMAINING to result.remainingMissing,
                    KEY_OFFSET to offset,
                ),
            )

            if (result.processed == 0 || !result.hasMore) {
                return Result.success(
                    workDataOf(
                        KEY_PROGRESS_HASHED to totalHashed,
                        KEY_PROGRESS_REMAINING to result.remainingMissing,
                    ),
                )
            }
        }

        // Continue in a follow-up job with the advanced offset (REPLACE would cancel this
        // finishing worker; APPEND after unique name keeps the chain ordered).
        val continuation = OneTimeWorkRequestBuilder<LibraryHashWorker>()
            .setInputData(
                workDataOf(
                    KEY_REHASH_ALL to rehashAll,
                    KEY_OFFSET to offset,
                ),
            )
            .build()
        WorkManager.getInstance(applicationContext).enqueueUniqueWork(
            UNIQUE_NAME,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            continuation,
        )
        return Result.success(
            workDataOf(
                KEY_PROGRESS_HASHED to totalHashed,
                KEY_PROGRESS_REMAINING to -1,
                KEY_OFFSET to offset,
            ),
        )
    }

    companion object {
        const val UNIQUE_NAME = "xora-library-hash"
        const val KEY_REHASH_ALL = "rehash_all"
        const val KEY_OFFSET = "offset"
        const val KEY_PROGRESS_HASHED = "hashed"
        const val KEY_PROGRESS_REMAINING = "remaining"
        private const val MAX_RUNTIME_MS = 15 * 60 * 1000L
    }
}
