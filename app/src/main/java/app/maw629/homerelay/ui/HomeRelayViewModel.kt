package app.maw629.homerelay.ui

import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.maw629.homerelay.data.DestinationRepository
import app.maw629.homerelay.data.UploadErrorCode
import app.maw629.homerelay.data.UploadState
import app.maw629.homerelay.destination.DestinationGateway
import app.maw629.homerelay.destination.DestinationResult
import app.maw629.homerelay.domain.UploadRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsState(
    val destinationName: String?,
    val error: String? = null
)

data class UploadRow(
    val id: String,
    val name: String,
    val sizeBytes: Long,
    val createdAtMillis: Long,
    val state: UploadState,
    val errorCode: UploadErrorCode,
    val errorMessage: String?
)

interface PersistableUriPermissionTaker {
    fun takePersistableUriPermission(uri: Uri)
    fun releasePersistableUriPermission(uri: Uri)
}

class HomeRelayViewModel(
    private val destinationStore: DestinationRepository,
    private val gateway: DestinationGateway,
    private val uploadRepository: UploadRepository
) : ViewModel() {
    private val destinationError = MutableStateFlow<String?>(null)
    val settingsState: StateFlow<SettingsState> = destinationStore.destinationTreeUri
        .combine(destinationError) { uri, error ->
            SettingsState(destinationName = uri?.let { "Drive folder selected" }, error = error)
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, SettingsState(destinationName = null))

    val uploads: StateFlow<List<UploadRow>> = uploadRepository.observeUploads()
        .map { uploads ->
            uploads.map { upload ->
                UploadRow(
                    id = upload.id,
                    name = upload.originalName,
                    sizeBytes = upload.byteSize,
                    createdAtMillis = upload.createdAtMillis,
                    state = upload.state,
                    errorCode = upload.errorCode,
                    errorMessage = upload.errorCode.message
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun selectDestination(uri: Uri, permissionTaker: PersistableUriPermissionTaker) {
        viewModelScope.launch {
            val previousUri = destinationStore.destinationTreeUri.firstOrNull()
            var acquiredGrant = false
            val result = try {
                permissionTaker.takePersistableUriPermission(uri)
                acquiredGrant = true
                gateway.validate(uri)
            } catch (_: SecurityException) {
                DestinationResult.AccessLost
            }
            if (result == DestinationResult.Success) {
                destinationStore.setDestination(uri.toString())
                if (previousUri != null && previousUri != uri.toString()) {
                    runCatching {
                        permissionTaker.releasePersistableUriPermission(Uri.parse(previousUri))
                    }
                }
                destinationError.value = null
            } else {
                if (acquiredGrant && previousUri != uri.toString()) {
                    runCatching { permissionTaker.releasePersistableUriPermission(uri) }
                }
                destinationError.value =
                    "Home Relay cannot write to that folder. Choose another writable Drive folder."
            }
        }
    }

    fun retryUpload(id: String) {
        viewModelScope.launch { uploadRepository.retry(id) }
    }

    fun cancelUpload(id: String) {
        viewModelScope.launch { uploadRepository.cancel(id) }
    }
}

private val UploadErrorCode.message: String?
    get() = when (this) {
        UploadErrorCode.NONE -> null
        UploadErrorCode.SOURCE_UNREADABLE -> "The staged file is no longer available."
        UploadErrorCode.STAGING_STORAGE_FULL -> "Home Relay needs more storage space."
        UploadErrorCode.SHARE_INTERRUPTED -> "The shared file could not be prepared. Share it again."
        UploadErrorCode.DESTINATION_ACCESS_LOST -> "Choose the destination folder again."
        UploadErrorCode.DESTINATION_QUOTA -> "The destination folder has no available storage."
        UploadErrorCode.DESTINATION_POLICY -> "The destination provider rejected this upload."
        UploadErrorCode.WRITE_OUTCOME_UNKNOWN -> "The previous upload result is unknown. Retry creates a new file."
    }
