package app.maw629.homerelay.domain

import app.maw629.homerelay.data.UploadDao
import app.maw629.homerelay.data.UploadErrorCode
import app.maw629.homerelay.data.UploadItem
import app.maw629.homerelay.data.UploadState
import app.maw629.homerelay.share.IncomingShare
import app.maw629.homerelay.share.StageResult
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
    private val randomSuffix: () -> String
) {
    suspend fun enqueue(staged: StageResult.Staged, share: IncomingShare): String {
        val id = newId()
        val item = UploadItem(
            id = id,
            originalName = share.displayName,
            mimeType = share.mimeType,
            outputName = OutputNameFactory.create(share.displayName, nowMillis(), randomSuffix()),
            stagedPath = staged.file.absolutePath,
            byteSize = staged.byteSize,
            createdAtMillis = nowMillis(),
            retryCount = 0,
            state = UploadState.QUEUED,
            errorCode = UploadErrorCode.NONE
        )
        dao.insert(item)
        scheduler.schedule(id)
        return id
    }

    suspend fun retry(id: String) {
        operationMutex.withLock {
            val item = checkNotNull(dao.get(id))
            check(item.state == UploadState.NEEDS_ATTENTION)
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

    private companion object {
        val operationMutex = Mutex()
        val CANCELLABLE_STATES = setOf(
            UploadState.QUEUED,
            UploadState.UPLOADING,
            UploadState.NEEDS_ATTENTION
        )
    }
}
