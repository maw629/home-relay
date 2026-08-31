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

    private fun viewModel(
        store: DestinationRepository,
        gateway: DestinationGateway
    ): HomeRelayViewModel =
        HomeRelayViewModel(
            store,
            gateway,
            UploadRepository(EmptyUploadDao(), NoOpUploadScheduler(), { "id" }, { 0L }, { "suffix" })
        )

    private class RecordingPermissionTaker : PersistableUriPermissionTaker {
        override fun takePersistableUriPermission(uri: Uri) = Unit
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
        override suspend fun insert(item: UploadItem) = Unit
        override fun observeAll(): Flow<List<UploadItem>> = MutableStateFlow(emptyList())
        override suspend fun get(id: String): UploadItem? = null
        override suspend fun update(item: UploadItem) = Unit
        override suspend fun beginUpload(id: String): Int = 0
        override suspend fun finishUpload(id: String, state: UploadState, errorCode: UploadErrorCode, retryCount: Int): Int = 0
        override suspend fun retry(id: String, outputName: String, retryCount: Int): Int = 0
        override suspend fun cancel(id: String): Int = 0
        override suspend fun delete(id: String) = Unit
    }
}
