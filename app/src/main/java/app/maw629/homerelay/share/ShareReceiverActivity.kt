package app.maw629.homerelay.share

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import app.maw629.homerelay.HomeRelayApplication
import app.maw629.homerelay.ui.theme.HomeRelayTheme
import java.util.UUID
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

class ShareReceiverActivity : ComponentActivity() {
    private var queueStatus by mutableStateOf<ShareQueueStatus>(ShareQueueStatus.Preparing)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val shares = ShareIntentParser.parse(intent)
        setContent { ShareQueueScreen(queueStatus) }
        lifecycleScope.launch { queueStatus = queueShares(shares) }
    }

    private suspend fun queueShares(shares: List<IncomingShare>): ShareQueueStatus {
        val container = (application as HomeRelayApplication).container
        if (container.destinationStore.destinationTreeUri.firstOrNull() == null) {
            return ShareQueueStatus.DestinationMissing
        }

        var queuedCount = 0
        for (share in shares) {
            val target = container.shareStager.pendingFile(UUID.randomUUID().toString())
            when (val result = container.shareStager.stage(target, share)) {
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

@Composable
internal fun ShareQueueScreen(
    status: ShareQueueStatus,
    darkTheme: Boolean = androidx.compose.foundation.isSystemInDarkTheme(),
    dynamicColor: Boolean = true
) {
    HomeRelayTheme(darkTheme = darkTheme, dynamicColor = dynamicColor) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                Text(
                    when (status) {
                        ShareQueueStatus.Preparing -> "Preparing files for Home Relay"
                        is ShareQueueStatus.Queued -> "Queued ${status.count} ${if (status.count == 1) "file" else "files"} for Home Relay"
                        ShareQueueStatus.DestinationMissing -> "Choose a destination in Home Relay before sharing files"
                        ShareQueueStatus.SourceUnreadable -> "A shared file could not be read"
                        ShareQueueStatus.StorageFull -> "Not enough storage to queue shared files"
                    }
                )
            }
        }
    }
}
