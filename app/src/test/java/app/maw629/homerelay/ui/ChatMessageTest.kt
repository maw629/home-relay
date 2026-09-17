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
        assertEquals(formatFileSize(0L), "0 bytes")
        assertEquals(formatFileSize(42L), "42 bytes")
        assertEquals(formatFileSize(999L), "999 bytes")
    }

    @Test
    fun kilobytesBoundary() {
        assertEquals(formatFileSize(1_000L),     "0.98 KB")
        assertEquals(formatFileSize(1_018L),     "0.99 KB")
        assertEquals(formatFileSize(1_019L),     "1 KB")
        assertEquals(formatFileSize(1_024L),     "1 KB")
        assertEquals(formatFileSize(1_029L),     "1 KB")
        assertEquals(formatFileSize(1_030L),     "1.01 KB")
        assertEquals(formatFileSize(5_632L),     "5.5 KB")
        assertEquals(formatFileSize(10_234L),    "9.99 KB")
        assertEquals(formatFileSize(10_235L),    "10 KB")
        assertEquals(formatFileSize(10_240L),    "10 KB")
        assertEquals(formatFileSize(10_291L),    "10 KB")
        assertEquals(formatFileSize(10_292L),    "10.1 KB")
        assertEquals(formatFileSize(101_519L),   "99.1 KB")
        assertEquals(formatFileSize(102_348L),   "99.9 KB")
        assertEquals(formatFileSize(102_349L),   "100 KB")
        assertEquals(formatFileSize(102_400L),   "100 KB")
        assertEquals(formatFileSize(102_911L),   "100 KB")
        assertEquals(formatFileSize(102_912L),   "101 KB")
        assertEquals(formatFileSize(467_455L),   "456 KB")
        assertEquals(formatFileSize(1_023_487L), "999 KB")
    }

    @Test
    fun megabytesBoundary() {
        assertEquals(formatFileSize(1_023_488L),   "0.98 MB")
        assertEquals(formatFileSize(1_043_333L),   "0.99 MB")
        assertEquals(formatFileSize(1_043_334L),   "1 MB")
        assertEquals(formatFileSize(1_048_576L),   "1 MB")
        assertEquals(formatFileSize(1_053_818L),   "1 MB")
        assertEquals(formatFileSize(1_053_819L),   "1.01 MB")
        assertEquals(formatFileSize(4_655_677L),   "4.44 MB")
        assertEquals(formatFileSize(10_480_517L),  "9.99 MB")
        assertEquals(formatFileSize(10_480_518L),  "10 MB")
        assertEquals(formatFileSize(10_485_760L),  "10 MB")
        assertEquals(formatFileSize(10_538_188L),  "10 MB")
        assertEquals(formatFileSize(10_538_189L),  "10.1 MB")
        assertEquals(formatFileSize(24_536_678L),  "23.4 MB")
        assertEquals(formatFileSize(104_805_171L), "99.9 MB")
        assertEquals(formatFileSize(104_805_172L), "100 MB")
        assertEquals(formatFileSize(104_857_600L), "100 MB")
        assertEquals(formatFileSize(105_381_887L), "100 MB")
        assertEquals(formatFileSize(105_381_888L), "101 MB")
        assertEquals(formatFileSize(361_758_720L), "345 MB")
        assertEquals(formatFileSize(1_048_051_711L), "999 MB")
    }

    @Test
    fun gigabytesBoundary() {
        assertEquals(formatFileSize(1_048_051_712L),   "0.98 GB")
        assertEquals(formatFileSize(1_068_373_114L),   "0.99 GB")
        assertEquals(formatFileSize(1_068_373_115L),   "1 GB")
        assertEquals(formatFileSize(1_073_741_824L),   "1 GB")
        assertEquals(formatFileSize(1_079_110_533L),   "1 GB")
        assertEquals(formatFileSize(1_079_110_534L),   "1.01 GB")
        assertEquals(formatFileSize(7_151_120_545L),   "6.66 GB")
        assertEquals(formatFileSize(10_732_049_530L),  "9.99 GB")
        assertEquals(formatFileSize(10_732_049_531L),  "10 GB")
        assertEquals(formatFileSize(10_737_418_240L),  "10 GB")
        assertEquals(formatFileSize(10_791_105_331L),  "10 GB")
        assertEquals(formatFileSize(10_791_105_332L),  "10.1 GB")
        assertEquals(formatFileSize(107_320_495_308L), "99.9 GB")
        assertEquals(formatFileSize(107_320_495_309L), "100 GB")
        assertEquals(formatFileSize(107_374_182_400L), "100 GB")
        assertEquals(formatFileSize(107_911_053_311L), "100 GB")
        assertEquals(formatFileSize(107_911_053_312L), "101 GB")
        assertEquals(formatFileSize(847_182_299_136L), "789 GB")
        assertEquals(formatFileSize(1_073_204_953_087L), "999 GB")
    }

    @Test
    fun terabytesBoundary() {
        assertEquals(formatFileSize(1_073_204_953_088L),    "0.98 TB")
        assertEquals(formatFileSize(1_094_014_069_637L),    "0.99 TB")
        assertEquals(formatFileSize(1_094_014_069_638L),    "1 TB")
        assertEquals(formatFileSize(1_099_511_627_776L),    "1 TB")
        assertEquals(formatFileSize(1_105_009_185_914L),    "1 TB")
        assertEquals(formatFileSize(1_105_009_185_915L),    "1.01 TB")
        assertEquals(formatFileSize(8_543_205_347_820L),    "7.77 TB")
        assertEquals(formatFileSize(10_989_618_719_621L),   "9.99 TB")
        assertEquals(formatFileSize(10_989_618_719_622L),   "10 TB")
        assertEquals(formatFileSize(10_995_116_277_760L),   "10 TB")
        assertEquals(formatFileSize(11_050_091_859_148L),   "10 TB")
        assertEquals(formatFileSize(11_050_091_859_149L),   "10.1 TB")
        assertEquals(formatFileSize(30_896_276_740_096L),   "28.1 TB")
        assertEquals(formatFileSize(109_896_187_196_210L),  "99.9 TB")
        assertEquals(formatFileSize(109_896_187_196_212L),  "100 TB")
        assertEquals(formatFileSize(109_951_162_777_600L),  "100 TB")
        assertEquals(formatFileSize(110_500_918_591_487L),  "100 TB")
        assertEquals(formatFileSize(110_500_918_591_488L),  "101 TB")
        assertEquals(formatFileSize(257_286_120_899_584L),  "234 TB")
        assertEquals(formatFileSize(1_098_961_871_962_111L), "999 TB")
        assertEquals(formatFileSize(1_098_961_871_962_112L), "1000 TB")
        assertEquals(formatFileSize(1_894_788_388_146_381L), "1723 TB")
    }

    @Test
    fun trailingZerosTrimmed() {
        assertEquals(formatFileSize(1_048_576L), "1 MB")
        assertEquals(formatFileSize(1_572_864L), "1.5 MB")
    }

    @Test
    fun negativeSizeCoercedToZero() {
        assertEquals(formatFileSize(-5L), "0 bytes")
    }

    @Test
    fun dayKeyResolvesUtcCalendarDay() {
        assertEquals(
            java.time.LocalDate.of(2001, 9, 9),
            dayKey(1_000_000_000_000L, ZoneId.of("UTC"))
        )
    }

    @Test
    fun sameDayHeaderShowsToday() {
        assertEquals(
            "Today",
            dayHeaderText(1_000_000_060_000L, 1_000_003_600_000L, ZoneId.of("UTC"))
        )
    }

    @Test
    fun previousDayHeaderShowsYesterday() {
        assertEquals(
            "Yesterday",
            dayHeaderText(1_000_000_000_000L, 1_000_086_400_000L, ZoneId.of("UTC"))
        )
    }

    @Test
    fun threeDaysAgoHeaderShowsWeekday() {
        assertEquals(
            "Sunday",
            dayHeaderText(1_000_000_000_000L, 1_000_259_200_000L, ZoneId.of("UTC"))
        )
    }

    @Test
    fun sevenDaysAgoHeaderShowsDate() {
        assertEquals(
            "2001/09/09",
            dayHeaderText(1_000_000_000_000L, 1_000_604_800_000L, ZoneId.of("UTC"))
        )
    }

    @Test
    fun futureHeaderShowsToday() {
        assertEquals(
            "Today",
            dayHeaderText(1_000_003_600_000L, 1_000_000_000_000L, ZoneId.of("UTC"))
        )
    }
}
