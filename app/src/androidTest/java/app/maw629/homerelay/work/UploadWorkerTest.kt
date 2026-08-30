package app.maw629.homerelay.work

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import androidx.work.ForegroundInfo
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import androidx.work.testing.TestListenableWorkerBuilder
import app.maw629.homerelay.data.DestinationStore
import app.maw629.homerelay.data.UploadDao
import app.maw629.homerelay.data.UploadErrorCode
import app.maw629.homerelay.data.UploadItem
import app.maw629.homerelay.data.UploadState
import app.maw629.homerelay.destination.DestinationGateway
import app.maw629.homerelay.destination.DestinationResult
import app.maw629.homerelay.notifications.UploadNotificationSink
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UploadWorkerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dao = FakeUploadDao()
    private val gateway = FakeDestinationGateway()
    private val notifier = FakeUploadNotifier()
    private val store = DestinationStore(
        PreferenceDataStoreFactory.create(
            produceFile = { File(context.cacheDir, "worker-test-${UUID.randomUUID()}.preferences_pb") }
        )
    )

    @Test
    fun successCompletesDeletesStagedFileAndPostsCompletion() = runTest {
        val upload = item("item-1")
        dao.insert(upload)
        destinationIsConfigured()

        val result = runWorker(upload.id)

        assertTrue(result is ListenableWorker.Result.Success)
        assertEquals(UploadState.COMPLETED, dao.get(upload.id)!!.state)
        assertFalse(File(upload.stagedPath).exists())
        assertEquals(listOf(upload.id), notifier.completedIds)
    }

    @Test
    fun transientDestinationFailureReturnsRetryAndKeepsQueuedItem() = runTest {
        val upload = item("item-1")
        dao.insert(upload)
        destinationIsConfigured()
        gateway.nextWriteResult = DestinationResult.TransientFailure

        val result = runWorker(upload.id)

        assertTrue(result is ListenableWorker.Result.Retry)
        assertEquals(UploadState.QUEUED, dao.get(upload.id)!!.state)
        assertTrue(File(upload.stagedPath).exists())
    }

    @Test
    fun accessLostNeedsAttentionAndRetainsStagedFile() = runTest {
        val upload = item("item-1")
        dao.insert(upload)
        destinationIsConfigured()
        gateway.nextWriteResult = DestinationResult.AccessLost

        val result = runWorker(upload.id)

        assertTrue(result is ListenableWorker.Result.Success)
        assertNeedsAttention(upload, UploadErrorCode.DESTINATION_ACCESS_LOST)
    }

    @Test
    fun permanentFailureUsesGatewayErrorCode() = runTest {
        val upload = item("item-1")
        dao.insert(upload)
        destinationIsConfigured()
        gateway.nextWriteResult = DestinationResult.PermanentFailure(UploadErrorCode.DESTINATION_QUOTA)

        runWorker(upload.id)

        assertNeedsAttention(upload, UploadErrorCode.DESTINATION_QUOTA)
    }

    @Test
    fun unknownWriteOutcomeNeedsAttention() = runTest {
        val upload = item("item-1")
        dao.insert(upload)
        destinationIsConfigured()
        gateway.nextWriteResult = DestinationResult.UnknownWriteOutcome

        runWorker(upload.id)

        assertNeedsAttention(upload, UploadErrorCode.WRITE_OUTCOME_UNKNOWN)
    }

    @Test
    fun missingDestinationNeedsAttention() = runTest {
        val upload = item("item-1")
        dao.insert(upload)

        runWorker(upload.id)

        assertNeedsAttention(upload, UploadErrorCode.DESTINATION_ACCESS_LOST)
    }

    @Test
    fun missingStagedFileNeedsAttention() = runTest {
        val upload = item("item-1")
        File(upload.stagedPath).delete()
        dao.insert(upload)
        destinationIsConfigured()

        val result = runWorker(upload.id)

        assertTrue(result is ListenableWorker.Result.Success)
        assertNeedsAttention(upload, UploadErrorCode.SOURCE_UNREADABLE, stagedFileExists = false)
    }

    @Test
    fun cancellationDuringWriteWinsOverWorkerCompletion() = runTest {
        val upload = item("item-1")
        dao.insert(upload)
        destinationIsConfigured()
        gateway.beforeReturning = { dao.cancel(upload.id) }

        val result = runWorker(upload.id)

        assertTrue(result is ListenableWorker.Result.Success)
        assertEquals(UploadState.CANCELLED, dao.get(upload.id)!!.state)
        assertEquals(emptyList<String>(), notifier.completedIds)
    }

    @Test
    fun largeFilesReportInitialAndPeriodicForegroundProgress() = runTest {
        val upload = item("item-1", byteSize = 10L * 1024 * 1024)
        dao.insert(upload)
        destinationIsConfigured()
        gateway.copiedBytes = listOf(1024L * 1024, 10L * 1024 * 1024)

        runWorker(upload.id)

        assertEquals(listOf(0L, 1024L * 1024, 10L * 1024 * 1024), notifier.foregroundBytes)
    }

    @Test
    fun workerPostsUploadingBeforeWriting() = runTest {
        val upload = item("item-1")
        dao.insert(upload)
        destinationIsConfigured()

        runWorker(upload.id)

        assertEquals(listOf(upload.id), notifier.uploadingIds)
    }

    @Test
    fun reexecutedWorkerReclaimsInterruptedUploadAndCompletesOnce() = runTest {
        val upload = item("item-1").copy(state = UploadState.UPLOADING)
        dao.insert(upload)
        destinationIsConfigured()

        runWorker(upload.id)

        assertEquals(UploadState.COMPLETED, dao.get(upload.id)!!.state)
        assertEquals(listOf(upload.id), notifier.completedIds)
    }

    private suspend fun destinationIsConfigured() {
        store.setDestination("content://example/tree/destination")
    }

    private suspend fun assertNeedsAttention(
        upload: UploadItem,
        error: UploadErrorCode,
        stagedFileExists: Boolean = true
    ) {
        assertEquals(UploadState.NEEDS_ATTENTION, dao.get(upload.id)!!.state)
        assertEquals(stagedFileExists, File(upload.stagedPath).exists())
        assertEquals(listOf(upload.id to error), notifier.attentionItems)
    }

    private suspend fun runWorker(itemId: String): ListenableWorker.Result {
        val worker = TestListenableWorkerBuilder<UploadWorker>(context)
            .setInputData(workDataOf(UploadWorker.UPLOAD_ITEM_ID to itemId))
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters
                ): ListenableWorker = UploadWorker(
                    appContext,
                    workerParameters,
                    dao,
                    store,
                    gateway,
                    notifier
                )
            })
            .build()
        return worker.doWork()
    }

    private fun item(id: String, byteSize: Long = 8): UploadItem {
        val file = File.createTempFile("upload", ".pdf", context.cacheDir).apply { writeText("contents") }
        return UploadItem(
            id = id,
            originalName = "report.pdf",
            mimeType = "application/pdf",
            outputName = "report.pdf",
            stagedPath = file.absolutePath,
            byteSize = byteSize,
            createdAtMillis = 1,
            retryCount = 0,
            state = UploadState.QUEUED,
            errorCode = UploadErrorCode.NONE
        )
    }
}

