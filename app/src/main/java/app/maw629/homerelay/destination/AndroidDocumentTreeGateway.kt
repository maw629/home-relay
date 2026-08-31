package app.maw629.homerelay.destination

import android.content.ContentResolver
import android.content.Context
import android.provider.DocumentsContract
import app.maw629.homerelay.data.UploadErrorCode
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CancellationException

class AndroidDocumentTreeGateway(context: Context) : DestinationGateway {
    private val contentResolver: ContentResolver = context.applicationContext.contentResolver

    override suspend fun validate(treeUri: android.net.Uri): DestinationResult {
        var documentCreated = false
        return try {
            val probeUri = DocumentsContract.createDocument(
                contentResolver,
                treeDocumentUri(treeUri),
                "application/octet-stream",
                ".home-relay-probe-${UUID.randomUUID()}"
            ) ?: return DestinationResult.TransientFailure
            documentCreated = true
            contentResolver.openOutputStream(probeUri)?.use { } ?: return DestinationResult.UnknownWriteOutcome
            if (DocumentsContract.deleteDocument(contentResolver, probeUri)) {
                DestinationResult.Success
            } else {
                DestinationResult.UnknownWriteOutcome
            }
        } catch (exception: SecurityException) {
            DestinationResult.AccessLost
        } catch (exception: FileNotFoundException) {
            DestinationResult.AccessLost
        } catch (exception: IllegalArgumentException) {
            DestinationResult.AccessLost
        } catch (exception: IOException) {
            writeFailure(exception, documentCreated)
        } catch (exception: Exception) {
            if (exception is CancellationException) throw exception
            writeFailure(exception, documentCreated)
        }
    }

    override suspend fun write(
        treeUri: android.net.Uri,
        source: File,
        mimeType: String,
        outputName: String,
        onBytesCopied: suspend (Long) -> Unit
    ): DestinationResult {
        var documentCreated = false
        return try {
            val documentUri = DocumentsContract.createDocument(
                contentResolver,
                treeDocumentUri(treeUri),
                mimeType,
                outputName
            ) ?: return DestinationResult.TransientFailure
            documentCreated = true
            source.inputStream().buffered().use { input ->
                contentResolver.openOutputStream(documentUri)?.buffered()?.use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var copied = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count == -1) break
                        output.write(buffer, 0, count)
                        copied += count
                        onBytesCopied(copied)
                    }
                } ?: return DestinationResult.UnknownWriteOutcome
            }
            DestinationResult.Success
        } catch (exception: SecurityException) {
            DestinationResult.AccessLost
        } catch (exception: FileNotFoundException) {
            DestinationResult.AccessLost
        } catch (exception: IOException) {
            writeFailure(exception, documentCreated)
        } catch (exception: Exception) {
            if (exception is CancellationException) throw exception
            writeFailure(exception, documentCreated)
        }
    }

    private fun treeDocumentUri(treeUri: android.net.Uri): android.net.Uri =
        DocumentsContract.buildDocumentUriUsingTree(
            treeUri,
            DocumentsContract.getTreeDocumentId(treeUri)
        )

    private fun writeFailure(exception: Exception, documentCreated: Boolean): DestinationResult {
        val message = generateSequence<Throwable>(exception) { it.cause }
            .mapNotNull(Throwable::message)
            .joinToString(" ")
            .lowercase()
        return when {
            message.contains("quota") || message.contains("no space") || message.contains("storage full") ->
                DestinationResult.PermanentFailure(UploadErrorCode.DESTINATION_QUOTA)
            message.contains("policy") || message.contains("not allowed") || message.contains("blocked") ->
                DestinationResult.PermanentFailure(UploadErrorCode.DESTINATION_POLICY)
            documentCreated -> DestinationResult.UnknownWriteOutcome
            else -> DestinationResult.TransientFailure
        }
    }
}
