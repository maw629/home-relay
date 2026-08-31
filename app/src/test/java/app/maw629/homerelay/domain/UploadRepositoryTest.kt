package app.maw629.homerelay.domain

import app.maw629.homerelay.data.UploadDao
import app.maw629.homerelay.data.UploadErrorCode
import app.maw629.homerelay.data.UploadItem
import app.maw629.homerelay.data.UploadState
import app.maw629.homerelay.share.IncomingShare
import app.maw629.homerelay.share.StageResult
import app.maw629.homerelay.work.UploadScheduler
import app.maw629.homerelay.notifications.UploadNotificationSink
import androidx.work.ForegroundInfo
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class UploadRepositoryTest {
    private val dao = FakeUploadDao()
    private val scheduler = FakeUploadScheduler()
    private val repository = UploadRepository(
        dao = dao,
        scheduler = scheduler,
        newId = { "new-item" },
        nowMillis = { 1_788_013_501_000 },
        randomSuffix = { "a1b2c3" }
    )

    @Test
    fun retryFromNeedsAttentionGeneratesANewOutputNameAndQueuesWork() = runTest {
        dao.insert(item("item-1", UploadState.NEEDS_ATTENTION, "old-name.pdf"))

        repository.retry("item-1")

        val item = dao.get("item-1")!!
        assertEquals(UploadState.QUEUED, item.state)
        assertEquals(UploadErrorCode.NONE, item.errorCode)
        assertEquals(2, item.retryCount)
        assertNotEquals("old-name.pdf", item.outputName)
        assertEquals(listOf("item-1"), scheduler.scheduledIds)
    }

    @Test
    fun enqueuePersistsQueuedItemAndSchedulesIt() = runTest {
        val stagedFile = File.createTempFile("upload", ".pdf")
        try {
            val id = repository.enqueue(
                StageResult.Staged(stagedFile, 42, "report.pdf"),
                IncomingShare(android.net.Uri.EMPTY, "report.pdf", "application/pdf")
            )

            val item = dao.get(id)!!
            assertEquals("new-item", item.id)
            assertEquals(UploadState.QUEUED, item.state)
            assertEquals("20260829-142501-a1b2c3-report.pdf", item.outputName)
            assertEquals(listOf("new-item"), scheduler.scheduledIds)
        } finally {
            stagedFile.delete()
        }
    }

    @Test
    fun enqueueUsesTheStagedProviderDisplayNameForPersistedNames() = runTest {
        val stagedFile = File.createTempFile("upload", ".pdf")
        try {
            val id = repository.enqueue(
                StageResult.Staged(stagedFile, 42, "provider-report.pdf"),
                IncomingShare(android.net.Uri.parse("content://sender/opaque-id"), "opaque-id", "application/pdf")
            )

            val item = dao.get(id)!!
            assertEquals("provider-report.pdf", item.originalName)
            assertEquals("20260829-142501-a1b2c3-provider-report.pdf", item.outputName)
        } finally {
            stagedFile.delete()
        }
    }

    @Test
    fun enqueuePostsQueuedStatusAfterPersistingAndScheduling() = runTest {
        val events = mutableListOf<String>()
        val dao = FakeUploadDao(events)
        val scheduler = FakeUploadScheduler(events)
        val notifier = RecordingNotifier(events)
        val repository = UploadRepository(
            dao, scheduler, { "queued-item" }, { 1_788_013_501_000 }, { "a1b2c3" }, notifier
        )
        val stagedFile = File.createTempFile("upload", ".pdf")
        try {
            repository.enqueue(
                StageResult.Staged(stagedFile, 42, "report.pdf"),
                IncomingShare(android.net.Uri.parse("content://sender/report"), "report.pdf", "application/pdf")
            )

            assertEquals("queued-item", notifier.queuedIds.single())
            assertEquals(UploadState.QUEUED, dao.get("queued-item")!!.state)
            assertEquals(
                listOf("persisted:queued-item", "scheduled:queued-item", "queued:queued-item"),
                events
            )
        } finally {
            stagedFile.delete()
        }
    }

    @Test
    fun resumePendingRequeuesInterruptedUploadsAndSchedulesQueuedItems() = runTest {
        dao.insert(item("queued", UploadState.QUEUED))
        dao.insert(item("interrupted", UploadState.UPLOADING))
        dao.insert(item("completed", UploadState.COMPLETED))

        repository.resumePending()

        assertEquals(UploadState.QUEUED, dao.get("interrupted")!!.state)
        assertEquals(listOf("queued", "interrupted"), scheduler.scheduledIds)
    }

    @Test
    fun cancelDeletesStagedFileCancelsWorkAndMarksItemCancelled() = runTest {
        val stagedFile = File.createTempFile("upload", ".pdf")
        dao.insert(item("item-1", UploadState.QUEUED, stagedPath = stagedFile.absolutePath))

        repository.cancel("item-1")

        assertFalse(stagedFile.exists())
        assertEquals(UploadState.CANCELLED, dao.get("item-1")!!.state)
        assertEquals(listOf("item-1"), scheduler.cancelledIds)
    }

    @Test
    fun retryRejectsItemsOutsideNeedsAttention() = runTest {
        dao.insert(item("item-1", UploadState.QUEUED))

        try {
            repository.retry("item-1")
            fail("Queued uploads must not be retried")
        } catch (_: IllegalStateException) {
        }

        assertEquals(UploadState.QUEUED, dao.get("item-1")!!.state)
        assertEquals(emptyList<String>(), scheduler.scheduledIds)
    }

    @Test
    fun cancelRejectsCompletedItems() = runTest {
        dao.insert(item("item-1", UploadState.COMPLETED))

        try {
            repository.cancel("item-1")
            fail("Completed uploads must not be cancelled")
        } catch (_: IllegalStateException) {
        }

        assertEquals(UploadState.COMPLETED, dao.get("item-1")!!.state)
        assertEquals(emptyList<String>(), scheduler.cancelledIds)
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun cancelCannotBeFollowedByRetryScheduling() = runTest {
        dao.insert(item("item-1", UploadState.NEEDS_ATTENTION))
        val scheduleEntered = CompletableDeferred<Unit>()
        val allowSchedule = CompletableDeferred<Unit>()
        scheduler.beforeSchedule = {
            scheduleEntered.complete(Unit)
            allowSchedule.await()
        }

        val retry = async { repository.retry("item-1") }
        scheduleEntered.await()
        val cancel = async { repository.cancel("item-1") }
        runCurrent()
        assertEquals(UploadState.QUEUED, dao.get("item-1")!!.state)
        assertFalse(cancel.isCompleted)
        allowSchedule.complete(Unit)
        retry.await()
        cancel.await()

        assertEquals(UploadState.CANCELLED, dao.get("item-1")!!.state)
        assertEquals(listOf("schedule:item-1", "cancel:item-1"), scheduler.operations)
    }

    private fun item(
        id: String,
        state: UploadState,
        outputName: String = "report.pdf",
        stagedPath: String = "/tmp/staged-file"
    ) = UploadItem(
        id = id,
        originalName = "report.pdf",
        mimeType = "application/pdf",
        outputName = outputName,
        stagedPath = stagedPath,
        byteSize = 42,
        createdAtMillis = 1,
        retryCount = 1,
        state = state,
        errorCode = UploadErrorCode.DESTINATION_ACCESS_LOST
    )
}

private class FakeUploadDao(private val events: MutableList<String>? = null) : UploadDao {
    private val items = linkedMapOf<String, UploadItem>()
    private val uploads = MutableStateFlow<List<UploadItem>>(emptyList())

    override suspend fun insert(item: UploadItem) {
        items[item.id] = item
        uploads.value = items.values.toList()
        events?.add("persisted:${item.id}")
    }

    override fun observeAll(): Flow<List<UploadItem>> = uploads

    override suspend fun get(id: String): UploadItem? = items[id]

    override suspend fun completeStaging(
        id: String,
        originalName: String,
        outputName: String,
        byteSize: Long
    ): Int {
        val item = items[id] ?: return 0
        if (item.state != UploadState.STAGING) return 0
        update(
            item.copy(
                originalName = originalName,
                outputName = outputName,
                byteSize = byteSize,
                state = UploadState.QUEUED,
                errorCode = UploadErrorCode.NONE
            )
        )
        return 1
    }

    override suspend fun failStaging(id: String, errorCode: UploadErrorCode): Int {
        val item = items[id] ?: return 0
        if (item.state != UploadState.STAGING) return 0
        update(item.copy(state = UploadState.NEEDS_ATTENTION, errorCode = errorCode))
        return 1
    }

    override suspend fun stagingItems(): List<UploadItem> = items.values
        .filter { it.state == UploadState.STAGING }
        .sortedBy { it.createdAtMillis }

    override suspend fun update(item: UploadItem) {
        items[item.id] = item
        uploads.value = items.values.toList()
    }

    override suspend fun delete(id: String) {
        items.remove(id)
        uploads.value = items.values.toList()
    }

    override suspend fun beginUpload(id: String): Int = TODO("Not implemented")

    override suspend fun requeueInterruptedUploads(): Int {
        val interrupted = items.values.filter { it.state == UploadState.UPLOADING }
        interrupted.forEach { update(it.copy(state = UploadState.QUEUED)) }
        return interrupted.size
    }

    override suspend fun requeueInterruptedUpload(id: String): Int {
        val item = items[id] ?: return 0
        if (item.state != UploadState.UPLOADING) return 0
        update(item.copy(state = UploadState.QUEUED))
        return 1
    }

    override suspend fun queuedIds(): List<String> = items.values
        .filter { it.state == UploadState.QUEUED }
        .map { it.id }

    override suspend fun finishUpload(
        id: String,
        state: UploadState,
        errorCode: UploadErrorCode,
        retryCount: Int
    ): Int = TODO("Not implemented")

    override suspend fun retry(id: String, outputName: String, retryCount: Int): Int {
        val item = items[id] ?: return 0
        if (item.state != UploadState.NEEDS_ATTENTION) return 0
        update(item.copy(outputName = outputName, retryCount = retryCount, state = UploadState.QUEUED, errorCode = UploadErrorCode.NONE))
        return 1
    }

    override suspend fun cancel(id: String): Int {
        val item = items[id] ?: return 0
        if (item.state !in setOf(UploadState.QUEUED, UploadState.UPLOADING, UploadState.NEEDS_ATTENTION)) return 0
        update(item.copy(state = UploadState.CANCELLED))
        return 1
    }
}

private class FakeUploadScheduler(private val events: MutableList<String>? = null) : UploadScheduler {
    val scheduledIds = mutableListOf<String>()
    val cancelledIds = mutableListOf<String>()
    val operations = mutableListOf<String>()
    var beforeSchedule: (suspend () -> Unit)? = null

    override suspend fun schedule(uploadItemId: String) {
        beforeSchedule?.invoke()
        scheduledIds += uploadItemId
        operations += "schedule:$uploadItemId"
        events?.add("scheduled:$uploadItemId")
    }

    override suspend fun cancel(uploadItemId: String) {
        cancelledIds += uploadItemId
        operations += "cancel:$uploadItemId"
    }
}

private class RecordingNotifier(private val events: MutableList<String>? = null) : UploadNotificationSink {
    val queuedIds = mutableListOf<String>()

    override fun foregroundInfo(item: UploadItem, copied: Long, total: Long): ForegroundInfo =
        ForegroundInfo(1, android.app.Notification())

    override fun queued(item: UploadItem) {
        queuedIds += item.id
        events?.add("queued:${item.id}")
    }

    override fun uploading(item: UploadItem) = Unit
    override fun completed(item: UploadItem) = Unit
    override fun needsAttention(item: UploadItem, error: UploadErrorCode) = Unit
}
