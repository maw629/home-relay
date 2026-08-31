package app.maw629.homerelay.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.ForegroundInfo
import app.maw629.homerelay.R
import app.maw629.homerelay.data.UploadErrorCode
import app.maw629.homerelay.data.UploadItem

interface UploadNotificationSink {
    fun foregroundInfo(item: UploadItem, copied: Long, total: Long): ForegroundInfo
    fun queued(item: UploadItem)
    fun uploading(item: UploadItem)
    fun completed(item: UploadItem)
    fun needsAttention(item: UploadItem, error: UploadErrorCode)
}

class UploadNotifier(private val context: Context) : UploadNotificationSink {
    companion object {
        const val CHANNEL_ID = "home_relay_uploads"
        private const val FOREGROUND_NOTIFICATION_ID = 1001
    }

    fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Home Relay uploads",
            NotificationManager.IMPORTANCE_LOW
        )
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    override fun foregroundInfo(item: UploadItem, copied: Long, total: Long): ForegroundInfo {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_home_relay)
            .setContentTitle("Uploading ${item.originalName}")
            .setProgress(total.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), copied.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), false)
            .setOngoing(true)
            .build()
        return ForegroundInfo(
            FOREGROUND_NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
    }

    override fun queued(item: UploadItem) = post(item.id.hashCode(), "Queued for Home Relay", item.originalName)

    override fun uploading(item: UploadItem) = post(item.id.hashCode(), "Uploading to Home Relay", item.originalName)

    override fun completed(item: UploadItem) = post(item.id.hashCode(), "Saved to Home Relay", item.originalName)

    override fun needsAttention(item: UploadItem, error: UploadErrorCode) = post(
        item.id.hashCode(),
        "Home Relay needs attention",
        "${item.originalName}: ${error.name.lowercase().replace('_', ' ')}"
    )

    private fun post(id: Int, title: String, text: String) {
        if (NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            NotificationManagerCompat.from(context).notify(
                id,
                NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_stat_home_relay)
                    .setContentTitle(title)
                    .setContentText(text)
                    .setAutoCancel(true)
                    .build()
            )
        }
    }
}
