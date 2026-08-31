package app.maw629.homerelay.share

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AbstractSavedStateViewModelFactory
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import app.maw629.homerelay.BuildConfig
import app.maw629.homerelay.HomeRelayApplication
import app.maw629.homerelay.ui.theme.HomeRelayTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class ShareReceiverActivity : ComponentActivity() {
    private var intakeStatus by mutableStateOf<ShareIntakeStatus>(ShareIntakeStatus.Preparing)
    private lateinit var backCallback: OnBackPressedCallback

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val operations = (application as HomeRelayApplication).container.shareIntakeCoordinator
        val factory = object : AbstractSavedStateViewModelFactory(this, null) {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(
                key: String,
                modelClass: Class<T>,
                handle: SavedStateHandle
            ): T = ShareIntakeViewModel(handle, operations) as T
        }
        val viewModel = ViewModelProvider(this, factory)[ShareIntakeViewModel::class.java]

        backCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = Unit
        }
        onBackPressedDispatcher.addCallback(this, backCallback)
        setContent {
            ShareQueueOverlay(intakeStatus)
            val terminalStatus = intakeStatus as? ShareIntakeStatus.Terminal
            if (terminalStatus != null) {
                LaunchedEffect(terminalStatus.terminalAtMillis) {
                    val remainingMillis = terminalDisplayRemainingMillis(
                        terminalAtMillis = terminalStatus.terminalAtMillis,
                        displayDurationMillis = BuildConfig.SHARE_STATUS_DISPLAY_MILLIS,
                        nowMillis = System.currentTimeMillis()
                    )
                    delay(remainingMillis)
                    finish()
                }
            }
        }
        lifecycleScope.launch {
            viewModel.status.collect { status ->
                intakeStatus = status
                backCallback.isEnabled = status is ShareIntakeStatus.Preparing
            }
        }
        viewModel.beginOrAttach(
            if (viewModel.hasSavedIntake) emptyList() else ShareIntentParser.parse(intent)
        )
    }
}

internal fun terminalDisplayRemainingMillis(
    terminalAtMillis: Long,
    displayDurationMillis: Long,
    nowMillis: Long
): Long {
    require(displayDurationMillis >= 0L)
    if (nowMillis <= terminalAtMillis) return displayDurationMillis

    val elapsedMillis = nowMillis - terminalAtMillis
    if (elapsedMillis < 0L) return 0L

    return maxOf(0L, displayDurationMillis - elapsedMillis)
}

sealed interface ShareQueueStatus {
    data object Preparing : ShareQueueStatus
    data class Queued(val count: Int) : ShareQueueStatus
    data class Mixed(val queuedCount: Int, val attentionCount: Int) : ShareQueueStatus
    data object DestinationMissing : ShareQueueStatus
    data object SourceUnreadable : ShareQueueStatus
    data object StorageFull : ShareQueueStatus
    data object ShareInterrupted : ShareQueueStatus
    data object QueueUnavailable : ShareQueueStatus
}

@Composable
internal fun ShareQueueScreen(
    status: ShareIntakeStatus,
    darkTheme: Boolean = androidx.compose.foundation.isSystemInDarkTheme(),
    dynamicColor: Boolean = true
) {
    ShareQueueScreen(
        status = status.toQueueStatus(),
        darkTheme = darkTheme,
        dynamicColor = dynamicColor
    )
}

@Composable
internal fun ShareQueueScreen(
    status: ShareQueueStatus,
    darkTheme: Boolean = androidx.compose.foundation.isSystemInDarkTheme(),
    dynamicColor: Boolean = true
) {
    ShareQueueOverlay(status, darkTheme, dynamicColor)
}

@Composable
internal fun ShareQueueOverlay(
    status: ShareIntakeStatus,
    darkTheme: Boolean = androidx.compose.foundation.isSystemInDarkTheme(),
    dynamicColor: Boolean = true
) {
    ShareQueueOverlay(status.toQueueStatus(), darkTheme, dynamicColor)
}

@Composable
internal fun ShareQueueOverlay(
    status: ShareQueueStatus,
    darkTheme: Boolean = androidx.compose.foundation.isSystemInDarkTheme(),
    dynamicColor: Boolean = true
) {
    HomeRelayTheme(darkTheme = darkTheme, dynamicColor = dynamicColor) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .padding(24.dp)
                    .widthIn(max = 360.dp)
                    .testTag("share-status-card")
            ) {
                Text(
                    text = when (status) {
                        ShareQueueStatus.Preparing -> "Preparing files for Home Relay"
                        is ShareQueueStatus.Queued -> "Queued ${status.count} ${if (status.count == 1) "file" else "files"} for Home Relay"
                        is ShareQueueStatus.Mixed -> "Queued ${status.queuedCount} ${if (status.queuedCount == 1) "file" else "files"}; ${status.attentionCount} need attention"
                        ShareQueueStatus.DestinationMissing -> "Choose a destination in Home Relay before sharing files"
                        ShareQueueStatus.SourceUnreadable -> "A shared file could not be read"
                        ShareQueueStatus.StorageFull -> "Not enough storage to queue shared files"
                        ShareQueueStatus.ShareInterrupted -> "Share the file again."
                        ShareQueueStatus.QueueUnavailable -> "Home Relay could not save the shared file"
                    },
                    modifier = Modifier.padding(24.dp),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}

internal fun ShareIntakeStatus.toQueueStatus(): ShareQueueStatus = when (this) {
    ShareIntakeStatus.Preparing -> ShareQueueStatus.Preparing
    is ShareIntakeStatus.Terminal -> when {
        queuedCount > 0 && attentionCount == 0 && queueUnavailableCount == 0 -> {
            ShareQueueStatus.Queued(queuedCount)
        }
        queuedCount > 0 -> ShareQueueStatus.Mixed(queuedCount, attentionCount + queueUnavailableCount)
        queueUnavailableCount > 0 -> ShareQueueStatus.QueueUnavailable
        attentionErrors == setOf(app.maw629.homerelay.data.UploadErrorCode.SHARE_INTERRUPTED) -> {
            ShareQueueStatus.ShareInterrupted
        }
        attentionErrors == setOf(app.maw629.homerelay.data.UploadErrorCode.DESTINATION_ACCESS_LOST) -> {
            ShareQueueStatus.DestinationMissing
        }
        attentionErrors == setOf(app.maw629.homerelay.data.UploadErrorCode.STAGING_STORAGE_FULL) -> {
            ShareQueueStatus.StorageFull
        }
        attentionErrors == setOf(app.maw629.homerelay.data.UploadErrorCode.SOURCE_UNREADABLE) -> {
            ShareQueueStatus.SourceUnreadable
        }
        else -> ShareQueueStatus.QueueUnavailable
    }
}
