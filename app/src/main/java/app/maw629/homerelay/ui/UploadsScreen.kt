package app.maw629.homerelay.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.maw629.homerelay.data.UploadErrorCode
import app.maw629.homerelay.data.UploadState

@Composable
fun OwnMessageBubble(
    row: UploadRow,
    onRetry: (String) -> Unit,
    onCancel: (String) -> Unit,
    onChooseFolder: () -> Unit,
    modifier: Modifier = Modifier
) {
    val status = messageStatus(row.state)
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        Surface(
            modifier = Modifier.widthIn(min = 192.dp, max = 320.dp).testTag("messageBubble"),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FileTypeBadge(
                        monogram = fileMonogram(row.name),
                        color = fileBadgeColor(fileTypeLabel(row.name))
                    )
                    Column(
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = middleEllipsize(row.name),
                            modifier = Modifier.widthIn(max = 240.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = "${fileTypeLabel(row.name)} • ${formatFileSize(row.sizeBytes)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = formatMessageTime(row.createdAtMillis),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                when (status) {
                    MessageStatus.SENDING -> {
                        Text(
                            text = if (row.state == UploadState.QUEUED) "Sending…" else "Uploading…",
                            style = MaterialTheme.typography.bodySmall
                        )
                        if (row.state == UploadState.QUEUED) {
                            TextButton(onClick = { onCancel(row.id) }) {
                                Text("Cancel")
                            }
                        }
                    }
                    MessageStatus.SENT -> {
                        Text(
                            text = "Sent ✓",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    MessageStatus.FAILED -> {
                        row.errorMessage?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        TextButton(onClick = { onRetry(row.id) }) {
                            Text("Retry")
                        }
                        if (row.errorCode == UploadErrorCode.DESTINATION_ACCESS_LOST) {
                            TextButton(onClick = onChooseFolder) {
                                Text("Choose folder again")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun UploadsScreen(
    modifier: Modifier = Modifier,
    uploads: List<UploadRow>,
    onRetry: (String) -> Unit,
    onCancel: (String) -> Unit,
    onChooseFolder: () -> Unit
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        reverseLayout = true
    ) {
        val grouped = uploads.groupBy { dayKey(it.createdAtMillis) }
        grouped.forEach { (day, dayUploads) ->
            // Messages first: with reverseLayout the later-composed header
            // lands visually above its day's messages (Messenger/Zalo order).
            items(dayUploads, key = { it.id }) { upload ->
                OwnMessageBubble(
                    row = upload,
                    onRetry = onRetry,
                    onCancel = onCancel,
                    onChooseFolder = onChooseFolder
                )
            }
            item(key = "day-$day") {
                DayHeader(text = dayHeaderText(dayUploads.first().createdAtMillis))
            }
        }
        if (uploads.isEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "No uploads yet — shared files will appear here like messages",
                        modifier = Modifier.testTag("emptyState")
                    )
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier.testTag("senderAvatar"),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.secondary
                    ) {
                        // Fixed square size: CircleShape on a glyph-sized box
                        // draws an ellipse, so center the initial in a square.
                        Box(
                            modifier = Modifier.size(36.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "M",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                    Text(
                        text = "Me",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
                Text(
                    text = "Home Relay",
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = "Recent uploads",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
