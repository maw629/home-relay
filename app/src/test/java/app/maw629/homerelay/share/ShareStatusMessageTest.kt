package app.maw629.homerelay.share

import org.junit.Assert.assertEquals
import org.junit.Test

class ShareStatusMessageTest {
    @Test
    fun preparingMessage() {
        assertEquals("Preparing files for Home Relay", statusMessage(ShareQueueStatus.Preparing))
    }

    @Test
    fun singleFileQueuedMessage() {
        assertEquals("Queued 1 file for Home Relay", statusMessage(ShareQueueStatus.Queued(1)))
    }

    @Test
    fun multipleFilesQueuedMessage() {
        assertEquals("Queued 3 files for Home Relay", statusMessage(ShareQueueStatus.Queued(3)))
    }

    @Test
    fun destinationMissingMessage() {
        assertEquals(
            "Choose a destination in Home Relay before sharing files",
            statusMessage(ShareQueueStatus.DestinationMissing)
        )
    }

    @Test
    fun sourceUnreadableMessage() {
        assertEquals("A shared file could not be read", statusMessage(ShareQueueStatus.SourceUnreadable))
    }

    @Test
    fun storageFullMessage() {
        assertEquals("Not enough storage to queue shared files", statusMessage(ShareQueueStatus.StorageFull))
    }
}