private class FakeUploadDao : UploadDao {
    private val items = mutableMapOf<String, UploadItem>()
    private val uploads = MutableStateFlow<List<UploadItem>>(emptyList())

    override suspend fun insert(item: UploadItem) = save(item)
    override fun observeAll(): Flow<List<UploadItem>> = uploads
    override suspend fun get(id: String): UploadItem? = items[id]
    override suspend fun update(item: UploadItem) = save(item)
    override suspend fun delete(id: String) {
        items.remove(id)
        uploads.value = items.values.toList()
    }

    override suspend fun beginUpload(id: String): Int = transition(id, setOf(UploadState.QUEUED), UploadState.UPLOADING)

    override suspend fun requeueInterruptedUploads(): Int = 0

    override suspend fun requeueInterruptedUpload(id: String): Int = transition(
        id,
        setOf(UploadState.UPLOADING),
        UploadState.QUEUED
    )

    override suspend fun queuedIds(): List<String> = items.values
        .filter { it.state == UploadState.QUEUED }
        .map { it.id }

    override suspend fun finishUpload(
        id: String,
        state: UploadState,
        errorCode: UploadErrorCode,
        retryCount: Int
    ): Int {
        val item = items[id] ?: return 0
        if (item.state != UploadState.UPLOADING) return 0
        save(item.copy(state = state, errorCode = errorCode, retryCount = retryCount))
        return 1
    }

    override suspend fun retry(id: String, outputName: String, retryCount: Int): Int {
        val item = items[id] ?: return 0
        if (item.state != UploadState.NEEDS_ATTENTION) return 0
        save(item.copy(outputName = outputName, retryCount = retryCount, state = UploadState.QUEUED, errorCode = UploadErrorCode.NONE))
        return 1
    }

    override suspend fun cancel(id: String): Int = transition(
        id,
        setOf(UploadState.QUEUED, UploadState.UPLOADING, UploadState.NEEDS_ATTENTION),
        UploadState.CANCELLED
    )

    private fun transition(id: String, from: Set<UploadState>, to: UploadState): Int {
        val item = items[id] ?: return 0
        if (item.state !in from) return 0
        save(item.copy(state = to))
        return 1
    }

    private fun save(item: UploadItem) {
        items[item.id] = item
        uploads.value = items.values.toList()
    }
}

private class FakeDestinationGateway : DestinationGateway {
    var nextWriteResult: DestinationResult = DestinationResult.Success
    var copiedBytes: List<Long> = emptyList()
    var beforeReturning: (suspend () -> Unit)? = null

    override suspend fun validate(treeUri: Uri): DestinationResult = DestinationResult.Success

    override suspend fun write(
        treeUri: Uri,
        source: File,
        mimeType: String,
        outputName: String,
        onBytesCopied: suspend (Long) -> Unit
    ): DestinationResult {
        copiedBytes.forEach { onBytesCopied(it) }
        beforeReturning?.invoke()
        return nextWriteResult
    }
}

private class FakeUploadNotifier : UploadNotificationSink {
    val foregroundBytes = mutableListOf<Long>()
    val uploadingIds = mutableListOf<String>()
    val completedIds = mutableListOf<String>()
    val attentionItems = mutableListOf<Pair<String, UploadErrorCode>>()

    override fun foregroundInfo(item: UploadItem, copied: Long, total: Long): ForegroundInfo {
        foregroundBytes += copied
        return ForegroundInfo(1, android.app.Notification())
    }

    override fun queued(item: UploadItem) = Unit

    override fun uploading(item: UploadItem) {
        uploadingIds += item.id
    }

    override fun completed(item: UploadItem) {
        completedIds += item.id
    }

    override fun needsAttention(item: UploadItem, error: UploadErrorCode) {
        attentionItems += item.id to error
    }
}
