package app.maw629.homerelay.share

import app.maw629.homerelay.data.UploadErrorCode
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ShareQueueStatusTest {
    @Test
    fun queuePersistenceFailureTakesPriorityOverSpecificAllFailureMessage() {
        val status = ShareIntakeStatus.Terminal(
            queuedCount = 0,
            attentionCount = 1,
            queueUnavailableCount = 1,
            attentionErrors = setOf(UploadErrorCode.SOURCE_UNREADABLE),
            terminalAtMillis = 1
        )

        assertEquals(ShareQueueStatus.QueueUnavailable, status.toQueueStatus())
    }
}
