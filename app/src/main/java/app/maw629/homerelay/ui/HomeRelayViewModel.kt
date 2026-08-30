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
    val state: UploadState,
    val errorCode: UploadErrorCode
)

fun interface PersistableUriPermissionTaker {
    fun takePersistableUriPermission(uri: Uri)
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
                    state = upload.state,
                    errorCode = upload.errorCode
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun selectDestination(uri: Uri, permissionTaker: PersistableUriPermissionTaker) {
        viewModelScope.launch {
            val result = try {
                permissionTaker.takePersistableUriPermission(uri)
                gateway.validate(uri)
            } catch (_: SecurityException) {
                DestinationResult.AccessLost
            }
            if (result == DestinationResult.Success) {
                destinationStore.setDestination(uri.toString())
                destinationError.value = null
            } else {
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
