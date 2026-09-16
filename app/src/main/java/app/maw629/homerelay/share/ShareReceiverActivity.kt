package app.maw629.homerelay.share

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import app.maw629.homerelay.HomeRelayApplication
import java.util.UUID
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

class ShareReceiverActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val shares = ShareIntentParser.parse(intent)
        lifecycleScope.launch {
            try {
                val status = queueShares(shares)
                Toast.makeText(applicationContext, statusMessage(status), Toast.LENGTH_SHORT).show()
            } finally {
                finish()
            }
        }
    }

    private suspend fun queueShares(shares: List<IncomingShare>): ShareQueueStatus {
        val container = (application as HomeRelayApplication).container
        if (container.destinationStore.destinationTreeUri.firstOrNull() == null) {
            return ShareQueueStatus.DestinationMissing
        }

        var queuedCount = 0
        for (share in shares) {
            when (val result = container.shareStager.stage(UUID.randomUUID().toString(), share)) {
                is StageResult.Staged -> {
                    container.uploadRepository.enqueue(result, share)
                    queuedCount++
                }
                StageResult.SourceUnreadable -> return ShareQueueStatus.SourceUnreadable
                StageResult.StorageFull -> return ShareQueueStatus.StorageFull
            }
        }
        return ShareQueueStatus.Queued(queuedCount)
    }
}

sealed interface ShareQueueStatus {
    data object Preparing : ShareQueueStatus
    data class Queued(val count: Int) : ShareQueueStatus
    data object DestinationMissing : ShareQueueStatus
    data object SourceUnreadable : ShareQueueStatus
    data object StorageFull : ShareQueueStatus
}

internal fun statusMessage(status: ShareQueueStatus): String = when (status) {
    ShareQueueStatus.Preparing -> "Preparing files for Home Relay"
    is ShareQueueStatus.Queued -> "Queued ${status.count} ${if (status.count == 1) "file" else "files"} for Home Relay"
    ShareQueueStatus.DestinationMissing -> "Choose a destination in Home Relay before sharing files"
    ShareQueueStatus.SourceUnreadable -> "A shared file could not be read"
    ShareQueueStatus.StorageFull -> "Not enough storage to queue shared files"
}
