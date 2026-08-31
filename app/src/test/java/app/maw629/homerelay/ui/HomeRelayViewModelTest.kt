package app.maw629.homerelay.ui

import android.net.Uri
import app.maw629.homerelay.data.DestinationRepository
import app.maw629.homerelay.data.UploadDao
import app.maw629.homerelay.data.UploadErrorCode
import app.maw629.homerelay.data.UploadItem
import app.maw629.homerelay.data.UploadState
import app.maw629.homerelay.destination.DestinationGateway
import app.maw629.homerelay.destination.DestinationResult
import app.maw629.homerelay.domain.UploadRepository
import app.maw629.homerelay.work.UploadScheduler
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class HomeRelayViewModelTest {
    @After
    fun resetMainDispatcher() {
        Dispatchers.resetMain()
    }

    @Test
    fun successfulValidationReplacesDestinationAndClearsExistingError() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val store = FakeDestinationRepository("content://old")
        val gateway = FakeDestinationGateway(DestinationResult.AccessLost)
        val viewModel = viewModel(store, gateway)
        advanceUntilIdle()
        val uri = Uri.parse("content://new/tree/drive")

        viewModel.selectDestination(uri, RecordingPermissionTaker())
        advanceUntilIdle()
        assertEquals(
            "Home Relay cannot write to that folder. Choose another writable Drive folder.",
            viewModel.settingsState.value.error
        )

        gateway.result = DestinationResult.Success
        viewModel.selectDestination(uri, RecordingPermissionTaker())
        advanceUntilIdle()

        assertEquals(uri.toString(), store.destinationTreeUri.first())
        assertEquals(SettingsState("Drive folder selected"), viewModel.settingsState.value)
    }

    @Test
    fun failedValidationPreservesDestinationAndShowsError() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val store = FakeDestinationRepository("content://old")
        val viewModel = viewModel(store, FakeDestinationGateway(DestinationResult.AccessLost))
        advanceUntilIdle()

        viewModel.selectDestination(Uri.parse("content://new/tree/drive"), RecordingPermissionTaker())
        advanceUntilIdle()

        assertEquals("content://old", store.destinationTreeUri.first())
        assertEquals("Drive folder selected", viewModel.settingsState.value.destinationName)
        assertEquals(
            "Home Relay cannot write to that folder. Choose another writable Drive folder.",
            viewModel.settingsState.value.error
        )
    }

    @Test
    fun failedValidationReleasesTheNewlyAcquiredGrant() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val permissionTaker = RecordingPermissionTaker()
        val viewModel = viewModel(
            FakeDestinationRepository("content://old"),
            FakeDestinationGateway(DestinationResult.AccessLost)
        )

        viewModel.selectDestination(Uri.parse("content://new/tree/drive"), permissionTaker)
        advanceUntilIdle()

        assertEquals(
            listOf("take:content://new/tree/drive", "release:content://new/tree/drive"),
            permissionTaker.operations
        )
    }

    @Test
    fun failedValidationOfTheStoredUriRetainsItsExistingGrant() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val storedUri = "content://old/tree/drive"
        val store = FakeDestinationRepository(storedUri)
        val permissionTaker = RecordingPermissionTaker(retainedGrant = storedUri)
        val viewModel = viewModel(store, FakeDestinationGateway(DestinationResult.AccessLost))

        viewModel.selectDestination(Uri.parse(storedUri), permissionTaker)
        advanceUntilIdle()

        assertEquals(storedUri, store.currentDestination)
        assertEquals(setOf(storedUri), permissionTaker.retainedGrants)
        assertEquals(listOf("take:$storedUri"), permissionTaker.operations)
    }

    @Test
    fun successfulReplacementReleasesThePreviousGrantAfterPersistence() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val store = FakeDestinationRepository("content://old")
        val permissionTaker = RecordingPermissionTaker(destination = { store.currentDestination })
        val viewModel = viewModel(store, FakeDestinationGateway(DestinationResult.Success))

        viewModel.selectDestination(Uri.parse("content://new/tree/drive"), permissionTaker)
        advanceUntilIdle()

        assertEquals("content://new/tree/drive", store.currentDestination)
        assertEquals(listOf("take:content://new/tree/drive", "release:content://old"), permissionTaker.operations)
        assertEquals(listOf("content://new/tree/drive"), permissionTaker.destinationWhenReleased)
    }

    @Test
    fun interruptedShareShowsAnActionableError() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val dao = EmptyUploadDao()
        dao.insert(
            UploadItem(
                id = "interrupted-share",
                originalName = "report.pdf",
                mimeType = "application/pdf",
                outputName = "report.pdf",
                stagedPath = "/tmp/report.pdf",
                byteSize = 42,
                createdAtMillis = 1,
                retryCount = 0,
                state = UploadState.NEEDS_ATTENTION,
                errorCode = UploadErrorCode.SHARE_INTERRUPTED
            )
        )
        val viewModel = viewModel(
            FakeDestinationRepository(null),
            FakeDestinationGateway(DestinationResult.Success),
            dao
        )

        assertEquals(
            "The shared file could not be prepared. Share it again.",
            viewModel.uploads.first { it.isNotEmpty() }.single().errorMessage
        )
    }

    private fun viewModel(
        store: DestinationRepository,
        gateway: DestinationGateway,
        dao: UploadDao = EmptyUploadDao()
    ): HomeRelayViewModel =
        HomeRelayViewModel(
            store,
            gateway,
            UploadRepository(dao, NoOpUploadScheduler(), { "id" }, { 0L }, { "suffix" })
        )

    private class RecordingPermissionTaker(
        private val destination: (() -> String?)? = null,
        retainedGrant: String? = null
    ) : PersistableUriPermissionTaker {
        val operations = mutableListOf<String>()
        val destinationWhenReleased = mutableListOf<String?>()
        val retainedGrants = retainedGrant?.let(::setOf)?.toMutableSet() ?: mutableSetOf()

        override fun takePersistableUriPermission(uri: Uri) {
            operations += "take:$uri"
            retainedGrants += uri.toString()
        }

        override fun releasePersistableUriPermission(uri: Uri) {
            operations += "release:$uri"
            retainedGrants -= uri.toString()
            destination?.let { destinationWhenReleased += it() }
        }
    }

    private class FakeDestinationGateway(var result: DestinationResult) : DestinationGateway {
        override suspend fun validate(treeUri: Uri): DestinationResult = result

        override suspend fun write(
            treeUri: Uri,
            source: File,
            mimeType: String,
            outputName: String,
            onBytesCopied: suspend (Long) -> Unit
        ): DestinationResult = result
    }

    private class FakeDestinationRepository(initialDestination: String?) : DestinationRepository {
        private val destination = MutableStateFlow(initialDestination)
        val currentDestination: String? get() = destination.value
        override val destinationTreeUri: Flow<String?> = destination

        override suspend fun setDestination(uri: String) {
            destination.value = uri
        }
    }

    private class NoOpUploadScheduler : UploadScheduler {
        override suspend fun schedule(uploadItemId: String) = Unit
        override suspend fun cancel(uploadItemId: String) = Unit
    }

    private class EmptyUploadDao : UploadDao {
        private val items = mutableMapOf<String, UploadItem>()
        private val uploads = MutableStateFlow<List<UploadItem>>(emptyList())

        override suspend fun insert(item: UploadItem) = save(item)
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
            save(
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
            save(item.copy(state = UploadState.NEEDS_ATTENTION, errorCode = errorCode))
            return 1
        }

        override suspend fun stagingItems(): List<UploadItem> = items.values
            .filter { it.state == UploadState.STAGING }
            .sortedBy { it.createdAtMillis }

        override suspend fun update(item: UploadItem) = save(item)
        override suspend fun beginUpload(id: String): Int = 0
        override suspend fun requeueInterruptedUploads(): Int = 0
        override suspend fun requeueInterruptedUpload(id: String): Int = 0
        override suspend fun queuedIds(): List<String> = emptyList()
        override suspend fun finishUpload(id: String, state: UploadState, errorCode: UploadErrorCode, retryCount: Int): Int = 0
        override suspend fun retry(id: String, outputName: String, retryCount: Int): Int = 0
        override suspend fun cancel(id: String): Int = 0
        override suspend fun delete(id: String) {
            items.remove(id)
            uploads.value = items.values.toList()
        }

        private fun save(item: UploadItem) {
            items[item.id] = item
            uploads.value = items.values.toList()
        }
    }
}
