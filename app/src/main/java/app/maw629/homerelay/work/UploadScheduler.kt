package app.maw629.homerelay.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

interface UploadScheduler {
    suspend fun schedule(uploadItemId: String)
    suspend fun cancel(uploadItemId: String)
}

class WorkManagerUploadScheduler(context: Context) : UploadScheduler {
    private val appContext = context.applicationContext

    override suspend fun schedule(uploadItemId: String) {
        val request = OneTimeWorkRequestBuilder<UploadWorker>()
            .setInputData(workDataOf(UploadWorker.UPLOAD_ITEM_ID to uploadItemId))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(appContext).enqueueUniqueWork(
            workName(uploadItemId),
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    override suspend fun cancel(uploadItemId: String) {
        WorkManager.getInstance(appContext).cancelUniqueWork(workName(uploadItemId))
    }

    private fun workName(uploadItemId: String) = "upload:$uploadItemId"
}
