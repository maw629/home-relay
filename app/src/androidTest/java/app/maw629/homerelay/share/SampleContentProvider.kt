package app.maw629.homerelay.share

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.FileNotFoundException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class SampleContentProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?
    ): Cursor = MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME)).apply {
        addRow(arrayOf(if (uri.lastPathSegment == "missing-display-name") null else REPORT_NAME))
    }

    override fun getType(uri: Uri): String = "application/pdf"

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r") throw FileNotFoundException("Read-only test provider")
        if (uri.lastPathSegment !in readablePaths) throw FileNotFoundException(uri.toString())

        val pipe = if (uri.lastPathSegment == "failed-stream.pdf") {
            ParcelFileDescriptor.createReliablePipe()
        } else {
            ParcelFileDescriptor.createPipe()
        }
        val writeSide = pipe[1]
        if (uri.lastPathSegment == "failed-stream.pdf") {
            writeSide.closeWithError("Test source stream failed")
            return pipe[0]
        }
        val blockingControl = blockingControl
        thread(name = "SampleContentProvider", isDaemon = true) {
            ParcelFileDescriptor.AutoCloseOutputStream(writeSide).use { output ->
                if (uri.lastPathSegment == "blocking-report.pdf") {
                    blockingControl.started.countDown()
                    blockingControl.release.await()
                }
                output.write(REPORT_BYTES)
            }
        }
        return pipe[0]
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<String>?
    ): Int = 0

    companion object {
        const val REPORT_NAME = "report.pdf"
        val REPORT_BYTES = "hello".toByteArray()
        val readablePaths = setOf(
            "report.pdf",
            "opaque-id",
            "missing-display-name",
            "failed-stream.pdf",
            "blocking-report.pdf"
        )

        @Volatile
        private var blockingControl = BlockingControl()

        @JvmStatic
        fun resetBlockingSource() {
            blockingControl = BlockingControl()
        }

        @JvmStatic
        fun awaitBlockingSourceStarted(timeout: Long, unit: TimeUnit): Boolean =
            blockingControl.started.await(timeout, unit)

        @JvmStatic
        fun releaseBlockingSource() {
            blockingControl.release.countDown()
        }

        private data class BlockingControl(
            val started: CountDownLatch = CountDownLatch(1),
            val release: CountDownLatch = CountDownLatch(1)
        )
    }
}
