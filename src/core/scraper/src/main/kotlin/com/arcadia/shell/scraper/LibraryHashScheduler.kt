package com.arcadia.shell.scraper

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LibraryHashScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val workManager get() = WorkManager.getInstance(context)

    /**
     * @param rehashAll recompute every ROM hash (use after hash-rule fixes). Default only fills
     *   missing MD5s so a post-scan pass stays cheap.
     */
    fun enqueue(rehashAll: Boolean = false, replace: Boolean = false, offset: Int = 0) {
        val request = OneTimeWorkRequestBuilder<LibraryHashWorker>()
            .setInputData(
                workDataOf(
                    LibraryHashWorker.KEY_REHASH_ALL to rehashAll,
                    LibraryHashWorker.KEY_OFFSET to offset,
                ),
            )
            .build()

        workManager.enqueueUniqueWork(
            LibraryHashWorker.UNIQUE_NAME,
            if (replace || rehashAll) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
            request,
        )
    }

    fun cancel() {
        workManager.cancelUniqueWork(LibraryHashWorker.UNIQUE_NAME)
    }

    fun isRunning(): Flow<Boolean> = workManager
        .getWorkInfosForUniqueWorkFlow(LibraryHashWorker.UNIQUE_NAME)
        .map { infos ->
            infos.any { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED }
        }
}
