package app.maw629.homerelay.share

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import app.maw629.homerelay.data.UploadErrorCode
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
class ShareIntakeViewModelTest {
    @After
    fun resetMainDispatcher() {
        Dispatchers.resetMain()
    }

    @Test
    fun firstIntakePersistsUuidBeforeStartingCoordinatorOperation() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val handle = SavedStateHandle()
        val operations = FakeShareIntakeOperations()
        operations.onStart = { intakeId, _ ->
            assertEquals(intakeId, handle[ShareIntakeViewModel.INTAKE_ID_KEY])
        }
        val viewModel = ShareIntakeViewModel(handle, operations)

        viewModel.beginOrAttach(listOf(reportShare))

        val intakeId = requireNotNull(handle.get<String>(ShareIntakeViewModel.INTAKE_ID_KEY))
        UUID.fromString(intakeId)
        assertEquals(listOf(intakeId), operations.startedIds)
    }

    @Test
    fun recreatedViewModelObservesSavedLiveIntakeWithoutStartingItAgain() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val handle = SavedStateHandle()
        val operations = FakeShareIntakeOperations()
        val firstViewModel = ShareIntakeViewModel(handle, operations)

        firstViewModel.beginOrAttach(listOf(reportShare))
        val intakeId = requireNotNull(handle.get<String>(ShareIntakeViewModel.INTAKE_ID_KEY))
        val recreatedViewModel = ShareIntakeViewModel(handle, operations)
        recreatedViewModel.beginOrAttach(listOf(otherShare))

        assertEquals(listOf(intakeId), operations.startedIds)
        assertEquals(listOf(intakeId), operations.observedIds)
    }

    @Test
    fun processRestoredIntakeWaitsForRecoveryThenReportsInterruptedWithoutRestarting() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val handle = SavedStateHandle(
            mapOf(ShareIntakeViewModel.INTAKE_ID_KEY to "restored-intake")
        )
        val operations = FakeShareIntakeOperations()
        val viewModel = ShareIntakeViewModel(
            handle,
            operations,
            nowUptimeMillis = { 1_788_013_501_999L }
        )

        viewModel.beginOrAttach(listOf(reportShare))
        assertEquals(ShareIntakeStatus.Preparing, viewModel.status.value)
        operations.recovery.complete(Result.success(Unit))
        advanceUntilIdle()

        assertEquals(emptyList<String>(), operations.startedIds)
        assertEquals(listOf("restored-intake"), operations.observedIds)
        assertEquals(
            ShareIntakeStatus.Terminal(
                queuedCount = 0,
                attentionCount = 1,
                queueUnavailableCount = 0,
                attentionErrors = setOf(UploadErrorCode.SHARE_INTERRUPTED),
                terminalAtMillis = 1_788_013_501_999L
            ),
            viewModel.status.value
        )
    }

    @Test
    fun clearingPreparingViewModelRequestsDeferredOperationRelease() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val handle = SavedStateHandle()
        val operations = FakeShareIntakeOperations()
        val owner = TestViewModelStoreOwner()
        val viewModel = ViewModelProvider(owner, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
                ShareIntakeViewModel(handle, operations) as T
        })[ShareIntakeViewModel::class.java]

        viewModel.beginOrAttach(listOf(reportShare))
        val intakeId = requireNotNull(handle.get<String>(ShareIntakeViewModel.INTAKE_ID_KEY))
        owner.viewModelStore.clear()

        assertEquals(listOf(intakeId), operations.releasedIds)
    }

    private class FakeShareIntakeOperations : ShareIntakeOperations {
        val startedIds = mutableListOf<String>()
        val observedIds = mutableListOf<String>()
        val operations = mutableMapOf<String, MutableStateFlow<ShareIntakeStatus>>()
        val releasedIds = mutableListOf<String>()
        val recovery = CompletableDeferred<Result<Unit>>()
        var onStart: (String, List<IncomingShare>) -> Unit = { _, _ -> }

        override suspend fun recoverInterruptedStaging() = Unit

        override suspend fun awaitRecovery(): Result<Unit> = recovery.await()

        override fun start(
            intakeId: String,
            shares: List<IncomingShare>
        ): StateFlow<ShareIntakeStatus> {
            onStart(intakeId, shares)
            startedIds += intakeId
            return operations.getOrPut(intakeId) { MutableStateFlow(ShareIntakeStatus.Preparing) }
        }

        override fun observe(intakeId: String): StateFlow<ShareIntakeStatus>? {
            observedIds += intakeId
            return operations[intakeId]
        }

        override fun release(intakeId: String) {
            releasedIds += intakeId
        }
    }

    private class TestViewModelStoreOwner : ViewModelStoreOwner {
        override val viewModelStore = ViewModelStore()
    }

    private companion object {
        val reportShare = IncomingShare(
            Uri.parse("content://sender/report.pdf"),
            "report.pdf",
            "application/pdf"
        )
        val otherShare = IncomingShare(
            Uri.parse("content://sender/other.pdf"),
            "other.pdf",
            "application/pdf"
        )
    }
}
