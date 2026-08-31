package app.maw629.homerelay.share

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.maw629.homerelay.HomeRelayApplication
import app.maw629.homerelay.data.UploadState
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first
import org.junit.BeforeClass
import org.junit.Before
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlinx.coroutines.runBlocking

@RunWith(AndroidJUnit4::class)
class ShareReceiverActivityTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    @Before
    fun clearQueueAndResetBlockingSource() = runBlocking {
        val application = ApplicationProvider.getApplicationContext<HomeRelayApplication>()
        application.container.database.clearAllTables()
        SampleContentProvider.resetBlockingSource()
    }

    @After
    fun releaseBlockingSource() {
        SampleContentProvider.releaseBlockingSource()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun singleFileShareQueuesItemAndShowsConfirmation() {
        ActivityScenario.launch<ShareReceiverActivity>(sampleSendIntent()).use {
            composeRule.waitUntilAtLeastOneExists(
                hasText("Queued 1 file for Home Relay"),
                5_000
            )
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun backDoesNotFinishReceiverWhileBlockingSourceIsPreparing() {
        ActivityScenario.launch<ShareReceiverActivity>(blockingSendIntent()).use { scenario ->
            assertTrue(
                "The blocking provider must be opened before Back is dispatched",
                SampleContentProvider.awaitBlockingSourceStarted(5, TimeUnit.SECONDS)
            )

            scenario.onActivity { activity ->
                activity.onBackPressedDispatcher.onBackPressed()
            }

            assertTrue(
                "Back must not finish a receiver whose source has not reached a durable outcome",
                scenario.state != androidx.lifecycle.Lifecycle.State.DESTROYED
            )
            SampleContentProvider.releaseBlockingSource()
            composeRule.waitUntilAtLeastOneExists(
                hasText("Queued 1 file for Home Relay"),
                5_000
            )
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun recreationOfBlockingShareCreatesExactlyOneDurableQueueRow() {
        ActivityScenario.launch<ShareReceiverActivity>(blockingSendIntent()).use { scenario ->
            assertTrue(
                "The blocking provider must be opened before receiver recreation",
                SampleContentProvider.awaitBlockingSourceStarted(5, TimeUnit.SECONDS)
            )

            scenario.recreate()
            SampleContentProvider.releaseBlockingSource()
            composeRule.waitUntilAtLeastOneExists(
                hasText("Queued 1 file for Home Relay"),
                5_000
            )

            val rows = runBlocking {
                ApplicationProvider.getApplicationContext<HomeRelayApplication>()
                    .container
                    .database
                    .uploadDao()
                    .observeAll()
                    .first()
            }
            assertEquals(
                "Recreating the receiver must not create a second row for the same shared URI",
                1,
                rows.size
            )
            assertTrue(
                "The one row must finish staging as queued or durable attention",
                rows.single().state == UploadState.QUEUED ||
                    rows.single().state == UploadState.NEEDS_ATTENTION
            )
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun sharedFileStatusStartsBelowStatusBarInset() {
        ActivityScenario.launch<ShareReceiverActivity>(sampleSendIntent()).use { scenario ->
            val statusMatcher = hasText("Preparing files for Home Relay") or
                hasText("Queued 1 file for Home Relay") or
                hasText("Choose a destination in Home Relay before sharing files") or
                hasText("A shared file could not be read") or
                hasText("Not enough storage to queue shared files")
            composeRule.waitUntilAtLeastOneExists(statusMatcher, 5_000)
            composeRule.onNode(statusMatcher).assertIsDisplayed()
            val statusTop = composeRule.onAllNodes(statusMatcher)
                .fetchSemanticsNodes()
                .single()
                .boundsInRoot
                .top
            var topInset = 0
            scenario.onActivity { activity ->
                topInset = requireNotNull(ViewCompat.getRootWindowInsets(activity.window.decorView))
                    .getInsets(WindowInsetsCompat.Type.statusBars())
                    .top
            }

            assertTrue(
                "Share status must start below the status bar inset",
                statusTop >= topInset
            )
        }
    }

    private fun sampleSendIntent() = Intent(Intent.ACTION_SEND)
        .setClass(
            ApplicationProvider.getApplicationContext(),
            ShareReceiverActivity::class.java
        )
        .setType("application/pdf")
        .putExtra(
            Intent.EXTRA_STREAM,
            Uri.parse("content://app.maw629.homerelay.share-test/report.pdf")
        )
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

    private fun blockingSendIntent() = Intent(Intent.ACTION_SEND)
        .setClass(
            ApplicationProvider.getApplicationContext(),
            ShareReceiverActivity::class.java
        )
        .setType("application/pdf")
        .putExtra(
            Intent.EXTRA_STREAM,
            Uri.parse("content://app.maw629.homerelay.share-test/blocking-report.pdf")
        )
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

    private companion object {
        @JvmStatic
        @BeforeClass
        fun configureDestinationBeforeActivityLaunch() = runBlocking {
            ApplicationProvider.getApplicationContext<HomeRelayApplication>()
                .container
                .destinationStore
                .setDestination("content://app.maw629.homerelay.share-test/tree/destination")
        }

    }
}
