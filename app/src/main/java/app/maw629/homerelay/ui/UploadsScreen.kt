package app.maw629.homerelay.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.maw629.homerelay.data.UploadErrorCode
import app.maw629.homerelay.data.UploadState
import java.time.Instant

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
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Home Relay")
                Text("Recent uploads")
            }
        }
        items(uploads, key = { it.id }) { upload ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(upload.name)
                Text("${upload.sizeBytes} bytes")
                Text(Instant.ofEpochMilli(upload.createdAtMillis).toString())
                Text(upload.state.name.replace('_', ' '))
                upload.errorMessage?.let { Text(it) }
                if (upload.state == UploadState.NEEDS_ATTENTION) {
                    Button(onClick = { onRetry(upload.id) }) { Text("Retry") }
                    if (upload.errorCode == UploadErrorCode.DESTINATION_ACCESS_LOST) {
                        Button(onClick = onChooseFolder) { Text("Choose folder again") }
                    }
                }
                if (upload.state == UploadState.QUEUED) {
                    Button(onClick = { onCancel(upload.id) }) { Text("Cancel") }
                }
            }
        }
    }
}
