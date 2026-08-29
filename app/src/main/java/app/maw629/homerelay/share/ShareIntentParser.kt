package app.maw629.homerelay.share

import android.content.Intent
import android.net.Uri

object ShareIntentParser {
    @Suppress("DEPRECATION")
    fun parse(intent: Intent): List<IncomingShare> {
        if (intent.action != Intent.ACTION_SEND && intent.action != Intent.ACTION_SEND_MULTIPLE) {
            return emptyList()
        }

        val uris = linkedMapOf<String, Uri>()
        fun add(uri: Uri?) {
            if (uri != null) uris.putIfAbsent(uri.toString(), uri)
        }

        if (intent.action == Intent.ACTION_SEND) {
            add(intent.getParcelableExtra(Intent.EXTRA_STREAM))
        } else {
            intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)?.forEach(::add)
        }
        intent.clipData?.let { clipData ->
            for (index in 0 until clipData.itemCount) add(clipData.getItemAt(index).uri)
        }

        return uris.values.map { uri ->
            IncomingShare(
                uri = uri,
                displayName = uri.lastPathSegment?.takeIf(String::isNotBlank) ?: "shared-file",
                mimeType = intent.type ?: "application/octet-stream"
            )
        }
    }
}
