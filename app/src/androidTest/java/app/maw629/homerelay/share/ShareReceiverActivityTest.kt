package app.maw629.homerelay.share

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.SystemClock
import android.view.ViewGroup
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import app.maw629.homerelay.HomeRelayApplication
import app.maw629.homerelay.data.UploadState
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
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
    fun realShareStaysVisibleForTheDefaultTerminalDurationThenFinishes() {
        ActivityScenario.launch<ShareReceiverActivity>(sampleSendIntent()).use { scenario ->
            composeRule.waitUntilAtLeastOneExists(
                hasText("Queued 1 file for Home Relay"),
                5_000
            )
            val terminalObservedAt = SystemClock.elapsedRealtime()

            SystemClock.sleep(1_000)
            assertNotEquals(
                "The receiver must remain visible one second after its terminal status is observed",
                androidx.lifecycle.Lifecycle.State.DESTROYED,
                scenario.state
            )

            assertTrue(
                "The receiver must finish by 2.1 seconds after its terminal status is observed",
                awaitDestroyed(scenario, 1_100)
            )
            assertTrue(
                "The default terminal status display must not exceed 2.1 seconds",
                SystemClock.elapsedRealtime() - terminalObservedAt <= 2_100
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
    fun terminalDeadlineSurvivesReceiverRecreation() {
        ActivityScenario.launch<ShareReceiverActivity>(sampleSendIntent()).use { scenario ->
            composeRule.waitUntilAtLeastOneExists(
                hasText("Queued 1 file for Home Relay"),
                5_000
            )
            val terminalObservedAt = SystemClock.elapsedRealtime()

            SystemClock.sleep(700)
            scenario.recreate()

            assertTrue(
                "Recreating the receiver must retain the original terminal deadline",
                awaitDestroyed(scenario, 1_400)
            )
            assertTrue(
                "Receiver recreation must not reset the default terminal status display",
                SystemClock.elapsedRealtime() - terminalObservedAt <= 2_100
            )
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun backFinishesReceiverAfterRealShareReachesTerminalStatus() {
        ActivityScenario.launch<ShareReceiverActivity>(sampleSendIntent()).use { scenario ->
            composeRule.waitUntilAtLeastOneExists(
                hasText("Queued 1 file for Home Relay"),
                5_000
            )

            scenario.onActivity { activity ->
                activity.onBackPressedDispatcher.onBackPressed()
            }

            assertTrue(
                "Back must finish a receiver after its real source reaches a durable terminal outcome",
                awaitDestroyed(scenario, 500)
            )
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun statusCardIsCenteredInSafeDrawingCoordinates() {
        ActivityScenario.launch<ShareReceiverActivity>(sampleSendIntent()).use { scenario ->
            composeRule.waitUntilAtLeastOneExists(hasTestTag("share-status-card"), 5_000)
            val cardBounds = composeRule.onNodeWithTag("share-status-card")
                .assertIsDisplayed()
                .fetchSemanticsNode()
                .boundsInRoot
            val rootBounds = composeRule.onRoot().fetchSemanticsNode().boundsInRoot
            lateinit var safeDrawingInsets: androidx.core.graphics.Insets
            scenario.onActivity { activity ->
                safeDrawingInsets = requireNotNull(
                    ViewCompat.getRootWindowInsets(activity.window.decorView)
                ).getInsets(
                    WindowInsetsCompat.Type.systemBars() or
                        WindowInsetsCompat.Type.displayCutout() or
                        WindowInsetsCompat.Type.ime()
                )
            }
            val safeDrawingCenterX = rootBounds.left +
                (safeDrawingInsets.left + rootBounds.width - safeDrawingInsets.right) / 2f
            val safeDrawingCenterY = rootBounds.top +
                (safeDrawingInsets.top + rootBounds.height - safeDrawingInsets.bottom) / 2f

            assertEquals(
                "Status card center must use the safe-drawing rectangle in Compose root coordinates",
                safeDrawingCenterX,
                cardBounds.center.x,
                1f
            )
            assertEquals(
                "Status card center must use the safe-drawing rectangle in Compose root coordinates",
                safeDrawingCenterY,
                cardBounds.center.y,
                1f
            )
        }
    }

    @OptIn(ExperimentalTestApi::class)
    @Suppress("DEPRECATION")
    @Test
    fun transparentReceiverLeavesHostBackgroundVisibleOutsideCardAndSystemBars() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        ActivityScenario.launch(ShareOverlayHostActivity::class.java).use { hostScenario ->
            instrumentation.waitForIdleSync()
            hostScenario.onActivity { host ->
                host.startActivity(sampleSendIntent())
            }

            composeRule.waitUntilAtLeastOneExists(hasTestTag("share-status-card"), 5_000)
            val cardBounds = composeRule.onNodeWithTag("share-status-card")
                .fetchSemanticsNode()
                .boundsInRoot
            val composeRootLocation = IntArray(2)
            var statusBarInset = 0
            var navigationBarInset = 0
            lateinit var receiver: ShareReceiverActivity
            instrumentation.runOnMainSync {
                receiver = ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(Stage.RESUMED)
                    .filterIsInstance<ShareReceiverActivity>()
                    .single()
                receiver.findViewById<ViewGroup>(android.R.id.content)
                    .getChildAt(0)
                    .getLocationOnScreen(composeRootLocation)
                val insets = requireNotNull(
                    ViewCompat.getRootWindowInsets(receiver.window.decorView)
                )
                statusBarInset = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
                navigationBarInset = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            }
            val screenshot = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
            val cardLeftOnScreen = cardBounds.left + composeRootLocation[0]
            val cardRightOnScreen = cardBounds.right + composeRootLocation[0]
            val cardCenterYOnScreen = cardBounds.center.y + composeRootLocation[1]
            val outsideCardX = if (cardLeftOnScreen >= 16f) {
                (cardLeftOnScreen / 2f).toInt()
            } else {
                ((cardRightOnScreen + screenshot.width) / 2f).toInt()
            }
            val outsideCardY = cardCenterYOnScreen.toInt()
            val visibleBackground = screenshot.getPixel(outsideCardX, outsideCardY)

            assertColorNear(
                "An opaque receiver window must not cover the source app outside the status card",
                ShareOverlayHostActivity.BACKGROUND_COLOR,
                visibleBackground
            )

            assertTrue("Receiver must render a status bar", statusBarInset > 0)
            assertTrue("Receiver must render a navigation bar", navigationBarInset > 0)
            assertColorNear(
                "An opaque status-bar contrast scrim must not cover the source app",
                ShareOverlayHostActivity.BACKGROUND_COLOR,
                screenshot.getPixel(screenshot.width / 2, statusBarInset / 2)
            )
            assertColorNear(
                "An opaque navigation-bar contrast scrim must not cover the source app",
                ShareOverlayHostActivity.BACKGROUND_COLOR,
                screenshot.getPixel(
                    screenshot.width / 4,
                    screenshot.height - navigationBarInset / 2
                )
            )
            assertEquals("Receiver status bar must be transparent", Color.TRANSPARENT, receiver.window.statusBarColor)
            assertEquals(
                "Receiver navigation bar must be transparent",
                Color.TRANSPARENT,
                receiver.window.navigationBarColor
            )
        }
    }

    private fun awaitDestroyed(
        scenario: ActivityScenario<ShareReceiverActivity>,
        timeoutMillis: Long
    ): Boolean {
        val deadline = SystemClock.elapsedRealtime() + timeoutMillis
        while (SystemClock.elapsedRealtime() < deadline) {
            if (scenario.state == androidx.lifecycle.Lifecycle.State.DESTROYED) return true
            SystemClock.sleep(10)
        }
        return scenario.state == androidx.lifecycle.Lifecycle.State.DESTROYED
    }

    private fun assertColorNear(message: String, expected: Int, actual: Int) {
        assertTrue(
            message,
            abs(Color.red(expected) - Color.red(actual)) <= 2 &&
                abs(Color.green(expected) - Color.green(actual)) <= 2 &&
                abs(Color.blue(expected) - Color.blue(actual)) <= 2
        )
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
