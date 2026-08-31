package app.maw629.homerelay.share

import android.net.Uri

data class IncomingShare(
    val uri: Uri,
    val displayName: String,
    val mimeType: String
)
