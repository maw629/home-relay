package app.maw629.homerelay.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
            modifier = Modifier.testTag("messageBubble"),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = row.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = formatFileSize(row.sizeBytes),
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = formatMessageTime(row.createdAtMillis),
                    style = MaterialTheme.typography.bodySmall
                )
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
        items(uploads, key = { it.id }) { upload ->
            OwnMessageBubble(
                row = upload,
                onRetry = onRetry,
                onCancel = onCancel,
                onChooseFolder = onChooseFolder
            )
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Home Relay")
                Text("Recent uploads")
            }
        }
    }
}
