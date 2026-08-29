package app.maw629.homerelay.destination

import android.net.Uri
import app.maw629.homerelay.data.UploadErrorCode
import java.io.File

sealed interface DestinationResult {
    data object Success : DestinationResult
    data object TransientFailure : DestinationResult
    data object AccessLost : DestinationResult
    data class PermanentFailure(val errorCode: UploadErrorCode) : DestinationResult
    data object UnknownWriteOutcome : DestinationResult
}

interface DestinationGateway {
    suspend fun validate(treeUri: Uri): DestinationResult

    suspend fun write(
        treeUri: Uri,
        source: File,
        mimeType: String,
        outputName: String,
        onBytesCopied: suspend (Long) -> Unit = {}
    ): DestinationResult
}
