package app.maw629.homerelay.ui

import app.maw629.homerelay.data.UploadState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

enum class MessageStatus {
    SENDING,
    SENT,
    FAILED
}

fun messageStatus(state: UploadState): MessageStatus = when (state) {
    UploadState.QUEUED, UploadState.UPLOADING -> MessageStatus.SENDING
    UploadState.COMPLETED -> MessageStatus.SENT
    UploadState.NEEDS_ATTENTION -> MessageStatus.FAILED
    // Unreachable: CANCELLED rows are filtered out of the conversation
    // upstream in HomeRelayViewModel.uploads; FAILED is the safe fallback.
    UploadState.CANCELLED -> MessageStatus.FAILED
}

private val messageTimeFormat = DateTimeFormatter.ofPattern("HH:mm")
private val messageDateFormat = DateTimeFormatter.ofPattern("yyyy/MM/dd")

// Unit rollover points: 999.5 of the current unit, so a displayed value
// never rounds up to "1000 <unit>".
private const val KB_ROLLOVER_BYTES = 1_023_488L // 999.5 × 1024
private const val MB_ROLLOVER_BYTES = 1_048_051_712L // 999.5 × 1024²
private const val GB_ROLLOVER_BYTES = 1_073_204_953_088L // 999.5 × 1024³

fun formatFileSize(sizeBytes: Long): String {
    val bytes = sizeBytes.coerceAtLeast(0L)
    if (bytes < 1_000L) return "$bytes bytes"
    val (divisor, unit) = when {
        bytes < KB_ROLLOVER_BYTES -> 1_024.0 to "KB"
        bytes < MB_ROLLOVER_BYTES -> 1_048_576.0 to "MB"
        bytes < GB_ROLLOVER_BYTES -> 1_073_741_824.0 to "GB"
        else -> 1_099_511_627_776.0 to "TB"
    }
    val value = bytes / divisor
    val decimals = when {
        value < 10.0 -> 2
        value < 100.0 -> 1
        else -> 0
    }
    val text = String.format(Locale.ENGLISH, "%.${decimals}f", value).trimEnd('0').trimEnd('.')
    return "$text $unit"
}

fun formatMessageTime(
    createdAtMillis: Long,
    nowMillis: Long = System.currentTimeMillis(),
    zone: ZoneId = ZoneId.systemDefault()
): String {
    val created = Instant.ofEpochMilli(createdAtMillis).atZone(zone)
    val daysAgo = ChronoUnit.DAYS.between(created.toLocalDate(), Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()).toInt()
    val time = created.format(messageTimeFormat)
    return when {
        daysAgo <= 0 -> time
        daysAgo == 1 -> "Yesterday $time"
        daysAgo < 7 -> "${created.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)} $time"
        else -> "${created.format(messageDateFormat)} $time"
    }
}
