package app.maw629.homerelay.domain

import app.maw629.homerelay.data.UploadDao
import app.maw629.homerelay.data.UploadErrorCode
import app.maw629.homerelay.data.UploadItem
import app.maw629.homerelay.data.UploadState
import app.maw629.homerelay.share.IncomingShare
import app.maw629.homerelay.share.StageResult
import app.maw629.homerelay.notifications.UploadNotificationSink
import app.maw629.homerelay.work.UploadScheduler
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class UploadRepository(
    private val dao: UploadDao,
    private val scheduler: UploadScheduler,
    private val newId: () -> String,
    private val nowMillis: () -> Long,
    private val randomSuffix: () -> String,
    private val notifier: UploadNotificationSink? = null
) {
    suspend fun enqueue(staged: StageResult.Staged, share: IncomingShare): String {
        val id = newId()
        val item = UploadItem(
            id = id,
            originalName = staged.displayName,
            mimeType = share.mimeType,
            outputName = OutputNameFactory.create(staged.displayName, nowMillis(), randomSuffix()),
            stagedPath = staged.file.absolutePath,
            byteSize = staged.byteSize,
            createdAtMillis = nowMillis(),
            retryCount = 0,
            state = UploadState.QUEUED,
            errorCode = UploadErrorCode.NONE
        )
        dao.insert(item)
        scheduler.schedule(id)
        notifier?.queued(item)
        return id
    }

    suspend fun createStaging(
        share: IncomingShare,
        stagedPathForId: (String) -> String
    ): UploadItem {
        val id = newId()
        val stagedPath = stagedPathForId(id)
        val item = UploadItem(
            id = id,
            originalName = share.displayName,
            mimeType = share.mimeType,
            outputName = OutputNameFactory.create(share.displayName, nowMillis(), randomSuffix()),
            stagedPath = stagedPath,
            byteSize = 0L,
            createdAtMillis = nowMillis(),
            retryCount = 0,
            state = UploadState.STAGING,
            errorCode = UploadErrorCode.NONE
        )
        dao.insert(item)
        return item
    }

    suspend fun completeStaging(item: UploadItem, staged: StageResult.Staged): Boolean {
        val outputName = OutputNameFactory.create(staged.displayName, nowMillis(), randomSuffix())
        if (
            dao.completeStaging(
                item.id,
                staged.displayName,
                outputName,
                staged.byteSize
            ) == 0
        ) {
            return false
        }

        val queuedItem = item.copy(
            originalName = staged.displayName,
            outputName = outputName,
            byteSize = staged.byteSize,
            state = UploadState.QUEUED,
            errorCode = UploadErrorCode.NONE
        )
        runCatching { scheduler.schedule(item.id) }
        runCatching { notifier?.queued(queuedItem) }
        return true
    }

    suspend fun failStaging(id: String, error: UploadErrorCode): Boolean =
        dao.failStaging(id, error) == 1

    suspend fun recoverInterruptedStaging(): Int {
        var recoveredCount = 0
        dao.stagingItems().forEach { item ->
            if (dao.failStaging(item.id, UploadErrorCode.SHARE_INTERRUPTED) == 1) {
                File(item.stagedPath).delete()
                recoveredCount++
            }
        }
        return recoveredCount
    }

    suspend fun retry(id: String) {
        operationMutex.withLock {
            val item = checkNotNull(dao.get(id))
            check(item.state == UploadState.NEEDS_ATTENTION)
            check(item.errorCode.isRetryable())
            check(File(item.stagedPath).isFile)
            check(
                dao.retry(
                    id,
                    OutputNameFactory.create(item.originalName, nowMillis(), randomSuffix()),
                    item.retryCount + 1
                ) == 1
            )
            scheduler.schedule(id)
        }
    }

    suspend fun cancel(id: String) {
        operationMutex.withLock {
            val item = checkNotNull(dao.get(id))
            check(item.state in CANCELLABLE_STATES)
            check(dao.cancel(id) == 1)
            scheduler.cancel(id)
            File(item.stagedPath).delete()
        }
    }

    fun observeUploads(): Flow<List<UploadItem>> = dao.observeAll()

    suspend fun resumePending() {
        operationMutex.withLock {
            recoverInterruptedStaging()
            dao.requeueInterruptedUploads()
            dao.queuedIds().forEach { scheduler.schedule(it) }
        }
    }

    private companion object {
        val operationMutex = Mutex()
        val CANCELLABLE_STATES = setOf(
            UploadState.QUEUED,
            UploadState.UPLOADING,
            UploadState.NEEDS_ATTENTION
        )
    }
}

fun UploadErrorCode.isRetryable(): Boolean = when (this) {
    UploadErrorCode.NONE,
    UploadErrorCode.SOURCE_UNREADABLE,
    UploadErrorCode.STAGING_STORAGE_FULL,
    UploadErrorCode.SHARE_INTERRUPTED -> false
    else -> true
}
