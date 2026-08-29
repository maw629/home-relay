package app.maw629.homerelay.share

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.system.ErrnoException
import android.system.OsConstants
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface StageResult {
    data class Staged(val file: File, val byteSize: Long) : StageResult
    data object SourceUnreadable : StageResult
    data object StorageFull : StageResult
}

interface ShareStager {
    suspend fun stage(id: String, share: IncomingShare): StageResult
}

class AndroidShareStager(private val context: Context) : ShareStager {
    override suspend fun stage(id: String, share: IncomingShare): StageResult = withContext(Dispatchers.IO) {
        val pendingDirectory = File(context.noBackupFilesDir, "pending")
        if (!pendingDirectory.exists() && !pendingDirectory.mkdirs()) {
            return@withContext StageResult.StorageFull
        }

        val displayName = resolveDisplayName(share.uri) ?: "shared-file"
        val target = File(pendingDirectory, "${safeName(id, "item")}-${safeName(displayName, "shared-file")}")
        var temporary: File? = null

        try {
            temporary = File.createTempFile("${safeName(id, "item")}-", ".partial", pendingDirectory)
            val byteSize = context.contentResolver.openInputStream(share.uri)?.use { input ->
                temporary.outputStream().buffered().use { output -> input.copyTo(output) }
            } ?: return@withContext StageResult.SourceUnreadable

            if (target.exists() && !target.delete()) return@withContext StageResult.StorageFull
            if (!temporary.renameTo(target)) return@withContext StageResult.StorageFull
            temporary = null
            StageResult.Staged(target, byteSize)
        } catch (error: IOException) {
            if (isStorageFull(error)) StageResult.StorageFull else StageResult.SourceUnreadable
        } catch (_: SecurityException) {
            StageResult.SourceUnreadable
        } finally {
            temporary?.delete()
        }
    }

    private fun resolveDisplayName(uri: Uri): String? = try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) cursor.getString(index)?.takeIf(String::isNotBlank) else null
            }
    } catch (_: SecurityException) {
        null
    }

    private fun safeName(value: String, fallback: String): String {
        val normalized = value.replace(Regex("[^A-Za-z0-9._-]"), "_").trim('.', '_').take(120)
        return normalized.ifBlank { fallback }
    }

    private fun isStorageFull(error: IOException): Boolean {
        var cause: Throwable? = error
        while (cause != null) {
            if (cause is ErrnoException && cause.errno == OsConstants.ENOSPC) return true
            val message = cause.message.orEmpty()
            if (message.contains("ENOSPC", ignoreCase = true) ||
                message.contains("no space left", ignoreCase = true) ||
                message.contains("not enough space", ignoreCase = true)
            ) {
                return true
            }
            cause = cause.cause
        }
        return false
    }
}
