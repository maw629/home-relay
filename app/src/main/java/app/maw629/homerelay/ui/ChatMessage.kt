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
