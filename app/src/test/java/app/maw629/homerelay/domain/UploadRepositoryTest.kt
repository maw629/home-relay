package app.maw629.homerelay.domain

import app.maw629.homerelay.data.UploadDao
import app.maw629.homerelay.data.UploadErrorCode
import app.maw629.homerelay.data.UploadItem
import app.maw629.homerelay.data.UploadState
import app.maw629.homerelay.share.IncomingShare
import app.maw629.homerelay.share.StageResult
import app.maw629.homerelay.work.UploadScheduler
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
                StageResult.Staged(stagedFile, 42),
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

private class FakeUploadDao : UploadDao {
    private val items = linkedMapOf<String, UploadItem>()
    private val uploads = MutableStateFlow<List<UploadItem>>(emptyList())

    override suspend fun insert(item: UploadItem) {
        items[item.id] = item
        uploads.value = items.values.toList()
    }

    override fun observeAll(): Flow<List<UploadItem>> = uploads

    override suspend fun get(id: String): UploadItem? = items[id]

    override suspend fun update(item: UploadItem) {
        items[item.id] = item
        uploads.value = items.values.toList()
    }

    override suspend fun delete(id: String) {
        items.remove(id)
        uploads.value = items.values.toList()
    }

    override suspend fun beginUpload(id: String): Int = TODO("Not implemented")

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

private class FakeUploadScheduler : UploadScheduler {
    val scheduledIds = mutableListOf<String>()
    val cancelledIds = mutableListOf<String>()
    val operations = mutableListOf<String>()
    var beforeSchedule: (suspend () -> Unit)? = null

    override suspend fun schedule(uploadItemId: String) {
        beforeSchedule?.invoke()
        scheduledIds += uploadItemId
        operations += "schedule:$uploadItemId"
    }

    override suspend fun cancel(uploadItemId: String) {
        cancelledIds += uploadItemId
        operations += "cancel:$uploadItemId"
    }
}
