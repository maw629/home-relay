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
import org.junit.BeforeClass
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlinx.coroutines.runBlocking

@RunWith(AndroidJUnit4::class)
class ShareReceiverActivityTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    @Test
    fun singleFileShareQueuesItemAndShowsConfirmation() {
        ActivityScenario.launch<ShareReceiverActivity>(sampleSendIntent()).use {
            composeRule.onNodeWithText("Queued 1 file for Home Relay").assertExists()
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
