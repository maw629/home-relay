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

    @Test
    fun bytesBelowThresholdShowByteCount() {
        assertEquals("0 bytes", formatFileSize(0L))
        assertEquals("42 bytes", formatFileSize(42L))
        assertEquals("999 bytes", formatFileSize(999L))
    }

    @Test
    fun kilobytesBoundary() {
        assertEquals("0.98 KB", formatFileSize(1_000L))
        assertEquals("1 KB", formatFileSize(1_024L))
        assertEquals("9.99 KB", formatFileSize(10_230L))
        assertEquals("10.1 KB", formatFileSize(10_383L))
        assertEquals("99.1 KB", formatFileSize(101_519L))
        assertEquals("111 KB", formatFileSize(113_664L))
        assertEquals("921 KB", formatFileSize(943_104L))
        assertEquals("977 KB", formatFileSize(999_999L))
        assertEquals("977 KB", formatFileSize(1_000_000L))
        assertEquals("999 KB", formatFileSize(1_023_487L))
    }

    @Test
    fun megabytesBoundary() {
        assertEquals("0.98 MB", formatFileSize(1_023_488L))
        assertEquals("0.98 MB", formatFileSize(1_024_000L))
        assertEquals("0.98 MB", formatFileSize(1_024_001L))
        assertEquals("0.99 MB", formatFileSize(1_038_336L))
        assertEquals("1 MB", formatFileSize(1_048_576L))
        assertEquals("954 MB", formatFileSize(999_999_999L))
        assertEquals("954 MB", formatFileSize(1_000_000_000L))
        assertEquals("999 MB", formatFileSize(1_048_051_711L))
    }

    @Test
    fun gigabytesBoundary() {
        assertEquals("0.98 GB", formatFileSize(1_048_051_712L))
        assertEquals("0.98 GB", formatFileSize(1_048_576_000L))
        assertEquals("0.98 GB", formatFileSize(1_048_576_001L))
        assertEquals("2.33 GB", formatFileSize(2_500_000_000L))
        assertEquals("931 GB", formatFileSize(1_000_000_000_000L))
        assertEquals("999 GB", formatFileSize(1_073_204_953_087L))
    }

    @Test
    fun terabytesBoundary() {
        assertEquals("0.98 TB", formatFileSize(1_073_204_953_088L))
        assertEquals("0.98 TB", formatFileSize(1_073_741_824_000L))
        assertEquals("0.98 TB", formatFileSize(1_073_741_824_001L))
    }

    @Test
    fun trailingZerosTrimmed() {
        assertEquals("1 MB", formatFileSize(1_048_576L))
        assertEquals("1.5 MB", formatFileSize(1_572_864L))
    }

    @Test
    fun negativeSizeCoercedToZero() {
        assertEquals("0 bytes", formatFileSize(-5L))
    }
}
