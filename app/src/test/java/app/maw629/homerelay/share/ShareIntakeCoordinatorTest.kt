package app.maw629.homerelay.share

import android.net.Uri
import app.maw629.homerelay.data.DestinationRepository
import app.maw629.homerelay.data.UploadDao
import app.maw629.homerelay.data.UploadErrorCode
import app.maw629.homerelay.data.UploadItem
import app.maw629.homerelay.data.UploadState
import app.maw629.homerelay.domain.UploadRepository
import app.maw629.homerelay.work.UploadScheduler
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class ShareIntakeCoordinatorTest {
    @Test
    fun coordinatorCreatesStagingRowBeforeOpeningSource() = runTest {
        val fixture = Fixture(StandardTestDispatcher(testScheduler))
        try {
            val stageStarted = CompletableDeferred<Unit>()
            fixture.stager.onStage = { target, _ ->
                assertEquals(UploadState.STAGING, fixture.dao.items.values.single().state)
                assertEquals(target.absolutePath, fixture.dao.items.values.single().stagedPath)
                stageStarted.complete(Unit)
                StageResult.Staged(target.apply { writeText("bytes") }, 5, "report.pdf")
            }

            fixture.coordinator.recoverInterruptedStaging()
            val operation = fixture.coordinator.start("intake-1", listOf(reportShare))
            advanceUntilIdle()

            assertTrue(stageStarted.isCompleted)
            assertEquals(UploadState.QUEUED, fixture.dao.items.values.single().state)
            assertEquals(1, operation.terminal().queuedCount)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun repeatedStartReturnsOneOperationAndStagesEachShareOnce() = runTest {
        val fixture = Fixture(StandardTestDispatcher(testScheduler))
        try {
            fixture.coordinator.recoverInterruptedStaging()
            val shares = listOf(reportShare, photoShare)

            val first = fixture.coordinator.start("intake-1", shares)
            val second = fixture.coordinator.start("intake-1", shares)
            advanceUntilIdle()

            assertSame(first, second)
            assertEquals(2, fixture.stager.stageCalls)
            assertEquals(2, first.terminal().queuedCount)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun sourceAndStorageFailuresBecomeAttentionRowsWithoutPrivateFiles() = runTest {
        val fixture = Fixture(StandardTestDispatcher(testScheduler))
        try {
            fixture.stager.onStage = { target, share ->
                target.writeText("partial")
                if (share == reportShare) StageResult.SourceUnreadable else StageResult.StorageFull
            }
            fixture.coordinator.recoverInterruptedStaging()

            val operation = fixture.coordinator.start("intake-1", listOf(reportShare, photoShare))
            advanceUntilIdle()

            val terminal = operation.terminal()
            assertEquals(2, terminal.attentionCount)
            assertEquals(
                setOf(UploadErrorCode.SOURCE_UNREADABLE, UploadErrorCode.STAGING_STORAGE_FULL),
                terminal.attentionErrors
            )
            assertTrue(fixture.dao.items.values.all { it.state == UploadState.NEEDS_ATTENTION })
            assertTrue(
                fixture.dao.items.values.map(UploadItem::stagedPath).map(::File).none(File::exists)
            )
        } finally {
            fixture.close()
        }
    }

    @Test
    fun missingDestinationIsCheckedAfterStagingAndKeepsThePrivateFile() = runTest {
        val fixture = Fixture(StandardTestDispatcher(testScheduler))
        try {
            fixture.destination.value = null
            var staged = false
            fixture.stager.onStage = { target, _ ->
                target.writeText("bytes")
                staged = true
                StageResult.Staged(target, 5, "report.pdf")
            }
            fixture.destination.onRead = {
                assertTrue(staged)
                assertTrue(fixture.dao.items.values.single().stagedPath.let(::File).isFile)
            }
            fixture.coordinator.recoverInterruptedStaging()

            val operation = fixture.coordinator.start("intake-1", listOf(reportShare))
            advanceUntilIdle()

            val item = fixture.dao.items.values.single()
            val terminal = operation.terminal()
            assertEquals(1, fixture.destination.reads)
            assertEquals(UploadState.NEEDS_ATTENTION, item.state)
            assertEquals(UploadErrorCode.DESTINATION_ACCESS_LOST, item.errorCode)
            assertTrue(File(item.stagedPath).isFile)
            assertEquals(setOf(UploadErrorCode.DESTINATION_ACCESS_LOST), terminal.attentionErrors)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun stagingInsertFailureDoesNotOpenTheSourceAndReportsQueueUnavailable() = runTest {
        val fixture = Fixture(StandardTestDispatcher(testScheduler))
        try {
            fixture.dao.insertFailure = IllegalStateException("Room is unavailable")
            fixture.coordinator.recoverInterruptedStaging()

            val operation = fixture.coordinator.start("intake-1", listOf(reportShare))
            advanceUntilIdle()

            assertEquals(0, fixture.stager.stageCalls)
            assertEquals(0, fixture.dao.items.size)
            assertEquals(1, operation.terminal().queueUnavailableCount)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun guardedQueueTransitionFailureDeletesFileAndLeavesStagingRow() = runTest {
        val fixture = Fixture(StandardTestDispatcher(testScheduler))
        try {
            fixture.dao.completeStagingResult = 0
            fixture.stager.onStage = { target, _ ->
                target.writeText("bytes")
                StageResult.Staged(target, 5, "report.pdf")
            }
            fixture.coordinator.recoverInterruptedStaging()

            val operation = fixture.coordinator.start("intake-1", listOf(reportShare))
            advanceUntilIdle()

            val item = fixture.dao.items.values.single()
            assertEquals(UploadState.STAGING, item.state)
            assertFalse(File(item.stagedPath).exists())
            assertEquals(1, operation.terminal().queueUnavailableCount)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun destinationReadFailureKeepsCompletedFileAndRecordsAttention() = runTest {
        val fixture = Fixture(StandardTestDispatcher(testScheduler))
        try {
            fixture.destination.readFailure = IllegalStateException("DataStore is unavailable")
            fixture.stager.onStage = { target, _ ->
                StageResult.Staged(target.apply { writeText("bytes") }, 5, "report.pdf")
            }
            fixture.coordinator.recoverInterruptedStaging()

            val operation = fixture.coordinator.start("intake-1", listOf(reportShare))
            advanceUntilIdle()

            val item = fixture.dao.items.values.single()
            assertEquals(UploadState.NEEDS_ATTENTION, item.state)
            assertEquals(UploadErrorCode.DESTINATION_ACCESS_LOST, item.errorCode)
            assertTrue(File(item.stagedPath).isFile)
            assertEquals(1, operation.terminal().attentionCount)
            assertEquals(
                setOf(UploadErrorCode.DESTINATION_ACCESS_LOST),
                operation.terminal().attentionErrors
            )
        } finally {
            fixture.close()
        }
    }

    @Test
    fun thrownFinalQueueTransitionDeletesFileAndReportsQueueUnavailable() = runTest {
        val fixture = Fixture(StandardTestDispatcher(testScheduler))
        try {
            fixture.dao.completeStagingFailure = IllegalStateException("Room is unavailable")
            fixture.stager.onStage = { target, _ ->
                StageResult.Staged(target.apply { writeText("bytes") }, 5, "report.pdf")
            }
            fixture.coordinator.recoverInterruptedStaging()

            val operation = fixture.coordinator.start("intake-1", listOf(reportShare))
            advanceUntilIdle()

            val item = fixture.dao.items.values.single()
            assertEquals(UploadState.STAGING, item.state)
            assertFalse(File(item.stagedPath).exists())
            assertEquals(1, operation.terminal().queueUnavailableCount)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun recoveryMarksExistingStagingInterruptedBeforeOpeningNewSource() = runTest {
        val fixture = Fixture(StandardTestDispatcher(testScheduler))
        try {
            val interruptedFile = File(fixture.pendingDirectory, "interrupted.staged").apply {
                writeText("incomplete")
            }
            fixture.dao.insert(fixture.item("interrupted", UploadState.STAGING, interruptedFile.absolutePath))
            fixture.stager.onStage = { target, _ ->
                assertEquals(
                    UploadErrorCode.SHARE_INTERRUPTED,
                    fixture.dao.items.getValue("interrupted").errorCode
                )
                assertFalse(interruptedFile.exists())
                StageResult.Staged(target.apply { writeText("bytes") }, 5, "report.pdf")
            }

            val operation = fixture.coordinator.start("intake-1", listOf(reportShare))
            runCurrent()
            assertEquals(0, fixture.stager.stageCalls)
            fixture.coordinator.recoverInterruptedStaging()
            advanceUntilIdle()

            assertEquals(UploadState.NEEDS_ATTENTION, fixture.dao.items.getValue("interrupted").state)
            assertEquals(1, operation.terminal().queuedCount)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun recoveryFailureReportsQueueUnavailableWithoutOpeningTheSource() = runTest {
        val fixture = Fixture(StandardTestDispatcher(testScheduler))
        try {
            fixture.dao.stagingItemsFailure = IllegalStateException("Room is unavailable")

            fixture.coordinator.recoverInterruptedStaging()
            val operation = fixture.coordinator.start("intake-1", listOf(reportShare))
            advanceUntilIdle()

            assertEquals(0, fixture.stager.stageCalls)
            assertEquals(1, operation.terminal().queueUnavailableCount)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun schedulerFailureAfterQueueTransitionStillReportsQueued() = runTest {
        val fixture = Fixture(StandardTestDispatcher(testScheduler))
        try {
            fixture.scheduler.scheduleFailure = IllegalStateException("WorkManager is unavailable")
            fixture.coordinator.recoverInterruptedStaging()

            val operation = fixture.coordinator.start("intake-1", listOf(reportShare))
            advanceUntilIdle()

            assertEquals(UploadState.QUEUED, fixture.dao.items.values.single().state)
            assertEquals(1, operation.terminal().queuedCount)
            assertEquals(0, operation.terminal().queueUnavailableCount)
        } finally {
            fixture.close()
        }
    }

    @Test
    fun releaseDuringPreparationRemovesOperationAfterItBecomesTerminal() = runTest {
        val fixture = Fixture(StandardTestDispatcher(testScheduler))
        try {
            val stageEntered = CompletableDeferred<Unit>()
            val completeStaging = CompletableDeferred<Unit>()
            fixture.stager.onStage = { target, _ ->
                stageEntered.complete(Unit)
                completeStaging.await()
                StageResult.Staged(target.apply { writeText("bytes") }, 5, "report.pdf")
            }
            fixture.coordinator.recoverInterruptedStaging()

            val operation = fixture.coordinator.start("intake-1", listOf(reportShare))
            runCurrent()
            stageEntered.await()
            fixture.coordinator.release("intake-1")

            assertSame(operation, fixture.coordinator.observe("intake-1"))
            completeStaging.complete(Unit)
            advanceUntilIdle()

            assertTrue(operation.value is ShareIntakeStatus.Terminal)
            assertEquals(null, fixture.coordinator.observe("intake-1"))
        } finally {
            fixture.close()
        }
    }

    private class Fixture(dispatcher: TestDispatcher) {
        val pendingDirectory = File.createTempFile("share-intake", "pending").apply {
            delete()
            check(mkdir())
        }
        val dao = FakeUploadDao()
        val scheduler = FakeUploadScheduler()
        val stager = FakeShareStager(pendingDirectory)
        val destination = FakeDestinationRepository()
        private var nextId = 0
        private val applicationScope = CoroutineScope(SupervisorJob() + dispatcher)
        private val repository = UploadRepository(
            dao = dao,
            scheduler = scheduler,
            newId = { "item-${nextId++}" },
            nowMillis = { 1_788_013_501_000L },
            randomSuffix = { "a1b2c3" }
        )
        val coordinator = ShareIntakeCoordinator(
            applicationScope = applicationScope,
            stager = stager,
            destinationRepository = destination,
            uploadRepository = repository,
            nowMillis = { 1_788_013_501_999L }
        )

        fun item(id: String, state: UploadState, stagedPath: String) = UploadItem(
            id = id,
            originalName = "report.pdf",
            mimeType = "application/pdf",
            outputName = "report.pdf",
            stagedPath = stagedPath,
            byteSize = 0,
            createdAtMillis = 1,
            retryCount = 0,
            state = state,
            errorCode = UploadErrorCode.NONE
        )

        fun close() {
            applicationScope.cancel()
            pendingDirectory.deleteRecursively()
        }
    }

    private class FakeShareStager(private val pendingDirectory: File) : ShareStager {
        var stageCalls = 0
        var onStage: suspend (File, IncomingShare) -> StageResult = { target, share ->
            StageResult.Staged(target.apply { writeText("bytes") }, 5, share.displayName)
        }

        override fun pendingFile(id: String): File = File(pendingDirectory, "$id.staged")

        override suspend fun stage(target: File, share: IncomingShare): StageResult {
            stageCalls++
            return onStage(target, share)
        }
    }

    private class FakeDestinationRepository : DestinationRepository {
        var value: String? = "content://destination/tree"
        var readFailure: Throwable? = null
        var reads = 0
        var onRead: (() -> Unit)? = null

        override val destinationTreeUri: Flow<String?> = flow {
            reads++
            onRead?.invoke()
            readFailure?.let { throw it }
            emit(value)
        }

        override suspend fun setDestination(uri: String) {
            value = uri
        }
    }

    private class FakeUploadDao : UploadDao {
        val items = linkedMapOf<String, UploadItem>()
        private val uploads = MutableStateFlow<List<UploadItem>>(emptyList())
        var insertFailure: Throwable? = null
        var stagingItemsFailure: Throwable? = null
        var completeStagingFailure: Throwable? = null
        var completeStagingResult: Int? = null

        override suspend fun insert(item: UploadItem) {
            insertFailure?.let { throw it }
            items[item.id] = item
            publish()
        }

        override fun observeAll(): Flow<List<UploadItem>> = uploads

        override suspend fun get(id: String): UploadItem? = items[id]

        override suspend fun completeStaging(
            id: String,
            originalName: String,
            outputName: String,
            byteSize: Long
        ): Int {
            completeStagingFailure?.let { throw it }
            completeStagingResult?.let { return it }
            val item = items[id] ?: return 0
            if (item.state != UploadState.STAGING) return 0
            items[id] = item.copy(
                originalName = originalName,
                outputName = outputName,
                byteSize = byteSize,
                state = UploadState.QUEUED,
                errorCode = UploadErrorCode.NONE
            )
            publish()
            return 1
        }

        override suspend fun failStaging(id: String, errorCode: UploadErrorCode): Int {
            val item = items[id] ?: return 0
            if (item.state != UploadState.STAGING) return 0
            items[id] = item.copy(state = UploadState.NEEDS_ATTENTION, errorCode = errorCode)
            publish()
            return 1
        }

        override suspend fun stagingItems(): List<UploadItem> {
            stagingItemsFailure?.let { throw it }
            return items.values.filter { it.state == UploadState.STAGING }
        }

        override suspend fun update(item: UploadItem) {
            items[item.id] = item
            publish()
        }

        override suspend fun beginUpload(id: String): Int = 0

        override suspend fun requeueInterruptedUploads(): Int = 0

        override suspend fun requeueInterruptedUpload(id: String): Int = 0

        override suspend fun queuedIds(): List<String> = items.values
            .filter { it.state == UploadState.QUEUED }
            .map(UploadItem::id)

        override suspend fun finishUpload(
            id: String,
            state: UploadState,
            errorCode: UploadErrorCode,
            retryCount: Int
        ): Int = 0

        override suspend fun retry(id: String, outputName: String, retryCount: Int): Int = 0

        override suspend fun cancel(id: String): Int = 0

        override suspend fun delete(id: String) {
            items.remove(id)
            publish()
        }

        private fun publish() {
            uploads.value = items.values.toList()
        }
    }

    private class FakeUploadScheduler : UploadScheduler {
        var scheduleFailure: Throwable? = null

        override suspend fun schedule(uploadItemId: String) {
            scheduleFailure?.let { throw it }
        }

        override suspend fun cancel(uploadItemId: String) = Unit
    }

    private fun ShareIntakeStatus.terminal(): ShareIntakeStatus.Terminal {
        assertTrue("Expected a terminal intake status", this is ShareIntakeStatus.Terminal)
        return this as ShareIntakeStatus.Terminal
    }

    private fun kotlinx.coroutines.flow.StateFlow<ShareIntakeStatus>.terminal(): ShareIntakeStatus.Terminal =
        value.terminal()

    private companion object {
        val reportShare = IncomingShare(
            Uri.parse("content://sender/report"),
            "report.pdf",
            "application/pdf"
        )
        val photoShare = IncomingShare(
            Uri.parse("content://sender/photo"),
            "photo.jpg",
            "image/jpeg"
        )
    }
}
