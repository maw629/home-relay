package app.maw629.homerelay.ui

import app.maw629.homerelay.data.UploadState
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatMessageTest {
    @Test
    fun queuedMapsToSending() {
        assertEquals(MessageStatus.SENDING, messageStatus(UploadState.QUEUED))
    }

    @Test
    fun uploadingMapsToSending() {
        assertEquals(MessageStatus.SENDING, messageStatus(UploadState.UPLOADING))
    }

    @Test
    fun completedMapsToSent() {
        assertEquals(MessageStatus.SENT, messageStatus(UploadState.COMPLETED))
    }

    @Test
    fun needsAttentionMapsToFailed() {
        assertEquals(MessageStatus.FAILED, messageStatus(UploadState.NEEDS_ATTENTION))
    }

    @Test
    fun cancelledFallsBackToFailed() {
        assertEquals(MessageStatus.FAILED, messageStatus(UploadState.CANCELLED))
    }

    @Test
    fun epochZeroFormatsToMidnightUtc() {
        assertEquals("00:00", formatMessageTime(0L, 1_000_000L, ZoneId.of("UTC")))
    }

    @Test
    fun knownInstantFormatsToUtcWallTime() {
        assertEquals("01:46", formatMessageTime(1_000_000_000_000L, 1_000_003_600_000L, ZoneId.of("UTC")))
    }

    @Test
    fun todayShowsTimeOnly() {
        assertEquals("01:47", formatMessageTime(1_000_000_060_000L, 1_000_003_600_000L, ZoneId.of("UTC")))
    }

    @Test
    fun yesterdayShowsYesterdayAndTime() {
        assertEquals(
            "Yesterday 01:46",
            formatMessageTime(1_000_000_000_000L, 1_000_086_400_000L, ZoneId.of("UTC"))
        )
    }

    @Test
    fun sixDaysAgoShowsWeekdayAndTime() {
        assertEquals(
            "Saturday 01:46",
            formatMessageTime(999_913_600_000L, 1_000_432_000_000L, ZoneId.of("UTC"))
        )
    }

    @Test
    fun threeDaysAgoShowsWeekdayAndTime() {
        assertEquals(
            "Sunday 01:46",
            formatMessageTime(1_000_000_000_000L, 1_000_259_200_000L, ZoneId.of("UTC"))
        )
    }

    @Test
    fun sevenDaysAgoShowsDateAndTime() {
        assertEquals(
            "2001/09/09 01:46",
            formatMessageTime(1_000_000_000_000L, 1_000_604_800_000L, ZoneId.of("UTC"))
        )
    }

    @Test
    fun tenDaysAgoShowsDateAndTime() {
        assertEquals(
            "2001/09/09 01:46",
            formatMessageTime(1_000_000_000_000L, 1_000_864_000_000L, ZoneId.of("UTC"))
        )
    }

    @Test
    fun futureTimestampShowsTimeOnly() {
        assertEquals("02:46", formatMessageTime(1_000_003_600_000L, 1_000_000_000_000L, ZoneId.of("UTC")))
    }
}
