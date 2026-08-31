package app.maw629.homerelay.work

import android.content.Context
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.maw629.homerelay.data.DestinationStore
import app.maw629.homerelay.data.UploadDao
import app.maw629.homerelay.data.UploadErrorCode
import app.maw629.homerelay.data.UploadItem
import app.maw629.homerelay.data.UploadState
import app.maw629.homerelay.destination.DestinationGateway
import app.maw629.homerelay.destination.DestinationResult
import app.maw629.homerelay.notifications.UploadNotificationSink
import java.io.File
import kotlinx.coroutines.flow.firstOrNull

class UploadWorker(
    appContext: Context,
    params: WorkerParameters,
    private val dao: UploadDao,
    private val destinationStore: DestinationStore,
    private val gateway: DestinationGateway,
    private val notifier: UploadNotificationSink
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val itemId = inputData.getString(UPLOAD_ITEM_ID) ?: return Result.failure()
        val item = dao.get(itemId) ?: return Result.failure()
        if (item.state != UploadState.QUEUED || dao.beginUpload(item.id) == 0) return Result.success()
        val destinationUri = destinationStore.destinationTreeUri.firstOrNull()?.let(Uri::parse)
            ?: return needsAttention(item, UploadErrorCode.DESTINATION_ACCESS_LOST)
        val source = File(item.stagedPath)
        if (!source.isFile) return needsAttention(item, UploadErrorCode.SOURCE_UNREADABLE)

        val hasForegroundProgress = item.byteSize >= FOREGROUND_PROGRESS_THRESHOLD_BYTES
        var lastForegroundUpdate = 0L
        if (hasForegroundProgress) setForeground(notifier.foregroundInfo(item, 0, item.byteSize))

        return when (
            val result = gateway.write(destinationUri, source, item.mimeType, item.outputName) { copied ->
                if (hasForegroundProgress &&
                    (copied == item.byteSize || copied - lastForegroundUpdate >= PROGRESS_UPDATE_BYTES)
                ) {
                    lastForegroundUpdate = copied
                    setForeground(notifier.foregroundInfo(item, copied, item.byteSize))
                }
            }
        ) {
            DestinationResult.Success -> complete(item)
            DestinationResult.TransientFailure -> {
                if (dao.finishUpload(item.id, UploadState.QUEUED, UploadErrorCode.NONE, runAttemptCount + 1) == 1) {
                    Result.retry()
                } else {
                    Result.success()
                }
            }
            DestinationResult.AccessLost -> needsAttention(item, UploadErrorCode.DESTINATION_ACCESS_LOST)
            is DestinationResult.PermanentFailure -> needsAttention(item, result.errorCode)
            DestinationResult.UnknownWriteOutcome -> needsAttention(item, UploadErrorCode.WRITE_OUTCOME_UNKNOWN)
        }
    }

    private suspend fun complete(item: UploadItem): Result {
        if (dao.finishUpload(item.id, UploadState.COMPLETED, UploadErrorCode.NONE, item.retryCount) == 0) {
            return Result.success()
        }
        File(item.stagedPath).delete()
        notifier.completed(item)
        return Result.success()
    }

    private suspend fun needsAttention(item: UploadItem, error: UploadErrorCode): Result {
        if (dao.finishUpload(item.id, UploadState.NEEDS_ATTENTION, error, item.retryCount) == 0) {
            return Result.success()
        }
        notifier.needsAttention(item, error)
        return Result.success()
    }

    companion object {
        const val UPLOAD_ITEM_ID = "upload_item_id"
        private const val FOREGROUND_PROGRESS_THRESHOLD_BYTES = 10L * 1024 * 1024
        private const val PROGRESS_UPDATE_BYTES = 1024L * 1024
    }
}
